package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.util.Vector
import kotlin.math.roundToLong
import kotlin.random.Random

class BackstepAbility(
    override val id: String,
    private val triggerDistance: Double = DEFAULT_TRIGGER_DISTANCE,
    private val triggerDurationTicks: Long = DEFAULT_TRIGGER_DURATION_TICKS,
    private val cooldownTicks: Long = DEFAULT_COOLDOWN_TICKS,
    private val horizontalSpeed: Double = DEFAULT_HORIZONTAL_SPEED,
    private val verticalSpeed: Double = DEFAULT_VERTICAL_SPEED,
    private val postStepFaceTicks: Long = DEFAULT_POST_STEP_FACE_TICKS,
    private val obstacleCheckTicks: Int = DEFAULT_OBSTACLE_CHECK_TICKS,
    private val shootArrowOnLand: Boolean = false,
    private val arrowSpeedMultiplier: Double = DEFAULT_ARROW_SPEED_MULTIPLIER,
    private val homingConfig: MobShootUtil.HomingConfig? = null
) : MobAbility {
    data class Runtime(
        var cooldownTicks: Long = 0L,
        var closeRangeTicks: Long = 0L,
        var postStepFaceTicks: Long = 0L,
        var postStepTargetId: java.util.UUID? = null,
        var searchPhaseOffsetSteps: Int = 0,
        var wasInAir: Boolean = false
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        keepFacingDuringAir(context, abilityRuntime)

        if (abilityRuntime.cooldownTicks > 0L) {
            abilityRuntime.cooldownTicks = (abilityRuntime.cooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }

        if (!context.isCombatActive()) {
            abilityRuntime.closeRangeTicks = 0L
            return
        }

        if (!MobAbilityUtils.shouldProcessSearchTick(context.activeMob.tickCount, context.loadSnapshot.searchIntervalMultiplier, abilityRuntime.searchPhaseOffsetSteps)) {
            return
        }
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        val entity = context.entity
        val target = MobAbilityUtils.resolveTarget(entity) ?: run {
            abilityRuntime.closeRangeTicks = 0L
            return
        }
        val effectiveTriggerDistance = if (triggerDistance <= 0.0) getInteractionRange(target) else triggerDistance
        val distanceSquared = entity.location.distanceSquared(target.location)
        val triggerDistanceSquared = effectiveTriggerDistance * effectiveTriggerDistance
        if (distanceSquared > triggerDistanceSquared) {
            abilityRuntime.closeRangeTicks = 0L
            return
        }
        abilityRuntime.closeRangeTicks = triggerDurationTicks

        if (abilityRuntime.cooldownTicks > 0L) return
        if (!entity.isOnGround) return

        val away = entity.location.toVector().subtract(target.location.toVector()).setY(0.0)
        if (away.lengthSquared() < 0.0001) return
        val launch = away.normalize().multiply(horizontalSpeed).setY(verticalSpeed)

        if (!isBackstepPathClear(entity.location, launch, obstacleCheckTicks)) {
            return
        }

        MobAbilityUtils.faceTowards(entity, target)
        entity.velocity = launch
        entity.world.playSound(entity.location, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.8f, 1.2f)

        abilityRuntime.postStepFaceTicks = postStepFaceTicks
        abilityRuntime.postStepTargetId = target.uniqueId
        abilityRuntime.closeRangeTicks = 0L

        abilityRuntime.cooldownTicks =
            (cooldownTicks * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(cooldownTicks)
    }

    private fun keepFacingDuringAir(context: MobRuntimeContext, runtime: Runtime) {
        if (runtime.postStepFaceTicks <= 0L) {
            runtime.wasInAir = false
            return
        }
        val entity = context.entity
        if (entity.isOnGround) {
            if (runtime.wasInAir && shootArrowOnLand) {
                fireLandingArrow(context, runtime)
            }
            runtime.postStepFaceTicks = 0L
            runtime.postStepTargetId = null
            runtime.wasInAir = false
            return
        }

        runtime.wasInAir = true
        val targetId = runtime.postStepTargetId ?: return
        val target = Bukkit.getEntity(targetId) as? LivingEntity
        if (target == null || !target.isValid || target.isDead || target.world.uid != entity.world.uid) {
            runtime.postStepFaceTicks = 0L
            runtime.postStepTargetId = null
            runtime.wasInAir = false
            return
        }

        MobAbilityUtils.faceTowards(entity, target)
        runtime.postStepFaceTicks = (runtime.postStepFaceTicks - context.tickDelta).coerceAtLeast(0L)
    }

    private fun fireLandingArrow(context: MobRuntimeContext, runtime: Runtime) {
        val targetId = runtime.postStepTargetId ?: return
        val entity = context.entity
        val target = Bukkit.getEntity(targetId) as? LivingEntity
        if (target == null || !target.isValid || target.isDead || target.world.uid != entity.world.uid) return

        MobShootUtil.shootArrow(
            plugin = context.plugin,
            entity = entity,
            target = target,
            activeMob = context.activeMob,
            speedMultiplier = arrowSpeedMultiplier,
            homingConfig = homingConfig
        )
    }

    private fun getInteractionRange(entity: LivingEntity): Double {
        return try {
            entity.javaClass.getMethod("getInteractionRange").invoke(entity) as? Double ?: DEFAULT_FALLBACK_DISTANCE
        } catch (_: Exception) {
            DEFAULT_FALLBACK_DISTANCE
        }
    }

    private fun isBackstepPathClear(start: Location, initialVelocity: Vector, simulateTicks: Int): Boolean {
        val world = start.world ?: return false
        var current = start.clone().add(0.0, 0.1, 0.0)
        var velocity = initialVelocity.clone()
        var landingCandidate = current.clone()

        repeat(simulateTicks.coerceAtLeast(1)) {
            val next = current.clone().add(velocity)
            if (collidesOnSegment(world, current, next)) {
                return false
            }

            landingCandidate = next
            current = next
            velocity = velocity.multiply(0.98)
            velocity.y -= 0.08
        }

        val below = landingCandidate.clone().add(0.0, -0.2, 0.0).block
        val feet = landingCandidate.block
        val head = landingCandidate.clone().add(0.0, 1.0, 0.0).block
        if (!below.type.isSolid) return false
        if (!feet.isPassable) return false
        if (!head.isPassable) return false
        return true
    }

    private fun collidesOnSegment(world: org.bukkit.World, from: Location, to: Location): Boolean {
        val segment = to.toVector().subtract(from.toVector())
        val length = segment.length()
        if (length <= 0.0001) return false
        val direction = segment.clone().normalize()

        val lowFrom = from.clone().add(0.0, 0.15, 0.0)
        val highFrom = from.clone().add(0.0, 1.2, 0.0)
        val lowHit = world.rayTraceBlocks(lowFrom, direction, length, FluidCollisionMode.NEVER, true)
        val highHit = world.rayTraceBlocks(highFrom, direction, length, FluidCollisionMode.NEVER, true)
        return lowHit != null || highHit != null
    }

    companion object {
        const val DEFAULT_TRIGGER_DISTANCE = 0.0
        const val DEFAULT_TRIGGER_DURATION_TICKS = 40L
        const val DEFAULT_COOLDOWN_TICKS = 80L
        const val DEFAULT_HORIZONTAL_SPEED = 0.9
        const val DEFAULT_VERTICAL_SPEED = 0.45
        const val DEFAULT_POST_STEP_FACE_TICKS = 20L
        const val DEFAULT_OBSTACLE_CHECK_TICKS = 12
        const val DEFAULT_ARROW_SPEED_MULTIPLIER = 1.0
        private const val DEFAULT_FALLBACK_DISTANCE = 3.0
        private const val SEARCH_PHASE_VARIANTS = 16
    }
}
