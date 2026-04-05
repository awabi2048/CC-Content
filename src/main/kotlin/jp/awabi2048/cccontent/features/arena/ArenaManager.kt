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
import jp.awabi2048.cccontent.features.arena.quest.ArenaQuestService
import jp.awabi2048.cccontent.features.common.BGMManager
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.VoidChunkGenerator
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.items.arena.ArenaMobTokenItem
import jp.awabi2048.cccontent.items.arena.BoomerangTokenItem
import jp.awabi2048.cccontent.mob.MobDefinition
import jp.awabi2048.cccontent.mob.MobSpawnCondition
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.MobSpawnOptions
import jp.awabi2048.cccontent.mob.ability.PeriodicCobwebAbility
import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import jp.awabi2048.cccontent.util.OageMessageSender
import jp.awabi2048.cccontent.world.WorldSettingsHelper
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import io.papermc.paper.scoreboard.numbers.NumberFormat
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.GameMode
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
import org.bukkit.block.data.Waterlogged
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Ageable
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Marker
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Shulker
import org.bukkit.entity.Zombie
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.Action
import org.bukkit.event.Event
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.entity.EntityMountEvent
import org.bukkit.event.player.PlayerAnimationType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.util.Vector
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.logging.Level
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

private enum class ArenaPoolWorldState {
    READY,
    IN_USE,
    CLEANING,
    BROKEN
}

private data class ArenaWorldCleanupVolume(
    val bounds: ArenaBounds,
    var x: Int = bounds.minX,
    var y: Int = bounds.minY,
    var z: Int = bounds.minZ
)

private data class ArenaWorldCleanupJob(
    val worldName: String,
    val volumes: ArrayDeque<ArenaWorldCleanupVolume>,
    val totalBlocks: Long,
    var processedBlocks: Long,
    val startedAtMillis: Long,
    var lastProgressLogAtMillis: Long
)

private data class ArenaDifficultyConfig(
    val id: String,
    val difficultyRange: ClosedFloatingPointRange<Double>,
    val mobSpawnIntervalTicks: Long,
    val mobCountMultiplier: Double,
    val waveMultiplier: Double,
    val waves: Int,
    val display: String,
    val reviveMaxPerPlayer: Int,
    val reviveTimeLimitSeconds: Int,
    val mobStatsMaxHealth: Double,
    val mobStatsMaxAttack: Double,
    val mobStatsMaxMovementSpeed: Double,
    val mobStatsMaxArmor: Double
) {
    val difficultyValue: Double
        get() = (difficultyRange.start + difficultyRange.endInclusive) / 2.0

    fun waveScale(wave: Int): Double {
        return (1.0 + waveMultiplier * wave).coerceAtLeast(0.0)
    }
}

