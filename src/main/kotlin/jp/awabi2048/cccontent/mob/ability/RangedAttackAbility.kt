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
import org.bukkit.entity.TippedArrow
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random

class RangedAttackAbility(
    override val id: String,
    private val arrowSpeedMultiplier: Double = 1.0,
    private val intervalMultiplier: Double = 1.0,
    private val effectArrowChance: Double = 0.0,
    private val retreatOnCloseRange: Boolean = false,
    private val retreatMinDistanceSquared: Double = DEFAULT_RETREAT_MIN_DISTANCE_SQUARED,
    private val retreatSpeed: Double = DEFAULT_RETREAT_SPEED,
    private val rangedWeaponTypes: Set<Material> = setOf(Material.BOW, Material.CROSSBOW)
) : MobAbility {

    data class Runtime(
        var shotCooldownTicks: Long = 0L,
        var aimTicks: Long = 0L,
        var lostSightTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    private data class EffectArrowSpec(
        val type: PotionEffectType,
        val durationTicks: Int,
        val amplifier: Int
    )

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
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

    private fun resetAimState(runtime: Runtime) {
        runtime.aimTicks = 0L
        runtime.lostSightTicks = 0L
    }

    private fun calculateRequiredAimTicks(distanceSquared: Double): Long {
        val base = when {
            distanceSquared < 100.0 -> 10L
            distanceSquared < 256.0 -> 20L
            else -> 30L
        }
        return (base * intervalMultiplier).roundToLong().coerceAtLeast(6L)
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

        val arrowSpeed = calculateArrowSpeed(distance) * arrowSpeedMultiplier
        val effectSpec = if (Random.nextDouble() < effectArrowChance) buildEffectArrowSpec() else null
        val projectile = if (effectSpec != null) {
            entity.launchProjectile(TippedArrow::class.java).apply {
                shooter = entity
                addCustomEffect(
                    PotionEffect(effectSpec.type, effectSpec.durationTicks, effectSpec.amplifier, false, true, true),
                    true
                )
            }
        } else {
            entity.launchProjectile(Arrow::class.java).apply {
                shooter = entity
            }
        }
        projectile.velocity = direction.normalize().multiply(arrowSpeed)
        projectile.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED
        projectile.isCritical = false
        if (effectSpec != null) {
            MobService.getInstance(context.plugin)?.markSkeletonEffectArrow(
                projectile,
                effectSpec.type.name,
                effectSpec.amplifier,
                effectSpec.durationTicks
            )
        }

        entity.world.spawnParticle(Particle.CRIT, source, 5, 0.08, 0.08, 0.08, 0.02)
        entity.world.playSound(entity.location, Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.0f)
        MobService.getInstance(context.plugin)?.recordProjectileLaunch(context.sessionKey)
    }

    private fun buildEffectArrowSpec(): EffectArrowSpec {
        val effectType = when (Random.nextInt(3)) {
            0 -> PotionEffectType.POISON
            1 -> PotionEffectType.WITHER
            else -> PotionEffectType.SLOWNESS
        }
        return EffectArrowSpec(effectType, EFFECT_ARROW_DURATION_TICKS, 0)
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

    companion object {
        const val DEFAULT_EFFECT_ARROW_CHANCE = 0.25
        const val DEFAULT_RAPID_INTERVAL_MULTIPLIER = 0.70
        const val DEFAULT_FAST_ARROW_SPEED_MULTIPLIER = 1.35
        const val DEFAULT_RETREAT_MIN_DISTANCE_SQUARED = 36.0
        const val DEFAULT_RETREAT_SPEED = 0.22

        private const val SEARCH_PHASE_VARIANTS = 16
        private const val BOW_SHOT_COOLDOWN_TICKS = 30L
        private const val AIM_RESET_LOST_SIGHT_TICKS = 20L
        private const val TARGET_PREDICTION_BASE = 0.3
        private const val TARGET_PREDICTION_DISTANCE_SCALE = 0.015
        private const val GRAVITY_COMPENSATION_PER_BLOCK = 0.03
        private const val EFFECT_ARROW_DURATION_TICKS = 200
    }
}
