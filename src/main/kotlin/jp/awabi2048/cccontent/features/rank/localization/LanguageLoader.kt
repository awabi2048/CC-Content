package jp.awabi2048.cccontent.features.rank.localization

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import org.bukkit.plugin.java.JavaPlugin

class LanguageLoader(
    private val plugin: JavaPlugin,
    private val language: String = "ja_jp"
) {
    private val normalizedLanguage = language.lowercase()

    fun getMessage(key: String, vararg placeholders: Pair<String, Any?>): String {
        return CCSystem.getAPI().getI18nString(normalizedLanguage, key, placeholdersMap(*placeholders)).replace('&', '§')
    }

    fun getRawMessage(key: String): String {
        return CCSystem.getAPI().getI18nString(normalizedLanguage, key).replace('&', '§')
    }

    fun getStringList(key: String): List<String> {
        return CCSystem.getAPI().getI18nStringList(normalizedLanguage, key).map { it.replace('&', '§') }
    }

    private fun placeholdersMap(vararg placeholders: Pair<String, Any?>): Map<String, Any> {
        return placeholders.associate { (key, value) -> key to (value ?: "null") }
    }
    fun getMessage(key: LocalizationKey<String>, vararg placeholders: Pair<String, Any?>): String =
        CCSystem.getAPI().getLocalized(normalizedLanguage, key, placeholdersMap(*placeholders)).replace('&', '§')

    fun getRawMessage(key: LocalizationKey<String>): String =
        CCSystem.getAPI().getLocalized(normalizedLanguage, key).replace('&', '§')

    fun getStringList(key: LocalizationKey<List<String>>): List<String> =
        CCSystem.getAPI().getLocalized(normalizedLanguage, key).map { it.replace('&', '§') }
}
