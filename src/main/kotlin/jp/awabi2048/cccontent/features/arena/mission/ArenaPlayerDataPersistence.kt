package jp.awabi2048.cccontent.features.arena.mission

import jp.awabi2048.cccontent.features.arena.ArenaYamlFiles
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Bukkit オブジェクトを含まない、非同期保存専用の不変スナップショットです。 */
data class ArenaPlayerMissionSnapshot(
    val totalMissionClearCount: Int,
    val totalMobKillCount: Int,
    val totalStrongEnemyKillCount: Int,
    val totalOverEnchantSuccessCount: Int,
    val barrierRestartCount: Int,
    val lobbyVisited: Boolean,
    val lobbyTutorialCompleted: Boolean,
    val licenseTierId: String,
    val completedMissionIndices: List<Int>,
    val enchantShardKillCounters: Map<String, Map<String, Int>>
) {
    companion object {
        fun from(data: ArenaPlayerMissionData): ArenaPlayerMissionSnapshot {
            return ArenaPlayerMissionSnapshot(
                totalMissionClearCount = data.totalMissionClearCount,
                totalMobKillCount = data.totalMobKillCount,
                totalStrongEnemyKillCount = data.totalStrongEnemyKillCount,
                totalOverEnchantSuccessCount = data.totalOverEnchantSuccessCount,
                barrierRestartCount = data.barrierRestartCount,
                lobbyVisited = data.lobbyVisited,
                lobbyTutorialCompleted = data.lobbyTutorialCompleted,
                licenseTierId = data.licenseTier.id,
                completedMissionIndices = data.completedMissionIndices.toList().sorted(),
                enchantShardKillCounters = data.enchantShardKillCounters
                    .toSortedMap()
                    .mapValues { (_, countsByMob) ->
                        countsByMob.filterValues { it > 0 }.toSortedMap().toMap()
                    }
                    .filterValues { it.isNotEmpty() }
                    .toMap()
            )
        }
    }
}

fun interface ArenaPlayerSnapshotWriter {
    fun write(file: File, snapshot: ArenaPlayerMissionSnapshot)
}

fun interface ArenaPlayerWriteFailureListener {
    fun onFailure(playerId: UUID, error: Throwable)
}

/**
 * プレイヤーごとの更新を短時間まとめ、単一I/Oスレッドで順序通りに保存します。
 *
 * revision が一致した保存だけを最新として扱うため、保存中に次の撃破更新が入っても
 * 古い保存完了によってdirty状態が失われません。
 */
