@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.ActiveMob
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.EntityType
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.TippedArrow
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.random.Random

object MobShootUtil {

    data class HomingConfig(
        val accuracy: Double = 0.75,
        val turnStrength: Double = 0.15,
        val maxTurnDegrees: Double = 12.0
    )

    fun shootArrow(
        plugin: JavaPlugin,
        entity: LivingEntity,
        target: LivingEntity,
        activeMob: ActiveMob,
        speedMultiplier: Double = 1.0,
        effectArrowChance: Double = 0.0,
        homingConfig: HomingConfig? = null,
        arrowSpeed: Double? = null,
        effectArrowAmplifier: Int = 0,
        effectArrowType: PotionEffectType? = null,
        confusionArrowChance: Double = 0.0,
        confusionDurationTicks: Int = 200
    ) {
        val source = entity.eyeLocation
        val distance = source.distance(target.eyeLocation)
        val predictionScale = (0.3 + distance * 0.015).coerceIn(0.25, 0.75)
        val predictedTarget = target.eyeLocation.add(target.velocity.clone().multiply(predictionScale))

        val direction = predictedTarget.toVector().subtract(source.toVector())
        direction.y += distance * 0.03
        if (direction.lengthSquared() < 0.0001) return

        val spread = calculateSpread(distance)
        direction.x += Random.nextDouble(-spread, spread)
        direction.y += Random.nextDouble(-spread * 0.6, spread * 0.6)
        direction.z += Random.nextDouble(-spread, spread)

        val baseArrowSpeed = arrowSpeed ?: calculateArrowSpeed(distance)
        val finalSpeed = baseArrowSpeed * speedMultiplier

        val roll = Random.nextDouble()
        val effectSpec = when {
            roll < effectArrowChance -> {
                if (effectArrowType != null) EffectArrowSpec(effectArrowType, 200, effectArrowAmplifier)
                else buildEffectArrowSpec()
            }
            roll < effectArrowChance + confusionArrowChance -> null
            else -> null
        }
        val isConfusionArrow = roll >= effectArrowChance && roll < effectArrowChance + confusionArrowChance

        val projectile = entity.launchProjectile(Arrow::class.java).apply {
            shooter = entity
        }
        MobService.getInstance(plugin)?.markMobShotArrow(projectile)
        if (entity.type == EntityType.WITHER_SKELETON) {
            projectile.fireTicks = maxOf(projectile.fireTicks, 100)
        }
        if (effectSpec != null) {
            (projectile as? TippedArrow)?.addCustomEffect(
                PotionEffect(effectSpec.type, effectSpec.durationTicks, effectSpec.amplifier, false, true, true),
                true
            )
        }
        projectile.velocity = direction.normalize().multiply(finalSpeed)
        projectile.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED
        projectile.isCritical = true

        if (homingConfig != null) {
            HomingArrowService.getInstance(plugin).launch(
                HomingArrowService.LaunchSpec(
                    arrowId = projectile.uniqueId,
                    targetId = target.uniqueId,
                    accuracy = homingConfig.accuracy,
                    turnStrength = homingConfig.turnStrength,
                    maxTurnDegrees = homingConfig.maxTurnDegrees
                )
            )
        }

        if (effectSpec != null) {
            MobService.getInstance(plugin)?.markSkeletonEffectArrow(
                projectile,
                effectSpec.type.name,
                effectSpec.amplifier,
                effectSpec.durationTicks
            )
        } else if (isConfusionArrow) {
            MobService.getInstance(plugin)?.markSkeletonEffectArrow(
                projectile,
                CONFUSION_EFFECT_NAME,
                confusionDurationTicks,
                200
            )
        }

        entity.world.spawnParticle(Particle.CRIT, source, 5, 0.08, 0.08, 0.08, 0.02)
        entity.world.playSound(entity.location, Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.0f)
        MobService.getInstance(plugin)?.recordProjectileLaunch(activeMob.sessionKey)
    }

    private fun calculateArrowSpeed(distance: Double): Double {
        return when {
            distance < 8.0 -> 1.85
            distance < 16.0 -> 2.0
            else -> 2.15
        }
    }

    private fun calculateSpread(distance: Double): Double {
        return when {
            distance < 8.0 -> 0.012
            distance < 16.0 -> 0.02
            else -> 0.03
        }
    }

    private data class EffectArrowSpec(
        val type: PotionEffectType,
        val durationTicks: Int,
        val amplifier: Int
    )

    private fun buildEffectArrowSpec(): EffectArrowSpec {
        val effectType = when (Random.nextInt(3)) {
            0 -> PotionEffectType.POISON
            1 -> PotionEffectType.WITHER
            else -> PotionEffectType.SLOWNESS
        }
        return EffectArrowSpec(effectType, 200, 0)
    }

    const val CONFUSION_EFFECT_NAME = "CONFUSION"
}
