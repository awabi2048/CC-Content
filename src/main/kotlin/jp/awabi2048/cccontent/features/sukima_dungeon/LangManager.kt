package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object LangManager {
    private val langMap = mutableMapOf<String, YamlConfiguration>()
    private var defaultLang = "ja_jp"

    fun load(plugin: JavaPlugin) {
        val langDir = File(plugin.dataFolder, "lang")
        if (!langDir.exists()) {
            langDir.mkdirs()
        }

        saveDefaultLang(plugin, "ja_jp.yml")
        saveDefaultLang(plugin, "en_us.yml")

        langMap.clear()
        val langFiles = langDir.listFiles { file ->
            file.isFile && file.name.endsWith(".yml", ignoreCase = true)
        }?.sortedBy { it.name.lowercase() }.orEmpty()

        if (langFiles.isEmpty()) {
            throw IllegalStateException("[LangManager] 言語ファイルが見つかりません: ${langDir.absolutePath}")
        }

        langFiles.forEach { file ->
            val langName = file.name.removeSuffix(".yml").lowercase()
            val config = YamlConfiguration.loadConfiguration(file)
            langMap[langName] = config
        }

        if (defaultLang !in langMap.keys) {
            throw IllegalStateException("[LangManager] 既定言語ファイルが見つかりません: $defaultLang.yml")
        }
    }

    private fun saveDefaultLang(plugin: JavaPlugin, fileName: String) {
        val file = File(plugin.dataFolder, "lang/$fileName")
        if (!file.exists()) {
            plugin.saveResource("lang/$fileName", false)
        }
    }

     fun getMessage(lang: String, key: String, params: Map<String, String> = emptyMap()): String {
         val normalizedLang = lang.lowercase()
         val config = langMap[normalizedLang]
             ?: throw IllegalStateException("[LangManager] 言語がロードされていません: $lang (available=${langMap.keys})")

        val fullKey = "sukima_dungeon.$key"
        val messageList = config.getStringList(fullKey)
        var message = when {
            messageList.isNotEmpty() -> messageList.random()
            config.isString(fullKey) -> config.getString(fullKey)
                ?: throw IllegalStateException("[LangManager] メッセージ取得に失敗しました: key=$fullKey lang=$lang")
            else -> throw IllegalStateException("[LangManager] メッセージキーが見つかりません: key=$fullKey lang=$lang")
        }

        for ((k, v) in params) {
            message = message.replace("{$k}", v)
        }

        return ChatColor.translateAlternateColorCodes('&', message)
    }

     fun getList(lang: String, key: String): List<String> {
         val normalizedLang = lang.lowercase()
         val config = langMap[normalizedLang]
             ?: throw IllegalStateException("[LangManager] 言語がロードされていません: $lang (available=${langMap.keys})")
        val fullKey = "sukima_dungeon.$key"
        if (config.isList(fullKey)) {
            return config.getStringList(fullKey).map { ChatColor.translateAlternateColorCodes('&', it) }
        }
        throw IllegalStateException("[LangManager] リストキーが見つからないか型が不正です: key=$fullKey lang=$lang")
    }

     fun getTierName(lang: String, tier: String): String {
         val normalizedLang = lang.lowercase()
         val config = langMap[normalizedLang]
             ?: throw IllegalStateException("[LangManager] 言語がロードされていません: $lang (available=${langMap.keys})")
        val key = "sukima_dungeon.tiers.$tier"
        if (!config.isString(key)) {
            throw IllegalStateException("[LangManager] tier名キーが見つからないか型が不正です: key=$key lang=$lang")
        }
        return config.getString(key)
            ?: throw IllegalStateException("[LangManager] tier名の取得に失敗しました: key=$key lang=$lang")
    }
}
