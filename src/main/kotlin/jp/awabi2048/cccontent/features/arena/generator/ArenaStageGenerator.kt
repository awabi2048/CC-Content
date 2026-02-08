package jp.awabi2048.cccontent.features.arena.generator

import jp.awabi2048.cccontent.features.arena.ArenaBounds
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Marker
import kotlin.math.max
import kotlin.random.Random

private enum class PathDirection(val dx: Int, val dz: Int) {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    fun left(): PathDirection = entries[(ordinal + 3) % 4]
    fun right(): PathDirection = entries[(ordinal + 1) % 4]
    fun opposite(): PathDirection = entries[(ordinal + 2) % 4]
}

private data class TilePoint(val x: Int, val z: Int)

private data class TilePlacement(
    val index: Int,
    val point: TilePoint,
    val structureType: ArenaStructureType,
    val rotationQuarter: Int
)

data class ArenaStageBuildResult(
    val playerSpawn: Location,
    val stageBounds: ArenaBounds,
    val roomBounds: Map<Int, ArenaBounds>,
    val roomMobSpawns: Map<Int, List<Location>>,
    val barrierLocation: Location
)

class ArenaStageGenerator {
    fun build(
        world: World,
        origin: Location,
        theme: ArenaTheme,
        waves: Int,
        random: Random = Random.Default
    ): ArenaStageBuildResult {
        val path = generatePath(waves * 2, random)
        val placements = toPlacements(path, random)
        val tileSize = theme.tileSize

        for (placement in placements) {
            val base = locationForTile(origin, placement.point, tileSize)
            placeStructure(theme, placement, base, random)
        }

        val roomBounds = mutableMapOf<Int, ArenaBounds>()
        val roomMobSpawns = mutableMapOf<Int, List<Location>>()
        val minTileX = path.minOf { it.x }
        val maxTileX = path.maxOf { it.x }
        val minTileZ = path.minOf { it.z }
        val maxTileZ = path.maxOf { it.z }
        val stageBounds = ArenaBounds(
            minX = origin.blockX + minTileX * tileSize,
            maxX = origin.blockX + (maxTileX + 1) * tileSize - 1,
            minZ = origin.blockZ + minTileZ * tileSize,
            maxZ = origin.blockZ + (maxTileZ + 1) * tileSize - 1
        )

        val roomPoints = path.filterIndexed { index, _ -> index % 2 == 0 }
        roomPoints.forEachIndexed { roomIndex, point ->
            val base = locationForTile(origin, point, tileSize)
            roomBounds[roomIndex] = ArenaBounds(
                minX = base.blockX,
                maxX = base.blockX + tileSize - 1,
                minZ = base.blockZ,
                maxZ = base.blockZ + tileSize - 1
            )

            if (roomIndex > 0) {
                val markers = findMarkers(world, base, tileSize)
                roomMobSpawns[roomIndex] = if (markers.mobSpawns.isNotEmpty()) {
                    markers.mobSpawns
                } else {
                    listOf(base.clone().add(tileSize / 2.0, 1.0, tileSize / 2.0))
                }
            }
        }

        val finalRoomPoint = roomPoints.last()
        val finalRoomBase = locationForTile(origin, finalRoomPoint, tileSize)
        val finalMarkers = findMarkers(world, finalRoomBase, tileSize)
        val barrierLocation = finalMarkers.barrierCore
            ?: finalRoomBase.clone().add(tileSize / 2.0, 1.0, tileSize / 2.0)
        barrierLocation.block.type = org.bukkit.Material.RESPAWN_ANCHOR

        val playerSpawn = locationForTile(origin, roomPoints.first(), tileSize)
            .add(tileSize / 2.0, 1.0, tileSize / 2.0)

        return ArenaStageBuildResult(
            playerSpawn = playerSpawn,
            stageBounds = stageBounds,
            roomBounds = roomBounds,
            roomMobSpawns = roomMobSpawns,
            barrierLocation = barrierLocation
        )
    }

    private fun generatePath(stepCount: Int, random: Random): List<TilePoint> {
        for (attempt in 0 until 80) {
            val path = mutableListOf(TilePoint(0, 0))
            val visited = mutableSetOf(TilePoint(0, 0))
            var dir = PathDirection.entries.random(random)

            var failed = false
            for (step in 0 until stepCount) {
                val current = path.last()
                val candidates = listOf(dir, dir.left(), dir.right())
                    .map { candidate -> candidate to TilePoint(current.x + candidate.dx, current.z + candidate.dz) }
                    .filter { (_, next) -> !visited.contains(next) }

                val selected = when {
                    candidates.isEmpty() -> null
                    candidates.size == 1 -> candidates.first()
                    else -> {
                        val forward = candidates.firstOrNull { it.first == dir }
                        val turnCandidates = candidates.filter { it.first != dir }
                        if (forward != null && random.nextDouble() < 0.60) {
                            forward
                        } else {
                            turnCandidates.randomOrNull(random) ?: forward
                        }
                    }
                }

                if (selected == null) {
                    failed = true
                    break
                }

                dir = selected.first
                path.add(selected.second)
                visited.add(selected.second)
            }

            if (!failed) {
                return path
            }
        }

        val fallback = mutableListOf(TilePoint(0, 0))
        repeat(stepCount) { i -> fallback.add(TilePoint(i + 1, 0)) }
        return fallback
    }

