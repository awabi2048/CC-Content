package jp.awabi2048.cccontent.mob.ability.zombie

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.MobAttackContext
import jp.awabi2048.cccontent.mob.ability.MobAbility
import jp.awabi2048.cccontent.mob.ability.MobAbilityRuntime
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.event.entity.EntityDamageByEntityEvent
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random

class ZombieBowAbility : MobAbility {
    override val id: String = "zombie_bow"

    data class Runtime(
        var shotCooldownTicks: Long = 0L,
        var aimTicks: Long = 0L,
        var lostSightTicks: Long = 0L
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime()
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        if (abilityRuntime.shotCooldownTicks > 0L) {
            abilityRuntime.shotCooldownTicks -= 10L
        }

        if (!context.isCombatActive()) {
            resetAimState(abilityRuntime)
            return
        }

        val loadSnapshot = context.loadSnapshot
        if (context.activeMob.tickCount % (10L * loadSnapshot.searchIntervalMultiplier.toLong()) != 0L) {
            return
        }

        val entity = context.entity
        val target = resolveTarget(entity) ?: run {
            resetAimState(abilityRuntime)
            return
        }

        val distanceSquared = entity.location.distanceSquared(target.location)
        if (distanceSquared < BOW_MODE_MIN_DISTANCE_SQUARED) {
            if (isHoldingBow(entity)) {
                faceTowards(entity, target)
                retreatFromTarget(entity, target)
            }
            resetAimState(abilityRuntime)
            return
        }

        faceTowards(entity, target)
        if (!entity.hasLineOfSight(target)) {
            abilityRuntime.lostSightTicks += 10L
            if (abilityRuntime.lostSightTicks >= AIM_RESET_LOST_SIGHT_TICKS) {
                resetAimState(abilityRuntime)
            }
            return
        }
        abilityRuntime.lostSightTicks = 0L

        if (abilityRuntime.shotCooldownTicks > 0L) {
            return
        }

        abilityRuntime.aimTicks += 10L
        val requiredAimTicks = calculateRequiredAimTicks(distanceSquared)
        if (abilityRuntime.aimTicks < requiredAimTicks) {
            return
        }

        if (Random.nextDouble() < loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        shootArrow(context, entity, target, distanceSquared)
        abilityRuntime.shotCooldownTicks =
            (BOW_SHOT_COOLDOWN_TICKS * loadSnapshot.abilityCooldownMultiplier).roundToLong()
                .coerceAtLeast(BOW_SHOT_COOLDOWN_TICKS)
        abilityRuntime.aimTicks = 0L
    }

    override fun onAttack(context: MobAttackContext, runtime: MobAbilityRuntime?) {
        if (!isDirectMeleeAttack(context.event, context.entity)) {
            return
        }
        val mainHandType = context.entity.equipment?.itemInMainHand?.type ?: return
        if (mainHandType == Material.BOW) {
            context.event.isCancelled = true
            context.event.damage = 0.0
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

    private fun resetAimState(runtime: Runtime) {
        runtime.aimTicks = 0L
        runtime.lostSightTicks = 0L
    }

    private fun calculateRequiredAimTicks(distanceSquared: Double): Long {
        return when {
            distanceSquared < 100.0 -> 10L
            distanceSquared < 256.0 -> 20L
            else -> 30L
        }
    }

    private fun shootArrow(context: MobRuntimeContext, entity: LivingEntity, target: LivingEntity, distanceSquared: Double) {
        val source = entity.eyeLocation
        val distance = sqrt(distanceSquared)
        val predictionScale = (TARGET_PREDICTION_BASE + distance * TARGET_PREDICTION_DISTANCE_SCALE)
            .coerceIn(0.25, 0.75)
        val predictedTarget = target.eyeLocation.add(target.velocity.clone().multiply(predictionScale))
        val direction = predictedTarget.toVector().subtract(source.toVector())
        direction.y += distance * GRAVITY_COMPENSATION_PER_BLOCK
        if (direction.lengthSquared() < 0.0001) return

        val spread = calculateSpread(distance)
        direction.x += Random.nextDouble(-spread, spread)
        direction.y += Random.nextDouble(-spread * 0.6, spread * 0.6)
        direction.z += Random.nextDouble(-spread, spread)

        val arrow = entity.launchProjectile(Arrow::class.java)
        arrow.shooter = entity
        arrow.velocity = direction.normalize().multiply(calculateArrowSpeed(distance))
        arrow.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED
        arrow.isCritical = false

        entity.world.spawnParticle(Particle.CRIT, source, 5, 0.08, 0.08, 0.08, 0.02)
        entity.world.playSound(entity.location, Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.0f)
        MobService.getInstance(context.plugin)?.recordProjectileLaunch(context.sessionKey)
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

    private fun isHoldingBow(entity: LivingEntity): Boolean {
        return entity.equipment?.itemInMainHand?.type == Material.BOW
    }

    private fun retreatFromTarget(entity: LivingEntity, target: LivingEntity) {
        val retreatDirection = entity.location.toVector().subtract(target.location.toVector()).setY(0.0)
        if (retreatDirection.lengthSquared() < 0.0001) {
            return
        }

        val retreatVelocity = retreatDirection.normalize().multiply(RETREAT_SPEED)
        entity.velocity = entity.velocity.setX(retreatVelocity.x).setZ(retreatVelocity.z)
    }

    private fun isDirectMeleeAttack(event: EntityDamageByEntityEvent, attacker: LivingEntity): Boolean {
        val damager = event.damager as? LivingEntity ?: return false
        return damager.uniqueId == attacker.uniqueId
    }

    private companion object {
        const val BOW_MODE_MIN_DISTANCE_SQUARED = 36.0
        const val BOW_SHOT_COOLDOWN_TICKS = 30L
        const val AIM_RESET_LOST_SIGHT_TICKS = 20L
        const val TARGET_PREDICTION_BASE = 0.3
        const val TARGET_PREDICTION_DISTANCE_SCALE = 0.015
        const val GRAVITY_COMPENSATION_PER_BLOCK = 0.03
        const val RETREAT_SPEED = 0.22
    }
}
