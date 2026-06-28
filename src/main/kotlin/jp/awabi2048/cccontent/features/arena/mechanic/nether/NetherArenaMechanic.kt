package jp.awabi2048.cccontent.features.arena.mechanic.nether

import jp.awabi2048.cccontent.features.arena.ArenaBounds
import jp.awabi2048.cccontent.features.arena.mechanic.ArenaMechanicContext
import jp.awabi2048.cccontent.features.arena.mechanic.ArenaMechanicSupport
import jp.awabi2048.cccontent.features.arena.mechanic.ArenaThemeMechanic
import jp.awabi2048.cccontent.structure.LoadedSchemStructure
import jp.awabi2048.cccontent.structure.SchemStructureService
import jp.awabi2048.cccontent.structure.StructurePasteOptions
import jp.awabi2048.cccontent.structure.StructureTransform
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.data.BlockData
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

class NetherArenaMechanic(private val plugin: JavaPlugin) : ArenaThemeMechanic {
    private val trackPathsByWave = mutableMapOf<Int, MutableList<TrackPath>>()
    private val ventsByWave = mutableMapOf<Int, MutableList<MagmaVent>>()
    private val hitCooldownUntilTick = mutableMapOf<UUID, Long>()
    private var cartTemplate: LoadedSchemStructure? = null
    private var cartTemplateLoadAttempted = false

