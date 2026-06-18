package jp.awabi2048.cccontent.features.sukima_dungeon.generator

import jp.awabi2048.cccontent.structure.LoadedSchemStructure
import jp.awabi2048.cccontent.structure.SchemStructureService
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class StructureLoader(val plugin: JavaPlugin) {
    private val themes = mutableMapOf<String, Theme>()
    private val structureService = SchemStructureService(plugin)

    fun loadThemes() {
        val themeFile = File(plugin.dataFolder, "config/sukima_dungeon/theme.yml")
        if (!themeFile.exists()) {
            themeFile.parentFile.mkdirs()
            plugin.saveResource("config/sukima_dungeon/theme.yml", false)
        }
        val config = YamlConfiguration.loadConfiguration(themeFile)

        themes.clear()
        val themesSection = config.getConfigurationSection("themes") ?: return

        val baseStructureFolder = File(plugin.dataFolder, "structures/sukima_dungeon")
        if (!baseStructureFolder.exists()) {
            baseStructureFolder.mkdirs()
        }

        for (key in themesSection.getKeys(false)) {
            val path = themesSection.getString("$key.path") ?: key
            val iconStr = themesSection.getString("$key.icon") ?: "GRASS_BLOCK"
            val icon = Material.matchMaterial(iconStr) ?: Material.GRASS_BLOCK
            val time = if (themesSection.contains("$key.time")) themesSection.getLong("$key.time") else null
            val gravity = themesSection.getDouble("$key.gravity", 1.0)
            val voidYLimit = if (themesSection.contains("$key.void_y_limit")) themesSection.getDouble("$key.void_y_limit") else null
            val requiredTier = themesSection.getInt("$key.required_tier", 1)

            if (themesSection.contains("$key.tileSize")) {
                plugin.logger.warning("[SukimaDungeon] theme.yml の $key.tileSize は廃止されました（ストラクチャーサイズから自動算出）。エントリを無視します。")
            }

            val themeFolder = File(baseStructureFolder, path)
            if (!themeFolder.exists()) {
                themeFolder.mkdirs()
            }

            val structures = loadStructuresForTheme(themeFolder)
            val tileSize = structures.values.flatten().maxOfOrNull { maxOf(it.size.x, it.size.z) } ?: 16
            val theme = Theme(key, icon, tileSize, time, gravity, voidYLimit, requiredTier, structures)
            themes[key] = theme
        }

        validateThemes()
    }

    private fun loadStructuresForTheme(folder: File): Map<StructureType, List<LoadedSchemStructure>> {
        val structures = mutableMapOf<StructureType, MutableList<LoadedSchemStructure>>()
        for (type in StructureType.values()) {
            structures[type] = mutableListOf()
        }

        for (file in structureService.listSchemFiles(folder)) {
            val matchedType = StructureType.fromFileName(file.name) ?: continue
            val structure = structureService.load(file) ?: continue
            structures[matchedType]?.add(structure)
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
            plugin.logger.warning(
                "[SukimaDungeon] Missing schem structures: " +
                    errorThemes.entries.joinToString("; ") { (theme, types) ->
                        "$theme=${types.joinToString(",") { it.keyword }}"
                    }
            )
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
            val name = fileName.lowercase().removeSuffix(".schem")
            return values().find { type ->
                name == type.keyword || name.startsWith("${type.keyword}.")
            }
        }
    }
}
