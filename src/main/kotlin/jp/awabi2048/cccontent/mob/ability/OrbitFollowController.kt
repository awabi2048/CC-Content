package jp.awabi2048.cccontent.mob.ability

import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class OrbitFollowConfig(
    val orbitRadius: Double,
    val orbitHeightOffset: Double,
    val targetPositionDelayTicks: Int,
    val directionChangeIntervalTicks: Int,
    val directionTransitionTicks: Int,
    val baseAngularSpeed: Double,
    val yFollowLerpFactor: Double,
    val stationarySpeedThreshold: Double,
    val stationaryEnterTicks: Int,
    val stationaryExitTicks: Int,
    val stationaryHoverAmplitude: Double,
    val stationaryHoverAngularSpeed: Double
)

data class OrbitFollowOutput(
    val x: Double,
    val y: Double,
    val z: Double
)

data class OrbitTargetCenter(
    val x: Double,
    val z: Double
)

class OrbitFollowState(baseAngularSpeed: Double) {
    var orbitalAngle: Double = 0.0
    var currentAngularSpeed: Double = baseAngularSpeed
    var transitionStartAngularSpeed: Double = baseAngularSpeed
    var targetAngularSpeed: Double = baseAngularSpeed
    var directionChangeTicks: Int = 0
    var directionTransitionTicksRemaining: Int = 0
    var smoothedY: Double = 0.0
    var hasSmoothedY: Boolean = false
    var delayedTargetUuid: UUID? = null
    val delayedTargetCenters: ArrayDeque<OrbitTargetCenter> = ArrayDeque()
    var previousTargetX: Double = 0.0
    var previousTargetZ: Double = 0.0
    var hasPreviousTarget: Boolean = false
    var stationaryTicks: Int = 0
    var movingTicks: Int = 0
    var isStationaryMode: Boolean = false
    var stationaryAnchorX: Double = 0.0
    var stationaryAnchorZ: Double = 0.0
    var stationaryBaseY: Double = 0.0
    var stationaryHoverPhase: Double = 0.0
    var resumeTicksRemaining: Int = 0
    var resumeStartRadius: Double = 0.0
}

object OrbitFollowController {
    private fun startResumeFromCurrent(
        state: OrbitFollowState,
        config: OrbitFollowConfig,
        followerX: Double,
        followerZ: Double,
        centerX: Double,
        centerZ: Double
    ) {
        state.isStationaryMode = false
        state.resumeTicksRemaining = config.directionTransitionTicks
        state.resumeStartRadius = hypot(followerX - centerX, followerZ - centerZ)
        state.orbitalAngle = atan2(followerZ - centerZ, followerX - centerX)
    }

    fun reanchorFromCurrentPosition(
        state: OrbitFollowState,
        config: OrbitFollowConfig,
        targetUuid: UUID,
        followerX: Double,
        followerZ: Double,
        targetX: Double,
        targetZ: Double
    ) {
        state.delayedTargetUuid = targetUuid
        state.delayedTargetCenters.clear()
        state.delayedTargetCenters.addLast(OrbitTargetCenter(targetX, targetZ))
        state.stationaryTicks = 0
        state.movingTicks = 0
        state.isStationaryMode = false
        startResumeFromCurrent(state, config, followerX, followerZ, targetX, targetZ)
    }

    fun clearTarget(state: OrbitFollowState) {
        state.hasSmoothedY = false
        state.delayedTargetUuid = null
        state.delayedTargetCenters.clear()
        state.hasPreviousTarget = false
        state.stationaryTicks = 0
        state.movingTicks = 0
        state.isStationaryMode = false
        state.resumeTicksRemaining = 0
        state.resumeStartRadius = 0.0
    }

