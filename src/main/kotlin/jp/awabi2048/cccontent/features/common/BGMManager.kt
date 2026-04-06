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
        val startTick: Long,
        val loopDurationTicks: Long,
        @Volatile var nextPlayAtTick: Long,
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

        val startTick = Bukkit.getCurrentTick().toLong()
        val nextPlayAtTick = startTick + loopTicks

        // 初回即時再生
        player.playSound(player.location, soundKey, 1.0f, 1.0f)

        val task = object : org.bukkit.scheduler.BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) {
                    stop(player, soundKey)
                    return
                }

                val playback = activePlaybacks[player.uniqueId] ?: return
                val currentTick = Bukkit.getCurrentTick().toLong()
                if (currentTick >= playback.nextPlayAtTick) {
                    player.playSound(player.location, soundKey, 1.0f, 1.0f)
                    var next = playback.nextPlayAtTick + playback.loopDurationTicks
                    while (next <= currentTick) {
                        next += playback.loopDurationTicks
                    }
                    playback.nextPlayAtTick = next
                    playback.loopCount++
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)

        replaceActivePlayback(player, task, soundKey, startTick, loopTicks, nextPlayAtTick)
    }

    fun getElapsedNanos(player: Player): Long? {
        val playback = activePlaybacks[player.uniqueId] ?: return null
        val elapsedTicks = (Bukkit.getCurrentTick().toLong() - playback.startTick).coerceAtLeast(0L)
        return elapsedTicks * 50_000_000L
    }

    fun getElapsedBeats(player: Player, beatNanos: Double): Long {
        val elapsed = getElapsedNanos(player) ?: return 0L
        return (elapsed.toDouble() / beatNanos).toLong() + 1L
    }

    fun getPlaybackStartNanos(player: Player): Long? {
        return activePlaybacks[player.uniqueId]?.startTick?.times(50_000_000L)
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
        startTick: Long,
        loopDurationTicks: Long,
        nextPlayAtTick: Long
    ) {
        activePlaybacks.remove(player.uniqueId)?.let { current ->
            current.task.cancel()
            player.stopSound(current.soundKey)
        }
        activePlaybacks[player.uniqueId] = PrecisePlayback(
            task = task,
            soundKey = soundKey,
            startTick = startTick,
            loopDurationTicks = loopDurationTicks,
            nextPlayAtTick = nextPlayAtTick,
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
