@file:Suppress("USELESS_CAST")

package jp.awabi2048.cccontent.mob.ability

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import kotlin.math.asin
import kotlin.math.atan2

object MobAbilityUtils {
    fun resolveTarget(entity: LivingEntity): LivingEntity? {
        val target = (entity as? Mob)?.target as? LivingEntity ?: return null
        if (!target.isValid || target.isDead) {
            return null
        }
        return target
    }

    fun faceTowards(entity: LivingEntity, target: LivingEntity) {
        val direction = target.eyeLocation.toVector().subtract(entity.eyeLocation.toVector())
        if (direction.lengthSquared() < 0.0001) return

        val normalized = direction.normalize()
        val yaw = Math.toDegrees(atan2(-normalized.x, normalized.z)).toFloat()
        val pitch = Math.toDegrees(-asin(normalized.y)).toFloat()
        entity.setRotation(yaw, pitch)
    }

    fun shouldProcessSearchTick(
        tickCount: Long,
        searchIntervalMultiplier: Int,
        phaseOffsetSteps: Int,
        baseStepTicks: Long = 10L
    ): Boolean {
        val intervalSteps = searchIntervalMultiplier.coerceAtLeast(1).toLong()
        val stepTicks = baseStepTicks.coerceAtLeast(1L)
        val currentStep = (tickCount / stepTicks) + phaseOffsetSteps.toLong()
        return currentStep % intervalSteps == 0L
    }
}
