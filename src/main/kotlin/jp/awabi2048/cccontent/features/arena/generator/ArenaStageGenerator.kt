package jp.awabi2048.cccontent.features.arena.generator

import jp.awabi2048.cccontent.features.arena.ArenaBounds
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.entity.Marker
import kotlin.math.floor
import kotlin.random.Random

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

data class ArenaStageValidationIssue(
    val structureName: String,
    val missingMarkers: List<String>
)

class ArenaStageBuildException(
    val issues: List<ArenaStageValidationIssue>
) : IllegalStateException(
    issues.joinToString(" | ") { issue ->
        "structure=${issue.structureName} missing=${issue.missingMarkers.joinToString(", ")}"
    }
)

data class ArenaStageBuildResult(
    val playerSpawn: Location,
    val entranceLocation: Location,
    val stageBounds: ArenaBounds,
    val roomBounds: Map<Int, ArenaBounds>,
    val corridorBounds: Map<Int, ArenaBounds>,
    val roomMobSpawns: Map<Int, List<Location>>,
    val corridorDoorBlocks: Map<Int, List<Location>>,
    val doorAnimationPlacements: Map<Int, List<ArenaDoorAnimationPlacement>>,
    val barrierLocation: Location
)

data class ArenaDoorAnimationPlacement(
    val structureType: ArenaStructureType,
    val placeOrigin: Location,
    val rotationQuarter: Int,
    val openFrames: List<ArenaStructureTemplate>
)

private data class SelectedStructureVariant(
    val baseTemplate: ArenaStructureTemplate,
    val animatedVariant: ArenaAnimatedStructureVariant?
)

class ArenaStageGenerator {
    fun build(
        world: World,
        origin: Location,
        theme: ArenaTheme,
        waves: Int,
        random: Random = Random.Default
    ): ArenaStageBuildResult {
        val placements = generateAlternatingPlacements(waves, random, theme.orientation).sortedBy { it.index }
        val gridPitch = theme.gridPitch
        val placementBoundsByIndex = mutableMapOf<Int, ArenaBounds>()
        val selectedByPlacementIndex = mutableMapOf<Int, SelectedStructureVariant>()
        val placementOriginByIndex = mutableMapOf<Int, Location>()
        val validationIssues = mutableListOf<ArenaStageValidationIssue>()
        var previousPlacement: TilePlacement? = null

        for (placement in placements) {
            val base = locationForTile(origin, placement.point, gridPitch)
            val selected = pickStructureVariant(theme, placement, random)
                ?: error("[Arena] 構造テンプレートが見つかりません: type=${placement.structureType}")
            val template = selected.baseTemplate

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
            selectedByPlacementIndex[placement.index] = selected
            placementOriginByIndex[placement.index] = geometry.origin.clone()
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
            val markers = findMarkers(world, bounds)
            val templateName = selectedByPlacementIndex[roomPlacement.index]?.baseTemplate?.name ?: "unknown"

            if (roomIndex > 0) {
                if (markers.mobSpawns.isEmpty()) {
                    validationIssues.add(
                        ArenaStageValidationIssue(
                            structureName = templateName,
                            missingMarkers = listOf("arena.marker.mob")
                        )
                    )
                } else {
                    roomMobSpawns[roomIndex] = markers.mobSpawns
                }
            }

            val targetWave = when (roomPlacement.structureType) {
                ArenaStructureType.ENTRANCE -> 1
                ArenaStructureType.STRAIGHT -> roomIndex + 1
                else -> null
            }

            if (targetWave != null && targetWave in 1..waves) {
                val doorBlocks = markers.doorBlocks
                if (doorBlocks.size != 1) {
                    val reason = if (doorBlocks.isEmpty()) {
                        "arena.marker.door_block"
                    } else {
                        "arena.marker.door_block(single_required)"
                    }
                    validationIssues.add(
                        ArenaStageValidationIssue(
                            structureName = templateName,
                            missingMarkers = listOf(reason)
                        )
                    )
                } else {
                    corridorDoorBlocks[targetWave] = listOf(doorBlocks.first())
                }
            }
        }

        placements.filter { !it.isRoom }.forEach { corridorPlacement ->
            val targetWave = (corridorPlacement.index + 1) / 2
            if (targetWave !in 1..waves) return@forEach
            val bounds = placementBoundsByIndex[corridorPlacement.index] ?: return@forEach
            corridorBounds[targetWave] = bounds
        }

        val finalRoomBounds = placementBoundsByIndex[roomPlacements.last().index]
            ?: error("[Arena] 最終部屋の境界が見つかりません")
        val finalMarkers = findMarkers(world, finalRoomBounds)
        val barrierLocation = finalMarkers.barrierCore
        if (barrierLocation == null) {
            validationIssues.add(
                ArenaStageValidationIssue(
                    structureName = selectedByPlacementIndex[roomPlacements.last().index]?.baseTemplate?.name ?: "unknown",
                    missingMarkers = listOf("arena.marker.barrier_core")
                )
            )
        }

        val firstRoomBounds = placementBoundsByIndex[roomPlacements.first().index]
            ?: error("[Arena] 開始部屋の境界が見つかりません")
        val firstRoomMarkers = findMarkers(world, firstRoomBounds)
        val entranceLocation = firstRoomMarkers.entrance
        if (entranceLocation == null) {
            validationIssues.add(
                ArenaStageValidationIssue(
                    structureName = selectedByPlacementIndex[roomPlacements.first().index]?.baseTemplate?.name ?: "unknown",
                    missingMarkers = listOf("arena.marker.entrance")
                )
            )
        }

        val doorAnimationPlacements = buildDoorAnimationPlacements(
            placements = placements,
            waves = waves,
            selectedByPlacementIndex = selectedByPlacementIndex,
            placementOriginByIndex = placementOriginByIndex
        )

        if (validationIssues.isNotEmpty()) {
            throw ArenaStageBuildException(validationIssues)
        }

        val resolvedEntranceLocation = entranceLocation ?: error("[Arena] 開始部屋の entrance マーカーが見つかりません")
        val resolvedBarrierLocation = barrierLocation ?: error("[Arena] 最終部屋の barrier_core マーカーが見つかりません")
        val playerSpawn = resolvedEntranceLocation.clone()

        return ArenaStageBuildResult(
            playerSpawn = playerSpawn,
            entranceLocation = resolvedEntranceLocation,
            stageBounds = stageBounds,
            roomBounds = roomBounds,
            corridorBounds = corridorBounds,
            roomMobSpawns = roomMobSpawns,
            corridorDoorBlocks = corridorDoorBlocks,
            doorAnimationPlacements = doorAnimationPlacements,
            barrierLocation = resolvedBarrierLocation
        )
    }

