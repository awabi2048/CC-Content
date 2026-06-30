package jp.awabi2048.cccontent.features.arena.mechanic.nether

import jp.awabi2048.cccontent.features.arena.ArenaBounds
import jp.awabi2048.cccontent.features.arena.ArenaActionMarker
import jp.awabi2048.cccontent.features.arena.ArenaActionMarkerState
import jp.awabi2048.cccontent.features.arena.ArenaActionMarkerType
import jp.awabi2048.cccontent.features.arena.mechanic.ArenaMechanicBarrierGateResult
import jp.awabi2048.cccontent.features.arena.mechanic.ArenaMechanicContext
import jp.awabi2048.cccontent.features.arena.mechanic.ArenaMechanicObjectiveProgress
import jp.awabi2048.cccontent.features.arena.mechanic.ArenaMechanicSupport
import jp.awabi2048.cccontent.features.arena.mechanic.ArenaThemeMechanic
import jp.awabi2048.cccontent.structure.CardinalDirection
import jp.awabi2048.cccontent.structure.LoadedSchemStructure
import jp.awabi2048.cccontent.structure.SchemStructureService
import jp.awabi2048.cccontent.structure.StructurePasteOptions
import jp.awabi2048.cccontent.structure.StructureTransform
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class NetherArenaMechanic(private val plugin: JavaPlugin) : ArenaThemeMechanic {
    private val trackPathsByWave = mutableMapOf<Int, MutableList<TrackPath>>()
    private val ventsByWave = mutableMapOf<Int, MutableList<MagmaVent>>()
    private val hitCooldownUntilTick = mutableMapOf<UUID, Long>()
    private var cartTemplate: LoadedSchemStructure? = null
    private var cartTemplateLoadAttempted = false
    private var goalState: NetherGoalState? = null
    private val structureService = SchemStructureService(plugin)
    private val loadedStructures = mutableMapOf<String, LoadedSchemStructure?>()

    override fun onStageReady(context: ArenaMechanicContext) {
        trackPathsByWave.clear()
        ventsByWave.clear()
        hitCooldownUntilTick.clear()
        goalState = null

        for (wave in 1..context.session.waves) {
            registerTrackPath(context, wave, LEFT_TRACK_TAG, LEFT_CART_REFERENCE_TAG, "left")
            registerTrackPath(context, wave, RIGHT_TRACK_TAG, RIGHT_CART_REFERENCE_TAG, "right")

            val ventMarkers = context.markersForWave(wave, MAGMA_VENT_TAG)
            val selectedVentMarkers = ventMarkers
                .shuffled()
                .take((ventMarkers.size / MAGMA_VENT_COUNT_DIVISOR).coerceAtLeast(if (ventMarkers.isEmpty()) 0 else 1))
            val vents = selectedVentMarkers
                .map { MagmaVent(wave = wave, location = it.clone()) }
                .toMutableList()
            if (vents.isNotEmpty()) {
                ventsByWave[wave] = vents
                vents.forEach { context.addCleanupBounds(ArenaMechanicSupport.boundsAround(it.location, 0, 0, 0)) }
            }
        }
        prepareGoalObjective(context)

        val pathCount = trackPathsByWave.values.sumOf { it.size }
        val ventCount = ventsByWave.values.sumOf { it.size }
        val goal = goalState
        if (pathCount > 0 || ventCount > 0 || goal != null) {
            plugin.logger.info("[Arena] Nether mechanic prepared: world=${context.session.worldName} tracks=$pathCount vents=$ventCount goalObjective=${goal != null}")
        }
    }

    override fun onActionMarkersInitialized(context: ArenaMechanicContext) {
        val state = goalState ?: return
        val world = context.world ?: return
        val currentTick = Bukkit.getCurrentTick().toLong()

        state.engineMarkers.forEach { location ->
            val marker = ArenaActionMarker(
                id = UUID.randomUUID(),
                type = ArenaActionMarkerType.NETHER_ENGINE_START,
                center = actionMarkerCenter(location, world),
                holdTicksRequired = NETHER_ACTION_MARKER_HOLD_TICKS,
                renderWhenRunning = false,
                stateColors = mapOf(
                    ArenaActionMarkerState.PRE_ACTIVATED to Color.fromRGB(255, 64, 64),
                    ArenaActionMarkerState.READY to Color.fromRGB(255, 128, 64),
                    ArenaActionMarkerState.RUNNING to Color.fromRGB(96, 255, 128)
                ),
                state = ArenaActionMarkerState.READY
            )
            setMarkerState(marker, ArenaActionMarkerState.READY, currentTick)
            context.session.actionMarkers[marker.id] = marker
            state.engineMarkerIds += marker.id
        }

        state.coreActivationMarkers.forEach { location ->
            val coreSide = nearestCoreSide(state, location)
            val marker = ArenaActionMarker(
                id = UUID.randomUUID(),
                type = ArenaActionMarkerType.NETHER_CORE_ACTIVATE,
                center = actionMarkerCenter(location, world),
                holdTicksRequired = NETHER_ACTION_MARKER_HOLD_TICKS,
                renderWhenRunning = false,
                stateColors = mapOf(
                    ArenaActionMarkerState.PRE_ACTIVATED to Color.fromRGB(255, 64, 64),
                    ArenaActionMarkerState.READY to Color.fromRGB(255, 255, 255),
                    ArenaActionMarkerState.RUNNING to Color.fromRGB(128, 220, 255)
                ),
                state = ArenaActionMarkerState.PRE_ACTIVATED
            )
            setMarkerState(marker, ArenaActionMarkerState.PRE_ACTIVATED, currentTick)
            context.session.actionMarkers[marker.id] = marker
            state.coreMarkerIds[coreSide]?.add(marker.id)
        }
    }

    override fun onTick(context: ArenaMechanicContext, currentTick: Long) {
        val world = context.world ?: return
        val activeWaves = ArenaMechanicSupport.activeWaves(context)

        for ((wave, paths) in trackPathsByWave) {
            val active = wave in activeWaves
            paths.forEach { path ->
                if (active) {
                    path.tick(context, currentTick)
                } else {
                    path.clear()
                }
            }
        }

        for ((wave, vents) in ventsByWave) {
            val active = wave in activeWaves
            vents.forEach { vent ->
                if (active && vent.location.world?.uid == world.uid) {
                    vent.tick(context, currentTick)
                } else {
                    vent.restore()
                }
            }
        }

        hitCooldownUntilTick.entries.removeIf { it.value <= currentTick }
        tickGoalObjective(context, currentTick)
    }

    override fun onActionMarkerTriggered(context: ArenaMechanicContext, marker: ArenaActionMarker): Boolean {
        val state = goalState ?: return false
        return when (marker.type) {
            ArenaActionMarkerType.NETHER_ENGINE_START -> {
                if (!state.engineMarkerIds.contains(marker.id)) return false
                onEngineMarkerActivated(context, state)
                true
            }
            ArenaActionMarkerType.NETHER_CORE_ACTIVATE -> {
                val side = state.coreMarkerIds.entries.firstOrNull { (_, ids) -> marker.id in ids }?.key ?: return false
                state.coreActivationMarkerIds += marker.id
                state.cores.getValue(side).activationMarkersArmed = true
                onCoreActivationMarkerActivated(context, state)
                true
            }
            else -> false
        }
    }

    override fun onMobKilled(context: ArenaMechanicContext, mob: LivingEntity) {
        val state = goalState ?: return
        if (context.session.currentWave != context.session.waves) return
        if (!state.engineStarted || !state.allCoreMarkersActivated) return
        if (state.allCoresCompleted) return

        val location = mob.location
        val activeCore = state.cores.values.firstOrNull { core ->
            !core.completed &&
                core.mode == state.areaMode &&
                isInsideCoreCountArea(core.center, location)
        } ?: return

        activeCore.killCount += 1
        renderCoreKillParticle(location.world ?: return, activeCore.center)
        if (activeCore.killCount >= CORE_REQUIRED_KILLS) {
            completeCore(context, state, activeCore)
        }
    }

    override fun hasCustomBarrierRestartObjective(context: ArenaMechanicContext): Boolean {
        return goalState != null
    }

    override fun barrierRestartGate(context: ArenaMechanicContext): ArenaMechanicBarrierGateResult {
        val state = goalState ?: return ArenaMechanicBarrierGateResult.allowed()
        if (state.allCoresCompleted) return ArenaMechanicBarrierGateResult.allowed()
        return ArenaMechanicBarrierGateResult.blocked("nether cores are not completed")
    }

    override fun barrierRestartProgress(context: ArenaMechanicContext): ArenaMechanicObjectiveProgress? {
        val state = goalState ?: return null
        val total = (state.engineMarkerIds.size + state.coreActivationMarkerIdsRequired + CORE_REQUIRED_KILLS * 2)
            .coerceAtLeast(1)
        val done = state.activatedEngineMarkerCount(context) +
            state.coreActivationMarkerIds.size.coerceAtMost(state.coreActivationMarkerIdsRequired) +
            state.cores.values.sumOf { min(it.killCount, CORE_REQUIRED_KILLS) }
        val progress = (done.toDouble() / total.toDouble()).toFloat().coerceIn(0.0f, 1.0f)
        val label = when {
            !state.engineStarted -> "§6ネザー機関起動: エンジン ${state.activatedEngineMarkerCount(context)}/${state.engineMarkerIds.size.coerceAtLeast(1)}"
            !state.allCoreMarkersActivated -> "§fネザー機関起動: コア起動 ${state.coreActivationMarkerIds.size}/${state.coreActivationMarkerIdsRequired.coerceAtLeast(1)}"
            else -> "§bネザー機関起動: 左 ${state.cores.getValue(CoreSide.LEFT).killCount}/${CORE_REQUIRED_KILLS} 右 ${state.cores.getValue(CoreSide.RIGHT).killCount}/${CORE_REQUIRED_KILLS}"
        }
        return ArenaMechanicObjectiveProgress(label, progress, BossBar.Color.YELLOW)
    }

    override fun onWaveCleared(context: ArenaMechanicContext, wave: Int) {
        trackPathsByWave[wave]?.forEach { it.clear() }
        ventsByWave[wave]?.forEach { it.restore() }
    }

    override fun onSessionEnded(context: ArenaMechanicContext, success: Boolean) {
        dispose()
    }

    override fun dispose() {
        trackPathsByWave.values.flatten().forEach { it.clear() }
        ventsByWave.values.flatten().forEach { it.restore() }
        trackPathsByWave.clear()
        ventsByWave.clear()
        hitCooldownUntilTick.clear()
        goalState = null
    }

    private fun prepareGoalObjective(context: ArenaMechanicContext) {
        val finalWave = context.session.waves
        val engineMarkers = context.markersForWave(finalWave, ENGINE_START_TAG)
        val leftCore = context.markersForWave(finalWave, CORE_CENTER_LEFT_TAG).firstOrNull()
        val rightCore = context.markersForWave(finalWave, CORE_CENTER_RIGHT_TAG).firstOrNull()
        val coreActivationMarkers = context.markersForWave(finalWave, CORE_ACTIVATE_TAG)
        val goalBounds = context.session.roomBounds[finalWave]

        if (engineMarkers.isEmpty() && leftCore == null && rightCore == null && coreActivationMarkers.isEmpty()) {
            return
        }
        if (engineMarkers.isEmpty() || leftCore == null || rightCore == null || coreActivationMarkers.isEmpty() || goalBounds == null) {
            plugin.logger.warning(
                "[Arena] Nether goal objective markers are incomplete: world=${context.session.worldName} " +
                    "engines=${engineMarkers.size} left=${leftCore != null} right=${rightCore != null} coreActivators=${coreActivationMarkers.size} goalBounds=${goalBounds != null}"
            )
            return
        }

        val initialAreaMode = if (Random.nextBoolean()) NetherAreaMode.BLAZING else NetherAreaMode.SOUL
        val leftMode = if (Random.nextBoolean()) NetherAreaMode.BLAZING else NetherAreaMode.SOUL
        val rightMode = leftMode.opposite()
        goalState = NetherGoalState(
            areaMode = initialAreaMode,
            goalBounds = goalBounds,
            engineMarkers = engineMarkers.map { it.clone() },
            coreActivationMarkers = coreActivationMarkers.map { it.clone() },
            cores = mutableMapOf(
                CoreSide.LEFT to NetherCoreState(CoreSide.LEFT, leftCore.clone(), leftMode),
                CoreSide.RIGHT to NetherCoreState(CoreSide.RIGHT, rightCore.clone(), rightMode)
            )
        )
        pasteGoalArea(context, goalState ?: return)
    }

    private fun tickGoalObjective(context: ArenaMechanicContext, currentTick: Long) {
        val state = goalState ?: return
        if (state.engineStarted && currentTick >= state.nextModeSwitchTick) {
            switchGoalMode(context, state, currentTick)
        }
        if (currentTick >= state.nextCoreParticleTick) {
            state.nextCoreParticleTick = currentTick + CORE_PARTICLE_INTERVAL_TICKS
            renderCoreParticles(context, state)
        }
    }

    private fun onEngineMarkerActivated(context: ArenaMechanicContext, state: NetherGoalState) {
        if (state.engineStarted) return
        if (state.activatedEngineMarkerCount(context) < state.engineMarkerIds.size.coerceAtLeast(1)) return

        state.engineStarted = true
        val currentTick = Bukkit.getCurrentTick().toLong()
        state.nextModeSwitchTick = currentTick + randomModeSwitchDelayTicks()
        state.coreMarkerIds.values.flatten()
            .mapNotNull { context.session.actionMarkers[it] }
            .forEach { setMarkerState(it, ArenaActionMarkerState.READY, currentTick) }
        switchGoalMode(context, state, currentTick)
        playParticipantsSound(context, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.7f)
    }

    private fun onCoreActivationMarkerActivated(context: ArenaMechanicContext, state: NetherGoalState) {
        if (state.coreActivationMarkerIds.size < state.coreActivationMarkerIdsRequired) return
        state.allCoreMarkersActivated = true
        playParticipantsSound(context, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f)
        renderCoreParticles(context, state)
    }

    private fun completeCore(context: ArenaMechanicContext, state: NetherGoalState, core: NetherCoreState) {
        if (core.completed) return
        core.completed = true
        val world = context.world ?: return
        world.playSound(core.center, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.1f)
        drawCoreParticleEllipse(world, core.center, Particle.DUST, Color.fromRGB(96, 160, 255), CORE_PARTICLES_ON_COMPLETE)
        if (state.allCoresCompleted) {
            context.requestBarrierRestart()
        }
    }

    private fun switchGoalMode(context: ArenaMechanicContext, state: NetherGoalState, currentTick: Long) {
        punishIncompleteCoresOnModeSwitch(context, state)
        state.areaMode = state.areaMode.opposite()
        state.cores.values.forEach { core -> core.mode = core.mode.opposite() }
        state.nextModeSwitchTick = currentTick + randomModeSwitchDelayTicks()
        pasteGoalArea(context, state)
        playParticipantsSound(context, if (state.areaMode == NetherAreaMode.BLAZING) Sound.ITEM_FIRECHARGE_USE else Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 0.8f)
    }

    private fun punishIncompleteCoresOnModeSwitch(context: ArenaMechanicContext, state: NetherGoalState) {
        val world = context.world ?: return
        state.cores.values
            .filter { !it.completed && it.killCount > 0 }
            .forEach { core ->
                world.spawnParticle(Particle.EXPLOSION, core.center, 1, 0.0, 0.0, 0.0, 0.0)
                world.playSound(core.center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f)
                drawCoreParticleEllipse(world, core.center, Particle.FLAME, null, CORE_PARTICLES_ON_FAILURE, outwardFlame = true)
                core.killCount = 0
            }
    }

    private fun pasteGoalArea(context: ArenaMechanicContext, state: NetherGoalState) {
        val world = context.world ?: return
        val areaTemplate = loadStructure(
            if (state.areaMode == NetherAreaMode.BLAZING) GOAL_AREA_BLAZING_PATH else GOAL_AREA_SOUL_PATH
        )
        if (areaTemplate != null) {
            areaTemplate.paste(
                Location(world, state.goalBounds.minX.toDouble(), state.goalBounds.minY.toDouble(), state.goalBounds.minZ.toDouble()),
                StructurePasteOptions(pasteAir = true, copyEntities = false, copyBiomes = true)
            )
        }
        state.cores.values.forEach { pasteCoreStructure(world, it) }
    }

    private fun pasteCoreStructure(world: World, core: NetherCoreState) {
        val template = loadStructure(if (core.mode == NetherAreaMode.BLAZING) CORE_BLAZING_PATH else CORE_SOUL_PATH) ?: return
        val origin = core.center.clone().apply {
            this.world = world
            x = blockX - (template.size.x / 2).toDouble()
            y = blockY - (template.size.y / 2).toDouble()
            z = blockZ - (template.size.z / 2).toDouble()
        }
        template.paste(origin, StructurePasteOptions(pasteAir = true, copyEntities = false, copyBiomes = true))
    }

    private fun renderCoreParticles(context: ArenaMechanicContext, state: NetherGoalState) {
        val world = context.world ?: return
        state.cores.values.forEach { core ->
            val color = when {
                core.completed -> Color.fromRGB(96, 160, 255)
                !state.allCoreMarkersActivated -> Color.fromRGB(255, 64, 64)
                core.mode != state.areaMode -> Color.fromRGB(120, 120, 120)
                else -> Color.fromRGB(255, 255, 255)
            }
            drawCoreParticleEllipse(world, core.center, Particle.DUST, color, CORE_PARTICLES_IDLE)
        }
    }

    private fun renderCoreKillParticle(world: World, center: Location) {
        drawCoreParticleEllipse(world, center, Particle.DUST, Color.fromRGB(96, 255, 128), CORE_PARTICLES_ON_KILL)
    }

    private fun drawCoreParticleEllipse(
        world: World,
        center: Location,
        particle: Particle,
        color: Color?,
        count: Int,
        outwardFlame: Boolean = false
    ) {
        val normal = randomUnitVector()
        val basisA = perpendicularUnit(normal)
        val basisB = normal.clone().crossProduct(basisA).normalize()
        val dust = color?.let { Particle.DustOptions(it, 1.0f) }

        repeat(count) {
            val angle = Random.nextDouble(0.0, Math.PI * 2.0)
            val direction = basisA.clone().multiply(cos(angle)).add(basisB.clone().multiply(sin(angle))).normalize()
            val radius = ellipsoidSurfaceRadius(direction)
            val point = center.clone().add(direction.clone().multiply(radius))
            if (dust != null) {
                world.spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, dust)
            } else if (outwardFlame) {
                world.spawnParticle(particle, point, 1, direction.x * 0.08, direction.y * 0.08, direction.z * 0.08, 0.08)
            } else {
                world.spawnParticle(particle, point, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
    }

    private fun isInsideCoreCountArea(center: Location, location: Location): Boolean {
        if (center.world?.uid != location.world?.uid) return false
        val dx = location.x - center.x
        val dy = location.y - center.y
        val dz = location.z - center.z
        if (abs(dy) <= CORE_VERTICAL_CUTOUT_HALF_HEIGHT) return false
        return (dx * dx + dz * dz) / (CORE_HORIZONTAL_RADIUS * CORE_HORIZONTAL_RADIUS) +
            (dy * dy) / (CORE_VERTICAL_RADIUS * CORE_VERTICAL_RADIUS) <= 1.0
    }

    private fun actionMarkerCenter(location: Location, world: World): Location {
        return location.clone().apply {
            this.world = world
            y += ACTION_MARKER_CENTER_Y_OFFSET
        }
    }

    private fun nearestCoreSide(state: NetherGoalState, location: Location): CoreSide {
        return state.cores.values.minBy { core -> core.center.distanceSquared(location) }.side
    }

    private fun setMarkerState(marker: ArenaActionMarker, state: ArenaActionMarkerState, currentTick: Long) {
        marker.state = state
        val color = marker.colorFor(state)
        marker.colorTransitionFrom = color
        marker.colorTransitionTo = color
        marker.colorTransitionStartTick = currentTick
    }

    private fun loadStructure(path: String): LoadedSchemStructure? {
        return loadedStructures.getOrPut(path) { structureService.load(path) }
    }

    private fun randomModeSwitchDelayTicks(): Long {
        return Random.nextLong(MODE_SWITCH_MIN_TICKS, MODE_SWITCH_MAX_TICKS + 1)
    }

    private fun playParticipantsSound(context: ArenaMechanicContext, sound: Sound, volume: Float, pitch: Float) {
        context.participantPlayers().forEach { player ->
            player.playSound(player.location, sound, volume, pitch)
        }
    }

    private fun ellipsoidSurfaceRadius(direction: Vector): Double {
        val x = direction.x / CORE_HORIZONTAL_RADIUS
        val y = direction.y / CORE_VERTICAL_RADIUS
        val z = direction.z / CORE_HORIZONTAL_RADIUS
        return 1.0 / sqrt(x * x + y * y + z * z).coerceAtLeast(0.0001)
    }

    private fun randomUnitVector(): Vector {
        val y = Random.nextDouble(-1.0, 1.0)
        val theta = Random.nextDouble(0.0, Math.PI * 2.0)
        val r = sqrt((1.0 - y * y).coerceAtLeast(0.0))
        return Vector(r * cos(theta), y, r * sin(theta)).normalize()
    }

    private fun perpendicularUnit(vector: Vector): Vector {
        val base = if (abs(vector.x) < 0.8) Vector(1.0, 0.0, 0.0) else Vector(0.0, 0.0, 1.0)
        return vector.clone().crossProduct(base).normalize()
    }

    private fun registerTrackPath(context: ArenaMechanicContext, wave: Int, tag: String, referenceTag: String, laneName: String) {
        val markers = context.markersForWave(wave, tag)
        if (markers.isEmpty()) return
        if (markers.size != 2) {
            plugin.logger.warning("[Arena] Nether track path requires exactly 2 markers: world=${context.session.worldName} wave=$wave lane=$laneName count=${markers.size}")
            return
        }
        val first = markers[0]
        val second = markers[1]
        val points = buildStraightPath(first, second)
        if (points.isEmpty()) {
            plugin.logger.warning("[Arena] Nether track path must be straight on X or Z: world=${context.session.worldName} wave=$wave lane=$laneName")
            return
        }

        val template = loadCartTemplate()
        if (template == null) {
            plugin.logger.warning("[Arena] Nether cart template is missing: $CART_STRUCTURE_PATH")
            return
        }

        val axis = if (first.blockX != second.blockX) TrackAxis.X else TrackAxis.Z
        val footprint = StructureTransform(axis.cartRotationQuarter).bounds(template.size.x, template.size.z)
        val references = context.markersForWave(wave)
            .filter { it.tag == referenceTag }
        if (references.size > 1) {
            plugin.logger.warning("[Arena] Nether cart reference marker should be one per lane: world=${context.session.worldName} wave=$wave lane=$laneName count=${references.size}")
        }
        val reference = references.firstOrNull()
        val extendedPoints = extendTrackPoints(points, axis, max(footprint.width, footprint.depth))
        val startIndex = reference?.location?.let { nearestPointIndex(extendedPoints, it) } ?: 0
        val referenceYaw = reference?.facingYaw ?: axis.defaultCartYaw
        val direction = if (Random.nextBoolean()) 1 else -1
        val interval = Random.nextLong(
            MIN_CART_INTERVAL_TICKS,
            MAX_CART_INTERVAL_TICKS + 1
        )
        val path = TrackPath(
            wave = wave,
            laneName = laneName,
            points = extendedPoints,
            axis = axis,
            template = template,
            referenceYaw = referenceYaw,
            vehicles = mutableListOf(
                CartVehicle(index = startIndex, direction = direction, moveIntervalTicks = interval),
                CartVehicle(index = (startIndex + extendedPoints.size / 2).floorMod(extendedPoints.size), direction = direction, moveIntervalTicks = interval)
            )
        )
        trackPathsByWave.getOrPut(wave) { mutableListOf() } += path
        context.addCleanupBounds(ArenaMechanicSupport.boundsFromCorners(first, second, margin = max(template.size.x, template.size.z)))
    }

    private fun loadCartTemplate(): LoadedSchemStructure? {
        if (!cartTemplateLoadAttempted) {
            cartTemplateLoadAttempted = true
            cartTemplate = SchemStructureService(plugin).load(CART_STRUCTURE_PATH)
        }
        return cartTemplate
    }

    private fun buildStraightPath(first: Location, second: Location): List<Location> {
        if (first.world?.uid != second.world?.uid) return emptyList()
        if (first.blockX != second.blockX && first.blockZ != second.blockZ) return emptyList()

        val points = mutableListOf<Location>()
        if (first.blockX == second.blockX) {
            val step = if (second.blockZ >= first.blockZ) 1 else -1
            var z = first.blockZ
            while (true) {
                points += Location(first.world, first.blockX.toDouble(), first.blockY.toDouble(), z.toDouble())
                if (z == second.blockZ) break
                z += step
            }
        } else {
            val step = if (second.blockX >= first.blockX) 1 else -1
            var x = first.blockX
            while (true) {
                points += Location(first.world, x.toDouble(), first.blockY.toDouble(), first.blockZ.toDouble())
                if (x == second.blockX) break
                x += step
            }
        }
        return points
    }

    private fun extendTrackPoints(points: List<Location>, axis: TrackAxis, extension: Int): List<Location> {
        if (points.isEmpty() || extension <= 0) return points
        val world = points.first().world
        val head = points.first()
        val tail = points.last()
        val step = when (axis) {
            TrackAxis.X -> if (tail.blockX >= head.blockX) 1 else -1
            TrackAxis.Z -> if (tail.blockZ >= head.blockZ) 1 else -1
        }
        val before = (extension downTo 1).map { offset ->
            when (axis) {
                TrackAxis.X -> Location(world, (head.blockX - step * offset).toDouble(), head.blockY.toDouble(), head.blockZ.toDouble())
                TrackAxis.Z -> Location(world, head.blockX.toDouble(), head.blockY.toDouble(), (head.blockZ - step * offset).toDouble())
            }
        }
        val after = (1..extension).map { offset ->
            when (axis) {
                TrackAxis.X -> Location(world, (tail.blockX + step * offset).toDouble(), tail.blockY.toDouble(), tail.blockZ.toDouble())
                TrackAxis.Z -> Location(world, tail.blockX.toDouble(), tail.blockY.toDouble(), (tail.blockZ + step * offset).toDouble())
            }
        }
        return before + points + after
    }

    private fun nearestPointIndex(points: List<Location>, location: Location): Int {
        return points.indices.minByOrNull { index ->
            val point = points[index]
            val dx = point.blockX - location.blockX
            val dy = point.blockY - location.blockY
            val dz = point.blockZ - location.blockZ
            dx * dx + dy * dy + dz * dz
        } ?: 0
    }

    private fun rotationQuarterForYaw(yaw: Float): Int {
        return when (CardinalDirection.fromPlayerYaw(yaw)) {
            CardinalDirection.SOUTH -> 0
            CardinalDirection.WEST -> 1
            CardinalDirection.NORTH -> 2
            CardinalDirection.EAST -> 3
        }
    }

    private inner class TrackPath(
        private val wave: Int,
        private val laneName: String,
        private val points: List<Location>,
        private val axis: TrackAxis,
        private val template: LoadedSchemStructure,
        private val referenceYaw: Float,
        private val vehicles: MutableList<CartVehicle>
    ) {
        fun tick(context: ArenaMechanicContext, currentTick: Long) {
            if (points.isEmpty()) return
            vehicles.forEach { vehicle ->
                if (vehicle.lastBounds == null) {
                    placeVehicle(vehicle)
                }
                vehicle.accumulatedTicks++
                if (vehicle.accumulatedTicks >= vehicle.moveIntervalTicks) {
                    vehicle.accumulatedTicks = 0
                    vehicle.index = (vehicle.index + vehicle.direction).floorMod(points.size)
                    placeVehicle(vehicle)
                }
                applyCollision(context, vehicle, currentTick)
                playRollingSound(vehicle, currentTick)
            }
        }

        fun clear() {
            vehicles.forEach { vehicle ->
                restoreSnapshot(vehicle.lastSnapshot)
                vehicle.lastSnapshot = emptyList()
                vehicle.lastBounds = null
            }
        }

        private fun placeVehicle(vehicle: CartVehicle) {
            restoreSnapshot(vehicle.lastSnapshot)
            val origin = points[vehicle.index].clone()
            val bounds = cartBounds(origin)
            vehicle.lastSnapshot = snapshot(bounds)
            vehicle.lastBounds = bounds
            template.paste(
                origin,
                StructurePasteOptions(
                    rotationQuarter = rotationQuarterForYaw(referenceYaw),
                    pasteAir = false,
                    copyEntities = false,
                    copyBiomes = false
                )
            )
        }

        private fun cartBounds(origin: Location): ArenaBounds {
            val (widthX, widthZ) = rotatedCartFootprint()
            return ArenaBounds(
                minX = origin.blockX,
                maxX = origin.blockX + widthX - 1,
                minY = origin.blockY,
                maxY = origin.blockY + template.size.y - 1,
                minZ = origin.blockZ,
                maxZ = origin.blockZ + widthZ - 1
            )
        }

        private fun rotatedCartFootprint(): Pair<Int, Int> {
            // cart.schem はX軸方向を基準に作る。Z軸経路ではpaste時の回転と同じfootprintで、
            // 復元snapshot・当たり判定・中心合わせも合わせて扱う。
            val bounds = StructureTransform(rotationQuarterForYaw(referenceYaw)).bounds(template.size.x, template.size.z)
            return bounds.width to bounds.depth
        }

        private fun snapshot(bounds: ArenaBounds): List<BlockSnapshot> {
            val world = points.first().world ?: return emptyList()
            val snapshots = mutableListOf<BlockSnapshot>()
            for (x in bounds.minX..bounds.maxX) {
                for (y in bounds.minY..bounds.maxY) {
                    for (z in bounds.minZ..bounds.maxZ) {
                        val block = world.getBlockAt(x, y, z)
                        snapshots += BlockSnapshot(Location(world, x.toDouble(), y.toDouble(), z.toDouble()), block.blockData.clone())
                    }
                }
            }
            return snapshots
        }

        private fun restoreSnapshot(snapshot: List<BlockSnapshot>) {
            snapshot.forEach { block ->
                block.location.block.setBlockData(block.blockData, false)
            }
        }

        private fun applyCollision(context: ArenaMechanicContext, vehicle: CartVehicle, currentTick: Long) {
            val bounds = vehicle.lastBounds ?: return
            val direction = cartDirection(vehicle)
            val frontBounds = frontCollisionBounds(bounds, direction)
            val center = Location(
                points.first().world,
                (frontBounds.minX + frontBounds.maxX + 1) * 0.5,
                (frontBounds.minY + frontBounds.maxY + 1) * 0.5,
                (frontBounds.minZ + frontBounds.maxZ + 1) * 0.5
            )
            val targets = ArenaMechanicSupport.targetsNear(
                context,
                center,
                (frontBounds.maxX - frontBounds.minX + 1) * 0.5,
                (frontBounds.maxY - frontBounds.minY + 1) * 0.5,
                (frontBounds.maxZ - frontBounds.minZ + 1) * 0.5
            )
            targets.forEach { target ->
                if (!isOnMovingFrontFace(target.location, frontBounds)) return@forEach
                if (hitCooldownUntilTick[target.uniqueId]?.let { it > currentTick } == true) return@forEach
                hitCooldownUntilTick[target.uniqueId] = currentTick + CART_HIT_COOLDOWN_TICKS
                target.damage(CART_DAMAGE)
                applyKnockback(target, direction)
                target.world.spawnParticle(Particle.CRIT, target.location.clone().add(0.0, 1.0, 0.0), 6, 0.25, 0.25, 0.25, 0.02)
            }
        }

        private fun cartDirection(vehicle: CartVehicle): Vector {
            val sign = vehicle.direction.toDouble()
            return when (axis) {
                TrackAxis.X -> Vector(sign, 0.0, 0.0)
                TrackAxis.Z -> Vector(0.0, 0.0, sign)
            }
        }

        private fun frontCollisionBounds(bounds: ArenaBounds, direction: Vector): ArenaBounds {
            return when {
                direction.x > 0.0 -> bounds.copy(minX = bounds.maxX)
                direction.x < 0.0 -> bounds.copy(maxX = bounds.minX)
                direction.z > 0.0 -> bounds.copy(minZ = bounds.maxZ)
                else -> bounds.copy(maxZ = bounds.minZ)
            }
        }

        private fun isOnMovingFrontFace(location: Location, bounds: ArenaBounds): Boolean {
            return location.x >= bounds.minX &&
                location.x < bounds.maxX + 1.0 &&
                location.y >= bounds.minY &&
                location.y < bounds.maxY + 1.0 &&
                location.z >= bounds.minZ &&
                location.z < bounds.maxZ + 1.0
        }

        private fun applyKnockback(target: LivingEntity, direction: Vector) {
            val velocity = target.velocity.add(direction.clone().multiply(CART_KNOCKBACK_STRENGTH))
            velocity.y = max(velocity.y, CART_KNOCKBACK_Y)
            target.velocity = velocity
        }

        private fun playRollingSound(vehicle: CartVehicle, currentTick: Long) {
            val interval = (CART_SOUND_BASE_INTERVAL_TICKS / CART_SOUND_PITCH).toLong().coerceAtLeast(20L)
            if (currentTick < vehicle.nextSoundTick) return
            vehicle.nextSoundTick = currentTick + interval
            val center = points[vehicle.index].clone().add(0.5, 0.5, 0.5)
            center.world?.playSound(center, Sound.ENTITY_MINECART_INSIDE, CART_SOUND_VOLUME, CART_SOUND_PITCH)
        }

        override fun toString(): String = "TrackPath(wave=$wave,lane=$laneName,points=${points.size})"
    }

    private inner class MagmaVent(
        private val wave: Int,
        val location: Location
    ) {
        private var phase: MagmaVentPhase = MagmaVentPhase.IDLE
        private var remainingTicks: Long = Random.nextLong(MAGMA_IDLE_MIN_TICKS, MAGMA_IDLE_MAX_TICKS + 1)
        private var nextDamageTick: Long = 0L

        fun tick(context: ArenaMechanicContext, currentTick: Long) {
            if (location.world == null) return
            remainingTicks--
            when (phase) {
                MagmaVentPhase.IDLE -> {
                    if (remainingTicks <= 0) beginWarning()
                }
                MagmaVentPhase.WARNING -> {
                    renderWarning()
                    if (remainingTicks <= 0) beginErupting(currentTick)
                }
                MagmaVentPhase.ERUPTING -> {
                    renderEruption()
                    if (currentTick >= nextDamageTick) {
                        nextDamageTick = currentTick + MAGMA_DAMAGE_INTERVAL_TICKS
                        applyEruptionDamage(context)
                    }
                    if (remainingTicks <= 0) {
                        restore()
                        resetIdle()
                    }
                }
            }
        }

        fun restore() {
            phase = MagmaVentPhase.IDLE
            remainingTicks = Random.nextLong(MAGMA_IDLE_MIN_TICKS, MAGMA_IDLE_MAX_TICKS + 1)
        }

        private fun beginWarning() {
            location.world?.playSound(location, Sound.BLOCK_LAVA_POP, 0.4f, 0.8f)
            phase = MagmaVentPhase.WARNING
            remainingTicks = MAGMA_WARNING_TICKS
        }

        private fun beginErupting(currentTick: Long) {
            location.world?.playSound(location, Sound.ITEM_FIRECHARGE_USE, 0.5f, 0.75f)
            phase = MagmaVentPhase.ERUPTING
            remainingTicks = MAGMA_ERUPTION_TICKS
            nextDamageTick = currentTick
        }

        private fun resetIdle() {
            phase = MagmaVentPhase.IDLE
            remainingTicks = Random.nextLong(MAGMA_IDLE_MIN_TICKS, MAGMA_IDLE_MAX_TICKS + 1)
        }

        private fun renderWarning() {
            val world = location.world ?: return
            world.spawnParticle(Particle.FLAME, location.clone().add(0.5, 0.15, 0.5), 1, 0.15, 0.02, 0.15, 0.04)
        }

        private fun renderEruption() {
            val world = location.world ?: return
            world.spawnParticle(Particle.LAVA, location.clone().add(0.5, 0.8, 0.5), 1, 0.08, 0.2, 0.08, 0.08)
            world.spawnParticle(Particle.FLAME, location.clone().add(0.5, 0.9, 0.5), 2, 0.08, 0.4, 0.08, 0.08)
        }

        private fun applyEruptionDamage(context: ArenaMechanicContext) {
            val center = location.clone().add(0.5, 1.0, 0.5)
            ArenaMechanicSupport.targetsNear(context, center, 0.75, 2.0, 0.75).forEach { target ->
                target.damage(if (context.session.promoted) MAGMA_DAMAGE_PROMOTED else MAGMA_DAMAGE)
                target.fireTicks = max(target.fireTicks, if (context.session.promoted) MAGMA_FIRE_TICKS_PROMOTED else MAGMA_FIRE_TICKS)
            }
        }

        override fun toString(): String = "MagmaVent(wave=$wave,location=${location.blockX},${location.blockY},${location.blockZ})"
    }

    private data class CartVehicle(
        var index: Int,
        val direction: Int,
        val moveIntervalTicks: Long,
        var accumulatedTicks: Long = 0L,
        var nextSoundTick: Long = 0L,
        var lastBounds: ArenaBounds? = null,
        var lastSnapshot: List<BlockSnapshot> = emptyList()
    )

    private data class NetherGoalState(
        var areaMode: NetherAreaMode,
        val goalBounds: ArenaBounds,
        val engineMarkers: List<Location>,
        val coreActivationMarkers: List<Location>,
        val cores: MutableMap<CoreSide, NetherCoreState>,
        var engineStarted: Boolean = false,
        var allCoreMarkersActivated: Boolean = false,
        var nextModeSwitchTick: Long = Long.MAX_VALUE,
        var nextCoreParticleTick: Long = 0L,
        val engineMarkerIds: MutableSet<UUID> = mutableSetOf(),
        val coreMarkerIds: MutableMap<CoreSide, MutableSet<UUID>> = mutableMapOf(
            CoreSide.LEFT to mutableSetOf(),
            CoreSide.RIGHT to mutableSetOf()
        ),
        val coreActivationMarkerIds: MutableSet<UUID> = mutableSetOf()
    ) {
        val coreActivationMarkerIdsRequired: Int
            get() = coreMarkerIds.values.sumOf { it.size }

        val allCoresCompleted: Boolean
            get() = cores.values.all { it.completed }

        fun activatedEngineMarkerCount(context: ArenaMechanicContext): Int {
            return engineMarkerIds.count { markerId ->
                context.session.actionMarkers[markerId]?.state == ArenaActionMarkerState.RUNNING
            }
        }
    }

    private data class NetherCoreState(
        val side: CoreSide,
        val center: Location,
        var mode: NetherAreaMode,
        var killCount: Int = 0,
        var completed: Boolean = false,
        var activationMarkersArmed: Boolean = false
    )

    private data class BlockSnapshot(
        val location: Location,
        val blockData: BlockData
    )

    private enum class NetherAreaMode {
        BLAZING,
        SOUL;

        fun opposite(): NetherAreaMode = when (this) {
            BLAZING -> SOUL
            SOUL -> BLAZING
        }
    }

    private enum class CoreSide {
        LEFT,
        RIGHT
    }

    private enum class TrackAxis {
        X,
        Z;

        val cartRotationQuarter: Int
            get() = when (this) {
                X -> 0
                Z -> 1
            }

        val defaultCartYaw: Float
            get() = when (this) {
                X -> 270.0f
                Z -> 0.0f
            }
    }

    private enum class MagmaVentPhase {
        IDLE,
        WARNING,
        ERUPTING
    }

    private fun Int.floorMod(size: Int): Int {
        val mod = this % size
        return if (mod < 0) mod + size else mod
    }

    companion object {
        private const val LEFT_TRACK_TAG = "arena.marker.mechanic.nether.track_path.left"
        private const val RIGHT_TRACK_TAG = "arena.marker.mechanic.nether.track_path.right"
        private const val LEFT_CART_REFERENCE_TAG = "arena.marker.mechanic.nether.cart_reference.left"
        private const val RIGHT_CART_REFERENCE_TAG = "arena.marker.mechanic.nether.cart_reference.right"
        private const val MAGMA_VENT_TAG = "arena.marker.mechanic.nether.magma_vent"
        private const val ENGINE_START_TAG = "arena.marker.mechanic.nether.engine_start"
        private const val CORE_CENTER_LEFT_TAG = "arena.marker.mechanic.nether.core_center.left"
        private const val CORE_CENTER_RIGHT_TAG = "arena.marker.mechanic.nether.core_center.right"
        private const val CORE_ACTIVATE_TAG = "arena.marker.mechanic.nether.core_activate"
        private const val CART_STRUCTURE_PATH = "structures/arena/nether/mechanic/cart.schem"
        private const val GOAL_AREA_BLAZING_PATH = "structures/arena/nether/mechanic/goal_blazing.schem"
        private const val GOAL_AREA_SOUL_PATH = "structures/arena/nether/mechanic/goal_soul.schem"
        private const val CORE_BLAZING_PATH = "structures/arena/nether/mechanic/core_blazing.schem"
        private const val CORE_SOUL_PATH = "structures/arena/nether/mechanic/core_soul.schem"
        private const val MIN_CART_INTERVAL_TICKS = 10L
        private const val MAX_CART_INTERVAL_TICKS = 17L
        private const val CART_DAMAGE = 4.0
        private const val CART_KNOCKBACK_STRENGTH = 2.4
        private const val CART_KNOCKBACK_Y = 0.8
        private const val CART_HIT_COOLDOWN_TICKS = 20L
        private const val CART_SOUND_BASE_INTERVAL_TICKS = 120.0
        private const val CART_SOUND_PITCH = 0.75f
        private const val CART_SOUND_VOLUME = 0.175f
        private const val MAGMA_VENT_COUNT_DIVISOR = 10
        private const val MAGMA_IDLE_MIN_TICKS = 80L
        private const val MAGMA_IDLE_MAX_TICKS = 140L
        private const val MAGMA_WARNING_TICKS = 40L
        private const val MAGMA_ERUPTION_TICKS = 60L
        private const val MAGMA_DAMAGE_INTERVAL_TICKS = 20L
        private const val MAGMA_DAMAGE = 3.0
        private const val MAGMA_DAMAGE_PROMOTED = 4.0
        private const val MAGMA_FIRE_TICKS = 40
        private const val MAGMA_FIRE_TICKS_PROMOTED = 60
        private const val NETHER_ACTION_MARKER_HOLD_TICKS = 60
        private const val ACTION_MARKER_CENTER_Y_OFFSET = 0.1
        private const val MODE_SWITCH_MIN_TICKS = 20L * 60L
        private const val MODE_SWITCH_MAX_TICKS = 20L * 120L
        private const val CORE_HORIZONTAL_RADIUS = 12.0
        private const val CORE_VERTICAL_RADIUS = 24.0
        private const val CORE_VERTICAL_CUTOUT_HALF_HEIGHT = 5.0
        private const val CORE_REQUIRED_KILLS = 10
        private const val CORE_PARTICLE_INTERVAL_TICKS = 20L
        private const val CORE_PARTICLES_IDLE = 10
        private const val CORE_PARTICLES_ON_KILL = 15
        private const val CORE_PARTICLES_ON_COMPLETE = 10
        private const val CORE_PARTICLES_ON_FAILURE = 10
    }
}
