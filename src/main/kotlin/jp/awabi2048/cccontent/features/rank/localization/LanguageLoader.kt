package jp.awabi2048.cccontent.features.rank.localization

import com.awabi2048.ccsystem.CCSystem
import org.bukkit.plugin.java.JavaPlugin

class LanguageLoader(
    private val plugin: JavaPlugin,
    private val language: String = "ja_jp"
) {
    private val sourceId = "CC-Content:rank"
    private val normalizedLanguage = language.lowercase()

    fun getMessage(key: String, vararg placeholders: Pair<String, Any?>): String {
        return CCSystem.getAPI().getI18nString(sourceId, normalizedLanguage, key, placeholdersMap(*placeholders)).replace('&', '§')
    }

    fun getRawMessage(key: String): String {
        return CCSystem.getAPI().getI18nString(sourceId, normalizedLanguage, key).replace('&', '§')
    }

    fun getStringList(key: String): List<String> {
        return CCSystem.getAPI().getI18nStringList(sourceId, normalizedLanguage, key).map { it.replace('&', '§') }
    }

    private fun placeholdersMap(vararg placeholders: Pair<String, Any?>): Map<String, Any> {
        return placeholders.associate { (key, value) -> key to (value ?: "null") }
    }
}