class ArenaPlayerDataWriteBehind @JvmOverloads constructor(
    private val playerDirectory: File,
    private val debounceMillis: Long = 250L,
    private val retryMillis: Long = 1_000L,
    private val writer: ArenaPlayerSnapshotWriter = ArenaPlayerSnapshotWriter(::writeSnapshotAtomically),
    private val failureListener: ArenaPlayerWriteFailureListener = ArenaPlayerWriteFailureListener { _, _ -> }
) {
    private data class VersionedSnapshot(
        val playerId: UUID,
        val revision: Long,
        val snapshot: ArenaPlayerMissionSnapshot
    )

    private val lock = ReentrantLock()
    private val idleCondition = lock.newCondition()
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
        ThreadFactory { runnable ->
            Thread(runnable, "cc-content-arena-player-data").apply { isDaemon = true }
        }
    )
    private val latestRevision = mutableMapOf<UUID, Long>()
    private val persistedRevision = mutableMapOf<UUID, Long>()
    private val pending = linkedMapOf<UUID, VersionedSnapshot>()
    private var scheduled: ScheduledFuture<*>? = null
    private var running: Boolean = false
    private var accepting: Boolean = true

    init {
        require(debounceMillis >= 0L) { "debounceMillis must not be negative" }
        require(retryMillis > 0L) { "retryMillis must be positive" }
    }

    fun submit(playerId: UUID, snapshot: ArenaPlayerMissionSnapshot): Long = lock.withLock {
        check(accepting) { "Arena player persistence is shutting down" }
        val revision = (latestRevision[playerId] ?: 0L) + 1L
        latestRevision[playerId] = revision
        pending[playerId] = VersionedSnapshot(playerId, revision, snapshot)
        scheduleLocked(debounceMillis)
        revision
    }

    fun isDirty(playerId: UUID): Boolean = lock.withLock {
        (persistedRevision[playerId] ?: 0L) < (latestRevision[playerId] ?: 0L)
    }

    fun flush(timeoutMillis: Long): Boolean {
        require(timeoutMillis >= 0L) { "timeoutMillis must not be negative" }
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        lock.withLock {
            expediteScheduledWriteLocked()
            while (!isIdleLocked()) {
                val remainingNanos = deadlineNanos - System.nanoTime()
                if (remainingNanos <= 0L) return false
                idleCondition.awaitNanos(remainingNanos)
                expediteScheduledWriteLocked()
            }
            return true
        }
    }

    fun closeAndFlush(timeoutMillis: Long): Boolean {
        lock.withLock {
            accepting = false
        }
        val flushed = flush(timeoutMillis)
        executor.shutdown()
        val remaining = timeoutMillis.coerceAtLeast(1L)
        if (!executor.awaitTermination(remaining, TimeUnit.MILLISECONDS)) {
            executor.shutdownNow()
        }
        return flushed
    }

    private fun scheduleLocked(delayMillis: Long) {
        if (running || scheduled?.isDone == false) return
        scheduled = executor.schedule(::drainPending, delayMillis, TimeUnit.MILLISECONDS)
    }

    private fun expediteScheduledWriteLocked() {
        if (pending.isEmpty() || running) return
        val current = scheduled
        if (current != null && !current.isDone) {
            current.cancel(false)
            scheduled = null
        }
        scheduleLocked(0L)
    }

    private fun drainPending() {
        val batch = lock.withLock {
            scheduled = null
            if (running || pending.isEmpty()) {
                idleCondition.signalAll()
                return@withLock emptyList()
            }
            running = true
            pending.values.toList().also { pending.clear() }
        }
        if (batch.isEmpty()) return

        var hadFailure = false
        batch.forEach { entry ->
            try {
                writer.write(File(playerDirectory, "${entry.playerId}.yml"), entry.snapshot)
                lock.withLock {
                    val previous = persistedRevision[entry.playerId] ?: 0L
                    if (entry.revision > previous) {
                        persistedRevision[entry.playerId] = entry.revision
                    }
                }
            } catch (error: Throwable) {
                hadFailure = true
                // 障害通知側の例外でI/Oワーカー自体を失わないよう、保存再試行とは分離します。
                runCatching { failureListener.onFailure(entry.playerId, error) }
                lock.withLock {
                    val newer = pending[entry.playerId]
                    if (newer == null || newer.revision < entry.revision) {
                        pending[entry.playerId] = entry
                    }
                }
            }
        }

        lock.withLock {
            running = false
            if (pending.isNotEmpty()) {
                scheduleLocked(if (hadFailure) retryMillis else debounceMillis)
            }
            idleCondition.signalAll()
        }
    }

    private fun isIdleLocked(): Boolean {
        if (running || pending.isNotEmpty() || scheduled?.isDone == false) return false
        return latestRevision.all { (playerId, revision) ->
            (persistedRevision[playerId] ?: 0L) >= revision
        }
    }

    private companion object {
        fun writeSnapshotAtomically(file: File, snapshot: ArenaPlayerMissionSnapshot) {
            val config = YamlConfiguration()
            config.set("arena.total_clear_count", snapshot.totalMissionClearCount)
            config.set("arena.total_mob_kill_count", snapshot.totalMobKillCount)
            config.set("arena.total_strong_enemy_kill_count", snapshot.totalStrongEnemyKillCount)
            config.set("arena.total_over_enchant_success_count", snapshot.totalOverEnchantSuccessCount)
            config.set("arena.barrier_restart_count", snapshot.barrierRestartCount)
            config.set("arena.lobby.visited", snapshot.lobbyVisited)
            config.set("arena.lobby.tutorial_completed", snapshot.lobbyTutorialCompleted)
            config.set("arena.license_tier", snapshot.licenseTierId)
            config.set("arena.completed", snapshot.completedMissionIndices)
            config.set("arena.enchant_shard_kill_counters", snapshot.enchantShardKillCounters)
            ArenaYamlFiles.saveAtomically(file, config)
        }
    }
}
