package jp.awabi2048.cccontent.features.sukima_dungeon.generator

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * テーマとストラクチャーを読み込むクラス
 */
class StructureLoader(private val plugin: JavaPlugin) {
    
    private val loadedThemes: MutableMap<String, Theme> = mutableMapOf()
    private val structureCache: MutableMap<String, Map<StructureType, List<String>>> = mutableMapOf()
    
    /**
     * すべてのテーマを読み込む
     */
    fun loadThemes(): Map<String, Theme> {
        loadedThemes.clear()
        
        try {
            val configFile = File(plugin.dataFolder, "config/sukima/theme.yml")
            if (!configFile.exists()) {
                plugin.logger.warning("[SukimaDungeon] theme.yml が見つかりません")
                return emptyMap()
            }
            
            val config = YamlConfiguration.loadConfiguration(configFile)
            val themesSection = config.getConfigurationSection("themes") ?: return emptyMap()
            
            for (themeId in themesSection.getKeys(false)) {
                val themeSection = themesSection.getConfigurationSection(themeId) ?: continue
                val themeData = themeSection.getValues(false)
                
                val theme = Theme.fromConfigMap(themeId, themeData)
                
                // ストラクチャーを読み込み
                val structures = loadThemeStructures(theme)
                val completeTheme = theme.copy(structures = structures)
                
                loadedThemes[themeId] = completeTheme
                plugin.logger.info("[SukimaDungeon] テーマを読み込みました: $themeId")
            }
        } catch (e: Exception) {
            plugin.logger.warning("[SukimaDungeon] テーマ読み込みエラー: ${e.message}")
            e.printStackTrace()
        }
        
        return loadedThemes
    }
    
    /**
     * テーマのストラクチャーを読み込む
     */
    fun loadThemeStructures(theme: Theme): Map<StructureType, List<String>> {
        val cacheKey = theme.id
        if (cacheKey in structureCache) {
            return structureCache[cacheKey]!!
        }
        
        val structures = mutableMapOf<StructureType, List<String>>()
        val structureDir = File(plugin.dataFolder, "structures/sukima_dungeon/${theme.path}")
        
        if (!structureDir.exists()) {
            plugin.logger.warning("[SukimaDungeon] ストラクチャーディレクトリが見つかりません: ${structureDir.absolutePath}")
            return structures
        }
        
        // ストラクチャータイプごとにサブディレクトリを確認
        for (structureType in StructureType.entries) {
            val typeDir = File(structureDir, structureType.name.lowercase())
            if (typeDir.exists() && typeDir.isDirectory) {
                val nbtFiles = typeDir.listFiles { file ->
                    file.isFile && (file.extension == "nbt" || file.extension == "NBT")
                }?.map { it.name } ?: emptyList()
                
                if (nbtFiles.isNotEmpty()) {
                    structures[structureType] = nbtFiles
                    plugin.logger.fine("[SukimaDungeon] ${theme.id}/${structureType.name}: ${nbtFiles.size}個のストラクチャーを読み込み")
                }
            }
        }
        
        // フォールバック: MINIBOSS と TRAP がない場合は CROSS を使用
        if (!structures.containsKey(StructureType.MINIBOSS)) {
            structures[StructureType.MINIBOSS] = structures[StructureType.CROSS] ?: emptyList()
        }
        if (!structures.containsKey(StructureType.TRAP)) {
            structures[StructureType.TRAP] = structures[StructureType.CROSS] ?: emptyList()
        }
        
        structureCache[cacheKey] = structures
        return structures
    }
    
    /**
     * ランダムなストラクチャーファイルを選択
     */
    fun selectRandomStructure(theme: Theme, type: StructureType): String? {
        val structures = theme.structures[type] ?: return null
        if (structures.isEmpty()) return null
        
        return structures.random()
    }
    
    /**
     * テーマを取得
     */
    fun getTheme(id: String): Theme? {
        return loadedThemes[id]
    }
    
    /**
     * 読み込み済みテーマ一覧を取得
     */
    fun getAllThemes(): Collection<Theme> {
        return loadedThemes.values
    }
    
    /**
     * テーマをリロード
     */
    fun reloadThemes() {
        structureCache.clear()
        loadThemes()
    }
}