package jp.awabi2048.cccontent.items

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object CustomItemI18n {
    private const val DEFAULT_LOCALE = "ja_jp"

    private lateinit var plugin: JavaPlugin
    private val cache = mutableMapOf<String, YamlConfiguration>()

    fun initialize(plugin: JavaPlugin) {
        this.plugin = plugin
        cache.clear()
    }

    fun text(player: Player?, key: String, fallback: String): String {
        val locale = resolveLocale(player)
        val primary = getConfig(locale)?.getString(key)
        val fallbackValue = if (locale != DEFAULT_LOCALE) getConfig(DEFAULT_LOCALE)?.getString(key) else null
        val value = primary ?: fallbackValue ?: fallback
        return value.replace('&', '§')
    }

    fun list(player: Player?, key: String, fallback: List<String>): List<String> {
        val locale = resolveLocale(player)
        val value = getConfig(locale)?.getStringList(key).orEmpty()
        if (value.isNotEmpty()) {
            return value.map { it.replace('&', '§') }
        }

        if (locale != DEFAULT_LOCALE) {
            val fallbackValue = getConfig(DEFAULT_LOCALE)?.getStringList(key).orEmpty()
            if (fallbackValue.isNotEmpty()) {
                return fallbackValue.map { it.replace('&', '§') }
            }
        }

        return fallback
    }

    fun resolveLocale(player: Player?): String {
        val raw = player?.locale?.lowercase()?.replace('-', '_') ?: DEFAULT_LOCALE
        return when {
            raw == "ja_jp" -> "ja_jp"
            raw == "en_us" -> "en_us"
            raw.startsWith("ja") -> "ja_jp"
            raw.startsWith("en") -> "en_us"
            else -> DEFAULT_LOCALE
        }
    }

    private fun getConfig(locale: String): YamlConfiguration? {
        val normalized = locale.lowercase()
        cache[normalized]?.let { return it }

        if (!::plugin.isInitialized) {
            return null
        }

        val fromDataFolder = File(plugin.dataFolder, "lang/$normalized.yml")
        if (fromDataFolder.exists()) {
            return YamlConfiguration.loadConfiguration(fromDataFolder).also {
                cache[normalized] = it
            }
        }

        val fromResource = plugin.getResource("lang/$normalized.yml") ?: return null
        val config = fromResource.use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                YamlConfiguration.loadConfiguration(reader)
            }
        }
        cache[normalized] = config
        return config
    }
}
