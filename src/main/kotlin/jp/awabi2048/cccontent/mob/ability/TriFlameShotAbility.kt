package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobAttackContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

class TriFlameShotAbility(
    override val id: String,
    private val spreadAngleDegrees: Double = 10.0,
    private val arrowSpeedMultiplier: Double = 1.0,
    private val intervalMultiplier: Double = 1.0,
    private val fireTicks: Int = 100,
    private val retreatOnCloseRange: Boolean = false,
    private val retreatMinDistanceSquared: Double = 36.0,
    private val retreatSpeed: Double = 0.22,
    private val rangedWeaponTypes: Set<Material> = setOf(Material.BOW, Material.CROSSBOW)
) : MobAbility, CustomRangedAttackAbility {

    data class Runtime(
        var shotCooldownTicks: Long = 0L,
        var aimTicks: Long = 0L,
        var lostSightTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        if (abilityRuntime.shotCooldownTicks > 0L) {
            abilityRuntime.shotCooldownTicks = (abilityRuntime.shotCooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }

        if (!context.isCombatActive()) {
            resetAimState(abilityRuntime)
            return
        }

        val loadSnapshot = context.loadSnapshot
        if (!MobAbilityUtils.shouldProcessSearchTick(context.activeMob.tickCount, loadSnapshot.searchIntervalMultiplier, abilityRuntime.searchPhaseOffsetSteps)) {
            return
        }

        val entity = context.entity
        val target = MobAbilityUtils.resolveTarget(entity) ?: run {
            resetAimState(abilityRuntime)
            return
        }

        val distanceSquared = entity.location.distanceSquared(target.location)
        if (retreatOnCloseRange && distanceSquared < retreatMinDistanceSquared) {
            if (isHoldingRangedWeapon(entity)) {
                MobAbilityUtils.faceTowards(entity, target)
                retreatFromTarget(entity, target)
            }
            resetAimState(abilityRuntime)
            return
        }

        MobAbilityUtils.faceTowards(entity, target)

        if (!entity.hasLineOfSight(target)) {
            abilityRuntime.lostSightTicks += context.tickDelta
            if (abilityRuntime.lostSightTicks >= AIM_RESET_LOST_SIGHT_TICKS) {
                resetAimState(abilityRuntime)
            }
            return
        }
        abilityRuntime.lostSightTicks = 0L

        if (abilityRuntime.shotCooldownTicks > 0L) {
            return
        }

        abilityRuntime.aimTicks += context.tickDelta
        val requiredAimTicks = calculateRequiredAimTicks(distanceSquared)
        if (abilityRuntime.aimTicks < requiredAimTicks) {
            return
        }

        if (Random.nextDouble() < loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        shootTriVolley(context, entity, target)
        val baseCooldown = (BOW_SHOT_COOLDOWN_TICKS * intervalMultiplier).roundToLong().coerceAtLeast(8L)
        abilityRuntime.shotCooldownTicks =
            (baseCooldown * loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
        abilityRuntime.aimTicks = 0L
    }

    override fun onAttack(context: MobAttackContext, runtime: MobAbilityRuntime?) {
        if (!isDirectMeleeAttack(context.event, context.entity)) {
            return
        }
        val mainHandType = context.entity.equipment?.itemInMainHand?.type ?: return
        if (mainHandType in rangedWeaponTypes) {
            context.event.damage = 0.0
        }
    }

    private fun shootTriVolley(context: MobRuntimeContext, entity: LivingEntity, target: LivingEntity) {
        val source = entity.eyeLocation
        val distance = source.distance(target.eyeLocation)
        val predictionScale = (0.3 + distance * 0.015).coerceIn(0.25, 0.75)
        val predictedTarget = target.eyeLocation.add(target.velocity.clone().multiply(predictionScale))
        val baseDirection = predictedTarget.toVector().subtract(source.toVector())
        baseDirection.y += distance * 0.03
        if (baseDirection.lengthSquared() < 0.0001) {
            return
        }

        val spread = calculateSpread(distance)
        baseDirection.x += Random.nextDouble(-spread, spread)
        baseDirection.y += Random.nextDouble(-spread * 0.6, spread * 0.6)
        baseDirection.z += Random.nextDouble(-spread, spread)

        val baseArrowSpeed = calculateArrowSpeed(distance)
        val finalSpeed = baseArrowSpeed * arrowSpeedMultiplier

        val baseNormalized = baseDirection.normalize()
        val yawRadians = Math.toRadians(spreadAngleDegrees)
        val directions = listOf(
            rotateAroundY(baseNormalized, -yawRadians),
            baseNormalized,
            rotateAroundY(baseNormalized, yawRadians)
        )

        directions.forEach { direction ->
            val projectile = entity.launchProjectile(Arrow::class.java).apply {
                shooter = entity
            }
            projectile.velocity = direction.multiply(finalSpeed)
            projectile.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED
            projectile.isCritical = true
            projectile.fireTicks = fireTicks
        }

        entity.world.spawnParticle(Particle.CRIT, source, 7, 0.1, 0.1, 0.1, 0.02)
        entity.world.playSound(entity.location, Sound.ENTITY_SKELETON_SHOOT, 1.0f, 0.9f)
        MobService.getInstance(context.plugin)?.recordProjectileLaunch(context.activeMob.sessionKey)
    }

    private fun rotateAroundY(vector: Vector, angle: Double): Vector {
        val c = cos(angle)
        val s = sin(angle)
        val x = vector.x * c - vector.z * s
        val z = vector.x * s + vector.z * c
        return Vector(x, vector.y, z).normalize()
    }

    private fun resetAimState(runtime: Runtime) {
        runtime.aimTicks = 0L
        runtime.lostSightTicks = 0L
    }

    private fun calculateRequiredAimTicks(distanceSquared: Double): Long {
        val base = when {
            distanceSquared < 100.0 -> 12L
            distanceSquared < 256.0 -> 22L
            else -> 30L
        }
        return (base * intervalMultiplier).roundToLong().coerceAtLeast(6L)
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

    private fun isHoldingRangedWeapon(entity: LivingEntity): Boolean {
        return entity.equipment?.itemInMainHand?.type in rangedWeaponTypes
    }

    private fun retreatFromTarget(entity: LivingEntity, target: LivingEntity) {
        val retreatDirection = entity.location.toVector().subtract(target.location.toVector()).setY(0.0)
        if (retreatDirection.lengthSquared() < 0.0001) {
            return
        }

        val retreatVelocity = retreatDirection.normalize().multiply(retreatSpeed)
        entity.velocity = entity.velocity.setX(retreatVelocity.x).setZ(retreatVelocity.z)
    }

    private fun isDirectMeleeAttack(event: EntityDamageByEntityEvent, attacker: LivingEntity): Boolean {
        val damager = event.damager as? LivingEntity ?: return false
        return damager.uniqueId == attacker.uniqueId
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
        const val BOW_SHOT_COOLDOWN_TICKS = 32L
        const val AIM_RESET_LOST_SIGHT_TICKS = 20L
    }
}
