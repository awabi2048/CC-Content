package jp.awabi2048.cccontent.features.sukima_dungeon

import jp.awabi2048.cccontent.config.FeatureConfigManager
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

/**
 * スキマダンジョン用の設定ファイルヘルパー。
 */
object SukimaConfigHelper {
    private var cachedConfig: YamlConfiguration? = null

    fun getConfig(plugin: JavaPlugin): YamlConfiguration {
        cachedConfig?.let { return it }
        return reload(plugin)
    }

    fun reload(plugin: JavaPlugin): YamlConfiguration {
        val config = FeatureConfigManager.load(plugin, FeatureConfigManager.SUKIMA_SETTINGS_PATH)
        cachedConfig = config
        return config
    }
}
