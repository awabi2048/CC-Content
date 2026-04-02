package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.roundToLong
import kotlin.random.Random

class GenericBeamAbility(
    override val id: String,
    private val cooldownTicks: Long = 110L,
    private val chargeTicks: Long = 28L,
    private val minRange: Double = 2.0,
    private val maxRange: Double = 20.0,
    private val hitRadius: Double = 0.85,
    private val damageMultiplier: Double = 1.0,
    private val bonusDamage: Double = 0.0,
    private val directKnockback: Double = 1.2,
    private val verticalBoost: Double = 0.15,
    private val beamStep: Double = 0.45,
    private val lockLeadTicks: Long = 10L,
    private val requireLineOfSight: Boolean = true
) : MobAbility {

    override fun tickIntervalTicks(): Long = 1L

    data class Runtime(
        var cooldownTicks: Long = 0L,
        var chargeRemainingTicks: Long = 0L,
        var targetId: java.util.UUID? = null,
        var lockedTargetLocation: org.bukkit.Location? = null,
        var searchPhaseOffsetSteps: Int = 0
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
            rt.chargeRemainingTicks = 0L
            rt.targetId = null
            rt.lockedTargetLocation = null
            return
        }

        if (rt.chargeRemainingTicks > 0L) {
            processCharging(context, rt)
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

        val shooter = context.entity
        val target = MobAbilityUtils.resolveTarget(shooter) ?: return
        val distance = shooter.location.distance(target.location)
        if (distance < minRange || distance > maxRange) {
            return
        }
        if (requireLineOfSight && !shooter.hasLineOfSight(target)) {
            return
        }

        rt.targetId = target.uniqueId
        rt.chargeRemainingTicks = chargeTicks.coerceAtLeast(1L)
        rt.lockedTargetLocation = null
        shooter.world.playSound(shooter.location, Sound.ENTITY_GUARDIAN_AMBIENT, 0.8f, 1.8f)
    }

    private fun processCharging(context: MobRuntimeContext, runtime: Runtime) {
        val shooter = context.entity
        val target = resolveTarget(shooter, runtime.targetId)
        if (target == null) {
            runtime.chargeRemainingTicks = 0L
            runtime.targetId = null
            runtime.lockedTargetLocation = null
            return
        }
        val distance = shooter.location.distance(target.location)
        if (distance < minRange || distance > maxRange) {
            runtime.chargeRemainingTicks = 0L
            runtime.targetId = null
            runtime.lockedTargetLocation = null
            return
        }
        if (requireLineOfSight && !shooter.hasLineOfSight(target)) {
            runtime.chargeRemainingTicks = 0L
            runtime.targetId = null
            runtime.lockedTargetLocation = null
            return
        }

        if (runtime.chargeRemainingTicks <= lockLeadTicks.coerceAtLeast(1L) && runtime.lockedTargetLocation == null) {
            runtime.lockedTargetLocation = target.eyeLocation.clone()
        }

        val telegraphPoint = runtime.lockedTargetLocation ?: target.eyeLocation.clone()

        MobAbilityUtils.faceTowards(shooter, target)
        renderChargingEffect(shooter)
        renderTelegraphLine(shooter, telegraphPoint)
        runtime.chargeRemainingTicks = (runtime.chargeRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        if (runtime.chargeRemainingTicks > 0L) {
            return
        }

        fireBeam(context, shooter, target, runtime.lockedTargetLocation)
        runtime.targetId = null
        runtime.lockedTargetLocation = null

        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        runtime.cooldownTicks =
            (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    private fun resolveTarget(shooter: LivingEntity, targetId: java.util.UUID?): LivingEntity? {
        val fixedTarget = targetId?.let { Bukkit.getEntity(it) as? LivingEntity }
        if (fixedTarget != null && fixedTarget.isValid && !fixedTarget.isDead && fixedTarget.world.uid == shooter.world.uid) {
            return fixedTarget
        }
        val currentTarget = MobAbilityUtils.resolveTarget(shooter)
        if (currentTarget != null && currentTarget.world.uid == shooter.world.uid) {
            return currentTarget
        }
        return null
    }

    private fun fireBeam(
        context: MobRuntimeContext,
        shooter: LivingEntity,
        target: LivingEntity,
        lockedLocation: org.bukkit.Location?
    ) {
        val origin = shooter.eyeLocation
        val aimLocation = lockedLocation?.takeIf { it.world.uid == shooter.world.uid } ?: target.eyeLocation
        val aim = aimLocation.toVector().subtract(origin.toVector())
            .takeIf { it.lengthSquared() > 0.0001 }
            ?.normalize()
            ?: shooter.location.direction.clone().normalize()
        val world = shooter.world

        renderBeam(world, origin, aim)
        world.playSound(shooter.location, Sound.ENTITY_GUARDIAN_ATTACK, 1.0f, 1.2f)

        val hit = world.rayTraceEntities(origin, aim, maxRange, hitRadius) { candidate ->
            candidate is LivingEntity && candidate.uniqueId != shooter.uniqueId && candidate.isValid && !candidate.isDead
        }?.hitEntity as? LivingEntity

        if (hit != null) {
            if (!(hit is Player && isShieldBlockingSource(hit, shooter.location))) {
                val finalDamage = (context.definition.attack * damageMultiplier + bonusDamage).coerceAtLeast(0.0)
                if (finalDamage > 0.0) {
                    MobService.getInstance(context.plugin)?.issueDirectDamagePermit(shooter.uniqueId, hit.uniqueId)
                    hit.damage(finalDamage, shooter)
                }
                if (directKnockback > 0.0) {
                    val push = hit.location.toVector().subtract(shooter.location.toVector()).setY(0.0)
                    if (push.lengthSquared() > 0.0001) {
                        hit.velocity = hit.velocity.add(push.normalize().multiply(directKnockback).setY(verticalBoost))
                    }
                }
                world.spawnParticle(Particle.EXPLOSION, hit.location.clone().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0)
                world.playSound(hit.location, Sound.ENTITY_BLAZE_HURT, 0.8f, 0.6f)
            } else {
                hit.setCooldown(Material.SHIELD, 100)
                world.playSound(hit.location, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.9f)
                world.playSound(hit.location, Sound.ITEM_SHIELD_BREAK, 0.9f, 1.05f)
            }
        }
    }

    private fun renderChargingEffect(shooter: LivingEntity) {
        val center = shooter.location.clone().add(0.0, 1.1, 0.0)
        shooter.world.spawnParticle(Particle.SMOKE, center, 8, 0.25, 0.25, 0.25, 0.01)
        shooter.world.spawnParticle(
            Particle.DUST_COLOR_TRANSITION,
            center,
            6,
            0.18,
            0.18,
            0.18,
            0.0,
            Particle.DustTransition(Color.fromRGB(255, 80, 25), Color.fromRGB(255, 220, 120), 1.0f)
        )
    }

    private fun renderTelegraphLine(shooter: LivingEntity, targetPoint: org.bukkit.Location) {
        val origin = shooter.eyeLocation
        val direction = targetPoint.toVector().subtract(origin.toVector())
        if (direction.lengthSquared() < 0.0001) {
            return
        }
        val normalized = direction.normalize()
        val maxDistance = origin.distance(targetPoint).coerceAtMost(maxRange)
        val steps = (maxDistance / 0.4).toInt().coerceAtLeast(1)
        var cursor = origin.clone()
        repeat(steps) {
            shooter.world.spawnParticle(Particle.SMOKE, cursor, 2, 0.05, 0.05, 0.05, 0.01)
            cursor = cursor.add(normalized.clone().multiply(0.4))
        }
    }

    private fun renderBeam(world: org.bukkit.World, origin: org.bukkit.Location, direction: Vector) {
        val steps = (maxRange / beamStep.coerceAtLeast(0.1)).toInt().coerceAtLeast(1)
        var cursor = origin.clone()
        repeat(steps) {
            world.spawnParticle(
                Particle.DUST_COLOR_TRANSITION,
                cursor,
                2,
                0.05,
                0.05,
                0.05,
                0.0,
                Particle.DustTransition(Color.fromRGB(255, 96, 24), Color.fromRGB(255, 210, 80), 1.15f)
            )
            world.spawnParticle(Particle.FLAME, cursor, 1, 0.02, 0.02, 0.02, 0.0)
            cursor = cursor.add(direction.clone().multiply(beamStep))
        }
    }

    private fun isShieldBlockingSource(player: Player, sourceLocation: org.bukkit.Location): Boolean {
        if (!player.isBlocking) {
            return false
        }

        val facing = player.location.direction.clone().setY(0.0)
        if (facing.lengthSquared() < 0.0001) {
            return true
        }

        val toSource = sourceLocation.toVector().subtract(player.location.toVector()).setY(0.0)
        if (toSource.lengthSquared() < 0.0001) {
            return true
        }

        return facing.normalize().dot(toSource.normalize()) >= 0.2
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
