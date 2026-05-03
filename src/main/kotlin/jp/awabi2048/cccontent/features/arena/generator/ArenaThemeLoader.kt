package jp.awabi2048.cccontent.features.arena.generator

import jp.awabi2048.cccontent.config.CoreConfigManager
import jp.awabi2048.cccontent.features.arena.mission.ArenaMissionType
import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.structure.Structure
import java.io.File
import java.io.IOException
import kotlin.random.Random

data class ArenaTheme(
    val id: String,
    val path: String,
    val normalConfig: ArenaThemeConfig,
    val promotedConfig: ArenaThemeConfig?,
    val gridPitch: Int,
    val staticStructures: Map<ArenaStructureType, List<ArenaStaticStructureVariant>>,
    val animatedStructures: Map<ArenaStructureType, List<ArenaAnimatedStructureVariant>>,
) {
    val iconMaterial: Material get() = normalConfig.iconMaterial
    val weight: Int get() = normalConfig.weight
    val normalVariant: ArenaThemeVariant get() = normalConfig.variant
    val promotedVariant: ArenaThemeVariant? get() = promotedConfig?.variant
    val doorOpenSound: ArenaThemeDoorOpenSound get() = normalConfig.doorOpenSound
    val orientation: ArenaStructureOrientation get() = normalConfig.orientation
    val clearingBossMobId: String? get() = normalConfig.clearingBossMobId

    fun config(promoted: Boolean): ArenaThemeConfig {
        return if (promoted) promotedConfig ?: normalConfig else normalConfig
    }

    fun variant(promoted: Boolean): ArenaThemeVariant {
        return config(promoted).variant
    }

    fun withActiveConfig(promoted: Boolean): ArenaTheme {
        return if (!promoted) this else copy(normalConfig = config(promoted))
    }
}

data class ArenaThemeConfig(
    val iconMaterial: Material,
    val weight: Int,
    val variant: ArenaThemeVariant,
    val doorOpenSound: ArenaThemeDoorOpenSound,
    val orientation: ArenaStructureOrientation,
    val clearingBossMobId: String? = null
)

data class ArenaThemeVariant(
    val difficultyStar: Int,
    val difficultyExpBonusRate: Double,
    val waveExpBonusRateIncrement: Double,
    val maxParticipants: Int,
    val pedestalRoomProbability: Double,
    val reviveMaxPerPlayer: Int,
    val reviveTimeLimitSeconds: Int,
    val waves: List<ArenaWaveSpawnRule>
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
    val statMultiplier: Double,
    val maxAlive: Int?
)

data class ArenaWaveSpawnRule(
    val wave: Int,
    val spawnIntervalTicks: Long,
    val maxAlive: Int,
    val clearMobCount: Int,
    val weightedMobs: List<ArenaThemeWeightedMobEntry>
)

data class ArenaThemeLoadStatus(
    val availableThemeIds: Set<String> = emptySet(),
    val unavailableThemes: List<ArenaThemeLoadIssue> = emptyList(),
    val generalWarnings: List<String> = emptyList()
)

data class ArenaThemeLoadIssue(
    val themeId: String,
    val details: List<String>
)

data class ArenaStructureOrientation(
    val entranceExit: ArenaPathDirection,
    val cornerEntry: ArenaPathDirection,
    val cornerExit: ArenaPathDirection,
    val straightEntry: ArenaPathDirection,
    val corridorEntry: ArenaPathDirection,
    val goalEntry: ArenaPathDirection,
    val tjunctionEntry: ArenaPathDirection,
    val tjunctionThrough: ArenaPathDirection,
    val tjunctionBranch: ArenaPathDirection,
    val pedestalEntry: ArenaPathDirection
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
    GOAL("goal", false),
    TJUNCTION_ROOM("tjunction_room", false),
    PEDESTAL_ROOM("pedestal_room", false)
}

class ArenaThemeLoader(private val plugin: JavaPlugin) {
    private companion object {
        const val THEME_CONFIG_DIR = "config/arena/themes"

        val DEFAULT_THEME_RESOURCE_FILES = listOf(
            "desert_temple.yml",
            "end.yml",
            "natura.yml",
            "nether.yml",
            "ocean_monument.yml",
            "ruins.yml"
        )

        val REQUIRED_GOAL_MISSION_TYPES = listOf(
            ArenaMissionType.BARRIER_RESTART,
            ArenaMissionType.CLEARING
        )
    }