    private fun generateAlternatingPlacements(
        waves: Int,
        random: Random,
        orientation: ArenaStructureOrientation
    ): List<TilePlacement> {
        if (waves <= 0) {
            return listOf(
                TilePlacement(
                    index = 0,
                    point = TilePoint(0, 0),
                    structureType = ArenaStructureType.ENTRANCE,
                    rotationQuarter = entranceRotation(orientation, ArenaPathDirection.NORTH),
                    isRoom = true
                )
            )
        }

        repeat(200) {
            val roomPoints = mutableListOf(TilePoint(0, 0))
            val roomDirections = mutableListOf(ArenaPathDirection.entries.random(random))
            val occupied = mutableSetOf(TilePoint(0, 0))
            val corridors = mutableListOf<TilePlacement>()
            var failed = false

            for (wave in 1..waves) {
                val previousRoom = roomPoints.last()
                val startDirection = roomDirections.last()
                val corridorPoint = TilePoint(previousRoom.x + startDirection.dx, previousRoom.z + startDirection.dz)
                if (occupied.contains(corridorPoint)) {
                    failed = true
                    break
                }

                val candidateDirections = listOf(startDirection, startDirection.left(), startDirection.right()).shuffled(random)
                var selectedDirection: ArenaPathDirection? = null
                var selectedRoomPoint: TilePoint? = null

                for (endDirection in candidateDirections) {
                    if (endDirection != startDirection && !canResolveCornerRotation(orientation, startDirection.opposite(), endDirection)) {
                        continue
                    }
                    val nextRoom = if (endDirection == startDirection) {
                        TilePoint(corridorPoint.x + startDirection.dx, corridorPoint.z + startDirection.dz)
                    } else {
                        TilePoint(corridorPoint.x + endDirection.dx, corridorPoint.z + endDirection.dz)
                    }
                    if (occupied.contains(nextRoom)) {
                        continue
                    }

                    selectedDirection = endDirection
                    selectedRoomPoint = nextRoom
                    break
                }

                if (selectedDirection == null || selectedRoomPoint == null) {
                    failed = true
                    break
                }

                val corridorType = if (selectedDirection == startDirection) {
                    ArenaStructureType.CORRIDOR
                } else {
                    ArenaStructureType.CORNER
                }
                val corridorRotation = if (selectedDirection == startDirection) {
                    corridorRotation(orientation, startDirection.opposite())
                } else {
                    cornerRotation(orientation, startDirection.opposite(), selectedDirection)
                }

                corridors.add(
                    TilePlacement(
                        index = wave * 2 - 1,
                        point = corridorPoint,
                        structureType = corridorType,
                        rotationQuarter = corridorRotation,
                        isRoom = false
                    )
                )

                roomPoints.add(selectedRoomPoint)
                roomDirections.add(selectedDirection)
                occupied.add(corridorPoint)
                occupied.add(selectedRoomPoint)
            }

            if (failed) {
                return@repeat
            }

            val result = mutableListOf<TilePlacement>()
            result.add(
                TilePlacement(
                    index = 0,
                    point = roomPoints.first(),
                    structureType = ArenaStructureType.ENTRANCE,
                    rotationQuarter = entranceRotation(orientation, roomDirections.first()),
                    isRoom = true
                )
            )

            for (wave in 1..waves) {
                result.add(corridors[wave - 1])
                result.add(
                    TilePlacement(
                        index = wave * 2,
                        point = roomPoints[wave],
                        structureType = if (wave == waves) ArenaStructureType.GOAL else ArenaStructureType.STRAIGHT,
                        rotationQuarter = if (wave == waves) {
                            goalRotation(orientation, roomDirections[wave].opposite())
                        } else {
                            straightRotation(orientation, roomDirections[wave].opposite())
                        },
                        isRoom = true
                    )
                )
            }

            return result
        }

        return buildLinearFallbackPlacements(waves, orientation)
    }

