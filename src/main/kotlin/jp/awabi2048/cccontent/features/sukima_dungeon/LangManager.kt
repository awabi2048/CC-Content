package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object LangManager {
    private val langMap = mutableMapOf<String, YamlConfiguration>()
    private var defaultLang = "ja_jp"

    fun load(plugin: JavaPlugin) {
        val langDir = File(plugin.dataFolder, "lang/sukima")
        if (!langDir.exists()) {
            langDir.mkdirs()
        }

        // Save default resources
        saveDefaultLang(plugin, "en_us.yml")
        saveDefaultLang(plugin, "ja_jp.yml")

        // Load all yml files in lang dir
        langMap.clear()
        langDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".yml")) {
                val langName = file.name.removeSuffix(".yml")
                val config = YamlConfiguration.loadConfiguration(file)
                langMap[langName] = config
            }
        }
    }

    private fun saveDefaultLang(plugin: JavaPlugin, fileName: String) {
        val file = File(plugin.dataFolder, "lang/sukima/$fileName")
        if (!file.exists()) {
            plugin.saveResource("lang/sukima/$fileName", false)
        }
    }

    fun getMessage(lang: String, key: String, params: Map<String, String> = emptyMap()): String {
        val config = langMap[lang] ?: langMap[defaultLang] ?: return "Missing Lang Config: $lang"
        
        // Check if it's a list (for random selection)
        val messageList = config.getStringList("messages.$key")
        var message = if (messageList.isNotEmpty()) {
            messageList.random()
        } else {
            config.getString("messages.$key") ?: return "Missing message: $key ($lang)"
        }

        for ((k, v) in params) {
            message = message.replace("{$k}", v)
        }

        return ChatColor.translateAlternateColorCodes('&', message)
    }

    fun getList(lang: String, key: String): List<String> {
        val config = langMap[lang] ?: langMap[defaultLang] ?: return emptyList()
        val list = config.getStringList("messages.$key")
        if (list.isNotEmpty()) return list.map { ChatColor.translateAlternateColorCodes('&', it) }
        
        // Fallback to single string as list
        val single = config.getString("messages.$key") ?: return emptyList()
        return listOf(ChatColor.translateAlternateColorCodes('&', single))
    }

    fun getTierName(lang: String, tier: String): String {
        val config = langMap[lang] ?: langMap[defaultLang] ?: return tier
        return config.getString("tiers.$tier") ?: tier
    }
}