private data class ArenaDropEntry(
    val itemId: String,
    val minAmount: Int,
    val maxAmount: Int,
    val chance: Double
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
    val switchIntervalBeats: Int
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

private data class EntranceLiftTemplate(
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val structure: org.bukkit.structure.Structure
)

class ArenaManager(
    private val plugin: JavaPlugin,
    private val mobService: MobService = MobService(plugin)
) {
    val confusionManager = ConfusionManager(plugin)

    private companion object {
        const val DEBUG_VOID_WORLD_MARKER_FILE_NAME = ".cc-debug-void-world"
        const val DEBUG_VOID_TEMPLATE_MARKER_FILE_NAME = ".cc-debug-void-template"
        const val DEBUG_VOID_WORLD_TEMPLATE_NAME = "arena.debug.template"
        const val ARENA_POOL_WORLD_NAME_PREFIX = "arena.pool"
        const val ARENA_MAX_CONCURRENT_SESSIONS_DEFAULT = 3
        const val ARENA_POOL_SIZE_DEFAULT = 3
        const val ARENA_CLEANUP_BLOCKS_PER_TICK_DEFAULT = 300
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
        const val BARRIER_RESTART_BEAM_BLINK_INTERVAL_TICKS = 50L
        const val BARRIER_RESTART_BEAM_POINTS = 24
        const val ENTRANCE_NORMAL_BGM_START_DELAY_TICKS = 10L
        const val POSITION_SAMPLE_INTERVAL_MILLIS = 1000L
        const val POSITION_RESTORE_LOOKBACK_MILLIS = 10_000L
        const val POSITION_HISTORY_RETENTION_MILLIS = 12_000L
        const val POSITION_HISTORY_MAX_SAMPLES = 24
        const val ARENA_BGM_NORMAL_KEY_DEFAULT = "kota_server:ost_3.sukima_dungeon"
        const val ARENA_BGM_COMBAT_KEY_DEFAULT = "kota_server:ost_4.arena"
        const val ARENA_BGM_NORMAL_BPM_DEFAULT = 120.0
        const val ARENA_BGM_COMBAT_BPM_DEFAULT = 140.0
        const val ARENA_BGM_LOOP_BEATS_DEFAULT = 384
        const val ARENA_BGM_SWITCH_INTERVAL_BEATS_DEFAULT = 8
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
        const val MOB_TOKEN_DROP_CHANCE = 0.30
        const val MOB_TOKEN_LOOTING_BONUS_PER_LEVEL_DEFAULT = 0.033
        const val FINAL_WAVE_TIME_LIMIT_SECONDS_DEFAULT = 600
        const val STAGE_TRANSFER_BLINDNESS_TICKS = 20
        const val BARRIER_RETURN_HOLD_TICKS = 60
        const val MULTIPLAYER_JOIN_GRACE_SECONDS_DEFAULT = 45
        const val MULTIPLAYER_JOIN_MARKER_SEARCH_RADIUS_DEFAULT = 16.0
        const val MULTIPLAYER_MAX_PARTICIPANTS_DEFAULT = 6
        const val WAVE_START_COMBAT_BGM_DELAY_TICKS = 0L
        const val ENTRANCE_BGM_START_DELAY_TICKS = 30L
        const val MULTIPLAYER_INVITE_SWING_COOLDOWN_TICKS = 5L
        const val OAGE_FOLLOWUP_DELAY_TICKS = 60L
        const val OAGE_COMBAT_MONITOR_INTERVAL_TICKS = 1200L
        const val WAVE_CATCHUP_TELEPORT_DELAY_MILLIS = 30_000L
        const val DOWN_REVIVE_HOLD_SECONDS_DEFAULT = 3
        const val DOWN_REVIVE_RADIUS_DEFAULT = 2.5
        const val DOWN_SHULKER_FOLLOW_INTERVAL_TICKS_DEFAULT = 2L
        const val DOWN_GAME_OVER_BLINDNESS_TICKS = 200
        const val DOWN_GAME_OVER_RETURN_DELAY_MILLIS = 8000L
        const val REVIVE_LINK_TIMEOUT_TICKS = 200L
        const val DOWNED_PLAYER_WALK_SPEED = 0.1f
        const val DOWNED_GAME_OVER_WALK_SPEED = 0.0f
        const val ARENA_SIDEBAR_OBJECTIVE_NAME = "arena"
        const val ARENA_SIDEBAR_MAX_LINES = 15
        const val ENTRANCE_LIFT_STRUCTURE_PATH = "structures/arena/lift.nbt"
        const val ENTRANCE_LIFT_INTERVAL_TICKS_DEFAULT = 10L
        const val ENTRANCE_LIFT_TRANSFER_RISE_BLOCKS_DEFAULT = 2
        const val ENTRANCE_LIFT_MAX_RISE_BLOCKS_DEFAULT = 6
        const val ELDER_CURSE_DAMAGE = 10.0
        const val ELDER_CURSE_DURATION_TICKS = 100
        const val ELDER_CURSE_AMPLIFIER = 2
        const val ELDER_CURSE_PLAYER_COOLDOWN_MILLIS = 60_000L

        fun defaultSwitchIntervalBeats(): Int {
            return ARENA_BGM_SWITCH_INTERVAL_BEATS_DEFAULT
        }
    }

    private val random = kotlin.random.Random.Default
    private val legacySerializer = LegacyComponentSerializer.legacySection()
    private val themeLoader = ArenaThemeLoader(plugin)
    private val stageGenerator = ArenaStageGenerator()
    private val legacyColorPattern = Regex("(?i)§[0-9A-FK-ORX]")
    private val sessionsByWorld = mutableMapOf<String, ArenaSession>()
    private val playerToSessionWorld = mutableMapOf<UUID, String>()
    private val mobToSessionWorld = mutableMapOf<UUID, String>()
    private val pendingWorldDeletions = mutableMapOf<String, PendingWorldDeletion>()
    private val readyArenaWorldNames = ArrayDeque<String>()
    private val arenaWorldStates = mutableMapOf<String, ArenaPoolWorldState>()
    private val cleanupWorldJobs = ArrayDeque<ArenaWorldCleanupJob>()
    private val invitedPlayerLocks = mutableMapOf<UUID, String>()
    private val inviteSwingCooldownUntilTick = mutableMapOf<UUID, Long>()
    private val arenaSidebarPlayers = mutableSetOf<UUID>()
    private val arenaSidebarPreviousScoreboards = mutableMapOf<UUID, org.bukkit.scoreboard.Scoreboard>()
    private val difficultyConfigs = mutableMapOf<String, ArenaDifficultyConfig>()
    private val mobDefinitions = mutableMapOf<String, MobDefinition>()
    private val knownMobTypeIds = mutableSetOf<String>()
    private val mobToDefinitionTypeId = mutableMapOf<UUID, String>()
    private val elderCurseCooldownUntilMillis = mutableMapOf<UUID, Long>()
    private val liftOccupiedMarkerKeys = mutableSetOf<String>()
    private val liftOccupiedWaiters = mutableSetOf<UUID>()
    private var maintenanceTask: BukkitTask? = null
    private var playerMonitorTask: BukkitTask? = null
    private var actionMarkerTask: BukkitTask? = null
    private var shuttingDown: Boolean = false
    private var barrierRestartConfig = BarrierRestartConfig(30, 0.05)
    private var multiplayerJoinGraceSeconds = MULTIPLAYER_JOIN_GRACE_SECONDS_DEFAULT
    private var multiplayerJoinMarkerSearchRadius = MULTIPLAYER_JOIN_MARKER_SEARCH_RADIUS_DEFAULT
    private var multiplayerInviteAutoDeclineDistance = MULTIPLAYER_JOIN_MARKER_SEARCH_RADIUS_DEFAULT
    private var multiplayerStageBuildStepsPerTick = 1
    private var entranceLiftIntervalTicks = ENTRANCE_LIFT_INTERVAL_TICKS_DEFAULT
    private var entranceLiftTransferRiseBlocks = ENTRANCE_LIFT_TRANSFER_RISE_BLOCKS_DEFAULT
    private var entranceLiftMaxRiseBlocks = ENTRANCE_LIFT_MAX_RISE_BLOCKS_DEFAULT
    private var cachedEntranceLiftTemplate: EntranceLiftTemplate? = null
    private var maxConcurrentSessions = ARENA_MAX_CONCURRENT_SESSIONS_DEFAULT
    private var arenaPoolSize = ARENA_POOL_SIZE_DEFAULT
    private var cleanupBlocksPerTick = ARENA_CLEANUP_BLOCKS_PER_TICK_DEFAULT
    private var downReviveHoldSeconds = DOWN_REVIVE_HOLD_SECONDS_DEFAULT
    private var downReviveRadius = DOWN_REVIVE_RADIUS_DEFAULT
    private var downShulkerFollowIntervalTicks = DOWN_SHULKER_FOLLOW_INTERVAL_TICKS_DEFAULT
    private var actionMarkerHoldTicks = 60
    private var actionMarkerColorTransitionTicks = 16
    private var doorAnimationTotalTicks = DOOR_ANIMATION_TOTAL_TICKS_DEFAULT
    private var sharedWaveMaxAlive = SHARED_WAVE_MAX_ALIVE_DEFAULT
    private var mobTokenDropChance = MOB_TOKEN_DROP_CHANCE
    private var mobTokenLootingBonusPerLevel = MOB_TOKEN_LOOTING_BONUS_PER_LEVEL_DEFAULT
    private var finalWaveTimeLimitSeconds = FINAL_WAVE_TIME_LIMIT_SECONDS_DEFAULT
    private var arenaDoorAnimationSoundKey = "minecraft:block.iron_door.open"
    private var arenaDoorAnimationSoundPitch = 1.0f
    private var arenaQuestService: ArenaQuestService? = null
    private var arenaBgmConfig = ArenaBgmConfig(
        normal = ArenaBgmTrackConfig(
            soundKey = ARENA_BGM_NORMAL_KEY_DEFAULT,
            bpm = ARENA_BGM_NORMAL_BPM_DEFAULT,
            loopBeats = ARENA_BGM_LOOP_BEATS_DEFAULT,
            switchIntervalBeats = defaultSwitchIntervalBeats()
        ),
        combat = ArenaBgmTrackConfig(
            soundKey = ARENA_BGM_COMBAT_KEY_DEFAULT,
            bpm = ARENA_BGM_COMBAT_BPM_DEFAULT,
            loopBeats = ARENA_BGM_LOOP_BEATS_DEFAULT,
            switchIntervalBeats = defaultSwitchIntervalBeats()
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
        ensureDebugVoidWorldBootstrap()
        prepareArenaWorldPoolAtStartup()
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
        cachedEntranceLiftTemplate = null

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
        multiplayerInviteAutoDeclineDistance = config.getDouble(
            "arena.multiplayer.invite_auto_decline_distance",
            multiplayerJoinMarkerSearchRadius
        ).coerceAtLeast(1.0)
        multiplayerStageBuildStepsPerTick = config
            .getInt("arena.multiplayer.stage_build_steps_per_tick", 1)
            .coerceAtLeast(1)
        entranceLiftIntervalTicks = config
            .getLong("arena.entrance_lift.interval_ticks", ENTRANCE_LIFT_INTERVAL_TICKS_DEFAULT)
            .coerceAtLeast(1L)
        entranceLiftMaxRiseBlocks = config
            .getInt("arena.entrance_lift.max_rise_blocks", ENTRANCE_LIFT_MAX_RISE_BLOCKS_DEFAULT)
            .coerceAtLeast(1)
        entranceLiftTransferRiseBlocks = config
            .getInt("arena.entrance_lift.transfer_rise_blocks", ENTRANCE_LIFT_TRANSFER_RISE_BLOCKS_DEFAULT)
            .coerceIn(0, entranceLiftMaxRiseBlocks)
        downReviveHoldSeconds = config
            .getInt("arena.down.revive_hold_seconds", DOWN_REVIVE_HOLD_SECONDS_DEFAULT)
            .coerceAtLeast(1)
        downReviveRadius = config
            .getDouble("arena.down.revive_radius", DOWN_REVIVE_RADIUS_DEFAULT)
            .coerceAtLeast(1.0)
        downShulkerFollowIntervalTicks = config
            .getLong("arena.down.shulker_follow_interval_ticks", DOWN_SHULKER_FOLLOW_INTERVAL_TICKS_DEFAULT)
            .coerceAtLeast(1L)
        doorAnimationTotalTicks = config.getInt("arena.door_animation.total_ticks", DOOR_ANIMATION_TOTAL_TICKS_DEFAULT)
            .coerceAtLeast(1)
        arenaDoorAnimationSoundKey = config
            .getString("arena.door_animation.sound.key", "minecraft:block.iron_door.open")
            ?.trim()
            .orEmpty()
            .ifBlank { "minecraft:block.iron_door.open" }
        arenaDoorAnimationSoundPitch = config
            .getDouble("arena.door_animation.sound.pitch", 1.0)
            .toFloat()
            .coerceIn(0.5f, 2.0f)
        sharedWaveMaxAlive = config.getInt("arena.mob_spawn.system_max_alive", SHARED_WAVE_MAX_ALIVE_DEFAULT)
            .coerceAtLeast(1)
        mobTokenDropChance = config
            .getDouble("arena.mob_token_drop_chance", MOB_TOKEN_DROP_CHANCE)
            .coerceIn(0.0, 1.0)
        mobTokenLootingBonusPerLevel = config
            .getDouble("arena.mob_token_looting_bonus_per_level", MOB_TOKEN_LOOTING_BONUS_PER_LEVEL_DEFAULT)
            .coerceAtLeast(0.0)
        finalWaveTimeLimitSeconds = config
            .getInt("arena.final_wave_time_limit_seconds", FINAL_WAVE_TIME_LIMIT_SECONDS_DEFAULT)
            .coerceAtLeast(1)
        maxConcurrentSessions = config
            .getInt("arena.session.max_concurrent", ARENA_MAX_CONCURRENT_SESSIONS_DEFAULT)
            .coerceAtLeast(1)
        arenaPoolSize = config
            .getInt("arena.world_pool.size", ARENA_POOL_SIZE_DEFAULT)
            .coerceAtLeast(maxConcurrentSessions)
        cleanupBlocksPerTick = config
            .getInt("arena.world_pool.cleanup_blocks_per_tick", ARENA_CLEANUP_BLOCKS_PER_TICK_DEFAULT)
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
        val switchIntervalBeats = config.getInt("$path.switch_interval_beats", defaultSwitchIntervalBeats())
            .coerceAtLeast(1)

        return ArenaBgmTrackConfig(
            soundKey = soundKey,
            bpm = bpm,
            loopBeats = loopBeats,
            switchIntervalBeats = switchIntervalBeats
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
            val reviveMaxPerPlayerRaw = section.getInt("revive_max_per_player", -1)
            val reviveMaxPerPlayer = if (reviveMaxPerPlayerRaw <= 0) Int.MAX_VALUE else reviveMaxPerPlayerRaw
            val reviveTimeLimitSeconds = section.getInt("revive_time_limit_seconds", 0).coerceAtLeast(0)
            val mobStatsMaxHealth = section.getDouble("mob_stats_max_health", 20.0).coerceAtLeast(1.0)
            val mobStatsMaxAttack = section.getDouble("mob_stats_max_attack", 1.0).coerceAtLeast(0.0)
            val mobStatsMaxMovementSpeed = section.getDouble("mob_stats_max_movement_speed", 0.23).coerceAtLeast(0.01)
            val mobStatsMaxArmor = section.getDouble("mob_stats_max_armor", 0.0).coerceAtLeast(0.0)

            loaded[difficultyId] = ArenaDifficultyConfig(
                id = difficultyId,
                difficultyRange = difficultyRange,
                mobSpawnIntervalTicks = mobSpawnIntervalTicks,
                mobCountMultiplier = mobCountMultiplier,
                waveMultiplier = waveMultiplier,
                waves = waves,
                display = display,
                reviveMaxPerPlayer = reviveMaxPerPlayer,
                reviveTimeLimitSeconds = reviveTimeLimitSeconds,
                mobStatsMaxHealth = mobStatsMaxHealth,
                mobStatsMaxAttack = mobStatsMaxAttack,
                mobStatsMaxMovementSpeed = mobStatsMaxMovementSpeed,
                mobStatsMaxArmor = mobStatsMaxArmor
            )
        }

        difficultyConfigs.clear()
        difficultyConfigs.putAll(loaded)

        if (difficultyConfigs.isEmpty()) {
            plugin.logger.severe("[Arena] difficulty.yml が空のためアリーナを開始できません")
        }
    }

    fun setQuestService(questService: ArenaQuestService?) {
        arenaQuestService = questService
    }

    private fun loadMobDefinitions() {
        val loaded = mobService.reloadDefinitions()
        mobDefinitions.clear()
        mobDefinitions.putAll(loaded)
        knownMobTypeIds.clear()
        knownMobTypeIds.addAll(loaded.values.map { it.typeId.trim().lowercase(Locale.ROOT) })
        val tokenTypeIds = registerMobTypeTokenItems()
        validateMobTokenLanguageKeys(tokenTypeIds)

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
            val (minAmount, maxAmount) = parseAmountRange(entry["amount"]?.toString()?.trim())
            val chance = entry["chance"]?.toString()?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0
            entries.add(ArenaDropEntry(itemId = itemId, minAmount = minAmount, maxAmount = maxAmount, chance = chance))
        }
        return entries
    }

    private fun parseAmountRange(text: String?): Pair<Int, Int> {
        if (text.isNullOrBlank()) return 1 to 1
        val rangeMatch = Regex("^(\\d+)\\s*-\\s*(\\d+)$").matchEntire(text)
        if (rangeMatch != null) {
            val min = rangeMatch.groupValues[1].toIntOrNull()?.coerceAtLeast(0) ?: 0
            val max = rangeMatch.groupValues[2].toIntOrNull()?.coerceAtLeast(min) ?: min
            return min to max
        }
        val fixed = text.toIntOrNull()?.coerceAtLeast(0) ?: 1
        return fixed to fixed
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
        initialParticipants: List<Player> = emptyList(),
        questModifiers: ArenaQuestModifiers = ArenaQuestModifiers.NONE,
        difficultyScore: Double? = null,
        enableMultiplayerJoin: Boolean = false,
        inviteQuestTitle: String? = null,
        inviteQuestLore: List<String> = emptyList(),
        maxParticipants: Int = MULTIPLAYER_MAX_PARTICIPANTS_DEFAULT,
        showSessionStartedMessage: Boolean = true,
        onCompleted: ((Long) -> Unit)? = null
    ): ArenaStartResult {
        val startedAtNanos = System.nanoTime()
        fun completed(result: ArenaStartResult): ArenaStartResult {
            onCompleted?.invoke(elapsedMillisSince(startedAtNanos))
            return result
        }

        val participantPlayers = (listOf(target) + initialParticipants)
            .distinctBy { it.uniqueId }

        val alreadyInSession = participantPlayers.firstOrNull { playerToSessionWorld.containsKey(it.uniqueId) }
        if (alreadyInSession != null) {
            return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.already_in_session",
                "&c{player} はすでにアリーナセッション中です",
                arrayOf("player" to alreadyInSession.name)
            ))
        }

        if (sessionsByWorld.size >= maxConcurrentSessions) {
            return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.concurrent_limit_reached",
                "&c同時セッション上限に達しています ({current}/{max})",
                arrayOf("current" to sessionsByWorld.size, "max" to maxConcurrentSessions)
            ))
        }

        val difficulty = difficultyConfigs[difficultyId]
            ?: return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.difficulty_not_found",
                "&cdifficulty が見つかりません: {difficulty}",
                arrayOf("difficulty" to difficultyId)
            ))

        val theme = if (requestedTheme.isNullOrBlank()) {
            themeLoader.getRandomTheme(random)
        } else {
            themeLoader.getTheme(requestedTheme)
        } ?: return completed(ArenaStartResult.Error(
            "arena.messages.command.start_error.theme_not_found",
            "&c有効なテーマが見つかりません"
        ))

        val undefinedWave = (1..difficulty.waves).firstOrNull { wave ->
            selectSpawnCandidates(theme, wave).isEmpty()
        }
        if (undefinedWave != null) {
            plugin.logger.severe(
                "[Arena] セッション開始失敗: themeスポーン未定義ウェーブがあります " +
                    "theme=${theme.id} difficulty=$difficultyId wave=$undefinedWave"
            )
            return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.undefined_wave_spawn",
                "&ctheme '{theme}' は wave {wave} のスポーンが未定義です",
                arrayOf("theme" to theme.id, "mob_type" to theme.id, "wave" to undefinedWave)
            ))
        }

        val sanitizedMaxParticipants = maxParticipants.coerceIn(1, MULTIPLAYER_MAX_PARTICIPANTS_DEFAULT)
        if (participantPlayers.size > sanitizedMaxParticipants) {
            return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.too_many_participants",
                "&c参加人数が上限を超えています ({count}/{max})",
                arrayOf("count" to participantPlayers.size, "max" to sanitizedMaxParticipants)
            ))
        }

        val liftMarkers = if (enableMultiplayerJoin) {
            findNearbyLiftMarkers(target.location, multiplayerJoinMarkerSearchRadius)
        } else {
            emptyList()
        }
        if (enableMultiplayerJoin && !isEntranceLiftReady(liftMarkers)) {
            return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.lift_not_ready",
                "&cリフトの準備ができていないため開始できません"
            ))
        }

        if (enableMultiplayerJoin && liftMarkers.any { liftOccupiedMarkerKeys.contains(liftMarkerKey(it)) }) {
            liftOccupiedWaiters.add(target.uniqueId)
            return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.lift_occupied",
                "&cリフトが使用中のため開始できません"
            ))
        }

        val lobbyMarkers = findLoadedLobbyMarkers(target.world)
        if (lobbyMarkers.isEmpty()) {
            return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.lobby_marker_not_found",
                "&cロビーマーカーが見つからないため開始できません"
            ))
        }

        val world = acquireArenaPoolWorld() ?: return completed(ArenaStartResult.Error(
            "arena.messages.command.start_error.pool_world_unavailable",
            "&cアリーナワールド準備中のため開始できません"
        ))

        val returnLocations = participantPlayers.associate { it.uniqueId to it.location.clone() }.toMutableMap()
        val originalGameModes = participantPlayers.associate { it.uniqueId to it.gameMode }.toMutableMap()
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
            participants = participantPlayers.mapTo(mutableSetOf()) { it.uniqueId },
            returnLocations = returnLocations,
            originalGameModes = originalGameModes,
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
            joinAreaMarkerLocations = mutableListOf(),
            liftMarkerLocations = liftMarkers.map { it.clone() }.toMutableList(),
            lobbyMarkerLocations = lobbyMarkers.map { it.clone() }.toMutableList(),
            participantSpawnProtectionUntilMillis = if (enableMultiplayerJoin) Long.MAX_VALUE else now + 4000L,
            multiplayerJoinEnabled = enableMultiplayerJoin,
            phase = if (enableMultiplayerJoin) ArenaPhase.RECRUITING else ArenaPhase.PREPARING,
            joinGraceStartMillis = if (enableMultiplayerJoin) now else 0L,
            joinGraceDurationMillis = if (enableMultiplayerJoin) multiplayerJoinGraceSeconds * 1000L else 0L,
            joinGraceEndMillis = if (enableMultiplayerJoin) now + (multiplayerJoinGraceSeconds * 1000L) else 0L,
            inviteQuestTitle = inviteQuestTitle,
            inviteQuestLore = inviteQuestLore,
            stageGenerationCompleted = !enableMultiplayerJoin,
            reviveMaxPerPlayer = difficulty.reviveMaxPerPlayer,
            reviveTimeLimitSeconds = difficulty.reviveTimeLimitSeconds,
            sidebarParticipantOrder = participantPlayers.map { it.uniqueId }.toMutableList(),
            sidebarParticipantNames = participantPlayers.associate { it.uniqueId to it.name }.toMutableMap()
        )

        sessionsByWorld[world.name] = session
        participantPlayers.forEach { participant ->
            playerToSessionWorld[participant.uniqueId] = world.name
        }
        if (enableMultiplayerJoin) {
            liftMarkers.forEach { liftOccupiedMarkerKeys.add(liftMarkerKey(it)) }
        }
        logArenaPoolState("セッション開始", world.name)

        return try {
            if (enableMultiplayerJoin) {
                target.showBossBar(getOrCreateJoinCountdownBossBar(session, target.uniqueId))
                target.sendMessage(
                    ArenaI18n.text(
                        target,
                        "arena.messages.multiplayer.invite_window_started",
                        "§6クエストを受注しました！§7リフトを準備中です..."
                    )
                )
                target.sendMessage(
                    ArenaI18n.text(
                        target,
                        "arena.messages.multiplayer.invite_window_hint",
                        "§7左クリックでほかのプレイヤーをクエストに誘うことができます"
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
                            onCompleted?.invoke(elapsedMillisSince(startedAtNanos))
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
                            onCompleted?.invoke(elapsedMillisSince(startedAtNanos))
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
                    participant.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, STAGE_TRANSFER_BLINDNESS_TICKS, 0, false, false, false))
                    applySessionGameMode(session, participant)
                    playStageEntrySoundsLater(participant)
                }
                setDoorActionMarkersReadySilently(session, 1)
                broadcastStageStartMessage(session)
                initializeWavePipeline(session, theme, difficulty)
                updateSessionProgressBossBar(session)
                onCompleted?.invoke(elapsedMillisSince(startedAtNanos))
            }
            if (showSessionStartedMessage) {
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
            }
            ArenaStartResult.Success(theme.id, difficulty.waves, difficulty.id, difficulty.display)
        } catch (e: Exception) {
            if (e is ArenaStageBuildException) {
                plugin.logger.severe("[Arena] ステージの生成に失敗しました: ${e.message}")
                e.printStackTrace()
            }
            val failedSession = sessionsByWorld[world.name]
            if (failedSession != null) {
                terminateSession(failedSession, false)
            } else {
                markArenaWorldBroken(world.name)
            }
            onCompleted?.invoke(elapsedMillisSince(startedAtNanos))
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

    fun stopSessionToLobby(player: Player, reason: String = ArenaI18n.text(player, "arena.messages.session.ended", "&cアリーナセッションが終了しました")): Boolean {
        val session = getSession(player)
        val destination = if (session != null) resolveSessionLobbyLocation(session) else null
        return leavePlayerFromSession(player.uniqueId, reason, destination)
    }

    fun stopSessionById(playerId: UUID, reason: String? = null): Boolean {
        val player = Bukkit.getPlayer(playerId)
        val localizedReason = reason ?: ArenaI18n.text(player, "arena.messages.session.ended", "&cアリーナセッションが終了しました")
        return leavePlayerFromSession(playerId, localizedReason)
    }

    fun stopSessionToLobbyById(playerId: UUID, reason: String? = null): Boolean {
        val player = Bukkit.getPlayer(playerId)
        val localizedReason = reason ?: ArenaI18n.text(player, "arena.messages.session.ended", "&cアリーナセッションが終了しました")
        val worldName = playerToSessionWorld[playerId]
        val session = worldName?.let { sessionsByWorld[it] }
        val destination = if (session != null) resolveSessionLobbyLocation(session) else null
        return leavePlayerFromSession(playerId, localizedReason, destination)
    }

    fun notifyParticipantDeath(player: Player) {
        val session = getSession(player) ?: return
        session.participants
            .asSequence()
            .filter { it != player.uniqueId }
            .mapNotNull { Bukkit.getPlayer(it) }
            .filter { it.isOnline && it.world.name == session.worldName }
            .forEach { participant ->
                participant.sendMessage(ArenaI18n.text(participant, "arena.messages.down.participant_died", "§4{player}さんが倒れました... §7（おあげちゃんが救出に向かいます）", "player" to player.name))
                if (!hasOtherAliveNonDownParticipant(session, participant.uniqueId)) {
                    return@forEach
                }
                scheduleOageMessage(
                    participant,
                    OAGE_FOLLOWUP_DELAY_TICKS,
                    "arena.messages.oage.participant_died_followup",
                    "いま助けに行きますー！",
                    force = true
                )
            }
    }

    fun shutdown() {
        shuttingDown = true
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
                fallbackMessage = "&cサーバー停止によりアリーナを終了しました",
                duringShutdown = true
            )
        }
        sessionsByWorld.clear()
        playerToSessionWorld.clear()
        mobToSessionWorld.clear()
        mobToDefinitionTypeId.clear()
        cleanupWorldJobs.clear()
        readyArenaWorldNames.clear()
        confusionManager.clearAll()
        arenaWorldStates.clear()
        clearAllArenaSidebars()
        invitedPlayerLocks.clear()
        inviteSwingCooldownUntilTick.clear()
        liftOccupiedMarkerKeys.clear()
        liftOccupiedWaiters.clear()
        processPendingWorldDeletions()
    }

    fun getSession(player: Player): ArenaSession? {
        val worldName = playerToSessionWorld[player.uniqueId] ?: return null
        return sessionsByWorld[worldName]
    }

    fun getThemeIds(): Set<String> = themeLoader.getThemeIds()

    fun getTheme(themeId: String): ArenaTheme? = themeLoader.getTheme(themeId)

    fun getActiveSessionPlayerNames(): Set<String> {
        return playerToSessionWorld.keys.mapNotNull { uuid -> Bukkit.getPlayer(uuid)?.name }.toSet()
    }

    fun getActiveSessions(): List<ArenaSession> {
        return sessionsByWorld.values.toList()
    }

    fun resolveParticipantStatus(session: ArenaSession, playerId: UUID): String {
        return resolveSidebarParticipantStatus(session, playerId)
    }

    fun getLiftStatusForSession(session: ArenaSession): ArenaLiftStatus {
        val hasLiftMarkers = session.liftMarkerLocations.isNotEmpty()
        val anyOccupied = session.liftMarkerLocations.any { loc -> liftOccupiedMarkerKeys.contains(liftMarkerKey(loc)) }
        return when {
            !hasLiftMarkers || !isEntranceLiftReady(session.liftMarkerLocations) -> ArenaLiftStatus.UNAVAILABLE
            anyOccupied -> ArenaLiftStatus.OCCUPIED
            else -> ArenaLiftStatus.READY
        }
    }

    fun isPlayerInvitedToSession(playerId: UUID): Boolean {
        return invitedPlayerLocks.containsKey(playerId)
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
        removeArenaSidebar(ownerId)

        session.invitedParticipants.toList().forEach { invitedId ->
            hideJoinCountdownBossBar(session, invitedId)
            releaseInvitedPlayerLock(invitedId)
            removeArenaSidebar(invitedId)
        }

        session.waitingNotifiedParticipants.clear()
        session.waitingParticipants.clear()
        session.waitingSubtitleNextTickByPlayer.clear()
        session.invitedParticipants.clear()
    }

    private fun removeInvitedParticipant(session: ArenaSession, invitedId: UUID) {
        session.invitedParticipants.remove(invitedId)
        session.waitingParticipants.remove(invitedId)
        session.waitingNotifiedParticipants.remove(invitedId)
        session.waitingSubtitleNextTickByPlayer.remove(invitedId)
        hideJoinCountdownBossBar(session, invitedId)
        releaseInvitedPlayerLock(invitedId)
        removeArenaSidebar(invitedId)
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
                applyFinalWaveOvertimeWither(session, player, now)

                if (session.barrierRestartCompleted) {
                    continue
                }

                val currentWave = session.currentWave.coerceAtLeast(1)
                if (location.world?.name != world.name || !session.stageBounds.contains(location.x, location.y, location.z)) {
                    teleportToLatestDoorMarker(player, session, world, currentWave)
                    continue
                }

                val room = locateRoom(session, location)
                if (room != null && room > currentWave) {
                    teleportToCurrentWavePosition(player, session, world, applyBlindness = true)
                    continue
                }
                if (session.entranceNormalBgmStarted && room != null && room > 0) {
                    processRoomProgress(session, player, room)
                }

                val corridorWave = locateCorridorTargetWave(session, location)
                if (corridorWave != null) {
                    if (!session.openedCorridors.contains(corridorWave)) {
                        teleportToCurrentWavePosition(player, session, world, applyBlindness = true)
                        continue
                    }
                    clearPreviousRoomMobTargetsOnCorridorEntry(session, corridorWave)
                    if (corridorWave == currentWave) {
                        if (session.startedWaves.contains(corridorWave) &&
                            (corridorWave <= 1 || session.clearedWaves.contains(corridorWave - 1)) &&
                            session.corridorTriggeredWaves.add(corridorWave)
                        ) {
                            rebalanceTargetsForWave(session, corridorWave)
                        }
                    }
                }

                processWaveCatchupIfNeeded(session, player, world, now)
                recordParticipantValidLocation(session, participantId, player.location)
            }
        }
    }

    private fun applyFinalWaveOvertimeWither(session: ArenaSession, player: Player, nowMillis: Long) {
        if (session.barrierRestartCompleted) return
        if (session.finalWaveStartedAtMillis <= 0L) return
        if (!session.startedWaves.contains(session.waves)) return
        if (player.world.name != session.worldName) return
        if (isPlayerDowned(session, player.uniqueId)) return

        val limitMillis = finalWaveTimeLimitSeconds.toLong().coerceAtLeast(1L) * 1000L
        val elapsedMillis = (nowMillis - session.finalWaveStartedAtMillis).coerceAtLeast(0L)
        if (elapsedMillis <= limitMillis) return

        val overtimeMillis = elapsedMillis - limitMillis
        val overtimeLevel = (overtimeMillis / 60_000L).toInt().coerceAtLeast(0) + 1
        val amplifier = (overtimeLevel - 1).coerceAtLeast(0)
        player.addPotionEffect(PotionEffect(PotionEffectType.WITHER, 40, amplifier, false, true, true))
    }

    private fun clearPreviousRoomMobTargetsOnCorridorEntry(session: ArenaSession, corridorWave: Int) {
        val previousWave = corridorWave - 1
        if (previousWave <= 0) return
        if (!session.clearedWaves.contains(previousWave)) return
        val previousRoomBounds = session.roomBounds[previousWave] ?: return

        session.waveMobIds[previousWave]
            .orEmpty()
            .asSequence()
            .mapNotNull { Bukkit.getEntity(it) as? Mob }
            .filter { it.isValid && !it.isDead }
            .filter { mob ->
                val mobLocation = mob.location
                mobLocation.world?.name == session.worldName &&
                    previousRoomBounds.contains(mobLocation.x, mobLocation.y, mobLocation.z)
            }
            .forEach { mob ->
                val target = mob.target as? Player
                if (target != null && session.participants.contains(target.uniqueId)) {
                    mob.target = null
                }
            }
    }

    fun handleMobDeath(event: EntityDeathEvent) {
        val entityId = event.entity.uniqueId
        try {
            applyArenaMobDrop(event)
        } catch (exception: Exception) {
            plugin.logger.log(Level.SEVERE, "[Arena] モブドロップ処理に失敗しましたが討伐進行は継続します: entityId=$entityId", exception)
        }
        val worldName = mobToSessionWorld.remove(entityId) ?: return
        val session = sessionsByWorld[worldName] ?: return
        session.barrierDefenseMobIds.remove(entityId)
        session.barrierDefenseTargetMobIds.remove(entityId)
        session.barrierDefenseAssaultMobIds.remove(entityId)
        val killer = event.entity.killer
        val trackedParticipantDamagers = session.mobDamagedParticipants.remove(entityId)
            .orEmpty()
            .filter { session.participants.contains(it) }
            .toSet()
        val killerParticipantId = killer?.uniqueId?.takeIf { session.participants.contains(it) }
        val killAwardParticipantIds = when {
            killerParticipantId == null -> trackedParticipantDamagers
            trackedParticipantDamagers.isEmpty() -> setOf(killerParticipantId)
            else -> trackedParticipantDamagers + killerParticipantId
        }
        val countKill = killAwardParticipantIds.isNotEmpty()
        if (countKill) {
            killAwardParticipantIds.forEach { participantId ->
                arenaQuestService?.recordMobKill(participantId)
            }
        }
        consumeMob(session, entityId, countKill = countKill)
    }

    fun registerChildMob(entity: LivingEntity, definitionTypeId: String, options: MobSpawnOptions) {
        if (options.featureId != "arena") return
        val worldName = entity.world.name
        val session = sessionsByWorld[worldName] ?: return
        val entityId = entity.uniqueId
        mobToSessionWorld[entityId] = worldName
        mobToDefinitionTypeId[entityId] = definitionTypeId
        session.activeMobs.add(entityId)

        val wave = options.metadata["wave"]?.toIntOrNull()
            ?: session.currentWave.takeIf { it > 0 }
            ?: session.startedWaves.maxOrNull()
        if (wave != null) {
            session.mobWaveMap[entityId] = wave
            session.waveMobIds.getOrPut(wave) { mutableSetOf() }.add(entityId)
        }
    }

    fun handleParticipantDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val session = getSession(player) ?: return
        if (!session.participants.contains(player.uniqueId)) return

        if (isPlayerDowned(session, player.uniqueId)) {
            event.isCancelled = true
            return
        }

        val finalHealth = player.health - event.finalDamage
        if (finalHealth > 0.0) {
            return
        }

        event.isCancelled = true

        val otherAliveExists = hasOtherAliveNonDownParticipant(session, player.uniqueId)
        if (!isMultiplayerSession(session) ||
            !otherAliveExists ||
            !canBeRevived(session, player.uniqueId)
        ) {
            setPlayerDown(session, player, reviveDisabled = true)
            notifyParticipantDeath(player)
            handleInviteTargetUnavailable(player)
            if (isMultiplayerSession(session) && !otherAliveExists) {
                convertDownedPlayersToGameOver(session)
            }
            return
        }

        setPlayerDown(session, player)
    }

    fun handleElderGuardianCurse(event: EntityPotionEffectEvent) {
        val player = event.entity as? Player ?: return
        val session = getSession(player) ?: return
        if (!session.participants.contains(player.uniqueId)) return

        val effectType = event.modifiedType ?: event.newEffect?.type ?: return
        if (!isMiningFatigueType(effectType)) return
        if (event.cause == EntityPotionEffectEvent.Cause.PLUGIN) return

        event.isCancelled = true

        if (!isCurseApplyAction(event.action)) return
        val newEffect = event.newEffect ?: return
        if (newEffect.amplifier < ELDER_CURSE_AMPLIFIER) return

        val now = System.currentTimeMillis()
        val cooldownUntil = elderCurseCooldownUntilMillis[player.uniqueId] ?: 0L
        if (now < cooldownUntil) {
            return
        }
        elderCurseCooldownUntilMillis[player.uniqueId] = now + ELDER_CURSE_PLAYER_COOLDOWN_MILLIS

        player.damage(ELDER_CURSE_DAMAGE)
        player.addPotionEffect(
            PotionEffect(
                effectType,
                ELDER_CURSE_DURATION_TICKS,
                ELDER_CURSE_AMPLIFIER,
                false,
                true,
                true
            )
        )
    }

    fun handleDownedPlayerAttack(event: EntityDamageByEntityEvent) {
        val target = event.entity as? LivingEntity ?: return
        if (target is Player) return

        val attacker = resolveDamagerPlayer(event) ?: return
        val session = getSession(attacker) ?: return
        if (!isPlayerDowned(session, attacker.uniqueId)) {
            return
        }

        event.isCancelled = true
    }

    private fun isMiningFatigueType(effectType: PotionEffectType): Boolean {
        val key = effectType.key.key.uppercase(Locale.ROOT)
        return key == "MINING_FATIGUE" || key == "SLOW_DIGGING"
    }

    private fun isCurseApplyAction(action: EntityPotionEffectEvent.Action): Boolean {
        return action == EntityPotionEffectEvent.Action.ADDED ||
            action == EntityPotionEffectEvent.Action.CHANGED
    }

    fun handleParticipantFriendlyFire(event: EntityDamageByEntityEvent) {
        val damaged = event.entity as? Player ?: return
        val attacker = resolveDamagerPlayer(event) ?: return

        val damagedSession = getSession(damaged) ?: return
        val attackerSession = getSession(attacker) ?: return
        if (damagedSession.worldName != attackerSession.worldName) {
            return
        }
        if (!damagedSession.participants.contains(damaged.uniqueId)) {
            return
        }
        if (!attackerSession.participants.contains(attacker.uniqueId)) {
            return
        }

        if (event.damager is Player && attacker.uniqueId != damaged.uniqueId) {
            handleReviveTargetSelection(attackerSession, attacker, damaged)
        }

        event.isCancelled = true
    }

    fun handleArenaBlockBreak(event: BlockBreakEvent) {
        val worldName = event.block.world.name
        if (!sessionsByWorld.containsKey(worldName)) {
            return
        }

        if (event.block.type == Material.COBWEB) {
            PeriodicCobwebAbility.consumeArenaPlacedCobweb(event.block)
        }
    }

    fun handleArenaBlockPlace(event: BlockPlaceEvent) {
        val worldName = event.block.world.name
        if (!sessionsByWorld.containsKey(worldName)) {
            return
        }
        event.isCancelled = true
    }

    fun handleArenaInteract(event: PlayerInteractEvent) {
        val clicked = event.clickedBlock ?: return
        val worldName = clicked.world.name
        if (!sessionsByWorld.containsKey(worldName)) {
            return
        }
        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }
        if (!clicked.type.isInteractable) {
            return
        }
        event.isCancelled = true
        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DEFAULT)
    }

    fun handleArenaInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val session = getSession(player) ?: return
        if (!session.participants.contains(player.uniqueId)) return
        if (isPlayerDowned(session, player.uniqueId)) return

        val shulker = event.rightClicked as? Shulker ?: return
        val downedId = findDownedPlayerIdByShulker(session, shulker.uniqueId) ?: return
        val downed = Bukkit.getPlayer(downedId) ?: return
        if (!downed.isOnline || downed.world.uid != player.world.uid) return

        handleReviveTargetSelection(session, player, downed)
        event.isCancelled = true
    }

    fun handleArenaEntityMount(event: EntityMountEvent) {
        val player = event.entity as? Player ?: return
        val session = getSession(player) ?: return
        if (!session.participants.contains(player.uniqueId)) return

        val mount = event.mount
        val shulkerId = (mount as? Shulker)?.uniqueId
        val carrierId = (mount as? ArmorStand)?.uniqueId

        val downedId = shulkerId?.let { findDownedPlayerIdByShulker(session, it) }
            ?: carrierId?.let { findDownedPlayerIdByCarrier(session, it) }
            ?: return
        if (downedId == player.uniqueId) return

        event.isCancelled = true
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (mount.passengers.contains(player)) {
                mount.removePassenger(player)
            }
            val base = mount.location.block.location.add(1.5, 0.0, 0.5)
            val destination = if (base.block.isPassable) base else base.clone().add(0.0, 1.0, 0.0)
            player.teleport(destination)
        })
    }

    private fun findDownedPlayerIdByShulker(session: ArenaSession, shulkerId: UUID): UUID? {
        return session.downedPlayers.entries
            .firstOrNull { it.value.shulkerEntityId == shulkerId }
            ?.key
    }

    private fun findDownedPlayerIdByCarrier(session: ArenaSession, carrierId: UUID): UUID? {
        return session.downedPlayers.entries
            .firstOrNull { it.value.carrierEntityId == carrierId }
            ?.key
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
        event.isCancelled = true
    }

    fun handleMobDamagedByParticipant(event: EntityDamageByEntityEvent) {
        val damaged = event.entity as? LivingEntity ?: return
        if (damaged is Player) return

        val worldName = mobToSessionWorld[damaged.uniqueId] ?: return
        val session = sessionsByWorld[worldName] ?: return
        val attacker = resolveDamagerPlayer(event) ?: return
        if (!session.participants.contains(attacker.uniqueId)) return
        if (attacker.world.name != session.worldName) return

        session.mobDamagedParticipants
            .getOrPut(damaged.uniqueId) { mutableSetOf() }
            .add(attacker.uniqueId)
    }

    fun handleMultiplayerInviteSwing(event: PlayerAnimationEvent) {
        if (event.animationType != PlayerAnimationType.ARM_SWING) {
            return
        }

        val player = event.player
        val session = getSession(player)
        if (session != null && trySelectReviveTargetBySwing(session, player)) {
            return
        }

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
            0.0
        ) { candidate ->
            candidate is Player && candidate.uniqueId != source.uniqueId
        }
        return hit?.hitEntity as? Player
    }

    private fun trySelectReviveTargetBySwing(session: ArenaSession, player: Player): Boolean {
        if (!session.participants.contains(player.uniqueId)) {
            return false
        }
        if (isPlayerDowned(session, player.uniqueId)) {
            return false
        }

        val target = resolveReviveTargetByRayTrace(session, player) ?: return false

        handleReviveTargetSelection(session, player, target)
        return true
    }

    private fun resolveReviveTargetByRayTrace(session: ArenaSession, source: Player): Player? {
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
            when (candidate) {
                is Player -> candidate.uniqueId != source.uniqueId
                is Shulker -> true
                else -> false
            }
        }
        val entity = hit?.hitEntity ?: return null
        return when (entity) {
            is Player -> {
                if (!session.participants.contains(entity.uniqueId)) return null
                if (!isPlayerDowned(session, entity.uniqueId)) return null
                entity
            }
            is Shulker -> {
                val downedId = findDownedPlayerIdByShulker(session, entity.uniqueId) ?: return null
                val downed = Bukkit.getPlayer(downedId) ?: return null
                if (!downed.isOnline) return null
                downed
            }
            else -> null
        }
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
        session.sidebarParticipantNames.putIfAbsent(invited.uniqueId, invited.name)
        invitedPlayerLocks[invited.uniqueId] = session.worldName
        invited.isGlowing = true
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
                ArenaI18n.text(null, "arena.messages.multiplayer.invite_message_simple", "§8§l«§c§lArena§8§l» §7{owner}さんがあなたをアリーナに招待しました！", "owner" to ownerName)
            )
        }

        val hoverText = if (session.inviteQuestLore.isEmpty()) {
            ArenaI18n.text(null, "arena.messages.multiplayer.no_description", "§7説明はありません")
        } else {
            session.inviteQuestLore.joinToString("\n")
        }

        val prefix = legacySerializer.deserialize(ArenaI18n.text(null, "arena.messages.multiplayer.invite_message_prefix", "§8§l«§c§lArena§8§l» §7{owner}さんがあなたをクエスト§e", "owner" to ownerName))
        val questPart = legacySerializer.deserialize(ArenaI18n.text(null, "arena.messages.multiplayer.invite_message_quest", "§e【{quest}】", "quest" to questTitle))
            .hoverEvent(HoverEvent.showText(legacySerializer.deserialize(hoverText)))
        val suffix = legacySerializer.deserialize(ArenaI18n.text(null, "arena.messages.multiplayer.invite_message_suffix", "§7に招待しました！"))

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
        if (!session.participants.contains(targetPlayer.uniqueId) || isPlayerDowned(session, targetPlayer.uniqueId)) {
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

    private fun resolveDamagerPlayer(event: EntityDamageByEntityEvent): Player? {
        val damager = event.damager
        if (damager is Player) {
            return damager
        }
        if (damager is Projectile) {
            return damager.shooter as? Player
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
                    player.gameMode != org.bukkit.GameMode.SPECTATOR &&
                    !isPlayerDowned(session, player.uniqueId)
            }
            .minByOrNull { player ->
                player.location.distanceSquared(origin)
            }
    }

    private fun isMultiplayerSession(session: ArenaSession): Boolean {
        return session.participants.size > 1
    }

    private fun isPlayerDowned(session: ArenaSession, playerId: UUID): Boolean {
        return session.downedPlayers.containsKey(playerId)
    }

    private fun reviveCount(session: ArenaSession, playerId: UUID): Int {
        return session.reviveCountByPlayer[playerId] ?: 0
    }

    private fun canBeRevived(session: ArenaSession, playerId: UUID): Boolean {
        val maxCount = session.reviveMaxPerPlayer
        if (maxCount == Int.MAX_VALUE) {
            return true
        }
        return reviveCount(session, playerId) < maxCount
    }

    private fun transitionSessionPhase(session: ArenaSession, phase: ArenaPhase) {
        if (session.phase == phase) {
            return
        }
        session.phase = phase
    }

    private fun transitionToGameOver(session: ArenaSession) {
        if (session.phase == ArenaPhase.GAME_OVER || session.phase == ArenaPhase.TERMINATING) {
            return
        }

        transitionSessionPhase(session, ArenaPhase.GAME_OVER)
        updateArenaSidebars()
    }

    private fun hasOtherAliveNonDownParticipant(session: ArenaSession, excludedPlayerId: UUID): Boolean {
        val world = Bukkit.getWorld(session.worldName) ?: return false
        return session.participants
            .asSequence()
            .filter { it != excludedPlayerId }
            .filter { !session.downedPlayers.containsKey(it) }
            .mapNotNull { Bukkit.getPlayer(it) }
            .any { participant ->
                participant.isOnline &&
                    !participant.isDead &&
                    participant.world.uid == world.uid
            }
    }

    private fun setPlayerDown(session: ArenaSession, player: Player, reviveDisabled: Boolean = false) {
        if (session.downedPlayers.containsKey(player.uniqueId)) {
            return
        }

        val now = System.currentTimeMillis()
        val reviveDeadline = if (reviveDisabled) {
            now
        } else if (session.reviveTimeLimitSeconds > 0) {
            now + session.reviveTimeLimitSeconds * 1000L
        } else {
            Long.MAX_VALUE
        }
        session.downedPlayers[player.uniqueId] = ArenaDownedPlayerState(
            downedAtMillis = now,
            bleedoutAtMillis = reviveDeadline,
            reviveDisabled = reviveDisabled,
            gameOverLocation = if (reviveDisabled) player.location.clone() else null
        )
        session.reviveHoldStates[player.uniqueId] = ArenaReviveHoldState()
        session.downedOriginalWalkSpeeds.putIfAbsent(player.uniqueId, player.walkSpeed)
        player.getAttribute(Attribute.JUMP_STRENGTH)?.let { attr ->
            session.downedOriginalJumpStrengths.putIfAbsent(player.uniqueId, attr.baseValue)
            attr.baseValue = 0.0
        }

        player.health = 1.0
        player.fireTicks = 0
        player.fallDistance = 0.0f
        player.walkSpeed = if (reviveDisabled) DOWNED_GAME_OVER_WALK_SPEED else DOWNED_PLAYER_WALK_SPEED
        player.velocity = Vector(0.0, 0.0, 0.0)

        if (!player.isOnGround) {
            val loc = player.location
            val world = loc.world ?: return
            val bounds = session.roomBounds.entries.firstOrNull { (_, b) -> b.contains(loc.x, loc.y, loc.z) }?.value
            val maxSearch = (bounds?.let { it.maxY - it.minY } ?: 30).coerceIn(5, 64)
            val halfW = 0.3
            val offsets = listOf(-halfW, 0.0, halfW).flatMap { ox ->
                listOf(-halfW, 0.0, halfW).map { oz -> ox to oz }
            }
            var bestY: Double? = null
            var hitBlockY: Int? = null
            for ((ox, oz) in offsets) {
                val start = Location(world, loc.x + ox, loc.y, loc.z + oz, loc.yaw, loc.pitch)
                val hit = world.rayTraceBlocks(start, Vector(0.0, -1.0, 0.0), maxSearch.toDouble(), FluidCollisionMode.NEVER, true)
                val nonNullHit = hit ?: continue
                val y = nonNullHit.hitPosition.y
                if (bestY == null || y > bestY) {
                    bestY = y
                    hitBlockY = nonNullHit.hitBlock?.y
                }
            }
            if (bestY != null && hitBlockY != null) {
                val belowBlock = world.getBlockAt(loc.blockX, hitBlockY - 1, loc.blockZ)
                val belowTopY = belowBlock.collisionShape.boundingBoxes
                    .map { it.maxY + belowBlock.y }
                    .maxOrNull()
                if (belowTopY != null && belowTopY > bestY) {
                    bestY = belowTopY
                }
                player.teleport(Location(world, loc.x, bestY, loc.z, loc.yaw, loc.pitch))
            } else {
                val latestWave = latestEnteredWave(session) ?: 1
                teleportToNearestDoorMarkerForWave(player, session, world, latestWave.coerceIn(1, session.waves))
            }
        }

        player.playSound(player.location, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.75f)

        syncDownedShulker(session, player, forceTeleport = true)
        retargetMobsFromDownedPlayers(session)

        if (!reviveDisabled) {
            player.sendMessage(
                ArenaI18n.text(
                    player,
                    "arena.messages.down.entered",
                    "&cダウンしました。生存者の蘇生を待ってください"
                )
            )

            session.participants
                .asSequence()
                .filter { it != player.uniqueId }
                .mapNotNull { Bukkit.getPlayer(it) }
                .filter { it.isOnline && it.world.name == session.worldName }
                .forEach { participant ->
                    participant.sendMessage(ArenaI18n.text(participant, "arena.messages.down.needs_revive", "§c{player}さんがダウンしました！蘇生が必要です！", "player" to player.name))
                }
        }

        if (reviveDisabled && hasOtherAliveNonDownParticipant(session, player.uniqueId)) {
            val oageMessage = ArenaI18n.stringList(
                player,
                "arena.messages.oage.down_with_survivors",
                listOf("お疲れさま！他のみなさんの様子はここからでも確認できますよ！")
            ).randomOrNull() ?: "お疲れさま！他のみなさんの様子はここからでも確認できますよ！"
            sendOageMessage(
                player,
                "arena.messages.oage.down_with_survivors",
                oageMessage,
                force = true
            )
        }
    }

    private fun convertDownedPlayersToGameOver(session: ArenaSession) {
        val now = System.currentTimeMillis()
        transitionToGameOver(session)
        for (downedId in session.downedPlayers.keys.toList()) {
            val downState = session.downedPlayers[downedId] ?: continue
            if (downState.timeoutExecuteAtMillis != null) continue

            val downed = Bukkit.getPlayer(downedId)
            if (downed == null || !downed.isOnline || downed.world.name != session.worldName) {
                continue
            }

            downState.reviveDisabled = true
            downState.bleedoutAtMillis = now
            downState.timeoutExecuteAtMillis = null
            downState.gameOverLocation = downed.location.clone()

            syncDownedShulker(session, downed, forceTeleport = true)

            downed.walkSpeed = DOWNED_GAME_OVER_WALK_SPEED
            stopArenaBgmForPlayer(downed)
            downed.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, Int.MAX_VALUE, 0, false, false, false))
            downed.playSound(downed.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.75f)
            downed.sendMessage(
                ArenaI18n.text(
                    downed,
                    "arena.messages.down.game_over",
                    "&c目の前がまっくらになった...！\n&7おあげちゃんが救出中..."
                )
            )
            scheduleOageMessage(
                downed,
                OAGE_FOLLOWUP_DELAY_TICKS,
                "arena.messages.oage.game_over_followup",
                "いま行きます！ちょっと待ってね",
                force = true
            )
            clearReviveBindingForDowned(session, downedId, playInterruptedSound = false)

            downState.timeoutExecuteAtMillis = now + DOWN_GAME_OVER_RETURN_DELAY_MILLIS
        }
    }

    private fun recoverDownedPlayer(session: ArenaSession, downed: Player, revivedBy: Player?) {
        clearDownedState(session, downed.uniqueId, playInterruptedSound = false)
        session.reviveCountByPlayer[downed.uniqueId] = reviveCount(session, downed.uniqueId) + 1

        val maxHealth = downed.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        val targetHealth = (maxHealth * 0.5).coerceAtLeast(1.0)
        downed.health = targetHealth.coerceAtMost(maxHealth)
        downed.noDamageTicks = 20

        downed.sendMessage(
            ArenaI18n.text(
                downed,
                "arena.messages.down.recovered",
                "&a蘇生されました！"
            )
        )

        if (revivedBy != null && revivedBy.uniqueId != downed.uniqueId) {
            downed.playSound(downed.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
            revivedBy.playSound(revivedBy.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
            revivedBy.sendMessage(
                ArenaI18n.text(
                    revivedBy,
                    "arena.messages.down.revive_success",
                    "&a{player} を蘇生しました",
                    "player" to downed.name
                )
            )
        }
    }

    private fun updateDownedPlayers(currentTick: Long) {
        for (session in sessionsByWorld.values.toList()) {
            if (session.downedPlayers.isEmpty()) {
                continue
            }

            retargetMobsFromDownedPlayers(session)

            val shouldFollowShulker = currentTick % downShulkerFollowIntervalTicks == 0L

            for (downedId in session.downedPlayers.keys.toList()) {
                val downed = Bukkit.getPlayer(downedId)
                if (downed == null || !downed.isOnline || downed.world.name != session.worldName) {
                    clearDownedState(session, downedId)
                    continue
                }

                val downState = session.downedPlayers[downedId]
                applyDownedMovementLimit(downed, downState)
                if (shouldFollowShulker) {
                    syncDownedShulker(session, downed, forceTeleport = false)
                }
                if (downState != null && downState.bleedoutAtMillis != Long.MAX_VALUE && System.currentTimeMillis() >= downState.bleedoutAtMillis) {
                    val now = System.currentTimeMillis()
                    val timeoutExecuteAt = downState.timeoutExecuteAtMillis
                    if (timeoutExecuteAt == null) {
                        downed.playSound(downed.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.75f)
                        val blindnessTicks = if (downState.reviveDisabled) {
                            Int.MAX_VALUE
                        } else {
                            40
                        }
                        val executeDelayMillis = if (downState.reviveDisabled) {
                            DOWN_GAME_OVER_RETURN_DELAY_MILLIS
                        } else {
                            1000L
                        }
                        if (downState.reviveDisabled) {
                            transitionToGameOver(session)
                            stopArenaBgmForPlayer(downed)
                        }
                        downed.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, blindnessTicks, 0, false, false, false))
                        if (downState.reviveDisabled) {
                            downed.sendMessage(
                                ArenaI18n.text(
                                    downed,
                                    "arena.messages.down.game_over",
                                    "&c目の前がまっくらになった...！\n&7おあげちゃんが救出中..."
                                )
                            )
                            scheduleOageMessage(
                                downed,
                                OAGE_FOLLOWUP_DELAY_TICKS,
                                "arena.messages.oage.game_over_followup",
                                "いま行きます！ちょっと待ってね",
                                force = true
                            )
                        }
                        clearReviveBindingForDowned(session, downedId, playInterruptedSound = false)
                        downState.timeoutExecuteAtMillis = now + executeDelayMillis
                    } else if (now >= timeoutExecuteAt) {
                        if (downState.reviveDisabled) {
                            val otherAliveExists = hasOtherAliveNonDownParticipant(session, downedId)
                            val oageKey = if (otherAliveExists) {
                                "arena.messages.oage.game_over_with_survivors"
                            } else {
                                "arena.messages.oage.game_over_all_wiped"
                            }
                            scheduleOageMessage(
                                downed,
                                40L,
                                oageKey,
                                if (otherAliveExists) "おつかれさま！他の方の様子はここからでも確認できます！" else "おつかれさま！間に合って良かったです！",
                                force = true
                            )
                            stopSessionToLobbyById(downedId, "")
                        } else {
                            stopSessionById(
                                downedId,
                                ArenaI18n.text(downed, "arena.messages.down.timeout", "&cダウン時間切れです。ロビーへ転送されます")
                            )
                        }
                    }
                    continue
                }
            }

            updateReviveHoldStates(session)
        }
    }

    private fun updateReviveHoldStates(session: ArenaSession) {
        if (session.downedPlayers.isEmpty()) {
            session.reviveHoldStates.clear()
            session.reviveTargetByReviver.clear()
            session.reviveBossBarsByDowned.values.forEach { hideReviveBossBarFromAll(session, it) }
            session.reviveBossBarsByDowned.clear()
            return
        }

        val reviveRadiusSquared = downReviveRadius * downReviveRadius
        val requiredTicks = downReviveHoldSeconds * 20
        val currentTick = Bukkit.getCurrentTick().toLong()

        for (downedId in session.downedPlayers.keys.toList()) {
            val downed = Bukkit.getPlayer(downedId)
            if (downed == null || !downed.isOnline || downed.world.name != session.worldName) {
                clearReviveBindingForDowned(session, downedId)
                continue
            }

            val downState = session.downedPlayers[downedId]
            if (downState?.reviveDisabled == true) {
                clearReviveBindingForDowned(session, downedId, playInterruptedSound = false)
                continue
            }

            val holdState = session.reviveHoldStates.getOrPut(downedId) { ArenaReviveHoldState() }
            val reviverId = holdState.reviverPlayerId
            if (reviverId == null) {
                holdState.heldTicks = 0
                hideReviveBossBar(session, downedId)
                continue
            }

            if (holdState.heldTicks <= 0 && currentTick - holdState.linkedAtTick >= REVIVE_LINK_TIMEOUT_TICKS) {
                clearReviveBindingForDowned(session, downedId)
                continue
            }

            if (session.reviveTargetByReviver[reviverId] != downedId) {
                clearReviveBindingForDowned(session, downedId)
                continue
            }

            val reviver = Bukkit.getPlayer(reviverId)
            if (
                reviver == null ||
                !reviver.isOnline ||
                reviver.world.uid != downed.world.uid ||
                !session.participants.contains(reviverId) ||
                isPlayerDowned(session, reviverId)
            ) {
                clearReviveBindingForDowned(session, downedId)
                continue
            }

            val inRange = reviver.location.distanceSquared(downed.location) <= reviveRadiusSquared
            if (!inRange) {
                clearReviveBindingForDowned(session, downedId)
                continue
            }

            if (reviver.isSneaking && inRange) {
                holdState.heldTicks += 1
                holdState.lastProgressTick = currentTick
            } else {
                holdState.heldTicks = 0
            }

            val progress = (holdState.heldTicks.toFloat() / requiredTicks.toFloat()).coerceIn(0.0f, 1.0f)
            updateReviveBossBar(session, downed, reviver, progress)

            if (holdState.heldTicks < requiredTicks) {
                continue
            }

            recoverDownedPlayer(session, downed, reviver)
        }
    }

    private fun applyDownedMovementLimit(player: Player, downState: ArenaDownedPlayerState?) {
        if (downState?.reviveDisabled == true) {
            if (player.walkSpeed != DOWNED_GAME_OVER_WALK_SPEED) {
                player.walkSpeed = DOWNED_GAME_OVER_WALK_SPEED
            }
            val anchor = downState.gameOverLocation
            if (anchor != null) {
                val loc = player.location
                if (loc.world?.uid != anchor.world?.uid ||
                    loc.distanceSquared(anchor) > 0.01
                ) {
                    player.teleport(anchor)
                }
            }
            player.velocity = Vector(0.0, 0.0, 0.0)
            return
        }

        if (player.walkSpeed != DOWNED_PLAYER_WALK_SPEED) {
            player.walkSpeed = DOWNED_PLAYER_WALK_SPEED
        }

        if (!player.isInWater) {
            return
        }

        if (player.isSwimming) {
            player.isSwimming = false
        }

        val current = player.velocity
        if (current.x == 0.0 && current.z == 0.0) {
            return
        }
        player.velocity = Vector(0.0, current.y, 0.0)
    }

    private fun handleReviveTargetSelection(session: ArenaSession, reviver: Player, target: Player) {
        if (!session.participants.contains(reviver.uniqueId)) {
            return
        }
        if (!session.participants.contains(target.uniqueId)) {
            return
        }
        val targetDownState = session.downedPlayers[target.uniqueId] ?: return
        if (targetDownState.reviveDisabled) {
            return
        }
        if (isPlayerDowned(session, reviver.uniqueId)) {
            return
        }
        if (reviver.world.uid != target.world.uid) {
            return
        }

        clearReviveBindingByReviver(session, reviver.uniqueId)
        clearReviveBindingForDowned(session, target.uniqueId)

        val holdState = session.reviveHoldStates.getOrPut(target.uniqueId) { ArenaReviveHoldState() }
        val currentTick = Bukkit.getCurrentTick().toLong()
        holdState.reviverPlayerId = reviver.uniqueId
        holdState.heldTicks = 0
        holdState.linkedAtTick = currentTick
        holdState.lastProgressTick = currentTick
        session.reviveTargetByReviver[reviver.uniqueId] = target.uniqueId

        updateReviveBossBar(session, target, reviver, 0.0f)
        target.playSound(target.location, Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 0.9f)
        reviver.playSound(reviver.location, Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 0.9f)
    }

    private fun updateReviveBossBar(session: ArenaSession, downed: Player, reviver: Player, progress: Float) {
        val bossBars = session.reviveBossBarsByDowned.getOrPut(downed.uniqueId) {
            ArenaReviveBossBars(
                downedPlayerBar = BossBar.bossBar(Component.empty(), 0.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS),
                reviverPlayerBar = BossBar.bossBar(Component.empty(), 0.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS)
            )
        }
        bossBars.downedPlayerBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.revive.downed", "§7{reviver}さんが蘇生中...", "reviver" to reviver.name)))
        bossBars.reviverPlayerBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.revive.reviver", "§7{downed}さんを蘇生中...", "downed" to downed.name)))
        bossBars.downedPlayerBar.progress(progress.coerceIn(0.0f, 1.0f))
        bossBars.reviverPlayerBar.progress(progress.coerceIn(0.0f, 1.0f))

        if (downed.isOnline) {
            downed.showBossBar(bossBars.downedPlayerBar)
            downed.hideBossBar(bossBars.reviverPlayerBar)
        }
        if (reviver.isOnline) {
            reviver.showBossBar(bossBars.reviverPlayerBar)
            reviver.hideBossBar(bossBars.downedPlayerBar)
        }

        session.participants
            .asSequence()
            .filter { it != downed.uniqueId && it != reviver.uniqueId }
            .mapNotNull { Bukkit.getPlayer(it) }
            .filter { it.isOnline }
            .forEach {
                it.hideBossBar(bossBars.downedPlayerBar)
                it.hideBossBar(bossBars.reviverPlayerBar)
            }
    }

    private fun hideReviveBossBar(session: ArenaSession, downedId: UUID) {
        val bossBars = session.reviveBossBarsByDowned.remove(downedId) ?: return
        hideReviveBossBarFromAll(session, bossBars)
    }

    private fun hideReviveBossBarFromAll(session: ArenaSession, bossBars: ArenaReviveBossBars) {
        Bukkit.getOnlinePlayers()
            .asSequence()
            .filter { it.isOnline }
            .forEach {
                it.hideBossBar(bossBars.downedPlayerBar)
                it.hideBossBar(bossBars.reviverPlayerBar)
            }
    }

    private fun clearReviveBindingByReviver(session: ArenaSession, reviverId: UUID) {
        val downedId = session.reviveTargetByReviver.remove(reviverId) ?: return
        val holdState = session.reviveHoldStates[downedId]
        if (holdState != null && holdState.reviverPlayerId == reviverId) {
            holdState.reviverPlayerId = null
            holdState.heldTicks = 0
        }
        hideReviveBossBar(session, downedId)
    }

    private fun clearReviveBindingForDowned(session: ArenaSession, downedId: UUID, playInterruptedSound: Boolean = true) {
        val holdState = session.reviveHoldStates[downedId]
        val reviverId = holdState?.reviverPlayerId
        val hadLink = reviverId != null
        if (reviverId != null) {
            session.reviveTargetByReviver.remove(reviverId)
        }
        holdState?.reviverPlayerId = null
        holdState?.heldTicks = 0
        hideReviveBossBar(session, downedId)
        if (playInterruptedSound && hadLink) {
            val downed = Bukkit.getPlayer(downedId)
            val reviver = reviverId?.let { Bukkit.getPlayer(it) }
            if (downed != null && downed.isOnline) {
                downed.playSound(downed.location, Sound.UI_BUTTON_CLICK, 1.0f, 0.5f)
            }
            if (reviver != null && reviver.isOnline) {
                reviver.playSound(reviver.location, Sound.UI_BUTTON_CLICK, 1.0f, 0.5f)
            }
        }
    }

    private fun retargetMobsFromDownedPlayers(session: ArenaSession) {
        session.activeMobs.forEach { mobId ->
            val mob = Bukkit.getEntity(mobId) as? Mob ?: return@forEach
            val target = mob.target as? Player ?: return@forEach
            if (!isPlayerDowned(session, target.uniqueId)) {
                return@forEach
            }
            mob.target = selectNearestParticipant(session, mob.location)
        }
    }

    private fun syncDownedShulker(session: ArenaSession, downed: Player, forceTeleport: Boolean) {
        val downState = session.downedPlayers[downed.uniqueId] ?: return
        val loc = downed.location
        val anchor = Location(loc.world, loc.x, loc.y + 0.8, loc.z, loc.yaw, loc.pitch)
        val world = anchor.world ?: return

        val carrier = downState.carrierEntityId
            ?.let { Bukkit.getEntity(it) as? ArmorStand }
            ?.takeIf { it.isValid && !it.isDead }
            ?: (world.spawnEntity(anchor, EntityType.ARMOR_STAND) as ArmorStand).also { spawned ->
                configureDownedCarrier(spawned)
                downState.carrierEntityId = spawned.uniqueId
            }

        val shulker = downState.shulkerEntityId
            ?.let { Bukkit.getEntity(it) as? Shulker }
            ?.takeIf { it.isValid && !it.isDead }
            ?: (world.spawnEntity(anchor, EntityType.SHULKER) as Shulker).also { spawned ->
                configureDownedShulker(spawned)
                downState.shulkerEntityId = spawned.uniqueId
            }

        if (forceTeleport || carrier.location.distanceSquared(anchor) > 0.01) {
            carrier.removePassenger(shulker)
            carrier.teleport(anchor)
            shulker.teleport(anchor)
            carrier.addPassenger(shulker)
        }

        ejectAlivePassengersFromDownedShulker(session, shulker)
    }

    private fun configureDownedCarrier(armorStand: ArmorStand) {
        armorStand.setGravity(false)
        armorStand.isInvulnerable = true
        armorStand.isSilent = true
        armorStand.isPersistent = true
        armorStand.isVisible = false
        armorStand.isMarker = true
        armorStand.addScoreboardTag("arena.down.carrier")
    }

    private fun configureDownedShulker(shulker: Shulker) {
        shulker.setAI(false)
        shulker.setGravity(false)
        shulker.isInvulnerable = true
        shulker.isInvisible = true
        shulker.isSilent = true
        shulker.isPersistent = true
        shulker.addScoreboardTag("arena.down.shulker")
    }

    private fun ejectAlivePassengersFromDownedShulker(session: ArenaSession, shulker: Shulker) {
        val baseBlockLocation = shulker.location.block.location
        val ejectLocation = baseBlockLocation.clone().add(1.5, 0.0, 0.5)

        shulker.passengers.toList().forEach { passenger ->
            val passengerPlayer = passenger as? Player ?: run {
                shulker.removePassenger(passenger)
                return@forEach
            }
            if (!session.participants.contains(passengerPlayer.uniqueId) || isPlayerDowned(session, passengerPlayer.uniqueId)) {
                return@forEach
            }
            shulker.removePassenger(passengerPlayer)
            val destination = if (ejectLocation.block.isPassable) {
                ejectLocation
            } else {
                ejectLocation.clone().add(0.0, 1.0, 0.0)
            }
            passengerPlayer.teleport(destination)
        }
    }

    private fun clearDownedState(session: ArenaSession, playerId: UUID, playInterruptedSound: Boolean = true) {
        clearReviveBindingForDowned(session, playerId, playInterruptedSound)
        val downState = session.downedPlayers.remove(playerId)
        session.reviveHoldStates.remove(playerId)
        restoreDownedWalkSpeed(session, playerId)
        restoreDownedJumpStrength(session, playerId)

        val shulker = downState?.shulkerEntityId?.let { Bukkit.getEntity(it) as? Shulker }
        if (shulker != null && shulker.isValid && !shulker.isDead) {
            shulker.remove()
        }
        downState?.shulkerEntityId = null

        val carrier = downState?.carrierEntityId?.let { Bukkit.getEntity(it) as? ArmorStand }
        if (carrier != null && carrier.isValid && !carrier.isDead) {
            carrier.remove()
        }
        downState?.carrierEntityId = null
    }

    private fun restoreDownedWalkSpeed(session: ArenaSession, playerId: UUID) {
        val originalSpeed = session.downedOriginalWalkSpeeds.remove(playerId) ?: return
        val player = Bukkit.getPlayer(playerId) ?: return
        if (!player.isOnline) {
            return
        }
        player.walkSpeed = originalSpeed
    }

    private fun restoreDownedJumpStrength(session: ArenaSession, playerId: UUID) {
        val originalStrength = session.downedOriginalJumpStrengths.remove(playerId) ?: return
        val player = Bukkit.getPlayer(playerId) ?: return
        if (!player.isOnline) return
        player.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = originalStrength
    }

    private fun resolveSessionLobbyLocation(session: ArenaSession): Location? {
        if (session.lobbyMarkerLocations.isEmpty()) {
            return null
        }
        val selected = session.lobbyMarkerLocations[random.nextInt(session.lobbyMarkerLocations.size)]
        return selected.clone()
    }

    private fun applySessionGameMode(session: ArenaSession, player: Player) {
        session.originalGameModes.putIfAbsent(player.uniqueId, player.gameMode)
        if (player.gameMode != GameMode.ADVENTURE) {
            player.gameMode = GameMode.ADVENTURE
        }
    }

    private fun restoreSessionGameMode(session: ArenaSession, player: Player) {
        val original = session.originalGameModes.remove(player.uniqueId) ?: return
        if (player.gameMode != original) {
            player.gameMode = original
        }
    }

    private fun playStageEntrySounds(player: Player) {
        player.playSound(player.location, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.75f)
        player.playSound(player.location, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.25f)
    }

    private fun playStageEntrySoundsLater(player: Player) {
        val playerId = player.uniqueId
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val onlinePlayer = Bukkit.getPlayer(playerId) ?: return@Runnable
            if (!onlinePlayer.isOnline) return@Runnable
            playStageEntrySounds(onlinePlayer)
        }, 20L)
    }

    private fun applyArenaMobDrop(event: EntityDeathEvent) {
        val entity = event.entity
        if (!mobToSessionWorld.containsKey(entity.uniqueId)) {
            return
        }

        val killer = entity.killer
        val baseExp = event.droppedExp
        event.drops.clear()

        if (killer == null) {
            event.droppedExp = 0
            return
        }

        val definitionTypeId = mobToDefinitionTypeId[entity.uniqueId] ?: entity.type.name
        val normalizedTypeId = definitionTypeId.trim().lowercase(Locale.ROOT)
        val lootingLevel = resolveLootingLevel(killer.inventory)

        val rebuiltDrops = mutableListOf<ItemStack>()
        rebuiltDrops += buildConfiguredAdditionalDrops(killer, normalizedTypeId, lootingLevel)

        createMobTokenDrop(killer, normalizedTypeId, lootingLevel)?.let { token ->
            rebuiltDrops += token
        }

        rebuiltDrops.forEach { stack ->
            if (stack.type.isAir || stack.amount <= 0) return@forEach
            entity.world.dropItemNaturally(entity.location.add(0.0, 0.5, 0.0), stack)
        }
        event.droppedExp = floor(baseExp * dropConfig.mockExpBonusRate).toInt().coerceAtLeast(0)
    }

    private fun buildConfiguredAdditionalDrops(killer: Player, mobTypeId: String, lootingLevel: Int): List<ItemStack> {
        val entries = mutableListOf<ArenaDropEntry>()
        entries += dropConfig.additionalDefaultDrops
        entries += dropConfig.additionalByMobType[mobTypeId].orEmpty()
        val categoryTypeId = resolveMobTokenCategoryTypeId(mobTypeId)
        if (categoryTypeId != mobTypeId) {
            entries += dropConfig.additionalByMobType[categoryTypeId].orEmpty()
        }

        return entries.mapNotNull { entry ->
            if (random.nextDouble() >= entry.chance) return@mapNotNull null
            val amount = applyAmountModifiers(entry.minAmount, entry.maxAmount, lootingLevel)
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

    private fun applyAmountModifiers(minAmount: Int, maxAmount: Int, lootingLevel: Int): Int {
        val base = if (minAmount == maxAmount) {
            minAmount
        } else {
            random.nextInt(minAmount, maxAmount + 1)
        }
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

    private fun createMobTokenDrop(killer: Player, mobTypeId: String, lootingLevel: Int): ItemStack? {
        val definitionDropChance = mobDefinitions[mobTypeId]?.mobTokenDropChance
        val baseDropChance = (definitionDropChance ?: mobTokenDropChance).coerceIn(0.0, 1.0)
        val finalDropChance = (baseDropChance + lootingLevel.coerceAtLeast(0).toDouble() * mobTokenLootingBonusPerLevel)
            .coerceIn(0.0, 1.0)
        if (random.nextDouble() >= finalDropChance) {
            return null
        }
        val categoryTypeId = resolveMobTokenCategoryTypeId(mobTypeId)
        return CustomItemManager.createItemForPlayer(ArenaMobTokenItem(categoryTypeId), killer, 1)
    }

    private fun resolveMobTokenCategoryTypeId(mobTypeId: String): String {
        val normalized = sanitizeMobTypeId(mobTypeId)
        when (normalized) {
            "ashen_spirit", "water_spirit" -> return "spirit"
        }
        val baseEntityType = mobService.resolveMobType(normalized)?.baseEntityType
        if (baseEntityType != null) {
            return sanitizeMobTypeId(baseEntityType.name)
        }

        val fallbackEntityType = runCatching { EntityType.valueOf(normalized.uppercase(Locale.ROOT)) }.getOrNull()
        if (fallbackEntityType != null) {
            return sanitizeMobTypeId(fallbackEntityType.name)
        }

        return normalized
    }

    private fun sanitizeMobTypeId(typeId: String): String {
        val normalized = typeId.trim().lowercase(Locale.ROOT)
        return normalized.replace(Regex("[^a-z0-9_]+"), "_")
    }

    private fun registerMobTypeTokenItems(): Set<String> {
        val tokenTypeIds = buildSet {
            knownMobTypeIds.forEach { add(resolveMobTokenCategoryTypeId(it)) }
        }
        CustomItemManager.register(ArenaMobTokenItem())
        CustomItemManager.register(BoomerangTokenItem())
        return tokenTypeIds
    }

    private fun validateMobTokenLanguageKeys(requiredTypeIds: Set<String>) {
        if (requiredTypeIds.isEmpty()) return

        val locales = discoverAvailableLocales()
        locales.forEach { locale ->
            val config = loadLangConfig(locale)
            if (config == null) {
                plugin.logger.warning("[Arena] mob_token言語検証: locale=$locale の言語ファイルを読み込めません")
                return@forEach
            }

            val missingKeys = mutableListOf<String>()
            if (!config.isString("custom_items.arena.mob_token.display_format")) {
                missingKeys += "custom_items.arena.mob_token.display_format"
            }
            if (!config.isList("custom_items.arena.mob_token.lore")) {
                missingKeys += "custom_items.arena.mob_token.lore"
            }

            requiredTypeIds.forEach { typeId ->
                val normalizedTypeId = normalizeMobTokenLanguageTypeId(typeId)
                val key = if (usesHeadTokenDisplayName(normalizedTypeId)) {
                    "custom_items.arena.mob_token.mob_names.$normalizedTypeId"
                } else {
                    "custom_items.arena.mob_token.drop_names.$normalizedTypeId"
                }
                if (!config.isString(key)) {
                    missingKeys += key
                }
            }

            if (missingKeys.isNotEmpty()) {
                plugin.logger.warning("[Arena] mob_token言語検証: locale=$locale で不足/型不正キー ${missingKeys.size}件")
                missingKeys.forEach { key ->
                    plugin.logger.warning("[Arena]   - $key")
                }
            }
        }
    }

    private fun usesHeadTokenDisplayName(typeId: String): Boolean {
        return when (typeId) {
            "skeleton", "zombie", "creeper", "piglin", "ender_dragon" -> true
            else -> false
        }
    }

    private fun normalizeMobTokenLanguageTypeId(typeId: String): String {
        return when (sanitizeMobTypeId(typeId)) {
            "cave_spider" -> "spider"
            else -> sanitizeMobTypeId(typeId)
        }
    }

    private fun discoverAvailableLocales(): Set<String> {
        val locales = linkedSetOf("ja_jp", "en_us")

        val langDir = File(plugin.dataFolder, "lang")
        if (langDir.exists()) {
            val fromDataFolder = langDir.listFiles { file ->
                file.isFile && file.extension.equals("yml", ignoreCase = true)
            }.orEmpty().map { it.nameWithoutExtension.lowercase(Locale.ROOT) }
            locales.addAll(fromDataFolder)
        }

        return locales
    }

    private fun loadLangConfig(locale: String): YamlConfiguration? {
        val normalized = locale.lowercase(Locale.ROOT)
        val fromDataFolder = File(plugin.dataFolder, "lang/$normalized.yml")
        if (fromDataFolder.exists()) {
            return YamlConfiguration.loadConfiguration(fromDataFolder)
        }

        val resource = plugin.getResource("lang/$normalized.yml") ?: return null
        resource.use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                return YamlConfiguration.loadConfiguration(reader)
            }
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

    private fun leavePlayerFromSession(playerId: UUID, reason: String, destinationOverride: Location? = null): Boolean {
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
        session.barrierReturnHoldTicksByParticipant.remove(playerId)
        session.barrierReturnSubtitleNextTickByParticipant.remove(playerId)
        session.arenaBgmModeByParticipant.remove(playerId)
        session.arenaBgmSwitchRequestByParticipant.remove(playerId)
        session.playerWaveCatchupDeadlineMillis.remove(playerId)
        clearReviveBindingByReviver(session, playerId)
        clearDownedState(session, playerId)
        session.reviveCountByPlayer.remove(playerId)
        session.invitedParticipants.remove(playerId)
        session.waitingParticipants.remove(playerId)
        session.waitingNotifiedParticipants.remove(playerId)
        session.waitingSubtitleNextTickByPlayer.remove(playerId)
        session.entranceLiftLockedParticipants.remove(playerId)
        session.arenaPreparingUntilMillisByParticipant.remove(playerId)
        hideJoinCountdownBossBar(session, playerId)
        releaseInvitedPlayerLock(playerId)
        removeArenaSidebar(playerId)
        confusionManager.removeConfusion(playerId)

        val player = Bukkit.getPlayer(playerId)
        if (player != null && player.isOnline) {
            stopArenaBgmForPlayer(player)
            restoreSessionGameMode(session, player)
            session.progressBossBar?.let { player.hideBossBar(it) }
            val fallback = Bukkit.getWorlds().firstOrNull()?.spawnLocation
            val destination = destinationOverride
                ?: if (returnLocation?.world != null) returnLocation else fallback
            if (destination != null) {
                player.teleport(destination)
            }
            player.removePotionEffect(PotionEffectType.BLINDNESS)
            if (reason.isNotBlank()) {
                player.sendMessage(reason)
            }
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
        duringShutdown: Boolean = false,
        vararg messagePlaceholders: Pair<String, Any?>
    ) {
        transitionSessionPhase(session, ArenaPhase.TERMINATING)
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
        session.entranceLiftTask?.cancel()
        session.entranceLiftTask = null
        releaseEntranceLiftChunkTickets(session)
        session.stageBuildTask?.cancel()
        session.stageBuildTask = null
        cleanupBarrierRestartSession(session, removeDefenseMobs = true, smoke = false)
        hideSessionProgressBossBar(session)
        clearMultiplayerRecruitmentState(session)
        session.downedPlayers.keys.toList().forEach { downedId ->
            clearDownedState(session, downedId)
        }

        val sidebarCleanupTargets = linkedSetOf<UUID>()
        sidebarCleanupTargets.addAll(session.participants)
        sidebarCleanupTargets.addAll(session.invitedParticipants)
        sidebarCleanupTargets.addAll(session.sidebarParticipantOrder)
        sidebarCleanupTargets.forEach { playerId ->
            removeArenaSidebar(playerId)
        }

        session.participants.toList().forEach { participantId ->
            playerToSessionWorld.remove(participantId)
            inviteSwingCooldownUntilTick.remove(participantId)
            val player = Bukkit.getPlayer(participantId)
            if (player != null && player.isOnline) {
                if (duringShutdown || shuttingDown) {
                    stopArenaBgmForPlayer(player)
                } else {
                    scheduleBoundaryStopArenaBgmForPlayer(session, participantId, player)
                }
                restoreSessionGameMode(session, player)
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
        session.barrierReturnHoldTicksByParticipant.clear()
        session.barrierReturnSubtitleNextTickByParticipant.clear()
        session.returnLocations.clear()
        session.originalGameModes.clear()
        session.playerNotifiedWaves.clear()
        session.participantLocationHistory.clear()
        session.participantLastSampleMillis.clear()
        session.downedPlayers.clear()
        session.reviveHoldStates.clear()
        session.reviveTargetByReviver.clear()
        session.reviveBossBarsByDowned.values.forEach { hideReviveBossBarFromAll(session, it) }
        session.reviveBossBarsByDowned.clear()
        session.reviveCountByPlayer.clear()
        session.arenaBgmModeByParticipant.clear()
        session.arenaBgmSwitchRequestByParticipant.clear()
        session.downedOriginalWalkSpeeds.clear()
        session.downedOriginalJumpStrengths.clear()
        session.invitedParticipants.clear()
        session.sidebarParticipantOrder.clear()
        session.sidebarParticipantNames.clear()
        session.waitingParticipants.clear()
        session.waitingNotifiedParticipants.clear()
        session.waitingSubtitleNextTickByPlayer.clear()
        session.arenaPreparingUntilMillisByParticipant.clear()
        session.entranceLiftLockedParticipants.clear()
        session.joinCountdownBossBars.clear()
        session.joinAreaMarkerLocations.clear()
        releaseOccupiedLiftMarkers(session)
        liftOccupiedWaiters.clear()
        session.liftMarkerLocations.clear()
        session.lobbyMarkerLocations.clear()
        session.entranceLiftChunkTicketWorldName = null
        session.entranceLiftChunkTicketKeys.clear()
        session.multiplayerJoinEnabled = false
        transitionSessionPhase(session, ArenaPhase.PREPARING)
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
        session.waveEnteredAtMillis.clear()
        session.playerWaveCatchupDeadlineMillis.clear()
        session.waveSpawningStopped.clear()
        session.oageAnnouncements.clear()
        session.waveClearReminderTasks.values.forEach { it.cancel() }
        session.waveClearReminderTasks.clear()
        session.waveClearedAnnouncementTasks.values.forEach { it.cancel() }
        session.waveClearedAnnouncementTasks.clear()
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
            session.mobDamagedParticipants.remove(mobId)
            mobService.untrack(mobId)
            val entity = Bukkit.getEntity(mobId)
            if (entity != null && entity.isValid && !entity.isDead) {
                spawnMobDisappearSmoke(entity.location)
                entity.remove()
            }
            session.activeMobs.remove(mobId)
        }
        session.waveMobIds.clear()

        enqueueArenaWorldCleanup(session)
    }

    private fun enqueueArenaWorldCleanup(session: ArenaSession) {
        val worldName = session.worldName
        if (!worldName.startsWith("$ARENA_POOL_WORLD_NAME_PREFIX.")) {
            val legacyWorld = Bukkit.getWorld(worldName)
            if (legacyWorld != null) {
                tryDeleteWorld(legacyWorld)
            } else {
                queueWorldDeletion(worldName, File(Bukkit.getWorldContainer(), worldName))
            }
            return
        }

        val world = Bukkit.getWorld(worldName)
        if (world == null) {
            markArenaWorldBroken(worldName)
            logArenaPoolState("セッション終了", worldName)
            return
        }

        cleanupNonPlayerEntities(world)

        val cleanupBounds = collectCleanupBounds(session)
        if (cleanupBounds.isEmpty()) {
            markArenaWorldReady(worldName)
            logArenaPoolState("セッション終了", worldName)
            return
        }

        val totalBlocks = calculateCleanupBlocks(cleanupBounds)
        val now = System.currentTimeMillis()
        val volumes = ArrayDeque(cleanupBounds.map { ArenaWorldCleanupVolume(it) })
        cleanupWorldJobs.addLast(
            ArenaWorldCleanupJob(
                worldName = worldName,
                volumes = volumes,
                totalBlocks = totalBlocks,
                processedBlocks = 0L,
                startedAtMillis = now,
                lastProgressLogAtMillis = now
            )
        )
        arenaWorldStates[worldName] = ArenaPoolWorldState.CLEANING
        logArenaPoolState("セッション終了", worldName)
        val estimatedSeconds = estimateCleanupSeconds(totalBlocks)
        plugin.logger.info(
            "[Arena] クリーンアップ開始: world=$worldName blocks=$totalBlocks " +
                "estimated=${formatSeconds(estimatedSeconds)}s budget=${cleanupBlocksPerTick}/tick"
        )
    }

    private fun collectCleanupBounds(session: ArenaSession): List<ArenaBounds> {
        val bounds = linkedMapOf<String, ArenaBounds>()
        session.roomBounds.values.forEach { value ->
            bounds[boundsKey(value)] = value
        }
        session.corridorBounds.values.forEach { value ->
            bounds[boundsKey(value)] = value
        }
        if (bounds.isEmpty()) {
            bounds[boundsKey(session.stageBounds)] = session.stageBounds
        }
        return bounds.values.toList()
    }

    private fun boundsKey(bounds: ArenaBounds): String {
        return "${bounds.minX}:${bounds.maxX}:${bounds.minY}:${bounds.maxY}:${bounds.minZ}:${bounds.maxZ}"
    }

    private fun cleanupNonPlayerEntities(world: World) {
        world.entities.toList().forEach { entity ->
            if (entity.type == EntityType.PLAYER) {
                return@forEach
            }
            if (!entity.isDead && entity.isValid) {
                entity.remove()
            }
        }
    }

    private fun calculateCleanupBlocks(boundsList: List<ArenaBounds>): Long {
        return boundsList.sumOf { bounds ->
            val width = (bounds.maxX - bounds.minX + 1).toLong().coerceAtLeast(0L)
            val height = (bounds.maxY - bounds.minY + 1).toLong().coerceAtLeast(0L)
            val depth = (bounds.maxZ - bounds.minZ + 1).toLong().coerceAtLeast(0L)
            width * height * depth
        }
    }

    private fun estimateCleanupSeconds(totalBlocks: Long): Double {
        val blocksPerSecond = cleanupBlocksPerTick.toDouble() * 20.0
        if (blocksPerSecond <= 0.0) return 0.0
        return totalBlocks.toDouble() / blocksPerSecond
    }

    private fun formatSeconds(seconds: Double): String {
        return String.format(Locale.US, "%.1f", seconds.coerceAtLeast(0.0))
    }

    private fun formatPercent(value: Double): String {
        return String.format(Locale.US, "%.1f", value.coerceIn(0.0, 100.0))
    }

    private fun logArenaPoolState(event: String, worldName: String? = null) {
        val ready = arenaWorldStates.values.count { it == ArenaPoolWorldState.READY }
        val inUse = arenaWorldStates.values.count { it == ArenaPoolWorldState.IN_USE }
        val cleaning = arenaWorldStates.values.count { it == ArenaPoolWorldState.CLEANING }
        val broken = arenaWorldStates.values.count { it == ArenaPoolWorldState.BROKEN }
        val target = if (worldName != null) " world=$worldName" else ""
        plugin.logger.info(
            "[Arena] $event:$target pool={ready=$ready, in_use=$inUse, cleaning=$cleaning, broken=$broken, queue=${cleanupWorldJobs.size}}"
        )
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

        session.waveClearReminderTasks.remove(wave - 1)?.cancel()

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
        if (wave == session.waves && session.finalWaveStartedAtMillis <= 0L) {
            session.finalWaveStartedAtMillis = System.currentTimeMillis()
        }
        requestWaveStartCombatBgm(session)
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
            if (!isCombatBgmReadyForWaveStart(currentSession)) return@Runnable

            val spawnThrottle = mobService.getSpawnThrottle("arena:${currentSession.worldName}")
            val intervalChance = (1.0 / spawnThrottle.intervalMultiplier).coerceIn(0.0, 1.0)
            if (random.nextDouble() < spawnThrottle.skipChance) {
                return@Runnable
            }
            if (random.nextDouble() > intervalChance) {
                return@Runnable
            }

            val maxAliveBase = currentSession.stageMaxAliveCount.coerceAtLeast(1)
            val maxAlive = if (currentSession.barrierRestarting && wave == currentSession.waves) {
                (maxAliveBase * 2.0).roundToInt().coerceAtLeast(maxAliveBase)
            } else if (currentSession.barrierRestarting) {
                (maxAliveBase * 1.5).roundToInt().coerceAtLeast(maxAliveBase)
            } else {
                maxAliveBase
            }
            val waveAliveCount = currentSession.waveMobIds[wave]
                ?.count { mobId -> Bukkit.getEntity(mobId)?.let { it.isValid && !it.isDead } == true }
                ?: 0
            if (waveAliveCount >= maxAlive) {
                return@Runnable
            }

            val world = Bukkit.getWorld(currentSession.worldName) ?: return@Runnable
            val spawnPoint = selectSpawnPoint(spawns) ?: run {
                return@Runnable
            }
            val spawnCandidates = selectSpawnCandidates(theme, wave)
            val candidates = filterSpawnCandidatesByLocation(spawnCandidates, spawnPoint)
            if (candidates.isEmpty()) {
                return@Runnable
            }
            val weightedMob = selectWeightedMob(candidates) ?: run {
                return@Runnable
            }
            val definition = mobDefinitions[weightedMob.mobId] ?: run {
                return@Runnable
            }

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

    private fun teleportToLatestDoorMarker(player: Player, session: ArenaSession, world: World, currentWave: Int) {
        val latestWave = latestEnteredWave(session) ?: currentWave.coerceIn(1, session.waves)
        teleportToNearestDoorMarkerForWave(player, session, world, latestWave)
        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 15, 0, false, false, false))
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

    private fun notifyWaveEntryIfNeeded(session: ArenaSession, player: Player, wave: Int, playWitherSpawn: Boolean) {
        if (session.clearedWaves.contains(wave)) return

        val notified = session.playerNotifiedWaves.getOrPut(player.uniqueId) { mutableSetOf() }
        if (!notified.add(wave)) return

        val title = if (wave >= session.waves) {
            ArenaI18n.text(player, "arena.messages.wave.last_title", "§7- §6Last Wave §7-")
        } else {
            ArenaI18n.text(player, "arena.messages.wave.title", "§7- §6Wave {wave} §7-", "wave" to wave)
        }
        player.sendTitle("", title, 10, 50, 10)
        if (playWitherSpawn) {
            player.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f)
        }
    }

    private fun processRoomProgress(session: ArenaSession, player: Player, room: Int) {
        val firstEntrant = !session.enteredWaves.contains(room)
        notifyWaveEntryIfNeeded(session, player, room, playWitherSpawn = firstEntrant)
        if (firstEntrant) {
            handleWaveRoomEntry(session, player, room)
            return
        }
    }

    private fun handleWaveRoomEntry(session: ArenaSession, entrant: Player, wave: Int) {
        if (!session.enteredWaves.add(wave)) return

        val now = System.currentTimeMillis()
        session.waveEnteredAtMillis[wave] = now

        val catchupTargets = findPlayersInPreviousWaveOrEarlierRooms(session, wave, excludePlayerId = entrant.uniqueId)
        catchupTargets.forEach { target ->
            session.playerWaveCatchupDeadlineMillis[target.uniqueId] = now + WAVE_CATCHUP_TELEPORT_DELAY_MILLIS
        }

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

        val olderWave = previousWave - 1
        if (olderWave > 0) {
            removeWaveMobs(session, olderWave)
        }
    }

    private fun findPlayersInPreviousWaveOrEarlierRooms(
        session: ArenaSession,
        wave: Int,
        excludePlayerId: UUID
    ): List<Player> {
        if (wave <= 1) return emptyList()
        val world = Bukkit.getWorld(session.worldName) ?: return emptyList()
        return session.participants
            .asSequence()
            .filter { it != excludePlayerId }
            .mapNotNull { Bukkit.getPlayer(it) }
            .filter { it.isOnline && it.world.uid == world.uid }
            .filter { isPlayerInPreviousWaveOrEarlierRoom(session, it.location, wave) }
            .toList()
    }

    private fun isPlayerInPreviousWaveOrEarlierRoom(session: ArenaSession, location: Location, wave: Int): Boolean {
        val previousWave = (wave - 1).coerceAtLeast(1)
        val room = locateRoom(session, location) ?: return false
        return room <= previousWave
    }

    private fun latestEnteredWave(session: ArenaSession): Int? {
        return session.waveEnteredAtMillis.maxByOrNull { it.value }?.key
    }

    private fun hasPendingWaveCatchup(session: ArenaSession, player: Player): Boolean {
        session.playerWaveCatchupDeadlineMillis[player.uniqueId] ?: return false
        val latestWave = latestEnteredWave(session) ?: return false
        if (!isPlayerInPreviousWaveOrEarlierRoom(session, player.location, latestWave)) {
            session.playerWaveCatchupDeadlineMillis.remove(player.uniqueId)
            return false
        }
        return true
    }

    private fun processWaveCatchupIfNeeded(session: ArenaSession, player: Player, world: World, now: Long) {
        val deadline = session.playerWaveCatchupDeadlineMillis[player.uniqueId] ?: return
        val latestWave = latestEnteredWave(session) ?: run {
            session.playerWaveCatchupDeadlineMillis.remove(player.uniqueId)
            return
        }
        if (!isPlayerInPreviousWaveOrEarlierRoom(session, player.location, latestWave)) {
            session.playerWaveCatchupDeadlineMillis.remove(player.uniqueId)
            return
        }
        if (now < deadline) {
            return
        }

        teleportToNearestDoorMarkerForWave(player, session, world, latestWave)
        session.playerWaveCatchupDeadlineMillis.remove(player.uniqueId)
    }

    private fun teleportToNearestDoorMarkerForWave(player: Player, session: ArenaSession, world: World, wave: Int) {
        val location = player.location
        val nearest = session.corridorDoorBlocks[wave]
            .orEmpty()
            .asSequence()
            .map { marker -> marker.clone().apply { this.world = world } }
            .minByOrNull { marker -> marker.distanceSquared(location) }

        val destination = nearest ?: currentWavePosition(session, world)
        destination.yaw = location.yaw
        destination.pitch = location.pitch
        player.teleport(destination)
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

    private fun filterSpawnCandidatesByLocation(
        candidates: List<ArenaThemeWeightedMobEntry>,
        spawnPoint: Location
    ): List<ArenaThemeWeightedMobEntry> {
        return candidates.filter { entry ->
            val definition = mobDefinitions[entry.mobId] ?: return@filter false
            canSpawnAt(definition, spawnPoint)
        }
    }

    private fun canSpawnAt(definition: MobDefinition, spawnPoint: Location): Boolean {
        if (definition.spawnConditions.isEmpty()) {
            return true
        }
        return definition.spawnConditions.all { condition ->
            when (condition) {
                MobSpawnCondition.WATER_ONLY -> isWaterSpawnPoint(spawnPoint)
            }
        }
    }

    private fun isWaterSpawnPoint(spawnPoint: Location): Boolean {
        val block = spawnPoint.block
        if (block.type == Material.WATER || block.isLiquid) {
            return true
        }

        if (block.type == Material.LIGHT) {
            return (block.blockData as? Waterlogged)?.isWaterlogged == true
        }

        return false
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

        entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = maxHealth
        entity.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = attack
        entity.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = speed
        entity.getAttribute(Attribute.ARMOR)?.baseValue = armor
        entity.health = maxHealth
    }

    private fun enforceAdultMob(entity: LivingEntity) {
        val vehicle = entity.vehicle
        if (vehicle != null) {
            entity.leaveVehicle()
            if (entity is Zombie && vehicle.type == EntityType.CHICKEN && vehicle.isValid) {
                vehicle.remove()
            }
        }

        when (entity) {
            is Zombie -> {
                entity.isBaby = false
                cleanupNearbyChickenMounts(entity)
            }
            is Ageable -> entity.setAdult()
        }
    }

    private fun cleanupNearbyChickenMounts(zombie: Zombie) {
        zombie.getNearbyEntities(1.5, 1.5, 1.5)
            .asSequence()
            .filter { it.type == EntityType.CHICKEN }
            .forEach { chicken ->
                if (chicken.passengers.any { passenger -> passenger.uniqueId == zombie.uniqueId }) {
                    chicken.passengers.toList().forEach { passenger ->
                        chicken.removePassenger(passenger)
                        if (passenger.uniqueId != zombie.uniqueId) {
                            passenger.remove()
                        }
                    }
                    chicken.remove()
                }
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
        session.mobDamagedParticipants.remove(mobId)
        if (wave != null) {
            session.waveMobIds[wave]?.remove(mobId)
            tryQueueWaveClearedMessage(session, wave)
        }

        schedulePendingWaveClearedMessageIfReady(session)

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

        playSound(session, Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 2.0f)

        updateSessionProgressBossBar(session)
    }

    private fun clearWave(session: ArenaSession, wave: Int) {
        if (session.clearedWaves.contains(wave)) return

        session.clearedWaves.add(wave)
        session.lastClearedWaveForBossBar = wave
        stopWaveSpawning(session, wave)
        updateSessionProgressBossBar(session)
        playSound(session, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.25f)
        tryQueueWaveClearedMessage(session, wave)

        if (wave < session.waves) {
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
                if (activeSession !== session) return@Runnable
                if (!activeSession.clearedWaves.contains(wave)) return@Runnable
                val targetRoom = activeSession.roomBounds[wave + 1] ?: return@Runnable
                val maxAlive = activeSession.stageMaxAliveCount.coerceAtLeast(1)
                val aliveInRoom = activeSession.activeMobs
                    .asSequence()
                    .mapNotNull { mobId -> Bukkit.getEntity(mobId) as? Mob }
                    .filter { mob -> mob.isValid && !mob.isDead && mob.world.name == activeSession.worldName }
                    .count { mob ->
                        val location = mob.location
                        targetRoom.contains(location.x, location.y, location.z)
                    }
                if (aliveInRoom < maxAlive) return@Runnable
            }, 40L)
        }

        if (session.clearedWaves.size >= session.waves) {
            onAllWavesCleared(session)
            return
        }

        activateDoorActionMarkers(session.worldName, wave + 1)
    }

    private fun tryQueueWaveClearedMessage(session: ArenaSession, wave: Int) {
        if (!session.clearedWaves.contains(wave)) return

        val trackedMobIds = session.waveMobIds[wave].orEmpty()
        val aliveMobIds = trackedMobIds
            .asSequence()
            .mapNotNull { mobId -> Bukkit.getEntity(mobId) as? Mob }
            .filter { mob ->
                mob.isValid &&
                    !mob.isDead &&
                    mob.world.name == session.worldName
            }
            .map { mob -> mob.uniqueId }
            .toMutableSet()

        if (aliveMobIds.isNotEmpty()) {
            session.waveMobIds[wave] = aliveMobIds
            return
        }

        if (trackedMobIds.isNotEmpty()) {
            session.waveMobIds.remove(wave)
        }

        if (session.oageAnnouncements.contains("wave_cleared_room_empty_$wave")) return
        session.pendingWaveClearedAnnouncements.add(wave)
        schedulePendingWaveClearedMessageIfReady(session)
    }

    private fun tryBroadcastPendingWaveClearedMessageOnNormalBgm(session: ArenaSession) {
        schedulePendingWaveClearedMessageIfReady(session)
    }

    private fun schedulePendingWaveClearedMessageIfReady(session: ArenaSession) {
        session.pendingWaveClearedAnnouncements.clear()
        session.waveClearedAnnouncementTasks.values.forEach { it.cancel() }
        session.waveClearedAnnouncementTasks.clear()
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
                if (targetWave == 1) {
                    scheduleStartEntranceNormalBgm(activeSession, ENTRANCE_NORMAL_BGM_START_DELAY_TICKS)
                }
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
                    val applied = runCatching {
                        applyDoorAnimationFrame(placement, targetFrame)
                    }.onFailure { throwable ->
                        plugin.logger.warning(
                            "[Arena] ドアアニメフレーム適用に失敗: world=${currentSession.worldName} wave=$targetWave frame=$targetFrame error=${throwable.message}"
                        )
                    }.isSuccess
                    if (!applied) return@forEachIndexed
                    lastAppliedFrameByPlacement[placementIndex] = targetFrame
                }

                if (elapsedTicks >= doorAnimationTotalTicks) {
                    placements.forEachIndexed { placementIndex, placement ->
                        val lastFrame = placement.openFrames.size
                        if (lastFrame <= 0) return@forEachIndexed
                        val alreadyApplied = lastAppliedFrameByPlacement[placementIndex] ?: 0
                        if (alreadyApplied >= lastFrame) return@forEachIndexed
                        runCatching {
                            applyDoorAnimationFrame(placement, lastFrame)
                        }.onFailure { throwable ->
                            plugin.logger.warning(
                                "[Arena] ドアアニメ最終フレーム適用に失敗: world=${currentSession.worldName} wave=$targetWave error=${throwable.message}"
                            )
                        }
                    }
                    currentSession.animatingDoorWaves.remove(targetWave)
                    if (targetWave == 1) {
                        scheduleStartEntranceNormalBgm(currentSession, ENTRANCE_NORMAL_BGM_START_DELAY_TICKS)
                    }
                    updateCurrentWave(currentSession)
                    taskRef[0]?.cancel()
                    return@animationTick
                }

                if (elapsedTicks % 10 == 0) {
                    playDoorAnimationSound(currentSession)
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
            transitionSessionPhase(session, ArenaPhase.IN_PROGRESS)
        }
        playDoorAnimationSound(session)
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
            session.mobDamagedParticipants.remove(mobId)
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

        schedulePendingWaveClearedMessageIfReady(session)
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
        if (activeSession !== session) return
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
        val activatedAtTick = Bukkit.getCurrentTick().toLong()
        session.barrierRestartActivatedPoints.add(
            ArenaBarrierRestartActivatedPoint(
                location = point,
                activatedAtTick = activatedAtTick,
                nextRenderTick = activatedAtTick + BARRIER_RESTART_BEAM_BLINK_INTERVAL_TICKS
            )
        )

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
            player.playSound(point, "minecraft:entity.warden.sonic_boom", 0.9f, 0.75f)
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
        val currentTick = Bukkit.getCurrentTick().toLong()

        session.barrierRestartActivatedPoints.forEach { pointState ->
            if (currentTick < pointState.nextRenderTick) {
                return@forEach
            }
            while (currentTick >= pointState.nextRenderTick) {
                pointState.nextRenderTick += BARRIER_RESTART_BEAM_BLINK_INTERVAL_TICKS
            }

            val origin = pointState.location.clone().apply { this.world = world }
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
            val jitterX = x + random.nextDouble(-0.1, 0.1)
            val jitterY = y + random.nextDouble(-0.1, 0.1)
            val jitterZ = z + random.nextDouble(-0.1, 0.1)
            world.spawnParticle(Particle.END_ROD, jitterX, jitterY, jitterZ, 1, 0.0, 0.0, 0.0, 0.0)
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
                    mob.location.distanceSquared(point.location) < BARRIER_RESTART_POINT_DAMAGE_RADIUS_SQUARED
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
        val candidates = filterSpawnCandidatesByLocation(selectSpawnCandidates(theme, session.waves), spawnPoint)
        val weightedMob = selectWeightedMob(candidates) ?: return
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

        requestArenaBgmMode(session, ArenaBgmMode.STOPPED, strictNextBoundary = true)

        session.barrierRestarting = false
        session.barrierRestartCompleted = true
        session.finalWaveStartedAtMillis = 0L
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
        arenaQuestService?.recordBarrierRestart(session.participants)

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            activeSession.participants.forEach { participantId ->
                val player = Bukkit.getPlayer(participantId) ?: return@forEach
                if (!player.isOnline || player.world.name != activeSession.worldName) return@forEach
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f)
                player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f)
                sendOageMessage(
                    player,
                    "arena.messages.barrier.restart_confirmed",
                    ArenaI18n.text(player, "arena.messages.barrier.restart_confirmed", "結界の再起動を確認しました！")
                )
                player.sendTitle(
                    "",
                    ArenaI18n.text(player, "arena.messages.barrier.return_hint", "§7Shift長押しでロビーに帰還できます"),
                    0,
                    50,
                    10
                )
            }
        }, 200L)
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
            session.mobDamagedParticipants.remove(mobId)
            mobService.untrack(mobId)
            session.activeMobs.remove(mobId)
        }

        session.waveMobIds.values.forEach { it.clear() }

        schedulePendingWaveClearedMessageIfReady(session)
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
                session.mobDamagedParticipants.remove(mobId)
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
            BossBar.Overlay.NOTCHED_10
        ).also { created ->
            session.progressBossBar = created
        }

        val world = Bukkit.getWorld(session.worldName)
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            if (player.isOnline && world != null && player.world.name == world.name) {
                if (hasPendingWaveCatchup(session, player)) {
                    player.hideBossBar(bossBar)
                } else {
                    player.showBossBar(bossBar)
                }
            }
        }

        if (session.barrierRestarting) {
            bossBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.barrier_restart", "§7- §6Last Wave §7- §d再起動中...")))
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
                bossBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.wave_clear", "§7- §6Wave {wave} §7- §bCLEAR", "wave" to clearedWave)))
                bossBar.color(BossBar.Color.BLUE)
                bossBar.progress(1.0f)
                return
            }

            val waveLabel = if (clearedWave >= session.waves) {
                ArenaI18n.text(null, "arena.bossbar.last_wave_label", "Last Wave")
            } else {
                ArenaI18n.text(null, "arena.bossbar.wave_label", "Wave {wave}", "wave" to clearedWave)
            }
            bossBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.wave_clear_dynamic", "§7- §6{waveLabel} §7- §bCLEAR", "waveLabel" to waveLabel)))
            bossBar.color(BossBar.Color.BLUE)
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
            bossBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.last_wave_progress", "§7- §6Last Wave §7- §8(§7{activated}/§b{total}§8)", "activated" to activated, "total" to total)))
            bossBar.color(BossBar.Color.BLUE)
            bossBar.progress(progress)
            return
        }

        val kills = session.waveKillCount[wave] ?: 0
        val target = (session.waveClearTargets[wave] ?: 1).coerceAtLeast(1)
        val progress = (kills.toDouble() / target.toDouble()).toFloat().coerceIn(0.0f, 1.0f)
        val waveLabel = if (wave >= session.waves) {
            ArenaI18n.text(null, "arena.bossbar.last_wave_label", "Last Wave")
        } else {
            ArenaI18n.text(null, "arena.bossbar.wave_label", "Wave {wave}", "wave" to wave)
        }
        bossBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.wave_progress", "§7- §6{waveLabel} §7- §8(§c{kills}§7/{target}§8)", "waveLabel" to waveLabel, "kills" to kills, "target" to target)))
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
            session.mobDamagedParticipants.remove(mobId)
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
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            if (!player.isOnline || player.world.name != session.worldName) return@forEach
            player.playSound(player.location, sound, volume, pitch)
        }
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

    private fun broadcastOageMessage(
        session: ArenaSession,
        key: String,
        fallback: List<String>,
        vararg placeholders: Pair<String, Any?>
    ) {
        var lastMessage: String? = null
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            if (!player.isOnline || player.world.name != session.worldName) return@forEach

            val message = ArenaI18n.stringList(player, key, fallback, *placeholders).randomOrNull() ?: return@forEach
            lastMessage = message
            sendOageMessage(player, key, message)
        }
        if (lastMessage != null) {
            session.lastOageMessage = lastMessage
        }
    }

    private fun oageMessageChance(key: String): Double {
        return when (key) {
            "arena.messages.oage.stage_start" -> 1.0
            "arena.messages.oage.combat_all_engaged",
            "arena.messages.oage.combat_all_calm",
            "arena.messages.oage.wave_cleared_full_room" -> 0.3
            "arena.messages.oage.barrier_restart_started",
            "arena.messages.oage.game_over_followup",
            "arena.messages.oage.game_over_with_survivors",
            "arena.messages.oage.game_over_all_wiped",
            "arena.messages.oage.participant_died_followup",
            "arena.messages.oage.down_with_survivors" -> 1.0
            else -> 0.5
        }
    }

    private fun sendOageMessage(
        player: Player,
        key: String,
        message: String,
        force: Boolean = false
    ) {
        if (!force && random.nextDouble() >= oageMessageChance(key)) return
        OageMessageSender.send(player, "§f「$message」", plugin)
    }

    private fun scheduleOageMessage(
        player: Player,
        delayTicks: Long,
        key: String,
        fallback: String,
        vararg placeholders: Pair<String, Any?>,
        force: Boolean = false
    ) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val activePlayer = Bukkit.getPlayer(player.uniqueId) ?: return@Runnable
            if (!activePlayer.isOnline) return@Runnable
            val message = ArenaI18n.stringList(activePlayer, key, listOf(fallback), *placeholders).randomOrNull() ?: return@Runnable
            sendOageMessage(activePlayer, key, message, force = force)
        }, delayTicks)
    }

    private fun monitorOageBroadcasts(currentTick: Long) {
        if (currentTick % OAGE_COMBAT_MONITOR_INTERVAL_TICKS != 0L) return

        sessionsByWorld.values.forEach { session ->
            val world = Bukkit.getWorld(session.worldName) ?: return@forEach
            val participants = session.participants
                .asSequence()
                .mapNotNull { Bukkit.getPlayer(it) }
                .filter { it.isOnline && it.world.uid == world.uid && !isPlayerDowned(session, it.uniqueId) && !it.isDead && it.gameMode != GameMode.SPECTATOR }
                .toList()
            if (participants.isEmpty()) return@forEach

            val allInCombat = participants.all { hasMobTargetingParticipant(session, it.uniqueId) }
            val noneInCombat = participants.none { hasMobTargetingParticipant(session, it.uniqueId) }

            when {
                allInCombat -> broadcastOageMessage(
                    session,
                    "arena.messages.oage.combat_all_engaged",
                    listOf("なかなか手強いですね…！")
                )
                noneInCombat && session.startedWaves.contains(1) -> broadcastOageMessage(
                    session,
                    "arena.messages.oage.combat_all_calm",
                    listOf("いい感じです！この調子でがんばりましょう！")
                )
            }
        }
    }

    private fun broadcastStageStartMessage(session: ArenaSession) {
        if (!session.oageAnnouncements.add("stage_start")) return
        broadcastOageMessage(
            session,
            "arena.messages.oage.stage_start",
            listOf("現地に到着しました！おねがいしますね！")
        )
    }

    private fun scheduleWaveClearReminder(session: ArenaSession, wave: Int) {
        if (wave <= 0 || wave >= session.waves) return

        session.waveClearReminderTasks.remove(wave)?.cancel()
        val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (activeSession !== session) return@Runnable
            activeSession.waveClearReminderTasks.remove(wave)
            if (!activeSession.clearedWaves.contains(wave)) return@Runnable
            if (activeSession.startedWaves.contains(wave + 1)) return@Runnable

            broadcastOageMessage(
                activeSession,
                "arena.messages.oage.wave_clear_wait",
                listOf("扉の前のマーカーに近づくと、何かわかりそうです！")
            )
        }, 5L * 60L * 20L)

        session.waveClearReminderTasks[wave] = task
    }

    private fun sendBarrierRestartOageMessages(session: ArenaSession, progress: Double) {
        val remaining = (1.0 - progress).coerceIn(0.0, 1.0)

        if (remaining <= (2.0 / 3.0) && session.oageAnnouncements.add("barrier_restart_remaining_2_3")) {
            broadcastOageMessage(
                session,
                "arena.messages.oage.barrier_restart_remaining_2_3",
                listOf("再起動は順調です！持ちこたえてくださいー！")
            )
        }

        if (remaining <= (1.0 / 3.0) && session.oageAnnouncements.add("barrier_restart_remaining_1_3")) {
            broadcastOageMessage(
                session,
                "arena.messages.oage.barrier_restart_remaining_1_3",
                listOf("あとちょっとです...！")
            )
        }
    }

    private fun playSound(session: ArenaSession, sound: Sound, volume: Float, pitch: Float) {
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            player.playSound(player.location, sound, volume, pitch)
        }
    }

    private fun playSound(session: ArenaSession, soundKey: String, volume: Float, pitch: Float) {
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            player.playSound(player.location, soundKey, volume, pitch)
        }
    }

    private fun playDoorAnimationSound(session: ArenaSession) {
        val theme = themeLoader.getTheme(session.themeId)
        val key = theme?.doorOpenSound?.key ?: arenaDoorAnimationSoundKey
        val pitch = theme?.doorOpenSound?.pitch ?: arenaDoorAnimationSoundPitch
        playSound(session, key, 1.0f, pitch)
    }

    private fun scheduleStartEntranceNormalBgm(session: ArenaSession, delayTicks: Long = 0L) {
        val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (!activeSession.stageStarted || activeSession.barrierRestartCompleted) {
                return@Runnable
            }
            if (activeSession.entranceNormalBgmStarted) {
                return@Runnable
            }
            activeSession.entranceNormalBgmStarted = true

            val currentTick = Bukkit.getCurrentTick().toLong()
            activeSession.participants.forEach { participantId ->
                val player = Bukkit.getPlayer(participantId) ?: return@forEach
                if (!player.isOnline || player.world.name != activeSession.worldName) return@forEach
                val themeName = ArenaI18n.text(player, "arena.theme.${activeSession.themeId}.name", activeSession.themeId)
                player.sendTitle("", "§7« §6$themeName §7»", 10, 60, 10)
                startArenaBgmMode(activeSession, participantId, ArenaBgmMode.NORMAL, currentTick)
            }
        }, delayTicks.coerceAtLeast(0L))
        session.transitionTasks.add(task)
    }

    private fun requestArenaBgmMode(session: ArenaSession, targetMode: ArenaBgmMode, strictNextBoundary: Boolean = false) {
        if (targetMode != ArenaBgmMode.COMBAT) {
            session.arenaWaveStartCombatDelayTask?.cancel()
            session.arenaWaveStartCombatDelayTask = null
        }
        val currentTick = Bukkit.getCurrentTick().toLong()
        session.participants.forEach { participantId ->
            requestArenaBgmModeForParticipant(session, participantId, targetMode, currentTick, strictNextBoundary = strictNextBoundary)
        }
    }

    private fun requestWaveStartCombatBgm(session: ArenaSession) {
        session.arenaWaveStartCombatDelayTask?.cancel()
        session.arenaWaveStartCombatDelayTask = null

        val delayedTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            session.arenaWaveStartCombatDelayTask = null
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            val currentTick = Bukkit.getCurrentTick().toLong()
            activeSession.participants.forEach { participantId ->
                requestWaveStartCombatBgmForParticipant(activeSession, participantId, currentTick)
            }
        }, WAVE_START_COMBAT_BGM_DELAY_TICKS)
        session.arenaWaveStartCombatDelayTask = delayedTask
        session.transitionTasks.add(delayedTask)
    }

    private fun requestWaveStartCombatBgmForParticipant(session: ArenaSession, participantId: UUID, currentTick: Long) {
        val currentMode = participantArenaBgmMode(session, participantId)
        if (currentMode == ArenaBgmMode.COMBAT && session.arenaBgmSwitchRequestByParticipant[participantId] == null) {
            return
        }
        requestArenaBgmModeForParticipant(
            session,
            participantId,
            ArenaBgmMode.COMBAT,
            currentTick,
            strictNextBoundary = true
        )
    }

    private fun isCombatBgmReadyForWaveStart(session: ArenaSession): Boolean {
        val world = Bukkit.getWorld(session.worldName) ?: return false
        val targets = session.participants
            .asSequence()
            .filter { participantId -> !isPlayerDowned(session, participantId) }
            .mapNotNull { participantId -> Bukkit.getPlayer(participantId) }
            .filter { player ->
                player.isOnline &&
                    !player.isDead &&
                    player.gameMode != GameMode.SPECTATOR &&
                    player.world.uid == world.uid
            }
            .toList()
        if (targets.isEmpty()) {
            return true
        }
        return targets.all { player -> isCombatBgmReadyForParticipant(session, player.uniqueId) }
    }

    private fun isCombatBgmReadyForParticipant(session: ArenaSession, participantId: UUID): Boolean {
        val mode = participantArenaBgmMode(session, participantId)
        val request = session.arenaBgmSwitchRequestByParticipant[participantId]
        if (mode != ArenaBgmMode.COMBAT || request != null) {
            return false
        }
        return hasArenaBgmPlayback(session, participantId, ArenaBgmMode.COMBAT)
    }

    private fun requestArenaBgmModeForParticipant(
        session: ArenaSession,
        participantId: UUID,
        targetMode: ArenaBgmMode,
        currentTick: Long,
        strictNextBoundary: Boolean = false
    ) {
        if (session.barrierRestartCompleted && targetMode != ArenaBgmMode.STOPPED) {
            return
        }

        val currentMode = participantArenaBgmMode(session, participantId)
        val currentRequest = session.arenaBgmSwitchRequestByParticipant[participantId]

        if (currentRequest != null &&
            currentRequest.targetMode == targetMode &&
            currentRequest.strictNextBoundary == strictNextBoundary
        ) {
            return
        }

        if (currentMode == targetMode && currentRequest == null) {
            return
        }

        val requestedAtBeat = arenaBgmTrackForMode(currentMode)?.let { track ->
            currentAbsoluteBeat(session, participantId, track)
        }

        session.arenaBgmSwitchRequestByParticipant[participantId] = ArenaBgmSwitchRequest(
            targetMode = targetMode,
            requestedAtBeat = requestedAtBeat,
            strictNextBoundary = strictNextBoundary
        )
    }

    private fun updateArenaBgmTransitions() {
        val currentTick = Bukkit.getCurrentTick().toLong()
        sessionsByWorld.values.forEach { session ->
            val hasAliveCombatMob = hasAnyAliveCombatMob(session)
            if (session.stageStarted && !session.barrierRestarting && !session.barrierRestartCompleted) {
                if (hasAliveCombatMob) {
                    session.hadAliveCombatMobs = true
                    session.combatMobEmptySinceTick = null
                } else if (session.hadAliveCombatMobs) {
                    val emptySinceTick = session.combatMobEmptySinceTick
                    if (emptySinceTick == null) {
                        session.combatMobEmptySinceTick = currentTick
                        return@forEach
                    }
                    if (currentTick - emptySinceTick < 20L) {
                        return@forEach
                    }
                    session.hadAliveCombatMobs = false
                    session.combatMobEmptySinceTick = null
                    requestArenaBgmMode(session, ArenaBgmMode.NORMAL, strictNextBoundary = true)
                }
            }

            session.participants.forEach { participantId ->
                if (isPlayerDowned(session, participantId)) {
                    session.arenaBgmSwitchRequestByParticipant.remove(participantId)
                    return@forEach
                }
                if (!session.stageStarted) {
                    return@forEach
                }
                if (session.barrierRestartCompleted) {
                    processArenaBgmSwitchRequest(session, participantId, currentTick)
                    return@forEach
                }
                refreshArenaBgmPlaybackState(session, participantId)
                processArenaBgmSwitchRequest(session, participantId, currentTick)
            }
        }
    }

    private fun refreshArenaBgmPlaybackState(session: ArenaSession, participantId: UUID) {
        val currentMode = participantArenaBgmMode(session, participantId)
        if (currentMode != ArenaBgmMode.STOPPED && !hasArenaBgmPlayback(session, participantId, currentMode)) {
            session.arenaBgmModeByParticipant[participantId] = ArenaBgmMode.STOPPED
        }
    }

    private fun hasAnyAliveCombatMob(session: ArenaSession): Boolean {
        val trackedMobIds = sequence {
            yieldAll(session.activeMobs)
            yieldAll(session.barrierDefenseMobIds)
        }
        return trackedMobIds.any { mobId ->
            val mob = Bukkit.getEntity(mobId) as? Mob ?: return@any false
            mob.isValid && !mob.isDead && mob.world.name == session.worldName
        }
    }

    private fun hasMobTargetingParticipant(session: ArenaSession, participantId: UUID): Boolean {
        if (!session.participants.contains(participantId)) return false
        if (isPlayerDowned(session, participantId)) return false
        val participant = Bukkit.getPlayer(participantId) ?: return false
        if (!participant.isOnline || participant.isDead || participant.world.name != session.worldName) return false

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
            target.uniqueId == participantId && target.isOnline && !target.isDead && target.world.name == session.worldName
        }
    }

    private fun processArenaBgmSwitchRequest(session: ArenaSession, participantId: UUID, currentTick: Long) {
        val request = session.arenaBgmSwitchRequestByParticipant[participantId] ?: return
        val currentMode = participantArenaBgmMode(session, participantId)
        val player = Bukkit.getPlayer(participantId)
        if (player == null || !player.isOnline || player.world.name != session.worldName) {
            session.arenaBgmSwitchRequestByParticipant.remove(participantId)
            return
        }

        if (currentMode == ArenaBgmMode.STOPPED) {
            session.arenaBgmSwitchRequestByParticipant.remove(participantId)
            if (request.targetMode == ArenaBgmMode.STOPPED) {
                stopArenaBgmForParticipant(session, participantId)
                return
            }

            startArenaBgmMode(session, participantId, ArenaBgmMode.NORMAL, currentTick)
            if (request.targetMode == ArenaBgmMode.COMBAT) {
                requestArenaBgmModeForParticipant(
                    session,
                    participantId,
                    ArenaBgmMode.COMBAT,
                    currentTick,
                    strictNextBoundary = true
                )
            }
            return
        }

        if (!hasArenaBgmPlayback(session, participantId, currentMode)) {
            session.arenaBgmModeByParticipant[participantId] = ArenaBgmMode.STOPPED
            return
        }

        val currentTrack = arenaBgmTrackForMode(currentMode) ?: return
        val absoluteBeatNow = currentAbsoluteBeat(session, participantId, currentTrack)
        if (!isBeatBoundaryReached(currentTrack, absoluteBeatNow, request.requestedAtBeat, request.strictNextBoundary)) {
            return
        }

        session.arenaBgmSwitchRequestByParticipant.remove(participantId)
        val resolvedTargetMode = request.targetMode
        if (resolvedTargetMode == ArenaBgmMode.STOPPED) {
            stopArenaBgmForParticipant(session, participantId)
            return
        }
        if (resolvedTargetMode == currentMode) {
            return
        }

        startArenaBgmMode(session, participantId, resolvedTargetMode, currentTick)
    }

    private fun hasArenaBgmPlayback(session: ArenaSession, participantId: UUID, mode: ArenaBgmMode): Boolean {
        val player = Bukkit.getPlayer(participantId) ?: return false
        if (!player.isOnline || player.world.name != session.worldName) return false
        val track = arenaBgmTrackForMode(mode) ?: return false
        return BGMManager.isPlaying(player, track.soundKey)
    }

    private fun isBeatBoundaryReached(
        track: ArenaBgmTrackConfig,
        absoluteBeatNow: Long,
        requestedAtBeat: Long?,
        strictNextBoundary: Boolean
    ): Boolean {
        val switchInterval = track.switchIntervalBeats.toLong().coerceAtLeast(1L)
        val beatOffset = (absoluteBeatNow - 1L).mod(switchInterval)
        if (beatOffset != 0L) {
            return false
        }
        if (!strictNextBoundary) {
            return true
        }

        val currentBoundary = (absoluteBeatNow - 1L) / switchInterval
        val requestedBoundary = requestedAtBeat
            ?.let { beat -> (beat.coerceAtLeast(1L) - 1L) / switchInterval }
            ?: currentBoundary
        return currentBoundary > requestedBoundary
    }

    private fun scheduleBoundaryStopArenaBgmForPlayer(session: ArenaSession, participantId: UUID, player: Player) {
        val mode = participantArenaBgmMode(session, participantId)
        val track = arenaBgmTrackForMode(mode)
        if (track == null) {
            stopArenaBgmForPlayer(player)
            return
        }

        val absoluteBeatNow = currentAbsoluteBeat(session, participantId, track)
        val switchInterval = track.switchIntervalBeats.toLong().coerceAtLeast(1L)
        val beatOffset = (absoluteBeatNow - 1L).mod(switchInterval)
        val beatsUntilNextBoundary = if (beatOffset == 0L) switchInterval else (switchInterval - beatOffset)
        val delayTicks = (beatsUntilNextBoundary.toDouble() * track.beatTicks)
            .roundToLong()
            .coerceAtLeast(1L)

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.isOnline) {
                stopArenaBgmForPlayer(player)
            }
        }, delayTicks)
    }

    private fun currentAbsoluteBeat(
        session: ArenaSession,
        participantId: UUID,
        track: ArenaBgmTrackConfig
    ): Long {
        val player = Bukkit.getPlayer(participantId) ?: return 1L
        val beatNanos = track.beatTicks * 50_000_000.0
        return BGMManager.getElapsedBeats(player, beatNanos)
    }

    private fun startArenaBgmMode(session: ArenaSession, participantId: UUID, mode: ArenaBgmMode, startTick: Long) {
        val track = arenaBgmTrackForMode(mode) ?: return
        val currentMode = participantArenaBgmMode(session, participantId)
        if (currentMode == mode && hasArenaBgmPlayback(session, participantId, mode)) {
            session.arenaBgmSwitchRequestByParticipant.remove(participantId)
            return
        }
        val player = Bukkit.getPlayer(participantId)
        if (player != null && player.isOnline && player.world.name == session.worldName) {
            stopArenaBgmForPlayer(player)
            BGMManager.playPrecise(player, track.soundKey, track.loopTicks)
        }
        session.arenaBgmModeByParticipant[participantId] = mode
        session.arenaBgmSwitchRequestByParticipant.remove(participantId)
        if (mode == ArenaBgmMode.NORMAL) {
            tryBroadcastPendingWaveClearedMessageOnNormalBgm(session)
        }
    }

    private fun stopArenaBgm(session: ArenaSession) {
        session.arenaWaveStartCombatDelayTask?.cancel()
        session.arenaWaveStartCombatDelayTask = null
        session.participants.forEach { participantId ->
            stopArenaBgmForParticipant(session, participantId)
        }
    }

    private fun stopArenaBgmForParticipant(session: ArenaSession, participantId: UUID) {
        val player = Bukkit.getPlayer(participantId)
        if (player != null && player.isOnline && player.world.name == session.worldName) {
            stopArenaBgmForPlayer(player)
        }
        session.arenaBgmModeByParticipant[participantId] = ArenaBgmMode.STOPPED
        session.arenaBgmSwitchRequestByParticipant.remove(participantId)
    }

    private fun participantArenaBgmMode(session: ArenaSession, participantId: UUID): ArenaBgmMode {
        return session.arenaBgmModeByParticipant[participantId] ?: ArenaBgmMode.STOPPED
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

    private fun prepareArenaWorldPoolAtStartup() {
        readyArenaWorldNames.clear()
        cleanupWorldJobs.clear()
        arenaWorldStates.clear()

        val names = (1..arenaPoolSize).map { "$ARENA_POOL_WORLD_NAME_PREFIX.$it" }
        names.forEach { worldName ->
            if (resetArenaPoolWorld(worldName)) {
                markArenaWorldReady(worldName)
            } else {
                markArenaWorldBroken(worldName)
            }
        }

        plugin.logger.info("[Arena] ワールドプール初期化: ready=${readyArenaWorldNames.size} / total=${names.size}")
    }

    private fun resetArenaPoolWorld(worldName: String): Boolean {
        val loadedWorld = Bukkit.getWorld(worldName)
        if (loadedWorld != null && !Bukkit.unloadWorld(loadedWorld, false)) {
            plugin.logger.warning("[Arena] プールワールドのアンロードに失敗しました: $worldName")
            return false
        }

        val worldFolder = worldFolder(worldName)
        if (worldFolder.exists() && !deleteDirectory(worldFolder)) {
            plugin.logger.warning("[Arena] プールワールドフォルダ削除に失敗しました: $worldName")
            return false
        }

        val created = createArenaWorld(worldName)
        if (created == null) {
            plugin.logger.warning("[Arena] プールワールド生成に失敗しました: $worldName")
            return false
        }

        cleanupNonPlayerEntities(created)
        return true
    }

    private fun acquireArenaPoolWorld(): World? {
        val worldName = readyArenaWorldNames.removeFirstOrNull() ?: return null
        arenaWorldStates[worldName] = ArenaPoolWorldState.IN_USE
        val world = Bukkit.getWorld(worldName)
        if (world != null) {
            return world
        }
        markArenaWorldBroken(worldName)
        return null
    }

    private fun markArenaWorldReady(worldName: String) {
        cleanupWorldJobs.removeAll { it.worldName == worldName }
        readyArenaWorldNames.remove(worldName)
        if (Bukkit.getWorld(worldName) == null) {
            arenaWorldStates[worldName] = ArenaPoolWorldState.BROKEN
            return
        }
        readyArenaWorldNames.addLast(worldName)
        arenaWorldStates[worldName] = ArenaPoolWorldState.READY
    }

    private fun markArenaWorldBroken(worldName: String) {
        readyArenaWorldNames.remove(worldName)
        cleanupWorldJobs.removeAll { it.worldName == worldName }
        arenaWorldStates[worldName] = ArenaPoolWorldState.BROKEN
    }

    private fun createArenaWorld(worldName: String = "arena.${UUID.randomUUID()}"): World? {
        val creator = WorldCreator(worldName)
        creator.generator(VoidChunkGenerator())
        val world = creator.createWorld()
        world?.let { configureArenaVoidWorld(it) }
        return world
    }

    fun createDebugVoidWorld(): World? {
        val world = createArenaWorld() ?: return null
        if (!markDebugVoidWorld(world)) {
            plugin.logger.warning("[Arena] デバッグ用ボイドワールドのマーカー作成に失敗しました: ${world.name}")
            tryDeleteWorld(world)
            return null
        }
        return world
    }

    fun cloneDebugVoidWorld(): World? {
        val templateFolder = ensureDebugVoidWorldTemplate() ?: return null
        val cloneWorldName = "arena.debug.clone.${UUID.randomUUID()}"
        val cloneFolder = worldFolder(cloneWorldName)
        if (cloneFolder.exists()) {
            return null
        }

        if (!templateFolder.copyRecursively(cloneFolder, overwrite = false)) {
            plugin.logger.warning("[Arena] デバッグ用ボイドワールドの複製に失敗しました: ${templateFolder.name} -> $cloneWorldName")
            return null
        }

        removeMarkerFile(cloneFolder, DEBUG_VOID_TEMPLATE_MARKER_FILE_NAME)
        removeMarkerFile(cloneFolder, "uid.dat")
        val world = createArenaWorld(cloneWorldName) ?: run {
            deleteDirectory(cloneFolder)
            return null
        }
        if (!markDebugVoidWorld(world)) {
            plugin.logger.warning("[Arena] 複製ワールドのマーカー作成に失敗しました: ${world.name}")
            tryDeleteWorld(world)
            return null
        }
        return world
    }

    fun ensureDebugVoidWorldBootstrap() {
        ensureDebugVoidWorldTemplate() ?: return
        if (hasDebugVoidWorldOnDisk() || hasDebugVoidWorldLoaded()) {
            return
        }

        createDebugVoidWorld() ?: plugin.logger.warning("[Arena] 起動時のデバッグ用ボイドワールド生成に失敗しました")
    }

    fun isDebugVoidWorld(world: World): Boolean {
        return world.worldFolder.resolve(DEBUG_VOID_WORLD_MARKER_FILE_NAME).isFile
    }

    private fun ensureDebugVoidWorldTemplate(): File? {
        val templateWorld = Bukkit.getWorld(DEBUG_VOID_WORLD_TEMPLATE_NAME)
        if (templateWorld != null) {
            Bukkit.unloadWorld(templateWorld.name, false)
            return templateWorld.worldFolder
        }

        val templateFolder = worldFolder(DEBUG_VOID_WORLD_TEMPLATE_NAME)
        if (templateFolder.exists()) {
            if (!templateFolder.resolve(DEBUG_VOID_TEMPLATE_MARKER_FILE_NAME).isFile) {
                markTemplateMarker(templateFolder)
            }
            return templateFolder
        }

        val created = createArenaWorld(DEBUG_VOID_WORLD_TEMPLATE_NAME) ?: return null
        if (!markTemplateMarker(created.worldFolder)) {
            plugin.logger.warning("[Arena] デバッグテンプレートのマーカー作成に失敗しました: ${created.name}")
        }
        Bukkit.unloadWorld(created, false)
        return templateFolder
    }

    private fun hasDebugVoidWorldLoaded(): Boolean {
        return Bukkit.getWorlds().any { isDebugVoidWorld(it) }
    }

    private fun hasDebugVoidWorldOnDisk(): Boolean {
        return worldContainer().listFiles()?.any { candidate ->
            candidate.isDirectory && candidate.resolve(DEBUG_VOID_WORLD_MARKER_FILE_NAME).isFile
        } == true
    }

    fun deleteDebugVoidWorld(world: World): Boolean {
        if (!isDebugVoidWorld(world)) {
            return false
        }
        if (sessionsByWorld.containsKey(world.name)) {
            return false
        }
        tryDeleteWorld(world)
        return true
    }

    private fun markTemplateMarker(folder: File): Boolean {
        return runCatching {
            val markerFile = folder.resolve(DEBUG_VOID_TEMPLATE_MARKER_FILE_NAME)
            if (!markerFile.exists()) {
                markerFile.createNewFile()
            } else {
                true
            }
        }.getOrDefault(false)
    }

    private fun markDebugVoidWorld(world: World): Boolean {
        return runCatching {
            val markerFile = world.worldFolder.resolve(DEBUG_VOID_WORLD_MARKER_FILE_NAME)
            if (!markerFile.exists()) {
                markerFile.createNewFile()
            } else {
                true
            }
        }.getOrDefault(false)
    }

    private fun configureArenaVoidWorld(world: World) {
        world.apply {
            setGameRule(GameRule.MAX_ENTITY_CRAMMING, 0)
            setGameRule(GameRule.DO_MOB_SPAWNING, false)
            setGameRule(GameRule.DO_TILE_DROPS, false)
            setGameRule(GameRule.DO_MOB_LOOT, false)
            setGameRule(GameRule.MOB_GRIEFING, false)
            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
            setGameRule(GameRule.DO_WEATHER_CYCLE, false)
            setGameRule(GameRule.KEEP_INVENTORY, true)
            time = 6000
            WorldSettingsHelper.applyDistanceSettings(plugin, this, "arena.world_settings")
        }
    }

    private fun removeMarkerFile(folder: File, fileName: String) {
        runCatching { folder.resolve(fileName).delete() }
    }

    private fun worldFolder(worldName: String): File {
        return File(worldContainer(), worldName)
    }

    private fun worldContainer(): File {
        return Bukkit.getWorldContainer()
    }

    private fun elapsedMillisSince(startedAtNanos: Long): Long {
        return ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)
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

            cleanupUnavailableInvitedParticipants(session, owner)

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
                    waitingPlayer.sendTitle(ArenaI18n.text(waitingPlayer, "arena.messages.multiplayer.stage_wait_title", "§7アリーナを準備中..."), "", 0, 60, 0)
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

    private fun cleanupUnavailableInvitedParticipants(session: ArenaSession, owner: Player) {
        val maxDistanceSquared = multiplayerInviteAutoDeclineDistance * multiplayerInviteAutoDeclineDistance
        session.invitedParticipants.toList().forEach { invitedId ->
            val invited = Bukkit.getPlayer(invitedId)
            if (invited == null || !invited.isOnline) {
                removeInvitedParticipant(session, invitedId)
                owner.sendMessage(
                    ArenaI18n.text(
                        owner,
                        "arena.messages.multiplayer.invite_cancelled_offline",
                        "&e招待していたプレイヤーがログアウトしたため、招待を自動辞退にしました"
                    )
                )
                return@forEach
            }
            if (invited.world.uid != owner.world.uid) {
                removeInvitedParticipant(session, invitedId)
                invited.sendMessage(
                    ArenaI18n.text(
                        invited,
                        "arena.messages.multiplayer.invite_auto_declined_far",
                        "&e招待者から離れたため、招待を自動辞退しました"
                    )
                )
                owner.sendMessage(
                    ArenaI18n.text(
                        owner,
                        "arena.messages.multiplayer.invite_auto_declined_far_owner",
                        "&e{player} が離れたため、招待を自動辞退にしました",
                        "player" to invited.name
                    )
                )
                return@forEach
            }

            if (invited.location.distanceSquared(owner.location) > maxDistanceSquared) {
                removeInvitedParticipant(session, invitedId)
                invited.sendMessage(
                    ArenaI18n.text(
                        invited,
                        "arena.messages.multiplayer.invite_auto_declined_far",
                        "&e招待者から離れたため、招待を自動辞退しました"
                    )
                )
                owner.sendMessage(
                    ArenaI18n.text(
                        owner,
                        "arena.messages.multiplayer.invite_auto_declined_far_owner",
                        "&e{player} が離れたため、招待を自動辞退にしました",
                        "player" to invited.name
                    )
                )
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
            ownerBar.name(legacySerializer.deserialize(ArenaI18n.text(owner, "arena.bossbar.join_countdown", "§f開始まで §e{seconds} 秒", "seconds" to formatRemainingSeconds(ownerRemaining))))
            ownerBar.progress(ownerProgress)
            owner.showBossBar(ownerBar)
        }

        session.invitedParticipants.forEach { invitedId ->
            val invited = Bukkit.getPlayer(invitedId) ?: return@forEach
            if (!invited.isOnline) return@forEach

            val remaining = (session.joinGraceEndMillis - nowMillis).coerceAtLeast(0L)
            val progress = if (session.joinGraceDurationMillis <= 0L) {
                0.0f
            } else {
                (remaining.toDouble() / session.joinGraceDurationMillis.toDouble()).toFloat().coerceIn(0.0f, 1.0f)
            }
            val bar = getOrCreateJoinCountdownBossBar(session, invitedId)
            bar.name(legacySerializer.deserialize(ArenaI18n.text(invited, "arena.bossbar.join_countdown", "§f開始まで §e{seconds} 秒", "seconds" to formatRemainingSeconds(remaining))))
            bar.progress(progress)
            invited.showBossBar(bar)
        }
    }

    private fun getOrCreateJoinCountdownBossBar(session: ArenaSession, playerId: UUID): BossBar {
        return session.joinCountdownBossBars.getOrPut(playerId) {
            BossBar.bossBar(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.join_countdown", "§f開始まで §e{seconds} 秒", "seconds" to "0")), 1.0f, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_12)
        }
    }

    private fun formatRemainingSeconds(remainingMillis: Long): String {
        val seconds = (remainingMillis.coerceAtLeast(0L) / 1000L).coerceAtLeast(0L)
        return seconds.toString()
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
                session.liftMarkerLocations.any { markerLocation ->
                    isInsideLiftArea(markerLocation, player.location)
                }
            }
            .map { it.uniqueId }
            .toSet()

        val entered = waitingNow - session.waitingParticipants
        val exited = session.waitingParticipants - waitingNow
        session.waitingParticipants.clear()
        session.waitingParticipants.addAll(waitingNow)

        entered.forEach { playerId ->
            session.waitingNotifiedParticipants.add(playerId)
            Bukkit.getPlayer(playerId)?.takeIf { it.isOnline }?.let { player ->
                player.playSound(player.location, Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f)
            }
        }

        val currentTick = Bukkit.getCurrentTick().toLong()
        val withinGrace = System.currentTimeMillis() < session.joinGraceEndMillis
        if (withinGrace) {
            waitingNow.forEach { playerId ->
                val nextTick = session.waitingSubtitleNextTickByPlayer[playerId] ?: 0L
                if (currentTick < nextTick) return@forEach
                val player = Bukkit.getPlayer(playerId) ?: return@forEach
                if (!player.isOnline) return@forEach
                player.sendTitle("", ArenaI18n.text(player, "arena.messages.multiplayer.waiting_title", "§7参加待機中"), 0, 25, 5)
                session.waitingSubtitleNextTickByPlayer[playerId] = currentTick + 20L
            }
        }

        exited.forEach { playerId ->
            session.waitingNotifiedParticipants.remove(playerId)
            session.waitingSubtitleNextTickByPlayer.remove(playerId)
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            player.sendMessage(
                ArenaI18n.text(
                    player,
                    "arena.messages.multiplayer.waiting_exited",
                    "§cリフトから降りました。参加する場合は乗って待機してください！"
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
            session.originalGameModes.putIfAbsent(player.uniqueId, player.gameMode)
            session.sidebarParticipantNames[player.uniqueId] = player.name
        }
        session.sidebarParticipantOrder.clear()
        session.sidebarParticipantOrder.add(session.ownerPlayerId)
        session.sidebarParticipantOrder.addAll(
            participants
                .asSequence()
                .filter { it.uniqueId != session.ownerPlayerId }
                .map { it.uniqueId }
                .toList()
        )
        clearMultiplayerRecruitmentState(session)
        session.multiplayerJoinEnabled = false
        transitionSessionPhase(session, ArenaPhase.PREPARING)
        session.joinGraceEndMillis = 0L

        startMultiplayerStageIntro(session, participants)
    }

    private fun startMultiplayerStageIntro(session: ArenaSession, participants: List<Player>) {
        if (session.multiplayerJoinIntroStarted) {
            return
        }
        session.multiplayerJoinIntroStarted = true

        val stageWorld = Bukkit.getWorld(session.worldName) ?: run {
            terminateSession(session, false)
            return
        }
        val owner = participants.firstOrNull { it.uniqueId == session.ownerPlayerId }
        val introWorld = owner?.world ?: participants.firstOrNull()?.world ?: run {
            terminateSession(session, false)
            return
        }
        val liftTemplate = resolveEntranceLiftTemplate() ?: run {
            terminateSession(
                session,
                false,
                messageKey = "arena.messages.command.start_error.lift_not_ready",
                fallbackMessage = "&cリフトの準備ができていないため開始できません"
            )
            return
        }
        if (session.liftMarkerLocations.isEmpty()) {
            terminateSession(
                session,
                false,
                messageKey = "arena.messages.command.start_error.lift_not_ready",
                fallbackMessage = "&cリフトの準備ができていないため開始できません"
            )
            return
        }

        val referenceLocation = owner
            ?.takeIf { it.world.uid == introWorld.uid }
            ?.location
            ?: participants.firstOrNull { it.world.uid == introWorld.uid }?.location
            ?: introWorld.spawnLocation

        val availableMarkers = session.liftMarkerLocations
            .asSequence()
            .filter { marker -> marker.world?.uid == introWorld.uid }
            .map { it.clone() }
            .toList()
        if (availableMarkers.isEmpty()) {
            terminateSession(
                session,
                false,
                messageKey = "arena.messages.command.start_error.lift_not_ready",
                fallbackMessage = "&cリフトの準備ができていないため開始できません"
            )
            return
        }

        val baseMarker = availableMarkers
            .minByOrNull {
                it.distanceSquared(referenceLocation)
            }
            ?: run {
                terminateSession(
                    session,
                    false,
                    messageKey = "arena.messages.command.start_error.lift_not_ready",
                    fallbackMessage = "&cリフトの準備ができていないため開始できません"
                )
                return
            }

        val baseLocation = baseMarker.block.location.clone().apply { this.world = introWorld }
        val maxRise = entranceLiftMaxRiseBlocks
        retainEntranceLiftChunkTickets(session, introWorld, baseLocation, liftTemplate, maxRise)

        data class LiftPlayerUnit(
            val player: Player,
            val offsetX: Double,
            val offsetZ: Double
        )

        val playerOffsets = buildEntranceLiftSeatOffsets(participants.size)
        var currentRise = 0
        var peakHoldTicksRemaining = 0L
        var initialLiftPlaced = false

        val playerUnits = mutableListOf<LiftPlayerUnit>()

        participants.forEachIndexed { index, player ->
            session.arenaPreparingUntilMillisByParticipant[player.uniqueId] = Long.MAX_VALUE
            session.entranceLiftLockedParticipants.add(player.uniqueId)

            val offset = playerOffsets[index]
            playerUnits += LiftPlayerUnit(player, offset.first, offset.second)
        }

        val originalSpeeds = mutableMapOf<UUID, Pair<Float, Double>>()
        participants.forEach { player ->
            val walkSpeed = player.walkSpeed
            val jumpStrength = player.getAttribute(Attribute.JUMP_STRENGTH)?.value ?: 1.0
            originalSpeeds[player.uniqueId] = walkSpeed to jumpStrength
            player.walkSpeed = 0f
            player.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = 0.0
        }

        session.entranceLiftTask?.cancel()
        var transferred = false
        var descending = false

        session.entranceLiftTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName]
            if (activeSession !== session) {
                session.entranceLiftTask?.cancel()
                session.entranceLiftTask = null
                clearLiftFootprint(introWorld, baseLocation.clone().add(0.0, currentRise.toDouble(), 0.0), liftTemplate)
                restoreLiftChainsInRange(introWorld, baseLocation, liftTemplate, baseLocation.blockY, baseLocation.blockY + maxRise)
                releaseEntranceLiftChunkTickets(session)
                restorePlayerMovement(originalSpeeds)
                return@Runnable
            }

            playEntranceLiftTickSound(introWorld, entranceLiftSoundLocation(baseLocation, liftTemplate, currentRise))

            if (!initialLiftPlaced) {
                restoreLiftChainsInRange(introWorld, baseLocation, liftTemplate, baseLocation.blockY, baseLocation.blockY + maxRise)
                placeLiftStructure(introWorld, baseLocation, liftTemplate)
                initialLiftPlaced = true
                return@Runnable
            }

            val transferRise = entranceLiftTransferRiseBlocks.coerceIn(0, maxRise)

            if (!transferred && currentRise >= transferRise) {
                transferred = true
                teleportLiftParticipantsToStage(session, participants, stageWorld)
                participants.forEach { player ->
                    session.arenaPreparingUntilMillisByParticipant.remove(player.uniqueId)
                    session.entranceLiftLockedParticipants.remove(player.uniqueId)
                }
                restorePlayerMovement(originalSpeeds)
                session.participantSpawnProtectionUntilMillis = System.currentTimeMillis() + 4000L
                setDoorActionMarkersReadySilently(session, 1)
                broadcastStageStartMessage(session)
            }

            if (!descending && currentRise >= maxRise) {
                if (peakHoldTicksRemaining <= 0L) {
                    peakHoldTicksRemaining = 40L
                }
                peakHoldTicksRemaining -= entranceLiftIntervalTicks.coerceAtLeast(1L)
                if (peakHoldTicksRemaining > 0L) {
                    return@Runnable
                }
                descending = true
            }

            val oldRise = currentRise
            val delta = if (descending) -1 else 1
            currentRise += delta

            if (descending) {
                val oldTopY = baseLocation.blockY + oldRise + liftTemplate.sizeY - 1
                clearLiftLayer(introWorld, baseLocation, liftTemplate, oldTopY)
                val chainMinY = baseLocation.blockY + oldRise + liftTemplate.sizeY - 1
                val chainMaxY = baseLocation.blockY + oldRise + liftTemplate.sizeY
                val chainCeiling = baseLocation.blockY + maxRise
                for (y in chainMinY..chainMaxY) {
                    if (y <= chainCeiling) {
                        placeCornerChainsAtY(introWorld, baseLocation, liftTemplate, y)
                    }
                }
            } else {
                val oldBottomY = baseLocation.blockY + oldRise
                clearLiftLayer(introWorld, baseLocation, liftTemplate, oldBottomY)
            }

            val nextOrigin = baseLocation.clone().add(0.0, currentRise.toDouble(), 0.0)
            placeLiftStructure(introWorld, nextOrigin, liftTemplate)

            if (descending && currentRise <= 0) {
                restoreLiftChainsInRange(introWorld, baseLocation, liftTemplate, baseLocation.blockY, baseLocation.blockY + maxRise)
                session.entranceLiftTask?.cancel()
                session.entranceLiftTask = null
                restorePlayerMovement(originalSpeeds)
                releaseEntranceLiftChunkTickets(session)
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    releaseOccupiedLiftMarkers(session)
                    notifyLiftOccupiedWaiters()
                }, 40L)
                return@Runnable
            }

            playerUnits.forEach { unit ->
                if (!unit.player.isOnline) return@forEach
                if (unit.player.world.uid != introWorld.uid) return@forEach

                val targetLocation = entranceLiftSeatLocation(baseLocation, liftTemplate, currentRise, unit.offsetX, unit.offsetZ)
                val synchronizedLocation = targetLocation.clone().apply {
                    val current = unit.player.location
                    yaw = current.yaw
                    pitch = current.pitch
                }
                unit.player.teleport(synchronizedLocation)
            }
        }, 0L, entranceLiftIntervalTicks)
    }

    private fun restorePlayerMovement(originalSpeeds: Map<UUID, Pair<Float, Double>>) {
        originalSpeeds.forEach { (playerId, speeds) ->
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            player.walkSpeed = speeds.first
            player.getAttribute(Attribute.JUMP_STRENGTH)?.baseValue = speeds.second
        }
    }

    private fun entranceLiftSoundLocation(baseLocation: Location, template: EntranceLiftTemplate, rise: Int): Location {
        return baseLocation.clone().add(
            template.sizeX.toDouble() / 2.0,
            rise.toDouble(),
            template.sizeZ.toDouble() / 2.0
        )
    }

    private fun playEntranceLiftTickSound(world: World, location: Location) {
        world.playSound(location, "minecraft:item.armor.equip_netherite", 1.0f, 0.5f)
    }

    private fun resolveEntranceLiftTemplate(): EntranceLiftTemplate? {
        cachedEntranceLiftTemplate?.let { return it }
        return resolveLiftTemplate(ENTRANCE_LIFT_STRUCTURE_PATH)?.also {
            cachedEntranceLiftTemplate = it
        }
    }

    private fun resolveLiftTemplate(path: String): EntranceLiftTemplate? {
        val file = File(plugin.dataFolder, path)
        if (!file.exists()) {
            return null
        }
        val loaded = runCatching {
            Bukkit.getStructureManager().loadStructure(file)
        }.getOrNull() ?: return null
        val size = loaded.size
        if (size.blockX <= 0 || size.blockY <= 0 || size.blockZ <= 0) {
            return null
        }
        return EntranceLiftTemplate(
            sizeX = size.blockX,
            sizeY = size.blockY,
            sizeZ = size.blockZ,
            structure = loaded
        )
    }

    private fun placeLiftStructure(
        world: World,
        origin: Location,
        template: EntranceLiftTemplate
    ) {
        template.structure.place(
            origin.clone().apply { this.world = world },
            false,
            StructureRotation.NONE,
            Mirror.NONE,
            0,
            1.0f,
            java.util.Random()
        )
    }

    private fun clearLiftLayer(
        world: World,
        baseLocation: Location,
        template: EntranceLiftTemplate,
        y: Int
    ) {
        val minX = baseLocation.blockX
        val maxX = minX + template.sizeX - 1
        val minZ = baseLocation.blockZ
        val maxZ = minZ + template.sizeZ - 1
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                val block = world.getBlockAt(x, y, z)
                if (block.type != Material.AIR) {
                    block.setType(Material.AIR, false)
                }
            }
        }
    }

    private fun clearLiftFootprint(
        world: World,
        origin: Location,
        template: EntranceLiftTemplate
    ) {
        val minX = origin.blockX
        val minY = origin.blockY
        val minZ = origin.blockZ
        val maxX = minX + template.sizeX - 1
        val maxY = minY + template.sizeY - 1
        val maxZ = minZ + template.sizeZ - 1
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val block = world.getBlockAt(x, y, z)
                    if (block.type != Material.AIR) {
                        block.setType(Material.AIR, false)
                    }
                }
            }
        }
    }

    private fun placeCornerChainsAtY(
        world: World,
        baseLocation: Location,
        template: EntranceLiftTemplate,
        y: Int
    ) {
        val corners = listOf(
            Pair(baseLocation.blockX, baseLocation.blockZ),
            Pair(baseLocation.blockX + template.sizeX - 1, baseLocation.blockZ),
            Pair(baseLocation.blockX, baseLocation.blockZ + template.sizeZ - 1),
            Pair(baseLocation.blockX + template.sizeX - 1, baseLocation.blockZ + template.sizeZ - 1)
        )
        corners.forEach { (x, z) ->
            val block = world.getBlockAt(x, y, z)
            if (block.type == Material.AIR) {
                block.setType(Material.CHAIN, false)
            }
        }
    }

    private fun restoreLiftChainsInRange(
        world: World,
        baseLocation: Location,
        template: EntranceLiftTemplate,
        minY: Int,
        maxY: Int
    ) {
        for (y in minY..maxY) {
            placeCornerChainsAtY(world, baseLocation, template, y)
        }
    }

    private fun retainEntranceLiftChunkTickets(
        session: ArenaSession,
        world: World,
        baseLocation: Location,
        liftTemplate: EntranceLiftTemplate,
        maxRise: Int
    ) {
        releaseEntranceLiftChunkTickets(session)

        val footprintSizeX = liftTemplate.sizeX
        val footprintSizeZ = liftTemplate.sizeZ
        val minX = baseLocation.blockX
        val minZ = baseLocation.blockZ
        val maxX = minX + footprintSizeX - 1
        val maxZ = minZ + footprintSizeZ - 1

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                val chunkX = x shr 4
                val chunkZ = z shr 4
                if (world.addPluginChunkTicket(chunkX, chunkZ, plugin)) {
                    world.getChunkAt(chunkX, chunkZ).load(true)
                }
                session.entranceLiftChunkTicketKeys += encodeChunkKey(chunkX, chunkZ)
            }
        }
        session.entranceLiftChunkTicketWorldName = world.name
    }

    private fun releaseEntranceLiftChunkTickets(session: ArenaSession) {
        val worldName = session.entranceLiftChunkTicketWorldName ?: return
        val world = Bukkit.getWorld(worldName)
        if (world != null) {
            session.entranceLiftChunkTicketKeys.forEach { key ->
                val chunkX = decodeChunkKeyX(key)
                val chunkZ = decodeChunkKeyZ(key)
                world.removePluginChunkTicket(chunkX, chunkZ, plugin)
            }
        }
        session.entranceLiftChunkTicketKeys.clear()
        session.entranceLiftChunkTicketWorldName = null
    }

    private fun encodeChunkKey(chunkX: Int, chunkZ: Int): Long {
        return (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xffffffffL)
    }

    private fun decodeChunkKeyX(key: Long): Int {
        return (key shr 32).toInt()
    }

    private fun decodeChunkKeyZ(key: Long): Int {
        return key.toInt()
    }

    private fun buildEntranceLiftSeatOffsets(size: Int): List<Pair<Double, Double>> {
        if (size <= 1) return listOf(0.0 to 0.0)
        val spacing = 0.55
        val side = kotlin.math.ceil(kotlin.math.sqrt(size.toDouble())).toInt().coerceAtLeast(1)
        val center = (side - 1) / 2.0
        return buildList {
            for (index in 0 until size) {
                val row = index / side
                val col = index % side
                val x = (col - center) * spacing
                val z = (row - center) * spacing
                add(x to z)
            }
        }
    }
 
    private fun entranceLiftSeatLocation(
        baseLocation: Location,
        template: EntranceLiftTemplate,
        rise: Int,
        offsetX: Double,
        offsetZ: Double
    ): Location {
        return baseLocation.clone().add(
            template.sizeX.toDouble() / 2.0 + offsetX,
            rise.toDouble() + 1.0,
            template.sizeZ.toDouble() / 2.0 + offsetZ
        )
    }

    private fun teleportLiftParticipantsToStage(session: ArenaSession, participants: List<Player>, world: World) {
        val now = System.currentTimeMillis()
        participants.forEach { player ->
            if (!player.isOnline) return@forEach
            val spawnLocation = session.entranceLocation.clone().apply {
                this.world = world
            }
            world.getChunkAt(spawnLocation.blockX shr 4, spawnLocation.blockZ shr 4).load(true)
            applyStageStartFacingYaw(session, spawnLocation)
            player.teleport(spawnLocation)
            player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, STAGE_TRANSFER_BLINDNESS_TICKS, 0, false, false, false))
            applySessionGameMode(session, player)
            playStageEntrySoundsLater(player)
            session.participantLocationHistory[player.uniqueId] = ArrayDeque<TimedPlayerLocation>().apply {
                addLast(TimedPlayerLocation(now, spawnLocation.clone()))
            }
            session.participantLastSampleMillis[player.uniqueId] = now
        }
    }

    private fun findNearbyLiftMarkers(origin: Location, radius: Double): List<Location> {
        val world = origin.world ?: return emptyList()
        return world.getNearbyEntities(origin, radius, radius, radius)
            .asSequence()
            .filterIsInstance<Marker>()
            .filter { marker -> marker.scoreboardTags.contains("arena.marker.lift") }
            .map { it.location.clone() }
            .toList()
    }

    private fun liftMarkerKey(location: Location): String {
        val world = location.world ?: return "unknown:${location.blockX}:${location.blockY}:${location.blockZ}"
        return "${world.uid}:${location.blockX}:${location.blockY}:${location.blockZ}"
    }

    private fun releaseOccupiedLiftMarkers(session: ArenaSession) {
        session.liftMarkerLocations.forEach { loc ->
            liftOccupiedMarkerKeys.remove(liftMarkerKey(loc))
        }
    }

    private fun notifyLiftOccupiedWaiters() {
        if (liftOccupiedWaiters.isEmpty()) return
        val waiters = liftOccupiedWaiters.toList()
        liftOccupiedWaiters.clear()
        waiters.forEach { playerId ->
            val player = Bukkit.getPlayer(playerId)
            if (player != null && player.isOnline) {
                OageMessageSender.send(
                    player,
                    ArenaI18n.text(
                        player,
                        "arena.messages.oage.lift_occupied_done",
                        "§f「リフトの準備が終わりました！」"
                    ),
                    plugin
                )
            }
        }
    }

    private fun isEntranceLiftReady(liftMarkers: List<Location>): Boolean {
        if (liftMarkers.isEmpty()) {
            return false
        }
        val template = resolveEntranceLiftTemplate() ?: return false
        return template.sizeX > 0 && template.sizeY > 0 && template.sizeZ > 0
    }

    private fun findLoadedLobbyMarkers(world: World): List<Location> {
        return world.getEntitiesByClass(Marker::class.java)
            .asSequence()
            .filter { marker -> marker.scoreboardTags.contains("arena.marker.lobby") }
            .map { marker -> marker.location.clone() }
            .toList()
    }

    private fun isInsideLiftArea(markerLocation: Location, playerLocation: Location): Boolean {
        val markerWorld = markerLocation.world ?: return false
        if (playerLocation.world?.uid != markerWorld.uid) return false

        val template = resolveEntranceLiftTemplate() ?: return false

        val blockX = markerLocation.blockX
        val blockY = markerLocation.blockY
        val blockZ = markerLocation.blockZ

        val x = playerLocation.x
        val y = playerLocation.y
        val z = playerLocation.z

        return x >= blockX && x < blockX + template.sizeX.toDouble() &&
            z >= blockZ && z < blockZ + template.sizeZ.toDouble() &&
            y >= blockY - 1.0 && y < blockY + template.sizeY.toDouble() + 1.0
    }

    private fun updateActionMarkers() {
        val currentTick = Bukkit.getCurrentTick().toLong()
        sessionsByWorld.values.forEach { session ->
            val world = Bukkit.getWorld(session.worldName) ?: return@forEach
            if (session.actionMarkers.isEmpty()) return@forEach

            val participants = session.participants
                .asSequence()
                .mapNotNull { Bukkit.getPlayer(it) }
                .filter { it.isOnline && it.world.uid == world.uid && !isPlayerDowned(session, it.uniqueId) }
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

            session.actionMarkerHoldStates.entries.removeIf { entry ->
                !session.participants.contains(entry.key) || isPlayerDowned(session, entry.key)
            }
        }
    }

    private fun updateBarrierReturnHoldStates(currentTick: Long) {
        for (session in sessionsByWorld.values) {
            if (!session.barrierRestartCompleted) {
                if (session.barrierReturnHoldTicksByParticipant.isNotEmpty()) {
                    session.barrierReturnHoldTicksByParticipant.clear()
                    session.barrierReturnSubtitleNextTickByParticipant.clear()
                }
                continue
            }

            for (participantId in session.participants.toList()) {
                val player = Bukkit.getPlayer(participantId)
                if (player == null || !player.isOnline) {
                    session.barrierReturnHoldTicksByParticipant.remove(participantId)
                    session.barrierReturnSubtitleNextTickByParticipant.remove(participantId)
                    continue
                }

                if (!player.isSneaking) {
                    session.barrierReturnHoldTicksByParticipant.remove(participantId)
                    session.barrierReturnSubtitleNextTickByParticipant.remove(participantId)
                    continue
                }

                val holdTicks = (session.barrierReturnHoldTicksByParticipant[participantId] ?: 0) + 1
                session.barrierReturnHoldTicksByParticipant[participantId] = holdTicks

                val nextSubtitleTick = session.barrierReturnSubtitleNextTickByParticipant[participantId] ?: 0L
                if (currentTick >= nextSubtitleTick) {
                    val remainingSeconds = ((BARRIER_RETURN_HOLD_TICKS - holdTicks).coerceAtLeast(0)).toDouble() / 20.0
                    val formatted = String.format(Locale.US, "%.1f", remainingSeconds)
                    player.sendTitle(
                        "",
                        ArenaI18n.text(
                            player,
                            "arena.messages.barrier.return_countdown",
                            "§7帰還中 §e{seconds} 秒",
                            "seconds" to formatted
                        ),
                        0,
                        6,
                        0
                    )
                    session.barrierReturnSubtitleNextTickByParticipant[participantId] = currentTick + 2L
                }

                if (holdTicks < BARRIER_RETURN_HOLD_TICKS) {
                    continue
                }

                session.barrierReturnHoldTicksByParticipant.remove(participantId)
                session.barrierReturnSubtitleNextTickByParticipant.remove(participantId)
                stopSessionToLobbyById(
                    participantId,
                    ArenaI18n.text(player, "arena.messages.barrier.returned", "&aロビーへ帰還しました")
                )
            }
        }
    }

    private fun updateActionMarkerHoldState(session: ArenaSession, player: Player, currentTick: Long) {
        val marker = findHoldableActionMarker(session, player.location)
        if (marker?.type == ArenaActionMarkerType.DOOR_TOGGLE) {
            player.sendActionBar(
                Component.text(
                    ArenaI18n.text(
                        player,
                        "arena.messages.door.open_hint",
                        "Shift長押しで扉を開く"
                    )
                )
            )
        }
        if (!player.isSneaking || marker == null) {
            val holdState = session.actionMarkerHoldStates[player.uniqueId]
            if (!player.isSneaking && holdState != null && holdState.heldTicks >= 20) {
                player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 2.0f)
            }
            resetActionMarkerHoldState(session, player.uniqueId, currentTick)
            return
        }

        val anotherPlayerHolding = session.actionMarkerHoldStates.entries.any { (holderId, holdState) ->
            holderId != player.uniqueId && holdState.markerId == marker.id && holdState.heldTicks > 0
        }
        if (anotherPlayerHolding) {
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

    private fun updateArenaSidebars() {
        val activeViewers = linkedSetOf<UUID>()
        sessionsByWorld.values.toList().forEach { session ->
            if (session.multiplayerJoinEnabled) {
                val viewerIds = linkedSetOf<UUID>()
                viewerIds += session.ownerPlayerId
                viewerIds += session.invitedParticipants
                val lines = buildRecruitmentSidebarLines(session)
                viewerIds.forEach { playerId ->
                    val player = Bukkit.getPlayer(playerId) ?: return@forEach
                    if (!player.isOnline) return@forEach
                    renderArenaSidebar(player, lines)
                    activeViewers += playerId
                }
                return@forEach
            }

            val lines = buildBattleSidebarLines(session)
            session.participants.forEach { playerId ->
                val player = Bukkit.getPlayer(playerId) ?: return@forEach
                if (!player.isOnline) return@forEach
                renderArenaSidebar(player, lines)
                activeViewers += playerId
            }
        }

        (arenaSidebarPlayers - activeViewers).toList().forEach { playerId ->
            removeArenaSidebar(playerId)
        }
    }

    private fun buildRecruitmentSidebarLines(session: ArenaSession): List<String> {
        val now = System.currentTimeMillis()
        val remainingSeconds = (session.joinGraceEndMillis - now).coerceAtLeast(0L) / 1000L
        val questTitle = session.inviteQuestTitle?.takeIf { it.isNotBlank() } ?: ArenaI18n.text(null, "arena.ui.recruitment.default_quest_title", "アリーナクエスト")

        val lines = mutableListOf<String>()
        lines += ""
        lines += ArenaI18n.text(null, "arena.ui.recruitment.title", "§6【{questTitle}】", "questTitle" to questTitle)
        lines += ""
        lines += ArenaI18n.text(null, "arena.ui.recruitment.remaining", "§f開始まで §e{seconds} 秒", "seconds" to remainingSeconds)
        lines += ""

        val playerIds = linkedSetOf<UUID>()
        playerIds += session.ownerPlayerId
        playerIds += session.invitedParticipants
        playerIds.forEach { playerId ->
            val name = Bukkit.getPlayer(playerId)?.name
                ?: session.sidebarParticipantNames[playerId]
                ?: "Unknown"
            val inWaitingArea = session.waitingParticipants.contains(playerId)
            lines += if (inWaitingArea) {
                ArenaI18n.text(null, "arena.ui.recruitment.waiting_participant", "§b✦ {name}", "name" to name)
            } else {
                ArenaI18n.text(null, "arena.ui.recruitment.normal_participant", "§7✧ {name}", "name" to name)
            }
        }
        lines += ""
        return lines
    }

    private fun buildBattleSidebarLines(session: ArenaSession): List<String> {
        val inGetReady = !session.stageStarted || session.startedWaves.isEmpty()
        val sidebarWave = if (inGetReady) null else sidebarDisplayWave(session)
        val lines = mutableListOf<String>()
        lines += ""
        lines += when {
            session.phase == ArenaPhase.GAME_OVER -> ArenaI18n.text(null, "arena.ui.sidebar.defeat", "§c§lDEFEAT")
            inGetReady -> ArenaI18n.text(null, "arena.ui.sidebar.get_ready", "§7§lGet Ready!")
            else -> buildWaveSidebarHeader(session, sidebarWave ?: session.currentWave.coerceAtLeast(1))
        }
        lines += ""

        val participantOrder = if (session.sidebarParticipantOrder.isNotEmpty()) {
            session.sidebarParticipantOrder
        } else {
            session.participants.toList()
        }

        participantOrder.forEach { playerId ->
            val name = Bukkit.getPlayer(playerId)?.name
                ?: session.sidebarParticipantNames[playerId]
                ?: "Unknown"
            val status = resolveSidebarParticipantStatus(session, playerId)
            lines += ArenaI18n.text(null, "arena.ui.sidebar.participant_line", "§7◯ §b{name} {status}", "name" to name, "status" to status)
        }
        lines += ""
        return lines
    }

    private fun sidebarDisplayWave(session: ArenaSession): Int {
        val started = session.startedWaves.maxOrNull()
        return (started ?: session.currentWave).coerceIn(1, session.waves)
    }

    private fun buildWaveSidebarHeader(session: ArenaSession, wave: Int): String {
        val base = if (wave >= session.waves) {
            ArenaI18n.text(null, "arena.ui.sidebar.last_wave", "§7» §6§lLast Wave §7«")
        } else {
            ArenaI18n.text(null, "arena.ui.sidebar.wave", "§7» §6§lWave {wave} §7«", "wave" to wave)
        }
        return if (session.clearedWaves.contains(wave)) {
            "$base ${ArenaI18n.text(null, "arena.ui.sidebar.clear", "§dCLEAR")}" 
        } else {
            base
        }
    }

    private fun resolveSidebarParticipantStatus(session: ArenaSession, playerId: UUID): String {
        if (isSidebarPreparing(session, playerId)) {
            return ArenaI18n.text(null, "arena.ui.sidebar.status_preparing", "§dPREPARING")
        }

        val downState = session.downedPlayers[playerId]
        if (downState != null) {
            if (downState.reviveDisabled) {
                return ArenaI18n.text(null, "arena.ui.sidebar.status_dead", "§cDEAD")
            }
            return ArenaI18n.text(null, "arena.ui.sidebar.status_down", "§eDOWN")
        }

        val online = Bukkit.getPlayer(playerId)
        if (
            session.participants.contains(playerId) &&
            online != null &&
            online.isOnline &&
            !online.isDead &&
            online.world.name == session.worldName
        ) {
            return ArenaI18n.text(null, "arena.ui.sidebar.status_alive", "§aALIVE")
        }
        return ArenaI18n.text(null, "arena.ui.sidebar.status_dead", "§cDEAD")
    }

    private fun isSidebarPreparing(session: ArenaSession, playerId: UUID): Boolean {
        val preparingUntil = session.arenaPreparingUntilMillisByParticipant[playerId] ?: return false
        if (System.currentTimeMillis() > preparingUntil) {
            session.arenaPreparingUntilMillisByParticipant.remove(playerId)
            return false
        }
        return true
    }

    private fun renderArenaSidebar(player: Player, lines: List<String>) {
        val manager = Bukkit.getScoreboardManager() ?: return
        val scoreboard = ensureArenaSidebarScoreboard(player, manager)
        val objective = scoreboard.getObjective(ARENA_SIDEBAR_OBJECTIVE_NAME) ?: return

        scoreboard.entries.toList().forEach { entry ->
            scoreboard.resetScores(entry)
        }

        val visibleLines = lines.take(ARENA_SIDEBAR_MAX_LINES)
        visibleLines.forEachIndexed { index, line ->
            val score = visibleLines.size - index
            applyArenaSidebarLine(scoreboard, objective, index, padArenaSidebarText(line), score)
        }
        if (player.scoreboard !== scoreboard) {
            player.scoreboard = scoreboard
        }
    }

    private fun ensureArenaSidebarScoreboard(
        player: Player,
        manager: org.bukkit.scoreboard.ScoreboardManager
    ): org.bukkit.scoreboard.Scoreboard {
        val current = player.scoreboard
        val existingObjective = current.getObjective(ARENA_SIDEBAR_OBJECTIVE_NAME)
        if (existingObjective != null) {
            arenaSidebarPlayers += player.uniqueId
            return current
        }

        arenaSidebarPreviousScoreboards.putIfAbsent(player.uniqueId, current)
        val scoreboard = manager.newScoreboard
        val objective = scoreboard.registerNewObjective(
            ARENA_SIDEBAR_OBJECTIVE_NAME,
            "dummy",
            legacySerializer.deserialize(ArenaI18n.text(null, "arena.ui.sidebar.title", "§7« §cARENA §7»"))
        )
        objective.displaySlot = DisplaySlot.SIDEBAR
        objective.numberFormat(NumberFormat.blank())
        player.scoreboard = scoreboard
        arenaSidebarPlayers += player.uniqueId
        return scoreboard
    }

    private fun applyArenaSidebarLine(
        scoreboard: org.bukkit.scoreboard.Scoreboard,
        objective: org.bukkit.scoreboard.Objective,
        lineIndex: Int,
        text: String,
        score: Int
    ) {
        val teamName = "arena_ln_${lineIndex}"
        val entry = arenaSidebarEntry(lineIndex)
        val team = scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName)
        if (!team.hasEntry(entry)) {
            team.addEntry(entry)
        }
        team.prefix(legacySerializer.deserialize(text))
        team.suffix(Component.empty())
        objective.getScore(entry).score = score
    }

    private fun padArenaSidebarText(text: String, targetWidth: Int = 20): String {
        val currentWidth = visibleTextWidth(text)
        if (currentWidth >= targetWidth) {
            return text
        }
        return text + " ".repeat(targetWidth - currentWidth)
    }

    private fun visibleTextWidth(text: String): Int {
        val stripped = legacyColorPattern.replace(text, "")
        var width = 0
        var index = 0
        while (index < stripped.length) {
            val codePoint = stripped.codePointAt(index)
            width += if (isHalfWidthCodePoint(codePoint)) 1 else 2
            index += Character.charCount(codePoint)
        }
        return width
    }

    private fun isHalfWidthCodePoint(codePoint: Int): Boolean {
        return when {
            codePoint in 0x20..0x7E -> true
            codePoint in 0xA1..0xFF -> true
            Character.isWhitespace(codePoint) -> true
            else -> false
        }
    }

    private fun arenaSidebarEntry(index: Int): String {
        return when (index.coerceIn(0, ARENA_SIDEBAR_MAX_LINES - 1)) {
            0 -> "§0"
            1 -> "§1"
            2 -> "§2"
            3 -> "§3"
            4 -> "§4"
            5 -> "§5"
            6 -> "§6"
            7 -> "§7"
            8 -> "§8"
            9 -> "§9"
            10 -> "§a"
            11 -> "§b"
            12 -> "§c"
            13 -> "§d"
            else -> "§e"
        }
    }

    private fun removeArenaSidebar(playerId: UUID) {
        arenaSidebarPlayers.remove(playerId)
        val previousScoreboard = arenaSidebarPreviousScoreboards.remove(playerId)
        val player = Bukkit.getPlayer(playerId) ?: return
        if (!player.isOnline) {
            return
        }
        val manager = Bukkit.getScoreboardManager() ?: return
        player.scoreboard = previousScoreboard ?: manager.mainScoreboard
    }

    private fun clearAllArenaSidebars() {
        arenaSidebarPlayers.toList().forEach { playerId ->
            removeArenaSidebar(playerId)
        }
        arenaSidebarPlayers.clear()
        arenaSidebarPreviousScoreboards.clear()
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
                val currentTick = Bukkit.getCurrentTick().toLong()
                updateMultiplayerJoinStates()
                updateDownedPlayers(currentTick)
                updateActionMarkers()
                updateBarrierReturnHoldStates(currentTick)
                updateArenaBgmTransitions()
                processArenaWorldCleanupQueue()
                confusionManager.tick()
                if (currentTick % 5L == 0L) {
                    updateArenaSidebars()
                }
            }, 1L, 1L)
        }
    }

    private fun processArenaWorldCleanupQueue() {
        var budget = cleanupBlocksPerTick
        while (budget > 0) {
            val job = cleanupWorldJobs.firstOrNull() ?: return
            val now = System.currentTimeMillis()
            val world = Bukkit.getWorld(job.worldName)
            if (world == null) {
                cleanupWorldJobs.removeFirstOrNull()
                markArenaWorldBroken(job.worldName)
                continue
            }

            val volume = job.volumes.firstOrNull()
            if (volume == null) {
                cleanupWorldJobs.removeFirstOrNull()
                val elapsedMillis = (now - job.startedAtMillis).coerceAtLeast(0L)
                plugin.logger.info(
                    "[Arena] クリーンアップ完了: world=${job.worldName} blocks=${job.processedBlocks}/${job.totalBlocks} elapsed=${elapsedMillis}ms"
                )
                markArenaWorldReady(job.worldName)
                logArenaPoolState("クリーンアップ完了", job.worldName)
                continue
            }

            val block = world.getBlockAt(volume.x, volume.y, volume.z)
            if (block.type != Material.AIR) {
                block.setType(Material.AIR, false)
            }
            job.processedBlocks += 1L
            budget -= 1

            if (!advanceCleanupCursor(volume)) {
                job.volumes.removeFirstOrNull()
            }

            if (now - job.lastProgressLogAtMillis >= 30_000L) {
                val elapsedSeconds = (now - job.startedAtMillis).coerceAtLeast(0L).toDouble() / 1000.0
                val progress = if (job.totalBlocks <= 0L) 100.0 else (job.processedBlocks.toDouble() * 100.0 / job.totalBlocks.toDouble())
                val remainingBlocks = (job.totalBlocks - job.processedBlocks).coerceAtLeast(0L)
                val rate = if (elapsedSeconds <= 0.0) 0.0 else job.processedBlocks.toDouble() / elapsedSeconds
                val remainingSeconds = if (rate <= 0.0) estimateCleanupSeconds(remainingBlocks) else remainingBlocks.toDouble() / rate
                plugin.logger.info(
                    "[Arena] クリーンアップ進捗: world=${job.worldName} progress=${formatPercent(progress)}% " +
                        "processed=${job.processedBlocks}/${job.totalBlocks} elapsed=${formatSeconds(elapsedSeconds)}s " +
                        "remaining=${formatSeconds(remainingSeconds)}s"
                )
                job.lastProgressLogAtMillis = now
            }
        }
    }

    private fun advanceCleanupCursor(volume: ArenaWorldCleanupVolume): Boolean {
        if (volume.z < volume.bounds.maxZ) {
            volume.z += 1
            return true
        }
        volume.z = volume.bounds.minZ
        if (volume.y < volume.bounds.maxY) {
            volume.y += 1
            return true
        }
        volume.y = volume.bounds.minY
        if (volume.x < volume.bounds.maxX) {
            volume.x += 1
            return true
        }
        return false
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
