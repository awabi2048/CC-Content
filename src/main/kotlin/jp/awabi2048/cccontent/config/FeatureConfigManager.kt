package jp.awabi2048.cccontent.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object FeatureConfigManager {
    const val ARENA_SETTINGS_PATH = "config/arena/settings.yml"
    const val ARENA_MISSION_PATH = "config/arena/mission.yml"
    const val ARENA_OVER_ENCHANTER_PATH = "config/arena/over_enchanter.yml"
    const val ARENA_REWARD_PATH = "config/arena/reward.yml"
    const val SUKIMA_SETTINGS_PATH = "config/sukima_dungeon/settings.yml"
    const val RANK_SETTINGS_PATH = "config/rank/settings.yml"

    fun load(plugin: JavaPlugin, resourcePath: String): YamlConfiguration {
        val file = File(plugin.dataFolder, resourcePath)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            plugin.saveResource(resourcePath, false)
        }
        return YamlConfiguration.loadConfiguration(file)
    }
}
