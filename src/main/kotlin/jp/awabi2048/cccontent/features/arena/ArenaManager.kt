package jp.awabi2048.cccontent.features.arena

import jp.awabi2048.cccontent.config.CoreConfigManager
import jp.awabi2048.cccontent.features.arena.generator.ArenaStageGenerator
import jp.awabi2048.cccontent.features.arena.generator.ArenaStageBuildException
import jp.awabi2048.cccontent.features.arena.generator.ArenaTheme
import jp.awabi2048.cccontent.features.arena.generator.ArenaThemeLoader
import jp.awabi2048.cccontent.features.arena.generator.ArenaThemeLoadStatus
import jp.awabi2048.cccontent.features.arena.generator.ArenaDoorAnimationPlacement
import jp.awabi2048.cccontent.features.arena.generator.ArenaThemeWeightedMobEntry
import jp.awabi2048.cccontent.features.arena.generator.ArenaThemeVariant
import jp.awabi2048.cccontent.features.arena.generator.ArenaWaveSpawnRule
import jp.awabi2048.cccontent.features.arena.event.ArenaSessionEndedEvent
import jp.awabi2048.cccontent.features.arena.mission.ArenaMissionModifiers
import jp.awabi2048.cccontent.features.arena.mission.ArenaMissionService
import jp.awabi2048.cccontent.features.arena.mission.ArenaMissionType
import jp.awabi2048.cccontent.features.arena.mission.ArenaStatusSnapshot
import jp.awabi2048.cccontent.features.common.BGMManager
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.VoidChunkGenerator
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.items.arena.ArenaEnchantShardDefinition
import jp.awabi2048.cccontent.items.arena.ArenaEnchantShardItem
import jp.awabi2048.cccontent.items.arena.ArenaEnchantShardRegistry
import jp.awabi2048.cccontent.items.arena.ArenaMobTokenItem
import jp.awabi2048.cccontent.items.arena.BoomerangTokenItem
import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.mob.EntityMobSpawnOptions
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

sealed class ArenaStartResult {
    data class Success(
        val themeId: String,
        val waves: Int,
        val promoted: Boolean,
        val difficultyDisplay: String
    ) : ArenaStartResult()

