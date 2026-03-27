package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobShootBowContext
import org.bukkit.FluidCollisionMode
import org.bukkit.entity.AbstractArrow
import org.bukkit.util.Vector
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class CurveShotAbility(
    override val id: String,
    private val curveShotChance: Double = DEFAULT_CURVE_SHOT_CHANCE,
    private val curveForceDistance: Double = DEFAULT_CURVE_FORCE_DISTANCE,
    private val maxLaunchAngleDegrees: Double = DEFAULT_MAX_LAUNCH_ANGLE_DEGREES
) : MobAbility {

    override fun onShootBow(context: MobShootBowContext, runtime: MobAbilityRuntime?) {
        val arrow = context.event.projectile as? AbstractArrow
        if (arrow == null) {
            debugLog(context, false, "projectile_not_arrow", null, null, null, false, false)
            return
        }
        val shooter = context.entity
        val source = arrow.location.toVector()

        val target = MobAbilityUtils.resolveTarget(shooter)
        if (target == null) {
            debugLog(context, false, "target_missing", source, null, arrow.velocity, false, false)
            return
        }
        if (!target.isValid || target.isDead || target.world.uid != shooter.world.uid) {
            debugLog(context, false, "target_invalid", source, null, arrow.velocity, false, false)
            return
        }

        val targetPosition = target.eyeLocation.toVector()
        val horizontalDistance = targetPosition.clone().subtract(source).setY(0.0).length()
        val forcedByDistance = horizontalDistance >= curveForceDistance
        if (!forcedByDistance && Random.nextDouble() >= curveShotChance) {
            debugLog(context, false, "chance_roll_failed", source, targetPosition, arrow.velocity, false, false)
            return
        }

        val speed = arrow.velocity.length().coerceAtLeast(0.1)
        val curvedResult = computeCurvedVelocity(
            source = source,
            target = targetPosition,
            speed = speed,
            world = shooter.world,
            maxLaunchAngleDegrees = maxLaunchAngleDegrees
        )
        val finalVelocity = when {
            curvedResult != null -> curvedResult.velocity
            forcedByDistance -> buildFallbackCurveVelocity(
                source = source,
                target = targetPosition,
                speed = speed,
                launchAngleDegrees = maxLaunchAngleDegrees
            )
            else -> null
        }

        if (finalVelocity == null) {
            debugLog(context, false, "curve_solution_not_found", source, targetPosition, arrow.velocity, forcedByDistance, false)
            return
        }

        val clampedToMaxAngle = (curvedResult?.clamped ?: true) || launchAngleDegrees(finalVelocity) >= maxLaunchAngleDegrees - 0.01
        arrow.velocity = finalVelocity
        val reason = if (curvedResult == null && forcedByDistance) "curve_forced_fallback" else "curve_replaced"
        debugLog(context, true, reason, source, targetPosition, finalVelocity, forcedByDistance, clampedToMaxAngle)
    }

    private fun debugLog(
        context: MobShootBowContext,
        replaced: Boolean,
        reason: String,
        source: Vector?,
        target: Vector?,
        velocity: Vector?,
        forcedByDistance: Boolean,
        clampedToMaxAngle: Boolean
    ) {
        val horizontalDistance = if (source != null && target != null) {
            target.clone().subtract(source).setY(0.0).length()
        } else {
            -1.0
        }
        val angle = if (velocity != null) launchAngleDegrees(velocity) else Double.NaN
        val speed = velocity?.length() ?: Double.NaN
        context.plugin.logger.info(
            "[CurveShotDebug] mob=${context.activeMob.mobType.id} replaced=$replaced reason=$reason " +
                "forced_by_distance=$forcedByDistance clamped_to_30deg=$clampedToMaxAngle " +
                "angle=${"%.2f".format(Locale.US, angle)} speed=${"%.3f".format(Locale.US, speed)} " +
                "horizontalDistance=${"%.2f".format(Locale.US, horizontalDistance)}"
        )
    }

    private data class CurveVelocityResult(
        val velocity: Vector,
        val clamped: Boolean
    )

    private fun computeCurvedVelocity(
        source: Vector,
        target: Vector,
        speed: Double,
        world: org.bukkit.World,
        maxLaunchAngleDegrees: Double
    ): CurveVelocityResult? {
        val horizontal = target.clone().subtract(source).setY(0.0)
        val horizontalDistance = horizontal.length()
        if (horizontalDistance < 0.001) {
            return null
        }

        val heightDelta = target.y - source.y
        val gravity = CURVE_ARROW_GRAVITY_PER_TICK
        val speedSquared = speed * speed
        val discriminant = speedSquared * speedSquared - gravity * (gravity * horizontalDistance * horizontalDistance + 2.0 * heightDelta * speedSquared)
        if (discriminant <= 0.0) {
            return null
        }

        val tanTheta = (speedSquared + sqrt(discriminant)) / (gravity * horizontalDistance)
        val highArcTheta = atan(tanTheta)
        val maxTheta = Math.toRadians(maxLaunchAngleDegrees)
        val theta = min(highArcTheta, maxTheta)
        val cosTheta = cos(theta)
        if (cosTheta <= 0.0) {
            return null
        }
        val sinTheta = sin(theta)

        val horizontalDirection = horizontal.normalize()
        val velocity = horizontalDirection.multiply(speed * cosTheta).setY(speed * sinTheta)
        if (!isCurvePathClear(world, source, velocity, target)) {
            return null
        }
        return CurveVelocityResult(velocity = velocity, clamped = theta < highArcTheta)
    }

    private fun buildFallbackCurveVelocity(source: Vector, target: Vector, speed: Double, launchAngleDegrees: Double): Vector? {
        val horizontal = target.clone().subtract(source).setY(0.0)
        if (horizontal.lengthSquared() < 1.0E-6) {
            return null
        }
        val horizontalDirection = horizontal.normalize()
        val angleRad = Math.toRadians(launchAngleDegrees)
        val horizontalSpeed = speed * cos(angleRad)
        val ySpeed = speed * sin(angleRad)
        return horizontalDirection.multiply(horizontalSpeed).setY(ySpeed)
    }

    private fun isCurvePathClear(world: org.bukkit.World, source: Vector, velocity: Vector, target: Vector): Boolean {
        var previous = source.clone()

        var tick = 1
        while (tick <= CURVE_MAX_SIM_TICKS) {
            val time = tick.toDouble()
            val current = source.clone()
                .add(velocity.clone().multiply(time))
                .add(Vector(0.0, -0.5 * CURVE_ARROW_GRAVITY_PER_TICK * time * time, 0.0))

            val segment = current.clone().subtract(previous)
            val segmentLength = segment.length()
            if (segmentLength > 0.0) {
                val blocking = world.rayTraceBlocks(
                    org.bukkit.Location(world, previous.x, previous.y, previous.z),
                    segment.normalize(),
                    segmentLength,
                    FluidCollisionMode.NEVER,
                    true
                )
                if (blocking != null) {
                    return false
                }
            }

            if (current.distanceSquared(target) <= CURVE_HIT_TOLERANCE_SQUARED) {
                return true
            }
            if (abs(current.y - target.y) <= 1.6 && current.clone().setY(0.0).distanceSquared(target.clone().setY(0.0)) <= 2.0) {
                return true
            }
            if (current.y < source.y - CURVE_DROP_ABORT_DISTANCE) {
                return false
            }

            previous = current
            tick += 1
        }
        return false
    }

    private fun launchAngleDegrees(velocity: Vector): Double {
        val horizontal = sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
        if (horizontal < 1.0E-6) {
            return if (velocity.y >= 0.0) 90.0 else -90.0
        }
        return Math.toDegrees(atan2(velocity.y, horizontal))
    }

    companion object {
        const val DEFAULT_CURVE_SHOT_CHANCE = 0.70
        const val DEFAULT_CURVE_FORCE_DISTANCE = 12.0
        const val DEFAULT_MAX_LAUNCH_ANGLE_DEGREES = 30.0

        private const val CURVE_ARROW_GRAVITY_PER_TICK = 0.05
        private const val CURVE_MAX_SIM_TICKS = 80
        private const val CURVE_HIT_TOLERANCE_SQUARED = 1.2
        private const val CURVE_DROP_ABORT_DISTANCE = 8.0
    }
}
