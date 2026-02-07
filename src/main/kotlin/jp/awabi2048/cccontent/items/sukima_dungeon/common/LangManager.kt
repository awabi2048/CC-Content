package jp.awabi2048.cccontent.items.sukima_dungeon.common

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * SukimaDungeon 言語管理クラス
 * 元の SukimaDungeon プラグインの LangManager を再現
 */
class LangManager(private val plugin: JavaPlugin) {
    private val languageData: MutableMap<String, String> = mutableMapOf()
    private var currentLanguage: String = "ja_jp"
    
    init {
        loadLanguage(currentLanguage)
    }
    
    /**
     * 言語ファイルを読み込む
     * 統合言語ファイル（lang/ja_JP.yml）から読み込み
     */
    fun loadLanguage(language: String) {
        languageData.clear()
        currentLanguage = language
        
        val langDir = File(plugin.dataFolder, "lang").apply { mkdirs() }
        val langFile = File(langDir, "${language.uppercase()}.yml")
        
        // リソースからデフォルトファイルを抽出（存在しない場合）
        if (!langFile.exists()) {
            extractDefaultLanguageFile(language)
        }
        
        loadYamlFile(langFile)
        plugin.logger.info("[SukimaDungeon] 言語ファイルを読み込みました: $language")
    }
    
    /**
     * デフォルト言語ファイルをリソースから抽出
     */
    private fun extractDefaultLanguageFile(language: String) {
        val resourcePath = "lang/${language.uppercase()}.yml"
        val resourceStream = plugin.getResource(resourcePath)
        
        if (resourceStream != null) {
            val langDir = File(plugin.dataFolder, "lang").apply { mkdirs() }
            val langFile = File(langDir, "${language.uppercase()}.yml")
            langFile.parentFile?.mkdirs()
            
            resourceStream.use { input ->
                langFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            plugin.logger.info("[SukimaDungeon] デフォルト言語ファイルを抽出しました: $language")
        } else {
            plugin.logger.warning("[SukimaDungeon] リソースファイルが見つかりません: $resourcePath")
        }
    }
    
    /**
     * YAMLファイルを読み込んでフラットなマップに変換
     */
    private fun loadYamlFile(file: File) {
        try {
            val config = YamlConfiguration.loadConfiguration(file)
            flattenConfig(config, "")
        } catch (e: Exception) {
            plugin.logger.warning("[SukimaDungeon] 言語ファイルの読み込みに失敗しました: ${e.message}")
        }
    }
    
    /**
     * ネストされたYAML構造をフラットなキーに変換
     */
    private fun flattenConfig(section: org.bukkit.configuration.ConfigurationSection, prefix: String) {
        section.getKeys(false).forEach { key ->
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            val value = section.get(key)
            
            when (value) {
                is String -> languageData[fullKey] = value
                is org.bukkit.configuration.ConfigurationSection -> flattenConfig(value, fullKey)
                is List<*> -> {
                    // リストは改行区切りの文字列に結合
                    val listString = value.filterIsInstance<String>().joinToString("\n")
                    languageData[fullKey] = listString
                }
            }
        }
    }
    
    /**
     * メッセージを取得（プレースホルダー対応）
     * プレースホルダー形式: {key} 例: {tier}, {distance}, {cooldown}
     * 使用例: get("sukima.compass.activate", "tier" to 2, "duration" to 30)
     */
    fun get(key: String, vararg placeholders: Pair<String, Any?>): String {
        val template = languageData[key] ?: run {
            plugin.logger.warning("[SukimaDungeon] 翻訳キーが見つかりません: $key")
            return "§c[Missing: $key]"
        }
        
        var result = template
        for ((placeholderKey, value) in placeholders) {
            result = result.replace("{$placeholderKey}", value?.toString() ?: "null")
        }
        
        // カラーコード変換（& → §）
        result = result.replace('&', '§')
        return result
    }
    
    /**
     * メッセージを取得（リスト形式）
     */
    fun getList(key: String, vararg placeholders: Pair<String, Any?>): List<String> {
        val template = languageData[key] ?: run {
            plugin.logger.warning("[SukimaDungeon] 翻訳キーが見つかりません: $key")
            return listOf("§c[Missing: $key]")
        }
        
        var result = template
        for ((placeholderKey, value) in placeholders) {
            result = result.replace("{$placeholderKey}", value?.toString() ?: "null")
        }
        
        // 改行で分割し、カラーコードを変換
        return result.split("\n").map { it.replace('&', '§') }
    }
    
    /**
     * 現在の言語を取得
     */
    fun getCurrentLanguage(): String = currentLanguage
    
    /**
     * すべての言語データを取得（デバッグ用）
     */
    fun getAllData(): Map<String, String> = languageData.toMap()
}