    data class Error(
        val messageKey: String,
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

private data class ArenaLobbyMarkerSnapshot(
    val returnLobby: List<Location>,
    val main: List<Location>,
    val tutorialStart: List<Location>,
    val tutorialSteps: List<Location>,
    val pedestal: List<Location>
)

private data class ArenaDropEntry(
    val itemId: String,
    val minAmount: Int,
    val maxAmount: Int,
    val chance: Double
)

private data class ArenaMobDefinitionDrop(
    val baseExp: Int,
    val items: List<ArenaDropEntry>
)

private data class ArenaDropConfig(
    val byMobDefinition: Map<String, ArenaMobDefinitionDrop>
)

private data class BarrierRestartConfig(
    val defaultDurationSeconds: Int,
    val corruptionRatioBase: Double
)

private data class ArenaBgmTrackConfig(
    val soundKey: String,
    val bpm: Double,
    val loopBeats: Int,
    val switchIntervalBeats: Int,
    val pitch: Float
) {
    val beatTicks: Double
        get() = 1200.0 / bpm

    val loopTicks: Long
        get() = (beatTicks * loopBeats.toDouble()).roundToLong().coerceAtLeast(1L)
}

private data class ArenaBgmConfig(
    val normal: ArenaBgmTrackConfig,
    val combat: ArenaBgmTrackConfig,
    val lobby: ArenaBgmTrackConfig
)

private data class ArenaLobbyTutorialState(
    var stepIndex: Int = 0,
    var stepLocations: List<Location> = emptyList()
)

private data class ArenaLobbyProgress(
    val visited: Boolean = false,
    val tutorialCompleted: Boolean = false
)

data class ArenaStatusReport(
    val poolWorlds: List<ArenaPoolWorldStatus>,
    val readyWorldCount: Int,
    val totalWorldCount: Int,
    val inUseWorldCount: Int,
    val cleaningWorldCount: Int,
    val brokenWorldCount: Int,
    val arenaWorldReady: Int,
    val arenaWorldTotal: Int,
    val liftReady: Boolean,
    val lobbyReady: Boolean,
    val lobbyMainReady: Boolean,
    val lobbyTutorialReady: Boolean,
    val returnLobbyCount: Int,
    val mainLobbyCount: Int,
    val tutorialStartCount: Int,
    val tutorialStepCount: Int,
    val pedestalCount: Int,
    val activeSessionCount: Int,
    val maxConcurrentSessions: Int,
    val missionProgress: ArenaStatusSnapshot?,
    val lobbyProgressVisitedCount: Int,
    val lobbyProgressTutorialCompletedCount: Int,
    val themeLoadStatus: ArenaThemeLoadStatus
)

data class ArenaPoolWorldStatus(
    val name: String,
    val state: String
)

private enum class ArenaLobbyTargetType {
    AUTO,
    MAIN,
    TUTORIAL
}

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
        const val ARENA_BGM_LOBBY_KEY_DEFAULT = "kota_server:ost_3.sukima_dungeon"
        const val ARENA_BGM_NORMAL_BPM_DEFAULT = 120.0
        const val ARENA_BGM_COMBAT_BPM_DEFAULT = 140.0
        const val ARENA_BGM_LOBBY_BPM_DEFAULT = 120.0
        const val ARENA_BGM_LOOP_BEATS_DEFAULT = 384
        const val ARENA_BGM_SWITCH_INTERVAL_BEATS_DEFAULT = 8
        const val ARENA_BGM_NORMAL_PITCH_DEFAULT = 1.0f
        const val ARENA_BGM_COMBAT_PITCH_DEFAULT = 1.0f
        const val ACTION_MARKER_RADIUS = 1.0
        const val ACTION_MARKER_RADIUS_SQUARED = ACTION_MARKER_RADIUS * ACTION_MARKER_RADIUS
        const val ACTION_MARKER_MAX_Y_DISTANCE = 0.5
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
        const val STAGE_TRANSFER_BLINDNESS_TICKS = 60
        const val BARRIER_RETURN_HOLD_TICKS = 60
        const val MULTIPLAYER_JOIN_GRACE_SECONDS_DEFAULT = 45
        const val MULTIPLAYER_JOIN_MARKER_SEARCH_RADIUS_DEFAULT = 16.0
        const val MULTIPLAYER_MAX_PARTICIPANTS_DEFAULT = 6
        const val WAVE_START_COMBAT_BGM_DELAY_TICKS = 0L
        const val ENTRANCE_BGM_START_DELAY_TICKS = 30L
        const val MULTIPLAYER_INVITE_SWING_COOLDOWN_TICKS = 5L
        const val MULTIPLAYER_WAITING_EXIT_GRACE_TICKS = 10
        const val MULTIPLAYER_LIFT_AREA_MARGIN = 0.15
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
        const val ENTRANCE_LIFT_TRANSFER_RISE_BLOCKS_DEFAULT = 10
        const val ENTRANCE_LIFT_MAX_RISE_BLOCKS_DEFAULT = 17
        const val ELDER_CURSE_DAMAGE = 10.0
        const val ELDER_CURSE_DURATION_TICKS = 100
        const val ELDER_CURSE_AMPLIFIER = 2
        const val ELDER_CURSE_PLAYER_COOLDOWN_MILLIS = 60_000L
        const val FINAL_WAVE_OVERTIME_TRIGGER_DELAY_MILLIS = 5L * 60L * 1000L
        const val FINAL_WAVE_OVERTIME_INTERVAL_MILLIS = 30L * 1000L
        const val FINAL_WAVE_OVERTIME_DISCHARGE_RADIUS_BASE = 8.0
        const val FINAL_WAVE_OVERTIME_DISCHARGE_DAMAGE_BASE = 4.0
        const val FINAL_WAVE_OVERTIME_DISCHARGE_KNOCKBACK_BASE = 0.75
        const val LOBBY_TUTORIAL_HOLD_TICKS = 40
        const val LOBBY_TUTORIAL_COMPLETE_DELAY_TICKS = 40L
        const val LOBBY_MARKER_TAG_RETURN = "arena.marker.lobby"
        const val LOBBY_MARKER_TAG_MAIN = "arena.marker.lobby_main"
        const val LOBBY_MARKER_TAG_TUTORIAL_START = "arena.marker.lobby_tutorial_start"
        const val LOBBY_MARKER_TAG_TUTORIAL_STEP = "arena.marker.lobby_tutorial_step"
        const val LOBBY_MARKER_TAG_PEDESTAL = "arena.marker.pedestal"
        const val LOBBY_TUTORIAL_STEP_INDEX_TAG_PREFIX = "arena.marker.lobby_tutorial_step.index."

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
    private val entityMobToSessionWorld = mutableMapOf<UUID, String>()
    private val pendingWorldDeletions = mutableMapOf<String, PendingWorldDeletion>()
    private val readyArenaWorldNames = ArrayDeque<String>()
    private val arenaWorldStates = mutableMapOf<String, ArenaPoolWorldState>()
    private val cleanupWorldJobs = ArrayDeque<ArenaWorldCleanupJob>()
    private val invitedPlayerLocks = mutableMapOf<UUID, String>()
    private val inviteSwingCooldownUntilTick = mutableMapOf<UUID, Long>()
    private val pendingLobbyReturnPlayers = mutableSetOf<UUID>()
    private val arenaSidebarPlayers = mutableSetOf<UUID>()
    private val arenaSidebarPreviousScoreboards = mutableMapOf<UUID, org.bukkit.scoreboard.Scoreboard>()
    private val mobDefinitions = mutableMapOf<String, MobDefinition>()
    private val knownMobTypeIds = mutableSetOf<String>()
    private val mobToDefinitionTypeId = mutableMapOf<UUID, String>()
    private val entityMobToDefinitionTypeId = mutableMapOf<UUID, String>()
    private val elderCurseCooldownUntilMillis = mutableMapOf<UUID, Long>()
    private val lobbyVisitedParticipants = mutableSetOf<UUID>()
    private val tutorialCompletedParticipants = mutableSetOf<UUID>()
    private val lobbyTutorialStates = mutableMapOf<UUID, ArenaLobbyTutorialState>()
    private val lobbyTutorialMarkers = mutableMapOf<UUID, ArenaActionMarker>()
    private val lobbyTutorialHoldStates = mutableMapOf<UUID, ArenaActionMarkerHoldState>()
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
    private var arenaDoorAnimationSoundKey = "minecraft:block.iron_door.open"
    private var arenaDoorAnimationSoundPitch = 1.0f
    private var arenaMissionService: ArenaMissionService? = null
    private var arenaBgmConfig = ArenaBgmConfig(
        normal = ArenaBgmTrackConfig(
            soundKey = ARENA_BGM_NORMAL_KEY_DEFAULT,
            bpm = ARENA_BGM_NORMAL_BPM_DEFAULT,
            loopBeats = ARENA_BGM_LOOP_BEATS_DEFAULT,
            switchIntervalBeats = defaultSwitchIntervalBeats(),
            pitch = ARENA_BGM_NORMAL_PITCH_DEFAULT
        ),
        combat = ArenaBgmTrackConfig(
            soundKey = ARENA_BGM_COMBAT_KEY_DEFAULT,
            bpm = ARENA_BGM_COMBAT_BPM_DEFAULT,
            loopBeats = ARENA_BGM_LOOP_BEATS_DEFAULT,
            switchIntervalBeats = defaultSwitchIntervalBeats(),
            pitch = ARENA_BGM_COMBAT_PITCH_DEFAULT
        ),
            lobby = ArenaBgmTrackConfig(
                soundKey = ARENA_BGM_LOBBY_KEY_DEFAULT,
                bpm = ARENA_BGM_LOBBY_BPM_DEFAULT,
                loopBeats = ARENA_BGM_LOOP_BEATS_DEFAULT,
                switchIntervalBeats = defaultSwitchIntervalBeats(),
                pitch = 0.8409f
            )
    )
    private var dropConfig = ArenaDropConfig(
        byMobDefinition = emptyMap()
    )
    private var pedestalMenuProvider: (() -> ArenaEnchantPedestalMenu?)? = null

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

    private fun loadBattleConfigs() {
        val dropFile = ensureArenaConfig("config/arena/drop.yml")
        cachedEntranceLiftTemplate = null

        loadBarrierRestartConfig()
        loadArenaBgmConfig()
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
                ARENA_BGM_LOOP_BEATS_DEFAULT,
                ARENA_BGM_NORMAL_PITCH_DEFAULT
            ),
            combat = parseArenaBgmTrackConfig(
                config,
                "arena.bgm.combat",
                ARENA_BGM_COMBAT_KEY_DEFAULT,
                ARENA_BGM_COMBAT_BPM_DEFAULT,
                ARENA_BGM_LOOP_BEATS_DEFAULT,
                ARENA_BGM_COMBAT_PITCH_DEFAULT
            ),
            lobby = parseArenaBgmTrackConfig(
                config,
                "arena.bgm.lobby",
                ARENA_BGM_LOBBY_KEY_DEFAULT,
                ARENA_BGM_LOBBY_BPM_DEFAULT,
                ARENA_BGM_LOOP_BEATS_DEFAULT,
                0.8409f
            )
        )
    }

    private fun parseArenaBgmTrackConfig(
        config: FileConfiguration,
        path: String,
        defaultSoundKey: String,
        defaultBpm: Double,
        defaultLoopBeats: Int,
        defaultPitch: Float
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
        val pitch = config.getDouble("$path.pitch", defaultPitch.toDouble())
            .toFloat()
            .coerceIn(0.1f, 2.0f)

        return ArenaBgmTrackConfig(
            soundKey = soundKey,
            bpm = bpm,
            loopBeats = loopBeats,
            switchIntervalBeats = switchIntervalBeats,
            pitch = pitch
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

    fun setMissionService(missionService: ArenaMissionService?) {
        arenaMissionService = missionService
    }

    fun setPedestalMenuProvider(provider: (() -> ArenaEnchantPedestalMenu?)?) {
        pedestalMenuProvider = provider
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
            plugin.logger.severe("[Arena] config/mob_definition.yml did not load any mob definitions.")
        }
    }

    private fun loadDropConfig(file: File) {
        val config = YamlConfiguration.loadConfiguration(file)

        val byMobDefinition = mutableMapOf<String, ArenaMobDefinitionDrop>()
        config.getKeys(false).forEach { definitionId ->
            val key = definitionId.trim().lowercase(Locale.ROOT)
            val baseExp = config.getInt("$definitionId.base_exp", 0).coerceAtLeast(0)
            val items = parseDropEntries(config, "$definitionId.items")
            byMobDefinition[key] = ArenaMobDefinitionDrop(baseExp = baseExp, items = items)
        }

        dropConfig = ArenaDropConfig(
            byMobDefinition = byMobDefinition
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

    fun startSession(
        target: Player,
        requestedTheme: String,
        promoted: Boolean = false,
        initialParticipants: List<Player> = emptyList(),
        missionModifiers: ArenaMissionModifiers = ArenaMissionModifiers.NONE,
        missionTypeId: ArenaMissionType = ArenaMissionType.BARRIER_RESTART,
        enableMultiplayerJoin: Boolean = false,
        inviteMissionTitle: String? = null,
        inviteMissionLore: List<String> = emptyList(),
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
                arrayOf("player" to alreadyInSession.name)
            ))
        }

        if (sessionsByWorld.size >= maxConcurrentSessions) {
            return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.concurrent_limit_reached",
                arrayOf("current" to sessionsByWorld.size, "max" to maxConcurrentSessions)
            ))
        }

        val theme = themeLoader.getTheme(requestedTheme)
            ?: return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.theme_not_found",
                arrayOf("theme" to requestedTheme, "mob_type" to requestedTheme)
            ))
        if (promoted && theme.promotedVariant == null) {
            return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.promoted_not_configured",
                arrayOf("theme" to theme.id, "mob_type" to theme.id)
            ))
        }
        val activeTheme = theme.withActiveConfig(promoted)
        val variant = theme.variant(promoted)

        val undefinedWave = (1..variant.waves.size).firstOrNull { wave ->
            selectSpawnCandidates(theme, promoted, wave).isEmpty()
        }
        if (undefinedWave != null) {
            plugin.logger.severe(
                "[Arena] 驛｢・ｧ繝ｻ・ｻ驛｢譏ｴ繝ｻ邵ｺ蜥擾ｽｹ譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ鬯ｮ・｢陷ｿ・･繝ｻ・ｧ陷ｿ・･繝ｻ・､繝ｻ・ｱ髫ｰ・ｨ郢晢ｽｻ theme驛｢・ｧ繝ｻ・ｹ驛｢譎・ｺ｢郢晢ｽｻ驛｢譎｢・ｽ・ｳ髫ｴ蟷｢・ｽ・ｪ髯橸ｽｳ陞溘ｑ・ｽ・ｾ繝ｻ・ｩ驛｢・ｧ繝ｻ・ｦ驛｢・ｧ繝ｻ・ｧ驛｢譎｢・ｽ・ｼ驛｢譎・§遯ｶ・ｲ驍ｵ・ｺ郢ｧ繝ｻ・ｽ鬘費ｽｸ・ｺ繝ｻ・ｾ驍ｵ・ｺ郢晢ｽｻ" +
                    "theme=${theme.id} promoted=$promoted wave=$undefinedWave"
            )
            return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.undefined_wave_spawn",
                arrayOf("theme" to theme.id, "mob_type" to theme.id, "wave" to undefinedWave)
            ))
        }

        val difficultyMaxParticipants = variant.maxParticipants.coerceIn(1, MULTIPLAYER_MAX_PARTICIPANTS_DEFAULT)
        val sanitizedMaxParticipants = maxParticipants.coerceIn(1, difficultyMaxParticipants)
        if (participantPlayers.size > sanitizedMaxParticipants) {
            return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.too_many_participants",
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
                "arena.messages.command.start_error.lift_not_ready"))
        }

        if (enableMultiplayerJoin && liftMarkers.any { liftOccupiedMarkerKeys.contains(liftMarkerKey(it)) }) {
            liftOccupiedWaiters.add(target.uniqueId)
            return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.lift_occupied"))
        }

        val lobbyMarkers = findLoadedLobbyMarkerSnapshot(target.world)
        if (lobbyMarkers.returnLobby.isEmpty()) {
            return completed(ArenaStartResult.Error(
                "arena.messages.command.start_error.lobby_marker_not_found"))
        }

        val world = acquireArenaPoolWorld() ?: return completed(ArenaStartResult.Error(
            "arena.messages.command.start_error.pool_world_unavailable"))

        val returnLocations = participantPlayers.associate { it.uniqueId to it.location.clone() }.toMutableMap()
        val originalGameModes = participantPlayers.associate { it.uniqueId to it.gameMode }.toMutableMap()
        val origin = Location(world, 0.0, 64.0, 0.0)
        val now = System.currentTimeMillis()

        val placeholderLocation = Location(world, 0.0, 64.0, 0.0)
        val placeholderBounds = ArenaBounds(0, 0, 0, 0, 0, 0)
        val difficultyDisplay = ArenaMenuItems.difficultyStars(variant.difficultyStar)
        val session = ArenaSession(
            ownerPlayerId = target.uniqueId,
            worldName = world.name,
            themeId = theme.id,
            promoted = promoted,
            difficultyStar = variant.difficultyStar,
            waves = variant.waves.size,
            missionModifiers = missionModifiers,
            missionTypeId = missionTypeId,
            maxParticipants = sanitizedMaxParticipants,
            participants = participantPlayers.mapTo(mutableSetOf()) { it.uniqueId },
            returnLocations = returnLocations,
            originalGameModes = originalGameModes,
            playerSpawn = placeholderLocation.clone(),
            entranceLocation = placeholderLocation.clone(),
            entranceCheckpoint = placeholderLocation.clone(),
            goalCheckpoint = placeholderLocation.clone(),
            stageBounds = placeholderBounds,
            roomBounds = mutableMapOf(),
            corridorBounds = mutableMapOf(),
            transitBounds = mutableMapOf(),
            pedestalBounds = mutableMapOf(),
            pedestalMarkerBlocks = mutableSetOf(),
            roomMobSpawns = mutableMapOf(),
            roomCheckpoints = mutableMapOf(),
            activatedRoomCheckpoints = mutableMapOf(),
            corridorDoorBlocks = mutableMapOf(),
            doorAnimationPlacements = mutableMapOf(),
            barrierLocation = placeholderLocation.clone(),
            barrierPointLocations = mutableListOf(),
            joinAreaMarkerLocations = mutableListOf(),
            liftMarkerLocations = liftMarkers.map { it.clone() }.toMutableList(),
            lobbyMarkerLocations = lobbyMarkers.returnLobby.map { it.clone() }.toMutableList(),
            lobbyMainMarkerLocations = lobbyMarkers.main.map { it.clone() }.toMutableList(),
            lobbyTutorialStartMarkerLocations = lobbyMarkers.tutorialStart.map { it.clone() }.toMutableList(),
            lobbyTutorialStepMarkerLocations = lobbyMarkers.tutorialSteps.map { it.clone() }.toMutableList(),
            participantSpawnProtectionUntilMillis = if (enableMultiplayerJoin) Long.MAX_VALUE else now + 4000L,
            multiplayerJoinEnabled = enableMultiplayerJoin,
            phase = if (enableMultiplayerJoin) ArenaPhase.RECRUITING else ArenaPhase.PREPARING,
            joinGraceStartMillis = if (enableMultiplayerJoin) now else 0L,
            joinGraceDurationMillis = if (enableMultiplayerJoin) multiplayerJoinGraceSeconds * 1000L else 0L,
            joinGraceEndMillis = if (enableMultiplayerJoin) now + (multiplayerJoinGraceSeconds * 1000L) else 0L,
            inviteMissionTitle = inviteMissionTitle,
            inviteMissionLore = inviteMissionLore,
            stageGenerationCompleted = !enableMultiplayerJoin,
            reviveMaxPerPlayer = variant.reviveMaxPerPlayer,
            reviveTimeLimitSeconds = variant.reviveTimeLimitSeconds,
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
        logArenaPoolState("驛｢・ｧ繝ｻ・ｻ驛｢譏ｴ繝ｻ邵ｺ蜥擾ｽｹ譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ鬯ｮ・｢陷ｿ・･繝ｻ・ｧ郢晢ｽｻ, world.name")

        return try {
            if (enableMultiplayerJoin) {
                target.showBossBar(getOrCreateJoinCountdownBossBar(session, target.uniqueId))
                target.sendMessage(
                    ArenaI18n.text(target, "arena.messages.multiplayer.invite_window_started")
                )
                target.sendMessage(
                    ArenaI18n.text(target, "arena.messages.multiplayer.invite_window_hint")
                )

                session.stageBuildTask = stageGenerator.buildIncrementally(
                    plugin = plugin,
                    world = world,
                    origin = origin,
                    theme = activeTheme,
                    missionTypeId = missionTypeId,
                    waves = variant.waves.size,
                    pedestalRoomProbability = variant.pedestalRoomProbability,
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
                            initializeWavePipeline(session, activeTheme)
                            updateSessionProgressBossBar(session)
                            session.stageGenerationCompleted = true
                            onCompleted?.invoke(elapsedMillisSince(startedAtNanos))
                        }
                        .onFailure { throwable ->
                            if (throwable is ArenaStageBuildException) {
                                plugin.logger.severe("[Arena] 驛｢・ｧ繝ｻ・ｹ驛｢譏ｴ繝ｻ郢晢ｽｻ驛｢・ｧ繝ｻ・ｸ驍ｵ・ｺ繝ｻ・ｮ鬨ｾ蠅難ｽｻ阮吶・驍ｵ・ｺ繝ｻ・ｫ髯樊ｻゑｽｽ・ｱ髫ｰ・ｨ陷会ｽｱ繝ｻ・ｰ驍ｵ・ｺ繝ｻ・ｾ驍ｵ・ｺ陷会ｽｱ隨ｳ繝ｻ ${throwable.message}")
                                throwable.printStackTrace()
                            }
                            terminateSession(
                                session,
                                false,
                                messageKey = "arena.messages.command.start_error.stage_build_failed",
                            )
                            onCompleted?.invoke(elapsedMillisSince(startedAtNanos))
                        }
                }
            } else {
                val stage = stageGenerator.build(
                    world,
                    origin,
                    activeTheme,
                    missionTypeId,
                    variant.waves.size,
                    variant.pedestalRoomProbability,
                    random
                )
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
                initializeWavePipeline(session, activeTheme)
                updateSessionProgressBossBar(session)
                onCompleted?.invoke(elapsedMillisSince(startedAtNanos))
            }
            if (showSessionStartedMessage) {
                target.sendMessage(
                    ArenaI18n.text(target, "arena.messages.session.started", "theme" to theme.id, "mob_type" to theme.id, "difficulty" to difficultyDisplay, "waves" to variant.waves.size)
                )
            }
            ArenaStartResult.Success(theme.id, variant.waves.size, promoted, difficultyDisplay)
        } catch (e: Exception) {
            if (e is ArenaStageBuildException) {
                plugin.logger.severe("[Arena] 驛｢・ｧ繝ｻ・ｹ驛｢譏ｴ繝ｻ郢晢ｽｻ驛｢・ｧ繝ｻ・ｸ驍ｵ・ｺ繝ｻ・ｮ鬨ｾ蠅難ｽｻ阮吶・驍ｵ・ｺ繝ｻ・ｫ髯樊ｻゑｽｽ・ｱ髫ｰ・ｨ陷会ｽｱ繝ｻ・ｰ驍ｵ・ｺ繝ｻ・ｾ驍ｵ・ｺ陷会ｽｱ隨ｳ繝ｻ ${e.message}")
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
                arrayOf("message" to (e.message ?: "unknown"))
            )
        }
    }

    fun stopSession(player: Player, reason: String = ArenaI18n.text(player, "arena.messages.session.ended")): Boolean {
        return leavePlayerFromSession(player.uniqueId, reason)
    }

    fun stopSessionToLobby(player: Player, reason: String = ArenaI18n.text(player, "arena.messages.session.ended")): Boolean {
        val session = getSession(player)
        val destination = if (session != null) resolveSessionLobbyLocation(session) else null
        val stopped = leavePlayerFromSession(player.uniqueId, reason, destination)
        if (stopped && destination != null) {
            markLobbyVisited(player.uniqueId)
            playLobbyBgm(player)
        }
        return stopped
    }

    fun stopSessionById(playerId: UUID, reason: String? = null): Boolean {
        val player = Bukkit.getPlayer(playerId)
        val localizedReason = reason ?: ArenaI18n.text(player, "arena.messages.session.ended")
        return leavePlayerFromSession(playerId, localizedReason)
    }

    fun stopSessionToLobbyById(playerId: UUID, reason: String? = null): Boolean {
        val player = Bukkit.getPlayer(playerId)
        val localizedReason = reason ?: ArenaI18n.text(player, "arena.messages.session.ended")
        val worldName = playerToSessionWorld[playerId]
        val session = worldName?.let { sessionsByWorld[it] }
        val destination = if (session != null) resolveSessionLobbyLocation(session) else null
        val stopped = leavePlayerFromSession(playerId, localizedReason, destination)
        if (stopped && destination != null) {
            markLobbyVisited(playerId)
            if (player != null && player.isOnline) {
                playLobbyBgm(player)
            }
        }
        return stopped
    }

    fun sendPlayerToLobby(target: Player, lobbyType: String?): Boolean {
        if (!target.isOnline) {
            return false
        }

        val targetType = resolveLobbyTargetType(target.uniqueId, lobbyType)
        val destinationResolved = resolveLobbyDestination(target.world, targetType) ?: return false
        val snapshot = destinationResolved.first
        val destination = destinationResolved.second

        clearLobbyTutorialState(target.uniqueId)
        val moved = if (getSession(target) != null) {
            leavePlayerFromSession(target.uniqueId, "", destination)
        } else {
            target.teleport(destination)
        }
        if (!moved) {
            return false
        }

        markLobbyVisited(target.uniqueId)

        if (targetType == ArenaLobbyTargetType.TUTORIAL) {
            startLobbyTutorial(target, snapshot)
        } else {
            playLobbyBgm(target)
        }
        return true
    }

    private fun resolveLobbyTargetType(playerId: UUID, lobbyType: String?): ArenaLobbyTargetType {
        return when (lobbyType?.lowercase(Locale.ROOT)) {
            "tutorial" -> ArenaLobbyTargetType.TUTORIAL
            "main" -> ArenaLobbyTargetType.MAIN
            else -> ArenaLobbyTargetType.MAIN
        }
    }

    private fun resolveLobbyDestination(
        preferredWorld: World?,
        targetType: ArenaLobbyTargetType
    ): Pair<ArenaLobbyMarkerSnapshot, Location>? {
        val worlds = linkedSetOf<World>()
        if (preferredWorld != null) {
            worlds += preferredWorld
        }
        worlds += Bukkit.getWorlds()

        worlds.forEach { world ->
            val snapshot = findLoadedLobbyMarkerSnapshot(world)

            val candidates = when (targetType) {
                ArenaLobbyTargetType.MAIN -> snapshot.main
                ArenaLobbyTargetType.TUTORIAL -> snapshot.tutorialStart
                ArenaLobbyTargetType.AUTO -> snapshot.main
            }
            if (candidates.isEmpty()) {
                return@forEach
            }
            val picked = candidates[random.nextInt(candidates.size)].clone()
            return snapshot to picked
        }

        return null
    }

    private fun playLobbyBgm(player: Player) {
        val track = arenaBgmConfig.lobby
        BGMManager.playPrecise(player, track.soundKey, track.loopTicks, track.pitch)
    }

    private fun sendPlayerToLobbyOrSpawn(player: Player) {
        if (sendPlayerToLobby(player, "main")) {
            return
        }
        Bukkit.getWorlds().firstOrNull()?.spawnLocation?.let { spawn ->
            player.teleport(spawn)
        }
    }

    private fun clearLobbyTutorialState(playerId: UUID) {
        lobbyTutorialStates.remove(playerId)
        lobbyTutorialMarkers.remove(playerId)
        lobbyTutorialHoldStates.remove(playerId)
    }

    fun clearLobbyTutorialState(player: Player) {
        clearLobbyTutorialState(player.uniqueId)
    }

    private fun markLobbyVisited(playerId: UUID) {
        lobbyVisitedParticipants.add(playerId)
        arenaMissionService?.markLobbyVisited(playerId)
    }

    private fun markLobbyTutorialCompleted(playerId: UUID) {
        lobbyVisitedParticipants.add(playerId)
        tutorialCompletedParticipants.add(playerId)
        arenaMissionService?.markLobbyTutorialCompleted(playerId)
    }

    private fun loadLobbyProgress(playerId: UUID): ArenaLobbyProgress {
        if (lobbyVisitedParticipants.contains(playerId) || tutorialCompletedParticipants.contains(playerId)) {
            return ArenaLobbyProgress(
                visited = lobbyVisitedParticipants.contains(playerId),
                tutorialCompleted = tutorialCompletedParticipants.contains(playerId)
            )
        }

        arenaMissionService?.getLobbyProgress(playerId)?.let { (visited, tutorialCompleted) ->
            if (visited) {
                lobbyVisitedParticipants.add(playerId)
            }
            if (tutorialCompleted) {
                tutorialCompletedParticipants.add(playerId)
            }
            return ArenaLobbyProgress(visited = visited, tutorialCompleted = tutorialCompleted)
        }

        return ArenaLobbyProgress(
            visited = lobbyVisitedParticipants.contains(playerId),
            tutorialCompleted = tutorialCompletedParticipants.contains(playerId)
        )
    }

    private fun startLobbyTutorial(player: Player, snapshot: ArenaLobbyMarkerSnapshot) {
        if (!player.isOnline) {
            return
        }

        val stepPairs = snapshot.tutorialSteps
            .map { location ->
                val marker = location.world
                    ?.getNearbyEntities(location, 0.25, 0.25, 0.25)
                    ?.filterIsInstance<Marker>()
                    ?.firstOrNull { candidate ->
                        candidate.scoreboardTags.contains(LOBBY_MARKER_TAG_TUTORIAL_STEP)
                    }
                marker to location.clone().apply { this.world = player.world }
            }

        val steps = stepPairs
            .sortedWith(
                compareBy<Pair<Marker?, Location>> {
                    val marker = it.first
                    if (marker == null) Int.MAX_VALUE else extractTutorialStepIndex(marker) ?: Int.MAX_VALUE
                }.thenBy { it.second.blockX }
                    .thenBy { it.second.blockY }
                    .thenBy { it.second.blockZ }
            )
            .map { it.second }

        if (steps.isEmpty()) {
            markLobbyTutorialCompleted(player.uniqueId)
            showLobbyTutorialCompletedEffect(player)
            return
        }

        val state = ArenaLobbyTutorialState(stepIndex = 0, stepLocations = steps)
        lobbyTutorialStates[player.uniqueId] = state
        tutorialCompletedParticipants.remove(player.uniqueId)
        spawnLobbyTutorialMarker(player, state)
    }

    private fun spawnLobbyTutorialMarker(player: Player, state: ArenaLobbyTutorialState) {
        val stepLocation = state.stepLocations.getOrNull(state.stepIndex) ?: run {
            completeLobbyTutorial(player)
            return
        }

        val marker = ArenaActionMarker(
            id = UUID.randomUUID(),
            type = ArenaActionMarkerType.TUTORIAL_PROGRESS,
            center = stepLocation.clone().add(0.0, ACTION_MARKER_CENTER_Y_OFFSET, 0.0),
            holdTicksRequired = LOBBY_TUTORIAL_HOLD_TICKS,
            state = ArenaActionMarkerState.READY
        )
        marker.colorTransitionFrom = marker.colorFor(ArenaActionMarkerState.READY)
        marker.colorTransitionTo = marker.colorFor(ArenaActionMarkerState.READY)
        marker.colorTransitionStartTick = Bukkit.getCurrentTick().toLong()
        lobbyTutorialMarkers[player.uniqueId] = marker
        lobbyTutorialHoldStates.remove(player.uniqueId)
    }

    private fun completeLobbyTutorial(player: Player) {
        clearLobbyTutorialState(player.uniqueId)
        markLobbyTutorialCompleted(player.uniqueId)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!player.isOnline) {
                return@Runnable
            }
            showLobbyTutorialCompletedEffect(player)
        }, LOBBY_TUTORIAL_COMPLETE_DELAY_TICKS)
    }

    private fun showLobbyTutorialCompletedEffect(player: Player) {
        player.sendTitle("", ArenaI18n.text(player, "arena.messages.lobby.tutorial.completed_title"), 10, 100, 10)
        playLobbyBgm(player)
    }

    private fun extractTutorialStepIndex(marker: Marker): Int? {
        val tag = marker.scoreboardTags.firstOrNull { it.startsWith(LOBBY_TUTORIAL_STEP_INDEX_TAG_PREFIX) }
            ?: return null
        return tag.removePrefix(LOBBY_TUTORIAL_STEP_INDEX_TAG_PREFIX).toIntOrNull()
    }

    fun notifyParticipantDeath(player: Player) {
        val session = getSession(player) ?: return
        session.participants
            .asSequence()
            .filter { it != player.uniqueId }
            .mapNotNull { Bukkit.getPlayer(it) }
            .filter { it.isOnline && it.world.name == session.worldName }
            .forEach { participant ->
                participant.sendMessage(ArenaI18n.text(participant, "arena.messages.down.participant_died", "player" to player.name))
                if (!hasOtherAliveNonDownParticipant(session, participant.uniqueId)) {
                    return@forEach
                }
                scheduleOageMessage(
                    participant,
                    OAGE_FOLLOWUP_DELAY_TICKS,
                    "arena.messages.oage.participant_died_followup",
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
                duringShutdown = true
            )
        }
        sessionsByWorld.clear()
        playerToSessionWorld.clear()
        mobToSessionWorld.clear()
        entityMobToSessionWorld.clear()
        mobToDefinitionTypeId.clear()
        entityMobToDefinitionTypeId.clear()
        cleanupWorldJobs.clear()
        readyArenaWorldNames.clear()
        confusionManager.clearAll()
        arenaWorldStates.clear()
        clearAllArenaSidebars()
        invitedPlayerLocks.clear()
        inviteSwingCooldownUntilTick.clear()
        liftOccupiedMarkerKeys.clear()
        liftOccupiedWaiters.clear()
        lobbyVisitedParticipants.clear()
        tutorialCompletedParticipants.clear()
        lobbyTutorialStates.clear()
        lobbyTutorialMarkers.clear()
        lobbyTutorialHoldStates.clear()
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

    fun handleParticipantQuit(player: Player) {
        handleInviteTargetUnavailable(player)
        clearLobbyTutorialState(player)

        val session = getSession(player) ?: return
        if (!session.participants.contains(player.uniqueId)) return

        stopArenaBgmForPlayer(player)
        session.progressBossBar?.let { player.hideBossBar(it) }
        hideJoinCountdownBossBar(session, player.uniqueId)
        clearReviveBindingByReviver(session, player.uniqueId)
        removeArenaSidebar(player.uniqueId)
        updateArenaSidebars()
        scheduleTerminateIfAllParticipantsLoggedOut(session.worldName)
    }

    private fun scheduleTerminateIfAllParticipantsLoggedOut(worldName: String) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val session = sessionsByWorld[worldName] ?: return@Runnable
            if (session.participants.any { participantId ->
                    val participant = Bukkit.getPlayer(participantId)
                    participant != null && participant.isOnline
                }
            ) {
                return@Runnable
            }
            terminateSession(session, false)
        })
    }

    fun handleParticipantJoin(player: Player) {
        val session = getSession(player)
        if (session == null || !session.participants.contains(player.uniqueId)) {
            if (pendingLobbyReturnPlayers.remove(player.uniqueId)) {
                sendPlayerToLobbyOrSpawn(player)
            }
            return
        }

        val world = Bukkit.getWorld(session.worldName)
        if (world == null) {
            pendingLobbyReturnPlayers.add(player.uniqueId)
            sendPlayerToLobbyOrSpawn(player)
            return
        }

        val destination = resolveLatestRoomCheckpoint(session, world)
            ?: session.entranceCheckpoint.clone().apply { this.world = world }
        destination.yaw = player.location.yaw
        destination.pitch = player.location.pitch
        player.teleport(destination)
        applySessionGameMode(session, player)
        player.removePotionEffect(PotionEffectType.BLINDNESS)
        val now = System.currentTimeMillis()
        session.participantLocationHistory[player.uniqueId] = ArrayDeque<TimedPlayerLocation>().apply {
            addLast(TimedPlayerLocation(now, destination.clone()))
        }
        session.participantLastSampleMillis[player.uniqueId] = now
        session.progressBossBar?.let { player.showBossBar(it) }
        resumeArenaBgmForPlayer(session, player)
        updateArenaSidebars()
    }

    private fun applyStageBuildResult(session: ArenaSession, stage: jp.awabi2048.cccontent.features.arena.generator.ArenaStageBuildResult) {
        session.playerSpawn = stage.playerSpawn.clone()
        session.entranceLocation = stage.entranceLocation.clone()
        session.entranceCheckpoint = stage.entranceCheckpoint.clone()
        session.goalCheckpoint = stage.goalCheckpoint.clone()
        session.stageBounds = stage.stageBounds
        session.roomBounds.clear()
        session.roomBounds.putAll(stage.roomBounds)
        session.corridorBounds.clear()
        session.corridorBounds.putAll(stage.corridorBounds)
        session.transitBounds.clear()
        session.transitBounds.putAll(stage.transitBounds)
        session.pedestalBounds.clear()
        session.pedestalBounds.putAll(stage.pedestalBounds)
        session.pedestalMarkerBlocks.clear()
        session.pedestalMarkerBlocks.addAll(stage.pedestalMarkerBlocks)
        session.roomMobSpawns.clear()
        session.roomMobSpawns.putAll(stage.roomMobSpawns)
        session.roomCheckpoints.clear()
        session.roomCheckpoints.putAll(stage.roomCheckpoints.mapValues { (_, checkpoint) -> checkpoint.clone() })
        session.activatedRoomCheckpoints.clear()
        session.corridorDoorBlocks.clear()
        session.corridorDoorBlocks.putAll(stage.corridorDoorBlocks)
        session.doorAnimationPlacements.clear()
        session.doorAnimationPlacements.putAll(stage.doorAnimationPlacements)
        session.barrierLocation = stage.barrierLocation.clone()
        session.barrierPointLocations.clear()
        session.barrierPointLocations.addAll(stage.barrierPointLocations.map { it.clone() })

        // 髫ｰ蜉ｱ繝ｻ繝ｻ・ｨ陟托ｽｱ・朱・・ｹ譏ｴ繝ｻ邵ｺ蜥擾ｽｹ譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ鬨ｾ蛹・ｽｽ・ｨ: 驛｢譎・鯵邵ｺ蟶ｷ・ｹ・ｧ繝ｻ・ｹ驛｢譎・ｺ｢郢晢ｽｻ驛｢譎｢・ｽ・ｳ髣厄ｽｴ陷･・ｲ繝ｻ・ｽ繝ｻ・ｮ驛｢・ｧ陋幢ｽｵ邵ｺ諷包ｽｹ譎・ｱ堤ｹ晢ｽｻ
        session.clearingBossLocations.clear()
        session.clearingBossLocations.addAll(stage.clearingBossLocations.map { it.clone() })

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
        session.waitingOutsideTicksByPlayer.clear()
        session.invitedParticipants.clear()
    }

    private fun removeInvitedParticipant(session: ArenaSession, invitedId: UUID) {
        session.invitedParticipants.remove(invitedId)
        session.waitingParticipants.remove(invitedId)
        session.waitingNotifiedParticipants.remove(invitedId)
        session.waitingSubtitleNextTickByPlayer.remove(invitedId)
        session.waitingOutsideTicksByPlayer.remove(invitedId)
        hideJoinCountdownBossBar(session, invitedId)
        releaseInvitedPlayerLock(invitedId)
        removeArenaSidebar(invitedId)
    }

    private fun declineInvitedParticipant(session: ArenaSession, invited: Player) {
        removeInvitedParticipant(session, invited.uniqueId)
        invited.sendMessage(
            ArenaI18n.text(invited, "arena.messages.multiplayer.declined_self")
        )
        val owner = Bukkit.getPlayer(session.ownerPlayerId)
        if (owner != null && owner.isOnline) {
            owner.sendMessage(
                ArenaI18n.text(owner, "arena.messages.multiplayer.declined_notify_owner", "player" to invited.name)
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

            // 髫ｰ蜉ｱ繝ｻ繝ｻ・ｨ陟托ｽｱ・朱・・ｹ譏ｴ繝ｻ邵ｺ蜥擾ｽｹ譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ: 髫ｴ蠑ｱ・玖将・｣髯具ｽｻ郢晢ｽｻ繝ｻ讙趣ｽｹ譏ｶ繝ｻ邵ｺ閾･・ｹ譏ｴ繝ｻ邵ｺ繝ｻ
            if (isClearingMission(session) && isClearingBossTimeExpired(session)) {
                onClearingBossTimeExpired(session)
                continue
            }

            // 髫ｰ蜉ｱ繝ｻ繝ｻ・ｨ陟托ｽｱ・朱・・ｹ譏ｴ繝ｻ邵ｺ蜥擾ｽｹ譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ: 驛｢譎・鯵邵ｺ蟶ｷ・ｹ譎√・郢晢ｽｻ髫ｴ蜴・ｽｽ・ｴ髫ｴ繝ｻ・ｽ・ｰ郢晢ｽｻ陜捺ｺｷ繝ｻ鬯ｮ・｢隶鯉ｽ｢繝ｻ・｡繝ｻ・ｨ鬩穂ｼ夲ｽｽ・ｺ驍ｵ・ｺ繝ｻ・ｮ驍ｵ・ｺ雋・∞・ｽ竏壹・郢晢ｽｻ
            if (isClearingMission(session) && session.clearingBossSpawned) {
                updateSessionProgressBossBar(session)
            }

            tickFinalWaveOvertimeDischarge(session, now)

            for (participantId in session.participants.toList()) {
                val player = Bukkit.getPlayer(participantId)
                if (player == null || !player.isOnline) continue

                val location = player.location

                if (session.barrierRestartCompleted || session.missionCompleted) {
                    continue
                }

                val currentWave = session.currentWave.coerceAtLeast(1)
                if (location.world?.name != world.name || !session.stageBounds.contains(location.x, location.y, location.z)) {
                    teleportOnOutOfBounds(player, session, world, currentWave)
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

    private fun tickFinalWaveOvertimeDischarge(session: ArenaSession, nowMillis: Long) {
        if (session.barrierRestartCompleted || session.missionCompleted) return
        if (session.finalWaveStartedAtMillis <= 0L) return
        if (!session.startedWaves.contains(session.waves)) return

        if (session.overtimeDischargeState.nextTriggerAtMillis <= 0L) {
            session.overtimeDischargeState.nextTriggerAtMillis =
                session.finalWaveStartedAtMillis + FINAL_WAVE_OVERTIME_TRIGGER_DELAY_MILLIS
        }

        while (nowMillis >= session.overtimeDischargeState.nextTriggerAtMillis) {
            triggerFinalWaveOvertimeDischarge(session, session.overtimeDischargeState.triggerCount)
            session.overtimeDischargeState.triggerCount += 1
            session.overtimeDischargeState.nextTriggerAtMillis += FINAL_WAVE_OVERTIME_INTERVAL_MILLIS
        }
    }

    private fun triggerFinalWaveOvertimeDischarge(session: ArenaSession, triggerCount: Int) {
        val world = Bukkit.getWorld(session.worldName) ?: return
        val center = session.barrierLocation.clone().apply { this.world = world }.add(0.5, 1.0, 0.5)
        val scale = 1.1.pow(triggerCount.toDouble())
        val radius = (FINAL_WAVE_OVERTIME_DISCHARGE_RADIUS_BASE * scale).coerceAtLeast(0.5)
        val damage = (FINAL_WAVE_OVERTIME_DISCHARGE_DAMAGE_BASE * scale).coerceAtLeast(0.0)
        val knockback = (FINAL_WAVE_OVERTIME_DISCHARGE_KNOCKBACK_BASE * scale).coerceAtLeast(0.0)

        world.spawnParticle(Particle.ELECTRIC_SPARK, center, 96, 0.6, 0.4, 0.6, 0.2)
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9f, 1.5f)
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.9f, 1.25f)

        val lines = 16
        repeat(lines) { index ->
            val angle = (Math.PI * 2.0) * (index.toDouble() / lines.toDouble())
            val direction = Vector(cos(angle), 0.0, sin(angle))
            val steps = 14
            for (step in 0..steps) {
                val ratio = step.toDouble() / steps.toDouble()
                val point = center.clone().add(direction.clone().multiply(radius * ratio))
                world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.03, 0.03, 0.03, 0.0)
            }
        }

        session.participants
            .asSequence()
            .mapNotNull { Bukkit.getPlayer(it) }
            .filter { it.isOnline && it.world.uid == world.uid }
            .filter { !isPlayerDowned(session, it.uniqueId) }
            .forEach { participant ->
                val participantLocation = participant.location
                val distance = participantLocation.distance(center)
                if (distance > radius) {
                    return@forEach
                }

                if (damage > 0.0) {
                    participant.damage(damage)
                }

                val push = participantLocation.toVector().subtract(center.toVector())
                val horizontal = if (push.lengthSquared() <= 0.0001) {
                    participantLocation.direction.clone().setY(0.0)
                } else {
                    push.setY(0.0)
                }
                if (horizontal.lengthSquared() > 0.0001 && knockback > 0.0) {
                    val velocity = horizontal.normalize().multiply(knockback).setY(0.25)
                    participant.velocity = participant.velocity.add(velocity)
                }
                participant.playSound(participant.location, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.2f)
            }
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
            plugin.logger.log(Level.SEVERE, "[Arena] 驛｢譎｢・ｽ・｢驛｢譎・§郢晢ｽｩ驛｢譎｢・ｽ・ｭ驛｢譏ｴ繝ｻ郢晢ｽｻ髯ｷ繝ｻ・ｽ・ｦ鬨ｾ繝ｻ繝ｻ遶頑･｢譽斐・・ｱ髫ｰ・ｨ陷会ｽｱ繝ｻ・ｰ驍ｵ・ｺ繝ｻ・ｾ驍ｵ・ｺ陷会ｽｱ隨ｳ繝ｻ・ｸ・ｺ霑ｹ螟ｲ・ｽ・ｨ隲・ｹ繝ｻ・ｼ陞ｳ・｣・つ繝ｻ・ｲ鬮ｯ・ｦ陟募ｾ後・鬩搾ｽｯ陷･謫ｾ・ｽ・ｶ陞｢・ｹ繝ｻ・ｰ驍ｵ・ｺ繝ｻ・ｾ驍ｵ・ｺ郢晢ｽｻ entityId=$entityId", exception)
        }
        val worldName = mobToSessionWorld.remove(entityId)
            ?: entityMobToSessionWorld.remove(entityId)
            ?: return
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
            val definitionTypeId = mobToDefinitionTypeId[entityId]
                ?: entityMobToDefinitionTypeId[entityId]
                ?: event.entity.type.name
            killAwardParticipantIds.forEach { participantId ->
                arenaMissionService?.recordMobKill(participantId, definitionTypeId)
            }
        }
        consumeMob(session, entityId, countKill = countKill)
    }

    fun handleEntityDeath(entity: Entity) {
        val entityId = entity.uniqueId
        val worldName = entityMobToSessionWorld.remove(entityId) ?: return
        val session = sessionsByWorld[worldName] ?: return
        session.barrierDefenseMobIds.remove(entityId)
        session.barrierDefenseTargetMobIds.remove(entityId)
        session.barrierDefenseAssaultMobIds.remove(entityId)
        consumeMob(session, entityId, countKill = true)
    }

    fun getUnactivatedBarrierPointLocations(worldName: String): List<Location> {
        val session = sessionsByWorld[worldName] ?: return emptyList()
        if (!session.barrierRestarting) return emptyList()
        return session.actionMarkers.values
            .asSequence()
            .filter { it.type == ArenaActionMarkerType.BARRIER_ACTIVATE }
            .filter { it.state != ArenaActionMarkerState.RUNNING }
            .map { it.center.clone() }
            .toList()
    }

    fun isArenaParticipant(worldName: String, playerId: UUID): Boolean {
        val session = sessionsByWorld[worldName] ?: return false
        return session.participants.contains(playerId)
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

    fun registerChildEntityMob(entity: Entity, definitionTypeId: String, options: EntityMobSpawnOptions) {
        if (options.featureId != "arena") return
        val worldName = entity.world.name
        val session = sessionsByWorld[worldName] ?: return
        val entityId = entity.uniqueId
        entityMobToSessionWorld[entityId] = worldName
        entityMobToDefinitionTypeId[entityId] = definitionTypeId
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

        if (!session.stageStarted || session.phase != ArenaPhase.IN_PROGRESS) {
            return
        }

        if (isPlayerDowned(session, player.uniqueId)) {
            event.isCancelled = true
            return
        }

        val finalHealth = player.health - event.finalDamage
        if (finalHealth > 0.0) {
            return
        }

        if (hasVanillaTotemAvailable(player.inventory)) {
            return
        }

        event.isCancelled = true

        if (!isWaveCombatActive(session)) {
            player.health = 1.0
            player.noDamageTicks = 10
            return
        }

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

    private fun hasVanillaTotemAvailable(inventory: PlayerInventory): Boolean {
        return inventory.itemInMainHand.type == Material.TOTEM_OF_UNDYING ||
            inventory.itemInOffHand.type == Material.TOTEM_OF_UNDYING
    }

    private fun isWaveCombatActive(session: ArenaSession): Boolean {
        if (session.phase != ArenaPhase.IN_PROGRESS) return false
        if (!session.stageStarted) return false
        if (session.missionCompleted || session.barrierRestartCompleted || session.barrierRestarting) return false

        val wave = session.currentWave
        if (wave <= 0 || wave > session.waves) return false
        if (!session.startedWaves.contains(wave)) return false
        if (session.clearedWaves.contains(wave)) return false

        return true
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
        if (isArenaEnderPearlUse(event)) {
            event.isCancelled = true
            event.setUseInteractedBlock(Event.Result.DENY)
            event.setUseItemInHand(Event.Result.DENY)
            return
        }

        val clicked = event.clickedBlock ?: return
        val worldName = clicked.world.name
        val session = sessionsByWorld[worldName] ?: return
        if (event.action == Action.LEFT_CLICK_BLOCK && clicked.type == Material.COBWEB) {
            if (PeriodicCobwebAbility.consumeArenaPlacedCobweb(clicked)) {
                clicked.type = Material.AIR
                event.isCancelled = true
                event.setUseInteractedBlock(Event.Result.DENY)
                event.setUseItemInHand(Event.Result.DENY)
            }
            return
        }
        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }
        if (hasPedestalMarkerBlock(session, clicked.location)) {
            event.isCancelled = true
            event.setUseInteractedBlock(Event.Result.DENY)
            event.setUseItemInHand(Event.Result.DENY)
            openPedestalMenu(event.player)
            return
        }
        if (!clicked.type.isInteractable) {
            return
        }
        event.isCancelled = true
        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DEFAULT)
    }

    private fun isArenaEnderPearlUse(event: PlayerInteractEvent): Boolean {
        val player = event.player
        val session = getSession(player) ?: return false
        if (!session.participants.contains(player.uniqueId)) return false
        if (player.world.name != session.worldName) return false
        return event.item?.type == Material.ENDER_PEARL
    }

    fun handleArenaInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val clicked = event.rightClicked

        if (clicked is Marker && clicked.scoreboardTags.contains(LOBBY_MARKER_TAG_PEDESTAL)) {
            event.isCancelled = true
            if (!ArenaPermissions.hasPedestalMenuPermission(player)) {
                player.sendMessage(
                    ArenaI18n.text(player, "arena.messages.command.menu_permission_denied")
                )
                return
            }
            val menu = pedestalMenuProvider?.invoke()
            if (menu == null) {
                player.sendMessage(
                    ArenaI18n.text(player, "arena.messages.command.feature_unavailable")
                )
                return
            }
            menu.openMenu(player)
            return
        }

        val session = getSession(player) ?: return
        if (!session.participants.contains(player.uniqueId)) return
        if (isPlayerDowned(session, player.uniqueId)) return

        val shulker = clicked as? Shulker ?: return
        val downedId = findDownedPlayerIdByShulker(session, shulker.uniqueId) ?: return
        val downed = Bukkit.getPlayer(downedId) ?: return
        if (!downed.isOnline || downed.world.uid != player.world.uid) return

        handleReviveTargetSelection(session, player, downed)
        event.isCancelled = true
    }

    private fun hasPedestalMarkerBlock(session: ArenaSession, location: Location): Boolean {
        return session.pedestalMarkerBlocks.contains(ArenaBlockKey.from(location))
    }

    private fun openPedestalMenu(player: Player) {
        if (!ArenaPermissions.hasPedestalMenuPermission(player)) {
            player.sendMessage(
                ArenaI18n.text(player, "arena.messages.command.menu_permission_denied")
            )
            return
        }
        val menu = pedestalMenuProvider?.invoke()
        if (menu == null) {
            player.sendMessage(
                ArenaI18n.text(player, "arena.messages.command.feature_unavailable")
            )
            return
        }
        menu.openMenu(player)
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
        val entity = event.entity
        if (entity is Player) return
        if (!mobToSessionWorld.containsKey(entity.uniqueId) && !entityMobToSessionWorld.containsKey(entity.uniqueId)) return
        event.isCancelled = true
    }

    fun handleMobFriendlyFire(event: EntityDamageByEntityEvent) {
        val damaged = event.entity
        if (damaged is Player) return

        val damagedWorld = mobToSessionWorld[damaged.uniqueId]
            ?: entityMobToSessionWorld[damaged.uniqueId]
            ?: return
        val attackerMobId = resolveArenaMobAttackerId(event) ?: return
        val attackerWorld = mobToSessionWorld[attackerMobId]
            ?: entityMobToSessionWorld[attackerMobId]
            ?: return
        if (damagedWorld != attackerWorld) return
        event.isCancelled = true
    }

    fun handleMobDamagedByParticipant(event: EntityDamageByEntityEvent) {
        val damaged = event.entity
        if (damaged is Player) return

        val worldName = mobToSessionWorld[damaged.uniqueId]
            ?: entityMobToSessionWorld[damaged.uniqueId]
            ?: return
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
            if (System.currentTimeMillis() >= ownerSession.joinGraceEndMillis) {
                player.sendMessage(
                    ArenaI18n.text(player, "arena.messages.multiplayer.invite_window_closed")
                )
                return
            }

            val invited = rayTraceTargetPlayer(player) ?: return
            inviteSwingCooldownUntilTick[player.uniqueId] = currentTick + MULTIPLAYER_INVITE_SWING_COOLDOWN_TICKS
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

        val target = rayTraceTargetPlayer(player) ?: return
        if (target.uniqueId != owner.uniqueId) {
            return
        }
        inviteSwingCooldownUntilTick[player.uniqueId] = currentTick + MULTIPLAYER_INVITE_SWING_COOLDOWN_TICKS
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
            0.1
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
        val reservedSeats = session.participants.size + session.invitedParticipants.size
        if (reservedSeats >= session.maxParticipants) {
            owner.sendMessage(
                ArenaI18n.text(owner, "arena.messages.multiplayer.invite_failed_full")
            )
            return
        }

        if (playerToSessionWorld.containsKey(invited.uniqueId)) {
            owner.sendMessage(
                ArenaI18n.text(owner, "arena.messages.multiplayer.invite_failed_in_session", "player" to invited.name)
            )
            return
        }

        if (invited.world.uid != owner.world.uid) {
            owner.sendMessage(
                ArenaI18n.text(owner, "arena.messages.multiplayer.invite_failed_world")
            )
            return
        }

        val lockedWorld = invitedPlayerLocks[invited.uniqueId]
        if (lockedWorld != null && lockedWorld != session.worldName) {
            owner.sendMessage(
                ArenaI18n.text(owner, "arena.messages.multiplayer.invite_failed_already_locked", "player" to invited.name)
            )
            return
        }

        if (!session.invitedParticipants.add(invited.uniqueId)) {
            owner.sendMessage(
                ArenaI18n.text(owner, "arena.messages.multiplayer.already_invited", "player" to invited.name)
            )
            return
        }

        session.returnLocations.putIfAbsent(invited.uniqueId, invited.location.clone())
        session.sidebarParticipantNames.putIfAbsent(invited.uniqueId, invited.name)
        invitedPlayerLocks[invited.uniqueId] = session.worldName
        invited.isGlowing = true
        invited.showBossBar(getOrCreateJoinCountdownBossBar(session, invited.uniqueId))
        owner.sendMessage(
            ArenaI18n.text(owner, "arena.messages.multiplayer.invite_sent", "player" to invited.name)
        )
        invited.sendMessage(buildInviteMessageComponent(owner.name, session))
        invited.sendMessage(
            ArenaI18n.text(invited, "arena.messages.multiplayer.invited_decline_hint")
        )
    }

    private fun buildInviteMessageComponent(ownerName: String, session: ArenaSession): Component {
        val missionTitle = session.inviteMissionTitle
        if (missionTitle.isNullOrBlank()) {
            return legacySerializer.deserialize(
                ArenaI18n.text(null, "arena.messages.multiplayer.invite_message_simple", "owner" to ownerName)
            )
        }

        val hoverText = if (session.inviteMissionLore.isEmpty()) {
            ArenaI18n.text(null, "arena.messages.multiplayer.no_description")
        } else {
            session.inviteMissionLore.joinToString("\n")
        }

        val prefix = legacySerializer.deserialize(ArenaI18n.text(null, "arena.messages.multiplayer.invite_message_prefix", "owner" to ownerName))
        val missionPart = legacySerializer.deserialize(ArenaI18n.text(null, "arena.messages.multiplayer.invite_message_mission", "mission" to missionTitle))
            .hoverEvent(HoverEvent.showText(legacySerializer.deserialize(hoverText)))
        val suffix = legacySerializer.deserialize(ArenaI18n.text(null, "arena.messages.multiplayer.invite_message_suffix"))

        return Component.empty()
            .append(prefix)
            .append(missionPart)
            .append(suffix)
    }

    fun handleMobTarget(event: EntityTargetLivingEntityEvent) {
        val mob = event.entity as? Mob ?: return
        val worldName = mobToSessionWorld[mob.uniqueId]
            ?: entityMobToSessionWorld[mob.uniqueId]
            ?: return
        val session = sessionsByWorld[worldName] ?: return

        if (event.target == null) {
            if (mob.type == EntityType.SPIDER || mob.type == EntityType.CAVE_SPIDER) {
                mob.target = selectNearestParticipant(session, mob.location)
            }
            return
        }

        val target = event.target as? LivingEntity ?: return
        if (target !is Player) {
            val targetWorld = mobToSessionWorld[target.uniqueId]
                ?: entityMobToSessionWorld[target.uniqueId]
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
        if (entityMobToSessionWorld.containsKey(damager.uniqueId)) {
            return damager.uniqueId
        }

        if (damager is Projectile) {
            val shooterEntity = damager.shooter as? Entity ?: return null
            if (shooterEntity is Player) return null
            if (entityMobToSessionWorld.containsKey(shooterEntity.uniqueId)) {
                return shooterEntity.uniqueId
            }
            val shooter = shooterEntity as? LivingEntity ?: return null
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

        stopArenaBgm(session)
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
                ArenaI18n.text(player, "arena.messages.down.entered")
            )

            session.participants
                .asSequence()
                .filter { it != player.uniqueId }
                .mapNotNull { Bukkit.getPlayer(it) }
                .filter { it.isOnline && it.world.name == session.worldName }
                .forEach { participant ->
                    participant.sendMessage(ArenaI18n.text(participant, "arena.messages.down.needs_revive", "player" to player.name))
                }
        }

        if (reviveDisabled && hasOtherAliveNonDownParticipant(session, player.uniqueId)) {
            val oageMessage = ArenaI18n.stringList(player, "arena.messages.oage.down_with_survivors").randomOrNull() ?: return
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
                ArenaI18n.text(downed, "arena.messages.down.game_over")
            )
            scheduleOageMessage(
                downed,
                OAGE_FOLLOWUP_DELAY_TICKS,
                "arena.messages.oage.game_over_followup",
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
            ArenaI18n.text(downed, "arena.messages.down.recovered")
        )

        if (revivedBy != null && revivedBy.uniqueId != downed.uniqueId) {
            downed.playSound(downed.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
            revivedBy.playSound(revivedBy.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
            revivedBy.sendMessage(
                ArenaI18n.text(revivedBy, "arena.messages.down.revive_success", "player" to downed.name)
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
                                ArenaI18n.text(downed, "arena.messages.down.game_over")
                            )
                            scheduleOageMessage(
                                downed,
                                OAGE_FOLLOWUP_DELAY_TICKS,
                                "arena.messages.oage.game_over_followup",
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
                                force = true
                            )
                            stopSessionToLobbyById(downedId, "")
                        } else {
                            stopSessionById(
                                downedId,
                                ArenaI18n.text(downed, "arena.messages.down.timeout")
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
                player.teleport(Location(anchor.world, anchor.x, anchor.y, anchor.z, loc.yaw, loc.pitch))
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
        bossBars.downedPlayerBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.revive.downed", "reviver" to reviver.name)))
        bossBars.reviverPlayerBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.revive.reviver", "downed" to downed.name)))
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
        val worldName = mobToSessionWorld[entity.uniqueId]
            ?: entityMobToSessionWorld[entity.uniqueId]
            ?: return
        val session = sessionsByWorld[worldName]

        val killer = entity.killer
        event.drops.clear()

        if (killer == null) {
            event.droppedExp = 0
            return
        }

        val definitionTypeId = mobToDefinitionTypeId[entity.uniqueId]
            ?: entityMobToDefinitionTypeId[entity.uniqueId]
            ?: entity.type.name
        val normalizedTypeId = definitionTypeId.trim().lowercase(Locale.ROOT)
        val lootingLevel = resolveLootingLevel(killer.inventory)

        val rebuiltDrops = mutableListOf<ItemStack>()
        rebuiltDrops += buildConfiguredAdditionalDrops(killer, normalizedTypeId, lootingLevel)

        createMobTokenDrop(killer, normalizedTypeId, lootingLevel)?.let { token ->
            rebuiltDrops += token
        }

        createEnchantShardDrop(killer, normalizedTypeId)?.let { shard ->
            rebuiltDrops += shard
        }

        rebuiltDrops.forEach { stack ->
            if (stack.type.isAir || stack.amount <= 0) return@forEach
            entity.world.dropItemNaturally(entity.location.add(0.0, 0.5, 0.0), stack)
        }
        event.droppedExp = if (session != null) {
            calculateArenaDroppedExp(session, entity.uniqueId, normalizedTypeId)
        } else {
            0
        }
    }

    private fun buildConfiguredAdditionalDrops(killer: Player, mobTypeId: String, lootingLevel: Int): List<ItemStack> {
        val entries = dropConfig.byMobDefinition[mobTypeId]?.items.orEmpty()

        return entries.mapNotNull { entry ->
            if (random.nextDouble() >= entry.chance) return@mapNotNull null
            val amount = applyAmountModifiers(entry.minAmount, entry.maxAmount, lootingLevel)
            resolveConfiguredDrop(entry.itemId, killer, amount)
        }
    }

    private fun calculateArenaDroppedExp(session: ArenaSession, entityId: UUID, mobDefinitionId: String): Int {
        val baseExp = dropConfig.byMobDefinition[mobDefinitionId]?.baseExp ?: 0
        if (baseExp <= 0) {
            return 0
        }
        val variant = themeLoader.getTheme(session.themeId)?.variant(session.promoted) ?: return baseExp
        val wave = session.mobWaveMap[entityId]
            ?: session.currentWave.takeIf { it > 0 }
            ?: 1
        val multiplier = 1.0 +
            variant.difficultyExpBonusRate +
            ((wave.coerceAtLeast(1) - 1) * variant.waveExpBonusRateIncrement)
        return floor(baseExp * multiplier.coerceAtLeast(0.0)).toInt().coerceAtLeast(0)
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
        val lootingBonus = if (lootingLevel > 0) random.nextInt(lootingLevel + 1) else 0
        return (base + lootingBonus).coerceAtLeast(1)
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

    private fun createEnchantShardDrop(killer: Player, mobDefinitionId: String): ItemStack? {
        val missionService = arenaMissionService ?: return null
        val candidateDefinitions = ArenaEnchantShardRegistry.getDropDefinitionsForMob(mobDefinitionId)
        if (candidateDefinitions.isEmpty()) {
            return null
        }

        val evaluated = candidateDefinitions.mapNotNull { definition ->
            val baseChance = definition.baseDropChance ?: return@mapNotNull null
            val previousCount = missionService.getEnchantShardKillCount(killer.uniqueId, definition.key, mobDefinitionId)
            val attemptCount = previousCount + 1
            val finalChance = ArenaEnchantShardRegistry.calculateDropChance(baseChance, attemptCount)
            EvaluatedEnchantShardCandidate(definition, attemptCount, random.nextDouble() < finalChance)
        }
        if (evaluated.isEmpty()) {
            return null
        }

        val successful = evaluated.filter { it.success }
        val selected = successful.ifEmpty { null }?.let { it[random.nextInt(it.size)] }
        missionService.recordEnchantShardAttempts(
            playerId = killer.uniqueId,
            mobDefinitionId = mobDefinitionId,
            attemptCounts = evaluated.associate { it.definition.key to it.attemptCount },
            droppedShardKey = selected?.definition?.key
        )

        return selected?.let { ArenaEnchantShardItem.createShard(killer, it.definition, 1) }
    }

    private data class EvaluatedEnchantShardCandidate(
        val definition: ArenaEnchantShardDefinition,
        val attemptCount: Int,
        val success: Boolean
    )

    private fun resolveMobTokenCategoryTypeId(mobTypeId: String): String {
        val normalized = sanitizeMobTypeId(mobTypeId)
        mobService.resolveRewardCategoryId(normalized)?.let { return it }

        val baseEntityType = mobService.resolveMobType(normalized)?.baseEntityType
        if (baseEntityType != null) {
            return ArenaMobTokenItem.resolveTokenCategoryTypeId(baseEntityType.name)
        }

        val fallbackEntityType = runCatching { EntityType.valueOf(normalized.uppercase(Locale.ROOT)) }.getOrNull()
        if (fallbackEntityType != null) {
            return ArenaMobTokenItem.resolveTokenCategoryTypeId(fallbackEntityType.name)
        }

        return ArenaMobTokenItem.resolveTokenCategoryTypeId(normalized)
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
                plugin.logger.warning("[Arena] mob token language file could not be loaded: locale=$locale")
                return@forEach
            }

            val missingKeys = mutableListOf<String>()
            if (!config.isList("custom_items.arena.mob_token.lore")) {
                missingKeys += "custom_items.arena.mob_token.lore"
            }

            requiredTypeIds.forEach { typeId ->
                val normalizedTypeId = normalizeMobTokenLanguageTypeId(typeId)
                val key = "custom_items.arena.mob_token.token_names.$normalizedTypeId"
                if (!config.isString(key)) {
                    missingKeys += key
                }
            }

            if (missingKeys.isNotEmpty()) {
                plugin.logger.warning("[Arena] mob token language file could not be loaded: locale=$locale")
                missingKeys.forEach { key ->
                    plugin.logger.warning("[Arena]   - $key")
                }
            }
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
            val fromDataFolder = langDir.listFiles { file -> file.isDirectory }
                .orEmpty()
                .map { it.name.lowercase(Locale.ROOT) }
            locales.addAll(fromDataFolder)
        }

        return locales
    }

    private fun loadLangConfig(locale: String): YamlConfiguration? {
        val normalized = locale.lowercase(Locale.ROOT)
        val fromDataFolder = File(plugin.dataFolder, "lang/$normalized/custom_items.yml")
        if (fromDataFolder.exists()) {
            return YamlConfiguration.loadConfiguration(fromDataFolder)
        }

        val resource = plugin.getResource("lang/$normalized/custom_items.yml") ?: return null
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
        session.playerWaveCatchupDeadlineMillis.remove(playerId)
        clearReviveBindingByReviver(session, playerId)
        clearDownedState(session, playerId)
        session.reviveCountByPlayer.remove(playerId)
        session.invitedParticipants.remove(playerId)
        session.waitingParticipants.remove(playerId)
        session.waitingNotifiedParticipants.remove(playerId)
        session.waitingSubtitleNextTickByPlayer.remove(playerId)
        session.waitingOutsideTicksByPlayer.remove(playerId)
        session.entranceLiftLockedParticipants.remove(playerId)
        session.arenaPreparingUntilMillisByParticipant.remove(playerId)
        hideJoinCountdownBossBar(session, playerId)
        releaseInvitedPlayerLock(playerId)
        removeArenaSidebar(playerId)
        confusionManager.removeConfusion(playerId)

        val player = Bukkit.getPlayer(playerId)
        if (player != null && player.isOnline) {
            stopArenaBgmForParticipant(session, playerId)
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
            terminateSession(session, session.missionCompleted)
        }
        return true
    }

    private fun terminateSession(
        session: ArenaSession,
        success: Boolean,
        messageKey: String? = null,
        duringShutdown: Boolean = false,
        vararg messagePlaceholders: Pair<String, Any?>
    ) {
        transitionSessionPhase(session, ArenaPhase.TERMINATING)
        Bukkit.getPluginManager().callEvent(
            ArenaSessionEndedEvent(
                ownerPlayerId = session.ownerPlayerId,
                worldName = session.worldName,
                themeId = session.themeId,
                promoted = session.promoted,
                starCount = session.difficultyStar,
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
            if (player == null || !player.isOnline) {
                pendingLobbyReturnPlayers.add(participantId)
                return@forEach
            }
            if (duringShutdown || shuttingDown) {
                stopArenaBgmForPlayer(player)
            } else {
                scheduleBoundaryStopArenaBgmForPlayer(session, player)
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
            if (messageKey != null) {
                player.sendMessage(ArenaI18n.text(player, messageKey, *messagePlaceholders))
                if (success) {
                    player.sendMessage(
                        ArenaI18n.text(player, "arena.messages.session.retry_hint")
                    )
                }
            }
        }

        session.participants.clear()
        if (session.lobbyVisitedParticipants.isNotEmpty()) {
            lobbyVisitedParticipants.addAll(session.lobbyVisitedParticipants)
        }
        if (session.tutorialCompletedParticipants.isNotEmpty()) {
            tutorialCompletedParticipants.addAll(session.tutorialCompletedParticipants)
        }
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
        session.arenaBgmMode = ArenaBgmMode.STOPPED
        session.arenaBgmSwitchRequest = null
        session.arenaBgmModeStartedTick = 0L
        session.downedOriginalWalkSpeeds.clear()
        session.downedOriginalJumpStrengths.clear()
        session.invitedParticipants.clear()
        session.sidebarParticipantOrder.clear()
        session.sidebarParticipantNames.clear()
        session.waitingParticipants.clear()
        session.waitingNotifiedParticipants.clear()
        session.waitingSubtitleNextTickByPlayer.clear()
        session.waitingOutsideTicksByPlayer.clear()
        session.arenaPreparingUntilMillisByParticipant.clear()
        session.entranceLiftLockedParticipants.clear()
        session.joinCountdownBossBars.clear()
        session.joinAreaMarkerLocations.clear()
        releaseOccupiedLiftMarkers(session)
        liftOccupiedWaiters.clear()
        session.liftMarkerLocations.clear()
        session.lobbyMarkerLocations.clear()
        session.lobbyMainMarkerLocations.clear()
        session.lobbyTutorialStartMarkerLocations.clear()
        session.lobbyTutorialStepMarkerLocations.clear()
        session.lobbyVisitedParticipants.clear()
        session.tutorialCompletedParticipants.clear()
        session.tutorialProgressByParticipant.clear()
        session.tutorialActiveParticipants.clear()
        session.tutorialMarkers.clear()
        session.tutorialHoldStates.clear()
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
            val wasEntityMob = entityMobToSessionWorld.containsKey(mobId)
            mobToSessionWorld.remove(mobId)
            entityMobToSessionWorld.remove(mobId)
            mobToDefinitionTypeId.remove(mobId)
            entityMobToDefinitionTypeId.remove(mobId)
            session.mobWaveMap.remove(mobId)
            session.mobDamagedParticipants.remove(mobId)
            val entity = Bukkit.getEntity(mobId)
            if (entity != null && entity.isValid && !entity.isDead) {
                spawnMobDisappearSmoke(entity.location)
            }
            if (wasEntityMob) {
                mobService.despawnTrackedEntityMob(mobId)
            } else {
                mobService.despawnTrackedMob(mobId)
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
            logArenaPoolState("驛｢・ｧ繝ｻ・ｻ驛｢譏ｴ繝ｻ邵ｺ蜥擾ｽｹ譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ鬩搾ｽｨ郢ｧ繝ｻ・ｽ・ｺ郢晢ｽｻ, worldName")
            return
        }

        cleanupNonPlayerEntities(world)

        val cleanupBounds = collectCleanupBounds(session)
        if (cleanupBounds.isEmpty()) {
            markArenaWorldReady(worldName)
            logArenaPoolState("驛｢・ｧ繝ｻ・ｻ驛｢譏ｴ繝ｻ邵ｺ蜥擾ｽｹ譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ鬩搾ｽｨ郢ｧ繝ｻ・ｽ・ｺ郢晢ｽｻ, worldName")
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
        logArenaPoolState("驛｢・ｧ繝ｻ・ｻ驛｢譏ｴ繝ｻ邵ｺ蜥擾ｽｹ譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ鬩搾ｽｨ郢ｧ繝ｻ・ｽ・ｺ郢晢ｽｻ, worldName")
        val estimatedSeconds = estimateCleanupSeconds(totalBlocks)
        plugin.logger.info(
            "[Arena] 驛｢・ｧ繝ｻ・ｯ驛｢譎｢・ｽ・ｪ驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｳ驛｢・ｧ繝ｻ・｢驛｢譏ｴ繝ｻ郢晢ｽｻ鬯ｮ・｢陷ｿ・･繝ｻ・ｧ郢晢ｽｻ world=$worldName blocks=$totalBlocks " +
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
        session.transitBounds.values.forEach { value ->
            bounds[boundsKey(value)] = value
        }
        session.pedestalBounds.values.forEach { value ->
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
        theme: ArenaTheme
    ) {
        session.stageMaxAliveCount = calculateStageMaxAliveCount(session, theme)
        updateCurrentWave(session)
    }

    private fun startWave(
        session: ArenaSession,
        wave: Int,
        theme: ArenaTheme
    ) {
        if (wave <= 0 || wave > session.waves) return
        if (session.startedWaves.contains(wave)) return
        if (session.clearedWaves.contains(wave)) return

        session.waveClearReminderTasks.remove(wave - 1)?.cancel()

        val spawns = session.roomMobSpawns[wave].orEmpty()
        val waveRule = waveSpawnRule(session, theme, wave) ?: return
        val candidates = selectSpawnCandidates(theme, session.promoted, wave)
        val isFinalWaveBarrierObjective = wave == session.waves && hasBarrierActivationObjective(session)
        if (candidates.isEmpty()) {
            plugin.logger.severe(
                "[Arena] 鬯ｮ・｢陷ｿ・･繝ｻ・ｧ陋滂ｽｶ繝ｻ・ｸ陝蠑ｱ繝ｻ驛｢・ｧ繝ｻ・ｦ驛｢・ｧ繝ｻ・ｧ驛｢譎｢・ｽ・ｼ驛｢譎・§繝ｻ螳夲ｽｮﾂ隲幢ｽｷ郢晢ｽｻ: world=${session.worldName} theme=${theme.id} wave=$wave"
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
                "[Arena] wave=$wave 驍ｵ・ｺ繝ｻ・ｮ mob 驛｢・ｧ繝ｻ・ｹ驛｢譎・ｺ｢郢晢ｽｻ驛｢譎｢・ｽ・ｳ髣厄ｽｴ陷･・ｲ繝ｻ・ｽ繝ｻ・ｮ驍ｵ・ｺ郢晢ｽｻ髣比ｼ夲ｽｽ・ｶ驍ｵ・ｺ繝ｻ・ｮ驍ｵ・ｺ雋・∞・ｽ繝ｻ譏弱・・ｪ髯ｷ蟠趣ｽｼ譁撰ｿ驛｢譎｢・ｽ・ｪ驛｢・ｧ繝ｻ・｢驍ｵ・ｺ陷会ｽｱ遶擾ｽｪ驍ｵ・ｺ陷ｷ・ｶ・つ郢晢ｽｻ " +
                    " 髯昴・・ｽ・ｾ鬮ｮ雜｣・ｽ・｡鬯ｩ蟷｢・ｽ・ｨ髯橸ｽｻ闕ｵ譏ｶ繝ｻ 'arena.marker.mob' 驛｢・ｧ郢晢ｽｻ髯区ｺｷﾂ・ｶ繝ｻ・ｻ繝ｻ・･髣包ｽｳ闔ｨ竏壹・鬩励ｑ・ｽ・ｮ驍ｵ・ｺ陷会ｽｱ遯ｶ・ｻ驍ｵ・ｺ闕ｳ蟯ｩ蜻ｳ驍ｵ・ｺ髴郁ｲｻ・ｼ繝ｻ world=${session.worldName}"
            )
            session.startedWaves.add(wave)
            if (isFinalWaveBarrierObjective) {
                updateCurrentWave(session)
            } else {
                clearWave(session, wave)
            }
            return
        }

        val clearTarget = calculateWaveCount(waveRule.clearMobCount, session.missionModifiers.clearMobCountMultiplier, session.sessionVariance)

        session.lastClearedWaveForBossBar = null
        session.startedWaves.add(wave)
        if (wave == session.waves && session.finalWaveStartedAtMillis <= 0L) {
            session.finalWaveStartedAtMillis = System.currentTimeMillis()
            session.overtimeDischargeState = ArenaOvertimeDischargeState(
                nextTriggerAtMillis = session.finalWaveStartedAtMillis + FINAL_WAVE_OVERTIME_TRIGGER_DELAY_MILLIS,
                triggerCount = 0
            )
        }

        // 髫ｰ蜉ｱ繝ｻ繝ｻ・ｨ陟托ｽｱ・朱・・ｹ譏ｴ繝ｻ邵ｺ蜥擾ｽｹ譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ: 髫ｴ蟠｢ﾂ鬩搾ｽｨ郢ｧ繝ｻ竏ｴ驛｢・ｧ繝ｻ・ｧ驛｢譎｢・ｽ・ｼ驛｢譎・§邵ｲ蝣､・ｹ譎・鯵邵ｺ蟶ｷ・ｹ・ｧ陋幢ｽｵ邵ｺ蟶ｷ・ｹ譎・ｺ｢郢晢ｽｻ驛｢譎｢・ｽ・ｳ
        if (wave == session.waves && isClearingMission(session)) {
            spawnClearingBosses(session)
        }

        requestWaveStartCombatBgm(session)
        session.waveKillCount.putIfAbsent(wave, 0)
        session.waveClearTargets[wave] = clearTarget
        session.waveMobIds.putIfAbsent(wave, mutableSetOf())

        startSpawnLoop(session, wave, theme, spawns)
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

    private fun calculateWaveCount(base: Int, missionMultiplier: Double, sessionVariance: Double = 1.0): Int {
        val scaled = base * missionMultiplier * sessionVariance
        return ceil(scaled).toInt().coerceAtLeast(1)
    }

    private fun startSpawnLoop(
        session: ArenaSession,
        wave: Int,
        theme: ArenaTheme,
        spawns: List<Location>,
        intervalScale: Double = 1.0
    ) {
        val waveRule = waveSpawnRule(session, theme, wave) ?: return
        val interval = (waveRule.spawnIntervalTicks * session.sessionVariance * session.missionModifiers.spawnIntervalMultiplier * intervalScale)
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
            val spawnCandidates = selectSpawnCandidates(theme, currentSession.promoted, wave)
            val locationFiltered = filterSpawnCandidatesByLocation(spawnCandidates, spawnPoint)
            val candidates = filterSpawnCandidatesByMaxAlive(currentSession, locationFiltered)
            if (candidates.isEmpty()) {
                return@Runnable
            }
            val weightedMob = selectWeightedMob(candidates) ?: run {
                return@Runnable
            }
            val definition = mobDefinitions[weightedMob.mobId] ?: run {
                return@Runnable
            }

            spawnMob(world, currentSession, wave, spawnPoint, definition, weightedMob.statMultiplier)
        }, interval, interval)

        session.waveSpawnTasks[wave]?.cancel()
        session.waveSpawnTasks[wave] = task
    }

    private fun restartWaveSpawnLoopWithIntervalScale(session: ArenaSession, wave: Int, intervalScale: Double) {
        val theme = themeLoader.getTheme(session.themeId) ?: return
        val spawns = session.roomMobSpawns[wave].orEmpty()
        if (spawns.isEmpty()) return
        if (!session.startedWaves.contains(wave)) return
        if (session.waveSpawningStopped.contains(wave)) return
        if (session.clearedWaves.contains(wave)) return

        startSpawnLoop(session, wave, theme, spawns, intervalScale)
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

    private fun locateTransitWave(session: ArenaSession, location: Location): Int? {
        return session.transitBounds.entries.firstOrNull { (_, bounds) ->
            bounds.contains(location.x, location.y, location.z)
        }?.key
    }

    private fun locatePedestalWave(session: ArenaSession, location: Location): Int? {
        return session.pedestalBounds.entries.firstOrNull { (_, bounds) ->
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

    private enum class OutOfBoundsOrigin {
        ENTRANCE,
        ROOM,
        CORRIDOR,
        TRANSIT,
        PEDESTAL,
        GOAL,
        UNKNOWN
    }

    private fun teleportOnOutOfBounds(player: Player, session: ArenaSession, world: World, currentWave: Int) {
        val origin = resolveOutOfBoundsOrigin(session, player)
        val destination = resolveOutOfBoundsCheckpoint(session, world, origin)
        if (destination == null) {
            teleportToLatestDoorMarker(player, session, world, currentWave)
            return
        }
        destination.yaw = player.location.yaw
        destination.pitch = player.location.pitch
        player.teleport(destination)
        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 15, 0, false, false, false))
    }

    private fun resolveOutOfBoundsOrigin(session: ArenaSession, player: Player): OutOfBoundsOrigin {
        val latestValidLocation = session.participantLocationHistory[player.uniqueId]
            ?.lastOrNull()
            ?.location
            ?: return OutOfBoundsOrigin.UNKNOWN

        val corridorWave = locateCorridorTargetWave(session, latestValidLocation)
        if (corridorWave != null) {
            return OutOfBoundsOrigin.CORRIDOR
        }

        if (locateTransitWave(session, latestValidLocation) != null) {
            return OutOfBoundsOrigin.TRANSIT
        }

        if (locatePedestalWave(session, latestValidLocation) != null) {
            return OutOfBoundsOrigin.PEDESTAL
        }

        val room = locateRoom(session, latestValidLocation) ?: return OutOfBoundsOrigin.UNKNOWN
        return when {
            room <= 0 -> OutOfBoundsOrigin.ENTRANCE
            room >= session.waves -> OutOfBoundsOrigin.GOAL
            else -> OutOfBoundsOrigin.ROOM
        }
    }

    private fun resolveOutOfBoundsCheckpoint(
        session: ArenaSession,
        world: World,
        origin: OutOfBoundsOrigin
    ): Location? {
        return when (origin) {
            OutOfBoundsOrigin.ENTRANCE -> session.entranceCheckpoint.clone().apply { this.world = world }
            OutOfBoundsOrigin.GOAL -> session.goalCheckpoint.clone().apply { this.world = world }
            OutOfBoundsOrigin.CORRIDOR,
            OutOfBoundsOrigin.TRANSIT,
            OutOfBoundsOrigin.PEDESTAL,
            OutOfBoundsOrigin.ROOM -> resolveLatestRoomCheckpoint(session, world)
                ?: session.entranceCheckpoint.clone().apply { this.world = world }
            OutOfBoundsOrigin.UNKNOWN -> resolveLatestRoomCheckpoint(session, world)
                ?: session.entranceCheckpoint.clone().apply { this.world = world }
        }
    }

    private fun resolveLatestRoomCheckpoint(session: ArenaSession, world: World): Location? {
        val latestWave = latestEnteredWave(session) ?: return null
        val checkpoint = session.activatedRoomCheckpoints[latestWave] ?: return null
        return checkpoint.clone().apply { this.world = world }
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

        val transitWave = locateTransitWave(session, location)
        if (transitWave != null) {
            return transitWave <= currentWave
        }

        val pedestalWave = locatePedestalWave(session, location)
        if (pedestalWave != null) {
            return pedestalWave <= currentWave
        }

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
            ArenaI18n.text(player, "arena.messages.wave.last_title")
        } else {
            ArenaI18n.text(player, "arena.messages.wave.title", "wave" to wave)
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
        session.roomCheckpoints[wave]?.let { checkpoint ->
            session.activatedRoomCheckpoints[wave] = checkpoint.clone()
        }

        val catchupTargets = findPlayersInPreviousWaveOrEarlierRooms(session, wave, excludePlayerId = entrant.uniqueId)
        catchupTargets.forEach { target ->
            session.playerWaveCatchupDeadlineMillis[target.uniqueId] = now + WAVE_CATCHUP_TELEPORT_DELAY_MILLIS
        }

        session.fallbackWave = wave.coerceIn(1, session.waves)

        val theme = themeLoader.getTheme(session.themeId)
        if (theme != null) {
            startWave(session, wave, theme)
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

    private fun waveSpawnRule(session: ArenaSession, theme: ArenaTheme, wave: Int): ArenaWaveSpawnRule? {
        return theme.variant(session.promoted).waves.firstOrNull { it.wave == wave }
    }

    private fun selectSpawnCandidates(theme: ArenaTheme, promoted: Boolean, wave: Int): List<ArenaThemeWeightedMobEntry> {
        return theme.variant(promoted).waves
            .firstOrNull { it.wave == wave }
            ?.weightedMobs
            .orEmpty()
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

    private fun filterSpawnCandidatesByMaxAlive(
        session: ArenaSession,
        candidates: List<ArenaThemeWeightedMobEntry>
    ): List<ArenaThemeWeightedMobEntry> {
        if (candidates.isEmpty()) {
            return candidates
        }
        return candidates.filter { entry ->
            val maxAlive = entry.maxAlive ?: return@filter true
            val alive = countAliveMobsByDefinitionTypeId(session, entry.mobId)
            alive < maxAlive
        }
    }

    private fun countAliveMobsByDefinitionTypeId(session: ArenaSession, definitionTypeId: String): Int {
        return session.activeMobs.count { mobId ->
            val mappedType = mobToDefinitionTypeId[mobId] ?: entityMobToDefinitionTypeId[mobId]
            if (mappedType != definitionTypeId) {
                return@count false
            }
            val entity = Bukkit.getEntity(mobId)
            entity != null && entity.isValid && !entity.isDead
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
        statMultiplier: Double
    ) {
        val metadata = mapOf(
            "world" to session.worldName,
            "wave" to wave.toString(),
            "total_waves" to session.waves.toString()
        )

        val typeId = definition.typeId.trim().lowercase(Locale.ROOT)
        if (mobService.resolveMobType(typeId) != null) {
            val entity = mobService.spawn(
                definition,
                spawn,
                MobSpawnOptions(
                    featureId = "arena",
                    sessionKey = "arena:${session.worldName}",
                    combatActiveProvider = { true },
                    metadata = metadata
                )
            ) ?: return
            entity.removeWhenFarAway = false
            entity.canPickupItems = false
            enforceAdultMob(entity)
            spawnMobAppearParticles(world, entity.location)

            applyMobStats(entity, definition, statMultiplier, session.sessionVariance, session.missionModifiers)

            if (entity is Mob) {
                entity.target = findNearestParticipant(session, entity.location)
            }

            val entityId = entity.uniqueId
            session.activeMobs.add(entityId)
            session.waveMobIds.getOrPut(wave) { mutableSetOf() }.add(entityId)
            session.mobWaveMap[entityId] = wave
            mobToSessionWorld[entityId] = session.worldName
            entityMobToSessionWorld.remove(entityId)
            mobToDefinitionTypeId[entityId] = definition.typeId
            entityMobToDefinitionTypeId.remove(entityId)
            return
        }

        val entity = mobService.spawnEntity(
            definition,
            spawn,
            EntityMobSpawnOptions(
                featureId = "arena",
                sessionKey = "arena:${session.worldName}",
                combatActiveProvider = { true },
                metadata = metadata
            )
        ) ?: return

        entity.isPersistent = true
        spawnMobAppearParticles(world, entity.location)

        val entityId = entity.uniqueId
        session.activeMobs.add(entityId)
        session.waveMobIds.getOrPut(wave) { mutableSetOf() }.add(entityId)
        session.mobWaveMap[entityId] = wave
        mobToSessionWorld.remove(entityId)
        entityMobToSessionWorld[entityId] = session.worldName
        mobToDefinitionTypeId.remove(entityId)
        entityMobToDefinitionTypeId[entityId] = definition.typeId
        if (entity is LivingEntity) {
            applyMobStats(entity, definition, statMultiplier, session.sessionVariance, session.missionModifiers)
        }
    }

    private fun applyMobStats(
        entity: LivingEntity,
        definition: MobDefinition,
        statMultiplier: Double,
        sessionVariance: Double,
        missionModifiers: ArenaMissionModifiers
    ) {
        val maxHealth = (definition.health * statMultiplier * sessionVariance * missionModifiers.mobHealthMultiplier)
            .coerceAtLeast(1.0)
        val attack = (definition.attack * statMultiplier * sessionVariance * missionModifiers.mobAttackMultiplier)
            .coerceAtLeast(0.0)
        val speed = (definition.movementSpeed * statMultiplier * sessionVariance).coerceAtLeast(0.01)
        val armor = (definition.armor * statMultiplier * sessionVariance).coerceAtLeast(0.0)

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

    private fun consumeMob(session: ArenaSession, mobId: UUID, countKill: Boolean) {
        val removed = session.activeMobs.remove(mobId)
        if (!removed) return

        val wasEntityMob = entityMobToSessionWorld.containsKey(mobId)
        mobToSessionWorld.remove(mobId)
        entityMobToSessionWorld.remove(mobId)
        mobToDefinitionTypeId.remove(mobId)
        entityMobToDefinitionTypeId.remove(mobId)
        val wave = session.mobWaveMap.remove(mobId)
        session.mobDamagedParticipants.remove(mobId)
        if (wave != null) {
            session.waveMobIds[wave]?.remove(mobId)
            tryQueueWaveClearedMessage(session, wave)
        }

        schedulePendingWaveClearedMessageIfReady(session)

        // 髫ｰ蜉ｱ繝ｻ繝ｻ・ｨ陟托ｽｱ郢晢ｽｻ驛｢・ｧ繝ｻ・ｹ髮弱・・ｽ・ｻ髣費｣ｰ繝ｻ・｡驛｢譏ｶ繝ｻ邵ｺ閾･・ｹ譏ｴ繝ｻ邵ｺ繝ｻ
        if (session.clearingBossEntityIds.contains(mobId)) {
            session.clearingBossEntityIds.remove(mobId)
            if (checkClearingBossesDefeated(session)) {
                onClearingBossDefeated(session)
                return
            }
        }

        if (!countKill || wave == null) {
            return
        }

        if (wasEntityMob) {
            return
        }

        if (isClearingMission(session) && wave == session.waves) {
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
        if (isClearingMission(session)) {
            return false
        }
        return session.actionMarkers.values.any { it.type == ArenaActionMarkerType.BARRIER_ACTIVATE }
    }

    private fun isClearingMission(session: ArenaSession): Boolean {
        return session.missionTypeId == ArenaMissionType.CLEARING
    }

    private fun spawnClearingBosses(session: ArenaSession) {
        if (!isClearingMission(session)) return
        if (session.clearingBossSpawned) return
        if (session.clearingBossLocations.isEmpty()) {
            plugin.logger.warning("[Arena] CLEARING 驛｢譎・ｽｺ蛟･ﾎ暮Δ・ｧ繝ｻ・ｷ驛｢譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ驍ｵ・ｺ繝ｻ・ｧ驍ｵ・ｺ陷ｷ・ｶ遯ｶ・ｲ clearingBossLocations 驍ｵ・ｺ隶呵ｶ｣・ｽ・ｩ繝ｻ・ｺ驍ｵ・ｺ繝ｻ・ｧ驍ｵ・ｺ郢晢ｽｻ world=${session.worldName}")
            return
        }

        val themeConfig = themeLoader.getTheme(session.themeId)?.config(session.promoted)
        val bossMobId = if (!themeConfig?.clearingBossMobId.isNullOrBlank()) {
            themeConfig!!.clearingBossMobId!!
        } else {
            plugin.logger.warning("[Arena] CLEARING 驛｢譎・ｽｺ蛟･ﾎ暮Δ・ｧ繝ｻ・ｷ驛｢譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ: clearingBossMobId 驍ｵ・ｺ隴ｴ・ｧ隰費ｽｴ髯橸ｽｳ陞溘ｑ・ｽ・ｾ繝ｻ・ｩ驍ｵ・ｺ繝ｻ・ｮ驍ｵ・ｺ雋・∞・ｽ竏ｫ・ｹ譏ｴ繝ｻ郢晢ｽｵ驛｢・ｧ繝ｻ・ｩ驛｢譎｢・ｽ・ｫ驛｢譏ｴ繝ｻobId=zombie 驛｢・ｧ陷代・・ｽ・ｽ繝ｻ・ｿ鬨ｾ蛹・ｽｽ・ｨ: theme=${session.themeId}")
            "zombie"
        }

        val definition = mobDefinitions[bossMobId]
        if (definition == null) {
            plugin.logger.severe("[Arena] clearingBossMobId=$bossMobId is not defined in mob definitions.")
            return
        }

        val world = Bukkit.getWorld(session.worldName)
        if (world == null) {
            plugin.logger.severe("[Arena] 髫ｰ蜉ｱ繝ｻ繝ｻ・ｨ陟托ｽｱ郢晢ｽｻ驛｢・ｧ繝ｻ・ｹ驛｢・ｧ繝ｻ・ｹ驛｢譎・ｺ｢郢晢ｽｻ驛｢譎｢・ｽ・ｳ髯樊ｻゑｽｽ・ｱ髫ｰ・ｨ郢晢ｽｻ 驛｢譎｢・ｽ・ｯ驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｫ驛｢譎擾ｽｳ・ｨ遯ｶ・ｲ鬮ｫ遨ゑｽｹ譏ｶ蜻ｽ驍ｵ・ｺ闕ｵ譎｢・ｽ鬘費ｽｸ・ｺ繝ｻ・ｾ驍ｵ・ｺ陝ｶ蜻ｻ・ｽ繝ｻworld=${session.worldName}")
            return
        }

        // 髫ｰ蜉ｱ繝ｻ繝ｻ・ｨ陟托ｽｱ・朱・・ｹ譏ｴ繝ｻ邵ｺ蜥擾ｽｹ譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ鬨ｾ蛹・ｽｽ・ｨ驍ｵ・ｺ繝ｻ・ｮ髫ｴ蠑ｱ・玖将・｣髯具ｽｻ繝ｻ・ｶ鬯ｮ・ｯ髣企ｯ会ｽｽ蟶晏搦繝ｻ・ｭ髯橸ｽｳ郢晢ｽｻ
        session.clearingBossTimeLimitSeconds = calculateClearingBossTimeLimitSeconds(session.difficultyStar)

        session.clearingBossLocations.forEach { location ->
            val spawnLocation = location.clone().apply { this.world = world }
            spawnLocation.add(0.5, 0.0, 0.5)

            val entity = mobService.spawn(
                definition,
                spawnLocation,
                MobSpawnOptions(
                    featureId = "arena",
                    sessionKey = "arena:${session.worldName}",
                    combatActiveProvider = { true },
                    metadata = mapOf(
                        "world" to session.worldName,
                        "wave" to session.waves.toString(),
                        "total_waves" to session.waves.toString(),
                        "clearing_boss" to "true"
                    )
                )
            )

            if (entity != null) {
                entity.removeWhenFarAway = false
                entity.canPickupItems = false
                enforceAdultMob(entity)
                spawnMobAppearParticles(world, entity.location)

                // 驛｢譎・鯵邵ｺ蟶ｷ・ｸ・ｺ繝ｻ・ｮ驛｢・ｧ繝ｻ・ｹ驛｢譏ｴ繝ｻ郢晢ｽｻ驛｢・ｧ繝ｻ・ｿ驛｢・ｧ繝ｻ・ｹ驛｢・ｧ陝ｶ譏ｶ繝ｻ鬨ｾ蛹・ｽｽ・ｨ郢晢ｽｻ騾趣ｽｯ・つ陞｢・ｼ繝ｻ・ｸ繝ｻ・ｸ驍ｵ・ｺ繝ｻ・ｮ驛｢譎｢・ｽ・｢驛｢譎・§遶雁､・ｸ・ｺ繝ｻ・ｯ鬨ｾ・｡繝ｻ・ｰ驍ｵ・ｺ繝ｻ・ｪ驛｢・ｧ髣・ｽ･P髣厄ｽｫ繝ｻ・ｮ鬯ｯ貊ゑｽｽ・ｾ髯昴・繝ｻ郢晢ｽｻ鬯ｩ蛹・ｽｽ・ｩ鬨ｾ蛹・ｽｽ・ｨ驍ｵ・ｺ陷会ｽｱ遶企・・ｸ・ｺ郢晢ｽｻ繝ｻ・ｼ郢晢ｽｻ
                applyMobStats(entity, definition, 1.0, session.sessionVariance, ArenaMissionModifiers.NONE)

                if (entity is Mob) {
                    entity.target = findNearestParticipant(session, entity.location)
                }

                session.clearingBossEntityIds.add(entity.uniqueId)
                session.activeMobs.add(entity.uniqueId)
                mobToSessionWorld[entity.uniqueId] = session.worldName
                entityMobToSessionWorld.remove(entity.uniqueId)
                mobToDefinitionTypeId[entity.uniqueId] = definition.typeId.trim().lowercase(Locale.ROOT)
                entityMobToDefinitionTypeId.remove(entity.uniqueId)
                plugin.logger.info("[Arena] 髫ｰ蜉ｱ繝ｻ繝ｻ・ｨ陟托ｽｱ郢晢ｽｻ驛｢・ｧ繝ｻ・ｹ驛｢・ｧ陋幢ｽｵ邵ｺ蟶ｷ・ｹ譎・ｺ｢郢晢ｽｻ驛｢譎｢・ｽ・ｳ: mobId=$bossMobId location=${spawnLocation.blockX},${spawnLocation.blockY},${spawnLocation.blockZ}")
            } else {
                plugin.logger.warning("[Arena] 髫ｰ蜉ｱ繝ｻ繝ｻ・ｨ陟托ｽｱ郢晢ｽｻ驛｢・ｧ繝ｻ・ｹ驍ｵ・ｺ繝ｻ・ｮ驛｢・ｧ繝ｻ・ｹ驛｢譎・ｺ｢郢晢ｽｻ驛｢譎｢・ｽ・ｳ驍ｵ・ｺ繝ｻ・ｫ髯樊ｻゑｽｽ・ｱ髫ｰ・ｨ郢晢ｽｻ mobId=$bossMobId")
            }
        }

        session.clearingBossSpawned = true
        session.clearingBossTotalCount = session.clearingBossEntityIds.size
        broadcastClearingBossSpawnedMessage(session)
        updateSessionProgressBossBar(session)
    }

    private fun broadcastClearingBossSpawnedMessage(session: ArenaSession) {
        val world = Bukkit.getWorld(session.worldName) ?: return
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId)
            if (player != null && player.isOnline && player.world.name == session.worldName) {
                player.sendMessage(
                    ArenaI18n.text(player, "arena.messages.clearing.boss_spawned")
                )
                player.playSound(player.location, Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.2f)
            }
        }
    }

    private fun checkClearingBossesDefeated(session: ArenaSession): Boolean {
        if (!isClearingMission(session)) return false
        if (!session.clearingBossSpawned) return false
        if (session.clearingBossEntityIds.isEmpty()) return true

        val allDefeated = session.clearingBossEntityIds.all { bossId ->
            val entity = Bukkit.getEntity(bossId)
            entity == null || entity.isDead || !entity.isValid
        }

        return allDefeated
    }

    private fun onClearingBossDefeated(session: ArenaSession) {
        if (session.phase == ArenaPhase.TERMINATING) return

        plugin.logger.info("[Arena] 髫ｰ蜉ｱ繝ｻ繝ｻ・ｨ陟托ｽｱ郢晢ｽｻ驛｢・ｧ繝ｻ・ｹ髯ｷ闌ｨ・ｽ・ｨ髮狗ｿｫ繝ｻ world=${session.worldName}")

        completeClearingMission(session)
    }

    private fun completeClearingMission(session: ArenaSession) {
        if (session.missionCompleted) return

        requestArenaBgmMode(session, ArenaBgmMode.STOPPED, strictNextBoundary = true)

        session.missionCompleted = true
        session.finalWaveStartedAtMillis = 0L
        stopWaveSpawning(session, session.waves)

        removeRemainingWaveMobs(session)
        removeAllMobsInArenaWorld(session, smoke = true)
        session.lastClearedWaveForBossBar = session.waves
        updateSessionProgressBossBar(session)
        updateArenaSidebars()

        val world = Bukkit.getWorld(session.worldName)

        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId)
            if (player != null && player.isOnline && player.world.name == session.worldName) {
                player.sendMessage(
                    ArenaI18n.text(player, "arena.messages.clearing.success")
                )
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f)
                player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
                if (world != null) {
                    spawnMobAppearParticles(world, player.location)
                }
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (!activeSession.missionCompleted) return@Runnable
            activeSession.participants.forEach { participantId ->
                val player = Bukkit.getPlayer(participantId)
                if (player != null && player.isOnline && player.world.name == activeSession.worldName) {
                    player.sendTitle(
                        "",
                        ArenaI18n.text(player, "arena.messages.mission.return_hint"),
                        0,
                        50,
                        10
                    )
                }
            }
        }, 200L)
    }

    private fun calculateClearingBossTimeLimitSeconds(starLevel: Int): Int {
        // 鬯ｮ・ｮ繝ｻ・｣髫ｴ蜀猟ｧ繝ｻ・ｺ繝ｻ・ｦID驍ｵ・ｺ闕ｵ譎｢・ｽ閾･・ｬ蛟･繝ｻ郢晢ｽｻ髫ｰ・ｨ繝ｻ・ｰ驛｢・ｧ髮区ｧｫ蠕宣辧蠅灘惧繝ｻ・ｼ郢晢ｽｻtar_1, star_2, etc.郢晢ｽｻ郢晢ｽｻ
        return when (starLevel) {
            1 -> 600   // 髫ｨ蛟･繝ｻ: 10髯具ｽｻ郢晢ｽｻ
            2 -> 480   // 髫ｨ蛟･繝ｻ: 8髯具ｽｻ郢晢ｽｻ
            3 -> 300   // 髫ｨ蛟･繝ｻ: 5髯具ｽｻ郢晢ｽｻ
            4 -> 240   // 髫ｨ蛟･繝ｻ: 4髯具ｽｻ郢晢ｽｻ
            else -> 600
        }
    }

    private fun formatTimeRemaining(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(minutes, secs)
    }

    private fun getClearingBossTimeRemainingSeconds(session: ArenaSession): Int {
        if (!isClearingMission(session)) return -1
        if (!session.clearingBossSpawned) return -1
        if (session.finalWaveStartedAtMillis <= 0L) return session.clearingBossTimeLimitSeconds

        val elapsedMillis = (System.currentTimeMillis() - session.finalWaveStartedAtMillis).coerceAtLeast(0L)
        val elapsedSeconds = (elapsedMillis / 1000).toInt()
        val remaining = (session.clearingBossTimeLimitSeconds - elapsedSeconds).coerceAtLeast(0)
        return remaining
    }

    private fun isClearingBossTimeExpired(session: ArenaSession): Boolean {
        if (!isClearingMission(session)) return false
        if (!session.clearingBossSpawned) return false
        return getClearingBossTimeRemainingSeconds(session) <= 0
    }

    private fun onClearingBossTimeExpired(session: ArenaSession) {
        if (session.phase == ArenaPhase.TERMINATING) return
        if (session.phase == ArenaPhase.GAME_OVER) return

        plugin.logger.info("[Arena] 髫ｰ蜉ｱ繝ｻ繝ｻ・ｨ陟托ｽｱ・朱・・ｹ譏ｴ繝ｻ邵ｺ蜥擾ｽｹ譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ髫ｴ蠑ｱ・玖将・｣髯具ｽｻ郢晢ｽｻ繝ｻ繝ｻ world=${session.worldName}")

        stopWaveSpawning(session, session.waves)

        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId)
            if (player != null && player.isOnline && player.world.name == session.worldName) {
                player.sendMessage(
                    ArenaI18n.text(player, "arena.messages.clearing.time_expired")
                )
                player.playSound(player.location, Sound.ENTITY_WITHER_DEATH, 0.7f, 0.5f)
            }
        }

        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId)
            if (player != null && player.isOnline && player.world.name == session.worldName) {
                if (!isPlayerDowned(session, participantId)) {
                    setPlayerDown(session, player, reviveDisabled = true)
                }
            }
        }
        convertDownedPlayersToGameOver(session)
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
                            "[Arena] 驛｢譎擾ｽｳ・ｨ邵ｺ繝ｻ・ｹ・ｧ繝ｻ・｢驛｢譏懶ｽｹ譁滄豪・ｹ譎・ｽｼ驥・ｨ抵ｽｹ譎｢・ｽ・ｼ驛｢譎｢・｣・ｰ鬯ｩ蛹・ｽｽ・ｩ鬨ｾ蛹・ｽｽ・ｨ驍ｵ・ｺ繝ｻ・ｫ髯樊ｻゑｽｽ・ｱ髫ｰ・ｨ郢晢ｽｻ world=${currentSession.worldName} wave=$targetWave frame=$targetFrame error=${throwable.message}"
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
                                "[Arena] 驛｢譎擾ｽｳ・ｨ邵ｺ繝ｻ・ｹ・ｧ繝ｻ・｢驛｢譏懶ｽｹ譁滓･｢・ｭ蟠｢ﾂ鬩搾ｽｨ郢ｧ繝ｻﾎｨ驛｢譎｢・ｽ・ｬ驛｢譎｢・ｽ・ｼ驛｢譎｢・｣・ｰ鬯ｩ蛹・ｽｽ・ｩ鬨ｾ蛹・ｽｽ・ｨ驍ｵ・ｺ繝ｻ・ｫ髯樊ｻゑｽｽ・ｱ髫ｰ・ｨ郢晢ｽｻ world=${currentSession.worldName} wave=$targetWave error=${throwable.message}"
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
            session.firstDoorOpenedAtMillis = System.currentTimeMillis()
            transitionSessionPhase(session, ArenaPhase.IN_PROGRESS)
        }
        playDoorAnimationSound(session)
        plugin.logger.info("[Arena] door animation start: world=${session.worldName} target_wave=$targetWave")
    }

    private fun calculateStageMaxAliveCount(
        session: ArenaSession,
        theme: ArenaTheme
    ): Int {
        val finalWave = waveSpawnRule(session, theme, session.waves) ?: return 1
        val calculated = calculateWaveCount(
            finalWave.maxAlive,
            session.missionModifiers.maxSummonCountMultiplier,
            session.sessionVariance
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
            if (placement.mirrored) Mirror.LEFT_RIGHT else Mirror.NONE,
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
            val wasEntityMob = entityMobToSessionWorld.containsKey(mobId)
            mobToSessionWorld.remove(mobId)
            entityMobToSessionWorld.remove(mobId)
            mobToDefinitionTypeId.remove(mobId)
            entityMobToDefinitionTypeId.remove(mobId)
            val entity = Bukkit.getEntity(mobId)
            if (entity != null && entity.isValid && !entity.isDead) {
                spawnMobDisappearSmoke(entity.location)
            }
            if (wasEntityMob) {
                mobService.despawnTrackedEntityMob(mobId)
            } else {
                mobService.despawnTrackedMob(mobId)
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
        val mob = Bukkit.getEntity(mobId) ?: return false
        if (!mob.isValid || mob.isDead) return false
        if (mob !is Mob) return false
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
        val durationMillis = ((barrierRestartConfig.defaultDurationSeconds * session.sessionVariance) * 1000.0)
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
        val waveRule = waveSpawnRule(session, theme, session.waves) ?: return
        val spawnPoints = session.roomMobSpawns[session.waves].orEmpty()
        if (spawnPoints.isEmpty()) return

        val normalInterval = (waveRule.spawnIntervalTicks * session.sessionVariance * session.missionModifiers.spawnIntervalMultiplier)
            .roundToLong()
            .coerceAtLeast(1L)
        val interval = (normalInterval / 2.0).roundToLong().coerceAtLeast(1L)

        val normalMaxAlive = calculateWaveCount(
            waveRule.maxAlive,
            session.missionModifiers.maxSummonCountMultiplier,
            session.sessionVariance
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

            spawnBarrierDefenseMob(activeSession, theme, spawnPoints)
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
        spawnPoints: List<Location>
    ) {
        val spawnThrottle = mobService.getSpawnThrottle("arena:${session.worldName}")
        val intervalChance = (1.0 / spawnThrottle.intervalMultiplier).coerceIn(0.0, 1.0)
        if (random.nextDouble() < spawnThrottle.skipChance) return
        if (random.nextDouble() > intervalChance) return

        val spawnPoint = selectSpawnPoint(spawnPoints) ?: return
        val locationFiltered = filterSpawnCandidatesByLocation(selectSpawnCandidates(theme, session.promoted, session.waves), spawnPoint)
        val candidates = filterSpawnCandidatesByMaxAlive(session, locationFiltered)
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
        applyMobStats(entity, definition, weightedMob.statMultiplier, session.sessionVariance, session.missionModifiers)
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
        arenaMissionService?.recordBarrierRestart(session.participants)

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
                    ArenaI18n.text(player, "arena.messages.barrier.restart_confirmed")
                )
                player.sendTitle(
                    "",
                    ArenaI18n.text(player, "arena.messages.barrier.return_hint"),
                    0,
                    50,
                    10
                )
            }
        }, 200L)
    }

    private fun removeRemainingWaveMobs(session: ArenaSession) {
        session.activeMobs.toList().forEach { mobId ->
            val entity = Bukkit.getEntity(mobId)
            if (entity != null && entity.isValid && !entity.isDead) {
                spawnSmoke(entity.location)
            }
            val wasEntityMob = entityMobToSessionWorld.containsKey(mobId)
            mobToSessionWorld.remove(mobId)
            entityMobToSessionWorld.remove(mobId)
            mobToDefinitionTypeId.remove(mobId)
            entityMobToDefinitionTypeId.remove(mobId)
            session.mobWaveMap.remove(mobId)
            session.mobDamagedParticipants.remove(mobId)
            if (wasEntityMob) {
                mobService.despawnTrackedEntityMob(mobId)
            } else {
                mobService.despawnTrackedMob(mobId)
            }
            session.activeMobs.remove(mobId)
        }

        session.waveMobIds.values.forEach { it.clear() }

        schedulePendingWaveClearedMessageIfReady(session)
    }

    private fun removeAllMobsInArenaWorld(session: ArenaSession, smoke: Boolean) {
        val world = Bukkit.getWorld(session.worldName) ?: return
        world.entities
            .asSequence()
            .filter { it.isValid && !it.isDead }
            .filter {
                mobToSessionWorld.containsKey(it.uniqueId) ||
                    entityMobToSessionWorld.containsKey(it.uniqueId)
            }
            .forEach { trackedEntity ->
                val mobId = trackedEntity.uniqueId
                session.activeMobs.remove(mobId)
                session.mobWaveMap.remove(mobId)
                session.mobDamagedParticipants.remove(mobId)
                session.waveMobIds.values.forEach { it.remove(mobId) }
                session.barrierDefenseMobIds.remove(mobId)
                session.barrierDefenseTargetMobIds.remove(mobId)
                session.barrierDefenseAssaultMobIds.remove(mobId)
                val wasEntityMob = entityMobToSessionWorld.containsKey(mobId)
                mobToSessionWorld.remove(mobId)
                entityMobToSessionWorld.remove(mobId)
                mobToDefinitionTypeId.remove(mobId)
                entityMobToDefinitionTypeId.remove(mobId)
                if (smoke) {
                    spawnSmoke(trackedEntity.location)
                }
                if (wasEntityMob) {
                    mobService.despawnTrackedEntityMob(mobId)
                } else {
                    mobService.despawnTrackedMob(mobId)
                }
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
            bossBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.barrier_restart")))
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
                bossBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.wave_clear", "wave" to clearedWave)))
                bossBar.color(BossBar.Color.BLUE)
                bossBar.progress(1.0f)
                return
            }

            val waveLabel = if (clearedWave >= session.waves) {
                ArenaI18n.text(null, "arena.bossbar.last_wave_label")
            } else {
                ArenaI18n.text(null, "arena.bossbar.wave_label", "wave" to clearedWave)
            }
            bossBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.wave_clear_dynamic", "waveLabel" to waveLabel)))
            bossBar.color(BossBar.Color.BLUE)
            bossBar.progress(1.0f)
            return
        }

        val wave = session.currentWave.coerceIn(1, session.waves)

        // 髫ｰ蜉ｱ繝ｻ繝ｻ・ｨ陟托ｽｱ・朱・・ｹ譏ｴ繝ｻ邵ｺ蜥擾ｽｹ譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ: 髫ｴ蟠｢ﾂ鬩搾ｽｨ郢ｧ繝ｻ竏ｴ驛｢・ｧ繝ｻ・ｧ驛｢譎｢・ｽ・ｼ驛｢譎・§邵ｲ蝣､・ｹ譎・鯵邵ｺ蟶ｷ・ｹ譎√・郢晢ｽｻ驍ｵ・ｺ繝ｻ・ｫ髫ｹ・ｿ闕ｵ譎｢・ｽ鬘假ｽｭ蠑ｱ・玖将・｣驛｢・ｧ陞ｳ螟ｲ・ｽ・｡繝ｻ・ｨ鬩穂ｼ夲ｽｽ・ｺ
        if (wave == session.waves && isClearingMission(session) && session.clearingBossSpawned) {
            val remainingSeconds = getClearingBossTimeRemainingSeconds(session)
            val timeText = formatTimeRemaining(remainingSeconds)
            val bossCount = session.clearingBossTotalCount.coerceAtLeast(1)
            val aliveBossCount = session.clearingBossEntityIds.count { bossId ->
                val entity = Bukkit.getEntity(bossId)
                entity != null && !entity.isDead && entity.isValid
            }
            val defeatedCount = bossCount - aliveBossCount
            val progress = (defeatedCount.toDouble() / bossCount.toDouble()).toFloat().coerceIn(0.0f, 1.0f)
            val barColor = if (remainingSeconds <= 60) BossBar.Color.RED else BossBar.Color.PURPLE
            bossBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.clearing_boss", "time" to timeText, "alive" to aliveBossCount)))
            bossBar.color(barColor)
            bossBar.progress(progress)
            return
        }

        if (wave == session.waves && hasBarrierActivationObjective(session)) {
            val total = session.actionMarkers.values.count { it.type == ArenaActionMarkerType.BARRIER_ACTIVATE }.coerceAtLeast(1)
            val activated = session.actionMarkers.values.count {
                it.type == ArenaActionMarkerType.BARRIER_ACTIVATE && it.state == ArenaActionMarkerState.RUNNING
            }
            val progress = (activated.toDouble() / total.toDouble()).toFloat().coerceIn(0.0f, 1.0f)
            bossBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.last_wave_progress", "activated" to activated, "total" to total)))
            bossBar.color(BossBar.Color.BLUE)
            bossBar.progress(progress)
            return
        }

        val kills = session.waveKillCount[wave] ?: 0
        val target = (session.waveClearTargets[wave] ?: 1).coerceAtLeast(1)
        val progress = (kills.toDouble() / target.toDouble()).toFloat().coerceIn(0.0f, 1.0f)
        val waveLabel = if (wave >= session.waves) {
            ArenaI18n.text(null, "arena.bossbar.last_wave_label")
        } else {
            ArenaI18n.text(null, "arena.bossbar.wave_label", "wave" to wave)
        }
        bossBar.name(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.wave_progress", "waveLabel" to waveLabel, "kills" to kills, "target" to target)))
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
            val wasEntityMob = entityMobToSessionWorld.containsKey(mobId)
            mobToSessionWorld.remove(mobId)
            entityMobToSessionWorld.remove(mobId)
            mobToDefinitionTypeId.remove(mobId)
            entityMobToDefinitionTypeId.remove(mobId)
            session.mobDamagedParticipants.remove(mobId)
            session.barrierDefenseTargetMobIds.remove(mobId)
            session.barrierDefenseAssaultMobIds.remove(mobId)

            val entity = Bukkit.getEntity(mobId)
            if (entity != null && entity.isValid && !entity.isDead) {
                if (smoke) {
                    spawnSmoke(entity.location)
                }
            }
            if (wasEntityMob) {
                mobService.despawnTrackedEntityMob(mobId)
            } else {
                mobService.despawnTrackedMob(mobId)
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
        vararg placeholders: Pair<String, Any?>
    ) {
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            player.sendMessage(ArenaI18n.text(player, key, *placeholders))
        }
    }

    private fun broadcastOageMessage(
        session: ArenaSession,
        key: String,
        vararg placeholders: Pair<String, Any?>
    ) {
        var lastMessage: String? = null
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            if (!player.isOnline || player.world.name != session.worldName) return@forEach

            val message = ArenaI18n.stringList(player, key, *placeholders).randomOrNull() ?: return@forEach
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
            "arena.messages.oage.down_with_survivors",
            "arena.messages.oage.mission_returned_followup" -> 1.0
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
        OageMessageSender.send(
            player,
            message,
            plugin,
            sound = Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM,
            volume = 1.0f,
            pitch = 1.15f
        )
    }

    private fun scheduleOageMessage(
        player: Player,
        delayTicks: Long,
        key: String,
        vararg placeholders: Pair<String, Any?>,
        force: Boolean = false
    ) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val activePlayer = Bukkit.getPlayer(player.uniqueId) ?: return@Runnable
            if (!activePlayer.isOnline) return@Runnable
            val message = ArenaI18n.stringList(activePlayer, key, *placeholders).randomOrNull() ?: return@Runnable
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
                    "arena.messages.oage.combat_all_engaged")
                noneInCombat && session.startedWaves.contains(1) -> broadcastOageMessage(
                    session,
                    "arena.messages.oage.combat_all_calm")
            }
        }
    }

    private fun broadcastStageStartMessage(session: ArenaSession) {
        if (!session.oageAnnouncements.add("stage_start")) return
        broadcastOageMessage(
            session,
            "arena.messages.oage.stage_start")
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
                "arena.messages.oage.wave_clear_wait")
        }, 5L * 60L * 20L)

        session.waveClearReminderTasks[wave] = task
    }

    private fun sendBarrierRestartOageMessages(session: ArenaSession, progress: Double) {
        val remaining = (1.0 - progress).coerceIn(0.0, 1.0)

        if (remaining <= (2.0 / 3.0) && session.oageAnnouncements.add("barrier_restart_remaining_2_3")) {
            broadcastOageMessage(
                session,
                "arena.messages.oage.barrier_restart_remaining_2_3")
        }

        if (remaining <= (1.0 / 3.0) && session.oageAnnouncements.add("barrier_restart_remaining_1_3")) {
            broadcastOageMessage(
                session,
                "arena.messages.oage.barrier_restart_remaining_1_3")
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
        val themeConfig = themeLoader.getTheme(session.themeId)?.config(session.promoted)
        val key = themeConfig?.doorOpenSound?.key ?: arenaDoorAnimationSoundKey
        val pitch = themeConfig?.doorOpenSound?.pitch ?: arenaDoorAnimationSoundPitch
        playSound(session, key, 1.0f, pitch)
    }

    private fun scheduleStartEntranceNormalBgm(session: ArenaSession, delayTicks: Long = 0L) {
        val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (!activeSession.stageStarted || activeSession.barrierRestartCompleted || activeSession.missionCompleted) {
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
                val themeName = ArenaI18n.text(player, "arena.theme.${activeSession.themeId}.name")
                player.sendTitle("", ArenaI18n.text(player, "arena.messages.session.stage_title", "theme" to themeName), 10, 60, 10)
            }
            startArenaBgmMode(activeSession, ArenaBgmMode.NORMAL, currentTick)
        }, delayTicks.coerceAtLeast(0L))
        session.transitionTasks.add(task)
    }

    private fun requestArenaBgmMode(session: ArenaSession, targetMode: ArenaBgmMode, strictNextBoundary: Boolean = false) {
        if ((session.barrierRestartCompleted || session.missionCompleted) && targetMode != ArenaBgmMode.STOPPED) {
            return
        }
        if (targetMode != ArenaBgmMode.COMBAT) {
            session.arenaWaveStartCombatDelayTask?.cancel()
            session.arenaWaveStartCombatDelayTask = null
        }

        val currentMode = session.arenaBgmMode
        val currentRequest = session.arenaBgmSwitchRequest
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
            currentAbsoluteBeat(session, track)
        }

        session.arenaBgmSwitchRequest = ArenaBgmSwitchRequest(
            targetMode = targetMode,
            requestedAtBeat = requestedAtBeat,
            strictNextBoundary = strictNextBoundary
        )
    }

    private fun requestWaveStartCombatBgm(session: ArenaSession) {
        session.arenaWaveStartCombatDelayTask?.cancel()
        session.arenaWaveStartCombatDelayTask = null

        val delayedTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            session.arenaWaveStartCombatDelayTask = null
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            requestArenaBgmMode(activeSession, ArenaBgmMode.COMBAT, strictNextBoundary = true)
        }, WAVE_START_COMBAT_BGM_DELAY_TICKS)
        session.arenaWaveStartCombatDelayTask = delayedTask
        session.transitionTasks.add(delayedTask)
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
        if (session.arenaBgmMode != ArenaBgmMode.COMBAT || session.arenaBgmSwitchRequest != null) {
            return false
        }
        return targets.all { player -> hasArenaBgmPlayback(session, player.uniqueId, ArenaBgmMode.COMBAT) }
    }

    private fun updateArenaBgmTransitions() {
        val currentTick = Bukkit.getCurrentTick().toLong()
        sessionsByWorld.values.forEach { session ->
            if (session.phase == ArenaPhase.GAME_OVER || session.phase == ArenaPhase.TERMINATING) {
                if (session.arenaBgmMode != ArenaBgmMode.STOPPED || session.arenaBgmSwitchRequest != null) {
                    stopArenaBgm(session)
                }
                return@forEach
            }

            val hasAliveCombatMob = hasAnyAliveCombatMob(session)
            if (session.stageStarted && !session.barrierRestarting && !session.barrierRestartCompleted && !session.missionCompleted) {
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

            if (!session.stageStarted) {
                return@forEach
            }

            refreshArenaBgmPlaybackState(session, currentTick)
            processArenaBgmSwitchRequest(session, currentTick)
        }
    }

    private fun refreshArenaBgmPlaybackState(session: ArenaSession, currentTick: Long) {
        val currentMode = session.arenaBgmMode
        if (currentMode == ArenaBgmMode.STOPPED) {
            return
        }
        val track = arenaBgmTrackForMode(currentMode) ?: run {
            session.arenaBgmMode = ArenaBgmMode.STOPPED
            session.arenaBgmSwitchRequest = null
            session.arenaBgmModeStartedTick = 0L
            return
        }

        val targets = arenaBgmPlaybackTargets(session)
        if (targets.isEmpty()) {
            return
        }
        val allPlaying = targets.all { player -> BGMManager.isPlaying(player, track.soundKey) }
        if (!allPlaying) {
            startArenaBgmMode(session, currentMode, currentTick)
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

    private fun processArenaBgmSwitchRequest(session: ArenaSession, currentTick: Long) {
        val request = session.arenaBgmSwitchRequest ?: return
        val currentMode = session.arenaBgmMode

        if (currentMode == ArenaBgmMode.STOPPED) {
            session.arenaBgmSwitchRequest = null
            if (request.targetMode == ArenaBgmMode.STOPPED) {
                stopArenaBgm(session)
                return
            }

            startArenaBgmMode(session, ArenaBgmMode.NORMAL, currentTick)
            if (request.targetMode == ArenaBgmMode.COMBAT) {
                requestArenaBgmMode(session, ArenaBgmMode.COMBAT, strictNextBoundary = true)
            }
            return
        }

        if (!hasSessionArenaBgmPlayback(session, currentMode)) {
            session.arenaBgmMode = ArenaBgmMode.STOPPED
            session.arenaBgmSwitchRequest = null
            session.arenaBgmModeStartedTick = 0L
            return
        }

        val currentTrack = arenaBgmTrackForMode(currentMode) ?: return
        val absoluteBeatNow = currentAbsoluteBeat(session, currentTrack)
        if (!isBeatBoundaryReached(currentTrack, absoluteBeatNow, request.requestedAtBeat, request.strictNextBoundary)) {
            return
        }

        session.arenaBgmSwitchRequest = null
        val resolvedTargetMode = request.targetMode
        if (resolvedTargetMode == ArenaBgmMode.STOPPED) {
            stopArenaBgm(session)
            return
        }
        if (resolvedTargetMode == currentMode) {
            return
        }

        startArenaBgmMode(session, resolvedTargetMode, currentTick)
    }

    private fun hasSessionArenaBgmPlayback(session: ArenaSession, mode: ArenaBgmMode): Boolean {
        val track = arenaBgmTrackForMode(mode) ?: return false
        return arenaBgmPlaybackTargets(session).any { player -> BGMManager.isPlaying(player, track.soundKey) }
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

    private fun scheduleBoundaryStopArenaBgmForPlayer(session: ArenaSession, player: Player) {
        val mode = session.arenaBgmMode
        val track = arenaBgmTrackForMode(mode)
        if (track == null) {
            stopArenaBgmForPlayer(player)
            return
        }

        val absoluteBeatNow = currentAbsoluteBeat(session, track)
        val switchInterval = track.switchIntervalBeats.toLong().coerceAtLeast(1L)
        val beatOffset = (absoluteBeatNow - 1L).mod(switchInterval)
        val beatsUntilNextBoundary = if (beatOffset == 0L) switchInterval else (switchInterval - beatOffset)
        val delayTicks = (beatsUntilNextBoundary.toDouble() * track.beatTicks)
            .roundToLong()
            .coerceAtLeast(1L)
        val playbackStartNanos = BGMManager.getPlaybackStartNanos(player)

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.isOnline && BGMManager.getPlaybackStartNanos(player) == playbackStartNanos) {
                stopArenaBgmForPlayer(player)
            }
        }, delayTicks)
    }

    private fun currentAbsoluteBeat(session: ArenaSession, track: ArenaBgmTrackConfig): Long {
        val startedTick = session.arenaBgmModeStartedTick
        if (startedTick <= 0L) {
            return 1L
        }
        val elapsedTicks = (Bukkit.getCurrentTick().toLong() - startedTick).coerceAtLeast(0L)
        return (elapsedTicks.toDouble() / track.beatTicks).toLong() + 1L
    }

    private fun startArenaBgmMode(session: ArenaSession, mode: ArenaBgmMode, startTick: Long) {
        val track = arenaBgmTrackForMode(mode) ?: return
        val currentMode = session.arenaBgmMode
        if (currentMode == mode && hasSessionArenaBgmPlayback(session, mode)) {
            session.arenaBgmSwitchRequest = null
            return
        }

        arenaBgmPlaybackTargets(session).forEach { player ->
            stopAllArenaBgmForPlayer(player)
            BGMManager.playPrecise(player, track.soundKey, track.loopTicks, track.pitch)
        }
        session.arenaBgmMode = mode
        session.arenaBgmModeStartedTick = startTick
        session.arenaBgmSwitchRequest = null
        if (mode == ArenaBgmMode.NORMAL) {
            tryBroadcastPendingWaveClearedMessageOnNormalBgm(session)
        }
    }

    private fun resumeArenaBgmForPlayer(session: ArenaSession, player: Player) {
        val track = arenaBgmTrackForMode(session.arenaBgmMode) ?: return
        stopAllArenaBgmForPlayer(player)
        BGMManager.playPrecise(player, track.soundKey, track.loopTicks, track.pitch)
    }

    private fun stopArenaBgm(session: ArenaSession) {
        session.arenaWaveStartCombatDelayTask?.cancel()
        session.arenaWaveStartCombatDelayTask = null
        stopArenaBgmPlaybackOnly(session)
        session.arenaBgmMode = ArenaBgmMode.STOPPED
        session.arenaBgmModeStartedTick = 0L
        session.arenaBgmSwitchRequest = null
    }

    private fun stopArenaBgmForParticipant(session: ArenaSession, participantId: UUID) {
        val player = Bukkit.getPlayer(participantId)
        if (player != null && player.isOnline && player.world.name == session.worldName) {
            stopArenaBgmForPlayer(player)
        }
        val currentMode = session.arenaBgmMode
        if (currentMode != ArenaBgmMode.STOPPED && !hasSessionArenaBgmPlayback(session, currentMode)) {
            session.arenaBgmMode = ArenaBgmMode.STOPPED
            session.arenaBgmModeStartedTick = 0L
            session.arenaBgmSwitchRequest = null
        }
    }

    private fun arenaBgmPlaybackTargets(session: ArenaSession): List<Player> {
        return session.participants
            .asSequence()
            .mapNotNull { participantId -> Bukkit.getPlayer(participantId) }
            .filter { player -> player.isOnline && player.world.name == session.worldName }
            .toList()
    }

    private fun stopArenaBgmPlaybackOnly(session: ArenaSession) {
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            if (!player.isOnline || player.world.name != session.worldName) return@forEach
            stopArenaBgmForPlayer(player)
        }
    }

    private fun stopArenaBgmForPlayer(player: Player) {
        arenaSessionBgmKeys().forEach { soundKey ->
            BGMManager.stop(player, soundKey)
        }
    }

    private fun stopAllArenaBgmForPlayer(player: Player) {
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
        return setOf(arenaBgmConfig.normal.soundKey, arenaBgmConfig.combat.soundKey, arenaBgmConfig.lobby.soundKey)
    }

    private fun arenaSessionBgmKeys(): Set<String> {
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

        plugin.logger.info("[Arena] 驛｢譎｢・ｽ・ｯ驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｫ驛｢譎擾ｽｳ・ｨ郢晢ｽｻ驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｫ髯具ｽｻ隴弱・・・刹・ｹ郢晢ｽｻ ready=${readyArenaWorldNames.size} / total=${names.size}")
    }

    private fun resetArenaPoolWorld(worldName: String): Boolean {
        val loadedWorld = Bukkit.getWorld(worldName)
        if (loadedWorld != null && !Bukkit.unloadWorld(loadedWorld, false)) {
            plugin.logger.warning("[Arena] 驛｢譎丞ｹｲ郢晢ｽｻ驛｢譎｢・ｽ・ｫ驛｢譎｢・ｽ・ｯ驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｫ驛｢譎擾ｽｳ・ｨ郢晢ｽｻ驛｢・ｧ繝ｻ・｢驛｢譎｢・ｽ・ｳ驛｢譎｢・ｽ・ｭ驛｢譎｢・ｽ・ｼ驛｢譎擾ｽｳ・ｨ遶頑･｢譽斐・・ｱ髫ｰ・ｨ陷会ｽｱ繝ｻ・ｰ驍ｵ・ｺ繝ｻ・ｾ驍ｵ・ｺ陷会ｽｱ隨ｳ繝ｻ $worldName")
            return false
        }

        val worldFolder = worldFolder(worldName)
        if (worldFolder.exists() && !deleteDirectory(worldFolder)) {
            plugin.logger.warning("[Arena] 驛｢譎丞ｹｲ郢晢ｽｻ驛｢譎｢・ｽ・ｫ驛｢譎｢・ｽ・ｯ驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｫ驛｢譎擾ｽｳ・ｨ郢晢ｽｵ驛｢・ｧ繝ｻ・ｩ驛｢譎｢・ｽ・ｫ驛｢謨鳴髯ｷ蜿ｰ・ｼ竏晄ｱるし・ｺ繝ｻ・ｫ髯樊ｻゑｽｽ・ｱ髫ｰ・ｨ陷会ｽｱ繝ｻ・ｰ驍ｵ・ｺ繝ｻ・ｾ驍ｵ・ｺ陷会ｽｱ隨ｳ繝ｻ $worldName")
            return false
        }

        val created = createArenaWorld(worldName)
        if (created == null) {
            plugin.logger.warning("[Arena] 驛｢譎丞ｹｲ郢晢ｽｻ驛｢譎｢・ｽ・ｫ驛｢譎｢・ｽ・ｯ驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｫ驛｢譎√・陷・ｽｽ髫ｰ迹壹・遶頑･｢譽斐・・ｱ髫ｰ・ｨ陷会ｽｱ繝ｻ・ｰ驍ｵ・ｺ繝ｻ・ｾ驍ｵ・ｺ陷会ｽｱ隨ｳ繝ｻ $worldName")
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
            plugin.logger.warning("[Arena] 驛｢譏ｴ繝ｻ郢晢ｽｰ驛｢譏ｴ繝ｻ邵ｺ蟶敖蛹・ｽｽ・ｨ驛｢譎・鯵邵ｺ繝ｻ・ｹ譎擾ｽｳ・ｨ・趣ｽ｡驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｫ驛｢譎擾ｽｳ・ｨ郢晢ｽｻ驛｢譎・ｽｧ・ｭ郢晢ｽｻ驛｢・ｧ繝ｻ・ｫ驛｢譎｢・ｽ・ｼ髣厄ｽｴ隲帛現繝ｻ驍ｵ・ｺ繝ｻ・ｫ髯樊ｻゑｽｽ・ｱ髫ｰ・ｨ陷会ｽｱ繝ｻ・ｰ驍ｵ・ｺ繝ｻ・ｾ驍ｵ・ｺ陷会ｽｱ隨ｳ繝ｻ ${world.name}")
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
            plugin.logger.warning("[Arena] 驛｢譏ｴ繝ｻ郢晢ｽｰ驛｢譏ｴ繝ｻ邵ｺ蟶敖蛹・ｽｽ・ｨ驛｢譎・鯵邵ｺ繝ｻ・ｹ譎擾ｽｳ・ｨ・趣ｽ｡驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｫ驛｢譎擾ｽｳ・ｨ郢晢ｽｻ鬮ｫ髦ｪ繝ｻ繝ｻ・｣繝ｻ・ｽ驍ｵ・ｺ繝ｻ・ｫ髯樊ｻゑｽｽ・ｱ髫ｰ・ｨ陷会ｽｱ繝ｻ・ｰ驍ｵ・ｺ繝ｻ・ｾ驍ｵ・ｺ陷会ｽｱ隨ｳ繝ｻ ${templateFolder.name} -> $cloneWorldName")
            return null
        }

        removeMarkerFile(cloneFolder, DEBUG_VOID_TEMPLATE_MARKER_FILE_NAME)
        removeMarkerFile(cloneFolder, "uid.dat")
        val world = createArenaWorld(cloneWorldName) ?: run {
            deleteDirectory(cloneFolder)
            return null
        }
        if (!markDebugVoidWorld(world)) {
            plugin.logger.warning("[Arena] 鬮ｫ髦ｪ繝ｻ繝ｻ・｣繝ｻ・ｽ驛｢譎｢・ｽ・ｯ驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｫ驛｢譎擾ｽｳ・ｨ郢晢ｽｻ驛｢譎・ｽｧ・ｭ郢晢ｽｻ驛｢・ｧ繝ｻ・ｫ驛｢譎｢・ｽ・ｼ髣厄ｽｴ隲帛現繝ｻ驍ｵ・ｺ繝ｻ・ｫ髯樊ｻゑｽｽ・ｱ髫ｰ・ｨ陷会ｽｱ繝ｻ・ｰ驍ｵ・ｺ繝ｻ・ｾ驍ｵ・ｺ陷会ｽｱ隨ｳ繝ｻ ${world.name}")
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

        createDebugVoidWorld() ?: plugin.logger.warning("[Arena] Failed to create debug void world.")
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
            plugin.logger.warning("[Arena] 驛｢譏ｴ繝ｻ郢晢ｽｰ驛｢譏ｴ繝ｻ邵ｺ蝣､・ｹ譏ｴ繝ｻ・趣ｽｦ驛｢譎丞ｹｲ・取ｨ抵ｽｹ譎｢・ｽ・ｼ驛｢譎冗樟郢晢ｽｻ驛｢譎・ｽｧ・ｭ郢晢ｽｻ驛｢・ｧ繝ｻ・ｫ驛｢譎｢・ｽ・ｼ髣厄ｽｴ隲帛現繝ｻ驍ｵ・ｺ繝ｻ・ｫ髯樊ｻゑｽｽ・ｱ髫ｰ・ｨ陷会ｽｱ繝ｻ・ｰ驍ｵ・ｺ繝ｻ・ｾ驍ｵ・ｺ陷会ｽｱ隨ｳ繝ｻ ${created.name}")
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
            time = 18000
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
            plugin.logger.severe("[Arena] 驛｢譎｢・ｽ・ｯ驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｫ驛｢譎臥櫨霓､譛ｱ・ｫ・ｯ繝ｻ・､驛｢・ｧ陷ｻ莠･・ｦ蜻ｵ・ｰ・｢繝ｻ・ｵ: ${pending.worldName} path=${pending.folder.absolutePath}")
            pendingWorldDeletions.remove(pending.worldName)
        }
    }

    private fun queueWorldDeletion(worldName: String, folder: File) {
        pendingWorldDeletions.putIfAbsent(worldName, PendingWorldDeletion(worldName, folder))
    }

    private fun initializeActionMarkers(session: ArenaSession) {
        val world = Bukkit.getWorld(session.worldName)
        if (world == null) {
            plugin.logger.warning("[Arena] 驛｢・ｧ繝ｻ・｢驛｢・ｧ繝ｻ・ｯ驛｢・ｧ繝ｻ・ｷ驛｢譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ驛｢譎・ｽｧ・ｭ郢晢ｽｻ驛｢・ｧ繝ｻ・ｫ驛｢譎｢・ｽ・ｼ髯具ｽｻ隴弱・・・刹・ｹ隰費ｽｶ邵ｺ蟶ｷ・ｹ・ｧ繝ｻ・ｭ驛｢譏ｴ繝ｻ郢晢ｽｻ: 驛｢譎｢・ｽ・ｯ驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｫ驛｢譎牙愛隰費ｽｴ髯ｷ・ｿ鬮｢ﾂ繝ｻ・ｾ郢晢ｽｻworld=${session.worldName}")
            return
        }
        val markers = mutableMapOf<UUID, ArenaActionMarker>()

        if (!isClearingMission(session)) {
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
        }

        session.actionMarkers.clear()
        session.actionMarkers.putAll(markers)
        session.actionMarkerHoldStates.clear()
        plugin.logger.info("[Arena] 驛｢・ｧ繝ｻ・｢驛｢・ｧ繝ｻ・ｯ驛｢・ｧ繝ｻ・ｷ驛｢譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ驛｢譎・ｽｧ・ｭ郢晢ｽｻ驛｢・ｧ繝ｻ・ｫ驛｢譎｢・ｽ・ｼ髯具ｽｻ隴弱・・・刹・ｹ郢晢ｽｻ world=${session.worldName} total=${markers.size} barrier=${markers.values.count { it.type == ArenaActionMarkerType.BARRIER_ACTIVATE }}")
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
                    waitingPlayer.sendTitle(ArenaI18n.text(waitingPlayer, "arena.messages.multiplayer.stage_wait_title"), "", 0, 60, 0)
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
                    ArenaI18n.text(owner, "arena.messages.multiplayer.invite_cancelled_offline")
                )
                return@forEach
            }
            if (invited.world.uid != owner.world.uid) {
                removeInvitedParticipant(session, invitedId)
                invited.sendMessage(
                    ArenaI18n.text(invited, "arena.messages.multiplayer.invite_auto_declined_far")
                )
                owner.sendMessage(
                    ArenaI18n.text(owner, "arena.messages.multiplayer.invite_auto_declined_far_owner", "player" to invited.name)
                )
                return@forEach
            }

            if (invited.location.distanceSquared(owner.location) > maxDistanceSquared) {
                removeInvitedParticipant(session, invitedId)
                invited.sendMessage(
                    ArenaI18n.text(invited, "arena.messages.multiplayer.invite_auto_declined_far")
                )
                owner.sendMessage(
                    ArenaI18n.text(owner, "arena.messages.multiplayer.invite_auto_declined_far_owner", "player" to invited.name)
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
            ownerBar.name(legacySerializer.deserialize(ArenaI18n.text(owner, "arena.bossbar.join_countdown", "seconds" to formatRemainingSeconds(ownerRemaining))))
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
            bar.name(legacySerializer.deserialize(ArenaI18n.text(invited, "arena.bossbar.join_countdown", "seconds" to formatRemainingSeconds(remaining))))
            bar.progress(progress)
            invited.showBossBar(bar)
        }
    }

    private fun getOrCreateJoinCountdownBossBar(session: ArenaSession, playerId: UUID): BossBar {
        return session.joinCountdownBossBars.getOrPut(playerId) {
            BossBar.bossBar(legacySerializer.deserialize(ArenaI18n.text(null, "arena.bossbar.join_countdown", "seconds" to "0")), 1.0f, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_12)
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

        val waitingNow = mutableSetOf<UUID>()
        candidateIds.forEach { candidateId ->
            val player = Bukkit.getPlayer(candidateId)
            if (player == null || !player.isOnline) {
                session.waitingOutsideTicksByPlayer.remove(candidateId)
                return@forEach
            }

            val insideLiftArea = session.liftMarkerLocations.any { markerLocation ->
                isInsideLiftArea(markerLocation, player.location, margin = MULTIPLAYER_LIFT_AREA_MARGIN)
            }
            if (insideLiftArea) {
                waitingNow += candidateId
                session.waitingOutsideTicksByPlayer.remove(candidateId)
                return@forEach
            }

            if (session.waitingParticipants.contains(candidateId)) {
                val outsideTicks = (session.waitingOutsideTicksByPlayer[candidateId] ?: 0) + 1
                if (outsideTicks < MULTIPLAYER_WAITING_EXIT_GRACE_TICKS) {
                    waitingNow += candidateId
                    session.waitingOutsideTicksByPlayer[candidateId] = outsideTicks
                } else {
                    session.waitingOutsideTicksByPlayer.remove(candidateId)
                }
            } else {
                session.waitingOutsideTicksByPlayer.remove(candidateId)
            }
        }
        session.waitingOutsideTicksByPlayer.keys.removeIf { it !in candidateIds }

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
                player.sendTitle("", ArenaI18n.text(player, "arena.messages.multiplayer.waiting_title"), 0, 25, 5)
                session.waitingSubtitleNextTickByPlayer[playerId] = currentTick + 20L
            }
        }

        exited.forEach { playerId ->
            session.waitingNotifiedParticipants.remove(playerId)
            session.waitingSubtitleNextTickByPlayer.remove(playerId)
            session.waitingOutsideTicksByPlayer.remove(playerId)
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            player.sendMessage(
                ArenaI18n.text(player, "arena.messages.multiplayer.waiting_exited")
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
            )
            return
        }

        if (!session.waitingParticipants.contains(session.ownerPlayerId)) {
            terminateSession(
                session,
                false,
                messageKey = "arena.messages.multiplayer.cancelled_owner_not_waiting",
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
            )
            return
        }
        if (session.liftMarkerLocations.isEmpty()) {
            terminateSession(
                session,
                false,
                messageKey = "arena.messages.command.start_error.lift_not_ready",
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
        var transferBlindnessApplied = false

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
            val transferBlindnessRise = (transferRise - 1).coerceAtLeast(0)

            if (!transferBlindnessApplied && currentRise >= transferBlindnessRise) {
                transferBlindnessApplied = true
                participants.forEach { player ->
                    if (!player.isOnline || player.world.uid != introWorld.uid) return@forEach
                    player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, STAGE_TRANSFER_BLINDNESS_TICKS, 0, false, false, false))
                }
            }

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
                updateSessionProgressBossBar(session)
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
                    ArenaI18n.text(player, "arena.messages.oage.lift_occupied_done"),
                    plugin,
                    sound = Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM,
                    volume = 1.0f,
                    pitch = 1.15f
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

    private fun findLoadedLobbyMarkerSnapshot(world: World): ArenaLobbyMarkerSnapshot {
        val returnLobby = mutableListOf<Location>()
        val main = mutableListOf<Location>()
        val tutorialStart = mutableListOf<Location>()
        val tutorialSteps = mutableListOf<Location>()
        val pedestal = mutableListOf<Location>()

        world.getEntitiesByClass(Marker::class.java).forEach { marker ->
            val tags = marker.scoreboardTags
            val location = marker.location.clone()
            when {
                tags.contains(LOBBY_MARKER_TAG_RETURN) -> returnLobby += location
                tags.contains(LOBBY_MARKER_TAG_MAIN) -> main += location
                tags.contains(LOBBY_MARKER_TAG_TUTORIAL_START) -> tutorialStart += location
                tags.contains(LOBBY_MARKER_TAG_TUTORIAL_STEP) -> tutorialSteps += location
                tags.contains(LOBBY_MARKER_TAG_PEDESTAL) -> pedestal += location
            }
        }

        val sortedTutorialSteps = tutorialSteps
            .sortedBy { location ->
                val marker = world
                    .getNearbyEntities(location, 0.25, 0.25, 0.25)
                    .filterIsInstance<Marker>()
                    .firstOrNull { candidate ->
                        candidate.scoreboardTags.contains(LOBBY_MARKER_TAG_TUTORIAL_STEP)
                    }
                if (marker == null) Int.MAX_VALUE else extractTutorialStepIndex(marker) ?: Int.MAX_VALUE
            }

        return ArenaLobbyMarkerSnapshot(
            returnLobby = returnLobby,
            main = main,
            tutorialStart = tutorialStart,
            tutorialSteps = sortedTutorialSteps,
            pedestal = pedestal
        )
    }

    private fun isInsideLiftArea(markerLocation: Location, playerLocation: Location, margin: Double = 0.0): Boolean {
        val markerWorld = markerLocation.world ?: return false
        if (playerLocation.world?.uid != markerWorld.uid) return false

        val template = resolveEntranceLiftTemplate() ?: return false

        val blockX = markerLocation.blockX
        val blockY = markerLocation.blockY
        val blockZ = markerLocation.blockZ

        val x = playerLocation.x
        val y = playerLocation.y
        val z = playerLocation.z

        return x >= blockX - margin && x < blockX + template.sizeX.toDouble() + margin &&
            z >= blockZ - margin && z < blockZ + template.sizeZ.toDouble() + margin &&
            y >= blockY - 1.0 - margin && y < blockY + template.sizeY.toDouble() + 1.0 + margin
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

        updateLobbyTutorialMarkers(currentTick)
    }

    private fun updateLobbyTutorialMarkers(currentTick: Long) {
        val snapshot = lobbyTutorialMarkers.toMap()
        snapshot.forEach { (playerId, marker) ->
            val player = Bukkit.getPlayer(playerId)
            if (player == null || !player.isOnline || player.world.uid != marker.center.world?.uid) {
                clearLobbyTutorialState(playerId)
                return@forEach
            }

            val holdProgress = markerHoldProgress(marker, lobbyTutorialHoldStates[playerId])
            advanceMarkerMiddleRingRotation(marker, holdProgress)
            val color = resolveActionMarkerDisplayColor(marker, currentTick, holdProgress)
            renderActionMarkerParticles(player, marker, color)
            if (isInsideActionMarkerRange(player.location, marker.center)) {
                player.sendActionBar(
                    Component.text(ArenaI18n.text(player, "arena.messages.lobby.tutorial.hold_hint"))
                )
            }
            updateLobbyTutorialHoldState(player, marker, currentTick)
        }
    }

    private fun updateLobbyTutorialHoldState(player: Player, marker: ArenaActionMarker, currentTick: Long) {
        val playerId = player.uniqueId
        if (!player.isSneaking || !isInsideActionMarkerRange(player.location, marker.center)) {
            val holdState = lobbyTutorialHoldStates[playerId]
            if (holdState != null && holdState.heldTicks >= 20) {
                player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 2.0f)
            }
            lobbyTutorialHoldStates.remove(playerId)
            return
        }

        val holdState = lobbyTutorialHoldStates.getOrPut(playerId) { ArenaActionMarkerHoldState() }
        if (holdState.markerId != marker.id) {
            holdState.markerId = marker.id
            holdState.heldTicks = 0
            holdState.startSoundPlayed = false
        }

        if (!holdState.startSoundPlayed) {
            player.playSound(marker.center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 1.2f)
            holdState.startSoundPlayed = true
        }

        holdState.heldTicks = (holdState.heldTicks + 1).coerceAtMost(marker.holdTicksRequired)
        if (holdState.heldTicks < marker.holdTicksRequired) {
            return
        }

        transitionActionMarkerState(
            marker,
            ArenaActionMarkerState.RUNNING,
            currentTick,
            fromColor = resolveActionMarkerHoldColor(marker, 1.0)
        )
        player.playSound(marker.center, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 2.0f)

        val tutorialState = lobbyTutorialStates[playerId]
        val stepIndex = tutorialState?.stepIndex ?: 0
        val stepMessages = ArenaI18n.stringList(player, "arena.messages.lobby.tutorial.steps")
        stepMessages.getOrNull(stepIndex)
            ?.takeIf { it.isNotBlank() }
            ?.let { player.sendMessage(it) }

        lobbyTutorialHoldStates.remove(playerId)
        if (tutorialState == null) {
            completeLobbyTutorial(player)
            return
        }

        tutorialState.stepIndex += 1
        if (tutorialState.stepIndex >= tutorialState.stepLocations.size) {
            lobbyTutorialMarkers.remove(playerId)
            completeLobbyTutorial(player)
            return
        }

        spawnLobbyTutorialMarker(player, tutorialState)
    }

    private fun updateBarrierReturnHoldStates(currentTick: Long) {
        for (session in sessionsByWorld.values) {
            val canReturn = session.barrierRestartCompleted || session.missionCompleted
            if (!canReturn) {
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
                    val countdownKey = if (session.missionCompleted) "arena.messages.mission.return_countdown" else "arena.messages.barrier.return_countdown"
                    player.sendTitle(
                        "",
                        ArenaI18n.text(player, countdownKey, "seconds" to formatted),
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
                val returnKey = if (session.missionCompleted) "arena.messages.mission.returned" else "arena.messages.barrier.returned"
                val returnedToLobby = stopSessionToLobbyById(
                    participantId,
                    ArenaI18n.text(player, returnKey)
                )
                if (returnedToLobby && session.missionCompleted) {
                    val lobbyPlayer = Bukkit.getPlayer(participantId)
                    if (lobbyPlayer != null && lobbyPlayer.isOnline) {
                        val followupMessage = ArenaI18n.stringList(lobbyPlayer, "arena.messages.oage.mission_returned_followup").randomOrNull()
                        if (!followupMessage.isNullOrBlank()) {
                            sendOageMessage(
                                lobbyPlayer,
                                "arena.messages.oage.mission_returned_followup",
                                followupMessage
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateActionMarkerHoldState(session: ArenaSession, player: Player, currentTick: Long) {
        val marker = findHoldableActionMarker(session, player.location)
        if (marker?.type == ArenaActionMarkerType.DOOR_TOGGLE) {
            player.sendActionBar(
                Component.text(ArenaI18n.text(player, "arena.messages.door.open_hint"))
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

    private fun markerHoldProgress(marker: ArenaActionMarker, holdState: ArenaActionMarkerHoldState?): Double {
        val required = marker.holdTicksRequired.coerceAtLeast(1)
        val heldTicks = holdState?.heldTicks ?: 0
        return (heldTicks.toDouble() / required.toDouble()).coerceIn(0.0, 1.0)
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
                if (isClearingMission(session)) return
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
            ArenaActionMarkerType.TUTORIAL_PROGRESS -> {
                return
            }
        }
    }

    private fun findHoldableActionMarker(session: ArenaSession, location: Location): ArenaActionMarker? {
        return session.actionMarkers.values
            .asSequence()
            .filter { it.state == ArenaActionMarkerState.READY }
            .filter { marker -> isInsideActionMarkerRange(location, marker.center) }
            .minByOrNull { marker ->
                val dx = location.x - marker.center.x
                val dz = location.z - marker.center.z
                (dx * dx) + (dz * dz)
            }
    }

    private fun isInsideActionMarkerRange(location: Location, markerCenter: Location): Boolean {
        val worldUid = location.world?.uid ?: return false
        if (markerCenter.world?.uid != worldUid) return false
        if (abs(location.y - (markerCenter.y - 0.5)) > ACTION_MARKER_MAX_Y_DISTANCE) return false

        val dx = location.x - markerCenter.x
        val dz = location.z - markerCenter.z
        return (dx * dx) + (dz * dz) <= ACTION_MARKER_RADIUS_SQUARED
    }

    fun buildStatusReport(): ArenaStatusReport {
        val worldStates = arenaWorldStates.values.groupingBy { it }.eachCount()
        val lobbySnapshots = Bukkit.getWorlds().map { findLoadedLobbyMarkerSnapshot(it) }
        val returnLobbyCount = lobbySnapshots.sumOf { it.returnLobby.size }
        val mainLobbyCount = lobbySnapshots.sumOf { it.main.size }
        val tutorialStartCount = lobbySnapshots.sumOf { it.tutorialStart.size }
        val tutorialStepCount = lobbySnapshots.sumOf { it.tutorialSteps.size }
        val pedestalCount = lobbySnapshots.sumOf { it.pedestal.size }
        val missionSnapshot = arenaMissionService?.getStatusSnapshot()
        return ArenaStatusReport(
            poolWorlds = arenaWorldStates
                .map { (name, state) -> ArenaPoolWorldStatus(name, state.name.lowercase(Locale.US)) }
                .sortedBy { it.name },
            readyWorldCount = worldStates[ArenaPoolWorldState.READY] ?: 0,
            totalWorldCount = arenaWorldStates.size,
            inUseWorldCount = worldStates[ArenaPoolWorldState.IN_USE] ?: 0,
            cleaningWorldCount = worldStates[ArenaPoolWorldState.CLEANING] ?: 0,
            brokenWorldCount = worldStates[ArenaPoolWorldState.BROKEN] ?: 0,
            arenaWorldReady = readyArenaWorldNames.size,
            arenaWorldTotal = arenaPoolSize,
            liftReady = sessionsByWorld.values.any { getLiftStatusForSession(it) == ArenaLiftStatus.READY },
            lobbyReady = mainLobbyCount > 0 && tutorialStartCount > 0,
            lobbyMainReady = mainLobbyCount > 0,
            lobbyTutorialReady = tutorialStartCount > 0,
            returnLobbyCount = returnLobbyCount,
            mainLobbyCount = mainLobbyCount,
            tutorialStartCount = tutorialStartCount,
            tutorialStepCount = tutorialStepCount,
            pedestalCount = pedestalCount,
            activeSessionCount = sessionsByWorld.size,
            maxConcurrentSessions = maxConcurrentSessions,
            missionProgress = missionSnapshot,
            lobbyProgressVisitedCount = missionSnapshot?.lobbyProgressCount ?: lobbyVisitedParticipants.size,
            lobbyProgressTutorialCompletedCount = missionSnapshot?.lobbyTutorialCompletedCount ?: tutorialCompletedParticipants.size,
            themeLoadStatus = themeLoader.getLoadStatus()
        )
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
            ArenaActionMarkerType.TUTORIAL_PROGRESS -> Sound.BLOCK_NOTE_BLOCK_PLING
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
        val missionTitle = session.inviteMissionTitle?.takeIf { it.isNotBlank() } ?: ArenaI18n.text(null, "arena.ui.recruitment.default_mission_title")

        val lines = mutableListOf<String>()
        lines += ""
        lines += ArenaI18n.text(null, "arena.ui.recruitment.title", "missionTitle" to missionTitle)
        lines += ""
        lines += ArenaI18n.text(null, "arena.ui.recruitment.remaining", "seconds" to remainingSeconds)
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
                ArenaI18n.text(null, "arena.ui.recruitment.waiting_participant", "name" to name)
            } else {
                ArenaI18n.text(null, "arena.ui.recruitment.normal_participant", "name" to name)
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
            session.phase == ArenaPhase.GAME_OVER -> ArenaI18n.text(null, "arena.ui.sidebar.defeat")
            inGetReady -> ArenaI18n.text(null, "arena.ui.sidebar.get_ready")
            else -> buildWaveSidebarHeader(session, sidebarWave ?: session.currentWave.coerceAtLeast(1))
        }

        // 髫ｰ蜉ｱ繝ｻ繝ｻ・ｨ陟托ｽｱ・朱・・ｹ譏ｴ繝ｻ邵ｺ蜥擾ｽｹ譎｢・ｽ・ｧ驛｢譎｢・ｽ・ｳ: 鬩搾ｽｨ驕停沖ﾑ・垓蠑ｱ・玖将・｣驛｢・ｧ陞ｳ螟ｲ・ｽ・｡繝ｻ・ｨ鬩穂ｼ夲ｽｽ・ｺ
        if (isClearingMission(session) && session.firstDoorOpenedAtMillis != null) {
            val elapsedMillis = System.currentTimeMillis() - session.firstDoorOpenedAtMillis!!
            val elapsedSeconds = (elapsedMillis / 1000).toInt().coerceAtLeast(0)
            val elapsedTimeText = formatTimeRemaining(elapsedSeconds)
            lines += ArenaI18n.text(null, "arena.ui.sidebar.elapsed_time", "time" to elapsedTimeText)
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
            lines += ArenaI18n.text(null, "arena.ui.sidebar.participant_line", "name" to name, "status" to status)
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
            ArenaI18n.text(null, "arena.ui.sidebar.last_wave")
        } else {
            ArenaI18n.text(null, "arena.ui.sidebar.wave", "wave" to wave)
        }
        return if (session.clearedWaves.contains(wave)) {
            "$base ${ArenaI18n.text(null, "arena.ui.sidebar.clear")}" 
        } else {
            base
        }
    }

    private fun resolveSidebarParticipantStatus(session: ArenaSession, playerId: UUID): String {
        if (isSidebarPreparing(session, playerId)) {
            return ArenaI18n.text(null, "arena.ui.sidebar.status_preparing")
        }

        val downState = session.downedPlayers[playerId]
        if (downState != null) {
            if (downState.reviveDisabled) {
                return ArenaI18n.text(null, "arena.ui.sidebar.status_dead")
            }
            return ArenaI18n.text(null, "arena.ui.sidebar.status_down")
        }

        val online = Bukkit.getPlayer(playerId)
        if (
            session.participants.contains(playerId) &&
            online != null &&
            online.isOnline &&
            !online.isDead &&
            online.world.name == session.worldName
        ) {
            return ArenaI18n.text(null, "arena.ui.sidebar.status_alive")
        }
        return ArenaI18n.text(null, "arena.ui.sidebar.status_dead")
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
            legacySerializer.deserialize(ArenaI18n.text(null, "arena.ui.sidebar.title"))
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
                    "[Arena] 驛｢・ｧ繝ｻ・ｯ驛｢譎｢・ｽ・ｪ驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｳ驛｢・ｧ繝ｻ・｢驛｢譏ｴ繝ｻ郢晢ｽｻ髯橸ｽｳ陟包ｽ｡繝ｻ・ｺ郢晢ｽｻ world=${job.worldName} blocks=${job.processedBlocks}/${job.totalBlocks} elapsed=${elapsedMillis}ms"
                )
                markArenaWorldReady(job.worldName)
                logArenaPoolState("驛｢・ｧ繝ｻ・ｯ驛｢譎｢・ｽ・ｪ驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｳ驛｢・ｧ繝ｻ・｢驛｢譏ｴ繝ｻ郢晢ｽｻ髯橸ｽｳ陟包ｽ｡繝ｻ・ｺ郢晢ｽｻ, job.worldName")
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
                    "[Arena] 驛｢・ｧ繝ｻ・ｯ驛｢譎｢・ｽ・ｪ驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｳ驛｢・ｧ繝ｻ・｢驛｢譏ｴ繝ｻ郢晢ｽｻ鬯ｨ・ｾ繝ｻ・ｲ髫ｰ闊後・ world=${job.worldName} progress=${formatPercent(progress)}% " +
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
                plugin.logger.info("[Arena] 髫ｴ蟷｢・ｽ・ｪ髯ｷ蜿ｰ・ｼ竏晄ｱるΔ譎｢・ｽ・ｯ驛｢譎｢・ｽ・ｼ驛｢譎｢・ｽ・ｫ驛｢譎擾ｽｳ・ｨ郢晢ｽｻ髯ｷ蜿ｰ・ｼ竏晄ｱるし・ｺ繝ｻ・ｫ髫ｰ蠕｡・ｻ蜥擾ｽｲ・･: ${pending.worldName}")
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
