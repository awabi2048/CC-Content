package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.util.Vector
import kotlin.math.roundToLong
import kotlin.random.Random

class DashToTargetOffsetAbility(
    override val id: String,
    private val cooldownTicks: Long = 90L,
    private val triggerMinDistance: Double = 10.0,
    private val dashSpeed: Double = 1.55,
    private val dashVerticalSpeed: Double = 0.15,
    private val hoverHeight: Double = 3.0,
    private val hoverDurationTicks: Long = 10L,
    private val maxDashDurationTicks: Long = 24L,
    private val destinationReachDistance: Double = 1.35,
    private val stabilizationSpeedThreshold: Double = 0.18,
    private val stabilizationRequiredTicks: Long = 6L,
    private val explosionPower: Float = 2.2f,
    private val requireLineOfSight: Boolean = true
) : MobAbility {

    override fun tickIntervalTicks(): Long = 1L

    data class Runtime(
        var cooldownTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0,
        var isDashing: Boolean = false,
        var hovering: Boolean = false,
        var hoverTicksRemaining: Long = 0L,
        var hoverStartY: Double = 0.0,
        var stabilizationTicks: Long = 0L,
        var destination: org.bukkit.Location? = null,
        var targetId: java.util.UUID? = null,
        var dashEndTick: Long = 0L
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        if (rt.cooldownTicks > 0L) {
            rt.cooldownTicks = (rt.cooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }

        if (!context.isCombatActive()) {
            rt.isDashing = false
            rt.hovering = false
            rt.hoverTicksRemaining = 0L
            rt.stabilizationTicks = 0L
            rt.destination = null
            rt.targetId = null
            return
        }

        if (rt.isDashing) {
            processDashing(context, rt)
            return
        }

        if (rt.cooldownTicks > 0L) {
            return
        }
        if (!MobAbilityUtils.shouldProcessSearchTick(
                context.activeMob.tickCount,
                context.loadSnapshot.searchIntervalMultiplier,
                rt.searchPhaseOffsetSteps,
                baseStepTicks = 1L
            )) {
            return
        }
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        val entity = context.entity
        val target = MobAbilityUtils.resolveTarget(entity) ?: return
        val distance = entity.location.distance(target.location)
        if (distance < triggerMinDistance) {
            return
        }
        if (requireLineOfSight && !entity.hasLineOfSight(target)) {
            return
        }

        val destination = resolveDestination(entity, target) ?: return
        beginDash(context, rt, target, destination)
    }

    private fun beginDash(context: MobRuntimeContext, runtime: Runtime, target: LivingEntity, destination: org.bukkit.Location) {
        runtime.isDashing = true
        runtime.hovering = true
        runtime.hoverTicksRemaining = hoverDurationTicks.coerceAtLeast(1L)
        runtime.hoverStartY = context.entity.location.y
        runtime.stabilizationTicks = 0L
        runtime.destination = destination
        runtime.targetId = target.uniqueId
        runtime.dashEndTick = Bukkit.getCurrentTick().toLong() + maxDashDurationTicks.coerceAtLeast(4L) + runtime.hoverTicksRemaining
        context.entity.world.playSound(context.entity.location, Sound.ENTITY_BLAZE_AMBIENT, 0.8f, 0.7f)
        context.entity.world.spawnParticle(Particle.FLAME, context.entity.location.clone().add(0.0, 1.0, 0.0), 70, 2.0, 1.4, 2.0, 0.02)
        context.entity.world.spawnParticle(Particle.LAVA, context.entity.location.clone().add(0.0, 1.0, 0.0), 5, 1.8, 1.0, 1.8, 0.0)
        context.entity.world.spawnParticle(Particle.SMOKE, context.entity.location.clone().add(0.0, 1.0, 0.0), 30, 2.0, 1.6, 2.0, 0.03)
    }

    private fun processDashing(context: MobRuntimeContext, runtime: Runtime) {
        val entity = context.entity
        val destination = runtime.destination
        if (destination == null || destination.world.uid != entity.world.uid) {
            cancelDash(runtime)
            return
        }

        val nowTick = Bukkit.getCurrentTick().toLong()
        val toDestination = destination.toVector().subtract(entity.location.toVector())
        val reached = toDestination.lengthSquared() <= destinationReachDistance * destinationReachDistance
        val timedOut = nowTick >= runtime.dashEndTick

        if (timedOut) {
            explodeAtDestination(entity, entity.location.clone())
            cancelDash(runtime)
            val baseCooldown = cooldownTicks.coerceAtLeast(1L)
            runtime.cooldownTicks =
                (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
            return
        }

        if (runtime.hovering) {
            processHovering(context, entity, runtime)
            return
        }

        applyGuidedApproachMotion(entity, toDestination)

        if (reached) {
            val speedThresholdSquared = stabilizationSpeedThreshold * stabilizationSpeedThreshold
            val movingSlowly = entity.velocity.lengthSquared() <= speedThresholdSquared
            runtime.stabilizationTicks = if (movingSlowly) {
                runtime.stabilizationTicks + context.tickDelta
            } else {
                0L
            }
            if (runtime.stabilizationTicks >= stabilizationRequiredTicks.coerceAtLeast(1L)) {
                explodeAtDestination(entity, entity.location.clone())
                cancelDash(runtime)
                val baseCooldown = cooldownTicks.coerceAtLeast(1L)
                runtime.cooldownTicks =
                    (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
                return
            }
        } else {
            runtime.stabilizationTicks = 0L
        }

        entity.world.spawnParticle(Particle.FLAME, entity.location.clone().add(0.0, 1.0, 0.0), 4, 0.9, 0.7, 0.9, 0.01)
        entity.world.spawnParticle(Particle.SMOKE, entity.location.clone().add(0.0, 1.0, 0.0), 14, 0.7, 0.5, 0.7, 0.02)
        entity.world.spawnParticle(Particle.LAVA, entity.location.clone().add(0.0, 1.0, 0.0), 1, 0.8, 0.5, 0.8, 0.0)

        val target = runtime.targetId?.let { Bukkit.getEntity(it) as? LivingEntity }
        if (target != null && target.isValid && !target.isDead && target.world.uid == entity.world.uid) {
            MobAbilityUtils.faceTowards(entity, target)
        }
    }

    private fun processHovering(context: MobRuntimeContext, entity: LivingEntity, runtime: Runtime) {
        val targetHoverY = runtime.hoverStartY + hoverHeight
        val deltaY = targetHoverY - entity.location.y
        val verticalVelocity = when {
            deltaY > 0.9 -> 0.48
            deltaY > 0.45 -> 0.28
            deltaY > 0.12 -> 0.16
            else -> 0.04
        }
        entity.velocity = entity.velocity.setX(0.0).setY(verticalVelocity).setZ(0.0)
        entity.world.spawnParticle(Particle.FLAME, entity.location.clone().add(0.0, 1.2, 0.0), 4, 1.6, 1.0, 1.6, 0.02)
        entity.world.spawnParticle(Particle.SMOKE, entity.location.clone().add(0.0, 1.2, 0.0), 2, 1.4, 0.9, 1.4, 0.03)
        runtime.hoverTicksRemaining = (runtime.hoverTicksRemaining - context.tickDelta).coerceAtLeast(0L)
        if (runtime.hoverTicksRemaining <= 0L) {
            runtime.hovering = false
            runtime.hoverTicksRemaining = 0L
        }
    }

    private fun applyGuidedApproachMotion(entity: LivingEntity, toDestination: Vector) {
        if (toDestination.lengthSquared() < 0.0001) {
            return
        }
        val desired = toDestination.clone().normalize().multiply(dashSpeed)
        desired.y = when {
            toDestination.y > 1.0 -> dashVerticalSpeed
            toDestination.y < -1.0 -> -0.08
            else -> 0.03
        }
        entity.velocity = entity.velocity.multiply(0.35).add(desired.multiply(0.65))
    }

    private fun explodeAtDestination(entity: LivingEntity, destination: org.bukkit.Location) {
        val world = entity.world
        world.spawnParticle(Particle.EXPLOSION, destination, 5, 0.7, 0.3, 0.7, 0.0)
        world.spawnParticle(Particle.FLAME, destination, 280, 5.0, 2.8, 5.0, 0.04)
        world.spawnParticle(Particle.LAVA, destination, 10, 3.8, 2.1, 3.8, 0.0)
        world.spawnParticle(Particle.SMOKE, destination, 170, 4.5, 2.5, 4.5, 0.06)
        world.playSound(destination, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.85f)
        world.playSound(destination, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.55f)
        world.createExplosion(destination, explosionPower, false, false, entity)
    }

    private fun cancelDash(runtime: Runtime) {
        runtime.isDashing = false
        runtime.hovering = false
        runtime.hoverTicksRemaining = 0L
        runtime.hoverStartY = 0.0
        runtime.stabilizationTicks = 0L
        runtime.destination = null
        runtime.targetId = null
        runtime.dashEndTick = 0L
    }

    private fun resolveDestination(entity: LivingEntity, target: LivingEntity): org.bukkit.Location? {
        val toTarget = target.location.toVector().subtract(entity.location.toVector()).setY(0.0)
        if (toTarget.lengthSquared() < 0.0001) {
            return null
        }
        val interactionRange = resolveInteractionRange(target).coerceAtLeast(1.0)
        val offset = toTarget.normalize().multiply(interactionRange)
        val destination = target.location.clone().subtract(offset)

        val highest = destination.world.getHighestBlockYAt(destination)
        destination.y = highest.toDouble() + 1.0
        return destination
    }

    private fun resolveInteractionRange(target: LivingEntity): Double {
        return try {
            target.javaClass.getMethod("getInteractionRange").invoke(target) as? Double ?: DEFAULT_INTERACTION_RANGE
        } catch (_: Exception) {
            DEFAULT_INTERACTION_RANGE
        }
    }

    private companion object {
        const val DEFAULT_INTERACTION_RANGE = 3.0
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
