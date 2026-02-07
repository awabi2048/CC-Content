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
        val logger = plugin.logger
        val langDir = File(plugin.dataFolder, "lang")
        logger.info("[LangManager] Loading language files from: ${langDir.absolutePath}")
        if (!langDir.exists()) {
            langDir.mkdirs()
            logger.info("[LangManager] Created lang directory")
        }

        // Save default resources
        saveDefaultLang(plugin, "en_us.yml")
        saveDefaultLang(plugin, "ja_jp.yml")

         // Load all yml files in lang dir
         langMap.clear()
         // Load only specific language files
         val langFiles = listOf("ja_jp.yml", "en_us.yml")
         var loadedCount = 0
         langFiles.forEach { fileName ->
             val file = File(langDir, fileName)
             if (file.exists()) {
                 try {
                     val langName = fileName.removeSuffix(".yml").lowercase()
                     val config = YamlConfiguration.loadConfiguration(file)
                     langMap[langName] = config
                     loadedCount++
                     logger.info("[LangManager] Loaded language file: $fileName -> $langName")
                 } catch (e: Exception) {
                     logger.warning("[LangManager] Failed to load language file $fileName: ${e.message}")
                 }
             } else {
                 logger.warning("[LangManager] Language file not found: $file")
             }
         }
         logger.info("[LangManager] Loaded $loadedCount language files. Available keys: ${langMap.keys}")
    }

    private fun saveDefaultLang(plugin: JavaPlugin, fileName: String) {
        val file = File(plugin.dataFolder, "lang/$fileName")
        if (!file.exists()) {
            plugin.saveResource("lang/$fileName", false)
        }
    }

     fun getMessage(lang: String, key: String, params: Map<String, String> = emptyMap()): String {
         val normalizedLang = lang.lowercase()
         val config = langMap[normalizedLang] ?: langMap[defaultLang] ?: return "Missing Lang Config: $lang (available: ${langMap.keys})"
        
        // Check if it's a list (for random selection)
        val messageList = config.getStringList("sukima_dungeon.$key")
        var message = if (messageList.isNotEmpty()) {
            messageList.random()
        } else {
            config.getString("sukima_dungeon.$key") ?: return "Missing message: $key ($lang)"
        }

        for ((k, v) in params) {
            message = message.replace("{$k}", v)
        }

        return ChatColor.translateAlternateColorCodes('&', message)
    }

     fun getList(lang: String, key: String): List<String> {
         val normalizedLang = lang.lowercase()
         val config = langMap[normalizedLang] ?: langMap[defaultLang] ?: return emptyList()
        val list = config.getStringList("sukima_dungeon.$key")
        if (list.isNotEmpty()) return list.map { ChatColor.translateAlternateColorCodes('&', it) }
        
        // Fallback to single string as list
        val single = config.getString("sukima_dungeon.$key") ?: return emptyList()
        return listOf(ChatColor.translateAlternateColorCodes('&', single))
    }

     fun getTierName(lang: String, tier: String): String {
         val normalizedLang = lang.lowercase()
         val config = langMap[normalizedLang] ?: langMap[defaultLang] ?: return tier
        return config.getString("sukima_dungeon.tiers.$tier") ?: tier
    }
}
