package jp.awabi2048.cccontent.features.rank.localization

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * 言語ファイルを読み込むクラス
 */
class LanguageLoader(
    private val plugin: JavaPlugin,
    private val language: String = "ja_JP"
) {
    private val languageData: MutableMap<String, String> = mutableMapOf()
    private val bundledLanguageData: MutableMap<String, String> = mutableMapOf()
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

        loadBundledLanguageData()
        
        loadLanguageFile()
    }

    private fun resolveLanguageFile(langDir: File): File {
        val lowerCaseFile = File(langDir, "$normalizedLanguage.yml")
        if (lowerCaseFile.exists()) {
            return lowerCaseFile
        }

        val exactCaseFile = File(langDir, "$language.yml")
        if (exactCaseFile.exists()) {
            return exactCaseFile
        }

        return lowerCaseFile
    }

    private fun resourcePathCandidates(): List<String> {
        return listOf(
            "lang/$normalizedLanguage.yml",
            "lang/$language.yml"
        ).distinct()
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

            var mergedCount = 0
            for ((key, value) in bundledLanguageData) {
                if (!config.isSet(key)) {
                    config.set(key, value)
                    mergedCount++
                }
            }

            if (mergedCount > 0) {
                config.save(langFile)
                plugin.logger.info("言語ファイルの欠損キーを補完しました: $mergedCount 件 (${langFile.name})")
            }

            flattenConfig(config, "")
            plugin.logger.info("言語ファイルを読み込みました: ${langFile.absolutePath}")
        } catch (e: Exception) {
            plugin.logger.warning("言語ファイルの読み込みに失敗しました: ${e.message}")
        }
    }

    private fun loadBundledLanguageData() {
        bundledLanguageData.clear()

        val config = resourcePathCandidates().firstNotNullOfOrNull { candidate ->
            val stream = plugin.getResource(candidate) ?: return@firstNotNullOfOrNull null
            stream.use { input ->
                InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                    YamlConfiguration.loadConfiguration(reader)
                }
            }
        } ?: return

        flattenConfig(config, "", bundledLanguageData)
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
        var resolvedTemplate = template

        if (resolvedTemplate == null) {
            resolvedTemplate = bundledLanguageData[key]
        }

        if (resolvedTemplate == null) {
            // デバッグログ：見つからないキーを記録
            plugin.logger.warning("翻訳キーが見つかりません: $key (全キー数: ${languageData.size})")
            return "§c[Missing: $key]"
        }
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
        return languageData[key] ?: "§c[Missing: $key]"
    }

    /**
     * リスト形式のメッセージを取得
     */
    fun getStringList(key: String): List<String> {
        val config = YamlConfiguration.loadConfiguration(langFile)
        val list = config.getStringList(key)
        if (list.isEmpty()) {
            // バンドルされたリソースから取得
            val bundledConfig = resourcePathCandidates().firstNotNullOfOrNull { candidate ->
                val stream = plugin.getResource(candidate) ?: return@firstNotNullOfOrNull null
                stream.use { input ->
                    InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                        YamlConfiguration.loadConfiguration(reader)
                    }
                }
            }
            return bundledConfig?.getStringList(key)?.map { it.replace('&', '§') } ?: emptyList()
        }
        return list.map { it.replace('&', '§') }
    }
}
