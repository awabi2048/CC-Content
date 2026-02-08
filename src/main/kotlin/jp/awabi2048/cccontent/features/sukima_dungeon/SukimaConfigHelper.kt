package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

/**
 * SukimaDungeon用の設定ファイルヘルパー。
 * CC-Content全体の root config.yml を参照する。
 */
object SukimaConfigHelper {
    private var cachedConfig: YamlConfiguration? = null

    fun getConfig(plugin: JavaPlugin): YamlConfiguration {
        cachedConfig?.let { return it }
        return reload(plugin)
    }

    fun reload(plugin: JavaPlugin): YamlConfiguration {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()

        val config = YamlConfiguration()
        val section = plugin.config.getConfigurationSection("sukima_dungeon")
        if (section == null) {
            plugin.logger.warning("config.yml に sukima_dungeon セクションが見つかりません")
            cachedConfig = config
            return config
        }

        for (key in section.getKeys(true)) {
            if (section.isConfigurationSection(key)) continue
            config.set(key, section.get(key))
        }

        cachedConfig = config
        return config
    }
}
