package jp.awabi2048.cccontent.mob.ability

import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class EnderEyeGuidedConfig(
    val orbitRadius: Double,
    val orbitHeightOffset: Double,
    val targetPositionDelayTicks: Int,
    val directionChangeIntervalTicks: Int,
    val directionTransitionTicks: Int,
    val baseAngularSpeed: Double,
    val yFollowLerpFactor: Double
)

data class EnderEyeGuidedCenter(
    val x: Double,
    val z: Double
)

data class EnderEyeGuidedOutput(
    val x: Double,
    val y: Double,
    val z: Double
)

class EnderEyeGuidedState(baseAngularSpeed: Double) {
    var orbitalAngle: Double = 0.0
    var currentAngularSpeed: Double = baseAngularSpeed
    var transitionStartAngularSpeed: Double = baseAngularSpeed
    var targetAngularSpeed: Double = baseAngularSpeed
    var directionChangeTicks: Int = 0
    var directionTransitionTicksRemaining: Int = 0
    var smoothedY: Double = 0.0
    var hasSmoothedY: Boolean = false
    var delayedTargetUuid: UUID? = null
    val delayedTargetCenters: ArrayDeque<EnderEyeGuidedCenter> = ArrayDeque()
}

object EnderEyeGuidedMovementController {
    fun clearTarget(state: EnderEyeGuidedState) {
        state.hasSmoothedY = false
        state.delayedTargetUuid = null
        state.delayedTargetCenters.clear()
        state.directionChangeTicks = 0
        state.directionTransitionTicksRemaining = 0
    }

    fun update(
        state: EnderEyeGuidedState,
        config: EnderEyeGuidedConfig,
        targetUuid: UUID,
        followerX: Double,
        followerZ: Double,
        targetX: Double,
        targetY: Double,
        targetZ: Double
    ): EnderEyeGuidedOutput {
        if (state.delayedTargetUuid != targetUuid) {
            state.delayedTargetUuid = targetUuid
            state.delayedTargetCenters.clear()
            state.orbitalAngle = atan2(followerZ - targetZ, followerX - targetX)
        }

        state.delayedTargetCenters.addLast(EnderEyeGuidedCenter(targetX, targetZ))
        while (state.delayedTargetCenters.size > config.targetPositionDelayTicks.coerceAtLeast(0) + 1) {
            state.delayedTargetCenters.removeFirst()
        }
        val delayedCenter = state.delayedTargetCenters.firstOrNull() ?: EnderEyeGuidedCenter(targetX, targetZ)

        if (state.directionTransitionTicksRemaining > 0) {
            val total = config.directionTransitionTicks.coerceAtLeast(1)
            val elapsed = total - state.directionTransitionTicksRemaining + 1
            val t = (elapsed.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
            state.currentAngularSpeed = lerp(state.transitionStartAngularSpeed, state.targetAngularSpeed, t)
            state.directionTransitionTicksRemaining -= 1
        } else {
            state.currentAngularSpeed = state.targetAngularSpeed
        }

        if (state.directionChangeTicks <= 0) {
            state.directionChangeTicks = config.directionChangeIntervalTicks.coerceAtLeast(1)
            state.transitionStartAngularSpeed = state.currentAngularSpeed
            val direction = if (Random.nextBoolean()) 1.0 else -1.0
            state.targetAngularSpeed = direction * kotlin.math.abs(config.baseAngularSpeed)
            state.directionTransitionTicksRemaining = config.directionTransitionTicks.coerceAtLeast(1)
        } else {
            state.directionChangeTicks -= 1
        }

        state.orbitalAngle += state.currentAngularSpeed

        val baseX = delayedCenter.x + cos(state.orbitalAngle) * config.orbitRadius
        val baseZ = delayedCenter.z + sin(state.orbitalAngle) * config.orbitRadius
        val desiredY = targetY + config.orbitHeightOffset
        if (!state.hasSmoothedY) {
            state.smoothedY = desiredY
            state.hasSmoothedY = true
        } else {
            state.smoothedY += (desiredY - state.smoothedY) * config.yFollowLerpFactor.coerceIn(0.0, 1.0)
        }

        return EnderEyeGuidedOutput(baseX, state.smoothedY, baseZ)
    }

    private fun lerp(a: Double, b: Double, t: Double): Double {
        return a + (b - a) * t
    }
}
