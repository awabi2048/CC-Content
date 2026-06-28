@file:Suppress("REDUNDANT_ELSE_IN_WHEN")

package jp.awabi2048.cccontent.features.arena.generator

import jp.awabi2048.cccontent.features.arena.ArenaBounds
import jp.awabi2048.cccontent.features.arena.ArenaBlockKey
import jp.awabi2048.cccontent.features.arena.mechanic.ARENA_MECHANIC_MARKER_PREFIX
import jp.awabi2048.cccontent.features.arena.mechanic.ArenaMechanicMarker
import jp.awabi2048.cccontent.features.arena.mission.ArenaMissionType
import jp.awabi2048.cccontent.structure.CardinalDirection
import jp.awabi2048.cccontent.structure.StructureSchemas
import jp.awabi2048.cccontent.structure.StructurePasteOptions
import jp.awabi2048.cccontent.structure.StructureTransform
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import kotlin.math.floor
import kotlin.random.Random

private data class TilePoint(val x: Int, val z: Int)

private enum class PlacementRole {
    ENTRANCE,
    COMBAT_ROOM,
    GOAL_ROOM,
    TRANSIT_ROOM,
    PEDESTAL_ROOM,
    CORRIDOR
}

private data class PlacementTransform(
    val rotationQuarter: Int,
    val mirrored: Boolean = false
)

private data class TilePlacement(
    val index: Int,
    val point: TilePoint,
    val structureType: ArenaStructureType,
    val transform: PlacementTransform,
    val role: PlacementRole,
    val wave: Int? = null,
    val parentIndex: Int? = null
)

private data class PlacementGeometry(
    val origin: Location,
    val bounds: ArenaBounds
)

private data class ConnectionProfileCacheKey(
    val templateName: String,
    val rotationQuarter: Int,
    val mirrored: Boolean
)

private enum class ConnectionRole { IN, OUT }

private data class RotatedConnectionMarker(
    val blockX: Int,
    val blockY: Int,
    val blockZ: Int,
    val role: ConnectionRole
)

private data class RawConnectionMarker(
    val side: CardinalDirection,
    val role: ConnectionRole,
    val blockX: Int,
    val blockY: Int,
    val blockZ: Int,
    val posX: Double,
    val posZ: Double
)

private data class ConnectionMarkerProfile(
    val sideMarkers: Map<CardinalDirection, List<RotatedConnectionMarker>>,
    val widthX: Int,
    val widthZ: Int,
    val markerCount: Int,
    val issues: List<String>
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
    val entranceCheckpoint: Location,
    val stageBounds: ArenaBounds,
    val roomBounds: Map<Int, ArenaBounds>,
    val corridorBounds: Map<Int, ArenaBounds>,
    val transitBounds: Map<Int, ArenaBounds>,
    val pedestalBounds: Map<Int, ArenaBounds>,
    val pedestalMarkerBlocks: Set<ArenaBlockKey>,
    val roomMobSpawns: Map<Int, List<Location>>,
    val roomCheckpoints: Map<Int, Location>,
    val goalCheckpoint: Location,
    val corridorDoorBlocks: Map<Int, List<Location>>,
    val doorAnimationPlacements: Map<Int, List<ArenaDoorAnimationPlacement>>,
    val mechanicMarkersByWave: Map<Int, List<ArenaMechanicMarker>>,
    val barrierLocation: Location,
    val barrierPointLocations: List<Location>,
    val clearingBossLocations: List<Location> = emptyList()
)

data class ArenaDoorAnimationPlacement(
    val structureType: ArenaStructureType,
    val placeOrigin: Location,
    val rotationQuarter: Int,
    val mirrored: Boolean,
    val openFrames: List<ArenaStructureTemplate>
)

    private data class SelectedStructureVariant(
    val baseTemplate: ArenaStructureTemplate,
    val animatedVariant: ArenaAnimatedStructureVariant?,
    val variantKey: String
)

class ArenaStageGenerator {
    companion object {
        private const val CONNECTION_MARKER_LABEL = "arena.marker.connection"
        private const val MARKER_ENTITY_TYPE_ID = StructureSchemas.MARKER_ENTITY_TYPE
    }

    private enum class BuildPhase {
        PLACE_STRUCTURES,
        WAIT_FOR_MARKER_ENTITIES,
        SCAN_ROOMS,
        SCAN_CORRIDORS,
        FINALIZE,
        COMPLETE
    }

    private data class BuildJob(
        val world: World,
        val origin: Location,
        val theme: ArenaTheme,
        val missionTypeId: ArenaMissionType,
        val waves: Int,
        val pedestalRoomProbability: Double,
        val random: Random,
        val placements: List<TilePlacement>,
        val placementBoundsByIndex: MutableMap<Int, ArenaBounds> = mutableMapOf(),
        val selectedByPlacementIndex: MutableMap<Int, SelectedStructureVariant> = mutableMapOf(),
        val lastSelectedVariantKeyByType: MutableMap<ArenaStructureType, String> = mutableMapOf(),
        val placementOriginByIndex: MutableMap<Int, Location> = mutableMapOf(),
        val connectionProfilesByPlacementIndex: MutableMap<Int, ConnectionMarkerProfile> = mutableMapOf(),
        val connectionProfileCache: MutableMap<ConnectionProfileCacheKey, ConnectionMarkerProfile> = mutableMapOf(),
        val validationIssues: MutableList<ArenaStageValidationIssue> = mutableListOf(),
        var stageBounds: ArenaBounds? = null,
        val roomBounds: MutableMap<Int, ArenaBounds> = mutableMapOf(),
        val corridorBounds: MutableMap<Int, ArenaBounds> = mutableMapOf(),
        val transitBounds: MutableMap<Int, ArenaBounds> = mutableMapOf(),
        val pedestalBounds: MutableMap<Int, ArenaBounds> = mutableMapOf(),
        val pedestalMarkerBlocks: MutableSet<ArenaBlockKey> = mutableSetOf(),
        val roomMobSpawns: MutableMap<Int, List<Location>> = mutableMapOf(),
        val roomCheckpoints: MutableMap<Int, Location> = mutableMapOf(),
        val corridorDoorBlocks: MutableMap<Int, List<Location>> = mutableMapOf(),
        val mechanicMarkersByWave: MutableMap<Int, MutableList<ArenaMechanicMarker>> = mutableMapOf(),
        var entranceLocation: Location? = null,
        var entranceCheckpoint: Location? = null,
        var goalCheckpoint: Location? = null,
        var barrierLocation: Location? = null,
        var barrierPointLocations: List<Location> = emptyList(),
        var clearingBossLocations: List<Location> = emptyList(),
        var doorAnimationPlacements: Map<Int, List<ArenaDoorAnimationPlacement>> = emptyMap(),
        var placementCursor: Int = 0,
        var roomCursor: Int = 0,
        var corridorCursor: Int = 0,
        var phase: BuildPhase = BuildPhase.PLACE_STRUCTURES,
        var result: ArenaStageBuildResult? = null
    )

    fun build(
        world: World,
        origin: Location,
        theme: ArenaTheme,
        missionTypeId: ArenaMissionType = ArenaMissionType.BARRIER_RESTART,
        waves: Int,
        pedestalRoomProbability: Double = 0.0,
        random: Random = Random.Default
    ): ArenaStageBuildResult {
        val job = createBuildJob(world, origin, theme, missionTypeId, waves, pedestalRoomProbability, random)
        while (job.phase != BuildPhase.COMPLETE) {
            stepBuildJob(job)
        }
        return job.result ?: error("[Arena] ステージ生成結果が未確定です")
    }

