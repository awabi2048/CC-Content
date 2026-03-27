package jp.awabi2048.cccontent.features.sukima_dungeon

import jp.awabi2048.cccontent.CCContent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

object BGMManager {
    private data class ActivePlayback(
        val task: BukkitTask,
        val soundKey: String
    )

    private val activeTasks = mutableMapOf<UUID, ActivePlayback>()
    private val bgmConfigs = mutableMapOf<String, BGMConfig>()

    data class BGMConfig(val key: String, val duration: Int)

    fun loadConfig() {
        bgmConfigs.clear()
        val plugin = CCContent.instance
        val config = SukimaConfigHelper.getConfig(plugin)
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

        val task = object : org.bukkit.scheduler.BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) {
                    stop(player, soundKey)
                    return
                }

                // プレイヤーにのみ聞こえるように再生
                player.playSound(player.location, soundKey, 1.0f, 1.0f)
            }
        }.runTaskTimer(plugin, 0L, durationSeconds * 20L)

        replaceActivePlayback(player, task, soundKey)
    }

    fun playLoopTicks(player: Player, soundKey: String, loopTicks: Long) {
        val plugin = CCContent.instance
        if (loopTicks <= 0L) {
            plugin.logger.warning("BGM再生tickが不正です: key=$soundKey loopTicks=$loopTicks")
            return
        }

        val task = object : org.bukkit.scheduler.BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) {
                    stop(player, soundKey)
                    return
                }

                player.playSound(player.location, soundKey, 1.0f, 1.0f)
            }
        }.runTaskTimer(plugin, 0L, loopTicks)

        replaceActivePlayback(player, task, soundKey)
    }

    fun stop(player: Player) {
        val playback = activeTasks.remove(player.uniqueId) ?: return
        playback.task.cancel()
        player.stopSound(playback.soundKey)
    }

    fun stop(player: Player, soundKey: String) {
        val playback = activeTasks[player.uniqueId] ?: return
        if (playback.soundKey != soundKey) return
        playback.task.cancel()
        activeTasks.remove(player.uniqueId)
        player.stopSound(soundKey)
    }

    private fun replaceActivePlayback(player: Player, task: BukkitTask, soundKey: String) {
        activeTasks.remove(player.uniqueId)?.let { current ->
            current.task.cancel()
            player.stopSound(current.soundKey)
        }
        activeTasks[player.uniqueId] = ActivePlayback(task, soundKey)
    }

    fun stopAll() {
        val entries = activeTasks.toMap()
        entries.values.forEach { playback -> playback.task.cancel() }
        activeTasks.clear()
        for (player in Bukkit.getOnlinePlayers()) {
            entries[player.uniqueId]?.let { playback -> player.stopSound(playback.soundKey) }
            for (config in bgmConfigs.values) {
                player.stopSound(config.key)
            }
        }
    }
}
