package jp.awabi2048.cccontent.features.arena

import net.kyori.adventure.bossbar.BossBar
import jp.awabi2048.cccontent.features.arena.generator.ArenaDoorAnimationPlacement
import org.bukkit.Location
import org.bukkit.scheduler.BukkitTask
import jp.awabi2048.cccontent.features.arena.quest.ArenaQuestModifiers
import java.util.UUID

data class ArenaBounds(
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int,
    val minZ: Int,
    val maxZ: Int
) {
    fun contains(x: Double, y: Double, z: Double): Boolean {
        return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1
    }
}

data class TimedPlayerLocation(
    val timestampMillis: Long,
    val location: Location
)

enum class ArenaBgmMode {
    NORMAL,
    COMBAT,
    STOPPED
}

data class ArenaBgmSwitchRequest(
    val targetMode: ArenaBgmMode,
    val requestedAtTick: Long,
    val executeAtAbsoluteBeat: Long
)

data class ArenaSession(
    val ownerPlayerId: UUID,
    val worldName: String,
    val themeId: String,
    val difficultyId: String,
    val difficultyValue: Double,
    val difficultyScore: Double,
    val waves: Int,
    val questModifiers: ArenaQuestModifiers = ArenaQuestModifiers.NONE,
    var maxParticipants: Int = 6,
    val participants: MutableSet<UUID>,
    val returnLocations: MutableMap<UUID, Location>,
    var playerSpawn: Location,
    var entranceLocation: Location,
    var stageBounds: ArenaBounds,
    val roomBounds: MutableMap<Int, ArenaBounds>,
    val corridorBounds: MutableMap<Int, ArenaBounds>,
    val roomMobSpawns: MutableMap<Int, List<Location>>,
    val corridorDoorBlocks: MutableMap<Int, List<Location>>,
    val doorAnimationPlacements: MutableMap<Int, List<ArenaDoorAnimationPlacement>>,
    var barrierLocation: Location,
    val barrierPointLocations: MutableList<Location>,
    val joinAreaMarkerLocations: MutableList<Location> = mutableListOf(),
    var participantSpawnProtectionUntilMillis: Long = 0L,
    var multiplayerJoinEnabled: Boolean = false,
    var multiplayerJoinFinalizeStarted: Boolean = false,
    var multiplayerJoinIntroStarted: Boolean = false,
    var joinGraceStartMillis: Long = 0L,
    var joinGraceDurationMillis: Long = 0L,
    var joinGraceEndMillis: Long = 0L,
    var inviteQuestTitle: String? = null,
    var inviteQuestLore: List<String> = emptyList(),
    var stageGenerationCompleted: Boolean = true,
    var stageGenerationWaitTitleShown: Boolean = false,
    var stageBuildTask: BukkitTask? = null,
    var currentWave: Int = 0,
    var fallbackWave: Int = 1,
    var stageStarted: Boolean = false,
    var barrierActive: Boolean = false,
    var barrierRestarting: Boolean = false,
    var barrierRestartCompleted: Boolean = false,
    var barrierRestartStartMillis: Long = 0L,
    var barrierRestartDurationMillis: Long = 0L,
    var barrierRestartProgressMillis: Long = 0L,
    var barrierAmbientTask: BukkitTask? = null,
    var barrierRestartTask: BukkitTask? = null,
    var barrierRestartActivationTask: BukkitTask? = null,
    var barrierRestartEffectTask: BukkitTask? = null,
    var barrierRestartDamageTask: BukkitTask? = null,
    var barrierDefenseSpawnTask: BukkitTask? = null,
    var barrierDefensePressureTask: BukkitTask? = null,
    var progressBossBar: BossBar? = null,
    val barrierDefenseMobIds: MutableSet<UUID> = mutableSetOf(),
    val barrierDefenseTargetMobIds: MutableSet<UUID> = mutableSetOf(),
    val barrierDefenseAssaultMobIds: MutableSet<UUID> = mutableSetOf(),
    val barrierRestartActivationQueue: MutableList<Location> = mutableListOf(),
    val barrierRestartActivatedPoints: MutableList<Location> = mutableListOf(),
    val startedWaves: MutableSet<Int> = mutableSetOf(),
    val clearedWaves: MutableSet<Int> = mutableSetOf(),
    val activeMobs: MutableSet<UUID> = mutableSetOf(),
    val waveMobIds: MutableMap<Int, MutableSet<UUID>> = mutableMapOf(),
    val waveKillCount: MutableMap<Int, Int> = mutableMapOf(),
    var totalKillCount: Int = 0,
    val waveClearTargets: MutableMap<Int, Int> = mutableMapOf(),
    var lastClearedWaveForBossBar: Int? = null,
    var stageMaxAliveCount: Int = 1,
    val mobWaveMap: MutableMap<UUID, Int> = mutableMapOf(),
    val playerNotifiedWaves: MutableMap<UUID, MutableSet<Int>> = mutableMapOf(),
    val participantLocationHistory: MutableMap<UUID, ArrayDeque<TimedPlayerLocation>> = mutableMapOf(),
    val participantLastSampleMillis: MutableMap<UUID, Long> = mutableMapOf(),
    val invitedParticipants: MutableSet<UUID> = mutableSetOf(),
    val invitedAtMillis: MutableMap<UUID, Long> = mutableMapOf(),
    val waitingParticipants: MutableSet<UUID> = mutableSetOf(),
    val waitingNotifiedParticipants: MutableSet<UUID> = mutableSetOf(),
    val joinCountdownBossBars: MutableMap<UUID, BossBar> = mutableMapOf(),
    val corridorTriggeredWaves: MutableSet<Int> = mutableSetOf(),
    val openedCorridors: MutableSet<Int> = mutableSetOf(),
    val corridorOpenAnnouncements: MutableSet<Int> = mutableSetOf(),
    val enteredWaves: MutableSet<Int> = mutableSetOf(),
    val waveSpawningStopped: MutableSet<Int> = mutableSetOf(),
    val animatingDoorWaves: MutableSet<Int> = mutableSetOf(),
    val actionMarkers: MutableMap<UUID, ArenaActionMarker> = mutableMapOf(),
    val actionMarkerHoldStates: MutableMap<UUID, ArenaActionMarkerHoldState> = mutableMapOf(),
    var arenaBgmMode: ArenaBgmMode = ArenaBgmMode.STOPPED,
    var arenaBgmPlaybackStartTick: Long = 0L,
    var arenaCombatHadTargetingMob: Boolean = false,
    var arenaBgmSwitchRequest: ArenaBgmSwitchRequest? = null,
    val transitionTasks: MutableList<BukkitTask> = mutableListOf(),
    val waveSpawnTasks: MutableMap<Int, BukkitTask> = mutableMapOf()
)