    private fun toPlacements(path: List<TilePoint>, random: Random): List<TilePlacement> {
        val result = mutableListOf<TilePlacement>()
        for (index in path.indices) {
            val current = path[index]
            val previous = if (index > 0) path[index - 1] else null
            val next = if (index < path.lastIndex) path[index + 1] else null

            val fromDir = previous?.let { directionOf(it, current) }
            val toDir = next?.let { directionOf(current, it) }

            val placement = when {
                fromDir == null && toDir != null -> TilePlacement(
                    index,
                    current,
                    ArenaStructureType.STRAIGHT,
                    straightRotation(toDir)
                )
                fromDir != null && toDir == null -> TilePlacement(
                    index,
                    current,
                    ArenaStructureType.STRAIGHT,
                    straightRotation(fromDir)
                )
                fromDir != null && toDir != null && fromDir.opposite() == toDir -> TilePlacement(
                    index,
                    current,
                    ArenaStructureType.STRAIGHT,
                    straightRotation(toDir)
                )
                fromDir != null && toDir != null -> TilePlacement(
                    index,
                    current,
                    ArenaStructureType.CORNER,
                    cornerRotation(fromDir, toDir)
                )
                else -> TilePlacement(index, current, ArenaStructureType.STRAIGHT, random.nextInt(4))
            }

            result.add(placement)
        }

        return result
    }

    private fun directionOf(from: TilePoint, to: TilePoint): PathDirection {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return PathDirection.entries.first { it.dx == dx && it.dz == dz }
    }

    private fun straightRotation(direction: PathDirection): Int {
        return when (direction) {
            PathDirection.NORTH, PathDirection.SOUTH -> 0
            PathDirection.EAST, PathDirection.WEST -> 1
        }
    }

    private fun cornerRotation(fromDir: PathDirection, toDir: PathDirection): Int {
        val pair = setOf(fromDir, toDir)
        return when {
            pair == setOf(PathDirection.NORTH, PathDirection.WEST) -> 1
            pair == setOf(PathDirection.NORTH, PathDirection.EAST) -> 2
            pair == setOf(PathDirection.SOUTH, PathDirection.EAST) -> 3
            pair == setOf(PathDirection.SOUTH, PathDirection.WEST) -> 0
            else -> 0
        }
    }

    private fun placeStructure(
        theme: ArenaTheme,
        placement: TilePlacement,
        location: Location,
        random: Random
    ) {
        val structure = theme.structures[placement.structureType]
            ?.randomOrNull(random)
            ?: return

        val rotation = toRotation(placement.rotationQuarter)
        val shift = (theme.tileSize - 1).toDouble()
        var offsetX = 0.0
        var offsetZ = 0.0

        when (rotation) {
            StructureRotation.CLOCKWISE_90 -> offsetX = shift
            StructureRotation.CLOCKWISE_180 -> {
                offsetX = shift
                offsetZ = shift
            }
            StructureRotation.COUNTERCLOCKWISE_90 -> offsetZ = shift
            else -> {}
        }

        structure.place(
            location.clone().add(offsetX, 0.0, offsetZ),
            true,
            rotation,
            Mirror.NONE,
            0,
            1.0f,
            java.util.Random()
        )
    }

    private fun toRotation(rotationQuarter: Int): StructureRotation {
        return when (rotationQuarter.mod(4)) {
            1 -> StructureRotation.CLOCKWISE_90
            2 -> StructureRotation.CLOCKWISE_180
            3 -> StructureRotation.COUNTERCLOCKWISE_90
            else -> StructureRotation.NONE
        }
    }

    private data class TileMarkers(
        val mobSpawns: List<Location>,
        val barrierCore: Location?
    )

    private fun findMarkers(world: World, tileBase: Location, tileSize: Int): TileMarkers {
        val minX = tileBase.blockX
        val maxX = minX + tileSize - 1
        val minZ = tileBase.blockZ
        val maxZ = minZ + tileSize - 1
        val minY = tileBase.blockY - 32
        val maxY = tileBase.blockY + 32

        val minChunkX = minX shr 4
        val maxChunkX = maxX shr 4
        val minChunkZ = minZ shr 4
        val maxChunkZ = maxZ shr 4

        val mobs = mutableListOf<Location>()
        var barrier: Location? = null

        for (cx in minChunkX..maxChunkX) {
            for (cz in minChunkZ..maxChunkZ) {
                val chunk = world.getChunkAt(cx, cz)
                if (!chunk.isLoaded) chunk.load()

                for (entity in chunk.entities) {
                    val loc = entity.location
                    if (loc.blockX !in minX..maxX || loc.blockZ !in minZ..maxZ) continue
                    if (loc.blockY !in minY..maxY) continue

                    when (entity) {
                        is Marker -> {
                            if (entity.scoreboardTags.contains("arena.marker.mob")) {
                                mobs.add(loc.clone())
                            }
                            if (entity.scoreboardTags.contains("arena.marker.barrier_core")) {
                                barrier = loc.clone()
                            }
                        }
                        is ArmorStand -> {
                            when (entity.customName) {
                                "ARENA_MOB" -> mobs.add(loc.clone())
                                "ARENA_BARRIER_CORE" -> barrier = loc.clone()
                            }
                        }
                    }
                }
            }
        }

        if (barrier == null && mobs.isNotEmpty()) {
            val center = mobs.reduce { acc, next ->
                Location(
                    acc.world,
                    (acc.x + next.x) / 2.0,
                    max(acc.y, next.y),
                    (acc.z + next.z) / 2.0
                )
            }
            barrier = center
        }

        return TileMarkers(mobs, barrier)
    }

    private fun locationForTile(origin: Location, point: TilePoint, tileSize: Int): Location {
        return origin.clone().add((point.x * tileSize).toDouble(), 0.0, (point.z * tileSize).toDouble())
    }
}
