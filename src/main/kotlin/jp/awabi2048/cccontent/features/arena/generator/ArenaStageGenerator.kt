package jp.awabi2048.cccontent.features.arena.generator

import jp.awabi2048.cccontent.features.arena.ArenaBounds
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Marker
import kotlin.math.max
import kotlin.math.floor
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
    val rotationQuarter: Int,
    val isRoom: Boolean
)

private data class PlacementGeometry(
    val origin: Location,
    val bounds: ArenaBounds
)

data class ArenaStageBuildResult(
    val playerSpawn: Location,
    val entranceLocation: Location,
    val stageBounds: ArenaBounds,
    val roomBounds: Map<Int, ArenaBounds>,
    val corridorBounds: Map<Int, ArenaBounds>,
    val roomMobSpawns: Map<Int, List<Location>>,
    val corridorDoorBlocks: Map<Int, List<Location>>,
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
        val roomPath = generatePath(waves, random)
        val placements = toPlacements(roomPath).sortedBy { it.index }
        val gridPitch = theme.gridPitch
        val placementBoundsByIndex = mutableMapOf<Int, ArenaBounds>()
        var previousPlacement: TilePlacement? = null

        for (placement in placements) {
            val base = locationForTile(origin, placement.point, gridPitch)
            val template = pickStructureTemplate(theme, placement, random)
                ?: error("[Arena] 構造テンプレートが見つかりません: type=${placement.structureType}")

            val geometry = if (previousPlacement == null) {
                computeFirstPlacementGeometry(base, gridPitch, template.size, placement.rotationQuarter)
            } else {
                val previous = previousPlacement ?: error("[Arena] 直前配置が見つかりません")
                val previousBounds = placementBoundsByIndex[previous.index]
                    ?: error("[Arena] 直前配置の境界情報が見つかりません: index=${previous.index}")
                computeConnectedPlacementGeometry(
                    world = world,
                    previousPlacement = previous,
                    currentPlacement = placement,
                    previousBounds = previousBounds,
                    currentSize = template.size,
                    currentRotationQuarter = placement.rotationQuarter
                )
            }

            placeStructure(template, placement.rotationQuarter, geometry.origin)
            placementBoundsByIndex[placement.index] = geometry.bounds
            previousPlacement = placement
        }

        val roomBounds = mutableMapOf<Int, ArenaBounds>()
        val corridorBounds = mutableMapOf<Int, ArenaBounds>()
        val roomMobSpawns = mutableMapOf<Int, List<Location>>()
        val corridorDoorBlocks = mutableMapOf<Int, List<Location>>()
        val allBounds = placementBoundsByIndex.values
        require(allBounds.isNotEmpty()) { "[Arena] ステージ境界を計算できません" }
        val stageBounds = ArenaBounds(
            minX = allBounds.minOf { it.minX },
            maxX = allBounds.maxOf { it.maxX },
            minY = allBounds.minOf { it.minY },
            maxY = allBounds.maxOf { it.maxY },
            minZ = allBounds.minOf { it.minZ },
            maxZ = allBounds.maxOf { it.maxZ }
        )

        val roomPlacements = placements.filter { it.isRoom }.sortedBy { it.index }
        roomPlacements.forEachIndexed { roomIndex, roomPlacement ->
            val bounds = placementBoundsByIndex[roomPlacement.index] ?: return@forEachIndexed
            roomBounds[roomIndex] = bounds

            if (roomIndex > 0) {
                val markers = findMarkers(world, bounds)
                roomMobSpawns[roomIndex] = if (markers.mobSpawns.isNotEmpty()) {
                    markers.mobSpawns
                } else {
                    listOf(boundsCenter(world, bounds, 1.0))
                }
            }
        }

        placements.filter { !it.isRoom }.forEach { corridorPlacement ->
            val targetWave = (corridorPlacement.index + 1) / 2
            if (targetWave !in 1..waves) return@forEach
            val bounds = placementBoundsByIndex[corridorPlacement.index] ?: return@forEach
            corridorBounds[targetWave] = bounds

            val markers = findMarkers(world, bounds)
            require(markers.doorBlocks.isNotEmpty()) {
                "[Arena] ドアブロックマーカーが見つかりません: wave=$targetWave bounds=$bounds"
            }
            corridorDoorBlocks[targetWave] = markers.doorBlocks
        }

        val finalRoomBounds = placementBoundsByIndex[roomPlacements.last().index]
            ?: error("[Arena] 最終部屋の境界が見つかりません")
        val finalMarkers = findMarkers(world, finalRoomBounds)
        val barrierLocation = finalMarkers.barrierCore
            ?: boundsCenter(world, finalRoomBounds, 1.0)
        barrierLocation.block.type = org.bukkit.Material.RESPAWN_ANCHOR

        val firstRoomBounds = placementBoundsByIndex[roomPlacements.first().index]
            ?: error("[Arena] 開始部屋の境界が見つかりません")
        val firstRoomMarkers = findMarkers(world, firstRoomBounds)
        val entranceLocation = firstRoomMarkers.entrance
            ?: error("[Arena] entrance マーカーが見つかりません")
        val playerSpawn = entranceLocation.clone()

        return ArenaStageBuildResult(
            playerSpawn = playerSpawn,
            entranceLocation = entranceLocation,
            stageBounds = stageBounds,
            roomBounds = roomBounds,
            corridorBounds = corridorBounds,
            roomMobSpawns = roomMobSpawns,
            corridorDoorBlocks = corridorDoorBlocks,
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

    private fun toPlacements(roomPath: List<TilePoint>): List<TilePlacement> {
        val result = mutableListOf<TilePlacement>()
        for (index in roomPath.indices) {
            val currentRoom = roomPath[index]
            val current = TilePoint(currentRoom.x * 2, currentRoom.z * 2)
            val previous = if (index > 0) roomPath[index - 1] else null
            val next = if (index < roomPath.lastIndex) roomPath[index + 1] else null

            val fromDir = previous?.let { directionOf(it, currentRoom) }
            val toDir = next?.let { directionOf(currentRoom, it) }

            val placement = when {
                fromDir == null && toDir != null -> TilePlacement(
                    index * 2,
                    current,
                    ArenaStructureType.ENTRANCE,
                    straightRotation(toDir),
                    true
                )
                fromDir != null && toDir == null -> TilePlacement(
                    index * 2,
                    current,
                    ArenaStructureType.GOAL,
                    straightRotation(fromDir),
                    true
                )
                fromDir != null && toDir != null && fromDir == toDir -> TilePlacement(
                    index * 2,
                    current,
                    ArenaStructureType.STRAIGHT,
                    straightRotation(toDir),
                    true
                )
                fromDir != null && toDir != null -> TilePlacement(
                    index * 2,
                    current,
                    ArenaStructureType.CORNER,
                    cornerRotation(fromDir, toDir),
                    true
                )
                else -> TilePlacement(index * 2, current, ArenaStructureType.STRAIGHT, 0, true)
            }

            result.add(placement)

            if (next != null) {
                val corridorDir = directionOf(currentRoom, next)
                val corridorPoint = TilePoint(currentRoom.x + next.x, currentRoom.z + next.z)
                result.add(
                    TilePlacement(
                        index = index * 2 + 1,
                        point = corridorPoint,
                        structureType = ArenaStructureType.CORRIDOR,
                        rotationQuarter = straightRotation(corridorDir),
                        isRoom = false
                    )
                )
            }
        }

        return result.sortedBy { it.index }
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
        val opening1 = fromDir.opposite()
        val opening2 = toDir
        val openings = setOf(opening1, opening2)
        return when (openings) {
            setOf(PathDirection.NORTH, PathDirection.EAST) -> 0
            setOf(PathDirection.EAST, PathDirection.SOUTH) -> 1
            setOf(PathDirection.SOUTH, PathDirection.WEST) -> 2
            setOf(PathDirection.WEST, PathDirection.NORTH) -> 3
            else -> 0
        }
    }

    private fun pickStructureTemplate(
        theme: ArenaTheme,
        placement: TilePlacement,
        random: Random
    ): ArenaStructureTemplate? {
        return theme.structures[placement.structureType]
            ?.randomOrNull(random)
    }

    private fun placeStructure(
        template: ArenaStructureTemplate,
        rotationQuarter: Int,
        location: Location
    ) {
        val rotation = toRotation(rotationQuarter)

        template.structure.place(
            location,
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

    private fun computeFirstPlacementGeometry(
        tileBase: Location,
        gridPitch: Int,
        size: ArenaStructureSize,
        rotationQuarter: Int
    ): PlacementGeometry {
        val (widthX, widthZ) = rotatedFootprint(size, rotationQuarter)
        val offsetX = ((gridPitch - widthX) / 2.0).coerceAtLeast(0.0)
        val offsetZ = ((gridPitch - widthZ) / 2.0).coerceAtLeast(0.0)
        val footprintMin = tileBase.clone().add(offsetX, 0.0, offsetZ)
        val rotation = toRotation(rotationQuarter)
        val (rotationOffsetX, rotationOffsetZ) = placementOffset(size, rotation)
        val placeOrigin = footprintMin.clone().add(rotationOffsetX, 0.0, rotationOffsetZ)
        val bounds = placementBounds(footprintMin, widthX, widthZ, size.y)
        return PlacementGeometry(placeOrigin, bounds)
    }

    private fun computeConnectedPlacementGeometry(
        world: World,
        previousPlacement: TilePlacement,
        currentPlacement: TilePlacement,
        previousBounds: ArenaBounds,
        currentSize: ArenaStructureSize,
        currentRotationQuarter: Int
    ): PlacementGeometry {
        val direction = directionOf(previousPlacement.point, currentPlacement.point)
        val (currentWidthX, currentWidthZ) = rotatedFootprint(currentSize, currentRotationQuarter)
        val previousCenterX2 = previousBounds.minX + previousBounds.maxX + 1
        val previousCenterZ2 = previousBounds.minZ + previousBounds.maxZ + 1

        val minX: Int
        val minZ: Int
        when (direction) {
            PathDirection.EAST -> {
                minX = previousBounds.maxX + 1
                minZ = centeredMin(previousCenterZ2, currentWidthZ)
            }
            PathDirection.WEST -> {
                minX = previousBounds.minX - currentWidthX
                minZ = centeredMin(previousCenterZ2, currentWidthZ)
            }
            PathDirection.SOUTH -> {
                minX = centeredMin(previousCenterX2, currentWidthX)
                minZ = previousBounds.maxZ + 1
            }
            PathDirection.NORTH -> {
                minX = centeredMin(previousCenterX2, currentWidthX)
                minZ = previousBounds.minZ - currentWidthZ
            }
        }

        val footprintMin = Location(world, minX.toDouble(), previousBounds.minY.toDouble(), minZ.toDouble())
        val rotation = toRotation(currentRotationQuarter)
        val (rotationOffsetX, rotationOffsetZ) = placementOffset(currentSize, rotation)
        val placeOrigin = footprintMin.clone().add(rotationOffsetX, 0.0, rotationOffsetZ)
        val bounds = placementBounds(footprintMin, currentWidthX, currentWidthZ, currentSize.y)
        return PlacementGeometry(placeOrigin, bounds)
    }

    private fun centeredMin(referenceCenter2: Int, width: Int): Int {
        return floor((referenceCenter2 - width) / 2.0).toInt()
    }

    private fun placementOffset(size: ArenaStructureSize, rotation: StructureRotation): Pair<Double, Double> {
        return when (rotation) {
            StructureRotation.CLOCKWISE_90 -> (size.z - 1).toDouble() to 0.0
            StructureRotation.CLOCKWISE_180 -> (size.x - 1).toDouble() to (size.z - 1).toDouble()
            StructureRotation.COUNTERCLOCKWISE_90 -> 0.0 to (size.x - 1).toDouble()
            else -> 0.0 to 0.0
        }
    }

    private fun rotatedFootprint(size: ArenaStructureSize, rotationQuarter: Int): Pair<Int, Int> {
        val rotatedQuarter = rotationQuarter.mod(4)
        val widthX = if (rotatedQuarter == 1 || rotatedQuarter == 3) size.z else size.x
        val widthZ = if (rotatedQuarter == 1 || rotatedQuarter == 3) size.x else size.z
        return widthX to widthZ
    }

    private fun placementBounds(base: Location, widthX: Int, widthZ: Int, height: Int): ArenaBounds {
        return ArenaBounds(
            minX = base.blockX,
            maxX = base.blockX + widthX - 1,
            minY = base.blockY,
            maxY = base.blockY + height - 1,
            minZ = base.blockZ,
            maxZ = base.blockZ + widthZ - 1
        )
    }

    private fun boundsCenter(world: World, bounds: ArenaBounds, yOffset: Double): Location {
        val centerX = (bounds.minX + bounds.maxX + 1) / 2.0
        val centerY = bounds.minY + yOffset
        val centerZ = (bounds.minZ + bounds.maxZ + 1) / 2.0
        return Location(world, centerX, centerY, centerZ)
    }

    private data class TileMarkers(
        val mobSpawns: List<Location>,
        val entrance: Location?,
        val doorBlocks: List<Location>,
        val barrierCore: Location?
    )

    private fun findMarkers(world: World, bounds: ArenaBounds): TileMarkers {
        val minX = bounds.minX
        val maxX = bounds.maxX
        val minZ = bounds.minZ
        val maxZ = bounds.maxZ
        val minY = bounds.minY - 2
        val maxY = bounds.maxY + 2

        val minChunkX = minX shr 4
        val maxChunkX = maxX shr 4
        val minChunkZ = minZ shr 4
        val maxChunkZ = maxZ shr 4

        val mobs = mutableListOf<Location>()
        var entrance: Location? = null
        val doorBlocks = mutableListOf<Location>()
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
                            if (entity.scoreboardTags.contains("arena.marker.entrance")) {
                                entrance = loc.clone()
                            }
                            if (entity.scoreboardTags.contains("arena.marker.door_block")) {
                                doorBlocks.add(loc.clone())
                            }
                            if (entity.scoreboardTags.contains("arena.marker.barrier_core")) {
                                barrier = loc.clone()
                            }
                        }
                        is ArmorStand -> {
                            when (entity.customName) {
                                "ARENA_MOB" -> mobs.add(loc.clone())
                                "ARENA_ENTRANCE" -> entrance = loc.clone()
                                "ARENA_DOOR_BLOCK" -> doorBlocks.add(loc.clone())
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

        return TileMarkers(mobs, entrance, doorBlocks, barrier)
    }

    private fun locationForTile(origin: Location, point: TilePoint, gridPitch: Int): Location {
        return origin.clone().add((point.x * gridPitch).toDouble(), 0.0, (point.z * gridPitch).toDouble())
    }
}