    fun update(
        state: OrbitFollowState,
        config: OrbitFollowConfig,
        targetUuid: UUID,
        followerX: Double,
        followerZ: Double,
        targetX: Double,
        targetY: Double,
        targetZ: Double
    ): OrbitFollowOutput {
        if (state.delayedTargetUuid != targetUuid) {
            state.delayedTargetUuid = targetUuid
            state.delayedTargetCenters.clear()
            state.hasPreviousTarget = false
            state.stationaryTicks = 0
            state.movingTicks = 0
            state.isStationaryMode = false
            state.resumeTicksRemaining = 0
            state.resumeStartRadius = 0.0
        }

        val targetSpeed = if (state.hasPreviousTarget) {
            hypot(targetX - state.previousTargetX, targetZ - state.previousTargetZ)
        } else {
            0.0
        }
        state.previousTargetX = targetX
        state.previousTargetZ = targetZ
        state.hasPreviousTarget = true

        if (targetSpeed <= config.stationarySpeedThreshold) {
            state.stationaryTicks += 1
            state.movingTicks = 0
        } else {
            state.stationaryTicks = 0
            state.movingTicks += 1
        }

        if (!state.isStationaryMode && state.stationaryTicks >= config.stationaryEnterTicks) {
            state.isStationaryMode = true
            state.stationaryAnchorX = followerX
            state.stationaryAnchorZ = followerZ
            state.stationaryBaseY = state.smoothedY
            state.stationaryHoverPhase = 0.0
            state.resumeTicksRemaining = 0
        }
        if (state.isStationaryMode && state.movingTicks >= config.stationaryExitTicks) {
            startResumeFromCurrent(state, config, followerX, followerZ, targetX, targetZ)
        }

        state.delayedTargetCenters.addLast(OrbitTargetCenter(targetX, targetZ))
        val delayedCenter = if (state.delayedTargetCenters.size > config.targetPositionDelayTicks) {
            state.delayedTargetCenters.removeFirst()
        } else {
            state.delayedTargetCenters.first()
        }

        state.directionChangeTicks += 1
        if (state.directionChangeTicks >= config.directionChangeIntervalTicks) {
            state.directionChangeTicks = 0
            state.targetAngularSpeed = if (Random.nextBoolean()) config.baseAngularSpeed else -config.baseAngularSpeed
            state.transitionStartAngularSpeed = state.currentAngularSpeed
            state.directionTransitionTicksRemaining = config.directionTransitionTicks
        }

        if (state.directionTransitionTicksRemaining > 0 && config.directionTransitionTicks > 0) {
            val elapsedTicks = config.directionTransitionTicks - state.directionTransitionTicksRemaining + 1
            val t = elapsedTicks.toDouble() / config.directionTransitionTicks.toDouble()
            state.currentAngularSpeed =
                state.transitionStartAngularSpeed + (state.targetAngularSpeed - state.transitionStartAngularSpeed) * t
            state.directionTransitionTicksRemaining -= 1
        } else {
            state.currentAngularSpeed = state.targetAngularSpeed
        }
        state.orbitalAngle += state.currentAngularSpeed

        val desiredY = targetY + config.orbitHeightOffset
        if (!state.hasSmoothedY) {
            state.smoothedY = desiredY
            state.hasSmoothedY = true
            state.stationaryBaseY = desiredY
        } else {
            state.smoothedY += (desiredY - state.smoothedY) * config.yFollowLerpFactor
        }

        if (state.isStationaryMode) {
            state.stationaryBaseY += (state.smoothedY - state.stationaryBaseY) * config.yFollowLerpFactor
            state.stationaryHoverPhase += config.stationaryHoverAngularSpeed
            return OrbitFollowOutput(
                x = state.stationaryAnchorX,
                y = state.stationaryBaseY + sin(state.stationaryHoverPhase) * config.stationaryHoverAmplitude,
                z = state.stationaryAnchorZ
            )
        }

        if (state.resumeTicksRemaining > 0 && config.directionTransitionTicks > 0) {
            val dx = followerX - delayedCenter.x
            val dz = followerZ - delayedCenter.z
            state.orbitalAngle = atan2(dz, dx)

            val remainingRatio = state.resumeTicksRemaining.toDouble() / config.directionTransitionTicks.toDouble()
            state.currentAngularSpeed = state.targetAngularSpeed * (1.0 - remainingRatio)
            val currentRadius =
                state.resumeStartRadius * remainingRatio + config.orbitRadius * (1.0 - remainingRatio)
            state.resumeTicksRemaining -= 1

            return OrbitFollowOutput(
                x = delayedCenter.x + currentRadius * cos(state.orbitalAngle),
                y = state.smoothedY,
                z = delayedCenter.z + currentRadius * sin(state.orbitalAngle)
            )
        }

        return OrbitFollowOutput(
            x = delayedCenter.x + config.orbitRadius * cos(state.orbitalAngle),
            y = state.smoothedY,
            z = delayedCenter.z + config.orbitRadius * sin(state.orbitalAngle)
        )
    }
}
