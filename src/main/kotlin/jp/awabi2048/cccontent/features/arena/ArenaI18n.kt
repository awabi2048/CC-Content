package jp.awabi2048.cccontent.features.arena

import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object ArenaI18n {
    private const val DEFAULT_LOCALE = "ja_jp"

    private lateinit var plugin: JavaPlugin
    private val cache = mutableMapOf<String, YamlConfiguration>()

    fun initialize(plugin: JavaPlugin) {
        this.plugin = plugin
        cache.clear()
    }

    fun clearCache() {
        cache.clear()
    }

    fun text(sender: CommandSender?, key: String, fallback: String, vararg placeholders: Pair<String, Any?>): String {
        return text(sender as? Player, key, fallback, *placeholders)
    }

    fun text(player: Player?, key: String, fallback: String, vararg placeholders: Pair<String, Any?>): String {
        val locale = resolveLocale(player)
        val config = getConfig(locale)
        if (!config.isString(key)) {
            throw IllegalStateException("言語キーが見つからないか型が不正です: locale=$locale key=$key expected=String")
        }
        var value = config.getString(key)
            ?: throw IllegalStateException("言語キーの値取得に失敗しました: locale=$locale key=$key")
        for ((placeholderKey, placeholderValue) in placeholders) {
            value = value.replace("{$placeholderKey}", placeholderValue?.toString() ?: "null")
        }
        return value.replace('&', '§')
    }

    fun stringList(player: Player?, key: String, fallback: List<String>, vararg placeholders: Pair<String, Any?>): List<String> {
        val locale = resolveLocale(player)
        val config = getConfig(locale)
        if (!config.isList(key)) {
            throw IllegalStateException("言語キーが見つからないか型が不正です: locale=$locale key=$key expected=List")
        }
        val values = config.getStringList(key)
        return values.map { line ->
            var value = line
            for ((placeholderKey, placeholderValue) in placeholders) {
                value = value.replace("{$placeholderKey}", placeholderValue?.toString() ?: "null")
            }
            value.replace('&', '§')
        }
    }

    private fun resolveLocale(player: Player?): String {
        val raw = player?.locale?.lowercase()?.replace('-', '_') ?: DEFAULT_LOCALE
        return when {
            raw == "ja_jp" -> "ja_jp"
            raw == "en_us" -> "en_us"
            raw.startsWith("ja") -> "ja_jp"
            raw.startsWith("en") -> "en_us"
            else -> DEFAULT_LOCALE
        }
    }

    private fun getConfig(locale: String): YamlConfiguration {
        val normalized = locale.lowercase()
        cache[normalized]?.let { return it }

        if (!::plugin.isInitialized) {
            throw IllegalStateException("ArenaI18n が初期化されていません")
        }

        val fromDataFolder = File(plugin.dataFolder, "lang/$normalized.yml")
        if (fromDataFolder.exists()) {
            return YamlConfiguration.loadConfiguration(fromDataFolder).also {
                cache[normalized] = it
            }
        }

        val fromResource = plugin.getResource("lang/$normalized.yml")
            ?: throw IllegalStateException("言語ファイルが見つかりません: lang/$normalized.yml")
        val config = fromResource.use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                YamlConfiguration.loadConfiguration(reader)
            }
        }
        cache[normalized] = config
        return config
    }
}
