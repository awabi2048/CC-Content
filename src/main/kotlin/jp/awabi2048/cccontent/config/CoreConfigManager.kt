package jp.awabi2048.cccontent.config

import jp.awabi2048.cccontent.CCContent
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object CoreConfigManager {
    const val RESOURCE_PATH = "config/core.yml"
    private const val DATA_PATH = "config/core.yml"

    fun load(plugin: JavaPlugin): YamlConfiguration {
        val file = ensureExists(plugin)
        return YamlConfiguration.loadConfiguration(file)
    }

    fun get(plugin: JavaPlugin): FileConfiguration {
        return if (plugin is CCContent) {
            plugin.getCoreConfig()
        } else {
            load(plugin)
        }
    }

    private fun ensureExists(plugin: JavaPlugin): File {
        val file = File(plugin.dataFolder, DATA_PATH)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            plugin.saveResource(RESOURCE_PATH, false)
        }
        return file
    }
}
