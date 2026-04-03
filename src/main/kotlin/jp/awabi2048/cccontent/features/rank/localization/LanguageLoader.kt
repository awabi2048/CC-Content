package jp.awabi2048.cccontent.features.rank.localization

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * 言語ファイルを読み込むクラス
 */
class LanguageLoader(
    private val plugin: JavaPlugin,
    private val language: String = "ja_jp"
) {
    private val languageData: MutableMap<String, String> = mutableMapOf()
    private val langFile: File
    private val normalizedLanguage = language.lowercase()
    
    init {
        // リソースディレクトリから言語ファイルを抽出
        val langDir = File(plugin.dataFolder, "lang").apply { mkdirs() }
        langFile = resolveLanguageFile(langDir)
        
        // ファイルが存在しない場合はリソースから抽出
        if (!langFile.exists()) {
            extractDefaultLanguageFile()
        }

        if (!langFile.exists()) {
            throw IllegalStateException("言語ファイルが見つかりません: ${langFile.absolutePath}")
        }
        
        loadLanguageFile()
    }

    private fun resolveLanguageFile(langDir: File): File {
        val lowerCaseFile = File(langDir, "$normalizedLanguage.yml")
        return lowerCaseFile
    }

    private fun resourcePathCandidates(): List<String> {
        return listOf("lang/$normalizedLanguage.yml")
    }
    
    /**
     * リソースから言語ファイルを抽出
     */
    private fun extractDefaultLanguageFile() {
        val resourceStream = resourcePathCandidates()
            .firstNotNullOfOrNull { candidate -> plugin.getResource(candidate) }
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
            languageData.clear()
            val config = YamlConfiguration.loadConfiguration(langFile)
            flattenConfig(config, "")
        } catch (e: Exception) {
            throw IllegalStateException("言語ファイルの読み込みに失敗しました: ${e.message}", e)
        }
    }
    
    /**
     * ネストされたYAML構造をフラットな構造に変換
     */
    private fun flattenConfig(
        section: org.bukkit.configuration.ConfigurationSection,
        prefix: String,
        target: MutableMap<String, String> = languageData
    ) {
        section.getKeys(false).forEach { key ->
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            val value = section.get(key)
            
            when (value) {
                is String -> target[fullKey] = value
                is org.bukkit.configuration.ConfigurationSection -> {
                    flattenConfig(value, fullKey, target)
                }
            }
        }
    }
    
    /**
     * メッセージを取得（プレースホルダー対応）
     * プレースホルダー形式: {key} 例: {rank}, {profession}, {skill}
     * 使用例: getMessage("message.rank_up", "rank" to "Pioneer")
     */
    fun getMessage(key: String, vararg placeholders: Pair<String, Any?>): String {
        val template = languageData[key]
        var resolvedTemplate = template ?: throw IllegalStateException("翻訳キーが見つかりません: $key")
        var result: String = resolvedTemplate
        for ((placeholderKey, value) in placeholders) {
            result = result.replace("{$placeholderKey}", value?.toString() ?: "null")
        }
        // カラーコード変換（& → §）
        result = result.replace('&', '§')
        return result
    }
    
    /**
     * メッセージを直接取得（翻訳キーなし）
     */
    fun getRawMessage(key: String): String {
        return languageData[key] ?: throw IllegalStateException("翻訳キーが見つかりません: $key")
    }

    /**
     * リスト形式のメッセージを取得
     */
    fun getStringList(key: String): List<String> {
        val config = YamlConfiguration.loadConfiguration(langFile)
        if (!config.isList(key)) {
            throw IllegalStateException("翻訳リストキーが見つからないか型が不正です: $key")
        }
        return config.getStringList(key).map { it.replace('&', '§') }
    }
}