    private val optionalStructureTypes = setOf(
        ArenaStructureType.TJUNCTION_ROOM,
        ArenaStructureType.PEDESTAL_ROOM
    )

    private data class ParsedThemeConfig(
        val normalConfig: ArenaThemeConfig,
        val promotedConfig: ArenaThemeConfig?
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
    private var lastLoadStatus: ArenaThemeLoadStatus = ArenaThemeLoadStatus()

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

        val themeConfigDir = File(plugin.dataFolder, THEME_CONFIG_DIR)
        ensureDefaultThemeResources(themeConfigDir)
        val themeConfigFiles = themeConfigDir
            .listFiles { file -> file.isFile && file.extension.equals("yml", ignoreCase = true) }
            ?.sortedBy { it.nameWithoutExtension }
            .orEmpty()
        val configuredThemeIds = themeConfigFiles
            .map { it.nameWithoutExtension.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val folderById = themeFolders.associateBy { it.name }
        val folderThemeIds = folderById.keys

        configuredThemeIds
            .filterNot { folderThemeIds.contains(it) }
            .sorted()
            .forEach { themeId ->
                val warning = "[Arena] $THEME_CONFIG_DIR/$themeId.yml に定義がありますが structures/arena/$themeId が存在しないためスキップ: $themeId"
                plugin.logger.warning(warning)
                warnings.add(warning)
            }

        folderThemeIds
            .filterNot { configuredThemeIds.contains(it) }
            .sorted()
            .forEach { themeId ->
                val warning = "[Arena] structures/arena/$themeId は存在しますが $THEME_CONFIG_DIR/$themeId.yml に定義がないためスキップ: $themeId"
                plugin.logger.warning(warning)
                warnings.add(warning)
            }

        val configFileByThemeId = themeConfigFiles.associateBy { it.nameWithoutExtension.trim() }
        val validThemeIds = configuredThemeIds.intersect(folderThemeIds).sorted()

        for (themeId in validThemeIds) {
            val folder = folderById[themeId] ?: continue
            val themeConfigFile = configFileByThemeId[themeId] ?: continue
            val parsedThemeConfig = parseThemeConfig(themeConfigFile, themeId, warnings) ?: continue
            val loaded = loadStructures(folder, warnings)
            val missing = mutableListOf<String>()
            ArenaStructureType.entries.filterNot { optionalStructureTypes.contains(it) }.forEach { type ->
                if (type == ArenaStructureType.GOAL) {
                    val goalVariations = loaded.staticStructures[ArenaStructureType.GOAL].orEmpty()
                    REQUIRED_GOAL_MISSION_TYPES.forEach { missionType ->
                        if (goalVariations.none { it.variation == missionType.id }) {
                            missing += "goal.${missionType.id}"
                        }
                    }
                } else {
                    val unavailable = if (type.supportsAnimation) {
                        loaded.animatedStructures[type].isNullOrEmpty()
                    } else {
                        loaded.staticStructures[type].isNullOrEmpty()
                    }
                    if (unavailable) {
                        missing += type.keyword
                    }
                }
            }
            if (missing.isNotEmpty()) {
                val warning = "[Arena] 必須ストラクチャー不足のためスキップ: $themeId (${missing.joinToString()})"
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
                normalConfig = parsedThemeConfig.normalConfig,
                promotedConfig = parsedThemeConfig.promotedConfig,
                gridPitch = gridPitch,
                staticStructures = loaded.staticStructures,
                animatedStructures = loaded.animatedStructures
            )
        }

        lastLoadStatus = buildLoadStatus(configuredThemeIds + folderThemeIds, warnings)

        featureInitLogger?.apply {
            if (themes.isEmpty()) {
                setStatus("Arena", FeatureInitializationLogger.Status.FAILURE)
                addDetailMessage("Arena", "[Arena] theme load failed: no valid themes")
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
        themeConfigFile: File,
        themeId: String,
        warnings: MutableList<String>
    ): ParsedThemeConfig? {
        val themeConfig = YamlConfiguration.loadConfiguration(themeConfigFile)
        val sourcePath = "$THEME_CONFIG_DIR/${themeConfigFile.name}"
        val normalSection = themeConfig.getConfigurationSection("normal")
        if (normalSection == null) {
            val warning = "[Arena] invalid normal section: $sourcePath"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }

        val normalConfig = parseThemeConfigVariant(normalSection, themeId, "normal", warnings, null) ?: return null
        val promotedConfig = themeConfig
            .getConfigurationSection("promoted")
            ?.let { parseThemeConfigVariant(it, themeId, "promoted", warnings, normalConfig) }

        return ParsedThemeConfig(
            normalConfig = normalConfig,
            promotedConfig = promotedConfig
        )
    }

    private fun ensureDefaultThemeResources(themeConfigDir: File) {
        if (!themeConfigDir.exists()) {
            themeConfigDir.mkdirs()
        }
        DEFAULT_THEME_RESOURCE_FILES.forEach { fileName ->
            val resourcePath = "$THEME_CONFIG_DIR/$fileName"
            val targetFile = File(themeConfigDir, fileName)
            if (!targetFile.exists() && plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false)
            }
        }
    }

    private fun parseThemeConfigVariant(
        section: ConfigurationSection,
        themeId: String,
        variantName: String,
        warnings: MutableList<String>,
        fallback: ArenaThemeConfig?
    ): ArenaThemeConfig? {
        val weight = section.getInt("weight", fallback?.weight ?: 1)
        if (weight <= 0) {
            throw IllegalStateException("[Arena] $THEME_CONFIG_DIR/$themeId.yml の weight は1以上である必要があります: theme=$themeId variant=$variantName weight=$weight")
        }

        val iconMaterial = if (section.contains("icon")) {
            val iconName = (section.getString("icon") ?: Material.PAPER.name).trim()
            try {
                Material.valueOf(iconName.uppercase())
            } catch (_: IllegalArgumentException) {
                val warning = "[Arena] $THEME_CONFIG_DIR/$themeId.yml の icon が不正なためスキップします: theme=$themeId variant=$variantName icon=$iconName"
                plugin.logger.warning(warning)
                warnings.add(warning)
                return null
            }
        } else {
            fallback?.iconMaterial ?: Material.PAPER
        }

        val coreConfig = CoreConfigManager.get(plugin)
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
        val doorSoundKey = doorSoundSection?.getString("key")?.trim().orEmpty()
            .ifBlank { fallback?.doorOpenSound?.key ?: defaultDoorSoundKey }
        val doorSoundPitch = (doorSoundSection?.getDouble("pitch", fallback?.doorOpenSound?.pitch?.toDouble() ?: defaultDoorSoundPitch.toDouble())
            ?: fallback?.doorOpenSound?.pitch?.toDouble()
            ?: defaultDoorSoundPitch.toDouble()).toFloat().coerceIn(0.5f, 2.0f)

        val variant = parseThemeVariant(section, themeId, variantName, warnings, fallback?.variant) ?: return null
        val orientationSection = section.getConfigurationSection("orientation")
        val orientation = if (orientationSection != null) {
            parseOrientation(themeId, orientationSection, warnings) ?: return null
        } else if (fallback != null) {
            fallback.orientation
        } else {
            val warning = "[Arena] $THEME_CONFIG_DIR/$themeId.yml の orientation が見つからないためスキップします: theme=$themeId variant=$variantName"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }
        val clearingBossMobId = if (section.contains("clearing_boss_mob_id")) {
            section.getString("clearing_boss_mob_id")?.trim()?.takeIf { it.isNotBlank() }
        } else {
            fallback?.clearingBossMobId
        }

        return ArenaThemeConfig(
            iconMaterial = iconMaterial,
            weight = weight,
            variant = variant,
            doorOpenSound = ArenaThemeDoorOpenSound(
                key = doorSoundKey,
                pitch = doorSoundPitch
            ),
            orientation = orientation,
            clearingBossMobId = clearingBossMobId
        )
    }

    private fun parseOrientation(
        themeId: String,
        orientationSection: ConfigurationSection,
        warnings: MutableList<String>
    ): ArenaStructureOrientation? {
        val entranceExit = parseDirection(themeId, orientationSection, "entrance_exit", warnings) ?: return null
        val cornerEntry = parseDirection(themeId, orientationSection, "corner_entry", warnings) ?: return null
        val cornerExit = parseDirection(themeId, orientationSection, "corner_exit", warnings) ?: return null
        val straightEntry = parseDirection(themeId, orientationSection, "straight_entry", warnings) ?: return null
        val corridorEntry = parseDirection(themeId, orientationSection, "corridor_entry", warnings) ?: return null
        val goalEntry = parseDirection(themeId, orientationSection, "goal_entry", warnings) ?: return null
        val tjunctionEntry = parseOptionalDirection(orientationSection, "tjunction_entry") ?: straightEntry
        val tjunctionThrough = parseOptionalDirection(orientationSection, "tjunction_through") ?: straightEntry.opposite()
        val tjunctionBranch = parseOptionalDirection(orientationSection, "tjunction_branch") ?: straightEntry.right()
        val pedestalEntry = parseOptionalDirection(orientationSection, "pedestal_entry") ?: tjunctionBranch.opposite()

        if (cornerEntry == cornerExit) {
            val warning = "[Arena] $THEME_CONFIG_DIR/$themeId.yml の corner_entry/corner_exit が同じ方向のためスキップします: theme=$themeId value=${cornerEntry.token}"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }

        if (!cornerEntry.isAdjacent(cornerExit)) {
            val warning = "[Arena] $THEME_CONFIG_DIR/$themeId.yml の corner_entry/corner_exit は隣接方向のみ許可されています: theme=$themeId entry=${cornerEntry.token} exit=${cornerExit.token}"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }

        if (tjunctionEntry == tjunctionThrough || tjunctionEntry != tjunctionThrough.opposite()) {
            val warning = "[Arena] $THEME_CONFIG_DIR/$themeId.yml の tjunction_entry/tjunction_through は向かい合う必要があります: theme=$themeId entry=${tjunctionEntry.token} through=${tjunctionThrough.token}"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }

        if (!tjunctionBranch.isAdjacent(tjunctionEntry) || !tjunctionBranch.isAdjacent(tjunctionThrough)) {
            val warning = "[Arena] $THEME_CONFIG_DIR/$themeId.yml の tjunction_branch は entry/through の側面である必要があります: theme=$themeId entry=${tjunctionEntry.token} through=${tjunctionThrough.token} branch=${tjunctionBranch.token}"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }

        return ArenaStructureOrientation(
            entranceExit = entranceExit,
            cornerEntry = cornerEntry,
            cornerExit = cornerExit,
            straightEntry = straightEntry,
            corridorEntry = corridorEntry,
            goalEntry = goalEntry,
            tjunctionEntry = tjunctionEntry,
            tjunctionThrough = tjunctionThrough,
            tjunctionBranch = tjunctionBranch,
            pedestalEntry = pedestalEntry
        )
    }
    private fun parseThemeVariant(
        section: ConfigurationSection,
        themeId: String,
        variantName: String,
        warnings: MutableList<String>,
        fallback: ArenaThemeVariant? = null
    ): ArenaThemeVariant? {
        val difficultyStar = section.getInt("difficulty_star", fallback?.difficultyStar ?: -1)
        if (difficultyStar < 1) {
            throw IllegalStateException("[Arena] $THEME_CONFIG_DIR/$themeId.yml の difficulty_star は1以上である必要があります: theme=$themeId variant=$variantName difficulty_star=$difficultyStar")
        }
        val defaultDifficultyExpBonusRate = (difficultyStar - 1) * 0.15
        val defaultWaveExpBonusRateIncrement = difficultyStar * 0.05
        val difficultyExpBonusRate = section
            .getDouble("difficulty_exp_bonus_rate", fallback?.difficultyExpBonusRate ?: defaultDifficultyExpBonusRate)
            .coerceAtLeast(0.0)
        val waveExpBonusRateIncrement = section
            .getDouble("wave_exp_bonus_rate_increment", fallback?.waveExpBonusRateIncrement ?: defaultWaveExpBonusRateIncrement)
            .coerceAtLeast(0.0)
        val maxParticipants = section.getInt("max_participants", fallback?.maxParticipants ?: 6).coerceIn(1, 6)
        val pedestalRoomProbability = section.getDouble("pedestal_room_probability", fallback?.pedestalRoomProbability ?: 0.0).coerceIn(0.0, 1.0)
        val reviveMaxPerPlayerRaw = section.getInt("revive_max_per_player", fallback?.reviveMaxPerPlayer ?: -1)
        val reviveMaxPerPlayer = if (reviveMaxPerPlayerRaw <= 0) Int.MAX_VALUE else reviveMaxPerPlayerRaw
        val reviveTimeLimitSeconds = section.getInt("revive_time_limit_seconds", fallback?.reviveTimeLimitSeconds ?: 0).coerceAtLeast(0)
        val waveMaps = section.getMapList("waves")
        val waves = if (waveMaps.isEmpty() && fallback != null) {
            fallback.waves
        } else if (waveMaps.isEmpty()) {
            val warning = "[Arena] $THEME_CONFIG_DIR/$themeId.yml の waves が空のためスキップ: theme=$themeId variant=$variantName"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        } else {
            waveMaps.mapIndexed { index, raw ->
            parseWaveSpawnRule(themeId, variantName, index, raw)
            }.sortedBy { it.wave }
        }

        waves.forEachIndexed { index, rule ->
            val expected = index + 1
            if (rule.wave != expected) {
                throw IllegalStateException("[Arena] $THEME_CONFIG_DIR/$themeId.yml の waves.wave は1始まりの連番である必要があります: theme=$themeId variant=$variantName expected=$expected actual=${rule.wave}")
            }
        }

        return ArenaThemeVariant(
            difficultyStar = difficultyStar,
            difficultyExpBonusRate = difficultyExpBonusRate,
            waveExpBonusRateIncrement = waveExpBonusRateIncrement,
            maxParticipants = maxParticipants,
            pedestalRoomProbability = pedestalRoomProbability,
            reviveMaxPerPlayer = reviveMaxPerPlayer,
            reviveTimeLimitSeconds = reviveTimeLimitSeconds,
            waves = waves
        )
    }

    private fun parseWaveSpawnRule(
        themeId: String,
        variantName: String,
        index: Int,
        raw: Map<*, *>
    ): ArenaWaveSpawnRule {
        val wave = raw["wave"]?.toString()?.toIntOrNull()
            ?: throw IllegalStateException("[Arena] $THEME_CONFIG_DIR/$themeId.yml の waves[$index].wave が不正です: theme=$themeId variant=$variantName")
        val spawnInterval = raw["spawn_interval"]?.toString()?.toLongOrNull()?.coerceAtLeast(1L)
            ?: throw IllegalStateException("[Arena] $THEME_CONFIG_DIR/$themeId.yml の waves[$index].spawn_interval が不正です: theme=$themeId variant=$variantName")
        val clearMobCount = raw["clear_mob_count"]?.toString()?.toIntOrNull()?.coerceAtLeast(1)
            ?: throw IllegalStateException("[Arena] $THEME_CONFIG_DIR/$themeId.yml の waves[$index].clear_mob_count が不正です: theme=$themeId variant=$variantName")
        val maxAlive = raw["max_alive"]?.toString()?.toIntOrNull()?.coerceAtLeast(1)
            ?: throw IllegalStateException("[Arena] $THEME_CONFIG_DIR/$themeId.yml の waves[$index].max_alive が不正です: theme=$themeId variant=$variantName")
        val mobs = raw["mobs"] as? Map<*, *>
            ?: throw IllegalStateException("[Arena] $THEME_CONFIG_DIR/$themeId.yml の waves[$index].mobs が不正です: theme=$themeId variant=$variantName")
        val weightedMobs = mobs.map { (mobIdRaw, valueRaw) ->
            val mobId = mobIdRaw?.toString()?.trim().orEmpty()
            if (mobId.isBlank()) {
                throw IllegalStateException("[Arena] $THEME_CONFIG_DIR/$themeId.yml の mob ID が空です: theme=$themeId variant=$variantName wave=$wave")
            }
            val values = valueRaw as? Map<*, *>
                ?: throw IllegalStateException("[Arena] $THEME_CONFIG_DIR/$themeId.yml の mob定義が不正です: theme=$themeId variant=$variantName wave=$wave mob=$mobId")
            val weight = values["weight"]?.toString()?.toIntOrNull()?.coerceAtLeast(1)
                ?: throw IllegalStateException("[Arena] $THEME_CONFIG_DIR/$themeId.yml の mob.weight が不正です: theme=$themeId variant=$variantName wave=$wave mob=$mobId")
            val statMultiplier = values["stat_multiplier"]?.toString()?.toDoubleOrNull()?.coerceAtLeast(0.01) ?: 1.0
            val maxAlive = values["max_alive"]?.toString()?.toIntOrNull()?.coerceAtLeast(1)
            ArenaThemeWeightedMobEntry(
                mobId = mobId,
                weight = weight,
                statMultiplier = statMultiplier,
                maxAlive = maxAlive
            )
        }
        if (weightedMobs.isEmpty()) {
            throw IllegalStateException("[Arena] $THEME_CONFIG_DIR/$themeId.yml の waves[$index].mobs が空です: theme=$themeId variant=$variantName")
        }
        return ArenaWaveSpawnRule(
            wave = wave,
            spawnIntervalTicks = spawnInterval,
            maxAlive = maxAlive,
            clearMobCount = clearMobCount,
            weightedMobs = weightedMobs
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
            val warning = "[Arena] $THEME_CONFIG_DIR/$themeId.yml の方向指定が未設定のためスキップ: theme=$themeId key=$key"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }
        val direction = ArenaPathDirection.fromToken(raw)
        if (direction == null) {
            val warning = "[Arena] $THEME_CONFIG_DIR/$themeId.yml の方向指定が不正のためスキップ: theme=$themeId key=$key value=$raw"
            plugin.logger.warning(warning)
            warnings.add(warning)
            return null
        }
        return direction
    }

    private fun parseOptionalDirection(
        section: org.bukkit.configuration.ConfigurationSection,
        key: String
    ): ArenaPathDirection? {
        val raw = section.getString(key)?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return ArenaPathDirection.fromToken(raw)
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
                    val warning = "[Arena] 命名規則に合わないためスキップ: ${file.name}"
                    plugin.logger.warning(warning)
                    warnings.add(warning)
                    continue
                }

                if (type.supportsAnimation) {
                    val parsed = parseAnimatedFile(type, parts, template)
                    if (parsed == null) {
                        val warning = "[Arena] 命名規則に合わないためスキップ: ${file.name}"
                        plugin.logger.warning(warning)
                        warnings.add(warning)
                        continue
                    }
                    parsedAnimated.add(parsed)
                } else {
                    val parsed = parseStaticFile(type, parts, template)
                    if (parsed == null) {
                        val warning = "[Arena] 命名規則に合わないためスキップ: ${file.name}"
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

    fun getLoadStatus(): ArenaThemeLoadStatus = lastLoadStatus

    private fun buildLoadStatus(candidateThemeIds: Set<String>, warnings: List<String>): ArenaThemeLoadStatus {
        val availableThemeIds = themes.keys.toSortedSet()
        val unavailableThemeIds = (candidateThemeIds - availableThemeIds)
            .filter { it.isNotBlank() }
            .sorted()
        val unavailableThemes = unavailableThemeIds.map { themeId ->
            val details = warnings.filter { warning ->
                warning.contains(themeId) || warning.contains("structures/arena/$themeId")
            }.ifEmpty { listOf("[Arena] theme was not loaded: $themeId") }
            ArenaThemeLoadIssue(themeId, details)
        }
        val unavailableSet = unavailableThemeIds.toSet()
        val generalWarnings = warnings.filterNot { warning ->
            unavailableSet.any { themeId ->
                warning.contains(themeId) || warning.contains("structures/arena/$themeId")
            }
        }
        return ArenaThemeLoadStatus(
            availableThemeIds = availableThemeIds,
            unavailableThemes = unavailableThemes,
            generalWarnings = generalWarnings
        )
    }
}
