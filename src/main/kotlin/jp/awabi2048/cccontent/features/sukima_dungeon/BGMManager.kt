package jp.awabi2048.cccontent.features.sukima_dungeon

import jp.awabi2048.cccontent.CCContent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

object BGMManager {
    private val activeTasks = mutableMapOf<UUID, BukkitTask>()
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
        stop(player)
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
        
        val task = object : org.bukkit.scheduler.BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) {
                    stop(player)
                    return
                }
                
                // プレイヤーにのみ聞こえるように再生
                player.playSound(player.location, config.key, 1.0f, 1.0f)
            }
        }.runTaskTimer(plugin, 0L, config.duration * 20L)
        
        activeTasks[player.uniqueId] = task
    }

    fun stop(player: Player) {
        activeTasks.remove(player.uniqueId)?.cancel()
        player.stopSound("") // 全てのサウンドを停止（必要に応じて特定に絞る）
    }
    
    fun stopAll() {
        activeTasks.values.forEach { it.cancel() }
        activeTasks.clear()
        for (player in Bukkit.getOnlinePlayers()) {
            player.stopSound("")
        }
    }
}
