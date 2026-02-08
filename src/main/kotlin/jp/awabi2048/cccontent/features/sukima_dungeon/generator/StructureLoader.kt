package jp.awabi2048.cccontent.features.sukima_dungeon.generator

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.structure.Structure
import java.io.File
import java.io.IOException

class StructureLoader(val plugin: JavaPlugin) {
    private val themes = mutableMapOf<String, Theme>()

    fun loadThemes() {
        val themeFile = File(plugin.dataFolder, "sukima/theme.yml")
        if (!themeFile.exists()) {
            themeFile.parentFile.mkdirs()
            plugin.saveResource("sukima/theme.yml", false)
        }
        val config = YamlConfiguration.loadConfiguration(themeFile)
        
        themes.clear()
        val themesSection = config.getConfigurationSection("themes") ?: return

        val baseStructureFolder = File(plugin.dataFolder, "structures")
        if (!baseStructureFolder.exists()) {
            baseStructureFolder.mkdirs()
        }

        for (key in themesSection.getKeys(false)) {

            // path defaults to key if not specified
            val path = themesSection.getString("$key.path") ?: key 
            val iconStr = themesSection.getString("$key.icon") ?: "GRASS_BLOCK"
            val icon = Material.matchMaterial(iconStr) ?: Material.GRASS_BLOCK
            val tileSize = themesSection.getInt("$key.tileSize", 16)
            val time = if (themesSection.contains("$key.time")) themesSection.getLong("$key.time") else null
            val gravity = themesSection.getDouble("$key.gravity", 1.0)
            val voidYLimit = if (themesSection.contains("$key.void_y_limit")) themesSection.getDouble("$key.void_y_limit") else null
            val requiredTier = themesSection.getInt("$key.required_tier", 1)
            
            val themeFolder = File(baseStructureFolder, path)
            if (!themeFolder.exists()) {
                themeFolder.mkdirs()
                plugin.logger.info("Created theme directory: ${themeFolder.absolutePath}")
            }

            val structures = loadStructuresForTheme(themeFolder)
            val theme = Theme(key, icon, tileSize, time, gravity, voidYLimit, requiredTier, structures)
            themes[key] = theme
            plugin.logger.info("Loaded theme: $key with ${structures.values.sumOf { it.size }} structures")
        }
        
        validateThemes()
    }

    private fun loadStructuresForTheme(folder: File): Map<StructureType, List<Structure>> {
        val structures = mutableMapOf<StructureType, MutableList<Structure>>()
        
        // Initialize lists
        for (type in StructureType.values()) {
            structures[type] = mutableListOf()
        }

        val files = folder.listFiles() ?: return emptyMap()

        for (file in files) {
            if (!file.name.endsWith(".nbt")) continue
            
            val matchedType = StructureType.fromFileName(file.name)

            if (matchedType != null) {
                try {
                    val structure = Bukkit.getStructureManager().loadStructure(file)
                    structures[matchedType]?.add(structure)
                } catch (e: IOException) {
                    plugin.logger.severe("ストラクチャーの読み込みに失敗しました: ${file.name}")
                    e.printStackTrace()
                } catch (e: Exception) {
                    plugin.logger.severe("ストラクチャー読み込み中に予期しないエラーが発生しました: ${file.name}")
                    e.printStackTrace()
                }
            }
        }
        
        return structures
    }

    fun getTheme(name: String): Theme? {
        return themes[name]
    }
    
    fun getDefaultTheme(): Theme? {
        return themes["default"] ?: themes.values.firstOrNull()
    }

    fun getThemeNames(): Set<String> {
        return themes.keys
    }

    fun validateThemes() {
        val invalidThemeKeys = mutableListOf<String>()
        val errorThemes = mutableMapOf<String, List<StructureType>>()
        
        for ((key, theme) in themes) {
            val missingTypes = mutableListOf<StructureType>()
            for (type in StructureType.values()) {
                if (theme.structures[type].isNullOrEmpty()) {
                    // MINIBOSS and REST are optional for now (mock implementation)
                    if (type != StructureType.MINIBOSS && type != StructureType.REST) {
                        missingTypes.add(type)
                    }
                }
            }
            if (missingTypes.isNotEmpty()) {
                errorThemes[theme.getDisplayName()] = missingTypes
                invalidThemeKeys.add(key)
            }
        }

        for (key in invalidThemeKeys) {
            themes.remove(key)
        }

        if (errorThemes.isNotEmpty()) {
            plugin.logger.severe("--- ストラクチャー読み込みエラー ---")
            for ((themeName, missing) in errorThemes) {
                val missingStr = missing.joinToString(", ") { it.keyword }
                plugin.logger.severe("テーマ '${themeName}' は以下のストラクチャーが不足しているため、読み込まれませんでした: $missingStr")
            }
            plugin.logger.severe("--------------------------------")
        }
    }
}

enum class StructureType(val keyword: String) {
    STRAIGHT("straight"),
    CORNER("corner"),
    T_SHAPE("t_shape"),
    CROSS("cross"),
    DEAD_END("deadend"),
    TRAP("trap"),
    ENTRANCE("entrance"),
    MINIBOSS("miniboss"),
    REST("rest");

    companion object {
        fun fromFileName(fileName: String): StructureType? {
            val name = fileName.lowercase().removeSuffix(".nbt")
            return values().find { type ->
                name == type.keyword || name.startsWith("${type.keyword}.")
            }
        }
    }
}
