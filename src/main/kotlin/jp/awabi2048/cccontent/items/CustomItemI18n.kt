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
        val config = getConfig(locale)
        if (!config.isString(key)) {
            throw IllegalStateException("言語キーが見つからないか型が不正です: locale=$locale key=$key expected=String")
        }
        val value = config.getString(key)
            ?: throw IllegalStateException("言語キーの値取得に失敗しました: locale=$locale key=$key")
        return value.replace('&', '§')
    }

    fun list(player: Player?, key: String, fallback: List<String>): List<String> {
        val locale = resolveLocale(player)
        val config = getConfig(locale)
        if (!config.isList(key)) {
            throw IllegalStateException("言語キーが見つからないか型が不正です: locale=$locale key=$key expected=List")
        }
        return config.getStringList(key).map { it.replace('&', '§') }
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

    private fun getConfig(locale: String): YamlConfiguration {
        val normalized = locale.lowercase()
        cache[normalized]?.let { return it }

        if (!::plugin.isInitialized) {
            throw IllegalStateException("CustomItemI18n が初期化されていません")
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
