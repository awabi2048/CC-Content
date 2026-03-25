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

data class ArenaSession(
    val ownerPlayerId: UUID,
    val worldName: String,
    val themeId: String,
    val mobTypeId: String,
    val difficultyId: String,
    val difficultyValue: Double,
    val difficultyScore: Double,
    val waves: Int,
    val questModifiers: ArenaQuestModifiers = ArenaQuestModifiers.NONE,
    val participants: MutableSet<UUID>,
    val returnLocations: MutableMap<UUID, Location>,
    val playerSpawn: Location,
    val entranceLocation: Location,
    val stageBounds: ArenaBounds,
    val roomBounds: Map<Int, ArenaBounds>,
    val corridorBounds: Map<Int, ArenaBounds>,
    val roomMobSpawns: Map<Int, List<Location>>,
    val corridorDoorBlocks: Map<Int, List<Location>>,
    val doorAnimationPlacements: Map<Int, List<ArenaDoorAnimationPlacement>>,
    val barrierLocation: Location,
    var participantSpawnProtectionUntilMillis: Long = 0L,
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
    var barrierDefenseSpawnTask: BukkitTask? = null,
    var barrierDefensePressureTask: BukkitTask? = null,
    var barrierRestartBossBar: BossBar? = null,
    val barrierRestartCorruptedSlots: MutableSet<Int> = mutableSetOf(),
    val barrierDefenseMobIds: MutableSet<UUID> = mutableSetOf(),
    val barrierDefenseTargetMobIds: MutableSet<UUID> = mutableSetOf(),
    val barrierDefenseAssaultMobIds: MutableSet<UUID> = mutableSetOf(),
    val startedWaves: MutableSet<Int> = mutableSetOf(),
    val clearedWaves: MutableSet<Int> = mutableSetOf(),
    val activeMobs: MutableSet<UUID> = mutableSetOf(),
    val waveMobIds: MutableMap<Int, MutableSet<UUID>> = mutableMapOf(),
    val waveKillCount: MutableMap<Int, Int> = mutableMapOf(),
    var totalKillCount: Int = 0,
    val waveClearTargets: MutableMap<Int, Int> = mutableMapOf(),
    val waveMaxAliveCounts: MutableMap<Int, Int> = mutableMapOf(),
    val mobWaveMap: MutableMap<UUID, Int> = mutableMapOf(),
    val playerNotifiedWaves: MutableMap<UUID, MutableSet<Int>> = mutableMapOf(),
    val participantLocationHistory: MutableMap<UUID, ArrayDeque<TimedPlayerLocation>> = mutableMapOf(),
    val participantLastSampleMillis: MutableMap<UUID, Long> = mutableMapOf(),
    val corridorTriggeredWaves: MutableSet<Int> = mutableSetOf(),
    val openedCorridors: MutableSet<Int> = mutableSetOf(),
    val corridorOpenAnnouncements: MutableSet<Int> = mutableSetOf(),
    val enteredWaves: MutableSet<Int> = mutableSetOf(),
    val waveSpawningStopped: MutableSet<Int> = mutableSetOf(),
    val animatingDoorWaves: MutableSet<Int> = mutableSetOf(),
    val transitionTasks: MutableList<BukkitTask> = mutableListOf(),
    val waveSpawnTasks: MutableMap<Int, BukkitTask> = mutableMapOf()
)
