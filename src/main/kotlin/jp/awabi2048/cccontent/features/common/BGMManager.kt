package jp.awabi2048.cccontent.features.common

import jp.awabi2048.cccontent.CCContent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

object BGMManager {
    private data class PrecisePlayback(
        val task: BukkitTask,
        val soundKey: String,
        val startNanos: Long,
        val loopDurationNanos: Long,
        @Volatile var nextPlayAtNanos: Long,
        @Volatile var loopCount: Long
    )

    private val activePlaybacks = mutableMapOf<UUID, PrecisePlayback>()
    private val bgmConfigs = mutableMapOf<String, BGMConfig>()

    data class BGMConfig(val key: String, val duration: Int)

    fun loadConfig() {
        bgmConfigs.clear()
        val plugin = CCContent.instance
        val config = jp.awabi2048.cccontent.features.sukima_dungeon.SukimaConfigHelper.getConfig(plugin)
        val section = config.getConfigurationSection("bgm")
        if (section == null) {
            plugin.logger.warning("BGM設定セクション(bgm)が見つかりません")
            return
        }
        val keys = section.getKeys(false)
        for (key in keys) {
            val bgmKey = section.getString("$key.key") ?: continue
            val duration = section.getInt("$key.duration", 0)
            bgmConfigs[key] = BGMConfig(bgmKey, duration)
        }
    }

    fun play(player: Player, bgmId: String) {
        val plugin = CCContent.instance
        var config = bgmConfigs[bgmId]
        if (config == null) {
            loadConfig()
            config = bgmConfigs[bgmId]
        }
        if (config == null) {
            plugin.logger.warning("BGM設定が見つかりません: id=$bgmId")
            return
        }
        if (config.duration <= 0) {
            plugin.logger.warning("BGM再生時間が不正です: id=$bgmId duration=${config.duration}")
            return
        }

        playLoop(player, config.key, config.duration)
    }

    fun playLoop(player: Player, soundKey: String, durationSeconds: Int) {
        val plugin = CCContent.instance
        if (durationSeconds <= 0) {
            plugin.logger.warning("BGM再生時間が不正です: key=$soundKey duration=$durationSeconds")
            return
        }
        val loopTicks = durationSeconds * 20L
        playPrecise(player, soundKey, loopTicks)
    }

    fun playLoopTicks(player: Player, soundKey: String, loopTicks: Long) {
        if (loopTicks <= 0L) {
            CCContent.instance.logger.warning("BGM再生tickが不正です: key=$soundKey loopTicks=$loopTicks")
            return
        }
        playPrecise(player, soundKey, loopTicks)
    }

    fun playPrecise(player: Player, soundKey: String, loopTicks: Long) {
        val plugin = CCContent.instance
        if (loopTicks <= 0L) {
            plugin.logger.warning("BGM再生tickが不正です: key=$soundKey loopTicks=$loopTicks")
            return
        }

        val loopDurationNanos = loopTicks * 50_000_000L
        val startNanos = System.nanoTime()
        val nextPlayAtNanos = startNanos + loopDurationNanos

        // 初回即時再生
        player.playSound(player.location, soundKey, 1.0f, 1.0f)

        val task = object : org.bukkit.scheduler.BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) {
                    stop(player, soundKey)
                    return
                }

                val playback = activePlaybacks[player.uniqueId] ?: return
                val now = System.nanoTime()
                if (now >= playback.nextPlayAtNanos) {
                    player.playSound(player.location, soundKey, 1.0f, 1.0f)
                    var next = playback.nextPlayAtNanos + playback.loopDurationNanos
                    while (next <= now) {
                        next += playback.loopDurationNanos
                    }
                    playback.nextPlayAtNanos = next
                    playback.loopCount++
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)

        replaceActivePlayback(player, task, soundKey, startNanos, loopDurationNanos, nextPlayAtNanos)
    }

    fun getElapsedNanos(player: Player): Long? {
        val playback = activePlaybacks[player.uniqueId] ?: return null
        return System.nanoTime() - playback.startNanos
    }

    fun getElapsedBeats(player: Player, beatNanos: Double): Long {
        val elapsed = getElapsedNanos(player) ?: return 0L
        return (elapsed.toDouble() / beatNanos).toLong() + 1L
    }

    fun getPlaybackStartNanos(player: Player): Long? {
        return activePlaybacks[player.uniqueId]?.startNanos
    }

    fun isPlaying(player: Player, soundKey: String? = null): Boolean {
        val playback = activePlaybacks[player.uniqueId] ?: return false
        return soundKey == null || playback.soundKey == soundKey
    }

    fun stop(player: Player) {
        val playback = activePlaybacks.remove(player.uniqueId) ?: return
        playback.task.cancel()
        player.stopSound(playback.soundKey)
    }

    fun stop(player: Player, soundKey: String) {
        val playback = activePlaybacks[player.uniqueId] ?: return
        if (playback.soundKey != soundKey) return
        playback.task.cancel()
        activePlaybacks.remove(player.uniqueId)
        player.stopSound(soundKey)
    }

    private fun replaceActivePlayback(
        player: Player,
        task: BukkitTask,
        soundKey: String,
        startNanos: Long,
        loopDurationNanos: Long,
        nextPlayAtNanos: Long
    ) {
        activePlaybacks.remove(player.uniqueId)?.let { current ->
            current.task.cancel()
            player.stopSound(current.soundKey)
        }
        activePlaybacks[player.uniqueId] = PrecisePlayback(
            task = task,
            soundKey = soundKey,
            startNanos = startNanos,
            loopDurationNanos = loopDurationNanos,
            nextPlayAtNanos = nextPlayAtNanos,
            loopCount = 0L
        )
    }

    fun stopAll() {
        val entries = synchronized(activePlaybacks) { activePlaybacks.toMap() }
        entries.values.forEach { playback -> playback.task.cancel() }
        activePlaybacks.clear()
        for (player in Bukkit.getOnlinePlayers()) {
            entries[player.uniqueId]?.let { playback -> player.stopSound(playback.soundKey) }
            for (config in bgmConfigs.values) {
                player.stopSound(config.key)
            }
        }
    }
}