    fun buildIncrementally(
        plugin: JavaPlugin,
        world: World,
        origin: Location,
        theme: ArenaTheme,
        missionTypeId: ArenaMissionType = ArenaMissionType.BARRIER_RESTART,
        waves: Int,
        pedestalRoomProbability: Double = 0.0,
        random: Random = Random.Default,
        stepsPerTick: Int = 1,
        onComplete: (Result<ArenaStageBuildResult>) -> Unit
    ): BukkitTask {
        val job = createBuildJob(world, origin, theme, missionTypeId, waves, pedestalRoomProbability, random)
        val safeStepsPerTick = stepsPerTick.coerceAtLeast(1)
        lateinit var task: BukkitTask
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            try {
                var stepsRun = 0
                while (stepsRun < safeStepsPerTick) {
                    if (job.phase == BuildPhase.COMPLETE) break
                    val phaseBefore = job.phase
                    stepBuildJob(job)
                    stepsRun += 1
                    if (
                        phaseBefore == BuildPhase.WAIT_FOR_MARKER_ENTITIES ||
                        job.phase == BuildPhase.WAIT_FOR_MARKER_ENTITIES
                    ) {
                        break
                    }
                }

                if (job.phase == BuildPhase.COMPLETE) {
                    task.cancel()
                    onComplete(Result.success(job.result ?: error("[Arena] ステージ生成結果が未確定です")))
                }
            } catch (throwable: Throwable) {
                task.cancel()
                onComplete(Result.failure(throwable))
            }
        }, 1L, 1L)
        return task
    }

    private fun createBuildJob(
        world: World,
        origin: Location,
        theme: ArenaTheme,
        missionTypeId: ArenaMissionType,
        waves: Int,
        pedestalRoomProbability: Double,
        random: Random
    ): BuildJob {
        val pedestalRoomEnabled = theme.staticStructures[ArenaStructureType.TJUNCTION_ROOM].orEmpty().isNotEmpty() &&
            theme.staticStructures[ArenaStructureType.PEDESTAL_ROOM].orEmpty().isNotEmpty()
        val placements = generateAlternatingPlacements(
            waves,
            random,
            if (pedestalRoomEnabled) pedestalRoomProbability else 0.0
        )
            .sortedBy { it.index }
        return BuildJob(
            world = world,
            origin = origin,
            theme = theme,
            missionTypeId = missionTypeId,
            waves = waves,
            pedestalRoomProbability = pedestalRoomProbability,
            random = random,
            placements = placements
        )
    }

    private fun stepBuildJob(job: BuildJob) {
        when (job.phase) {
            BuildPhase.PLACE_STRUCTURES -> processPlaceStructure(job)
            BuildPhase.WAIT_FOR_MARKER_ENTITIES -> processWaitForMarkerEntities(job)
            BuildPhase.SCAN_ROOMS -> processScanRoom(job)
            BuildPhase.SCAN_CORRIDORS -> processScanCorridor(job)
            BuildPhase.FINALIZE -> processFinalize(job)
            BuildPhase.COMPLETE -> return
        }
    }

    private fun processPlaceStructure(job: BuildJob) {
        val placement = job.placements.getOrNull(job.placementCursor) ?: run {
            val allBounds = job.placementBoundsByIndex.values
            require(allBounds.isNotEmpty()) { "[Arena] ステージ境界を計算できません" }
            job.stageBounds = ArenaBounds(
                minX = allBounds.minOf { it.minX },
                maxX = allBounds.maxOf { it.maxX },
                minY = allBounds.minOf { it.minY },
                maxY = allBounds.maxOf { it.maxY },
                minZ = allBounds.minOf { it.minZ },
                maxZ = allBounds.maxOf { it.maxZ }
            )
            // WorldEdit/FAWE の貼り付け直後は Bukkit の chunk.entities に Marker がまだ見えないことがある。
            job.phase = BuildPhase.WAIT_FOR_MARKER_ENTITIES
            return
        }

        val base = locationForTile(job.origin, placement.point, job.theme.gridPitch)
        val selected = pickStructureVariant(job, placement)
            ?: error("[Arena] 構造テンプレートが見つかりません: type=${placement.structureType}")
        val template = selected.baseTemplate
        val connectionProfile = job.connectionProfileCache.getOrPut(
            ConnectionProfileCacheKey(template.name, placement.transform.rotationQuarter, placement.transform.mirrored)
        ) {
            buildConnectionMarkerProfile(template, placement.transform)
        }

        if (connectionProfile.markerCount == 0) {
            job.validationIssues.add(
                ArenaStageValidationIssue(
                    structureName = template.name,
                    missingMarkers = listOf(CONNECTION_MARKER_LABEL)
                )
            )
        }
        if (connectionProfile.issues.isNotEmpty()) {
            job.validationIssues.add(
                ArenaStageValidationIssue(
                    structureName = template.name,
                    missingMarkers = connectionProfile.issues
                )
            )
        }

        val geometry = if (placement.parentIndex == null) {
            computeFirstPlacementGeometry(base, job.theme.gridPitch, template.size, placement.transform)
        } else {
            val previous = job.placements.firstOrNull { it.index == placement.parentIndex }
                ?: error("[Arena] 接続元配置が見つかりません: index=${placement.parentIndex}")
            val previousBounds = job.placementBoundsByIndex[previous.index]
                ?: error("[Arena] 接続元配置の境界情報が見つかりません: index=${previous.index}")
            val previousTemplateName = job.selectedByPlacementIndex[previous.index]?.baseTemplate?.name
                ?: "unknown"
            val previousConnectionProfile = job.connectionProfilesByPlacementIndex[previous.index]
                ?: error("[Arena] 接続元配置の接続マーカー情報が見つかりません: index=${previous.index}")
            try {
                val connectedGeometry = computeConnectedPlacementGeometry(
                    world = job.world,
                    previousPlacement = previous,
                    currentPlacement = placement,
                    previousBounds = previousBounds,
                    currentSize = template.size,
                    currentTransform = placement.transform,
                    previousTemplateName = previousTemplateName,
                    currentTemplateName = template.name,
                    previousConnectionProfile = previousConnectionProfile,
                    currentConnectionProfile = connectionProfile
                )
                val collidedPlacement = findCollidedPlacementIndex(
                    candidate = connectedGeometry.bounds,
                    existing = job.placementBoundsByIndex,
                    exceptPlacementIndex = previous.index
                )
                if (collidedPlacement != null) {
                    error(
                        "$CONNECTION_MARKER_LABEL(collision collidedWith=$collidedPlacement " +
                            "candidate=${boundsSummary(connectedGeometry.bounds)})"
                    )
                }
                connectedGeometry
            } catch (e: IllegalStateException) {
                val msg = e.message ?: "$CONNECTION_MARKER_LABEL(connection_failed)"
                val structName = if (msg.contains("previous_")) previousTemplateName else template.name
                throw ArenaStageBuildException(
                    listOf(
                        ArenaStageValidationIssue(
                            structureName = structName,
                            missingMarkers = listOf(msg)
                        )
                    )
                )
            }
        }

        placeStructure(template, placement.transform, geometry.origin)
        job.placementBoundsByIndex[placement.index] = geometry.bounds
        job.selectedByPlacementIndex[placement.index] = selected
        job.lastSelectedVariantKeyByType[placement.structureType] = selected.variantKey
        job.placementOriginByIndex[placement.index] = geometry.origin.clone()
        job.connectionProfilesByPlacementIndex[placement.index] = connectionProfile
        job.placementCursor += 1
    }

    private fun processWaitForMarkerEntities(job: BuildJob) {
        job.phase = BuildPhase.SCAN_ROOMS
    }

    private fun processScanRoom(job: BuildJob) {
        val roomPlacements = job.placements.filter { it.role != PlacementRole.CORRIDOR }.sortedBy { it.index }
        val roomPlacement = roomPlacements.getOrNull(job.roomCursor) ?: run {
            job.phase = BuildPhase.SCAN_CORRIDORS
            return
        }

        val bounds = job.placementBoundsByIndex[roomPlacement.index] ?: run {
            job.roomCursor += 1
            return
        }
        val selected = job.selectedByPlacementIndex[roomPlacement.index]
        val markers = collectTemplateMarkers(job.world, selected?.baseTemplate, roomPlacement.transform, bounds)
        val templateName = selected?.baseTemplate?.name ?: "unknown"
        val wave = roomPlacement.wave
        val markerWave = wave ?: 0
        if (markers.mechanics.isNotEmpty()) {
            // Theme mechanics own the meaning of these markers; generation only preserves their room/wave placement.
            job.mechanicMarkersByWave.getOrPut(markerWave) { mutableListOf() }.addAll(markers.mechanics)
        }

        when (roomPlacement.role) {
            PlacementRole.ENTRANCE -> {
                job.roomBounds[0] = bounds
            }
            PlacementRole.COMBAT_ROOM,
            PlacementRole.GOAL_ROOM -> {
                val combatWave = wave ?: error("[Arena] 戦闘部屋の wave が未設定です: index=${roomPlacement.index}")
                job.roomBounds[combatWave] = bounds
                if (markers.mobSpawns.isEmpty()) {
                    job.validationIssues.add(
                        ArenaStageValidationIssue(
                            structureName = templateName,
                            missingMarkers = listOf("arena.marker.mob")
                        )
                    )
                } else {
                    job.roomMobSpawns[combatWave] = markers.mobSpawns
                }
            }
            PlacementRole.TRANSIT_ROOM -> {
                val transitWave = wave ?: error("[Arena] T字路部屋の wave が未設定です: index=${roomPlacement.index}")
                job.transitBounds[transitWave] = bounds
            }
            PlacementRole.PEDESTAL_ROOM -> {
                val pedestalWave = wave ?: error("[Arena] pedestal_room の wave が未設定です: index=${roomPlacement.index}")
                job.pedestalBounds[pedestalWave] = bounds
                job.pedestalMarkerBlocks.addAll(markers.pedestals.map { ArenaBlockKey.from(it) })
            }
            PlacementRole.CORRIDOR -> Unit
        }

        when (roomPlacement.role) {
            PlacementRole.ENTRANCE,
            PlacementRole.COMBAT_ROOM,
            PlacementRole.GOAL_ROOM -> {
                val checkpointReason = validateSingleRequiredMarker(markers.checkpoints, "arena.marker.checkpoint")
                if (checkpointReason != null) {
                    job.validationIssues.add(
                        ArenaStageValidationIssue(
                            structureName = templateName,
                            missingMarkers = listOf(checkpointReason)
                        )
                    )
                } else {
                    val checkpoint = markers.checkpoints.first()
                    when (roomPlacement.role) {
                        PlacementRole.ENTRANCE -> {
                            job.entranceCheckpoint = checkpoint
                        }
                        PlacementRole.COMBAT_ROOM -> {
                            val combatWave = wave ?: error("[Arena] 戦闘部屋の wave が未設定です: index=${roomPlacement.index}")
                            job.roomCheckpoints[combatWave] = checkpoint
                        }
                        PlacementRole.GOAL_ROOM -> {
                            val combatWave = wave ?: error("[Arena] ゴール部屋の wave が未設定です: index=${roomPlacement.index}")
                            job.goalCheckpoint = checkpoint
                            job.roomCheckpoints[combatWave] = checkpoint
                        }
                        else -> {
                            // no-op
                        }
                    }
                }
            }
            else -> {
                // transit/pedestal では checkpoint を必須化しない
            }
        }

        val targetWave = when (roomPlacement.role) {
            PlacementRole.ENTRANCE -> 1
            PlacementRole.COMBAT_ROOM -> wave?.plus(1)
            else -> null
        }

        if (targetWave != null && targetWave in 1..job.waves) {
            val doorBlocks = markers.doorBlocks
            if (doorBlocks.size != 1) {
                val reason = if (doorBlocks.isEmpty()) {
                    "arena.marker.door_block"
                } else {
                    "arena.marker.door_block(single_required)"
                }
                job.validationIssues.add(
                    ArenaStageValidationIssue(
                        structureName = templateName,
                        missingMarkers = listOf(reason)
                    )
                )
            } else {
                job.corridorDoorBlocks[targetWave] = listOf(doorBlocks.first())
            }
        }

        job.roomCursor += 1
    }

    private fun processScanCorridor(job: BuildJob) {
        val corridorPlacements = job.placements.filter { it.role == PlacementRole.CORRIDOR }
        val corridorPlacement = corridorPlacements.getOrNull(job.corridorCursor) ?: run {
            job.phase = BuildPhase.FINALIZE
            return
        }

        val bounds = job.placementBoundsByIndex[corridorPlacement.index]

        val targetWave = corridorPlacement.wave
        if (targetWave != null && targetWave in 1..job.waves) {
            if (bounds != null) {
                job.corridorBounds[targetWave] = bounds
            }
        }
        job.corridorCursor += 1
    }

    private fun processFinalize(job: BuildJob) {
        val roomPlacements = job.placements.filter { it.role != PlacementRole.CORRIDOR }.sortedBy { it.index }
        val finalRoomPlacement = roomPlacements.firstOrNull { it.role == PlacementRole.GOAL_ROOM }
            ?: error("[Arena] 最終部屋が見つかりません")
        val finalRoomBounds = job.placementBoundsByIndex[finalRoomPlacement.index]
            ?: error("[Arena] 最終部屋の境界が見つかりません")
        val selectedFinal = job.selectedByPlacementIndex[finalRoomPlacement.index]
        val finalMarkers = collectTemplateMarkers(job.world, selectedFinal?.baseTemplate, finalRoomPlacement.transform, finalRoomBounds)
        if (finalMarkers.barrierCores.size != 1) {
            val reason = if (finalMarkers.barrierCores.isEmpty()) {
                "arena.marker.barrier_core"
            } else {
                "arena.marker.barrier_core(single_required)"
            }
            job.validationIssues.add(
                ArenaStageValidationIssue(
                    structureName = selectedFinal?.baseTemplate?.name ?: "unknown",
                    missingMarkers = listOf(reason)
                )
            )
        }
        if (finalMarkers.barrierPoints.isEmpty()) {
            job.validationIssues.add(
                ArenaStageValidationIssue(
                    structureName = selectedFinal?.baseTemplate?.name ?: "unknown",
                    missingMarkers = listOf("arena.marker.barrier_point")
                )
            )
        }

        val firstRoomPlacement = roomPlacements.firstOrNull { it.role == PlacementRole.ENTRANCE }
            ?: error("[Arena] 開始部屋が見つかりません")
        if (job.placementBoundsByIndex[firstRoomPlacement.index] == null) {
            error("[Arena] 開始部屋の境界が見つかりません")
        }
        val entranceCheckpoint = job.entranceCheckpoint
        if (entranceCheckpoint == null) {
            job.validationIssues.add(
                ArenaStageValidationIssue(
                    structureName = job.selectedByPlacementIndex[firstRoomPlacement.index]?.baseTemplate?.name ?: "unknown",
                    missingMarkers = listOf("arena.marker.checkpoint")
                )
            )
        }

        val goalCheckpoint = job.goalCheckpoint
        if (job.waves > 0 && goalCheckpoint == null) {
            job.validationIssues.add(
                ArenaStageValidationIssue(
                    structureName = selectedFinal?.baseTemplate?.name ?: "unknown",
                    missingMarkers = listOf("arena.marker.checkpoint")
                )
            )
        }

        job.doorAnimationPlacements = buildDoorAnimationPlacements(
            placements = job.placements,
            waves = job.waves,
            selectedByPlacementIndex = job.selectedByPlacementIndex,
            placementOriginByIndex = job.placementOriginByIndex
        )

        if (job.validationIssues.isNotEmpty()) {
            throw ArenaStageBuildException(job.validationIssues)
        }

        val resolvedEntranceCheckpoint = entranceCheckpoint ?: error("[Arena] 開始部屋の checkpoint マーカーが見つかりません")
        val resolvedEntranceLocation = resolvedEntranceCheckpoint
        val resolvedGoalCheckpoint = goalCheckpoint ?: resolvedEntranceCheckpoint
        val resolvedBarrierLocation = finalMarkers.barrierCores.firstOrNull()
            ?: error("[Arena] 最終部屋の barrier_core マーカーが見つかりません")
        val resolvedBarrierPoints = finalMarkers.barrierPoints
        val resolvedClearingBossLocations = finalMarkers.clearingBosses

        job.entranceLocation = resolvedEntranceLocation
        job.entranceCheckpoint = resolvedEntranceCheckpoint
        job.goalCheckpoint = resolvedGoalCheckpoint
        job.barrierLocation = resolvedBarrierLocation
        job.barrierPointLocations = resolvedBarrierPoints
        job.clearingBossLocations = resolvedClearingBossLocations
        job.result = ArenaStageBuildResult(
            playerSpawn = resolvedEntranceLocation.clone(),
            entranceLocation = resolvedEntranceLocation,
            entranceCheckpoint = resolvedEntranceCheckpoint,
            stageBounds = job.stageBounds ?: error("[Arena] ステージ境界が見つかりません"),
            roomBounds = job.roomBounds,
            corridorBounds = job.corridorBounds,
            transitBounds = job.transitBounds,
            pedestalBounds = job.pedestalBounds,
            pedestalMarkerBlocks = job.pedestalMarkerBlocks,
            roomMobSpawns = job.roomMobSpawns,
            roomCheckpoints = job.roomCheckpoints,
            goalCheckpoint = resolvedGoalCheckpoint,
            corridorDoorBlocks = job.corridorDoorBlocks,
            doorAnimationPlacements = job.doorAnimationPlacements,
            mechanicMarkersByWave = job.mechanicMarkersByWave.mapValues { (_, markers) -> markers.toList() },
            barrierLocation = resolvedBarrierLocation,
            barrierPointLocations = resolvedBarrierPoints,
            clearingBossLocations = resolvedClearingBossLocations
        )
        job.phase = BuildPhase.COMPLETE
    }

    private fun generateAlternatingPlacements(
        waves: Int,
        random: Random,
        pedestalRoomProbability: Double
    ): List<TilePlacement> {
        if (waves <= 0) {
            return listOf(
                TilePlacement(
                    index = 0,
                    point = TilePoint(0, 0),
                    structureType = ArenaStructureType.ENTRANCE,
                    transform = entranceTransform(CardinalDirection.NORTH),
                    role = PlacementRole.ENTRANCE,
                    wave = 0
                )
            )
        }

        val pedestalWaveCandidates = if (waves >= 2 && random.nextDouble() < pedestalRoomProbability) {
            (1 until waves).toList().shuffled(random)
        } else {
            emptyList()
        }

        for (pedestalWave in pedestalWaveCandidates + listOf<Int?>(null)) {
            val placements = generatePlacementsForPedestalWave(waves, random, pedestalWave)
            if (placements != null) {
                return placements
            }
        }

        return buildLinearFallbackPlacements(waves)
    }

    private fun generatePlacementsForPedestalWave(
        waves: Int,
        random: Random,
        pedestalWave: Int?
    ): List<TilePlacement>? {
        repeat(200) {
            val combatRoomPoints = mutableListOf(TilePoint(0, 0))
            val roomDirections = mutableListOf(CardinalDirection.entries.random(random))
            val occupied = mutableSetOf(TilePoint(0, 0))
            val placements = mutableListOf<TilePlacement>()
            var nextIndex = 0

            placements.add(
                TilePlacement(
                    index = nextIndex++,
                    point = TilePoint(0, 0),
                    structureType = ArenaStructureType.ENTRANCE,
                    transform = entranceTransform(roomDirections.first()),
                    role = PlacementRole.ENTRANCE,
                    wave = 0
                )
            )

            var failed = false
            for (wave in 1..waves) {
                val previousRoom = combatRoomPoints.last()
                val startDirection = roomDirections.last()
                val corridorPoint = TilePoint(previousRoom.x + startDirection.dx, previousRoom.z + startDirection.dz)
                if (occupied.contains(corridorPoint)) {
                    failed = true
                    break
                }

                val candidateDirections = listOf(startDirection, startDirection.left(), startDirection.right()).shuffled(random)
                var selectedDirection: CardinalDirection? = null
                var selectedCombatRoomPoint: TilePoint? = null
                var selectedTransitPoint: TilePoint? = null
                var selectedPedestalPoint: TilePoint? = null
                var selectedTransitTransform: PlacementTransform? = null
                var selectedPedestalTransform: PlacementTransform? = null

                for (endDirection in candidateDirections) {
                    if (endDirection != startDirection && !canResolveCornerTransform(startDirection.opposite(), endDirection)) {
                        continue
                    }

                    val candidateCombatRoomPoint: TilePoint
                    if (wave == pedestalWave) {
                        val transitPoint = TilePoint(corridorPoint.x + endDirection.dx, corridorPoint.z + endDirection.dz)
                        val combatRoomPoint = TilePoint(transitPoint.x + endDirection.dx, transitPoint.z + endDirection.dz)
                        if (occupied.contains(transitPoint) || occupied.contains(combatRoomPoint)) {
                            continue
                        }

                        val branchDirections = listOf(endDirection.left(), endDirection.right()).shuffled(random)
                        var resolved = false
                        for (branchDirection in branchDirections) {
                            val pedestalPoint = TilePoint(transitPoint.x + branchDirection.dx, transitPoint.z + branchDirection.dz)
                            if (occupied.contains(pedestalPoint)) {
                                continue
                            }
                            val transitTransform = tjunctionRoomTransform(
                                actualEntry = endDirection.opposite(),
                                actualOuts = setOf(endDirection, branchDirection)
                            ) ?: continue
                            selectedTransitPoint = transitPoint
                            selectedPedestalPoint = pedestalPoint
                            selectedTransitTransform = transitTransform
                            selectedPedestalTransform = pedestalRoomTransform(branchDirection.opposite())
                            resolved = true
                            break
                        }
                        if (!resolved) {
                            continue
                        }
                        candidateCombatRoomPoint = combatRoomPoint
                    } else {
                        val nextRoom = if (endDirection == startDirection) {
                            TilePoint(corridorPoint.x + startDirection.dx, corridorPoint.z + startDirection.dz)
                        } else {
                            TilePoint(corridorPoint.x + endDirection.dx, corridorPoint.z + endDirection.dz)
                        }
                        if (occupied.contains(nextRoom)) {
                            continue
                        }
                        candidateCombatRoomPoint = nextRoom
                    }

                    selectedDirection = endDirection
                    selectedCombatRoomPoint = candidateCombatRoomPoint
                    break
                }

                if (selectedDirection == null || selectedCombatRoomPoint == null) {
                    failed = true
                    break
                }

                val corridorType = if (selectedDirection == startDirection) ArenaStructureType.CORRIDOR else ArenaStructureType.CORNER
                val selectedCorridorTransform = if (selectedDirection == startDirection) {
                    corridorTransform(startDirection.opposite())
                } else {
                    cornerTransform(startDirection.opposite(), selectedDirection)
                }

                val previousPlacementIndex = placements.last().index
                val corridorIndex = nextIndex++
                placements.add(
                    TilePlacement(
                        index = corridorIndex,
                        point = corridorPoint,
                        structureType = corridorType,
                        transform = selectedCorridorTransform,
                        role = PlacementRole.CORRIDOR,
                        wave = wave,
                        parentIndex = previousPlacementIndex
                    )
                )

                var combatParentIndex = corridorIndex
                if (wave == pedestalWave) {
                    if (selectedTransitPoint == null || selectedPedestalPoint == null || selectedTransitTransform == null || selectedPedestalTransform == null) {
                        failed = true
                        break
                    }
                    val transitPoint = selectedTransitPoint
                    val pedestalPoint = selectedPedestalPoint
                    val transitTransform = selectedTransitTransform
                    val pedestalTransform = selectedPedestalTransform

                    val transitIndex = nextIndex++
                    placements.add(
                        TilePlacement(
                            index = transitIndex,
                            point = transitPoint,
                            structureType = ArenaStructureType.TJUNCTION_ROOM,
                            transform = transitTransform,
                            role = PlacementRole.TRANSIT_ROOM,
                            wave = wave,
                            parentIndex = corridorIndex
                        )
                    )
                    placements.add(
                        TilePlacement(
                            index = nextIndex++,
                            point = pedestalPoint,
                            structureType = ArenaStructureType.PEDESTAL_ROOM,
                            transform = pedestalTransform,
                            role = PlacementRole.PEDESTAL_ROOM,
                            wave = wave,
                            parentIndex = transitIndex
                        )
                    )
                    occupied.add(transitPoint)
                    occupied.add(pedestalPoint)
                    combatParentIndex = transitIndex
                }

                placements.add(
                    TilePlacement(
                        index = nextIndex++,
                        point = selectedCombatRoomPoint,
                        structureType = if (wave == waves) ArenaStructureType.GOAL else ArenaStructureType.STRAIGHT,
                        transform = if (wave == waves) {
                            goalTransform(selectedDirection.opposite())
                        } else {
                            straightTransform(selectedDirection.opposite())
                        },
                        role = if (wave == waves) PlacementRole.GOAL_ROOM else PlacementRole.COMBAT_ROOM,
                        wave = wave,
                        parentIndex = combatParentIndex
                    )
                )

                combatRoomPoints.add(selectedCombatRoomPoint)
                roomDirections.add(selectedDirection)
                occupied.add(corridorPoint)
                occupied.add(selectedCombatRoomPoint)
            }

            if (!failed) {
                return placements
            }
        }

        return null
    }

    private fun buildLinearFallbackPlacements(waves: Int): List<TilePlacement> {
        val result = mutableListOf<TilePlacement>()
        var roomPoint = TilePoint(0, 0)
        var index = 0
        result.add(
            TilePlacement(
                index = index,
                point = roomPoint,
                structureType = ArenaStructureType.ENTRANCE,
                transform = entranceTransform(CardinalDirection.EAST),
                role = PlacementRole.ENTRANCE,
                wave = 0
            )
        )
        index += 1

        for (wave in 1..waves) {
            val corridorPoint = TilePoint(roomPoint.x + CardinalDirection.EAST.dx, roomPoint.z + CardinalDirection.EAST.dz)
            result.add(
                TilePlacement(
                    index = index,
                    point = corridorPoint,
                    structureType = ArenaStructureType.CORRIDOR,
                    transform = corridorTransform(CardinalDirection.WEST),
                    role = PlacementRole.CORRIDOR,
                    wave = wave,
                    parentIndex = index - 1
                )
            )
            index += 1

            roomPoint = TilePoint(corridorPoint.x + CardinalDirection.EAST.dx, corridorPoint.z + CardinalDirection.EAST.dz)
            result.add(
                TilePlacement(
                    index = index,
                    point = roomPoint,
                    structureType = if (wave == waves) ArenaStructureType.GOAL else ArenaStructureType.STRAIGHT,
                    transform = if (wave == waves) {
                        goalTransform(CardinalDirection.WEST)
                    } else {
                        straightTransform(CardinalDirection.WEST)
                    },
                    role = if (wave == waves) PlacementRole.GOAL_ROOM else PlacementRole.COMBAT_ROOM,
                    wave = wave,
                    parentIndex = index - 1
                )
            )
            index += 1
        }

        return result
    }

    private fun directionOf(from: TilePoint, to: TilePoint): CardinalDirection {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return CardinalDirection.entries.first { it.dx == dx && it.dz == dz }
    }

    private fun entranceTransform(actualExit: CardinalDirection): PlacementTransform {
        val schema = requireArenaSchema(ArenaStructureType.ENTRANCE)
        val canonicalOut = schema.outSides.firstOrNull()
            ?: error("[Arena] entrance schema outSides is empty")
        return PlacementTransform(StructureTransform.rotationBetween(canonicalOut, actualExit))
    }

    private fun straightTransform(actualEntry: CardinalDirection): PlacementTransform {
        val schema = requireArenaSchema(ArenaStructureType.STRAIGHT)
        val canonicalIn = schema.inSide ?: error("[Arena] straight schema inSide is missing")
        return PlacementTransform(StructureTransform.rotationBetween(canonicalIn, actualEntry))
    }

    private fun corridorTransform(actualEntry: CardinalDirection): PlacementTransform {
        val schema = requireArenaSchema(ArenaStructureType.CORRIDOR)
        val canonicalIn = schema.inSide ?: error("[Arena] corridor schema inSide is missing")
        return PlacementTransform(StructureTransform.rotationBetween(canonicalIn, actualEntry))
    }

    private fun goalTransform(actualEntry: CardinalDirection): PlacementTransform {
        val schema = requireArenaSchema(ArenaStructureType.GOAL)
        val canonicalIn = schema.inSide ?: error("[Arena] goal schema inSide is missing")
        return PlacementTransform(StructureTransform.rotationBetween(canonicalIn, actualEntry))
    }

    private fun pedestalRoomTransform(actualEntry: CardinalDirection): PlacementTransform {
        val schema = requireArenaSchema(ArenaStructureType.PEDESTAL_ROOM)
        val canonicalIn = schema.inSide ?: error("[Arena] pedestal_room schema inSide is missing")
        return PlacementTransform(StructureTransform.rotationBetween(canonicalIn, actualEntry))
    }

    private fun cornerTransform(
        actualEntry: CardinalDirection,
        actualExit: CardinalDirection
    ): PlacementTransform {
        val schema = requireArenaSchema(ArenaStructureType.CORNER)
        val canonicalIn = schema.inSide ?: error("[Arena] corner schema inSide is missing")
        val canonicalOut = schema.outSides.firstOrNull()
            ?: error("[Arena] corner schema outSides is empty")
        val candidates = buildList {
            for (quarter in 0..3) add(PlacementTransform(quarter, mirrored = false))
            for (quarter in 0..3) add(PlacementTransform(quarter, mirrored = true))
        }
        return candidates.firstOrNull { transform ->
            transformDirection(canonicalIn, transform) == actualEntry &&
                transformDirection(canonicalOut, transform) == actualExit
        } ?: error("[Arena] corner 回転が解決できません: entry=${actualEntry.token}, exit=${actualExit.token}")
    }

    private fun canResolveCornerTransform(
        actualEntry: CardinalDirection,
        actualExit: CardinalDirection
    ): Boolean {
        if (actualEntry == actualExit || actualEntry.opposite() == actualExit) {
            return false
        }
        return runCatching { cornerTransform(actualEntry, actualExit) }.isSuccess
    }

    private fun tjunctionRoomTransform(
        actualEntry: CardinalDirection,
        actualOuts: Set<CardinalDirection>
    ): PlacementTransform? {
        val schema = requireArenaSchema(ArenaStructureType.TJUNCTION_ROOM)
        val canonicalIn = schema.inSide ?: error("[Arena] tjunction schema inSide is missing")
        if (schema.outSides.size != actualOuts.size) return null
        val candidates = buildList {
            for (quarter in 0..3) add(PlacementTransform(quarter, mirrored = false))
            for (quarter in 0..3) add(PlacementTransform(quarter, mirrored = true))
        }
        return candidates.firstOrNull { transform ->
            val transformedIn = transformDirection(canonicalIn, transform)
            val transformedOuts = schema.outSides.map { transformDirection(it, transform) }.toSet()
            transformedIn == actualEntry && transformedOuts == actualOuts
        }
    }

    private fun requireArenaSchema(type: ArenaStructureType) =
        StructureSchemas.arena(type.keyword) ?: error("[Arena] structure schema is missing: ${type.keyword}")

    private fun pickStructureVariant(job: BuildJob, placement: TilePlacement): SelectedStructureVariant? {
        val theme = job.theme
        val random = job.random
        val lastVariantKey = job.lastSelectedVariantKeyByType[placement.structureType]

        if (placement.structureType == ArenaStructureType.GOAL) {
            val requiredVariation = job.missionTypeId.id
            val variant = theme.staticStructures[ArenaStructureType.GOAL]
                .orEmpty()
                .firstOrNull { it.variation == requiredVariation }
                ?: error("[Arena] goal.${requiredVariation}.schem が見つかりません: theme=${theme.id}")
            return SelectedStructureVariant(
                baseTemplate = variant.template,
                animatedVariant = null,
                variantKey = variant.variation ?: variant.template.name
            )
        }

        return if (placement.structureType.supportsAnimation) {
            val variants = theme.animatedStructures[placement.structureType].orEmpty()
            val filtered = if (variants.size > 1 && !lastVariantKey.isNullOrBlank()) {
                variants.filter { (it.variation ?: it.closedTemplate.name) != lastVariantKey }
            } else {
                variants
            }
            val candidates = if (filtered.isNotEmpty()) filtered else variants
            candidates.randomOrNull(random)
                ?.let { variant ->
                    SelectedStructureVariant(
                        baseTemplate = variant.closedTemplate,
                        animatedVariant = variant,
                        variantKey = variant.variation ?: variant.closedTemplate.name
                    )
                }
        } else {
            val variants = theme.staticStructures[placement.structureType].orEmpty()
            val filtered = if (variants.size > 1 && !lastVariantKey.isNullOrBlank()) {
                variants.filter { (it.variation ?: it.template.name) != lastVariantKey }
            } else {
                variants
            }
            val candidates = if (filtered.isNotEmpty()) filtered else variants
            candidates.randomOrNull(random)
                ?.let { variant ->
                    SelectedStructureVariant(
                        baseTemplate = variant.template,
                        animatedVariant = null,
                        variantKey = variant.variation ?: variant.template.name
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
        val result = mutableMapOf<Int, List<ArenaDoorAnimationPlacement>>()

        for (targetWave in 1..waves) {
            val animationTargets = mutableListOf<ArenaDoorAnimationPlacement>()
            placements.forEach { placement ->
                if (placement.wave != targetWave) return@forEach
                if (placement.structureType != ArenaStructureType.CORRIDOR && placement.structureType != ArenaStructureType.CORNER) {
                    return@forEach
                }

                val selected = selectedByPlacementIndex[placement.index] ?: return@forEach
                val variant = selected.animatedVariant ?: return@forEach
                val origin = placementOriginByIndex[placement.index] ?: return@forEach

                animationTargets.add(
                    ArenaDoorAnimationPlacement(
                        structureType = placement.structureType,
                        placeOrigin = origin.clone(),
                        rotationQuarter = placement.transform.rotationQuarter,
                        mirrored = placement.transform.mirrored,
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
        transform: PlacementTransform,
        location: Location
    ) {
        template.structure.paste(
            location,
            StructurePasteOptions(
                rotationQuarter = transform.rotationQuarter,
                mirrorX = transform.mirrored,
                pasteAir = true,
                copyEntities = true,
                copyBiomes = true
            )
        )
    }

    private fun computeFirstPlacementGeometry(
        tileBase: Location,
        gridPitch: Int,
        size: ArenaStructureSize,
        transform: PlacementTransform
    ): PlacementGeometry {
        val (widthX, widthZ) = rotatedFootprint(size, transform)
        val offsetX = ((gridPitch - widthX) / 2.0).coerceAtLeast(0.0)
        val offsetZ = ((gridPitch - widthZ) / 2.0).coerceAtLeast(0.0)
        val footprintMin = tileBase.clone().add(offsetX, 0.0, offsetZ)
        // LoadedSchemStructure.paste() 側で回転後boundsのmin補正を行うため、
        // 生成器から渡すoriginは実際に置きたいフットプリント最小座標に揃える。
        val placeOrigin = footprintMin.clone()
        val bounds = placementBounds(footprintMin, widthX, widthZ, size.y)
        return PlacementGeometry(placeOrigin, bounds)
    }

    private fun computeConnectedPlacementGeometry(
        world: World,
        previousPlacement: TilePlacement,
        currentPlacement: TilePlacement,
        previousBounds: ArenaBounds,
        currentSize: ArenaStructureSize,
        currentTransform: PlacementTransform,
        previousTemplateName: String,
        currentTemplateName: String,
        previousConnectionProfile: ConnectionMarkerProfile,
        currentConnectionProfile: ConnectionMarkerProfile
    ): PlacementGeometry {
        val direction = directionOf(previousPlacement.point, currentPlacement.point)
        val (currentWidthX, currentWidthZ) = rotatedFootprint(currentSize, currentTransform)
        val previousSide = direction
        val currentSide = direction.opposite()
        val previousSideUnrotated = toUnrotatedDirection(previousSide, previousPlacement.transform)
        val currentSideUnrotated = toUnrotatedDirection(currentSide, currentTransform)
        val connectionContext =
            "from=${previousTemplateName}(index=${previousPlacement.index},type=${previousPlacement.structureType.keyword}) " +
                "to=${currentTemplateName}(index=${currentPlacement.index},type=${currentPlacement.structureType.keyword}) " +
                "worldDirection=${direction.token} fromWorldSide=${previousSide.token} toWorldSide=${currentSide.token} " +
                "fromUnrotatedSide=${previousSideUnrotated.token} toUnrotatedSide=${currentSideUnrotated.token}"
        val previousDetected = sideDetectedCoordinates(previousConnectionProfile, previousSide)
        val currentDetected = sideDetectedCoordinates(currentConnectionProfile, currentSide)
        val previousMarkers = previousConnectionProfile.sideMarkers[previousSide].orEmpty()
            .filter { it.role == ConnectionRole.OUT }
        val currentMarkers = currentConnectionProfile.sideMarkers[currentSide].orEmpty()
            .filter { it.role == ConnectionRole.IN }

        if (previousMarkers.isEmpty()) {
            error("$CONNECTION_MARKER_LABEL(previous_${previousSideUnrotated.token}_out_missing detected=$previousDetected $connectionContext)")
        }
        if (currentMarkers.isEmpty()) {
            error("$CONNECTION_MARKER_LABEL(current_${currentSideUnrotated.token}_in_missing detected=$currentDetected $connectionContext)")
        }

        if (previousMarkers.size > 1) {
            error(
                "$CONNECTION_MARKER_LABEL(previous_${previousSideUnrotated.token}_out_multiple detected=${previousMarkers.size} points=$previousDetected $connectionContext)"
            )
        }
        if (currentMarkers.size > 1) {
            error(
                "$CONNECTION_MARKER_LABEL(current_${currentSideUnrotated.token}_in_multiple detected=${currentMarkers.size} points=$currentDetected $connectionContext)"
            )
        }

        val previousMarker = previousMarkers.first()
        val currentMarker = currentMarkers.first()
        val targetBlockX = previousBounds.minX + previousMarker.blockX + direction.dx
        val targetBlockY = previousBounds.minY + previousMarker.blockY
        val targetBlockZ = previousBounds.minZ + previousMarker.blockZ + direction.dz
        val minX = targetBlockX - currentMarker.blockX
        val minY = targetBlockY - currentMarker.blockY
        val minZ = targetBlockZ - currentMarker.blockZ
        val previousWorldBlockX = previousBounds.minX + previousMarker.blockX
        val previousWorldBlockY = previousBounds.minY + previousMarker.blockY
        val previousWorldBlockZ = previousBounds.minZ + previousMarker.blockZ
        val currentWorldBlockX = minX + currentMarker.blockX
        val currentWorldBlockY = minY + currentMarker.blockY
        val currentWorldBlockZ = minZ + currentMarker.blockZ

        if (
            currentWorldBlockX != targetBlockX ||
            currentWorldBlockY != targetBlockY ||
            currentWorldBlockZ != targetBlockZ
        ) {
            error(
                "$CONNECTION_MARKER_LABEL(anchor_mismatch expected=(x=$targetBlockX,y=$targetBlockY,z=$targetBlockZ) " +
                    "actual=(x=$currentWorldBlockX,y=$currentWorldBlockY,z=$currentWorldBlockZ) $connectionContext)"
            )
        }

        val expectedPreviousConnectedX = previousWorldBlockX + direction.dx
        val expectedPreviousConnectedY = previousWorldBlockY
        val expectedPreviousConnectedZ = previousWorldBlockZ + direction.dz
        if (
            currentWorldBlockX != expectedPreviousConnectedX ||
            currentWorldBlockY != expectedPreviousConnectedY ||
            currentWorldBlockZ != expectedPreviousConnectedZ
        ) {
            error(
                "$CONNECTION_MARKER_LABEL(adjacent_mismatch previous=(x=$previousWorldBlockX,y=$previousWorldBlockY,z=$previousWorldBlockZ) " +
                    "current=(x=$currentWorldBlockX,y=$currentWorldBlockY,z=$currentWorldBlockZ) direction=${direction.token} $connectionContext)"
            )
        }

        val footprintMin = Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble())
        // paste() が回転後boundsのminを内部補正するため、originにplacementOffsetを二重適用しない。
        val placeOrigin = footprintMin.clone()
        val bounds = placementBounds(footprintMin, currentWidthX, currentWidthZ, currentSize.y)
        return PlacementGeometry(placeOrigin, bounds)
    }

    private fun buildConnectionMarkerProfile(
        template: ArenaStructureTemplate,
        transform: PlacementTransform
    ): ConnectionMarkerProfile {
        val size = template.size
        val (widthX, widthZ) = rotatedFootprint(size, transform)
        val sideMarkers = mutableMapOf(
            CardinalDirection.NORTH to mutableListOf<RotatedConnectionMarker>(),
            CardinalDirection.EAST to mutableListOf<RotatedConnectionMarker>(),
            CardinalDirection.SOUTH to mutableListOf<RotatedConnectionMarker>(),
            CardinalDirection.WEST to mutableListOf<RotatedConnectionMarker>()
        )
        val rawResult = buildRawConnectionMarkers(template)
        val issues = mutableListOf<String>()
        issues += rawResult.issues

        rawResult.markers.forEach { rawMarker ->
            val (rotatedX, rotatedZ) = rotateEntityPoint(
                rawMarker.posX,
                rawMarker.posZ,
                size,
                transform
            )
            val rotatedBlockX = floor(rotatedX).toInt()
            val rotatedBlockZ = floor(rotatedZ).toInt()
            val rotatedSide = transformDirection(rawMarker.side, transform)
            val rotatedMarker = RotatedConnectionMarker(
                blockX = rotatedBlockX,
                blockY = rawMarker.blockY,
                blockZ = rotatedBlockZ,
                role = rawMarker.role
            )
            val sideList = sideMarkers[rotatedSide] ?: return@forEach
            sideList.add(rotatedMarker)
            if (sideList.size > 1) {
                issues.add("$CONNECTION_MARKER_LABEL(multiple_points side=${rotatedSide.token} count=${sideList.size})")
            }
        }

        return ConnectionMarkerProfile(
            sideMarkers = sideMarkers.mapValues { it.value.toList() },
            widthX = widthX,
            widthZ = widthZ,
            markerCount = rawResult.markerCount,
            issues = issues.distinct()
        )
    }

    private data class RawConnectionMarkerResult(
        val markers: List<RawConnectionMarker>,
        val markerCount: Int,
        val issues: List<String>
    )

    private fun buildRawConnectionMarkers(template: ArenaStructureTemplate): RawConnectionMarkerResult {
        val size = template.size
        val sideMarkers = mutableMapOf(
            CardinalDirection.NORTH to mutableListOf<RawConnectionMarker>(),
            CardinalDirection.EAST to mutableListOf<RawConnectionMarker>(),
            CardinalDirection.SOUTH to mutableListOf<RawConnectionMarker>(),
            CardinalDirection.WEST to mutableListOf<RawConnectionMarker>()
        )
        val issues = mutableListOf<String>()
        var markerCount = 0

        template.structure.entities.forEach { entity ->
            if (entity.typeId != MARKER_ENTITY_TYPE_ID) return@forEach
            val hasIn = StructureSchemas.ARENA_CONNECTION_IN_TAG in entity.scoreboardTags
            val hasOut = StructureSchemas.ARENA_CONNECTION_OUT_TAG in entity.scoreboardTags
            if (!hasIn && !hasOut) return@forEach
            markerCount += 1

            val role = when {
                hasIn && !hasOut -> ConnectionRole.IN
                hasOut && !hasIn -> ConnectionRole.OUT
                else -> {
                    issues.add("$CONNECTION_MARKER_LABEL(ambiguous_role x=${floor(entity.x).toInt()} z=${floor(entity.z).toInt()})")
                    return@forEach
                }
            }

            val blockX = floor(entity.x).toInt()
            val blockY = floor(entity.y).toInt()
            val blockZ = floor(entity.z).toInt()
            val touchedSides = mutableListOf<CardinalDirection>()
            if (blockX == 0) touchedSides.add(CardinalDirection.WEST)
            if (blockX == size.x - 1) touchedSides.add(CardinalDirection.EAST)
            if (blockZ == 0) touchedSides.add(CardinalDirection.NORTH)
            if (blockZ == size.z - 1) touchedSides.add(CardinalDirection.SOUTH)

            if (touchedSides.isEmpty()) {
                issues.add("$CONNECTION_MARKER_LABEL(non_boundary x=$blockX z=$blockZ)")
                return@forEach
            }
            if (touchedSides.size != 1) {
                issues.add(
                    "$CONNECTION_MARKER_LABEL(ambiguous_boundary sides=${touchedSides.joinToString { it.token }} x=$blockX z=$blockZ)"
                )
                return@forEach
            }

            val marker = RawConnectionMarker(
                side = touchedSides.first(),
                role = role,
                blockX = blockX,
                blockY = blockY,
                blockZ = blockZ,
                posX = entity.x,
                posZ = entity.z
            )
            val sideList = sideMarkers[marker.side] ?: return@forEach
            sideList.add(marker)
            if (sideList.size > 1) {
                issues.add("$CONNECTION_MARKER_LABEL(multiple_points side=${marker.side.token} count=${sideList.size})")
            }
        }

        return RawConnectionMarkerResult(
            markers = sideMarkers.values.flatten(),
            markerCount = markerCount,
            issues = issues.distinct()
        )
    }

    private fun rotatePoint(
        x: Double,
        z: Double,
        size: ArenaStructureSize,
        transform: PlacementTransform
    ): Pair<Double, Double> {
        val (rawX, rawZ) = transformRawPoint(x, z, transform)
        val (offsetX, offsetZ) = placementOffset(size, transform)
        return rawX + offsetX to rawZ + offsetZ
    }

    private fun rotateEntityPoint(
        x: Double,
        z: Double,
        size: ArenaStructureSize,
        transform: PlacementTransform
    ): Pair<Double, Double> {
        val (rawX, rawZ) = transformRawEntityPoint(x, z, transform)
        val (offsetX, offsetZ) = placementOffset(size, transform)
        return rawX + offsetX to rawZ + offsetZ
    }

    private fun transformRawPoint(
        x: Double,
        z: Double,
        transform: PlacementTransform
    ): Pair<Double, Double> {
        val point = structureTransform(transform).applyRawPoint(x, z)
        return point.x to point.z
    }

    private fun transformRawEntityPoint(
        x: Double,
        z: Double,
        transform: PlacementTransform
    ): Pair<Double, Double> {
        val point = structureTransform(transform).applyRawPoint(x, z)
        return point.x to point.z
    }

    private fun transformDirection(direction: CardinalDirection, transform: PlacementTransform): CardinalDirection {
        return structureTransform(transform).applyDirection(direction)
    }

    private fun toUnrotatedDirection(direction: CardinalDirection, transform: PlacementTransform): CardinalDirection {
        return structureTransform(transform).inverseDirection(direction)
    }

    private fun structureTransform(transform: PlacementTransform): StructureTransform {
        return StructureTransform(transform.rotationQuarter, mirrorX = transform.mirrored)
    }

    private fun sideDetectedCoordinates(profile: ConnectionMarkerProfile, side: CardinalDirection): String {
        val markers = profile.sideMarkers[side].orEmpty()
            .sortedWith(compareBy<RotatedConnectionMarker> { it.blockY }.thenBy { it.blockX }.thenBy { it.blockZ })
        if (markers.isEmpty()) return "[]"
        return markers.joinToString(prefix = "[", postfix = "]") { marker ->
            "(x=${marker.blockX},y=${marker.blockY},z=${marker.blockZ})"
        }
    }

    private fun findCollidedPlacementIndex(
        candidate: ArenaBounds,
        existing: Map<Int, ArenaBounds>,
        exceptPlacementIndex: Int
    ): Int? {
        return existing.entries.firstOrNull { (placementIndex, bounds) ->
            placementIndex != exceptPlacementIndex && boundsOverlap(candidate, bounds)
        }?.key
    }

    private fun boundsOverlap(a: ArenaBounds, b: ArenaBounds): Boolean {
        return a.minX <= b.maxX && a.maxX >= b.minX &&
            a.minY <= b.maxY && a.maxY >= b.minY &&
            a.minZ <= b.maxZ && a.maxZ >= b.minZ
    }

    private fun boundsSummary(bounds: ArenaBounds): String {
        return "(min=${bounds.minX},${bounds.minY},${bounds.minZ} max=${bounds.maxX},${bounds.maxY},${bounds.maxZ})"
    }

    private fun placementOffset(size: ArenaStructureSize, transform: PlacementTransform): Pair<Double, Double> {
        val bounds = structureTransform(transform).bounds(size.x, size.z)
        return -bounds.minX.toDouble() to -bounds.minZ.toDouble()
    }

    private fun rotatedFootprint(size: ArenaStructureSize, transform: PlacementTransform): Pair<Int, Int> {
        val bounds = structureTransform(transform).bounds(size.x, size.z)
        return bounds.width to bounds.depth
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
        val checkpoints: List<Location>,
        val doorBlocks: List<Location>,
        val barrierCores: List<Location>,
        val barrierPoints: List<Location>,
        val clearingBosses: List<Location> = emptyList(),
        val pedestals: List<Location> = emptyList(),
        val mechanics: List<ArenaMechanicMarker> = emptyList()
    )

    private fun validateSingleRequiredMarker(markers: List<Location>, markerTag: String): String? {
        if (markers.size == 1) return null
        return if (markers.isEmpty()) {
            markerTag
        } else {
            "$markerTag(single_required)"
        }
    }

    private fun collectTemplateMarkers(
        world: World,
        template: ArenaStructureTemplate?,
        transform: PlacementTransform,
        bounds: ArenaBounds
    ): TileMarkers {
        val mobs = mutableListOf<Location>()
        val checkpoints = mutableListOf<Location>()
        val doorBlocks = mutableListOf<Location>()
        val barrierCores = mutableListOf<Location>()
        val barrierPoints = mutableListOf<Location>()
        val clearingBosses = mutableListOf<Location>()
        val pedestals = mutableListOf<Location>()
        val mechanics = mutableListOf<ArenaMechanicMarker>()

        if (template == null) {
            return TileMarkers(mobs, checkpoints, doorBlocks, barrierCores, barrierPoints, clearingBosses, pedestals, mechanics)
        }

        template.structure.entities.forEach { entity ->
            if (entity.typeId != MARKER_ENTITY_TYPE_ID) return@forEach

            val (rotatedX, rotatedZ) = rotateEntityPoint(entity.x, entity.z, template.size, transform)
            // 生成直後の同一tickではWorldEditが貼ったMarker実体がまだ列挙できない場合があるため、
            // 構造ファイルのMarker座標を配置boundsへ写像して、生成ロジックの判定ソースをschemへ寄せる。
            val loc = Location(
                world,
                bounds.minX + rotatedX,
                bounds.minY + entity.y,
                bounds.minZ + rotatedZ
            )

            if (entity.scoreboardTags.contains("arena.marker.mob")) {
                mobs.add(loc.clone())
            }
            if (entity.scoreboardTags.contains("arena.marker.checkpoint")) {
                checkpoints.add(loc.clone())
            }
            if (entity.scoreboardTags.contains("arena.marker.door_block")) {
                doorBlocks.add(loc.clone())
            }
            if (entity.scoreboardTags.contains("arena.marker.barrier_core")) {
                barrierCores.add(loc.clone())
            }
            if (entity.scoreboardTags.contains("arena.marker.barrier_point")) {
                barrierPoints.add(loc.clone())
            }
            if (entity.scoreboardTags.contains("arena.marker.clearing_boss")) {
                clearingBosses.add(loc.clone())
            }
            if (entity.scoreboardTags.contains("arena.marker.pedestal")) {
                pedestals.add(loc.clone())
            }
            entity.scoreboardTags
                .asSequence()
                .filter { it.startsWith(ARENA_MECHANIC_MARKER_PREFIX) }
                .forEach { tag ->
                    mechanics.add(ArenaMechanicMarker(tag, loc.clone()))
                }
        }

        return TileMarkers(mobs, checkpoints, doorBlocks, barrierCores, barrierPoints, clearingBosses, pedestals, mechanics)
    }

    private fun locationForTile(origin: Location, point: TilePoint, gridPitch: Int): Location {
        return origin.clone().add((point.x * gridPitch).toDouble(), 0.0, (point.z * gridPitch).toDouble())
    }
}
