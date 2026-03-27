package jp.awabi2048.cccontent.features.arena

import jp.awabi2048.cccontent.config.CoreConfigManager
import jp.awabi2048.cccontent.features.arena.generator.ArenaStageGenerator
import jp.awabi2048.cccontent.features.arena.generator.ArenaStageBuildException
import jp.awabi2048.cccontent.features.arena.generator.ArenaTheme
import jp.awabi2048.cccontent.features.arena.generator.ArenaThemeLoader
import jp.awabi2048.cccontent.features.arena.generator.ArenaDoorAnimationPlacement
import jp.awabi2048.cccontent.features.arena.generator.ArenaThemeWeightedMobEntry
import jp.awabi2048.cccontent.features.arena.event.ArenaSessionEndedEvent
import jp.awabi2048.cccontent.features.arena.quest.ArenaQuestModifiers
import jp.awabi2048.cccontent.features.sukima_dungeon.BGMManager
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.VoidChunkGenerator
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.items.arena.ArenaMobTokenItem
import jp.awabi2048.cccontent.mob.MobDefinition
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.MobSpawnOptions
import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import jp.awabi2048.cccontent.util.OageMessageSender
import jp.awabi2048.cccontent.world.WorldSettingsHelper
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Ageable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Marker
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Zombie
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerAnimationType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

sealed class ArenaStartResult {
    data class Success(
        val themeId: String,
        val waves: Int,
        val difficultyId: String,
        val difficultyDisplay: String
    ) : ArenaStartResult()

    data class Error(
        val messageKey: String,
        val fallback: String,
        val placeholders: Array<out Pair<String, Any?>> = emptyArray()
    ) : ArenaStartResult()
}

private data class PendingWorldDeletion(
    val worldName: String,
    val folder: File,
    var attempts: Int = 0
)

private data class ArenaDifficultyConfig(
    val id: String,
    val difficultyRange: ClosedFloatingPointRange<Double>,
    val mobSpawnIntervalTicks: Long,
    val mobCountMultiplier: Double,
    val waveMultiplier: Double,
    val waves: Int,
    val display: String,
    val mobStatsMaxHealth: Double,
    val mobStatsMaxAttack: Double,
    val mobStatsMaxMovementSpeed: Double,
    val mobStatsMaxArmor: Double,
    val mobStatsMaxScale: Double
) {
    val difficultyValue: Double
        get() = (difficultyRange.start + difficultyRange.endInclusive) / 2.0

    fun waveScale(wave: Int): Double {
        return (1.0 + waveMultiplier * wave).coerceAtLeast(0.0)
    }
}

private data class ArenaDropEntry(
    val itemId: String,
    val baseAmount: Int
)

private data class ArenaDropConfig(
    val additionalDefaultDrops: List<ArenaDropEntry>,
    val additionalByMobType: Map<String, List<ArenaDropEntry>>,
    val mockItemCountBonus: Int,
    val mockExpBonusRate: Double,
    val mockRarity: String
)

private data class BarrierRestartConfig(
    val defaultDurationSeconds: Int,
    val corruptionRatioBase: Double
)

private data class ArenaBgmTrackConfig(
    val soundKey: String,
    val bpm: Double,
    val loopBeats: Int,
    val switchBeats: List<Int>
) {
    val beatTicks: Double
        get() = 1200.0 / bpm

    val loopTicks: Long
        get() = (beatTicks * loopBeats.toDouble()).roundToLong().coerceAtLeast(1L)
}

private data class ArenaBgmConfig(
    val normal: ArenaBgmTrackConfig,
    val combat: ArenaBgmTrackConfig
)

