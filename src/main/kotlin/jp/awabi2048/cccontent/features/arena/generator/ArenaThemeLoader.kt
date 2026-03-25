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
    val staticStructures: Map<ArenaStructureType, List<ArenaStaticStructureVariant>>,
    val animatedStructures: Map<ArenaStructureType, List<ArenaAnimatedStructureVariant>>
)

data class ArenaStaticStructureVariant(
    val variation: String?,
    val template: ArenaStructureTemplate
)

data class ArenaAnimatedStructureVariant(
    val variation: String?,
    val closedTemplate: ArenaStructureTemplate,
    val openFrames: List<ArenaStructureTemplate>
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

enum class ArenaStructureType(val keyword: String, val supportsAnimation: Boolean) {
    STRAIGHT("straight", false),
    CORNER("corner", true),
    CORRIDOR("corridor", true),
    ENTRANCE("entrance", false),
    GOAL("goal", false)
}

class ArenaThemeLoader(private val plugin: JavaPlugin) {
    private sealed interface StructureState {
        data object Closed : StructureState
        data class Open(val index: Int) : StructureState
    }

    private data class LoadedThemeStructures(
        val staticStructures: Map<ArenaStructureType, List<ArenaStaticStructureVariant>>,
        val animatedStructures: Map<ArenaStructureType, List<ArenaAnimatedStructureVariant>>
    )

    private data class ParsedStaticFile(
        val type: ArenaStructureType,
        val variation: String?,
        val template: ArenaStructureTemplate
    )

    private data class ParsedAnimatedFile(
        val type: ArenaStructureType,
        val variation: String?,
        val state: StructureState,
        val template: ArenaStructureTemplate
    )

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
            val loaded = loadStructures(folder, warnings)
            val missing = ArenaStructureType.entries.filter { type ->
                if (type.supportsAnimation) {
                    loaded.animatedStructures[type].isNullOrEmpty()
                } else {
                    loaded.staticStructures[type].isNullOrEmpty()
                }
            }
            if (missing.isNotEmpty()) {
                val warning = "[Arena] 必須ストラクチャー不足のためスキップ: $themeId (${missing.joinToString { it.keyword }})"
                plugin.logger.warning(warning)
                warnings.add(warning)
                continue
            }

            val templates = loaded.staticStructures.values
                .flatten()
                .map { it.template } + loaded.animatedStructures.values
                .flatten()
                .map { it.closedTemplate }
            if (templates.isEmpty()) {
                val warning = "[Arena] 有効なストラクチャーが見つからないためスキップ: $themeId"
                plugin.logger.warning(warning)
                warnings.add(warning)
                continue
            }

            val gridPitch = templates.maxOfOrNull { maxOf(it.size.x, it.size.z) } ?: 1

            themes[themeId] = ArenaTheme(
                id = themeId,
                path = folder.name,
                gridPitch = gridPitch,
                staticStructures = loaded.staticStructures,
                animatedStructures = loaded.animatedStructures
            )
        }

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

    private fun loadStructures(folder: File, warnings: MutableList<String>): LoadedThemeStructures {
        val staticResult = mutableMapOf<ArenaStructureType, MutableList<ArenaStaticStructureVariant>>()
        val animatedResult = mutableMapOf<ArenaStructureType, MutableList<ArenaAnimatedStructureVariant>>()
        ArenaStructureType.entries.forEach {
            staticResult[it] = mutableListOf()
            animatedResult[it] = mutableListOf()
        }

        val files = folder.listFiles() ?: return LoadedThemeStructures(staticResult, animatedResult)
        val parsedStatic = mutableListOf<ParsedStaticFile>()
        val parsedAnimated = mutableListOf<ParsedAnimatedFile>()

        for (file in files) {
            if (!file.name.endsWith(".nbt", ignoreCase = true)) continue
            try {
                val template = loadTemplate(file)
                if (template == null) continue
                val parts = file.nameWithoutExtension.split('.')
                if (parts.isEmpty()) continue

                val type = ArenaStructureType.entries.firstOrNull { it.keyword == parts.first().lowercase() }
                if (type == null) {
                    val warning = "[Arena] 命名規約に合わないためスキップ: ${file.name}"
                    plugin.logger.warning(warning)
                    warnings.add(warning)
                    continue
                }

                if (type.supportsAnimation) {
                    val parsed = parseAnimatedFile(type, parts, template)
                    if (parsed == null) {
                        val warning = "[Arena] 命名規約に合わないためスキップ: ${file.name}"
                        plugin.logger.warning(warning)
                        warnings.add(warning)
                        continue
                    }
                    parsedAnimated.add(parsed)
                } else {
                    val parsed = parseStaticFile(type, parts, template)
                    if (parsed == null) {
                        val warning = "[Arena] 命名規約に合わないためスキップ: ${file.name}"
                        plugin.logger.warning(warning)
                        warnings.add(warning)
                        continue
                    }
                    parsedStatic.add(parsed)
                }
            } catch (e: IOException) {
                plugin.logger.warning("[Arena] ストラクチャー読み込み失敗: ${file.name} (${e.message})")
            } catch (e: Exception) {
                plugin.logger.warning("[Arena] ストラクチャー処理中に例外: ${file.name} (${e.message})")
            }
        }

        ArenaStructureType.entries.filterNot { it.supportsAnimation }.forEach { type ->
            parsedStatic
                .filter { it.type == type }
                .groupBy { it.variation }
                .forEach { (variation, variants) ->
                    val selected = variants.map { it.template }.sortedBy { it.name }.firstOrNull() ?: return@forEach
                    staticResult[type]?.add(
                        ArenaStaticStructureVariant(
                            variation = variation,
                            template = selected
                        )
                    )
                }
        }

        ArenaStructureType.entries.filter { it.supportsAnimation }.forEach { type ->
            val groupedByVariation = parsedAnimated
                .filter { it.type == type }
                .groupBy { it.variation }

            for ((variation, group) in groupedByVariation) {
                val closedCandidates = group
                    .filter { it.state is StructureState.Closed }
                    .map { it.template }
                    .sortedBy { it.name }
                if (closedCandidates.isEmpty()) {
                    val warning = "[Arena] closed が存在しないため系統をスキップ: theme=${folder.name} type=${type.keyword} variation=${variation ?: "default"}"
                    plugin.logger.warning(warning)
                    warnings.add(warning)
                    continue
                }

                val closed = closedCandidates.first()

                val openByIndex = group
                    .mapNotNull { parsed ->
                        val openState = parsed.state as? StructureState.Open ?: return@mapNotNull null
                        openState.index to parsed.template
                    }
                    .toMap()

                if (openByIndex.isEmpty()) {
                    val warning = "[Arena] open_i が存在しないため系統をスキップ: theme=${folder.name} type=${type.keyword} variation=${variation ?: "default"}"
                    plugin.logger.warning(warning)
                    warnings.add(warning)
                    continue
                }

                val maxIndex = openByIndex.keys.maxOrNull() ?: 0
                val expected = (1..maxIndex).toSet()
                val actual = openByIndex.keys.toSet()
                if (expected != actual) {
                    val warning = "[Arena] open_i が連番ではないため系統をスキップ: theme=${folder.name} type=${type.keyword} variation=${variation ?: "default"} actual=${actual.sorted().joinToString(",")}" 
                    plugin.logger.warning(warning)
                    warnings.add(warning)
                    continue
                }

                val openFrames = (1..maxIndex).map { index ->
                    openByIndex[index] ?: error("open_$index が見つかりません")
                }

                val mismatch = openFrames.firstOrNull { open ->
                    open.size.x != closed.size.x || open.size.y != closed.size.y || open.size.z != closed.size.z
                }
                if (mismatch != null) {
                    val warning = "[Arena] closed/open_i のサイズ不一致で系統をスキップ: theme=${folder.name} type=${type.keyword} variation=${variation ?: "default"}"
                    plugin.logger.warning(warning)
                    warnings.add(warning)
                    continue
                }

                animatedResult[type]?.add(
                    ArenaAnimatedStructureVariant(
                        variation = variation,
                        closedTemplate = closed,
                        openFrames = openFrames
                    )
                )
            }
        }

        return LoadedThemeStructures(staticResult, animatedResult)
    }

    private fun loadTemplate(file: File): ArenaStructureTemplate? {
        val structure = Bukkit.getStructureManager().loadStructure(file)
        val blockSize = structure.size
        val size = ArenaStructureSize(blockSize.blockX, blockSize.blockY, blockSize.blockZ)
        if (size.x <= 0 || size.y <= 0 || size.z <= 0) {
            plugin.logger.warning("[Arena] 不正なサイズのためスキップ: ${file.name} size=${size.x}*${size.y}*${size.z}")
            return null
        }
        return ArenaStructureTemplate(
            name = file.name,
            structure = structure,
            size = size
        )
    }

    private fun parseStaticFile(
        type: ArenaStructureType,
        parts: List<String>,
        template: ArenaStructureTemplate
    ): ParsedStaticFile? {
        if (parts.first().lowercase() != type.keyword) return null
        val variation = when (parts.size) {
            1 -> null
            2 -> parts[1].takeIf { it.isNotBlank() } ?: return null
            else -> return null
        }
        return ParsedStaticFile(
            type = type,
            variation = variation,
            template = template
        )
    }

    private fun parseAnimatedFile(
        type: ArenaStructureType,
        parts: List<String>,
        template: ArenaStructureTemplate
    ): ParsedAnimatedFile? {
        if (parts.first().lowercase() != type.keyword) return null
        if (parts.size !in 2..3) return null

        val variation = if (parts.size == 3) {
            parts[1].takeIf { it.isNotBlank() } ?: return null
        } else {
            null
        }
        val stateToken = parts.last().lowercase()
        val state = when {
            stateToken == "closed" -> StructureState.Closed
            stateToken.startsWith("open_") -> {
                val index = stateToken.removePrefix("open_").toIntOrNull() ?: return null
                if (index <= 0) return null
                StructureState.Open(index)
            }

            else -> return null
        }
        return ParsedAnimatedFile(
            type = type,
            variation = variation,
            state = state,
            template = template
        )
    }

    fun getTheme(id: String): ArenaTheme? = themes[id]

    fun getRandomTheme(random: Random = Random.Default): ArenaTheme? {
        if (themes.isEmpty()) return null
        return themes.values.random(random)
    }

    fun getThemeIds(): Set<String> = themes.keys
}
