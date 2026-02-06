package jp.awabi2048.cccontent.features.rank.localization

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * 言語ファイルを読み込むクラス
 */
class LanguageLoader(
    private val plugin: JavaPlugin,
    private val language: String = "ja_JP"
) {
    private val languageData: MutableMap<String, String> = mutableMapOf()
    private val langFile: File
    
    init {
        // リソースディレクトリから言語ファイルを抽出
        val langDir = File(plugin.dataFolder, "lang").apply { mkdirs() }
        langFile = File(langDir, "$language.yml")
        
        // ファイルが存在しない場合はリソースから抽出
        if (!langFile.exists()) {
            extractDefaultLanguageFile()
        }
        
        loadLanguageFile()
    }
    
    /**
     * リソースから言語ファイルを抽出
     */
    private fun extractDefaultLanguageFile() {
        val resourceStream = plugin.getResource("lang/$language.yml")
        if (resourceStream != null) {
            langFile.parentFile?.mkdirs()
            resourceStream.use { input ->
                langFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
    
    /**
     * 言語ファイルを読み込む
     */
    private fun loadLanguageFile() {
        try {
            val config = YamlConfiguration.loadConfiguration(langFile)
            flattenConfig(config, "")
        } catch (e: Exception) {
            plugin.logger.warning("言語ファイルの読み込みに失敗しました: ${e.message}")
        }
    }
    
    /**
     * ネストされたYAML構造をフラットな構造に変換
     */
    private fun flattenConfig(section: org.bukkit.configuration.ConfigurationSection, prefix: String) {
        section.getKeys(false).forEach { key ->
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            val value = section.get(key)
            
            when (value) {
                is String -> languageData[fullKey] = value
                is org.bukkit.configuration.ConfigurationSection -> {
                    flattenConfig(value, fullKey)
                }
            }
        }
    }
    
    /**
     * メッセージを取得
     */
    fun getMessage(key: String, vararg args: Any?): String {
        val template = languageData[key] ?: return "§c[Missing: $key]"
        var result = template
        args.forEachIndexed { index, arg ->
            result = result.replace("%${index + 1}\$s", arg?.toString() ?: "null")
            result = result.replace("%s", arg?.toString() ?: "null")
        }
        return result
    }
    
    /**
     * メッセージを直接取得（翻訳キーなし）
     */
    fun getRawMessage(key: String): String {
        return languageData[key] ?: "§c[Missing: $key]"
    }
}