class ArenaManager(
    private val plugin: JavaPlugin,
    private val mobService: MobService = MobService(plugin)
) {
    private companion object {
        const val DOOR_ANIMATION_START_DELAY_TICKS = 10L
        const val DOOR_ANIMATION_TOTAL_TICKS_DEFAULT = 60
        const val SHARED_WAVE_MAX_ALIVE_DEFAULT = 128
        const val BARRIER_RESTART_PROGRESS_STEP_TICKS = 5L
        const val BARRIER_RESTART_PROGRESS_STEP_MILLIS = 250L
        const val BARRIER_DEFENSE_TARGET_RATIO = 1.0 / 3.0
        const val BARRIER_DEFENSE_PRESSURE_RADIUS = 4.5
        const val BARRIER_DEFENSE_PRESSURE_RADIUS_SQUARED = BARRIER_DEFENSE_PRESSURE_RADIUS * BARRIER_DEFENSE_PRESSURE_RADIUS
        const val BARRIER_RESTART_POINT_DAMAGE_RADIUS = 4.0
        const val BARRIER_RESTART_POINT_DAMAGE_RADIUS_SQUARED = BARRIER_RESTART_POINT_DAMAGE_RADIUS * BARRIER_RESTART_POINT_DAMAGE_RADIUS
        const val BARRIER_RESTART_POINT_DAMAGE_MAX_RATIO = 0.25
        const val BARRIER_RESTART_EFFECT_INTERVAL_TICKS = 5L
        const val BARRIER_RESTART_DAMAGE_INTERVAL_TICKS = 20L
        const val BARRIER_RESTART_BEAM_POINTS = 24
        const val POSITION_SAMPLE_INTERVAL_MILLIS = 1000L
        const val POSITION_RESTORE_LOOKBACK_MILLIS = 10_000L
        const val POSITION_HISTORY_RETENTION_MILLIS = 12_000L
        const val POSITION_HISTORY_MAX_SAMPLES = 24
        const val ARENA_BGM_NORMAL_KEY_DEFAULT = "kota_server:ost_3.sukima_dungeon"
        const val ARENA_BGM_COMBAT_KEY_DEFAULT = "kota_server:ost_4.arena"
        const val ARENA_BGM_NORMAL_BPM_DEFAULT = 120.0
        const val ARENA_BGM_COMBAT_BPM_DEFAULT = 140.0
        const val ARENA_BGM_LOOP_BEATS_DEFAULT = 384
        const val ACTION_MARKER_RADIUS = 1.0
        const val ACTION_MARKER_RADIUS_SQUARED = ACTION_MARKER_RADIUS * ACTION_MARKER_RADIUS
        const val ACTION_MARKER_OUTER_RING_RADIUS = 1.0
        const val ACTION_MARKER_OUTER_RING_HEIGHT = 0.3
        const val ACTION_MARKER_INNER_RING_RADIUS = 0.75
        const val ACTION_MARKER_INNER_RING_HEIGHT = 0.0
        const val ACTION_MARKER_MIDDLE_RING_RADIUS = 0.875
        const val ACTION_MARKER_MIDDLE_RING_HEIGHT = 0.15
        const val ACTION_MARKER_CENTER_Y_OFFSET = -0.25
        const val BARRIER_MARKER_HOLD_TICKS = 60
        const val MIDDLE_RING_MAX_ANGULAR_VELOCITY = 0.14
        const val MOB_TOKEN_DROP_CHANCE = 0.25
        const val MULTIPLAYER_JOIN_GRACE_SECONDS_DEFAULT = 45
        const val MULTIPLAYER_JOIN_MARKER_SEARCH_RADIUS_DEFAULT = 16.0
        const val MULTIPLAYER_MAX_PARTICIPANTS_DEFAULT = 6
        const val MULTIPLAYER_STAGE_INTRO_SOUND_REPEAT = 5
        const val MULTIPLAYER_STAGE_INTRO_SOUND_INTERVAL_TICKS = 10L
        const val MULTIPLAYER_STAGE_INTRO_TELEPORT_EXTRA_DELAY_TICKS = 20L
        const val MULTIPLAYER_INVITE_SWING_COOLDOWN_TICKS = 5L

        fun defaultSwitchBeats(loopBeats: Int): List<Int> {
            val safeLoopBeats = loopBeats.coerceAtLeast(1)
            val beats = (8..safeLoopBeats step 8).toList()
            return if (beats.isEmpty()) listOf(safeLoopBeats) else beats
        }
    }

    private val random = kotlin.random.Random.Default
    private val legacySerializer = LegacyComponentSerializer.legacySection()
    private val themeLoader = ArenaThemeLoader(plugin)
    private val stageGenerator = ArenaStageGenerator()
    private val sessionsByWorld = mutableMapOf<String, ArenaSession>()
    private val playerToSessionWorld = mutableMapOf<UUID, String>()
    private val mobToSessionWorld = mutableMapOf<UUID, String>()
    private val pendingWorldDeletions = mutableMapOf<String, PendingWorldDeletion>()
    private val invitedPlayerLocks = mutableMapOf<UUID, String>()
    private val inviteSwingCooldownUntilTick = mutableMapOf<UUID, Long>()
    private val difficultyConfigs = mutableMapOf<String, ArenaDifficultyConfig>()
    private val mobDefinitions = mutableMapOf<String, MobDefinition>()
    private val knownMobTypeIds = mutableSetOf<String>()
    private val mobToDefinitionTypeId = mutableMapOf<UUID, String>()
    private var maintenanceTask: BukkitTask? = null
    private var playerMonitorTask: BukkitTask? = null
    private var actionMarkerTask: BukkitTask? = null
    private var barrierRestartConfig = BarrierRestartConfig(30, 0.05)
    private var multiplayerJoinGraceSeconds = MULTIPLAYER_JOIN_GRACE_SECONDS_DEFAULT
    private var multiplayerJoinMarkerSearchRadius = MULTIPLAYER_JOIN_MARKER_SEARCH_RADIUS_DEFAULT
    private var multiplayerStageBuildStepsPerTick = 1
    private var actionMarkerHoldTicks = 80
    private var actionMarkerColorTransitionTicks = 16
    private var doorAnimationTotalTicks = DOOR_ANIMATION_TOTAL_TICKS_DEFAULT
    private var sharedWaveMaxAlive = SHARED_WAVE_MAX_ALIVE_DEFAULT
    private var arenaBgmConfig = ArenaBgmConfig(
        normal = ArenaBgmTrackConfig(
            soundKey = ARENA_BGM_NORMAL_KEY_DEFAULT,
            bpm = ARENA_BGM_NORMAL_BPM_DEFAULT,
            loopBeats = ARENA_BGM_LOOP_BEATS_DEFAULT,
            switchBeats = defaultSwitchBeats(ARENA_BGM_LOOP_BEATS_DEFAULT)
        ),
        combat = ArenaBgmTrackConfig(
            soundKey = ARENA_BGM_COMBAT_KEY_DEFAULT,
            bpm = ARENA_BGM_COMBAT_BPM_DEFAULT,
            loopBeats = ARENA_BGM_LOOP_BEATS_DEFAULT,
            switchBeats = defaultSwitchBeats(ARENA_BGM_LOOP_BEATS_DEFAULT)
        )
    )
    private var dropConfig = ArenaDropConfig(
        additionalDefaultDrops = emptyList(),
        additionalByMobType = emptyMap(),
        mockItemCountBonus = 0,
        mockExpBonusRate = 1.0,
        mockRarity = "common"
    )

    fun initialize(featureInitLogger: FeatureInitializationLogger? = null) {
        loadBattleConfigs()
        themeLoader.load(featureInitLogger)
        startMaintenanceTask()
    }

    fun reloadThemes(featureInitLogger: FeatureInitializationLogger? = null) {
        loadBattleConfigs()
        themeLoader.load(featureInitLogger)
    }

    fun getDifficultyIds(): Set<String> = difficultyConfigs.keys

    private fun loadBattleConfigs() {
        val difficultyFile = ensureArenaConfig("config/arena/difficulty.yml")
        val dropFile = ensureArenaConfig("config/arena/drop.yml")

        loadBarrierRestartConfig()
        loadArenaBgmConfig()
        loadDifficultyConfigs(difficultyFile)
        loadMobDefinitions()
        loadDropConfig(dropFile)
    }

    private fun loadBarrierRestartConfig() {
        val config = CoreConfigManager.get(plugin)
        barrierRestartConfig = BarrierRestartConfig(
            defaultDurationSeconds = config.getInt("arena.barrier_restart.default_duration_seconds", 30).coerceAtLeast(1),
            corruptionRatioBase = config.getDouble("arena.barrier_restart.corruption_ratio_base", 0.05).coerceAtLeast(0.0)
        )
        multiplayerJoinGraceSeconds = config.getInt(
            "arena.multiplayer.join_grace_seconds",
            MULTIPLAYER_JOIN_GRACE_SECONDS_DEFAULT
        ).coerceAtLeast(1)
        multiplayerJoinMarkerSearchRadius = config.getDouble(
            "arena.multiplayer.join_marker_search_radius",
            MULTIPLAYER_JOIN_MARKER_SEARCH_RADIUS_DEFAULT
        ).coerceAtLeast(1.0)
        multiplayerStageBuildStepsPerTick = config
            .getInt("arena.multiplayer.stage_build_steps_per_tick", 1)
            .coerceAtLeast(1)
        doorAnimationTotalTicks = config.getInt("arena.door_animation.total_ticks", DOOR_ANIMATION_TOTAL_TICKS_DEFAULT)
            .coerceAtLeast(1)
        sharedWaveMaxAlive = config.getInt("arena.mob_spawn.system_max_alive", SHARED_WAVE_MAX_ALIVE_DEFAULT)
            .coerceAtLeast(1)
    }

    private fun loadArenaBgmConfig() {
        val config = CoreConfigManager.get(plugin)
        arenaBgmConfig = ArenaBgmConfig(
            normal = parseArenaBgmTrackConfig(
                config,
                "arena.bgm.normal",
                ARENA_BGM_NORMAL_KEY_DEFAULT,
                ARENA_BGM_NORMAL_BPM_DEFAULT,
                ARENA_BGM_LOOP_BEATS_DEFAULT
            ),
            combat = parseArenaBgmTrackConfig(
                config,
                "arena.bgm.combat",
                ARENA_BGM_COMBAT_KEY_DEFAULT,
                ARENA_BGM_COMBAT_BPM_DEFAULT,
                ARENA_BGM_LOOP_BEATS_DEFAULT
            )
        )
    }

    private fun parseArenaBgmTrackConfig(
        config: FileConfiguration,
        path: String,
        defaultSoundKey: String,
        defaultBpm: Double,
        defaultLoopBeats: Int
    ): ArenaBgmTrackConfig {
        val soundKey = config.getString("$path.key")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: defaultSoundKey
        val bpm = config.getDouble("$path.bpm", defaultBpm)
            .coerceAtLeast(1.0)
        val loopBeats = config.getInt("$path.loop_beats", defaultLoopBeats)
            .coerceAtLeast(1)
        val rawSwitchBeats = config.getIntegerList("$path.switch_beats")
        val switchBeats = rawSwitchBeats
            .asSequence()
            .map { it.coerceAtLeast(1) }
            .filter { it <= loopBeats }
            .distinct()
            .sorted()
            .toList()
            .ifEmpty { defaultSwitchBeats(loopBeats) }

        if (rawSwitchBeats.isNotEmpty() && switchBeats.isEmpty()) {
            plugin.logger.warning("[Arena] $path.switch_beats が無効値のみだったためデフォルト値を使用します")
        }

        return ArenaBgmTrackConfig(
            soundKey = soundKey,
            bpm = bpm,
            loopBeats = loopBeats,
            switchBeats = switchBeats
        )
    }

    private fun ensureArenaConfig(resourcePath: String): File {
        val file = File(plugin.dataFolder, resourcePath)
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource(resourcePath, false)
        }
        return file
    }

    private fun loadDifficultyConfigs(file: File) {
        val config = YamlConfiguration.loadConfiguration(file)
        val loaded = mutableMapOf<String, ArenaDifficultyConfig>()

        for (difficultyId in config.getKeys(false)) {
            val section = config.getConfigurationSection(difficultyId)
            if (section == null) {
                plugin.logger.severe("[Arena] difficulty.yml の読み込み失敗: $difficultyId")
                continue
            }

            val difficultyRange = parseDifficultyRange(section.getString("difficulty_range"))
            if (difficultyRange == null) {
                plugin.logger.severe("[Arena] difficulty.yml の difficulty_range が不正です: $difficultyId")
                continue
            }
            val mobSpawnIntervalTicks = section.getLong("mob_spawn_interval", 20L).coerceAtLeast(1L)
            val mobCountMultiplier = section.getDouble("mob_count_multiplier", 1.0).coerceAtLeast(0.01)
            val waveMultiplier = section.getDouble("wave_multiplier", 0.0)
            val waves = section.getInt("wave", 5).coerceAtLeast(1)
            val display = section.getString("display", difficultyId) ?: difficultyId
            val mobStatsMaxHealth = section.getDouble("mob_stats_max_health", 20.0).coerceAtLeast(1.0)
            val mobStatsMaxAttack = section.getDouble("mob_stats_max_attack", 1.0).coerceAtLeast(0.0)
            val mobStatsMaxMovementSpeed = section.getDouble("mob_stats_max_movement_speed", 0.23).coerceAtLeast(0.01)
            val mobStatsMaxArmor = section.getDouble("mob_stats_max_armor", 0.0).coerceAtLeast(0.0)
            val mobStatsMaxScale = section.getDouble("mob_stats_max_scale", 1.0).coerceAtLeast(0.1)

            loaded[difficultyId] = ArenaDifficultyConfig(
                id = difficultyId,
                difficultyRange = difficultyRange,
                mobSpawnIntervalTicks = mobSpawnIntervalTicks,
                mobCountMultiplier = mobCountMultiplier,
                waveMultiplier = waveMultiplier,
                waves = waves,
                display = display,
                mobStatsMaxHealth = mobStatsMaxHealth,
                mobStatsMaxAttack = mobStatsMaxAttack,
                mobStatsMaxMovementSpeed = mobStatsMaxMovementSpeed,
                mobStatsMaxArmor = mobStatsMaxArmor,
                mobStatsMaxScale = mobStatsMaxScale
            )
        }

        difficultyConfigs.clear()
        difficultyConfigs.putAll(loaded)

        if (difficultyConfigs.isEmpty()) {
            plugin.logger.severe("[Arena] difficulty.yml が空のためアリーナを開始できません")
        }
    }

    private fun loadMobDefinitions() {
        val loaded = mobService.reloadDefinitions()
        mobDefinitions.clear()
        mobDefinitions.putAll(loaded)
        knownMobTypeIds.clear()
        knownMobTypeIds.addAll(loaded.values.map { it.typeId.trim().lowercase(Locale.ROOT) })
        registerMobTypeTokenItems()

        if (mobDefinitions.isEmpty()) {
            plugin.logger.severe("[Arena] config/mob_definition.yml が空のためアリーナを開始できません")
        }
    }

    private fun loadDropConfig(file: File) {
        val config = YamlConfiguration.loadConfiguration(file)
        val mockItemCountBonus = config.getInt("difficulty_mock.item_count_bonus", 0)
        val mockExpBonusRate = config.getDouble("difficulty_mock.exp_bonus_rate", 1.0)
            .coerceAtLeast(0.0)
        val mockRarity = config.getString("difficulty_mock.rarity", "common") ?: "common"

        val additionalDefaultDrops = parseDropEntries(config, "additional_drops.default")
        val additionalByMobType = mutableMapOf<String, List<ArenaDropEntry>>()
        val byMobTypeSection = config.getConfigurationSection("additional_drops.by_mob_type")
        byMobTypeSection?.getKeys(false)?.forEach { mobTypeId ->
            val key = mobTypeId.trim().lowercase(Locale.ROOT)
            additionalByMobType[key] = parseDropEntries(config, "additional_drops.by_mob_type.$mobTypeId")
        }

        dropConfig = ArenaDropConfig(
            additionalDefaultDrops = additionalDefaultDrops,
            additionalByMobType = additionalByMobType,
            mockItemCountBonus = mockItemCountBonus,
            mockExpBonusRate = mockExpBonusRate,
            mockRarity = mockRarity
        )
    }

    private fun parseDropEntries(config: YamlConfiguration, path: String): List<ArenaDropEntry> {
        val raw = config.getMapList(path)
        val entries = mutableListOf<ArenaDropEntry>()
        for (entry in raw) {
            val itemId = entry["item"]?.toString()?.trim().orEmpty()
            if (itemId.isBlank()) {
                continue
            }
            val baseAmount = entry["amount"]?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            entries.add(ArenaDropEntry(itemId = itemId, baseAmount = baseAmount))
        }
        return entries
    }

    private fun parseDifficultyRange(text: String?): ClosedFloatingPointRange<Double>? {
        val raw = text?.trim().orEmpty()
        val matched = Regex("^([0-9]+(?:\\.[0-9]+)?)\\.\\.([0-9]+(?:\\.[0-9]+)?)$").matchEntire(raw) ?: return null
        val min = matched.groupValues[1].toDoubleOrNull() ?: return null
        val max = matched.groupValues[2].toDoubleOrNull() ?: return null
        if (min <= 0.0) return null
        if (max < min) return null
        return min..max
    }

    fun startSession(
        target: Player,
        difficultyId: String,
        requestedTheme: String?,
        questModifiers: ArenaQuestModifiers = ArenaQuestModifiers.NONE,
        difficultyScore: Double? = null,
        enableMultiplayerJoin: Boolean = false,
        inviteQuestTitle: String? = null,
        inviteQuestLore: List<String> = emptyList(),
        maxParticipants: Int = MULTIPLAYER_MAX_PARTICIPANTS_DEFAULT
    ): ArenaStartResult {
        if (playerToSessionWorld.containsKey(target.uniqueId)) {
            return ArenaStartResult.Error(
                "arena.messages.command.start_error.already_in_session",
                "&c{player} はすでにアリーナセッション中です",
                arrayOf("player" to target.name)
            )
        }

        val difficulty = difficultyConfigs[difficultyId]
            ?: return ArenaStartResult.Error(
                "arena.messages.command.start_error.difficulty_not_found",
                "&cdifficulty が見つかりません: {difficulty}",
                arrayOf("difficulty" to difficultyId)
            )

        val theme = if (requestedTheme.isNullOrBlank()) {
            themeLoader.getRandomTheme(random)
        } else {
            themeLoader.getTheme(requestedTheme)
        } ?: return ArenaStartResult.Error(
            "arena.messages.command.start_error.theme_not_found",
            "&c有効なテーマが見つかりません"
        )

        val undefinedWave = (1..difficulty.waves).firstOrNull { wave ->
            selectSpawnCandidates(theme, wave).isEmpty()
        }
        if (undefinedWave != null) {
            plugin.logger.severe(
                "[Arena] セッション開始失敗: themeスポーン未定義ウェーブがあります " +
                    "theme=${theme.id} difficulty=$difficultyId wave=$undefinedWave"
            )
            return ArenaStartResult.Error(
                "arena.messages.command.start_error.undefined_wave_spawn",
                "&ctheme '{theme}' は wave {wave} のスポーンが未定義です",
                arrayOf("theme" to theme.id, "mob_type" to theme.id, "wave" to undefinedWave)
            )
        }

        val joinAreaMarkers = if (enableMultiplayerJoin) {
            findNearbyJoinAreaMarkers(target.location, multiplayerJoinMarkerSearchRadius)
        } else {
            emptyList()
        }
        if (enableMultiplayerJoin && joinAreaMarkers.isEmpty()) {
            return ArenaStartResult.Error(
                "arena.messages.command.start_error.join_area_not_found",
                "&c開始待機エリアの参加マーカーが近くに存在しないため開始できません"
            )
        }

        val world = createArenaWorld() ?: return ArenaStartResult.Error(
            "arena.messages.command.start_error.world_create_failed",
            "&cアリーナ用ワールド作成に失敗しました"
        )

        val sanitizedMaxParticipants = maxParticipants.coerceIn(1, MULTIPLAYER_MAX_PARTICIPANTS_DEFAULT)
        val returnLocation = target.location.clone()
        val origin = Location(world, 0.0, 64.0, 0.0)
        val now = System.currentTimeMillis()

        val placeholderLocation = Location(world, 0.0, 64.0, 0.0)
        val placeholderBounds = ArenaBounds(0, 0, 0, 0, 0, 0)
        val session = ArenaSession(
            ownerPlayerId = target.uniqueId,
            worldName = world.name,
            themeId = theme.id,
            difficultyId = difficulty.id,
            difficultyValue = difficulty.difficultyValue,
            difficultyScore = difficultyScore ?: difficulty.difficultyValue,
            waves = difficulty.waves,
            questModifiers = questModifiers,
            maxParticipants = sanitizedMaxParticipants,
            participants = mutableSetOf(target.uniqueId),
            returnLocations = mutableMapOf(target.uniqueId to returnLocation),
            playerSpawn = placeholderLocation.clone(),
            entranceLocation = placeholderLocation.clone(),
            stageBounds = placeholderBounds,
            roomBounds = mutableMapOf(),
            corridorBounds = mutableMapOf(),
            roomMobSpawns = mutableMapOf(),
            corridorDoorBlocks = mutableMapOf(),
            doorAnimationPlacements = mutableMapOf(),
            barrierLocation = placeholderLocation.clone(),
            barrierPointLocations = mutableListOf(),
            joinAreaMarkerLocations = joinAreaMarkers.map { it.clone() }.toMutableList(),
            participantSpawnProtectionUntilMillis = if (enableMultiplayerJoin) Long.MAX_VALUE else now + 4000L,
            multiplayerJoinEnabled = enableMultiplayerJoin,
            joinGraceStartMillis = if (enableMultiplayerJoin) now else 0L,
            joinGraceDurationMillis = if (enableMultiplayerJoin) multiplayerJoinGraceSeconds * 1000L else 0L,
            joinGraceEndMillis = if (enableMultiplayerJoin) now + (multiplayerJoinGraceSeconds * 1000L) else 0L,
            inviteQuestTitle = inviteQuestTitle,
            inviteQuestLore = inviteQuestLore,
            stageGenerationCompleted = !enableMultiplayerJoin
        )

        sessionsByWorld[world.name] = session
        playerToSessionWorld[target.uniqueId] = world.name

        return try {
            if (enableMultiplayerJoin) {
                target.showBossBar(getOrCreateJoinCountdownBossBar(session, target.uniqueId))
                target.sendMessage(
                    ArenaI18n.text(
                        target,
                        "arena.messages.multiplayer.invite_window_started",
                        "&e左クリックで近くのプレイヤーを招待してください（{seconds}秒）",
                        "seconds" to multiplayerJoinGraceSeconds
                    )
                )
                target.sendMessage(
                    ArenaI18n.text(
                        target,
                        "arena.messages.multiplayer.wait_area_hint",
                        "&7招待したプレイヤーと開始待機エリアに入ると待機状態になります"
                    )
                )

                session.stageBuildTask = stageGenerator.buildIncrementally(
                    plugin = plugin,
                    world = world,
                    origin = origin,
                    theme = theme,
                    waves = difficulty.waves,
                    random = random,
                    stepsPerTick = multiplayerStageBuildStepsPerTick
                ) { buildResult ->
                    val active = sessionsByWorld[session.worldName]
                    if (active !== session) {
                        return@buildIncrementally
                    }
                    session.stageBuildTask = null

                    buildResult
                        .onSuccess { built ->
                            applyStageBuildResult(session, built)
                            initializeActionMarkers(session)
                            initializeBarrierRestartState(session)
                            startBarrierAmbientTask(session)
                            initializeWavePipeline(session, theme, difficulty)
                            updateSessionProgressBossBar(session)
                            session.stageGenerationCompleted = true
                        }
                        .onFailure { throwable ->
                            if (throwable is ArenaStageBuildException) {
                                plugin.logger.severe("[Arena] ステージの生成に失敗しました: ${throwable.message}")
                                throwable.printStackTrace()
                            }
                            terminateSession(
                                session,
                                false,
                                messageKey = "arena.messages.command.start_error.stage_build_failed",
                                fallbackMessage = "&cステージの生成に失敗しました"
                            )
                        }
                }
            } else {
                val stage = stageGenerator.build(world, origin, theme, difficulty.waves, random)
                applyStageBuildResult(session, stage)
                initializeActionMarkers(session)
                initializeBarrierRestartState(session)
                startBarrierAmbientTask(session)
                session.stageGenerationCompleted = true

                session.participants.forEach { participantId ->
                    val participant = Bukkit.getPlayer(participantId) ?: return@forEach
                    if (!participant.isOnline) return@forEach
                    val spawnLocation = session.entranceLocation.clone()
                    applyStageStartFacingYaw(session, spawnLocation)
                    participant.teleport(spawnLocation)
                }
                requestArenaBgmMode(session, ArenaBgmMode.NORMAL)
                setDoorActionMarkersReadySilently(session, 1)
                initializeWavePipeline(session, theme, difficulty)
                updateSessionProgressBossBar(session)
            }
            target.sendMessage(
                ArenaI18n.text(
                    target,
                    "arena.messages.session.started",
                    "&6[Arena] セッション開始: theme={theme}, difficulty={difficulty}, waves={waves}",
                    "theme" to theme.id,
                    "mob_type" to theme.id,
                    "difficulty" to difficulty.display,
                    "waves" to difficulty.waves
                )
            )
            ArenaStartResult.Success(theme.id, difficulty.waves, difficulty.id, difficulty.display)
        } catch (e: Exception) {
            if (e is ArenaStageBuildException) {
                plugin.logger.severe("[Arena] ステージの生成に失敗しました: ${e.message}")
                e.printStackTrace()
            }
            val failedSession = sessionsByWorld[world.name]
            if (failedSession != null) terminateSession(failedSession, false) else tryDeleteWorld(world)
            ArenaStartResult.Error(
                "arena.messages.command.start_error.stage_build_failed",
                "&c{message}",
                arrayOf("message" to (e.message ?: "unknown"))
            )
        }
    }

    fun stopSession(player: Player, reason: String = ArenaI18n.text(player, "arena.messages.session.ended", "&cアリーナセッションが終了しました")): Boolean {
        return leavePlayerFromSession(player.uniqueId, reason)
    }

    fun stopSessionById(playerId: UUID, reason: String? = null): Boolean {
        val player = Bukkit.getPlayer(playerId)
        val localizedReason = reason ?: ArenaI18n.text(player, "arena.messages.session.ended", "&cアリーナセッションが終了しました")
        return leavePlayerFromSession(playerId, localizedReason)
    }

    fun shutdown() {
        maintenanceTask?.cancel()
        maintenanceTask = null
        playerMonitorTask?.cancel()
        playerMonitorTask = null
        actionMarkerTask?.cancel()
        actionMarkerTask = null

        sessionsByWorld.values.toList().forEach { session ->
            terminateSession(
                session,
                false,
                messageKey = "arena.messages.session.ended_by_shutdown",
                fallbackMessage = "&cサーバー停止によりアリーナを終了しました"
            )
        }
        sessionsByWorld.clear()
        playerToSessionWorld.clear()
        mobToSessionWorld.clear()
        mobToDefinitionTypeId.clear()
        invitedPlayerLocks.clear()
        inviteSwingCooldownUntilTick.clear()
        processPendingWorldDeletions()
    }

    fun getSession(player: Player): ArenaSession? {
        val worldName = playerToSessionWorld[player.uniqueId] ?: return null
        return sessionsByWorld[worldName]
    }

    fun getThemeIds(): Set<String> = themeLoader.getThemeIds()

    fun getActiveSessionPlayerNames(): Set<String> {
        return playerToSessionWorld.keys.mapNotNull { uuid -> Bukkit.getPlayer(uuid)?.name }.toSet()
    }

    fun handleInviteTargetUnavailable(player: Player) {
        val worldName = invitedPlayerLocks[player.uniqueId] ?: return
        val session = sessionsByWorld[worldName] ?: run {
            invitedPlayerLocks.remove(player.uniqueId)
            player.isGlowing = false
            return
        }

        removeInvitedParticipant(session, player.uniqueId)
    }

    private fun applyStageBuildResult(session: ArenaSession, stage: jp.awabi2048.cccontent.features.arena.generator.ArenaStageBuildResult) {
        session.playerSpawn = stage.playerSpawn.clone()
        session.entranceLocation = stage.entranceLocation.clone()
        session.stageBounds = stage.stageBounds
        session.roomBounds.clear()
        session.roomBounds.putAll(stage.roomBounds)
        session.corridorBounds.clear()
        session.corridorBounds.putAll(stage.corridorBounds)
        session.roomMobSpawns.clear()
        session.roomMobSpawns.putAll(stage.roomMobSpawns)
        session.corridorDoorBlocks.clear()
        session.corridorDoorBlocks.putAll(stage.corridorDoorBlocks)
        session.doorAnimationPlacements.clear()
        session.doorAnimationPlacements.putAll(stage.doorAnimationPlacements)
        session.barrierLocation = stage.barrierLocation.clone()
        session.barrierPointLocations.clear()
        session.barrierPointLocations.addAll(stage.barrierPointLocations.map { it.clone() })

        val now = System.currentTimeMillis()
        session.participantLocationHistory[session.ownerPlayerId] = ArrayDeque<TimedPlayerLocation>().apply {
            addLast(TimedPlayerLocation(now, session.entranceLocation.clone()))
        }
        session.participantLastSampleMillis[session.ownerPlayerId] = now
    }

    private fun releaseInvitedPlayerLock(playerId: UUID) {
        invitedPlayerLocks.remove(playerId)
        val player = Bukkit.getPlayer(playerId)
        if (player != null && player.isOnline) {
            player.isGlowing = false
        }
    }

    private fun hideJoinCountdownBossBar(session: ArenaSession, playerId: UUID) {
        val bossBar = session.joinCountdownBossBars.remove(playerId) ?: return
        val player = Bukkit.getPlayer(playerId)
        if (player != null && player.isOnline) {
            player.hideBossBar(bossBar)
        }
    }

    private fun clearMultiplayerRecruitmentState(session: ArenaSession) {
        val ownerId = session.ownerPlayerId
        hideJoinCountdownBossBar(session, ownerId)

        session.invitedParticipants.toList().forEach { invitedId ->
            hideJoinCountdownBossBar(session, invitedId)
            releaseInvitedPlayerLock(invitedId)
        }

        session.invitedAtMillis.clear()
        session.waitingNotifiedParticipants.clear()
        session.waitingParticipants.clear()
        session.invitedParticipants.clear()
    }

    private fun removeInvitedParticipant(session: ArenaSession, invitedId: UUID) {
        session.invitedParticipants.remove(invitedId)
        session.invitedAtMillis.remove(invitedId)
        session.waitingParticipants.remove(invitedId)
        session.waitingNotifiedParticipants.remove(invitedId)
        hideJoinCountdownBossBar(session, invitedId)
        releaseInvitedPlayerLock(invitedId)
    }

    private fun declineInvitedParticipant(session: ArenaSession, invited: Player) {
        removeInvitedParticipant(session, invited.uniqueId)
        invited.sendMessage(
            ArenaI18n.text(
                invited,
                "arena.messages.multiplayer.declined_self",
                "&e招待を辞退しました"
            )
        )
        val owner = Bukkit.getPlayer(session.ownerPlayerId)
        if (owner != null && owner.isOnline) {
            owner.sendMessage(
                ArenaI18n.text(
                    owner,
                    "arena.messages.multiplayer.declined_notify_owner",
                    "&e{player} が招待を辞退しました",
                    "player" to invited.name
                )
            )
        }
    }

    private fun monitorParticipantPositions() {
        for (session in sessionsByWorld.values.toList()) {
            if (session.multiplayerJoinEnabled) {
                continue
            }
            val now = System.currentTimeMillis()
            if (now < session.participantSpawnProtectionUntilMillis) {
                continue
            }
            val world = Bukkit.getWorld(session.worldName) ?: continue

            for (participantId in session.participants.toList()) {
                val player = Bukkit.getPlayer(participantId)
                if (player == null || !player.isOnline) continue

                val location = player.location
                val currentWave = session.currentWave.coerceAtLeast(1)
                if (location.world?.name != world.name || !session.stageBounds.contains(location.x, location.y, location.z)) {
                    if (location.world?.name != world.name) {
                        stopSessionById(
                            participantId,
                            ArenaI18n.text(player, "arena.messages.session.ended_by_warp", "&cワープしたため死亡扱いでアリーナを終了しました")
                        )
                    } else {
                        teleportToRecentValidPosition(player, session, world, currentWave, applyBlindness = true)
                    }
                    continue
                }

                val room = locateRoom(session, location)
                if (room != null && room > currentWave) {
                    teleportToCurrentWavePosition(player, session, world, applyBlindness = true)
                    continue
                }
                if (room != null && room > 0) {
                    processRoomProgress(session, player, room)
                }

                val corridorWave = locateCorridorTargetWave(session, location)
                if (corridorWave != null) {
                    if (!session.openedCorridors.contains(corridorWave)) {
                        teleportToCurrentWavePosition(player, session, world, applyBlindness = true)
                        continue
                    }
                    if (corridorWave == currentWave) {
                        if (session.startedWaves.contains(corridorWave) &&
                            (corridorWave <= 1 || session.clearedWaves.contains(corridorWave - 1)) &&
                            session.corridorTriggeredWaves.add(corridorWave)
                        ) {
                            rebalanceTargetsForWave(session, corridorWave)
                        }
                    }
                }

                recordParticipantValidLocation(session, participantId, location)
            }
        }
    }

    fun handleMobDeath(event: EntityDeathEvent) {
        val entityId = event.entity.uniqueId
        applyArenaMobDrop(event)
        val worldName = mobToSessionWorld.remove(entityId) ?: return
        val session = sessionsByWorld[worldName] ?: return
        session.barrierDefenseMobIds.remove(entityId)
        session.barrierDefenseTargetMobIds.remove(entityId)
        session.barrierDefenseAssaultMobIds.remove(entityId)
        val killer = event.entity.killer
        val countKill = killer != null && session.participants.contains(killer.uniqueId)
        consumeMob(session, entityId, countKill = countKill)
    }

    fun handleMobFallDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.FALL) return
        val entity = event.entity as? LivingEntity ?: return
        if (entity is Player) return
        if (!mobToSessionWorld.containsKey(entity.uniqueId)) return
        event.isCancelled = true
    }

    fun handleMobFriendlyFire(event: EntityDamageByEntityEvent) {
        val damaged = event.entity as? LivingEntity ?: return
        if (damaged is Player) return

        val damagedWorld = mobToSessionWorld[damaged.uniqueId] ?: return
        val attackerMobId = resolveArenaMobAttackerId(event) ?: return
        val attackerWorld = mobToSessionWorld[attackerMobId] ?: return
        if (damagedWorld != attackerWorld) return
    }

    fun handleMultiplayerInviteSwing(event: PlayerAnimationEvent) {
        if (event.animationType != PlayerAnimationType.ARM_SWING) {
            return
        }

        val player = event.player
        val currentTick = Bukkit.getCurrentTick().toLong()
        val cooldownUntil = inviteSwingCooldownUntilTick[player.uniqueId] ?: Long.MIN_VALUE
        if (currentTick < cooldownUntil) {
            return
        }

        val ownerSession = getSession(player)
        if (ownerSession != null && ownerSession.multiplayerJoinEnabled && ownerSession.ownerPlayerId == player.uniqueId) {
            inviteSwingCooldownUntilTick[player.uniqueId] = currentTick + MULTIPLAYER_INVITE_SWING_COOLDOWN_TICKS

            if (System.currentTimeMillis() >= ownerSession.joinGraceEndMillis) {
                player.sendMessage(
                    ArenaI18n.text(
                        player,
                        "arena.messages.multiplayer.invite_window_closed",
                        "&c募集時間は終了しました"
                    )
                )
                return
            }

            val invited = rayTraceTargetPlayer(player) ?: return
            trySendMultiplayerInvite(player, invited, ownerSession)
            return
        }

        val lockedWorld = invitedPlayerLocks[player.uniqueId] ?: return
        val invitedSession = sessionsByWorld[lockedWorld] ?: run {
            invitedPlayerLocks.remove(player.uniqueId)
            player.isGlowing = false
            return
        }
        if (!invitedSession.multiplayerJoinEnabled) {
            return
        }
        if (!invitedSession.invitedParticipants.contains(player.uniqueId)) {
            return
        }

        val owner = Bukkit.getPlayer(invitedSession.ownerPlayerId) ?: return
        if (!owner.isOnline || owner.world.uid != player.world.uid) {
            return
        }

        inviteSwingCooldownUntilTick[player.uniqueId] = currentTick + MULTIPLAYER_INVITE_SWING_COOLDOWN_TICKS
        val target = rayTraceTargetPlayer(player) ?: return
        if (target.uniqueId != owner.uniqueId) {
            return
        }
        declineInvitedParticipant(invitedSession, player)
    }

    private fun rayTraceTargetPlayer(source: Player): Player? {
        val maxDistance = (source.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)?.value ?: 3.0).coerceAtLeast(1.0)
        val eyeLocation = source.eyeLocation
        val hit = source.world.rayTrace(
            eyeLocation,
            eyeLocation.direction,
            maxDistance,
            FluidCollisionMode.NEVER,
            true,
            0.2
        ) { candidate ->
            candidate is Player && candidate.uniqueId != source.uniqueId
        }
        return hit?.hitEntity as? Player
    }

    private fun trySendMultiplayerInvite(owner: Player, invited: Player, session: ArenaSession) {
        val currentInvitableCount = 1 + session.invitedParticipants.size
        if (currentInvitableCount >= session.maxParticipants) {
            owner.sendMessage(
                ArenaI18n.text(
                    owner,
                    "arena.messages.multiplayer.invite_failed_full",
                    "&cこのクエストの最大参加人数に達しているため招待できません"
                )
            )
            return
        }

        if (playerToSessionWorld.containsKey(invited.uniqueId)) {
            owner.sendMessage(
                ArenaI18n.text(
                    owner,
                    "arena.messages.multiplayer.invite_failed_in_session",
                    "&c{player} はすでに別のアリーナに参加しています",
                    "player" to invited.name
                )
            )
            return
        }

        if (invited.world.uid != owner.world.uid) {
            owner.sendMessage(
                ArenaI18n.text(
                    owner,
                    "arena.messages.multiplayer.invite_failed_world",
                    "&c同じワールド内のプレイヤーのみ招待できます"
                )
            )
            return
        }

        val lockedWorld = invitedPlayerLocks[invited.uniqueId]
        if (lockedWorld != null && lockedWorld != session.worldName) {
            owner.sendMessage(
                ArenaI18n.text(
                    owner,
                    "arena.messages.multiplayer.invite_failed_already_locked",
                    "&c{player} はすでに他の招待を受けています",
                    "player" to invited.name
                )
            )
            return
        }

        if (!session.invitedParticipants.add(invited.uniqueId)) {
            owner.sendMessage(
                ArenaI18n.text(
                    owner,
                    "arena.messages.multiplayer.already_invited",
                    "&e{player} はすでに招待済みです",
                    "player" to invited.name
                )
            )
            return
        }

        session.returnLocations.putIfAbsent(invited.uniqueId, invited.location.clone())
        invitedPlayerLocks[invited.uniqueId] = session.worldName
        invited.isGlowing = true
        session.invitedAtMillis[invited.uniqueId] = System.currentTimeMillis()
        invited.showBossBar(getOrCreateJoinCountdownBossBar(session, invited.uniqueId))
        owner.sendMessage(
            ArenaI18n.text(
                owner,
                "arena.messages.multiplayer.invite_sent",
                "&a{player} に招待を送信しました",
                "player" to invited.name
            )
        )
        invited.sendMessage(buildInviteMessageComponent(owner.name, session))
        invited.sendMessage(
            ArenaI18n.text(
                invited,
                "arena.messages.multiplayer.invited_decline_hint",
                "&7辞退する場合は、招待者を左クリックしてください"
            )
        )
    }

    private fun buildInviteMessageComponent(ownerName: String, session: ArenaSession): Component {
        val questTitle = session.inviteQuestTitle
        if (questTitle.isNullOrBlank()) {
            return legacySerializer.deserialize(
                "§8§l«§c§lArena§8§l» §7${ownerName}さんがあなたをアリーナに招待しました！"
            )
        }

        val hoverText = if (session.inviteQuestLore.isEmpty()) {
            "§7説明はありません"
        } else {
            session.inviteQuestLore.joinToString("\n")
        }

        val prefix = legacySerializer.deserialize("§8§l«§c§lArena§8§l» §7${ownerName}さんがあなたをクエスト§e")
        val questPart = legacySerializer.deserialize("§e【${questTitle}】")
            .hoverEvent(HoverEvent.showText(legacySerializer.deserialize(hoverText)))
        val suffix = legacySerializer.deserialize("§7に招待しました！")

        return Component.empty()
            .append(prefix)
            .append(questPart)
            .append(suffix)
    }

    fun handleMobTarget(event: EntityTargetLivingEntityEvent) {
        val mob = event.entity as? Mob ?: return
        val worldName = mobToSessionWorld[mob.uniqueId] ?: return
        val session = sessionsByWorld[worldName] ?: return

        val target = event.target as? LivingEntity ?: return
        if (target !is Player) {
            val targetWorld = mobToSessionWorld[target.uniqueId]
            if (targetWorld == worldName) {
                event.isCancelled = true
                mob.target = selectNearestParticipant(session, mob.location)
            }
            return
        }

        val targetPlayer = target
        if (!session.participants.contains(targetPlayer.uniqueId)) {
            event.isCancelled = true
            mob.target = selectNearestParticipant(session, mob.location)
        }
    }

    private fun resolveArenaMobAttackerId(event: EntityDamageByEntityEvent): UUID? {
        val damager = event.damager
        if (damager is LivingEntity && damager !is Player) {
            return damager.uniqueId
        }

        if (damager is Projectile) {
            val shooter = damager.shooter as? LivingEntity ?: return null
            if (shooter is Player) return null
            return shooter.uniqueId
        }

        return null
    }

    private fun selectNearestParticipant(session: ArenaSession, origin: Location): Player? {
        val world = Bukkit.getWorld(session.worldName) ?: return null
        return session.participants
            .mapNotNull { participantId -> Bukkit.getPlayer(participantId) }
            .filter { player ->
                player.isOnline &&
                    player.world.name == world.name &&
                    !player.isDead &&
                    player.gameMode != org.bukkit.GameMode.SPECTATOR
            }
            .minByOrNull { player ->
                player.location.distanceSquared(origin)
            }
    }

    private fun applyArenaMobDrop(event: EntityDeathEvent) {
        val entity = event.entity
        if (!mobToSessionWorld.containsKey(entity.uniqueId)) {
            return
        }

        val killer = entity.killer
        val baseExp = event.droppedExp
        val vanillaDrops = event.drops.map { it.clone() }
        event.drops.clear()

        if (killer == null) {
            event.droppedExp = 0
            return
        }

        val definitionTypeId = mobToDefinitionTypeId[entity.uniqueId] ?: entity.type.name
        val normalizedTypeId = definitionTypeId.trim().lowercase(Locale.ROOT)
        val lootingLevel = resolveLootingLevel(killer.inventory)

        val rebuiltDrops = mutableListOf<ItemStack>()
        rebuiltDrops += rebuildVanillaNonEquipmentDrops(vanillaDrops)
        rebuiltDrops += buildConfiguredAdditionalDrops(killer, normalizedTypeId, lootingLevel)

        createMobTokenDrop(killer, normalizedTypeId)?.let { token ->
            rebuiltDrops += token
        }

        rebuiltDrops.forEach { stack ->
            if (stack.type.isAir || stack.amount <= 0) return@forEach
            event.drops += stack
        }
        event.droppedExp = floor(baseExp * dropConfig.mockExpBonusRate).toInt().coerceAtLeast(0)
    }

    private fun rebuildVanillaNonEquipmentDrops(vanillaDrops: List<ItemStack>): List<ItemStack> {
        return vanillaDrops
            .filter { !it.type.isAir && it.amount > 0 }
            .filterNot { classifyEquipmentDropType(it.type) != null }
            .map { it.clone() }
    }

    private fun buildConfiguredAdditionalDrops(killer: Player, mobTypeId: String, lootingLevel: Int): List<ItemStack> {
        val entries = mutableListOf<ArenaDropEntry>()
        entries += dropConfig.additionalDefaultDrops
        entries += dropConfig.additionalByMobType[mobTypeId].orEmpty()

        return entries.mapNotNull { entry ->
            val amount = applyAmountModifiers(entry.baseAmount, lootingLevel)
            resolveConfiguredDrop(entry.itemId, killer, amount)
        }
    }

    private fun resolveConfiguredDrop(itemId: String, killer: Player, amount: Int): ItemStack? {
        if (amount <= 0) return null
        CustomItemManager.createItemForPlayer(itemId, killer, amount)?.let { return it }
        val material = runCatching { Material.valueOf(itemId.uppercase(Locale.ROOT)) }.getOrNull() ?: return null
        if (!material.isItem || material.isAir) return null
        return ItemStack(material, amount)
    }

    private fun applyAmountModifiers(baseAmount: Int, lootingLevel: Int): Int {
        val base = baseAmount.coerceAtLeast(1)
        val mockBonus = dropConfig.mockItemCountBonus
        val lootingBonus = if (lootingLevel > 0) random.nextInt(lootingLevel + 1) else 0
        return (base + mockBonus + lootingBonus).coerceAtLeast(1)
    }

    private fun resolveLootingLevel(inventory: PlayerInventory): Int {
        val main = inventory.itemInMainHand
        val off = inventory.itemInOffHand
        val mainLevel = main.getEnchantmentLevel(Enchantment.LOOTING)
        val offLevel = off.getEnchantmentLevel(Enchantment.LOOTING)
        return maxOf(mainLevel, offLevel)
    }

    private fun classifyEquipmentDropType(material: Material): String? {
        val name = material.name
        return when {
            name.endsWith("_AXE") -> "axe"
            name.endsWith("_SWORD") -> "sword"
            name == "SHIELD" -> "shield"
            name == "BOW" -> "bow"
            name.endsWith("_HELMET") -> "helmet"
            name.endsWith("_CHESTPLATE") -> "chestplate"
            name.endsWith("_LEGGINGS") -> "leggings"
            name.endsWith("_BOOTS") -> "boots"
            else -> null
        }
    }

    private fun createMobTokenDrop(killer: Player, mobTypeId: String): ItemStack? {
        if (random.nextDouble() >= MOB_TOKEN_DROP_CHANCE) {
            return null
        }
        val fullId = "arena.mob_token_${sanitizeMobTypeId(mobTypeId)}"
        return CustomItemManager.createItemForPlayer(fullId, killer, 1)
    }

    private fun sanitizeMobTypeId(typeId: String): String {
        val normalized = typeId.trim().lowercase(Locale.ROOT)
        return normalized.replace(Regex("[^a-z0-9_]+"), "_")
    }

    private fun registerMobTypeTokenItems() {
        for (typeId in knownMobTypeIds) {
            CustomItemManager.register(ArenaMobTokenItem(typeId))
        }
    }

    fun activateDoorActionMarkers(worldName: String, wave: Int): Int {
        val session = sessionsByWorld[worldName] ?: return 0
        ensureDoorActionMarkersForTargetWave(session, wave)
        val currentTick = Bukkit.getCurrentTick().toLong()
        var changed = 0
        session.actionMarkers.values
            .asSequence()
            .filter { it.type == ArenaActionMarkerType.DOOR_TOGGLE && it.wave == wave }
            .forEach { marker ->
                if (transitionActionMarkerState(marker, ArenaActionMarkerState.READY, currentTick)) {
                    playActionMarkerReadyEffect(session, marker)
                    changed += 1
                }
            }
        return changed
    }

    private fun setDoorActionMarkersReadySilently(session: ArenaSession, wave: Int) {
        ensureDoorActionMarkersForTargetWave(session, wave)
        session.actionMarkers.values
            .asSequence()
            .filter { it.type == ArenaActionMarkerType.DOOR_TOGGLE && it.wave == wave }
            .forEach { marker ->
                marker.state = ArenaActionMarkerState.READY
                val color = marker.colorFor(ArenaActionMarkerState.READY)
                marker.colorTransitionFrom = color
                marker.colorTransitionTo = color
                marker.colorTransitionStartTick = Bukkit.getCurrentTick().toLong()
            }
    }

    fun activateBarrierActionMarker(worldName: String): Boolean {
        val session = sessionsByWorld[worldName] ?: return false
        val currentTick = Bukkit.getCurrentTick().toLong()
        var changed = false
        session.actionMarkers.values
            .asSequence()
            .filter { it.type == ArenaActionMarkerType.BARRIER_ACTIVATE }
            .forEach { marker ->
                if (transitionActionMarkerState(marker, ArenaActionMarkerState.READY, currentTick)) {
                    playActionMarkerReadyEffect(session, marker)
                    changed = true
                }
            }
        return changed
    }

    private fun leavePlayerFromSession(playerId: UUID, reason: String): Boolean {
        val worldName = playerToSessionWorld[playerId] ?: return false
        val session = sessionsByWorld[worldName] ?: return false

        if (session.ownerPlayerId == playerId) {
            session.stageBuildTask?.cancel()
            session.stageBuildTask = null
        }

        playerToSessionWorld.remove(playerId)
        inviteSwingCooldownUntilTick.remove(playerId)
        session.participants.remove(playerId)
        val returnLocation = session.returnLocations.remove(playerId)
        session.playerNotifiedWaves.remove(playerId)
        session.participantLocationHistory.remove(playerId)
        session.participantLastSampleMillis.remove(playerId)
        session.actionMarkerHoldStates.remove(playerId)
        session.invitedParticipants.remove(playerId)
        session.invitedAtMillis.remove(playerId)
        session.waitingParticipants.remove(playerId)
        session.waitingNotifiedParticipants.remove(playerId)
        hideJoinCountdownBossBar(session, playerId)
        releaseInvitedPlayerLock(playerId)

        val player = Bukkit.getPlayer(playerId)
        if (player != null && player.isOnline) {
            stopArenaBgmForPlayer(player)
            session.progressBossBar?.let { player.hideBossBar(it) }
            val fallback = Bukkit.getWorlds().firstOrNull()?.spawnLocation
            val destination = if (returnLocation?.world != null) returnLocation else fallback
            if (destination != null) {
                player.teleport(destination)
            }
            player.sendMessage(reason)
        }

        if (session.participants.isEmpty()) {
            terminateSession(session, false)
        }
        return true
    }

    private fun terminateSession(
        session: ArenaSession,
        success: Boolean,
        messageKey: String? = null,
        fallbackMessage: String? = null,
        vararg messagePlaceholders: Pair<String, Any?>
    ) {
        Bukkit.getPluginManager().callEvent(
            ArenaSessionEndedEvent(
                ownerPlayerId = session.ownerPlayerId,
                worldName = session.worldName,
                themeId = session.themeId,
                difficultyId = session.difficultyId,
                difficultyValue = session.difficultyValue,
                waves = session.waves,
                success = success
            )
        )

        sessionsByWorld.remove(session.worldName)

        session.waveSpawnTasks.values.forEach { it.cancel() }
        session.waveSpawnTasks.clear()
        session.transitionTasks.forEach { it.cancel() }
        session.transitionTasks.clear()
        session.stageBuildTask?.cancel()
        session.stageBuildTask = null
        cleanupBarrierRestartSession(session, removeDefenseMobs = true, smoke = false)
        hideSessionProgressBossBar(session)
        clearMultiplayerRecruitmentState(session)

        session.participants.toList().forEach { participantId ->
            playerToSessionWorld.remove(participantId)
            inviteSwingCooldownUntilTick.remove(participantId)
            val player = Bukkit.getPlayer(participantId)
            if (player != null && player.isOnline) {
                stopArenaBgmForPlayer(player)
                val fallback = Bukkit.getWorlds().firstOrNull()?.spawnLocation
                val destination = if (session.returnLocations[participantId]?.world != null) {
                    session.returnLocations[participantId]
                } else {
                    fallback
                }
                if (destination != null) {
                    player.teleport(destination)
                }
                if (messageKey != null && fallbackMessage != null) {
                    player.sendMessage(ArenaI18n.text(player, messageKey, fallbackMessage, *messagePlaceholders))
                    if (success) {
                        player.sendMessage(
                            ArenaI18n.text(
                                player,
                                "arena.messages.session.retry_hint",
                                "&7管理コマンドで difficulty / theme を指定して再挑戦できます"
                            )
                        )
                    }
                }
            }
        }

        session.participants.clear()
        session.returnLocations.clear()
        session.playerNotifiedWaves.clear()
        session.participantLocationHistory.clear()
        session.participantLastSampleMillis.clear()
        session.invitedParticipants.clear()
        session.invitedAtMillis.clear()
        session.waitingParticipants.clear()
        session.waitingNotifiedParticipants.clear()
        session.joinCountdownBossBars.clear()
        session.joinAreaMarkerLocations.clear()
        session.multiplayerJoinEnabled = false
        session.multiplayerJoinFinalizeStarted = false
        session.multiplayerJoinIntroStarted = false
        session.joinGraceStartMillis = 0L
        session.joinGraceDurationMillis = 0L
        session.joinGraceEndMillis = 0L
        session.stageGenerationCompleted = true
        session.stageGenerationWaitTitleShown = false
        session.stageBuildTask = null
        session.corridorTriggeredWaves.clear()
        session.openedCorridors.clear()
        session.corridorOpenAnnouncements.clear()
        session.enteredWaves.clear()
        session.waveSpawningStopped.clear()
        session.animatingDoorWaves.clear()
        session.actionMarkers.clear()
        session.actionMarkerHoldStates.clear()
        session.stageStarted = false
        session.barrierActive = false
        session.barrierRestarting = false
        session.barrierRestartCompleted = false

        session.activeMobs.toList().forEach { mobId ->
            mobToSessionWorld.remove(mobId)
            mobToDefinitionTypeId.remove(mobId)
            session.mobWaveMap.remove(mobId)
            mobService.untrack(mobId)
            val entity = Bukkit.getEntity(mobId)
            if (entity != null && entity.isValid && !entity.isDead) {
                spawnMobDisappearSmoke(entity.location)
                entity.remove()
            }
            session.activeMobs.remove(mobId)
        }
        session.waveMobIds.clear()

        val world = Bukkit.getWorld(session.worldName)
        if (world != null) {
            tryDeleteWorld(world)
        } else {
            queueWorldDeletion(session.worldName, File(Bukkit.getWorldContainer(), session.worldName))
        }
    }

    private fun initializeWavePipeline(
        session: ArenaSession,
        theme: ArenaTheme,
        difficulty: ArenaDifficultyConfig
    ) {
        session.stageMaxAliveCount = calculateStageMaxAliveCount(session, theme, difficulty)
        updateCurrentWave(session)
    }

    private fun startWave(
        session: ArenaSession,
        wave: Int,
        theme: ArenaTheme,
        difficulty: ArenaDifficultyConfig
    ) {
        if (wave <= 0 || wave > session.waves) return
        if (session.startedWaves.contains(wave)) return
        if (session.clearedWaves.contains(wave)) return

        val spawns = session.roomMobSpawns[wave].orEmpty()
        val candidates = selectSpawnCandidates(theme, wave)
        val isFinalWaveBarrierObjective = wave == session.waves && hasBarrierActivationObjective(session)
        if (candidates.isEmpty()) {
            plugin.logger.severe(
                "[Arena] 開始不能ウェーブを検出: world=${session.worldName} theme=${theme.id} wave=$wave"
            )
            session.startedWaves.add(wave)
            if (isFinalWaveBarrierObjective) {
                updateCurrentWave(session)
            } else {
                clearWave(session, wave)
            }
            return
        }

        if (spawns.isEmpty()) {
            plugin.logger.severe(
                "[Arena] wave=$wave の mob スポーン位置が0件のため自動クリアします。" +
                    " 対象部屋に 'arena.marker.mob' を1個以上配置してください: world=${session.worldName}"
            )
            session.startedWaves.add(wave)
            if (isFinalWaveBarrierObjective) {
                updateCurrentWave(session)
            } else {
                clearWave(session, wave)
            }
            return
        }

        val clearTarget = calculateWaveCount(theme.mobSpawnConfig.clearMobCount, difficulty, wave, session.questModifiers.clearMobCountMultiplier)

        session.lastClearedWaveForBossBar = null
        session.startedWaves.add(wave)
        requestArenaBgmMode(session, ArenaBgmMode.COMBAT)
        session.waveKillCount.putIfAbsent(wave, 0)
        session.waveClearTargets[wave] = clearTarget
        session.waveMobIds.putIfAbsent(wave, mutableSetOf())

        startSpawnLoop(session, wave, theme, difficulty, spawns)
        updateCurrentWave(session)
    }

    private fun applyStageStartFacingYaw(session: ArenaSession, location: Location) {
        val marker = session.corridorDoorBlocks[1]
            ?.firstOrNull { it.world?.name == location.world?.name }
            ?: return
        val dx = marker.x - location.x
        val dz = marker.z - location.z
        val yaw = when {
            kotlin.math.abs(dx) >= kotlin.math.abs(dz) && dx >= 0.0 -> -90.0f
            kotlin.math.abs(dx) >= kotlin.math.abs(dz) && dx < 0.0 -> 90.0f
            dz >= 0.0 -> 0.0f
            else -> 180.0f
        }
        location.yaw = yaw
        location.pitch = 0.0f
    }

    private fun calculateWaveCount(base: Int, difficulty: ArenaDifficultyConfig, wave: Int, questMultiplier: Double): Int {
        val scaled = base * difficulty.mobCountMultiplier * difficulty.waveScale(wave) * questMultiplier
        return ceil(scaled).toInt().coerceAtLeast(1)
    }

    private fun startSpawnLoop(
        session: ArenaSession,
        wave: Int,
        theme: ArenaTheme,
        difficulty: ArenaDifficultyConfig,
        spawns: List<Location>,
        intervalScale: Double = 1.0
    ) {
        val interval = (difficulty.mobSpawnIntervalTicks * session.questModifiers.spawnIntervalMultiplier * intervalScale)
            .roundToLong()
            .coerceAtLeast(1L)
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val currentSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (!currentSession.startedWaves.contains(wave)) return@Runnable
            if (currentSession.waveSpawningStopped.contains(wave)) return@Runnable
            if (currentSession.clearedWaves.contains(wave)) return@Runnable

            val spawnThrottle = mobService.getSpawnThrottle("arena:${currentSession.worldName}")
            val intervalChance = (1.0 / spawnThrottle.intervalMultiplier).coerceIn(0.0, 1.0)
            if (random.nextDouble() < spawnThrottle.skipChance) return@Runnable
            if (random.nextDouble() > intervalChance) return@Runnable

            val maxAliveBase = currentSession.stageMaxAliveCount.coerceAtLeast(1)
            val maxAlive = if (currentSession.barrierRestarting && wave == currentSession.waves) {
                (maxAliveBase * 2.0).roundToInt().coerceAtLeast(maxAliveBase)
            } else if (currentSession.barrierRestarting) {
                (maxAliveBase * 1.5).roundToInt().coerceAtLeast(maxAliveBase)
            } else {
                maxAliveBase
            }
            if (currentSession.activeMobs.size >= maxAlive) return@Runnable

            val world = Bukkit.getWorld(currentSession.worldName) ?: return@Runnable
            val spawnPoint = selectSpawnPoint(spawns) ?: return@Runnable
            val weightedMob = selectWeightedMob(selectSpawnCandidates(theme, wave)) ?: return@Runnable
            val definition = mobDefinitions[weightedMob.mobId] ?: return@Runnable

            spawnMob(world, currentSession, wave, spawnPoint, definition, difficulty)
        }, interval, interval)

        session.waveSpawnTasks[wave]?.cancel()
        session.waveSpawnTasks[wave] = task
    }

    private fun restartWaveSpawnLoopWithIntervalScale(session: ArenaSession, wave: Int, intervalScale: Double) {
        val theme = themeLoader.getTheme(session.themeId) ?: return
        val difficulty = difficultyConfigs[session.difficultyId] ?: return
        val spawns = session.roomMobSpawns[wave].orEmpty()
        if (spawns.isEmpty()) return
        if (!session.startedWaves.contains(wave)) return
        if (session.waveSpawningStopped.contains(wave)) return
        if (session.clearedWaves.contains(wave)) return

        startSpawnLoop(session, wave, theme, difficulty, spawns, intervalScale)
    }

    private fun locateRoom(session: ArenaSession, location: Location): Int? {
        return session.roomBounds.entries.firstOrNull { (_, bounds) ->
            bounds.contains(location.x, location.y, location.z)
        }?.key
    }

    private fun locateCorridorTargetWave(session: ArenaSession, location: Location): Int? {
        return session.corridorBounds.entries.firstOrNull { (_, bounds) ->
            bounds.contains(location.x, location.y, location.z)
        }?.key
    }

    private fun teleportToCurrentWavePosition(player: Player, session: ArenaSession, world: World, applyBlindness: Boolean = false) {
        val target = currentWavePosition(session, world)
        target.yaw = player.location.yaw
        target.pitch = player.location.pitch
        player.teleport(target)
        if (applyBlindness) {
            player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 15, 0, false, false, false))
        }
    }

    private fun teleportToRecentValidPosition(
        player: Player,
        session: ArenaSession,
        world: World,
        currentWave: Int,
        applyBlindness: Boolean = false
    ) {
        val target = selectRecentValidPosition(session, player.uniqueId, world, currentWave)
            ?: currentWavePosition(session, world)
        target.yaw = player.location.yaw
        target.pitch = player.location.pitch
        player.teleport(target)
        if (applyBlindness) {
            player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 15, 0, false, false, false))
        }
    }

    private fun selectRecentValidPosition(session: ArenaSession, playerId: UUID, world: World, currentWave: Int): Location? {
        val history = session.participantLocationHistory[playerId] ?: return null
        if (history.isEmpty()) return null

        val now = System.currentTimeMillis()
        val targetMillis = now - POSITION_RESTORE_LOOKBACK_MILLIS
        var best: TimedPlayerLocation? = null
        var bestDistance = Long.MAX_VALUE

        for (sample in history) {
            if (!isValidPositionForRestore(session, sample.location, world, currentWave)) {
                continue
            }
            val distance = kotlin.math.abs(sample.timestampMillis - targetMillis)
            if (distance < bestDistance) {
                best = sample
                bestDistance = distance
            }
        }

        return best?.location?.clone()?.apply { this.world = world }
    }

    private fun isValidPositionForRestore(session: ArenaSession, location: Location, world: World, currentWave: Int): Boolean {
        if (location.world?.name != world.name) return false
        if (!session.stageBounds.contains(location.x, location.y, location.z)) return false

        val room = locateRoom(session, location)
        if (room != null && room > currentWave) return false

        val corridorWave = locateCorridorTargetWave(session, location) ?: return true
        return session.openedCorridors.contains(corridorWave)
    }

    private fun recordParticipantValidLocation(session: ArenaSession, participantId: UUID, location: Location) {
        val now = System.currentTimeMillis()
        val lastSampleMillis = session.participantLastSampleMillis[participantId] ?: 0L
        if (now - lastSampleMillis < POSITION_SAMPLE_INTERVAL_MILLIS) {
            return
        }

        val history = session.participantLocationHistory.getOrPut(participantId) { ArrayDeque() }
        history.addLast(TimedPlayerLocation(now, location.clone()))
        session.participantLastSampleMillis[participantId] = now

        while (history.size > POSITION_HISTORY_MAX_SAMPLES) {
            history.removeFirstOrNull()
        }
        while (history.firstOrNull()?.timestampMillis?.let { now - it > POSITION_HISTORY_RETENTION_MILLIS } == true) {
            history.removeFirstOrNull()
        }
    }

    private fun currentWavePosition(session: ArenaSession, world: World): Location {
        if (!session.stageStarted) {
            return session.entranceLocation.clone().apply {
                this.world = world
            }
        }

        val wave = session.fallbackWave.coerceIn(1, session.waves)
        val bounds = session.roomBounds[wave]
        if (bounds == null) {
            return session.playerSpawn.clone().apply {
                this.world = world
            }
        }

        val centerX = (bounds.minX + bounds.maxX + 1) / 2.0
        val centerY = bounds.minY + 1.0
        val centerZ = (bounds.minZ + bounds.maxZ + 1) / 2.0
        return Location(world, centerX, centerY, centerZ)
    }

    private fun notifyWaveEntryIfNeeded(session: ArenaSession, player: Player, wave: Int) {
        if (session.clearedWaves.contains(wave)) return

        val notified = session.playerNotifiedWaves.getOrPut(player.uniqueId) { mutableSetOf() }
        if (!notified.add(wave)) return

        val title = if (wave >= session.waves) {
            "§7- §6Last Wave §7-"
        } else {
            "§7- §6Wave $wave §7-"
        }
        player.sendTitle("", title, 10, 50, 10)
        player.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f)
    }

    private fun processRoomProgress(session: ArenaSession, player: Player, room: Int) {
        notifyWaveEntryIfNeeded(session, player, room)
        handleWaveRoomEntry(session, room)
    }

    private fun handleWaveRoomEntry(session: ArenaSession, wave: Int) {
        if (!session.enteredWaves.add(wave)) return

        session.fallbackWave = wave.coerceIn(1, session.waves)

        val theme = themeLoader.getTheme(session.themeId)
        val difficulty = difficultyConfigs[session.difficultyId]
        if (theme != null && difficulty != null) {
            startWave(session, wave, theme, difficulty)
        }

        ensureDoorActionMarkersForTargetWave(session, wave + 1)

        val previousWave = wave - 1
        if (previousWave <= 0) return

        stopWaveSpawning(session, previousWave)
        removeWaveMobs(session, previousWave)
    }

    private fun stopWaveSpawning(session: ArenaSession, wave: Int) {
        if (wave <= 0) return
        session.waveSpawningStopped.add(wave)
        session.waveSpawnTasks.remove(wave)?.cancel()
    }

    private fun selectSpawnPoint(markers: List<Location>): Location? {
        if (markers.isEmpty()) return null
        return markers[random.nextInt(markers.size)]
    }

    private fun selectSpawnCandidates(theme: ArenaTheme, wave: Int): List<ArenaThemeWeightedMobEntry> {
        return theme.mobSpawnConfig
            .candidatesForWave(wave)
            .filter { mobDefinitions.containsKey(it.mobId) }
    }

    private fun selectWeightedMob(candidates: List<ArenaThemeWeightedMobEntry>): ArenaThemeWeightedMobEntry? {
        if (candidates.isEmpty()) return null
        val totalWeight = candidates.sumOf { it.weight.coerceAtLeast(0) }
        if (totalWeight <= 0) return null

        var roll = random.nextInt(totalWeight)
        for (candidate in candidates) {
            roll -= candidate.weight
            if (roll < 0) {
                return candidate
            }
        }
        return candidates.last()
    }

    private fun spawnMob(
        world: World,
        session: ArenaSession,
        wave: Int,
        spawn: Location,
        definition: MobDefinition,
        difficulty: ArenaDifficultyConfig
    ) {
        val entity = mobService.spawn(
            definition,
            spawn,
            MobSpawnOptions(
                featureId = "arena",
                sessionKey = "arena:${session.worldName}",
                combatActiveProvider = { true },
                metadata = mapOf("world" to session.worldName, "wave" to wave.toString())
            )
        ) ?: return
        entity.removeWhenFarAway = false
        entity.canPickupItems = false
        enforceAdultMob(entity)
        spawnMobAppearParticles(world, entity.location)

        applyMobStats(entity, definition, difficulty, session.difficultyValue, session.questModifiers)

        if (entity is Mob) {
            entity.target = findNearestParticipant(session, entity.location)
        }

        val entityId = entity.uniqueId
        session.activeMobs.add(entityId)
        session.waveMobIds.getOrPut(wave) { mutableSetOf() }.add(entityId)
        session.mobWaveMap[entityId] = wave
        mobToSessionWorld[entityId] = session.worldName
        mobToDefinitionTypeId[entityId] = definition.typeId
    }

    private fun applyMobStats(
        entity: LivingEntity,
        definition: MobDefinition,
        difficulty: ArenaDifficultyConfig,
        difficultyValue: Double,
        questModifiers: ArenaQuestModifiers
    ) {
        val progress = difficultyProgress(difficulty.difficultyRange, difficultyValue)
        val maxHealth = (interpolate(definition.health, difficulty.mobStatsMaxHealth, progress) * questModifiers.mobHealthMultiplier)
            .coerceAtLeast(1.0)
        val attack = (interpolate(definition.attack, difficulty.mobStatsMaxAttack, progress) * questModifiers.mobAttackMultiplier)
            .coerceAtLeast(0.0)
        val speed = interpolate(definition.movementSpeed, difficulty.mobStatsMaxMovementSpeed, progress).coerceAtLeast(0.01)
        val armor = interpolate(definition.armor, difficulty.mobStatsMaxArmor, progress).coerceAtLeast(0.0)
        val scale = interpolate(definition.scale, difficulty.mobStatsMaxScale, progress).coerceAtLeast(0.1)

        entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = maxHealth
        entity.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = attack
        entity.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = speed
        entity.getAttribute(Attribute.ARMOR)?.baseValue = armor
        entity.getAttribute(Attribute.SCALE)?.baseValue = scale
        entity.health = maxHealth
    }

    private fun enforceAdultMob(entity: LivingEntity) {
        when (entity) {
            is Zombie -> entity.isBaby = false
            is Ageable -> entity.setAdult()
        }
    }

    private fun spawnMobAppearParticles(world: World, location: Location) {
        world.spawnParticle(Particle.WITCH, location, 10, 1.0, 1.0, 1.0, 0.0)
        world.spawnParticle(Particle.SMOKE, location, 10, 1.0, 1.0, 1.0, 0.0)
    }

    private fun interpolate(base: Double, maximum: Double, progress: Double): Double {
        return base + (maximum - base) * progress.coerceIn(0.0, 1.0)
    }

    private fun difficultyProgress(range: ClosedFloatingPointRange<Double>, difficultyValue: Double): Double {
        val width = range.endInclusive - range.start
        if (width <= 0.0) return 1.0
        return ((difficultyValue - range.start) / width).coerceIn(0.0, 1.0)
    }

    private fun consumeMob(session: ArenaSession, mobId: UUID, countKill: Boolean) {
        val removed = session.activeMobs.remove(mobId)
        if (!removed) return

        mobToSessionWorld.remove(mobId)
        mobToDefinitionTypeId.remove(mobId)
        val wave = session.mobWaveMap.remove(mobId)
        if (wave != null) {
            session.waveMobIds[wave]?.remove(mobId)
        }

        if (!countKill || wave == null) {
            return
        }

        session.totalKillCount += 1

        if (session.clearedWaves.contains(wave)) {
            return
        }

        val kills = (session.waveKillCount[wave] ?: 0) + 1
        session.waveKillCount[wave] = kills

        if (wave == session.waves && hasBarrierActivationObjective(session)) {
            updateSessionProgressBossBar(session)
            return
        }

        val target = session.waveClearTargets[wave] ?: return
        if (kills >= target) {
            clearWave(session, wave)
            return
        }

        updateSessionProgressBossBar(session)
    }

    private fun clearWave(session: ArenaSession, wave: Int) {
        if (session.clearedWaves.contains(wave)) return

        session.clearedWaves.add(wave)
        session.lastClearedWaveForBossBar = wave
        stopWaveSpawning(session, wave)
        updateSessionProgressBossBar(session)

        if (session.clearedWaves.size >= session.waves) {
            onAllWavesCleared(session)
            return
        }

        activateDoorActionMarkers(session.worldName, wave + 1)
    }

    private fun hasBarrierActivationObjective(session: ArenaSession): Boolean {
        return session.actionMarkers.values.any { it.type == ArenaActionMarkerType.BARRIER_ACTIVATE }
    }

    private fun startDoorAnimation(session: ArenaSession, targetWave: Int) {
        if (targetWave <= 0 || targetWave > session.waves) return
        if (!session.animatingDoorWaves.add(targetWave)) return

        val delayedTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable delayedStart@{
            val activeSession = sessionsByWorld[session.worldName] ?: return@delayedStart
            if (!activeSession.animatingDoorWaves.contains(targetWave)) return@delayedStart

            onDoorAnimationStarted(activeSession, targetWave)

            val placements = activeSession.doorAnimationPlacements[targetWave].orEmpty()
            if (placements.isEmpty()) {
                activeSession.animatingDoorWaves.remove(targetWave)
                updateCurrentWave(activeSession)
                return@delayedStart
            }

            var elapsedTicks = 0
            val lastAppliedFrameByPlacement = mutableMapOf<Int, Int>()
            val taskRef = arrayOfNulls<BukkitTask>(1)
            val animationTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable animationTick@{
                val currentSession = sessionsByWorld[session.worldName] ?: run {
                    taskRef[0]?.cancel()
                    return@animationTick
                }

                elapsedTicks += 1

                placements.forEachIndexed { placementIndex, placement ->
                    val targetFrame = targetFrameIndex(elapsedTicks, placement.openFrames.size)
                    if (targetFrame <= 0) return@forEachIndexed
                    val lastFrame = lastAppliedFrameByPlacement[placementIndex]
                    if (lastFrame == targetFrame) return@forEachIndexed
                    applyDoorAnimationFrame(placement, targetFrame)
                    lastAppliedFrameByPlacement[placementIndex] = targetFrame
                }

                if (elapsedTicks >= doorAnimationTotalTicks) {
                    currentSession.animatingDoorWaves.remove(targetWave)
                    updateCurrentWave(currentSession)
                    taskRef[0]?.cancel()
                    return@animationTick
                }

                if (elapsedTicks % 10 == 0) {
                    val pitch = (0.8f + elapsedTicks / 100.0f).coerceAtMost(1.4f)
                    playSound(currentSession, Sound.BLOCK_IRON_DOOR_OPEN, 0.85f, pitch)
                }
            }, 0L, 1L)
            taskRef[0] = animationTask
            activeSession.transitionTasks.add(animationTask)
        }, DOOR_ANIMATION_START_DELAY_TICKS)

        session.transitionTasks.add(delayedTask)
    }

    private fun onDoorAnimationStarted(session: ArenaSession, targetWave: Int) {
        session.openedCorridors.add(targetWave)
        onCorridorOpened(session, targetWave)
        if (targetWave == 1 && !session.stageStarted) {
            session.stageStarted = true
            playSound(session, Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.0f)
        }

        playSound(session, Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 0.8f)
        plugin.logger.info("[Arena] door animation start: world=${session.worldName} target_wave=$targetWave")
    }

    private fun calculateStageMaxAliveCount(
        session: ArenaSession,
        theme: ArenaTheme,
        difficulty: ArenaDifficultyConfig
    ): Int {
        val calculated = calculateWaveCount(
            theme.mobSpawnConfig.maxSummonCount,
            difficulty,
            session.waves,
            session.questModifiers.maxSummonCountMultiplier
        )
        return minOf(sharedWaveMaxAlive, calculated).coerceAtLeast(1)
    }

    private fun targetFrameIndex(elapsedTicks: Int, totalFrames: Int): Int {
        if (totalFrames <= 0 || elapsedTicks <= 0) return 0
        val clampedElapsed = elapsedTicks.coerceIn(1, doorAnimationTotalTicks)
        return ceil(clampedElapsed * totalFrames / doorAnimationTotalTicks.toDouble())
            .toInt()
            .coerceIn(1, totalFrames)
    }

    private fun applyDoorAnimationFrame(placement: ArenaDoorAnimationPlacement, frameIndex: Int) {
        val world = placement.placeOrigin.world ?: return
        val template = placement.openFrames.getOrNull(frameIndex - 1) ?: return
        template.structure.place(
            placement.placeOrigin.clone().apply { this.world = world },
            false,
            toStructureRotation(placement.rotationQuarter),
            Mirror.NONE,
            0,
            1.0f,
            java.util.Random()
        )
    }

    private fun toStructureRotation(rotationQuarter: Int): StructureRotation {
        return when (rotationQuarter.mod(4)) {
            1 -> StructureRotation.CLOCKWISE_90
            2 -> StructureRotation.CLOCKWISE_180
            3 -> StructureRotation.COUNTERCLOCKWISE_90
            else -> StructureRotation.NONE
        }
    }

    private fun onCorridorOpened(session: ArenaSession, wave: Int) {
        if (!session.corridorOpenAnnouncements.add(wave)) return
        playSound(session, Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.2f)
    }

    private fun rebalanceTargetsForWave(session: ArenaSession, wave: Int) {
        if (wave <= 0 || wave > session.waves) return

        val targets = session.participants
            .mapNotNull { participantId -> Bukkit.getPlayer(participantId) }
            .filter { it.isOnline && it.world.name == session.worldName }
            .shuffled(random)

        val mobs = session.waveMobIds[wave]
            .orEmpty()
            .mapNotNull { mobId -> Bukkit.getEntity(mobId) as? Mob }
            .filter { !it.isDead && it.isValid && it.world.name == session.worldName }
            .shuffled(random)

        if (mobs.isEmpty()) return
        if (targets.isEmpty()) {
            mobs.forEach { mob ->
                mob.target = null
            }
            return
        }

        mobs.forEachIndexed { index, mob ->
            val target = targets[index % targets.size]
            mob.target = target
        }
    }

    private fun findNearestParticipant(session: ArenaSession, location: Location): Player? {
        return session.participants
            .mapNotNull { participantId -> Bukkit.getPlayer(participantId) }
            .filter { it.isOnline && it.world.name == session.worldName }
            .minByOrNull { it.location.distanceSquared(location) }
    }

    private fun removeWaveMobs(session: ArenaSession, wave: Int) {
        val ids = session.waveMobIds[wave]?.toList().orEmpty()
        if (ids.isEmpty()) {
            session.waveMobIds.remove(wave)
            return
        }

        val remaining = mutableSetOf<UUID>()
        for (mobId in ids) {
            if (shouldKeepWaveMob(session, mobId)) {
                remaining.add(mobId)
                continue
            }

            session.activeMobs.remove(mobId)
            session.mobWaveMap.remove(mobId)
            mobToSessionWorld.remove(mobId)
            mobToDefinitionTypeId.remove(mobId)
            mobService.untrack(mobId)
            val entity = Bukkit.getEntity(mobId)
            if (entity != null && entity.isValid && !entity.isDead) {
                spawnMobDisappearSmoke(entity.location)
                entity.remove()
            }
        }

        if (remaining.isEmpty()) {
            session.waveMobIds.remove(wave)
        } else {
            session.waveMobIds[wave] = remaining
        }
    }

    private fun shouldKeepWaveMob(session: ArenaSession, mobId: UUID): Boolean {
        val mob = Bukkit.getEntity(mobId) as? Mob ?: return false
        if (!mob.isValid || mob.isDead) return false
        val targetPlayer = mob.target as? Player ?: return false
        return session.participants.contains(targetPlayer.uniqueId)
    }

    private fun updateCurrentWave(session: ArenaSession) {
        val next = (1..session.waves).firstOrNull { !session.clearedWaves.contains(it) } ?: session.waves
        session.currentWave = next
        updateSessionProgressBossBar(session)
    }

    private fun onAllWavesCleared(session: ArenaSession) {
        if (session.barrierActive) return
        session.waveSpawnTasks.values.forEach { it.cancel() }
        session.waveSpawnTasks.clear()
        session.barrierActive = true
        startBarrierRestartSequence(session)
    }

    private fun initializeBarrierRestartState(session: ArenaSession) {
        session.barrierRestarting = false
        session.barrierRestartCompleted = false
        session.barrierRestartStartMillis = 0L
        session.barrierRestartDurationMillis = 0L
        session.barrierRestartProgressMillis = 0L
        session.barrierRestartActivationTask?.cancel()
        session.barrierRestartActivationTask = null
        session.barrierRestartEffectTask?.cancel()
        session.barrierRestartEffectTask = null
        session.barrierRestartDamageTask?.cancel()
        session.barrierRestartDamageTask = null
        session.barrierRestartActivationQueue.clear()
        session.barrierRestartActivatedPoints.clear()
        session.barrierDefenseTargetMobIds.clear()
        session.barrierDefenseAssaultMobIds.clear()
    }

    private fun startBarrierAmbientTask(session: ArenaSession) {
        session.barrierAmbientTask?.cancel()
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val currentSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (currentSession.barrierRestarting) return@Runnable

            playSoundAtBarrier(currentSession, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 0.5f)
        }, 0L, 100L)
        session.barrierAmbientTask = task
    }

    private fun barrierRestartProgress(session: ArenaSession): Double {
        if (session.barrierRestartCompleted) return 1.0
        if (!session.barrierRestarting) return 0.0

        val duration = session.barrierRestartDurationMillis.coerceAtLeast(1L)
        return (session.barrierRestartProgressMillis.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun startBarrierRestartSequence(session: ArenaSession) {
        if (session.barrierRestartCompleted) return
        if (session.barrierRestarting) return

        val world = Bukkit.getWorld(session.worldName) ?: return
        val durationMillis = ((barrierRestartConfig.defaultDurationSeconds * session.difficultyScore) * 1000.0)
            .roundToLong()
            .coerceAtLeast(1000L)

        session.barrierRestarting = true
        session.barrierRestartCompleted = false
        session.barrierRestartStartMillis = System.currentTimeMillis()
        session.barrierRestartDurationMillis = durationMillis
        session.barrierRestartProgressMillis = 0L

        restartWaveSpawnLoopWithIntervalScale(session, session.waves, 0.5)

        broadcastRawMessage(session, "§c結界石の再起動を開始しました！結界を一時的に解除します！")

        session.barrierAmbientTask?.cancel()
        session.barrierAmbientTask = null
        playSoundAtBarrier(session, Sound.BLOCK_BEACON_DEACTIVATE, 5.0f, 0.5f)
        spawnBarrierRestartStartParticles(world, session)
        setupBarrierRestartActivationQueue(session, world)
        startBarrierRestartActivationTask(session)
        startBarrierRestartEffectTask(session)
        startBarrierRestartDamageTask(session)

        updateSessionProgressBossBar(session)

        session.barrierRestartTask?.cancel()
        scheduleBarrierRestartProgressTick(session)
    }

    private fun scheduleBarrierRestartProgressTick(session: ArenaSession) {
        session.barrierRestartTask?.cancel()
        val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            tickBarrierRestartSequence(session)
        }, BARRIER_RESTART_PROGRESS_STEP_TICKS)
        session.barrierRestartTask = task
    }

    private fun tickBarrierRestartSequence(session: ArenaSession) {
        val activeSession = sessionsByWorld[session.worldName] ?: return
        if (!activeSession.barrierRestarting) return

        activeSession.barrierRestartProgressMillis = (activeSession.barrierRestartProgressMillis + BARRIER_RESTART_PROGRESS_STEP_MILLIS)
            .coerceAtMost(activeSession.barrierRestartDurationMillis)

        val progress = barrierRestartProgress(activeSession)
        updateSessionProgressBossBar(activeSession)

        if (progress >= 1.0) {
            activeSession.barrierRestartTask = null
            completeBarrierRestartSequence(activeSession)
            return
        }

        scheduleBarrierRestartProgressTick(activeSession)
    }

    private fun setupBarrierRestartActivationQueue(session: ArenaSession, world: World) {
        val markerPoints = session.actionMarkers.values
            .asSequence()
            .filter { it.type == ArenaActionMarkerType.BARRIER_ACTIVATE }
            .map { marker -> marker.center.clone().apply { this.world = world } }
            .toMutableList()

        val points = if (markerPoints.isNotEmpty()) {
            markerPoints
        } else {
            session.barrierPointLocations
                .map { location -> location.clone().apply { this.world = world } }
                .toMutableList()
        }

        points.shuffle(random)
        session.barrierRestartActivationQueue.clear()
        session.barrierRestartActivationQueue.addAll(points)
        session.barrierRestartActivatedPoints.clear()
    }

    private fun startBarrierRestartActivationTask(session: ArenaSession) {
        session.barrierRestartActivationTask?.cancel()

        val queueSize = session.barrierRestartActivationQueue.size
        if (queueSize <= 0) {
            session.barrierRestartActivationTask = null
            return
        }

        val intervalTicks = ((session.barrierRestartDurationMillis.toDouble() / queueSize.toDouble()) / 50.0)
            .roundToLong()
            .coerceAtLeast(1L)

        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (!activeSession.barrierRestarting) {
                activeSession.barrierRestartActivationTask?.cancel()
                activeSession.barrierRestartActivationTask = null
                return@Runnable
            }
            activateNextBarrierRestartPoint(activeSession)
            if (activeSession.barrierRestartActivationQueue.isEmpty()) {
                activeSession.barrierRestartActivationTask?.cancel()
                activeSession.barrierRestartActivationTask = null
            }
        }, 0L, intervalTicks)

        session.barrierRestartActivationTask = task
    }

    private fun activateNextBarrierRestartPoint(session: ArenaSession) {
        if (session.barrierRestartActivationQueue.isEmpty()) return
        val point = session.barrierRestartActivationQueue.removeAt(0)
        session.barrierRestartActivatedPoints.add(point)

        val world = Bukkit.getWorld(session.worldName) ?: return
        world.spawnParticle(Particle.OMINOUS_SPAWNING, point, 20, 0.5, 0.5, 0.5, 0.1)
        world.spawnParticle(Particle.REVERSE_PORTAL, point, 100, 1.0, 1.0, 1.0, 5.0)

        val participants = session.participants
            .asSequence()
            .mapNotNull { Bukkit.getPlayer(it) }
            .filter { it.isOnline && it.world.uid == world.uid }
            .toList()

        participants.forEach { player ->
            player.playSound(point, Sound.BLOCK_ENDER_CHEST_OPEN, 0.9f, 0.5f)
            player.playSound(point, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.9f, 0.5f)
        }
    }

    private fun startBarrierRestartEffectTask(session: ArenaSession) {
        session.barrierRestartEffectTask?.cancel()
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (!activeSession.barrierRestarting) return@Runnable
            renderBarrierRestartEffects(activeSession)
        }, 0L, BARRIER_RESTART_EFFECT_INTERVAL_TICKS)
        session.barrierRestartEffectTask = task
    }

    private fun renderBarrierRestartEffects(session: ArenaSession) {
        if (session.barrierRestartActivatedPoints.isEmpty()) return
        val world = Bukkit.getWorld(session.worldName) ?: return
        val core = session.barrierLocation.clone().apply { this.world = world }.add(0.5, 1.0, 0.5)

        session.barrierRestartActivatedPoints.forEach { point ->
            val origin = point.clone().apply { this.world = world }
            world.spawnParticle(Particle.OMINOUS_SPAWNING, origin, 10, 0.5, 0.5, 0.5, 0.1)
            drawBarrierRestartLinkBeam(world, origin.clone().add(0.0, 0.25, 0.0), core)
        }
    }

    private fun drawBarrierRestartLinkBeam(world: World, from: Location, to: Location) {
        val points = BARRIER_RESTART_BEAM_POINTS.coerceAtLeast(4)
        for (index in 0..points) {
            val t = index.toDouble() / points.toDouble()
            val x = from.x + (to.x - from.x) * t
            val y = from.y + (to.y - from.y) * t
            val z = from.z + (to.z - from.z) * t
            world.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun startBarrierRestartDamageTask(session: ArenaSession) {
        session.barrierRestartDamageTask?.cancel()
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (!activeSession.barrierRestarting) return@Runnable
            applyBarrierRestartPointDamage(activeSession)
        }, BARRIER_RESTART_DAMAGE_INTERVAL_TICKS, BARRIER_RESTART_DAMAGE_INTERVAL_TICKS)
        session.barrierRestartDamageTask = task
    }

    private fun applyBarrierRestartPointDamage(session: ArenaSession) {
        if (session.barrierRestartActivatedPoints.isEmpty()) return
        val world = Bukkit.getWorld(session.worldName) ?: return

        world.entities
            .asSequence()
            .filterIsInstance<Mob>()
            .filter { it.isValid && !it.isDead }
            .forEach { mob ->
                val withinRange = session.barrierRestartActivatedPoints.any { point ->
                    mob.location.distanceSquared(point) < BARRIER_RESTART_POINT_DAMAGE_RADIUS_SQUARED
                }
                if (!withinRange) return@forEach
                val maxHealth = mob.getAttribute(Attribute.MAX_HEALTH)?.value?.coerceAtLeast(1.0) ?: return@forEach
                val damageAmount = (maxHealth * BARRIER_RESTART_POINT_DAMAGE_MAX_RATIO).coerceAtLeast(0.001)
                mob.damage(damageAmount)
            }
    }

    private fun startBarrierDefenseSpawnTask(session: ArenaSession) {
        session.barrierDefenseSpawnTask?.cancel()

        val theme = themeLoader.getTheme(session.themeId) ?: return
        val difficulty = difficultyConfigs[session.difficultyId] ?: return
        val spawnPoints = session.roomMobSpawns[session.waves].orEmpty()
        if (spawnPoints.isEmpty()) return

        val normalInterval = (difficulty.mobSpawnIntervalTicks * session.questModifiers.spawnIntervalMultiplier)
            .roundToLong()
            .coerceAtLeast(1L)
        val interval = (normalInterval / 2.0).roundToLong().coerceAtLeast(1L)

        val normalMaxAlive = calculateWaveCount(
            theme.mobSpawnConfig.maxSummonCount,
            difficulty,
            session.waves,
            session.questModifiers.maxSummonCountMultiplier
        )
        val defenseMaxAlive = if (normalMaxAlive <= 1) 1 else (normalMaxAlive / 2.0).roundToInt().coerceIn(1, normalMaxAlive - 1)

        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (!activeSession.barrierRestarting) return@Runnable

            val alive = activeSession.barrierDefenseMobIds.count { mobId ->
                val entity = Bukkit.getEntity(mobId) as? LivingEntity
                entity != null && entity.isValid && !entity.isDead && entity.world.name == activeSession.worldName
            }
            if (alive >= defenseMaxAlive) return@Runnable

            spawnBarrierDefenseMob(activeSession, theme, difficulty, spawnPoints)
        }, 0L, interval)

        session.barrierDefenseSpawnTask = task
    }

    private fun startBarrierDefensePressureTask(session: ArenaSession) {
        session.barrierDefensePressureTask?.cancel()
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (!activeSession.barrierRestarting) return@Runnable

            val barrierLocation = activeSession.barrierLocation.clone().add(0.5, 1.0, 0.5)
            val targetIds = activeSession.barrierDefenseTargetMobIds.toList()

            targetIds.forEach { mobId ->
                val mob = Bukkit.getEntity(mobId) as? LivingEntity ?: run {
                    activeSession.barrierDefenseTargetMobIds.remove(mobId)
                    activeSession.barrierDefenseAssaultMobIds.remove(mobId)
                    return@forEach
                }

                if (!mob.isValid || mob.isDead || mob.world.name != activeSession.worldName) {
                    activeSession.barrierDefenseTargetMobIds.remove(mobId)
                    activeSession.barrierDefenseAssaultMobIds.remove(mobId)
                    return@forEach
                }

                val currentLocation = mob.location
                val distanceSquared = currentLocation.distanceSquared(barrierLocation)
                if (distanceSquared <= BARRIER_DEFENSE_PRESSURE_RADIUS_SQUARED) {
                    activeSession.barrierDefenseAssaultMobIds.add(mobId)
                    mob.velocity = Vector(0.0, 0.0, 0.0)
                    return@forEach
                }

                activeSession.barrierDefenseAssaultMobIds.remove(mobId)
                val direction = barrierLocation.toVector().subtract(currentLocation.toVector())
                if (direction.lengthSquared() > 0.0001) {
                    mob.velocity = direction.normalize().multiply(0.18)
                }
            }
        }, 0L, 1L)

        session.barrierDefensePressureTask = task
    }

    private fun spawnBarrierDefenseMob(
        session: ArenaSession,
        theme: ArenaTheme,
        difficulty: ArenaDifficultyConfig,
        spawnPoints: List<Location>
    ) {
        val spawnThrottle = mobService.getSpawnThrottle("arena:${session.worldName}")
        val intervalChance = (1.0 / spawnThrottle.intervalMultiplier).coerceIn(0.0, 1.0)
        if (random.nextDouble() < spawnThrottle.skipChance) return
        if (random.nextDouble() > intervalChance) return

        val spawnPoint = selectSpawnPoint(spawnPoints) ?: return
        val weightedMob = selectWeightedMob(selectSpawnCandidates(theme, session.waves)) ?: return
        val definition = mobDefinitions[weightedMob.mobId] ?: return

        val entity = mobService.spawn(
            definition,
            spawnPoint,
            MobSpawnOptions(
                featureId = "arena_barrier_restart",
                sessionKey = "arena:${session.worldName}",
                combatActiveProvider = { true },
                metadata = mapOf("world" to session.worldName, "restart" to "true")
            )
        ) ?: return

        entity.removeWhenFarAway = false
        entity.canPickupItems = false
        enforceAdultMob(entity)
        applyMobStats(entity, definition, difficulty, session.difficultyValue, session.questModifiers)
        spawnMobAppearParticles(spawnPoint.world ?: return, spawnPoint)
        val isTargetingBarrier = random.nextDouble() < BARRIER_DEFENSE_TARGET_RATIO
        if (isTargetingBarrier) {
            session.barrierDefenseTargetMobIds.add(entity.uniqueId)
            if (entity is Mob) {
                entity.target = null
            }
        } else if (entity is Mob && session.corridorTriggeredWaves.contains(session.waves)) {
            entity.target = findNearestParticipant(session, entity.location)
        }

        val entityId = entity.uniqueId
        session.barrierDefenseMobIds.add(entityId)
        mobToSessionWorld[entityId] = session.worldName
        mobToDefinitionTypeId[entityId] = definition.typeId
    }

    private fun completeBarrierRestartSequence(session: ArenaSession) {
        if (!session.barrierRestarting) return

        requestArenaBgmMode(session, ArenaBgmMode.STOPPED)

        session.barrierRestarting = false
        session.barrierRestartCompleted = true
        stopWaveSpawning(session, session.waves)

        session.barrierRestartTask?.cancel()
        session.barrierRestartTask = null
        session.barrierDefenseSpawnTask?.cancel()
        session.barrierDefenseSpawnTask = null
        session.barrierAmbientTask?.cancel()
        session.barrierAmbientTask = null

        cleanupBarrierRestartSession(session, removeDefenseMobs = true, smoke = true)
        removeRemainingWaveMobs(session)
        removeAllMobsInArenaWorld(session, smoke = true)
        session.lastClearedWaveForBossBar = session.waves
        updateSessionProgressBossBar(session)

        playSoundAtBarrier(session, Sound.BLOCK_BEACON_ACTIVATE, 5.0f, 0.5f)
        val world = Bukkit.getWorld(session.worldName) ?: return
        spawnBarrierRestartSuccessParticles(world, session)
        broadcastRawMessage(session, "§a結界石の再起動に成功しました！")

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            activeSession.participants.forEach { participantId ->
                val player = Bukkit.getPlayer(participantId) ?: return@forEach
                if (!player.isOnline || player.world.name != activeSession.worldName) return@forEach
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f)
                player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f)
                OageMessageSender.send(player, "結界の再起動を確認しました！転送します！", plugin)
            }

            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val currentSession = sessionsByWorld[session.worldName] ?: return@Runnable
                terminateSession(
                    currentSession,
                    true,
                    messageKey = "arena.messages.session.cleared",
                    fallbackMessage = "&aアリーナクリア！"
                )
            }, 40L)
        }, 40L)
    }

    private fun removeRemainingWaveMobs(session: ArenaSession) {
        session.activeMobs.toList().forEach { mobId ->
            val entity = Bukkit.getEntity(mobId) as? LivingEntity
            if (entity != null && entity.isValid && !entity.isDead) {
                spawnSmoke(entity.location)
                entity.remove()
            }
            mobToSessionWorld.remove(mobId)
            mobToDefinitionTypeId.remove(mobId)
            session.mobWaveMap.remove(mobId)
            mobService.untrack(mobId)
            session.activeMobs.remove(mobId)
        }

        session.waveMobIds.values.forEach { it.clear() }
    }

    private fun removeAllMobsInArenaWorld(session: ArenaSession, smoke: Boolean) {
        val world = Bukkit.getWorld(session.worldName) ?: return
        world.entities
            .asSequence()
            .filterIsInstance<Mob>()
            .filter { it.isValid && !it.isDead }
            .forEach { mob ->
                val mobId = mob.uniqueId
                session.activeMobs.remove(mobId)
                session.mobWaveMap.remove(mobId)
                session.waveMobIds.values.forEach { it.remove(mobId) }
                session.barrierDefenseMobIds.remove(mobId)
                session.barrierDefenseTargetMobIds.remove(mobId)
                session.barrierDefenseAssaultMobIds.remove(mobId)
                mobToSessionWorld.remove(mobId)
                mobToDefinitionTypeId.remove(mobId)
                mobService.untrack(mobId)
                if (smoke) {
                    spawnSmoke(mob.location)
                }
                mob.remove()
            }
    }

    private fun updateSessionProgressBossBar(session: ArenaSession) {
        val bossBar = session.progressBossBar ?: BossBar.bossBar(
            Component.empty(),
            0.0f,
            BossBar.Color.YELLOW,
            BossBar.Overlay.PROGRESS
        ).also { created ->
            session.progressBossBar = created
        }

        val world = Bukkit.getWorld(session.worldName)
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            if (player.isOnline && world != null && player.world.name == world.name) {
                player.showBossBar(bossBar)
            }
        }

        if (session.barrierRestarting) {
            bossBar.name(Component.text("§7- §6Last Wave §7- §d再起動中..."))
            bossBar.color(BossBar.Color.PINK)
            bossBar.progress(barrierRestartProgress(session).toFloat().coerceIn(0.0f, 1.0f))
            return
        }

        if (!session.startedWaves.contains(1)) {
            session.participants.forEach { participantId ->
                val player = Bukkit.getPlayer(participantId) ?: return@forEach
                if (player.isOnline && world != null && player.world.name == world.name) {
                    player.hideBossBar(bossBar)
                }
            }
            return
        }

        val clearedWave = session.lastClearedWaveForBossBar
        if (clearedWave != null) {
            val nextWave = clearedWave + 1
            if (nextWave <= session.waves && !session.startedWaves.contains(nextWave)) {
                bossBar.name(Component.text("§7- §6Wave $clearedWave §7- §bCLEAR"))
                bossBar.color(BossBar.Color.GREEN)
                bossBar.progress(1.0f)
                return
            }

            val waveLabel = if (clearedWave >= session.waves) "Last Wave" else "Wave $clearedWave"
            bossBar.name(Component.text("§7- §6$waveLabel §7- §bCLEAR"))
            bossBar.color(BossBar.Color.GREEN)
            bossBar.progress(1.0f)
            return
        }

        val wave = session.currentWave.coerceIn(1, session.waves)
        if (wave == session.waves && hasBarrierActivationObjective(session)) {
            val total = session.actionMarkers.values.count { it.type == ArenaActionMarkerType.BARRIER_ACTIVATE }.coerceAtLeast(1)
            val activated = session.actionMarkers.values.count {
                it.type == ArenaActionMarkerType.BARRIER_ACTIVATE && it.state == ArenaActionMarkerState.RUNNING
            }
            val progress = (activated.toDouble() / total.toDouble()).toFloat().coerceIn(0.0f, 1.0f)
            bossBar.name(Component.text("§7- §6Last Wave §7- §8(§7$activated/§b$total§8)"))
            bossBar.color(BossBar.Color.BLUE)
            bossBar.progress(progress)
            return
        }

        val kills = session.waveKillCount[wave] ?: 0
        val target = (session.waveClearTargets[wave] ?: 1).coerceAtLeast(1)
        val progress = (kills.toDouble() / target.toDouble()).toFloat().coerceIn(0.0f, 1.0f)
        val waveLabel = if (wave >= session.waves) "Last Wave" else "Wave $wave"
        bossBar.name(Component.text("§7- §6$waveLabel §7- §8(§7$kills/§c$target§8)"))
        bossBar.color(BossBar.Color.RED)
        bossBar.progress(progress)
    }

    private fun spawnMobDisappearSmoke(location: Location) {
        val world = location.world ?: return
        world.spawnParticle(Particle.SMOKE, location, 10, 1.0, 1.0, 1.0, 0.0)
    }

    private fun hideSessionProgressBossBar(session: ArenaSession) {
        val bossBar = session.progressBossBar ?: return
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            if (player.isOnline) {
                player.hideBossBar(bossBar)
            }
        }
        session.progressBossBar = null
    }

    private fun cleanupBarrierRestartSession(session: ArenaSession, removeDefenseMobs: Boolean, smoke: Boolean) {
        session.barrierAmbientTask?.cancel()
        session.barrierAmbientTask = null
        session.barrierRestartTask?.cancel()
        session.barrierRestartTask = null
        session.barrierRestartActivationTask?.cancel()
        session.barrierRestartActivationTask = null
        session.barrierRestartEffectTask?.cancel()
        session.barrierRestartEffectTask = null
        session.barrierRestartDamageTask?.cancel()
        session.barrierRestartDamageTask = null
        session.barrierDefenseSpawnTask?.cancel()
        session.barrierDefenseSpawnTask = null
        session.barrierDefensePressureTask?.cancel()
        session.barrierDefensePressureTask = null

        if (removeDefenseMobs) {
            removeBarrierDefenseMobs(session, smoke)
        }

        session.barrierDefenseTargetMobIds.clear()
        session.barrierDefenseAssaultMobIds.clear()
        session.barrierRestartActivationQueue.clear()
        session.barrierRestartActivatedPoints.clear()

        hideSessionProgressBossBar(session)
    }

    private fun removeBarrierDefenseMobs(session: ArenaSession, smoke: Boolean) {
        val ids = session.barrierDefenseMobIds.toList()
        ids.forEach { mobId ->
            mobToSessionWorld.remove(mobId)
            mobToDefinitionTypeId.remove(mobId)
            mobService.untrack(mobId)
            session.barrierDefenseTargetMobIds.remove(mobId)
            session.barrierDefenseAssaultMobIds.remove(mobId)

            val entity = Bukkit.getEntity(mobId) as? LivingEntity
            if (entity != null && entity.isValid && !entity.isDead) {
                if (smoke) {
                    spawnSmoke(entity.location)
                }
                entity.remove()
            }
            session.barrierDefenseMobIds.remove(mobId)
        }
    }

    private fun spawnSmoke(location: Location) {
        val world = location.world ?: return
        world.spawnParticle(Particle.SMOKE, location, 30, 0.5, 2.0, 0.5, 0.0)
    }

    private fun spawnBarrierRestartStartParticles(world: World, session: ArenaSession) {
        val center = session.barrierLocation.clone().add(0.5, 0.5, 0.5)
        world.spawnParticle(Particle.ENCHANTED_HIT, center, 150, 3.5, 3.5, 3.5, 0.0)
        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, center, 100, 3.5, 3.5, 3.5, 0.0)
    }

    private fun spawnBarrierRestartSuccessParticles(world: World, session: ArenaSession) {
        val center = session.barrierLocation.clone().add(0.5, 0.5, 0.5)
        world.spawnParticle(Particle.OMINOUS_SPAWNING, center, 150, 3.5, 3.5, 3.5, 0.0)
        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, center, 150, 3.5, 3.5, 3.5, 0.0)
    }

    private fun playBarrierPointActivatedEffect(session: ArenaSession, marker: ArenaActionMarker) {
        val world = Bukkit.getWorld(session.worldName) ?: return
        spawnBarrierRestartSuccessParticles(world, session)
        world.spawnParticle(
            Particle.TRIAL_SPAWNER_DETECTION_OMINOUS,
            marker.center,
            50,
            1.0,
            2.0,
            1.0,
            0.0
        )
    }

    private fun broadcastRawMessage(session: ArenaSession, message: String) {
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            if (player.isOnline && player.world.name == session.worldName) {
                player.sendMessage(message)
            }
        }
    }

    private fun playSoundAtBarrier(session: ArenaSession, sound: Sound, volume: Float, pitch: Float) {
        val world = Bukkit.getWorld(session.worldName) ?: return
        val center = session.barrierLocation.clone().add(0.5, 0.5, 0.5)
        world.playSound(center, sound, volume, pitch)
    }

    private fun broadcastMessage(
        session: ArenaSession,
        key: String,
        fallback: String,
        vararg placeholders: Pair<String, Any?>
    ) {
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            player.sendMessage(ArenaI18n.text(player, key, fallback, *placeholders))
        }
    }

    private fun playSound(session: ArenaSession, sound: Sound, volume: Float, pitch: Float) {
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            player.playSound(player.location, sound, volume, pitch)
        }
    }

    private fun requestArenaBgmMode(session: ArenaSession, targetMode: ArenaBgmMode) {
        val currentTick = Bukkit.getCurrentTick().toLong()
        if (targetMode == session.arenaBgmMode && session.arenaBgmSwitchRequest == null) {
            return
        }

        if (session.arenaBgmMode == ArenaBgmMode.STOPPED) {
            if (targetMode == ArenaBgmMode.STOPPED) {
                session.arenaBgmSwitchRequest = null
                return
            }
            startArenaBgmMode(session, targetMode, currentTick)
            return
        }

        if (targetMode == session.arenaBgmMode) {
            session.arenaBgmSwitchRequest = null
            return
        }

        val request = buildArenaBgmSwitchRequest(session, targetMode, currentTick) ?: return
        session.arenaBgmSwitchRequest = request
    }

    private fun buildArenaBgmSwitchRequest(
        session: ArenaSession,
        targetMode: ArenaBgmMode,
        requestedAtTick: Long
    ): ArenaBgmSwitchRequest? {
        val currentTrack = arenaBgmTrackForMode(session.arenaBgmMode) ?: return null
        val currentAbsoluteBeat = currentAbsoluteBeatAtTick(session, currentTrack, requestedAtTick)
        val currentLoopBeats = currentTrack.loopBeats.toLong().coerceAtLeast(1L)
        val currentLoopBeat = (((currentAbsoluteBeat - 1L) % currentLoopBeats) + 1L).toInt()
        val nextSwitchBeat = currentTrack.switchBeats.firstOrNull { it > currentLoopBeat }
            ?: currentTrack.switchBeats.first()
        val currentLoopIndex = (currentAbsoluteBeat - 1L) / currentLoopBeats
        val executeAtAbsoluteBeat = if (nextSwitchBeat > currentLoopBeat) {
            (currentLoopIndex * currentLoopBeats) + nextSwitchBeat.toLong()
        } else {
            ((currentLoopIndex + 1L) * currentLoopBeats) + nextSwitchBeat.toLong()
        }

        return ArenaBgmSwitchRequest(
            targetMode = targetMode,
            requestedAtTick = requestedAtTick,
            executeAtAbsoluteBeat = executeAtAbsoluteBeat
        )
    }

    private fun updateArenaBgmTransitions() {
        val currentTick = Bukkit.getCurrentTick().toLong()
        sessionsByWorld.values.forEach { session ->
            updateArenaCombatBgmRequest(session)
            processArenaBgmSwitchRequest(session, currentTick)
        }
    }

    private fun updateArenaCombatBgmRequest(session: ArenaSession) {
        if (session.arenaBgmSwitchRequest != null) return
        val hasTargetingMob = hasMobTargetingParticipants(session)
        if (session.arenaBgmMode == ArenaBgmMode.COMBAT) {
            if (hasTargetingMob) {
                session.arenaCombatHadTargetingMob = true
                return
            }
            if (!session.arenaCombatHadTargetingMob) return
            requestArenaBgmMode(session, ArenaBgmMode.NORMAL)
        } else if (session.arenaBgmMode == ArenaBgmMode.NORMAL && hasTargetingMob) {
            requestArenaBgmMode(session, ArenaBgmMode.COMBAT)
        }
    }

    private fun hasMobTargetingParticipants(session: ArenaSession): Boolean {
        val participantIds = session.participants
        if (participantIds.isEmpty()) return false

        val trackedMobIds = sequence {
            yieldAll(session.activeMobs)
            yieldAll(session.barrierDefenseMobIds)
        }

        return trackedMobIds.any { mobId ->
            val mob = Bukkit.getEntity(mobId) as? Mob ?: return@any false
            if (!mob.isValid || mob.isDead || mob.world.name != session.worldName) {
                return@any false
            }
            val target = mob.target as? Player ?: return@any false
            participantIds.contains(target.uniqueId)
        }
    }

    private fun processArenaBgmSwitchRequest(session: ArenaSession, currentTick: Long) {
        val request = session.arenaBgmSwitchRequest ?: return

        if (session.arenaBgmMode == ArenaBgmMode.STOPPED) {
            session.arenaBgmSwitchRequest = null
            if (request.targetMode != ArenaBgmMode.STOPPED) {
                startArenaBgmMode(session, request.targetMode, currentTick)
            }
            return
        }

        val currentTrack = arenaBgmTrackForMode(session.arenaBgmMode) ?: return
        val absoluteBeatNow = currentAbsoluteBeatAtTick(session, currentTrack, currentTick)
        if (absoluteBeatNow < request.executeAtAbsoluteBeat) {
            return
        }

        session.arenaBgmSwitchRequest = null
        if (request.targetMode == ArenaBgmMode.STOPPED) {
            stopArenaBgm(session)
            return
        }
        if (request.targetMode == session.arenaBgmMode) {
            return
        }

        startArenaBgmMode(session, request.targetMode, currentTick)
    }

    private fun currentAbsoluteBeatAtTick(
        session: ArenaSession,
        track: ArenaBgmTrackConfig,
        tick: Long
    ): Long {
        val elapsedTicks = (tick - session.arenaBgmPlaybackStartTick).coerceAtLeast(0L)
        return floor(elapsedTicks.toDouble() / track.beatTicks).toLong() + 1L
    }

    private fun startArenaBgmMode(session: ArenaSession, mode: ArenaBgmMode, startTick: Long) {
        val track = arenaBgmTrackForMode(mode) ?: return
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            if (!player.isOnline || player.world.name != session.worldName) return@forEach
            BGMManager.playLoopTicks(player, track.soundKey, track.loopTicks)
        }
        session.arenaBgmMode = mode
        session.arenaBgmPlaybackStartTick = startTick
        session.arenaCombatHadTargetingMob = false
        session.arenaBgmSwitchRequest = null
    }

    private fun stopArenaBgm(session: ArenaSession) {
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            if (!player.isOnline || player.world.name != session.worldName) return@forEach
            stopArenaBgmForPlayer(player)
        }
        session.arenaBgmMode = ArenaBgmMode.STOPPED
        session.arenaBgmPlaybackStartTick = 0L
        session.arenaCombatHadTargetingMob = false
        session.arenaBgmSwitchRequest = null
    }

    private fun stopArenaBgmPlaybackOnly(session: ArenaSession) {
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            if (!player.isOnline || player.world.name != session.worldName) return@forEach
            stopArenaBgmForPlayer(player)
        }
    }

    private fun stopArenaBgmForPlayer(player: Player) {
        arenaBgmKeys().forEach { soundKey ->
            BGMManager.stop(player, soundKey)
        }
    }

    private fun arenaBgmTrackForMode(mode: ArenaBgmMode): ArenaBgmTrackConfig? {
        return when (mode) {
            ArenaBgmMode.NORMAL -> arenaBgmConfig.normal
            ArenaBgmMode.COMBAT -> arenaBgmConfig.combat
            ArenaBgmMode.STOPPED -> null
        }
    }

    private fun arenaBgmKeys(): Set<String> {
        return setOf(arenaBgmConfig.normal.soundKey, arenaBgmConfig.combat.soundKey)
    }

    private fun createArenaWorld(): World? {
        val worldName = "arena.${UUID.randomUUID()}"
        val creator = WorldCreator(worldName)
        creator.generator(VoidChunkGenerator())
        val world = creator.createWorld()
        world?.apply {
            setGameRule(GameRule.MAX_ENTITY_CRAMMING, 0)
            setGameRule(GameRule.DO_MOB_SPAWNING, false)
            setGameRule(GameRule.DO_TILE_DROPS, false)
            setGameRule(GameRule.MOB_GRIEFING, false)
            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
            setGameRule(GameRule.DO_WEATHER_CYCLE, false)
            setGameRule(GameRule.KEEP_INVENTORY, true)
            time = 6000
            WorldSettingsHelper.applyDistanceSettings(plugin, this, "arena.world_settings")
        }
        return world
    }

    private fun tryDeleteWorld(world: World) {
        if (!world.name.startsWith("arena.")) return

        val spawn = Bukkit.getWorlds().firstOrNull()?.spawnLocation
        world.players.toList().forEach { player ->
            if (spawn != null) {
                player.teleport(spawn)
            }
        }

        val unloaded = Bukkit.unloadWorld(world, false)
        if (!unloaded) {
            queueWorldDeletion(world.name, world.worldFolder)
            return
        }

        queueWorldDeletion(world.name, world.worldFolder)
    }

    private fun deleteQueuedWorldFolder(pending: PendingWorldDeletion): Boolean {
        if (Bukkit.getWorld(pending.worldName) != null) {
            return false
        }

        if (!pending.folder.exists()) {
            return true
        }

        return deleteDirectory(pending.folder)
    }

    private fun unloadQueuedWorldIfLoaded(pending: PendingWorldDeletion): Boolean {
        val loaded = Bukkit.getWorld(pending.worldName) ?: return true
        return Bukkit.unloadWorld(loaded, false)
    }

    private fun markQueuedWorldDeletionFailed(pending: PendingWorldDeletion, maxAttempts: Int) {
        pending.attempts += 1
        if (pending.attempts >= maxAttempts) {
            plugin.logger.severe("[Arena] ワールド削除を断念: ${pending.worldName} path=${pending.folder.absolutePath}")
            pendingWorldDeletions.remove(pending.worldName)
        }
    }

    private fun queueWorldDeletion(worldName: String, folder: File) {
        pendingWorldDeletions.putIfAbsent(worldName, PendingWorldDeletion(worldName, folder))
    }

    private fun initializeActionMarkers(session: ArenaSession) {
        val world = Bukkit.getWorld(session.worldName) ?: return
        val markers = mutableMapOf<UUID, ArenaActionMarker>()

        session.barrierPointLocations.forEach { location ->
            val marker = ArenaActionMarker(
                id = UUID.randomUUID(),
                type = ArenaActionMarkerType.BARRIER_ACTIVATE,
                center = location.clone().apply {
                    this.world = world
                    y += ACTION_MARKER_CENTER_Y_OFFSET
                },
                holdTicksRequired = BARRIER_MARKER_HOLD_TICKS,
                stateColors = mapOf(
                    ArenaActionMarkerState.PRE_ACTIVATED to Color.fromRGB(255, 216, 64),
                    ArenaActionMarkerState.READY to Color.fromRGB(255, 216, 64),
                    ArenaActionMarkerState.RUNNING to Color.fromRGB(128, 220, 255)
                ),
                state = ArenaActionMarkerState.READY
            )
            marker.colorTransitionFrom = marker.colorFor(ArenaActionMarkerState.READY)
            marker.colorTransitionTo = marker.colorFor(ArenaActionMarkerState.READY)
            marker.colorTransitionStartTick = Bukkit.getCurrentTick().toLong()
            markers[marker.id] = marker
        }

        session.actionMarkers.clear()
        session.actionMarkers.putAll(markers)
        session.actionMarkerHoldStates.clear()
    }

    private fun ensureDoorActionMarkersForTargetWave(session: ArenaSession, targetWave: Int) {
        if (targetWave <= 0 || targetWave > session.waves) return
        if (session.actionMarkers.values.any { it.type == ArenaActionMarkerType.DOOR_TOGGLE && it.wave == targetWave }) {
            return
        }

        val world = Bukkit.getWorld(session.worldName) ?: return
        val locations = session.corridorDoorBlocks[targetWave].orEmpty()
        locations.forEach { location ->
            val marker = ArenaActionMarker(
                id = UUID.randomUUID(),
                type = ArenaActionMarkerType.DOOR_TOGGLE,
                center = location.clone().apply {
                    this.world = world
                    y += ACTION_MARKER_CENTER_Y_OFFSET
                },
                holdTicksRequired = actionMarkerHoldTicks,
                wave = targetWave
            )
            session.actionMarkers[marker.id] = marker
        }

    }

    private fun updateMultiplayerJoinStates() {
        val now = System.currentTimeMillis()
        sessionsByWorld.values.toList().forEach { session ->
            if (!session.multiplayerJoinEnabled) {
                return@forEach
            }

            val owner = Bukkit.getPlayer(session.ownerPlayerId)
            if (owner == null || !owner.isOnline) {
                terminateSession(
                    session,
                    false,
                    messageKey = "arena.messages.multiplayer.cancelled_owner_offline",
                    fallbackMessage = "&cオーナーが不在のためマルチ参加募集を終了しました"
                )
                return@forEach
            }

            updateJoinCountdownBossBars(session, now)
            updateWaitingParticipants(session)

            if (!session.multiplayerJoinFinalizeStarted && now >= session.joinGraceEndMillis) {
                session.multiplayerJoinFinalizeStarted = true
            }

            if (!session.multiplayerJoinFinalizeStarted) {
                return@forEach
            }

            if (!session.stageGenerationCompleted) {
                if (!session.stageGenerationWaitTitleShown) {
                    val waiters = session.waitingParticipants
                        .mapNotNull { Bukkit.getPlayer(it) }
                        .filter { it.isOnline }
                    waiters.forEach { waitingPlayer ->
                        waitingPlayer.sendTitle("§7アリーナを準備中...", "", 0, 60, 0)
                    }
                    session.stageGenerationWaitTitleShown = true
                }
                return@forEach
            }

            if (!session.multiplayerJoinIntroStarted) {
                finalizeMultiplayerJoin(session)
            }
        }
    }

    private fun updateJoinCountdownBossBars(session: ArenaSession, nowMillis: Long) {
        val owner = Bukkit.getPlayer(session.ownerPlayerId)
        if (owner != null && owner.isOnline) {
            val ownerRemaining = (session.joinGraceEndMillis - nowMillis).coerceAtLeast(0L)
            val ownerProgress = if (session.joinGraceDurationMillis <= 0L) {
                0.0f
            } else {
                (ownerRemaining.toDouble() / session.joinGraceDurationMillis.toDouble()).toFloat().coerceIn(0.0f, 1.0f)
            }
            val ownerBar = getOrCreateJoinCountdownBossBar(session, owner.uniqueId)
            ownerBar.name(Component.text("§7アリーナを準備中... §8(§b残り ${formatRemainingSeconds(ownerRemaining)}秒§8)"))
            ownerBar.progress(ownerProgress)
            owner.showBossBar(ownerBar)
        }

        session.invitedParticipants.forEach { invitedId ->
            val invited = Bukkit.getPlayer(invitedId) ?: return@forEach
            if (!invited.isOnline) return@forEach

            val invitedAt = session.invitedAtMillis[invitedId] ?: nowMillis
            val invitedEnd = invitedAt + session.joinGraceDurationMillis
            val remaining = (invitedEnd - nowMillis).coerceAtLeast(0L)
            val progress = if (session.joinGraceDurationMillis <= 0L) {
                0.0f
            } else {
                (remaining.toDouble() / session.joinGraceDurationMillis.toDouble()).toFloat().coerceIn(0.0f, 1.0f)
            }
            val bar = getOrCreateJoinCountdownBossBar(session, invitedId)
            bar.name(Component.text("§7アリーナを準備中... §8(§b残り ${formatRemainingSeconds(remaining)}秒§8)"))
            bar.progress(progress)
            invited.showBossBar(bar)
        }
    }

    private fun getOrCreateJoinCountdownBossBar(session: ArenaSession, playerId: UUID): BossBar {
        return session.joinCountdownBossBars.getOrPut(playerId) {
            BossBar.bossBar(Component.text("§7アリーナを準備中... §8(§b残り 0.0秒§8)"), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS)
        }
    }

    private fun formatRemainingSeconds(remainingMillis: Long): String {
        val tenths = (remainingMillis.coerceAtLeast(0L) / 100L).toDouble() / 10.0
        return String.format(Locale.US, "%.1f", tenths)
    }

    private fun updateWaitingParticipants(session: ArenaSession) {
        val ownerId = session.ownerPlayerId
        val candidateIds = linkedSetOf<UUID>()
        candidateIds += ownerId
        candidateIds += session.invitedParticipants

        val waitingNow = candidateIds
            .asSequence()
            .mapNotNull { Bukkit.getPlayer(it) }
            .filter { it.isOnline }
            .filter { player ->
                session.joinAreaMarkerLocations.any { markerLocation ->
                    isInsideJoinArea(markerLocation, player.location)
                }
            }
            .map { it.uniqueId }
            .toSet()

        val entered = waitingNow - session.waitingParticipants
        val exited = session.waitingParticipants - waitingNow
        session.waitingParticipants.clear()
        session.waitingParticipants.addAll(waitingNow)

        entered.forEach { playerId ->
            if (!session.waitingNotifiedParticipants.add(playerId)) return@forEach
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            player.sendMessage(
                ArenaI18n.text(
                    player,
                    "arena.messages.multiplayer.waiting_entered",
                    "&a開始待機エリアに入りました。猶予終了まで待機してください"
                )
            )
        }

        exited.forEach { playerId ->
            session.waitingNotifiedParticipants.remove(playerId)
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            player.sendMessage(
                ArenaI18n.text(
                    player,
                    "arena.messages.multiplayer.waiting_exited",
                    "§c待機エリアから退出しました。この開始までにこのエリアにいないと、アリーナに参加できません！"
                )
            )
        }
    }

    private fun finalizeMultiplayerJoin(session: ArenaSession) {
        val owner = Bukkit.getPlayer(session.ownerPlayerId)
        if (owner == null || !owner.isOnline) {
            terminateSession(
                session,
                false,
                messageKey = "arena.messages.multiplayer.cancelled_owner_offline",
                fallbackMessage = "&cオーナーが不在のためマルチ参加募集を終了しました"
            )
            return
        }

        if (!session.waitingParticipants.contains(session.ownerPlayerId)) {
            terminateSession(
                session,
                false,
                messageKey = "arena.messages.multiplayer.cancelled_owner_not_waiting",
                fallbackMessage = "&cオーナーが開始待機エリアにいないため開始を中止しました"
            )
            return
        }

        val participants = session.waitingParticipants
            .asSequence()
            .mapNotNull { Bukkit.getPlayer(it) }
            .filter { it.isOnline }
            .filter { !playerToSessionWorld.containsKey(it.uniqueId) || playerToSessionWorld[it.uniqueId] == session.worldName }
            .toList()

        if (participants.none { it.uniqueId == session.ownerPlayerId }) {
            terminateSession(
                session,
                false,
                messageKey = "arena.messages.multiplayer.cancelled_owner_not_waiting",
                fallbackMessage = "&cオーナーが開始待機エリアにいないため開始を中止しました"
            )
            return
        }

        participants.forEach { player ->
            session.participants.add(player.uniqueId)
            playerToSessionWorld[player.uniqueId] = session.worldName
            session.returnLocations.putIfAbsent(player.uniqueId, player.location.clone())
        }
        clearMultiplayerRecruitmentState(session)
        session.multiplayerJoinEnabled = false
        session.joinGraceEndMillis = 0L

        startMultiplayerStageIntro(session, participants)
    }

    private fun startMultiplayerStageIntro(session: ArenaSession, participants: List<Player>) {
        if (session.multiplayerJoinIntroStarted) {
            return
        }
        session.multiplayerJoinIntroStarted = true

        participants.forEach { player ->
            player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 120, 0, false, false, false))
            player.sendMessage(
                ArenaI18n.text(
                    player,
                    "arena.messages.multiplayer.stage_intro",
                    "&6ステージ転送を開始します..."
                )
            )
        }

        repeat(MULTIPLAYER_STAGE_INTRO_SOUND_REPEAT) { index ->
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                participants.forEach { player ->
                    if (player.isOnline) {
                        player.playSound(player.location, "minecraft:item.armor.equip_netherite", 1.0f, 0.5f)
                    }
                }
            }, index * MULTIPLAYER_STAGE_INTRO_SOUND_INTERVAL_TICKS)
        }

        val teleportDelay =
            MULTIPLAYER_STAGE_INTRO_SOUND_REPEAT * MULTIPLAYER_STAGE_INTRO_SOUND_INTERVAL_TICKS +
                MULTIPLAYER_STAGE_INTRO_TELEPORT_EXTRA_DELAY_TICKS
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val world = Bukkit.getWorld(session.worldName) ?: return@Runnable
            val now = System.currentTimeMillis()

            participants.forEach { player ->
                if (!player.isOnline) return@forEach
                val spawnLocation = session.entranceLocation.clone().apply {
                    this.world = world
                }
                applyStageStartFacingYaw(session, spawnLocation)
                player.teleport(spawnLocation)
                session.participantLocationHistory[player.uniqueId] = ArrayDeque<TimedPlayerLocation>().apply {
                    addLast(TimedPlayerLocation(now, spawnLocation.clone()))
                }
                session.participantLastSampleMillis[player.uniqueId] = now
            }

            session.participantSpawnProtectionUntilMillis = System.currentTimeMillis() + 4000L
            requestArenaBgmMode(session, ArenaBgmMode.NORMAL)
            setDoorActionMarkersReadySilently(session, 1)
        }, teleportDelay)
    }

    private fun findNearbyJoinAreaMarkers(origin: Location, radius: Double): List<Location> {
        val world = origin.world ?: return emptyList()
        return world.getNearbyEntities(origin, radius, radius, radius)
            .asSequence()
            .filterIsInstance<Marker>()
            .filter { marker -> marker.scoreboardTags.contains("arena.marker.join_area") }
            .map { it.location.clone() }
            .toList()
    }

    private fun isInsideJoinArea(markerLocation: Location, playerLocation: Location): Boolean {
        val markerWorld = markerLocation.world ?: return false
        if (playerLocation.world?.uid != markerWorld.uid) return false

        val blockX = markerLocation.blockX
        val blockY = markerLocation.blockY
        val blockZ = markerLocation.blockZ

        val x = playerLocation.x
        val y = playerLocation.y
        val z = playerLocation.z

        return x >= blockX && x < blockX + 1.0 &&
            z >= blockZ && z < blockZ + 1.0 &&
            y >= blockY && y < blockY + 0.125
    }

    private fun updateActionMarkers() {
        val currentTick = Bukkit.getCurrentTick().toLong()
        sessionsByWorld.values.forEach { session ->
            val world = Bukkit.getWorld(session.worldName) ?: return@forEach
            if (session.actionMarkers.isEmpty()) return@forEach

            val participants = session.participants
                .asSequence()
                .mapNotNull { Bukkit.getPlayer(it) }
                .filter { it.isOnline && it.world.uid == world.uid }
                .toList()
            if (participants.isEmpty()) return@forEach

            session.actionMarkers.values.forEach { marker ->
                val holdProgress = markerHoldProgress(session, marker)
                advanceMarkerMiddleRingRotation(marker, holdProgress)
                val color = resolveActionMarkerDisplayColor(marker, currentTick, holdProgress)
                participants.forEach { player ->
                    renderActionMarkerParticles(player, marker, color)
                }
            }

            participants.forEach { player ->
                updateActionMarkerHoldState(session, player, currentTick)
            }

            session.actionMarkerHoldStates.keys.retainAll(session.participants)
        }
    }

    private fun updateActionMarkerHoldState(session: ArenaSession, player: Player, currentTick: Long) {
        val marker = findHoldableActionMarker(session, player.location)
        if (!player.isSneaking || marker == null) {
            resetActionMarkerHoldState(session, player.uniqueId, currentTick)
            return
        }

        val holdState = session.actionMarkerHoldStates.getOrPut(player.uniqueId) { ArenaActionMarkerHoldState() }
        if (holdState.markerId != marker.id) {
            val previousMarkerId = holdState.markerId
            val previousHeldTicks = holdState.heldTicks
            if (previousMarkerId != null && previousHeldTicks > 0) {
                val previousMarker = session.actionMarkers[previousMarkerId]
                if (previousMarker != null && previousMarker.state == ArenaActionMarkerState.READY) {
                    beginActionMarkerReturnTransition(previousMarker, previousHeldTicks, currentTick)
                }
            }
            holdState.markerId = marker.id
            holdState.heldTicks = 0
            holdState.startSoundPlayed = false
        }

        if (!holdState.startSoundPlayed) {
            playActionMarkerStartSound(session, marker)
            holdState.startSoundPlayed = true
        }

        holdState.heldTicks = (holdState.heldTicks + 1).coerceAtMost(marker.holdTicksRequired)

        if (holdState.heldTicks >= marker.holdTicksRequired) {
            transitionActionMarkerState(
                marker,
                ArenaActionMarkerState.RUNNING,
                currentTick,
                fromColor = resolveActionMarkerHoldColor(marker, 1.0)
            )
            playActionMarkerCompleteSound(session, marker)
            onActionMarkerTriggered(session, marker)
            resetActionMarkerHoldState(session, player.uniqueId, currentTick, animateReturn = false)
        }
    }

    private fun markerHoldProgress(session: ArenaSession, marker: ArenaActionMarker): Double {
        val required = marker.holdTicksRequired.coerceAtLeast(1)
        val highestHold = session.actionMarkerHoldStates.values
            .asSequence()
            .filter { it.markerId == marker.id }
            .maxOfOrNull { it.heldTicks }
            ?: 0
        return (highestHold.toDouble() / required.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun advanceMarkerMiddleRingRotation(marker: ArenaActionMarker, holdProgress: Double) {
        val angularVelocity = MIDDLE_RING_MAX_ANGULAR_VELOCITY * holdProgress.coerceIn(0.0, 1.0)
        marker.middleRingAngleRadians = (marker.middleRingAngleRadians + angularVelocity) % (Math.PI * 2.0)
    }

    private fun onActionMarkerTriggered(session: ArenaSession, marker: ArenaActionMarker) {
        when (marker.type) {
            ArenaActionMarkerType.DOOR_TOGGLE -> {
                val targetWave = marker.wave ?: return
                startDoorAnimation(session, targetWave)
            }
            ArenaActionMarkerType.BARRIER_ACTIVATE -> {
                playBarrierPointActivatedEffect(session, marker)
                if (!session.startedWaves.contains(session.waves)) return
                val total = session.actionMarkers.values.count { it.type == ArenaActionMarkerType.BARRIER_ACTIVATE }
                val activated = session.actionMarkers.values.count {
                    it.type == ArenaActionMarkerType.BARRIER_ACTIVATE && it.state == ArenaActionMarkerState.RUNNING
                }
                updateSessionProgressBossBar(session)
                if (total > 0 && activated >= total) {
                    onAllWavesCleared(session)
                }
            }
        }
    }

    private fun findHoldableActionMarker(session: ArenaSession, location: Location): ArenaActionMarker? {
        val worldUid = location.world?.uid ?: return null
        return session.actionMarkers.values
            .asSequence()
            .filter { it.state == ArenaActionMarkerState.READY }
            .filter { it.center.world?.uid == worldUid }
            .filter { marker ->
                val dx = location.x - marker.center.x
                val dz = location.z - marker.center.z
                (dx * dx) + (dz * dz) <= ACTION_MARKER_RADIUS_SQUARED
            }
            .minByOrNull { marker ->
                val dx = location.x - marker.center.x
                val dz = location.z - marker.center.z
                (dx * dx) + (dz * dz)
            }
    }

    private fun resetActionMarkerHoldState(
        session: ArenaSession,
        playerId: UUID,
        currentTick: Long,
        animateReturn: Boolean = true
    ) {
        val removed = session.actionMarkerHoldStates.remove(playerId) ?: return
        if (!animateReturn || removed.heldTicks <= 0) return
        val markerId = removed.markerId ?: return
        val marker = session.actionMarkers[markerId] ?: return
        if (marker.state != ArenaActionMarkerState.READY) return

        val stillHolding = session.actionMarkerHoldStates.values.any { it.markerId == markerId && it.heldTicks > 0 }
        if (stillHolding) return

        beginActionMarkerReturnTransition(marker, removed.heldTicks, currentTick)
    }

    private fun transitionActionMarkerState(
        marker: ArenaActionMarker,
        target: ArenaActionMarkerState,
        currentTick: Long,
        fromColor: Color? = null
    ): Boolean {
        if (marker.state == target) return false
        val currentColor = fromColor ?: resolveActionMarkerTransitionColor(marker, currentTick)
        marker.state = target
        marker.colorTransitionFrom = currentColor
        marker.colorTransitionTo = marker.colorFor(target)
        marker.colorTransitionStartTick = currentTick
        return true
    }

    private fun resolveActionMarkerDisplayColor(marker: ArenaActionMarker, currentTick: Long, holdProgress: Double): Color {
        if (marker.state == ArenaActionMarkerState.READY && holdProgress > 0.0) {
            return resolveActionMarkerHoldColor(marker, holdProgress)
        }
        return resolveActionMarkerTransitionColor(marker, currentTick)
    }

    private fun resolveActionMarkerTransitionColor(marker: ArenaActionMarker, currentTick: Long): Color {
        val durationTicks = actionMarkerColorTransitionTicks.coerceAtLeast(1)
        val elapsed = (currentTick - marker.colorTransitionStartTick).coerceAtLeast(0L)
        if (elapsed >= durationTicks) {
            return marker.colorTransitionTo
        }

        val ratio = elapsed.toDouble() / durationTicks.toDouble()
        return blendColor(marker.colorTransitionFrom, marker.colorTransitionTo, ratio)
    }

    private fun beginActionMarkerReturnTransition(marker: ArenaActionMarker, heldTicks: Int, currentTick: Long) {
        val heldProgress = (heldTicks.toDouble() / marker.holdTicksRequired.coerceAtLeast(1).toDouble()).coerceIn(0.0, 1.0)
        marker.colorTransitionFrom = resolveActionMarkerHoldColor(marker, heldProgress)
        marker.colorTransitionTo = marker.colorFor(ArenaActionMarkerState.READY)
        marker.colorTransitionStartTick = currentTick
    }

    private fun resolveActionMarkerHoldColor(marker: ArenaActionMarker, holdProgress: Double): Color {
        val ratio = holdProgress.coerceIn(0.0, 1.0)
        return blendColor(
            marker.colorFor(ArenaActionMarkerState.READY),
            marker.colorFor(ArenaActionMarkerState.RUNNING),
            ratio
        )
    }

    private fun blendColor(from: Color, to: Color, ratio: Double): Color {
        val clampedRatio = ratio.coerceIn(0.0, 1.0)
        val red = (from.red + (to.red - from.red) * clampedRatio).roundToInt().coerceIn(0, 255)
        val green = (from.green + (to.green - from.green) * clampedRatio).roundToInt().coerceIn(0, 255)
        val blue = (from.blue + (to.blue - from.blue) * clampedRatio).roundToInt().coerceIn(0, 255)
        return Color.fromRGB(red, green, blue)
    }

    private fun adjustMiddlePointColor(color: Color): Color {
        val hsb = java.awt.Color.RGBtoHSB(color.red, color.green, color.blue, null)
        val shiftedHue = (hsb[0] + 0.01f) % 1.0f
        val softenedSaturation = (hsb[1] * 0.70f).coerceIn(0.0f, 1.0f)
        val softenedBrightness = (hsb[2] * 0.98f).coerceIn(0.0f, 1.0f)
        val rgb = java.awt.Color.HSBtoRGB(shiftedHue, softenedSaturation, softenedBrightness)
        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF
        return Color.fromRGB(red, green, blue)
    }

    private fun playActionMarkerReadyEffect(session: ArenaSession, marker: ArenaActionMarker) {
        val world = marker.center.world ?: return
        val participants = session.participants
            .asSequence()
            .mapNotNull { Bukkit.getPlayer(it) }
            .filter { it.isOnline && it.world.uid == world.uid }
            .toList()

        if (participants.isEmpty()) return

        participants.forEach { player ->
            player.spawnParticle(
                Particle.END_ROD,
                marker.center.x,
                marker.center.y,
                marker.center.z,
                100,
                0.3,
                3.0,
                0.3,
                0.1
            )
            player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.0f)
            player.playSound(player.location, Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 0.5f)
        }
    }

    private fun playActionMarkerStartSound(session: ArenaSession, marker: ArenaActionMarker) {
        val world = marker.center.world ?: return
        session.participants
            .asSequence()
            .mapNotNull { Bukkit.getPlayer(it) }
            .filter { it.isOnline && it.world.uid == world.uid }
            .forEach { player ->
                player.playSound(marker.center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 1.2f)
            }
    }

    private fun playActionMarkerCompleteSound(session: ArenaSession, marker: ArenaActionMarker) {
        val world = marker.center.world ?: return
        val completeSound = when (marker.type) {
            ArenaActionMarkerType.BARRIER_ACTIVATE -> Sound.BLOCK_BEACON_POWER_SELECT
            ArenaActionMarkerType.DOOR_TOGGLE -> Sound.BLOCK_IRON_DOOR_OPEN
        }
        session.participants
            .asSequence()
            .mapNotNull { Bukkit.getPlayer(it) }
            .filter { it.isOnline && it.world.uid == world.uid }
            .forEach { player ->
                player.playSound(marker.center, completeSound, 0.9f, 1.15f)
                if (marker.type == ArenaActionMarkerType.BARRIER_ACTIVATE) {
                    player.playSound(marker.center, Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 0.5f)
                }
                player.playSound(marker.center, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 2.0f)
            }
    }

    private fun renderActionMarkerParticles(player: Player, marker: ArenaActionMarker, color: Color) {
        val center = marker.center
        val world = center.world ?: return
        if (player.world.uid != world.uid) return

        val outerDust = Particle.DustOptions(color, 0.5f)
        val innerDust = Particle.DustOptions(color, 0.5f)
        val middleDust = Particle.DustOptions(adjustMiddlePointColor(color), 0.75f)

        drawActionMarkerRing(player, center, ACTION_MARKER_OUTER_RING_RADIUS, ACTION_MARKER_OUTER_RING_HEIGHT, 32, outerDust)
        drawActionMarkerRing(player, center, ACTION_MARKER_INNER_RING_RADIUS, ACTION_MARKER_INNER_RING_HEIGHT, 24, innerDust)
        drawActionMarkerEightPoints(player, center, marker.middleRingAngleRadians, middleDust)
    }

    private fun drawActionMarkerRing(
        player: Player,
        center: Location,
        radius: Double,
        yOffset: Double,
        points: Int,
        dust: Particle.DustOptions
    ) {
        if (center.world == null) return
        val safePoints = points.coerceAtLeast(8)
        for (index in 0 until safePoints) {
            val angle = (2.0 * Math.PI * index.toDouble()) / safePoints.toDouble()
            val x = center.x + cos(angle) * radius
            val y = center.y + yOffset
            val z = center.z + sin(angle) * radius
            player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }
    }

    private fun drawActionMarkerEightPoints(player: Player, center: Location, baseAngleRadians: Double, dust: Particle.DustOptions) {
        if (center.world == null) return
        for (index in 0 until 8) {
            val angle = baseAngleRadians + (2.0 * Math.PI * index.toDouble()) / 8.0
            val x = center.x + cos(angle) * ACTION_MARKER_MIDDLE_RING_RADIUS
            val y = center.y + ACTION_MARKER_MIDDLE_RING_HEIGHT
            val z = center.z + sin(angle) * ACTION_MARKER_MIDDLE_RING_RADIUS
            player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }
    }

    private fun startMaintenanceTask() {
        if (maintenanceTask != null) return
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            reconcileActiveMobs()
            processPendingWorldDeletions()
        }, 20L, 20L)

        if (playerMonitorTask == null) {
            playerMonitorTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                monitorParticipantPositions()
            }, 5L, 5L)
        }

        if (actionMarkerTask == null) {
            actionMarkerTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                updateMultiplayerJoinStates()
                updateActionMarkers()
                updateArenaBgmTransitions()
            }, 1L, 1L)
        }
    }

    private fun reconcileActiveMobs() {
        sessionsByWorld.values.toList().forEach { session ->
            session.activeMobs.toList().forEach { mobId ->
                val entity = Bukkit.getEntity(mobId)
                val invalid = entity == null || entity.isDead || !entity.isValid || entity.type == EntityType.PLAYER || entity.world.name != session.worldName
                if (invalid) {
                    consumeMob(session, mobId, countKill = false)
                }
            }
        }
    }

    private fun processPendingWorldDeletions() {
        val maxAttempts = 30
        pendingWorldDeletions.values.toList().forEach { pending ->
            if (!unloadQueuedWorldIfLoaded(pending)) {
                markQueuedWorldDeletionFailed(pending, maxAttempts)
                return@forEach
            }

            if (deleteQueuedWorldFolder(pending)) {
                pendingWorldDeletions.remove(pending.worldName)
                plugin.logger.info("[Arena] 未削除ワールドの削除に成功: ${pending.worldName}")
                return@forEach
            }

            markQueuedWorldDeletionFailed(pending, maxAttempts)
        }
    }

    private fun deleteDirectory(file: File): Boolean {
        if (file.isDirectory) {
            val children = file.listFiles() ?: emptyArray()
            for (child in children) {
                if (!deleteDirectory(child)) return false
            }
        }
        return file.delete()
    }
}
