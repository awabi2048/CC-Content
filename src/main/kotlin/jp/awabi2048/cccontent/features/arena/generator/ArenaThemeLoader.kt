package jp.awabi2048.cccontent.features.arena.generator

import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.structure.Structure
import java.io.File
import java.io.IOException
import kotlin.random.Random

data class ArenaTheme(
    val id: String,
    val path: String,
    val gridPitch: Int,
    val structures: Map<ArenaStructureType, List<ArenaStructureTemplate>>
)

data class ArenaStructureSize(
    val x: Int,
    val y: Int,
    val z: Int
)

data class ArenaStructureTemplate(
    val name: String,
    val structure: Structure,
    val size: ArenaStructureSize
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

    fun load(featureInitLogger: FeatureInitializationLogger? = null) {
        themes.clear()
        val warnings = mutableListOf<String>()

        val structureRoot = File(plugin.dataFolder, "structures/arena")
        if (!structureRoot.exists()) {
            structureRoot.mkdirs()
        }

        val themeFolders = structureRoot.listFiles { file -> file.isDirectory }?.sortedBy { it.name } ?: emptyList()
        if (themeFolders.isEmpty()) {
            val warning = "[Arena] テーマ用ストラクチャーフォルダが見つかりません: structures/arena"
            plugin.logger.warning(warning)
            warnings.add(warning)
        }

        for (folder in themeFolders) {
            val themeId = folder.name
            val structures = loadStructures(folder)
            val missing = ArenaStructureType.entries.filter { structures[it].isNullOrEmpty() }
            if (missing.isNotEmpty()) {
                val warning = "[Arena] 必須ストラクチャー不足のためスキップ: $themeId (${missing.joinToString { it.keyword }})"
                plugin.logger.warning(warning)
                warnings.add(warning)
                continue
            }

            val templates = structures.values.flatten()
            if (templates.isEmpty()) {
                val warning = "[Arena] 有効なストラクチャーが見つからないためスキップ: $themeId"
                plugin.logger.warning(warning)
                warnings.add(warning)
                continue
            }

            val gridPitch = templates.maxOfOrNull { maxOf(it.size.x, it.size.z) } ?: 1

            themes[themeId] = ArenaTheme(themeId, folder.name, gridPitch, structures)
        }

        // FeatureInitializationLogger に情報を送信
        featureInitLogger?.apply {
            if (themes.isEmpty()) {
                setStatus("Arena", FeatureInitializationLogger.Status.FAILURE)
                addDetailMessage("Arena", "[Arena] テーマ読み込み失敗: 有効なテーマが0個です")
            } else if (warnings.isNotEmpty()) {
                setStatus("Arena", FeatureInitializationLogger.Status.WARNING)
                addSummaryMessage("Arena", "テーマ${themes.size}種")
                warnings.forEach { addDetailMessage("Arena", it) }
            } else {
                setStatus("Arena", FeatureInitializationLogger.Status.SUCCESS)
                addSummaryMessage("Arena", "テーマ${themes.size}種")
            }
        }
    }

    private fun loadStructures(folder: File): Map<ArenaStructureType, List<ArenaStructureTemplate>> {
        val result = mutableMapOf<ArenaStructureType, MutableList<ArenaStructureTemplate>>()
        ArenaStructureType.entries.forEach { result[it] = mutableListOf() }

        val files = folder.listFiles() ?: return result
        for (file in files) {
            if (!file.name.endsWith(".nbt", ignoreCase = true)) continue
            val type = ArenaStructureType.fromFileName(file.name) ?: continue
            try {
                val structure = Bukkit.getStructureManager().loadStructure(file)
                val blockSize = structure.size
                val size = ArenaStructureSize(blockSize.blockX, blockSize.blockY, blockSize.blockZ)
                if (size.x <= 0 || size.y <= 0 || size.z <= 0) {
                    plugin.logger.warning("[Arena] 不正なサイズのためスキップ: ${file.name} size=${size.x}*${size.y}*${size.z}")
                    continue
                }
                result[type]?.add(
                    ArenaStructureTemplate(
                        name = file.name,
                        structure = structure,
                        size = size
                    )
                )
            } catch (e: IOException) {
                plugin.logger.warning("[Arena] ストラクチャー読み込み失敗: ${file.name} (${e.message})")
            } catch (e: Exception) {
                plugin.logger.warning("[Arena] ストラクチャー処理中に例外: ${file.name} (${e.message})")
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
