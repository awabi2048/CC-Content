package jp.awabi2048.cccontent.features.arena

import org.bukkit.Location
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

data class ArenaBounds(
    val minX: Int,
    val maxX: Int,
    val minZ: Int,
    val maxZ: Int
) {
    fun contains(x: Double, z: Double): Boolean {
        return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1
    }
}

data class ArenaSession(
    val ownerPlayerId: UUID,
    val worldName: String,
    val themeId: String,
    val waves: Int,
    val participants: MutableSet<UUID>,
    val returnLocations: MutableMap<UUID, Location>,
    val playerSpawn: Location,
    val stageBounds: ArenaBounds,
    val roomBounds: Map<Int, ArenaBounds>,
    val roomMobSpawns: Map<Int, List<Location>>,
    val barrierLocation: Location,
    var currentWave: Int = 0,
    var barrierActive: Boolean = false,
    val startedWaves: MutableSet<Int> = mutableSetOf(),
    val activeMobs: MutableSet<UUID> = mutableSetOf(),
    val waveMobCount: MutableMap<Int, Int> = mutableMapOf(),
    val waveSpawnTasks: MutableMap<Int, BukkitTask> = mutableMapOf()
)
