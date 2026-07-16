package jp.awabi2048.cccontent.config

import jp.awabi2048.cccontent.CCContent
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

    fun setContentEnabled(plugin: JavaPlugin, featureId: String, enabled: Boolean) {
        val file = ensureExists(plugin)
        val config = YamlConfiguration.loadConfiguration(file)
        config.set("content_enabled.$featureId", enabled)
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            config.save(temporary)
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary.toPath())
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