    override fun onStageReady(context: ArenaMechanicContext) {
        trackPathsByWave.clear()
        ventsByWave.clear()
        hitCooldownUntilTick.clear()

        for (wave in 1..context.session.waves) {
            registerTrackPath(context, wave, LEFT_TRACK_TAG, "left")
            registerTrackPath(context, wave, RIGHT_TRACK_TAG, "right")

            val vents = context.markersForWave(wave, MAGMA_VENT_TAG)
                .map { MagmaVent(wave = wave, location = it.clone()) }
                .toMutableList()
            if (vents.isNotEmpty()) {
                ventsByWave[wave] = vents
                vents.forEach { context.addCleanupBounds(ArenaMechanicSupport.boundsAround(it.location, 0, 0, 0)) }
            }
        }

        val pathCount = trackPathsByWave.values.sumOf { it.size }
        val ventCount = ventsByWave.values.sumOf { it.size }
        if (pathCount > 0 || ventCount > 0) {
            plugin.logger.info("[Arena] Nether mechanic prepared: world=${context.session.worldName} tracks=$pathCount vents=$ventCount")
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
    }

    private fun registerTrackPath(context: ArenaMechanicContext, wave: Int, tag: String, laneName: String) {
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

        val direction = if (Random.nextBoolean()) 1 else -1
        val interval = Random.nextLong(
            MIN_CART_INTERVAL_TICKS,
            MAX_CART_INTERVAL_TICKS + 1
        )
        val path = TrackPath(
            wave = wave,
            laneName = laneName,
            points = points,
            axis = if (first.blockX != second.blockX) TrackAxis.X else TrackAxis.Z,
            template = template,
            vehicles = mutableListOf(
                CartVehicle(index = 0, direction = direction, moveIntervalTicks = interval),
                CartVehicle(index = points.size / 2, direction = direction, moveIntervalTicks = interval)
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

    private inner class TrackPath(
        private val wave: Int,
        private val laneName: String,
        private val points: List<Location>,
        private val axis: TrackAxis,
        private val template: LoadedSchemStructure,
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
            val center = points[vehicle.index]
            val origin = pasteOrigin(center)
            val bounds = cartBounds(origin)
            vehicle.lastSnapshot = snapshot(bounds)
            vehicle.lastBounds = bounds
            template.paste(
                origin,
                StructurePasteOptions(
                    rotationQuarter = axis.cartRotationQuarter,
                    pasteAir = false,
                    copyEntities = false,
                    copyBiomes = false
                )
            )
        }

        private fun pasteOrigin(center: Location): Location {
            val (widthX, widthZ) = rotatedCartFootprint()
            return center.clone().add(
                -(widthX / 2).toDouble(),
                CART_ORIGIN_Y_OFFSET.toDouble(),
                -(widthZ / 2).toDouble()
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
            val bounds = StructureTransform(axis.cartRotationQuarter).bounds(template.size.x, template.size.z)
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
            val center = Location(
                points.first().world,
                (bounds.minX + bounds.maxX + 1) * 0.5,
                (bounds.minY + bounds.maxY + 1) * 0.5,
                (bounds.minZ + bounds.maxZ + 1) * 0.5
            )
            val targets = ArenaMechanicSupport.targetsNear(
                context,
                center,
                (bounds.maxX - bounds.minX + 1) * 0.5,
                (bounds.maxY - bounds.minY + 1) * 0.5,
                (bounds.maxZ - bounds.minZ + 1) * 0.5
            )
            val direction = cartDirection(vehicle)
            targets.forEach { target ->
                if (!bounds.contains(target.location.x, target.location.y, target.location.z)) return@forEach
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
            center.world?.playSound(center, Sound.ENTITY_MINECART_INSIDE, 0.7f, CART_SOUND_PITCH)
        }

        override fun toString(): String = "TrackPath(wave=$wave,lane=$laneName,points=${points.size})"
    }

    private inner class MagmaVent(
        private val wave: Int,
        val location: Location
    ) {
        private var phase: MagmaVentPhase = MagmaVentPhase.IDLE
        private var remainingTicks: Long = Random.nextLong(MAGMA_IDLE_MIN_TICKS, MAGMA_IDLE_MAX_TICKS + 1)
        private var originalBlockData: BlockData? = null
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
            val data = originalBlockData ?: return
            location.block.setBlockData(data, false)
            originalBlockData = null
            phase = MagmaVentPhase.IDLE
            remainingTicks = Random.nextLong(MAGMA_IDLE_MIN_TICKS, MAGMA_IDLE_MAX_TICKS + 1)
        }

        private fun beginWarning() {
            captureOriginal()
            location.block.type = Material.RED_STAINED_GLASS
            location.world?.playSound(location, Sound.BLOCK_LAVA_POP, 0.8f, 0.8f)
            phase = MagmaVentPhase.WARNING
            remainingTicks = MAGMA_WARNING_TICKS
        }

        private fun beginErupting(currentTick: Long) {
            captureOriginal()
            location.block.type = Material.MAGMA_BLOCK
            location.world?.playSound(location, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.75f)
            phase = MagmaVentPhase.ERUPTING
            remainingTicks = MAGMA_ERUPTION_TICKS
            nextDamageTick = currentTick
        }

        private fun captureOriginal() {
            if (originalBlockData == null) {
                originalBlockData = location.block.blockData.clone()
            }
        }

        private fun resetIdle() {
            phase = MagmaVentPhase.IDLE
            remainingTicks = Random.nextLong(MAGMA_IDLE_MIN_TICKS, MAGMA_IDLE_MAX_TICKS + 1)
        }

        private fun renderWarning() {
            val world = location.world ?: return
            world.spawnParticle(Particle.FLAME, location.clone().add(0.5, 0.15, 0.5), 4, 0.25, 0.05, 0.25, 0.02)
        }

        private fun renderEruption() {
            val world = location.world ?: return
            world.spawnParticle(Particle.LAVA, location.clone().add(0.5, 1.0, 0.5), 3, 0.35, 0.8, 0.35, 0.02)
            world.spawnParticle(Particle.FLAME, location.clone().add(0.5, 1.1, 0.5), 8, 0.3, 1.2, 0.3, 0.03)
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

    private data class BlockSnapshot(
        val location: Location,
        val blockData: BlockData
    )

    private enum class TrackAxis {
        X,
        Z;

        val cartRotationQuarter: Int
            get() = when (this) {
                X -> 0
                Z -> 1
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
        private const val MAGMA_VENT_TAG = "arena.marker.mechanic.nether.magma_vent"
        private const val CART_STRUCTURE_PATH = "structures/arena/nether/mechanic/cart.schem"
        private const val CART_ORIGIN_Y_OFFSET = 0
        private const val MIN_CART_INTERVAL_TICKS = 30L
        private const val MAX_CART_INTERVAL_TICKS = 50L
        private const val CART_DAMAGE = 4.0
        private const val CART_KNOCKBACK_STRENGTH = 1.2
        private const val CART_KNOCKBACK_Y = 0.4
        private const val CART_HIT_COOLDOWN_TICKS = 20L
        private const val CART_SOUND_BASE_INTERVAL_TICKS = 120.0
        private const val CART_SOUND_PITCH = 0.75f
        private const val MAGMA_IDLE_MIN_TICKS = 80L
        private const val MAGMA_IDLE_MAX_TICKS = 140L
        private const val MAGMA_WARNING_TICKS = 40L
        private const val MAGMA_ERUPTION_TICKS = 60L
        private const val MAGMA_DAMAGE_INTERVAL_TICKS = 20L
        private const val MAGMA_DAMAGE = 3.0
        private const val MAGMA_DAMAGE_PROMOTED = 4.0
        private const val MAGMA_FIRE_TICKS = 40
        private const val MAGMA_FIRE_TICKS_PROMOTED = 60
    }
}
