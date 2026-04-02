package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.Waterlogged
import org.bukkit.entity.Mob
import org.bukkit.util.Vector
import kotlin.math.roundToInt

class ReturnToWaterPriorityAbility(
    override val id: String,
    private val searchRadius: Int = DEFAULT_SEARCH_RADIUS,
    private val requiredLandTicks: Long = DEFAULT_REQUIRED_LAND_TICKS
) : MobAbility {

    data class Runtime(
        var landTicks: Long = 0L,
        var jumping: Boolean = false,
        var jumpTicks: Long = 0L,
        var aiForcedOff: Boolean = false,
        var pathPoints: List<Vector>? = null,
        var pathIndex: Int = 0
    ) : MobAbilityRuntime

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime = Runtime()

    override fun tickIntervalTicks(): Long = 1L

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val mob = context.entity as? Mob ?: return
        val rt = runtime as? Runtime ?: return

        if (rt.jumping) {
            handleJumpingState(mob, rt)
            return
        }

        if (isInWaterLike(mob)) {
            rt.landTicks = 0L
            restoreAiIfNeeded(mob, rt)
            return
        }

        rt.landTicks = (rt.landTicks + context.tickDelta).coerceAtLeast(0L)
        if (rt.landTicks < requiredLandTicks) return

        val origin = mob.location.clone().add(0.0, 0.1, 0.0)
        val destination = findNearestWaterLocation(origin) ?: return
        val pathPoints = buildJumpPath(origin, destination, mob.world) ?: return

        mob.pathfinder.stopPathfinding()
        mob.target = null
        mob.setAI(false)
        rt.aiForcedOff = true
        faceTowardsLocation(mob, destination)

        rt.jumping = true
        rt.jumpTicks = 0L
        rt.landTicks = 0L
        rt.pathPoints = pathPoints
        rt.pathIndex = 0

        if (!applyNextJumpStep(mob, rt)) {
            resetJumpState(mob, rt)
        }
    }

    override fun onDeath(context: jp.awabi2048.cccontent.mob.MobDeathContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        restoreAiIfNeeded(context.entity as? Mob ?: return, rt)
    }

    private fun handleJumpingState(mob: Mob, runtime: Runtime) {
        if (isInWaterLike(mob)) {
            val target = MobAbilityUtils.resolveTarget(mob)
            if (target != null) {
                MobAbilityUtils.faceTowards(mob, target)
            }
            resetJumpState(mob, runtime)
            return
        }

        if (runtime.jumpTicks >= JUMP_TIMEOUT_TICKS || !applyNextJumpStep(mob, runtime)) {
            resetJumpState(mob, runtime)
        }
    }

    private fun applyNextJumpStep(mob: Mob, runtime: Runtime): Boolean {
        val points = runtime.pathPoints ?: return false
        if (runtime.pathIndex >= points.size) return false

        val world = mob.world
        val current = mob.location.clone().add(0.0, 0.1, 0.0)
        val point = points[runtime.pathIndex]
        val target = Location(world, point.x, point.y, point.z)

        if (collidesOnSegment(world, current, target)) {
            return false
        }

        faceTowardsLocation(mob, target)
        mob.velocity = target.toVector().subtract(mob.location.toVector())

        runtime.pathIndex += 1
        runtime.jumpTicks += 1L
        return true
    }

    private fun resetJumpState(mob: Mob, runtime: Runtime) {
        restoreAiIfNeeded(mob, runtime)
        runtime.jumping = false
        runtime.jumpTicks = 0L
        runtime.pathPoints = null
        runtime.pathIndex = 0
    }

    private fun restoreAiIfNeeded(mob: Mob, runtime: Runtime) {
        if (!runtime.aiForcedOff) return
        mob.setAI(true)
        runtime.aiForcedOff = false
    }

    private fun findNearestWaterLocation(origin: Location): Location? {
        val world = origin.world ?: return null
        var nearest: Location? = null
        var nearestDistanceSquared = Double.MAX_VALUE

        val baseX = origin.blockX
        val baseY = origin.blockY
        val baseZ = origin.blockZ

        for (dx in -searchRadius..searchRadius) {
            for (dy in -searchRadius..searchRadius) {
                for (dz in -searchRadius..searchRadius) {
                    val block = world.getBlockAt(baseX + dx, baseY + dy, baseZ + dz)
                    val isWater = isWaterBlock(block.type, block.blockData is Waterlogged && (block.blockData as Waterlogged).isWaterlogged)
                    if (!isWater) continue

                    val destination = block.location.clone().add(0.5, 0.1, 0.5)
                    val distanceSquared = destination.distanceSquared(origin)
                    if (distanceSquared < nearestDistanceSquared) {
                        nearestDistanceSquared = distanceSquared
                        nearest = destination
                    }
                }
            }
        }

        return nearest
    }

    private fun buildJumpPath(origin: Location, destination: Location, world: World): List<Vector>? {
        val horizontalDistance = origin.toVector().setY(0.0).distance(destination.toVector().setY(0.0))
        val flightTicks = (horizontalDistance * FLIGHT_TICKS_PER_BLOCK).roundToInt().coerceIn(MIN_FLIGHT_TICKS, MAX_FLIGHT_TICKS)
        val arcHeight = (MIN_ARC_HEIGHT + horizontalDistance * ARC_HEIGHT_PER_BLOCK).coerceAtMost(MAX_ARC_HEIGHT)

        val points = mutableListOf<Vector>()
        var previous = origin.toVector()
        for (step in 1..flightTicks) {
            val t = step.toDouble() / flightTicks.toDouble()
            val base = origin.toVector().multiply(1.0 - t).add(destination.toVector().multiply(t))
            val arc = 4.0 * arcHeight * t * (1.0 - t)
            val point = base.setY(base.y + arc)

            val prev = Location(world, previous.x, previous.y, previous.z)
            val next = Location(world, point.x, point.y, point.z)
            if (collidesOnSegment(world, prev, next)) {
                return null
            }

            points += point
            previous = point
        }

        return points
    }

    private fun collidesOnSegment(world: World, from: Location, to: Location): Boolean {
        val segment = to.toVector().subtract(from.toVector())
        val length = segment.length()
        if (length <= 0.0001) return false
        val direction = segment.clone().normalize()

        val lowFrom = from.clone().add(0.0, 0.15, 0.0)
        val highFrom = from.clone().add(0.0, 1.2, 0.0)
        val lowHit = world.rayTraceBlocks(lowFrom, direction, length, FluidCollisionMode.NEVER, true)
        val highHit = world.rayTraceBlocks(highFrom, direction, length, FluidCollisionMode.NEVER, true)
        return lowHit != null || highHit != null
    }

    private fun isInWaterLike(mob: Mob): Boolean {
        return mob.isInWater || mob.isInBubbleColumn
    }

    private fun faceTowardsLocation(entity: Mob, location: Location) {
        val direction = location.toVector().subtract(entity.eyeLocation.toVector())
        if (direction.lengthSquared() < 0.0001) return
        val normalized = direction.normalize()
        entity.setRotation(
            Math.toDegrees(kotlin.math.atan2(-normalized.x, normalized.z)).toFloat(),
            Math.toDegrees(-kotlin.math.asin(normalized.y)).toFloat()
        )
    }

    private fun isWaterBlock(type: Material, isWaterlogged: Boolean): Boolean {
        return type == Material.WATER || type == Material.BUBBLE_COLUMN || isWaterlogged
    }

    companion object {
        private const val DEFAULT_SEARCH_RADIUS = 5
        private const val DEFAULT_REQUIRED_LAND_TICKS = 10L
        private const val JUMP_TIMEOUT_TICKS = 40L
        private const val MIN_FLIGHT_TICKS = 8
        private const val MAX_FLIGHT_TICKS = 16
        private const val FLIGHT_TICKS_PER_BLOCK = 3.6
        private const val MIN_ARC_HEIGHT = 0.55
        private const val ARC_HEIGHT_PER_BLOCK = 0.16
        private const val MAX_ARC_HEIGHT = 1.25
    }
}
