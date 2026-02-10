package jp.awabi2048.cccontent.features.arena.generator

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.structure.Structure
import java.io.File
import java.io.IOException
import kotlin.random.Random

data class ArenaTheme(
    val id: String,
    val path: String,
    val tileSize: Int,
    val structures: Map<ArenaStructureType, List<Structure>>
)

enum class ArenaStructureType(val keyword: String) {
    STRAIGHT("straight"),
    CORNER("corner"),
    CORRIDOR("corridor"),
    ENTRANCE("entrance"),
    GOAL("goal");

    companion object {
        fun fromFileName(fileName: String): ArenaStructureType? {
            val normalized = fileName.lowercase().removeSuffix(".nbt")
            return entries.firstOrNull { normalized == it.keyword || normalized.startsWith("${it.keyword}.") }
        }
    }
}

class ArenaThemeLoader(private val plugin: JavaPlugin) {
    private val themes = mutableMapOf<String, ArenaTheme>()

    fun load() {
        val configFile = File(plugin.dataFolder, "arena/theme.yml")
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            plugin.saveResource("arena/theme.yml", false)
        }

        val config = YamlConfiguration.loadConfiguration(configFile)
        val themesSection = config.getConfigurationSection("themes")
        themes.clear()

        if (themesSection == null) {
            plugin.logger.warning("[Arena] テーマ定義が空です: arena/theme.yml")
            return
        }

        val structureRoot = File(plugin.dataFolder, "structures/arena")
        if (!structureRoot.exists()) {
            structureRoot.mkdirs()
        }

        for (themeId in themesSection.getKeys(false)) {
            val path = themesSection.getString("$themeId.path") ?: themeId
            val tileSize = themesSection.getInt("$themeId.tileSize", 16)
            if (tileSize <= 0) {
                plugin.logger.warning("[Arena] tileSize が不正なためスキップします: $themeId")
                continue
            }

            val folder = File(structureRoot, path)
            if (!folder.exists()) {
                folder.mkdirs()
                plugin.logger.info("[Arena] テーマ用フォルダを作成しました: ${folder.absolutePath}")
            }

            val structures = loadStructures(folder)
            val missing = ArenaStructureType.entries.filter { structures[it].isNullOrEmpty() }
            if (missing.isNotEmpty()) {
                plugin.logger.warning("[Arena] 必須ストラクチャー不足のためスキップ: $themeId (${missing.joinToString { it.keyword }})")
                continue
            }

            themes[themeId] = ArenaTheme(themeId, path, tileSize, structures)
        }

        plugin.logger.info("[Arena] 読み込みテーマ数: ${themes.size}")
    }

    private fun loadStructures(folder: File): Map<ArenaStructureType, List<Structure>> {
        val result = mutableMapOf<ArenaStructureType, MutableList<Structure>>()
        ArenaStructureType.entries.forEach { result[it] = mutableListOf() }

        val files = folder.listFiles() ?: return result
        for (file in files) {
            if (!file.name.endsWith(".nbt", ignoreCase = true)) continue
            val type = ArenaStructureType.fromFileName(file.name) ?: continue
            try {
                val structure = Bukkit.getStructureManager().loadStructure(file)
                result[type]?.add(structure)
            } catch (e: IOException) {
                plugin.logger.warning("[Arena] ストラクチャー読み込み失敗: ${file.absolutePath} (${e.message})")
            } catch (e: Exception) {
                plugin.logger.warning("[Arena] ストラクチャー読み込み中にエラー: ${file.absolutePath} (${e.message})")
            }
        }

        return result
    }

    fun getTheme(id: String): ArenaTheme? = themes[id]

    fun getRandomTheme(random: Random = Random.Default): ArenaTheme? {
        if (themes.isEmpty()) return null
        return themes.values.random(random)
    }

    fun getThemeIds(): Set<String> = themes.keys
}