    private fun buildLinearFallbackPlacements(waves: Int, orientation: ArenaStructureOrientation): List<TilePlacement> {
        val result = mutableListOf<TilePlacement>()
        var roomPoint = TilePoint(0, 0)
        var index = 0
        result.add(
            TilePlacement(
                index = index,
                point = roomPoint,
                structureType = ArenaStructureType.ENTRANCE,
                rotationQuarter = entranceRotation(orientation, ArenaPathDirection.EAST),
                isRoom = true
            )
        )
        index += 1

        for (wave in 1..waves) {
            val corridorPoint = TilePoint(roomPoint.x + ArenaPathDirection.EAST.dx, roomPoint.z + ArenaPathDirection.EAST.dz)
            result.add(
                TilePlacement(
                    index = index,
                    point = corridorPoint,
                    structureType = ArenaStructureType.CORRIDOR,
                    rotationQuarter = corridorRotation(orientation, ArenaPathDirection.WEST),
                    isRoom = false
                )
            )
            index += 1

            roomPoint = TilePoint(corridorPoint.x + ArenaPathDirection.EAST.dx, corridorPoint.z + ArenaPathDirection.EAST.dz)
            result.add(
                TilePlacement(
                    index = index,
                    point = roomPoint,
                    structureType = if (wave == waves) ArenaStructureType.GOAL else ArenaStructureType.STRAIGHT,
                    rotationQuarter = if (wave == waves) {
                        goalRotation(orientation, ArenaPathDirection.WEST)
                    } else {
                        straightRotation(orientation, ArenaPathDirection.WEST)
                    },
                    isRoom = true
                )
            )
            index += 1
        }

        return result
    }

    private fun directionOf(from: TilePoint, to: TilePoint): ArenaPathDirection {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return ArenaPathDirection.entries.first { it.dx == dx && it.dz == dz }
    }

    private fun entranceRotation(orientation: ArenaStructureOrientation, actualExit: ArenaPathDirection): Int {
        return rotationQuarterBetween(orientation.entranceExit, actualExit)
    }

    private fun straightRotation(orientation: ArenaStructureOrientation, actualEntry: ArenaPathDirection): Int {
        return rotationQuarterBetween(orientation.straightEntry, actualEntry)
    }

    private fun corridorRotation(orientation: ArenaStructureOrientation, actualEntry: ArenaPathDirection): Int {
        return rotationQuarterBetween(orientation.corridorEntry, actualEntry)
    }

    private fun goalRotation(orientation: ArenaStructureOrientation, actualEntry: ArenaPathDirection): Int {
        return rotationQuarterBetween(orientation.goalEntry, actualEntry)
    }

    private fun cornerRotation(
        orientation: ArenaStructureOrientation,
        actualEntry: ArenaPathDirection,
        actualExit: ArenaPathDirection
    ): Int {
        if (!canResolveCornerRotation(orientation, actualEntry, actualExit)) {
            error("[Arena] corner 回転が解決できません: entry=${actualEntry.token}, exit=${actualExit.token}")
        }
        for (quarter in 0..3) {
            val rotatedEntry = orientation.cornerEntry.rotateClockwise(quarter)
            val rotatedExit = orientation.cornerExit.rotateClockwise(quarter)
            if (rotatedEntry == actualEntry && rotatedExit == actualExit) {
                return quarter
            }
        }
        error("[Arena] corner 回転が解決できません: entry=${actualEntry.token}, exit=${actualExit.token}")
    }

