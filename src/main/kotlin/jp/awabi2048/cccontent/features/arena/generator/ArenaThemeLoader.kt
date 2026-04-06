package jp.awabi2048.cccontent.features.arena.generator

import jp.awabi2048.cccontent.config.CoreConfigManager
import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.structure.Structure
import java.io.File
import java.io.IOException
import kotlin.random.Random

data class ArenaTheme(
    val id: String,
    val path: String,
    val iconMaterial: Material,
    val mobSpawnConfig: ArenaThemeMobSpawnConfig,
    val doorOpenSound: ArenaThemeDoorOpenSound,
    val orientation: ArenaStructureOrientation,
    val gridPitch: Int,
    val staticStructures: Map<ArenaStructureType, List<ArenaStaticStructureVariant>>,
    val animatedStructures: Map<ArenaStructureType, List<ArenaAnimatedStructureVariant>>
)

data class ArenaThemeDoorOpenSound(
    val key: String,
    val pitch: Float
)

data class ArenaThemeWaveRange(
    val minInclusive: Int,
    val maxInclusive: Int?
) {
    fun contains(wave: Int): Boolean {
        if (wave < minInclusive) return false
        return maxInclusive == null || wave <= maxInclusive
    }
}

data class ArenaThemeWeightedMobEntry(
    val mobId: String,
    val weight: Int,
    val waveRange: ArenaThemeWaveRange,
    val maxAlive: Int?
)

data class ArenaThemeMobSpawnConfig(
    val maxSummonCount: Int,
    val clearMobCount: Int,
    val weightedMobs: List<ArenaThemeWeightedMobEntry>
) {
    fun candidatesForWave(wave: Int): List<ArenaThemeWeightedMobEntry> {
        return weightedMobs.filter { it.waveRange.contains(wave) }
    }
}

