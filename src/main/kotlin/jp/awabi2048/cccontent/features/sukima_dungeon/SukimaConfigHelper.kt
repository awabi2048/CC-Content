package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * SukimaDungeon用の設定ファイルヘルパー。
 * plugin.config（CC-Content全体のconfig.yml）ではなく、
 * config/sukima/config.yml を読み込む。
 */
object SukimaConfigHelper {
    private var cachedConfig: YamlConfiguration? = null

    fun getConfig(plugin: JavaPlugin): YamlConfiguration {
        cachedConfig?.let { return it }
        return reload(plugin)
    }

    fun reload(plugin: JavaPlugin): YamlConfiguration {
        val file = File(plugin.dataFolder, "config/sukima/config.yml")
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource("config/sukima/config.yml", false)
        }
        val config = YamlConfiguration.loadConfiguration(file)
        cachedConfig = config
        return config
    }
}