    private fun canResolveCornerRotation(
        orientation: ArenaStructureOrientation,
        actualEntry: ArenaPathDirection,
        actualExit: ArenaPathDirection
    ): Boolean {
        if (actualEntry == actualExit || actualEntry.opposite() == actualExit) {
            return false
        }
        for (quarter in 0..3) {
            val rotatedEntry = orientation.cornerEntry.rotateClockwise(quarter)
            val rotatedExit = orientation.cornerExit.rotateClockwise(quarter)
            if (rotatedEntry == actualEntry && rotatedExit == actualExit) {
                return true
            }
        }
        return false
    }

    private fun rotationQuarterBetween(from: ArenaPathDirection, to: ArenaPathDirection): Int {
        return (to.ordinal - from.ordinal).mod(4)
    }

    private fun pickStructureVariant(
        theme: ArenaTheme,
        placement: TilePlacement,
        random: Random
    ): SelectedStructureVariant? {
        return if (placement.structureType.supportsAnimation) {
            theme.animatedStructures[placement.structureType]
                ?.randomOrNull(random)
                ?.let { variant ->
                    SelectedStructureVariant(
                        baseTemplate = variant.closedTemplate,
                        animatedVariant = variant
                    )
                }
        } else {
            theme.staticStructures[placement.structureType]
                ?.randomOrNull(random)
                ?.let { variant ->
                    SelectedStructureVariant(
                        baseTemplate = variant.template,
                        animatedVariant = null
                    )
                }
        }
    }

    private fun buildDoorAnimationPlacements(
        placements: List<TilePlacement>,
        waves: Int,
        selectedByPlacementIndex: Map<Int, SelectedStructureVariant>,
        placementOriginByIndex: Map<Int, Location>
    ): Map<Int, List<ArenaDoorAnimationPlacement>> {
        val placementsByIndex = placements.associateBy { it.index }
        val result = mutableMapOf<Int, List<ArenaDoorAnimationPlacement>>()

        for (targetWave in 1..waves) {
            val animationTargets = mutableListOf<ArenaDoorAnimationPlacement>()
            val corridorIndex = targetWave * 2 - 1
            val roomIndex = targetWave * 2

            listOf(corridorIndex, roomIndex).forEach { index ->
                val placement = placementsByIndex[index] ?: return@forEach
                if (placement.structureType != ArenaStructureType.CORRIDOR && placement.structureType != ArenaStructureType.CORNER) {
                    return@forEach
                }

                val selected = selectedByPlacementIndex[index] ?: return@forEach
                val variant = selected.animatedVariant ?: return@forEach
                val origin = placementOriginByIndex[index] ?: return@forEach

                animationTargets.add(
                    ArenaDoorAnimationPlacement(
                        structureType = placement.structureType,
                        placeOrigin = origin.clone(),
                        rotationQuarter = placement.rotationQuarter,
                        openFrames = variant.openFrames
                    )
                )
            }

            if (animationTargets.isNotEmpty()) {
                result[targetWave] = animationTargets
            }
        }

        return result
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
            ArenaPathDirection.EAST -> {
                minX = previousBounds.maxX + 1
                minZ = centeredMin(previousCenterZ2, currentWidthZ)
            }
            ArenaPathDirection.WEST -> {
                minX = previousBounds.minX - currentWidthX
                minZ = centeredMin(previousCenterZ2, currentWidthZ)
            }
            ArenaPathDirection.SOUTH -> {
                minX = centeredMin(previousCenterX2, currentWidthX)
                minZ = previousBounds.maxZ + 1
            }
            ArenaPathDirection.NORTH -> {
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
                    if (loc.x < minX.toDouble() || loc.x > maxX.toDouble() + 1.0) continue
                    if (loc.z < minZ.toDouble() || loc.z > maxZ.toDouble() + 1.0) continue
                    if (loc.y < minY.toDouble() || loc.y > maxY.toDouble() + 1.0) continue

                    if (entity is Marker) {
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
                }
            }
        }

        return TileMarkers(mobs, entrance, doorBlocks, barrier)
    }

    private fun locationForTile(origin: Location, point: TilePoint, gridPitch: Int): Location {
        return origin.clone().add((point.x * gridPitch).toDouble(), 0.0, (point.z * gridPitch).toDouble())
    }
}
