package jp.awabi2048.cccontent.features.arena

import org.bukkit.Location
import org.bukkit.scheduler.BukkitTask
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

data class ArenaSession(
    val ownerPlayerId: UUID,
    val worldName: String,
    val themeId: String,
    val mobTypeId: String,
    val difficultyId: String,
    val waves: Int,
    val participants: MutableSet<UUID>,
    val returnLocations: MutableMap<UUID, Location>,
    val playerSpawn: Location,
    val stageBounds: ArenaBounds,
    val roomBounds: Map<Int, ArenaBounds>,
    val corridorBounds: Map<Int, ArenaBounds>,
    val roomMobSpawns: Map<Int, List<Location>>,
    val barrierLocation: Location,
    var currentWave: Int = 0,
    var barrierActive: Boolean = false,
    val startedWaves: MutableSet<Int> = mutableSetOf(),
    val clearedWaves: MutableSet<Int> = mutableSetOf(),
    val activeMobs: MutableSet<UUID> = mutableSetOf(),
    val waveMobIds: MutableMap<Int, MutableSet<UUID>> = mutableMapOf(),
    val waveKillCount: MutableMap<Int, Int> = mutableMapOf(),
    val waveClearTargets: MutableMap<Int, Int> = mutableMapOf(),
    val waveMaxAliveCounts: MutableMap<Int, Int> = mutableMapOf(),
    val mobWaveMap: MutableMap<UUID, Int> = mutableMapOf(),
    val playerNotifiedWaves: MutableMap<UUID, MutableSet<Int>> = mutableMapOf(),
    val corridorTriggeredWaves: MutableSet<Int> = mutableSetOf(),
    val waveSpawnTasks: MutableMap<Int, BukkitTask> = mutableMapOf()
)
