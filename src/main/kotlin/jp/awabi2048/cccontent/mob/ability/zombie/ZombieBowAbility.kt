package jp.awabi2048.cccontent.mob.ability.zombie

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.ability.MobAbility
import jp.awabi2048.cccontent.mob.ability.MobAbilityRuntime
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.roundToLong
import kotlin.random.Random

class ZombieBowAbility : MobAbility {
    override val id: String = "zombie_bow"

    data class Runtime(var shotCooldownTicks: Long = 0L) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime()
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        if (abilityRuntime.shotCooldownTicks > 0L) {
            abilityRuntime.shotCooldownTicks -= 10L
        }

        if (!context.isCombatActive()) return

        val loadSnapshot = context.loadSnapshot
        if (context.activeMob.tickCount % (10L * loadSnapshot.searchIntervalMultiplier.toLong()) != 0L) {
            return
        }

        val entity = context.entity
        val target = resolveTarget(entity) ?: return

        val distanceSquared = entity.location.distanceSquared(target.location)
        if (distanceSquared >= BOW_MODE_MIN_DISTANCE_SQUARED) {
            faceTowards(entity, target)
            if (!entity.hasLineOfSight(target)) {
                return
            }
            if (Random.nextDouble() < loadSnapshot.abilityExecutionSkipChance) {
                return
            }
            if (abilityRuntime.shotCooldownTicks <= 0L) {
                shootArrow(context, entity, target)
                abilityRuntime.shotCooldownTicks =
                    (BOW_SHOT_COOLDOWN_TICKS * loadSnapshot.abilityCooldownMultiplier).roundToLong()
                        .coerceAtLeast(BOW_SHOT_COOLDOWN_TICKS)
            }
            return
        }
    }

    private fun resolveTarget(entity: LivingEntity): LivingEntity? {
        val target = (entity as? Mob)?.target as? LivingEntity ?: return null
        if (!target.isValid || target.isDead) {
            return null
        }
        return target
    }

    private fun faceTowards(entity: LivingEntity, target: LivingEntity) {
        val direction = target.eyeLocation.toVector().subtract(entity.eyeLocation.toVector())
        if (direction.lengthSquared() < 0.0001) return

        val normalized = direction.normalize()
        val yaw = Math.toDegrees(atan2(-normalized.x, normalized.z)).toFloat()
        val pitch = Math.toDegrees(-asin(normalized.y)).toFloat()
        entity.setRotation(yaw, pitch)
    }

    private fun shootArrow(context: MobRuntimeContext, entity: LivingEntity, target: LivingEntity) {
        val source = entity.eyeLocation
        val predictedTarget = target.eyeLocation.add(target.velocity.clone().multiply(TARGET_PREDICTION_SCALE))
        val direction = predictedTarget.toVector().subtract(source.toVector())
        if (direction.lengthSquared() < 0.0001) return

        val arrow = entity.launchProjectile(Arrow::class.java)
        arrow.shooter = entity
        arrow.velocity = direction.normalize().multiply(ARROW_SPEED)
        arrow.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED
        arrow.isCritical = false

        entity.world.spawnParticle(Particle.CRIT, source, 5, 0.08, 0.08, 0.08, 0.02)
        entity.world.playSound(entity.location, Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.0f)
        MobService.getInstance(context.plugin)?.recordProjectileLaunch(context.sessionKey)
    }

    private companion object {
        const val BOW_MODE_MIN_DISTANCE_SQUARED = 36.0
        const val BOW_SHOT_COOLDOWN_TICKS = 30L
        const val ARROW_SPEED = 2.0
        const val TARGET_PREDICTION_SCALE = 0.4
    }
}
