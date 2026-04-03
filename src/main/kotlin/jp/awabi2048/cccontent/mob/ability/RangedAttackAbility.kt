package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobAttackContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.potion.PotionEffectType
import kotlin.math.roundToLong
import kotlin.random.Random

class RangedAttackAbility(
    override val id: String,
    private val arrowSpeedMultiplier: Double = 1.0,
    private val intervalMultiplier: Double = 1.0,
    private val effectArrowChance: Double = 0.0,
    private val effectArrowAmplifier: Int = 0,
    private val effectArrowType: PotionEffectType? = null,
    private val confusionArrowChance: Double = 0.0,
    private val confusionDurationTicks: Int = 200,
    private val retreatOnCloseRange: Boolean = false,
    private val retreatMinDistanceSquared: Double = DEFAULT_RETREAT_MIN_DISTANCE_SQUARED,
    private val retreatSpeed: Double = DEFAULT_RETREAT_SPEED,
    private val rangedWeaponTypes: Set<Material> = setOf(Material.BOW, Material.CROSSBOW),
    private val homingConfig: MobShootUtil.HomingConfig? = null
) : MobAbility {

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

        shootArrow(context, entity, target)
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

    private fun shootArrow(context: MobRuntimeContext, entity: LivingEntity, target: LivingEntity) {
        MobShootUtil.shootArrow(
            plugin = context.plugin,
            entity = entity,
            target = target,
            activeMob = context.activeMob,
            speedMultiplier = arrowSpeedMultiplier,
            effectArrowChance = effectArrowChance,
            effectArrowAmplifier = effectArrowAmplifier,
            effectArrowType = effectArrowType,
            confusionArrowChance = confusionArrowChance,
            confusionDurationTicks = confusionDurationTicks,
            homingConfig = homingConfig
        )
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
    }
}