data class ArenaStructureOrientation(
    val entranceExit: ArenaPathDirection,
    val cornerEntry: ArenaPathDirection,
    val cornerExit: ArenaPathDirection,
    val straightEntry: ArenaPathDirection,
    val corridorEntry: ArenaPathDirection,
    val goalEntry: ArenaPathDirection
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
    private data class ParsedThemeConfig(
        val iconMaterial: Material,
        val mobSpawnConfig: ArenaThemeMobSpawnConfig,
        val doorOpenSound: ArenaThemeDoorOpenSound,
        val orientation: ArenaStructureOrientation
    )

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

        val themeConfigFile = File(plugin.dataFolder, "config/arena/theme.yml")
        if (!themeConfigFile.exists()) {
            themeConfigFile.parentFile.mkdirs()
            plugin.saveResource("config/arena/theme.yml", false)
        }
        val themeConfig = YamlConfiguration.loadConfiguration(themeConfigFile)
        val configuredThemeIds = themeConfig.getKeys(false)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val folderById = themeFolders.associateBy { it.name }
        val folderThemeIds = folderById.keys

        configuredThemeIds
            .filterNot { folderThemeIds.contains(it) }
            .sorted()
            .forEach { themeId ->
                val warning = "[Arena] theme.yml に定義がありますが structures/arena/$themeId が存在しないためスキップ: $themeId"
                plugin.logger.warning(warning)
                warnings.add(warning)
            }

        folderThemeIds
            .filterNot { configuredThemeIds.contains(it) }
            .sorted()
            .forEach { themeId ->
                val warning = "[Arena] structures/arena/$themeId は存在しますが theme.yml に定義がないためスキップ: $themeId"
                plugin.logger.warning(warning)
                warnings.add(warning)
            }

        val validThemeIds = configuredThemeIds.intersect(folderThemeIds).sorted()

        for (themeId in validThemeIds) {
            val folder = folderById[themeId] ?: continue
            val parsedThemeConfig = parseThemeConfig(themeConfig, themeId, warnings) ?: continue
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
                iconMaterial = parsedThemeConfig.iconMaterial,
                mobSpawnConfig = parsedThemeConfig.mobSpawnConfig,
                doorOpenSound = parsedThemeConfig.doorOpenSound,
                orientation = parsedThemeConfig.orientation,
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

    private fun parseThemeConfig(
        config: YamlConfiguration,
        themeId: String,
        warnings: MutableList<String>
    ): ParsedThemeConfig? {
        val section = config.getConfigurationSection(themeId)
        if (section == null) {
            val warning = "[Arena] theme.yml のテーマ定義が不正なためスキップ: $themeId"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }

        val iconName = (section.getString("icon") ?: Material.PAPER.name).trim()
        val iconMaterial = try {
            Material.valueOf(iconName.uppercase())
        } catch (_: IllegalArgumentException) {
            val warning = "[Arena] theme.yml の icon が不正なためスキップ: theme=$themeId icon=$iconName"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }

        val coreConfig = CoreConfigManager.get(plugin)
        val maxSummonCount = section.getInt("max_summon_count", 1).coerceAtLeast(1)
        val clearMobCount = section.getInt("clear_mob_count", 1).coerceAtLeast(1)
        val defaultDoorSoundKey = coreConfig
            .getString("arena.door_animation.sound.key", "minecraft:block.iron_door.open")
            ?.trim()
            .orEmpty()
            .ifBlank { "minecraft:block.iron_door.open" }
        val defaultDoorSoundPitch = coreConfig
            .getDouble("arena.door_animation.sound.pitch", 1.0)
            .toFloat()
            .coerceIn(0.5f, 2.0f)
        val doorSoundSection = section.getConfigurationSection("door_open_sound")
        val doorSoundKey = doorSoundSection?.getString("key")?.trim().orEmpty().ifBlank { defaultDoorSoundKey }
        val doorSoundPitch = (doorSoundSection?.getDouble("pitch", defaultDoorSoundPitch.toDouble())
            ?: defaultDoorSoundPitch.toDouble()).toFloat().coerceIn(0.5f, 2.0f)
        val mobsSection = section.getConfigurationSection("mobs")
        if (mobsSection == null) {
            val warning = "[Arena] theme.yml の mobs セクションが見つからないためスキップ: theme=$themeId"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }
        val weightedMobs = mutableListOf<ArenaThemeWeightedMobEntry>()
        for (mobId in mobsSection.getKeys(false)) {
            val id = mobId.trim()
            if (id.isEmpty()) {
                val warning = "[Arena] theme.yml の mobs ID が空のためスキップ: theme=$themeId"
                plugin.logger.warning(warning)
                warnings.add(warning)
                continue
            }
            val weight = mobsSection.getInt("$mobId.weight", 0)
            if (weight <= 0) {
                val warning = "[Arena] theme.yml の mobs.weight は1以上である必要があります: theme=$themeId mob=$id"
                plugin.logger.warning(warning)
                warnings.add(warning)
                continue
            }
            val waveText = mobsSection.getString("$mobId.wave", "1..") ?: "1.."
            val waveRange = parseWaveRange(waveText)
            if (waveRange == null) {
                val warning = "[Arena] theme.yml の mobs.wave が不正です: theme=$themeId mob=$id wave=$waveText"
                plugin.logger.warning(warning)
                warnings.add(warning)
                continue
            }

            val maxAlivePath = "$mobId.max_alive"
            val maxAlive = if (mobsSection.contains(maxAlivePath)) {
                val value = mobsSection.getInt(maxAlivePath, 0)
                if (value <= 0) {
                    val warning = "[Arena] theme.yml の mobs.max_alive は1以上である必要があります: theme=$themeId mob=$id max_alive=$value"
                    plugin.logger.warning(warning)
                    warnings.add(warning)
                    continue
                }
                value
            } else {
                null
            }

            weightedMobs.add(
                ArenaThemeWeightedMobEntry(
                    mobId = id,
                    weight = weight,
                    waveRange = waveRange,
                    maxAlive = maxAlive
                )
            )
        }
        if (weightedMobs.isEmpty()) {
            val warning = "[Arena] theme.yml の有効な mobs 定義が1件もないためスキップ: theme=$themeId"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }

        val orientationSection = section.getConfigurationSection("orientation")
        if (orientationSection == null) {
            val warning = "[Arena] theme.yml の orientation が見つからないためスキップ: theme=$themeId"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }

        val entranceExit = parseDirection(themeId, orientationSection, "entrance_exit", warnings) ?: return null
        val cornerEntry = parseDirection(themeId, orientationSection, "corner_entry", warnings) ?: return null
        val cornerExit = parseDirection(themeId, orientationSection, "corner_exit", warnings) ?: return null
        val straightEntry = parseDirection(themeId, orientationSection, "straight_entry", warnings) ?: return null
        val corridorEntry = parseDirection(themeId, orientationSection, "corridor_entry", warnings) ?: return null
        val goalEntry = parseDirection(themeId, orientationSection, "goal_entry", warnings) ?: return null

        if (cornerEntry == cornerExit) {
            val warning = "[Arena] theme.yml の corner_entry/corner_exit が同じ方角のためスキップ: theme=$themeId value=${cornerEntry.token}"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }

        if (!cornerEntry.isAdjacent(cornerExit)) {
            val warning = "[Arena] theme.yml の corner_entry/corner_exit は隣接方向のみ許可されています: theme=$themeId entry=${cornerEntry.token} exit=${cornerExit.token}"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }

        return ParsedThemeConfig(
            iconMaterial = iconMaterial,
            mobSpawnConfig = ArenaThemeMobSpawnConfig(
                maxSummonCount = maxSummonCount,
                clearMobCount = clearMobCount,
                weightedMobs = weightedMobs
            ),
            doorOpenSound = ArenaThemeDoorOpenSound(
                key = doorSoundKey,
                pitch = doorSoundPitch
            ),
            orientation = ArenaStructureOrientation(
                entranceExit = entranceExit,
                cornerEntry = cornerEntry,
                cornerExit = cornerExit,
                straightEntry = straightEntry,
                corridorEntry = corridorEntry,
                goalEntry = goalEntry
            )
        )
    }

    private fun parseDirection(
        themeId: String,
        section: org.bukkit.configuration.ConfigurationSection,
        key: String,
        warnings: MutableList<String>
    ): ArenaPathDirection? {
        val raw = section.getString(key)?.trim().orEmpty()
        if (raw.isEmpty()) {
            val warning = "[Arena] theme.yml の方角指定が未設定のためスキップ: theme=$themeId key=$key"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }
        val direction = ArenaPathDirection.fromToken(raw)
        if (direction == null) {
            val warning = "[Arena] theme.yml の方角指定が不正のためスキップ: theme=$themeId key=$key value=$raw"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }
        return direction
    }

    private fun parseWaveRange(text: String): ArenaThemeWaveRange? {
        val exact = Regex("^(\\d+)$")
        exact.matchEntire(text.trim())?.let {
            val value = it.groupValues[1].toIntOrNull() ?: return null
            if (value <= 0) return null
            return ArenaThemeWaveRange(value, value)
        }

        val range = Regex("^(\\d+)\\.\\.(\\d+)?$")
        val matched = range.matchEntire(text.trim()) ?: return null
        val min = matched.groupValues[1].toIntOrNull() ?: return null
        val max = matched.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull()
        if (min <= 0) return null
        if (max != null && max < min) return null
        return ArenaThemeWaveRange(min, max)
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
