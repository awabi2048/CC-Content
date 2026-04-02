package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.roundToLong
import kotlin.random.Random

class LinearProjectileAbility(
    override val id: String,
    private val cooldownTicks: Long,
    private val minRange: Double,
    private val maxRange: Double,
    private val speedBlocksPerSecond: Double,
    private val maxTravelDistance: Double,
    private val hitRadius: Double = 0.45,
    private val damageMultiplier: Double = 1.0,
    private val aggressiveInRange: Boolean = false,
    private val preCastTicks: Long = 20L,
    private val postCastTicks: Long = 20L,
    private val shootSound: Sound = Sound.ENTITY_SPIDER_HURT,
    private val impactSound: Sound = Sound.BLOCK_SLIME_BLOCK_HIT,
    private val trailRenderer: (Location) -> Unit,
    private val onHit: (attacker: LivingEntity, target: LivingEntity) -> Unit = { _, _ -> }
) : MobAbility {

    data class Runtime(
        var shotCooldownTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0,
        var pendingShotAtTick: Long = -1L,
        var freezeUntilTick: Long = 0L,
        var pendingTargetId: java.util.UUID? = null,
        var pendingAimDirection: Vector? = null
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        if (abilityRuntime.shotCooldownTicks > 0L) {
            abilityRuntime.shotCooldownTicks = (abilityRuntime.shotCooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }

        val attacker = context.entity
        val nowTick = Bukkit.getCurrentTick().toLong()
        applyCastMovementLock(attacker, abilityRuntime, nowTick)

        if (abilityRuntime.pendingShotAtTick > 0L) {
            if (nowTick < abilityRuntime.pendingShotAtTick) {
                return
            }

            abilityRuntime.pendingShotAtTick = -1L
            fireIfReady(context, abilityRuntime, nowTick)
            return
        }

        if (!context.isCombatActive()) return

        if (abilityRuntime.shotCooldownTicks > 0L) return
        if (!shouldEvaluateTriggerTick(context, abilityRuntime)) return

        val target = MobAbilityUtils.resolveTarget(attacker) ?: return
        if (!attacker.hasLineOfSight(target)) return
        val distance = attacker.location.distance(target.location)

        val isOutsideRange = distance < minRange || distance > maxRange
        if (!isOutsideRange) {
            return
        }

        abilityRuntime.pendingTargetId = target.uniqueId
        abilityRuntime.pendingAimDirection = resolveAimDirection(attacker, target)

        schedulePreCastOrFire(context, abilityRuntime, nowTick)
    }

    private fun fireIfReady(context: MobRuntimeContext, runtime: Runtime, nowTick: Long) {
        val attacker = context.entity
        val target = resolvePendingTarget(attacker, runtime.pendingTargetId)
            ?: MobAbilityUtils.resolveTarget(attacker)
        if (target != null) {
            MobAbilityUtils.faceTowards(attacker, target)
        }

        val aimDirection = runtime.pendingAimDirection
            ?: target?.let { resolveAimDirection(attacker, it) }
            ?: attacker.location.direction.clone().normalize()
            .takeIf { it.lengthSquared() >= 0.0001 }
            ?: Vector(0.0, 0.0, 1.0)

        target?.let { MobAbilityUtils.faceTowards(attacker, it) }
        launchProjectile(context, attacker, aimDirection)

        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        runtime.shotCooldownTicks =
            (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
        runtime.freezeUntilTick = nowTick + postCastTicks.coerceAtLeast(0L)
        runtime.pendingTargetId = null
        runtime.pendingAimDirection = null
    }

    private fun shouldEvaluateTriggerTick(context: MobRuntimeContext, runtime: Runtime): Boolean {
        if (aggressiveInRange) {
            return true
        }
        if (!MobAbilityUtils.shouldProcessSearchTick(context.activeMob.tickCount, context.loadSnapshot.searchIntervalMultiplier, runtime.searchPhaseOffsetSteps)) {
            return false
        }
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) {
            return false
        }
        return true
    }

    private fun schedulePreCastOrFire(context: MobRuntimeContext, runtime: Runtime, nowTick: Long) {
        val preTicks = preCastTicks.coerceAtLeast(0L)
        if (preTicks <= 0L) {
            fireIfReady(context, runtime, nowTick)
            return
        }
        runtime.pendingShotAtTick = nowTick + preTicks
        runtime.freezeUntilTick = maxOf(runtime.freezeUntilTick, nowTick + preTicks)
    }

    private fun applyCastMovementLock(attacker: LivingEntity, runtime: Runtime, nowTick: Long) {
        if (nowTick >= runtime.freezeUntilTick) return

        attacker.velocity = attacker.velocity.clone().setX(0.0).setZ(0.0)
    }

    private fun resolvePendingTarget(attacker: LivingEntity, targetId: java.util.UUID?): LivingEntity? {
        if (targetId == null) return null
        val targetEntity = org.bukkit.Bukkit.getEntity(targetId) as? LivingEntity ?: return null
        if (!targetEntity.isValid || targetEntity.isDead) return null
        if (targetEntity.world.uid != attacker.world.uid) return null
        return targetEntity
    }

    private fun resolveAimDirection(attacker: LivingEntity, target: LivingEntity): Vector {
        val direction = target.eyeLocation.toVector().subtract(attacker.eyeLocation.toVector())
        if (direction.lengthSquared() < 0.0001) {
            return attacker.location.direction.clone().normalize().takeIf { it.lengthSquared() >= 0.0001 }
                ?: Vector(0.0, 0.0, 1.0)
        }
        return direction.normalize()
    }

    private fun launchProjectile(context: MobRuntimeContext, attacker: LivingEntity, direction: Vector) {
        val world = attacker.world
        val start = attacker.location.block.location.clone().add(0.5, 0.1, 0.5)
        val normalized = direction.clone().normalize()
        val stepDistance = (speedBlocksPerSecond / 20.0).coerceAtLeast(0.01)
        var traveledDistance = 0.0
        var current = start
        val damage = resolveDamage(attacker, context.definition.attack)

        MobService.getInstance(context.plugin)?.recordProjectileLaunch(context.sessionKey)
        world.playSound(attacker.location, shootSound, 1.0f, 1.0f)

        object : BukkitRunnable() {
            override fun run() {
                if (!attacker.isValid || attacker.isDead) {
                    cancel()
                    return
                }

                if (traveledDistance >= maxTravelDistance) {
                    cancel()
                    return
                }

                val next = current.clone().add(normalized.clone().multiply(stepDistance))

                val blockHit = world.rayTraceBlocks(
                    current,
                    normalized,
                    stepDistance,
                    FluidCollisionMode.NEVER,
                    true
                )
                if (blockHit != null) {
                    world.playSound(current, impactSound, 0.8f, 1.0f)
                    cancel()
                    return
                }

                val hit = world.rayTraceEntities(
                    current,
                    normalized,
                    stepDistance,
                    hitRadius
                ) { candidate ->
                    candidate is LivingEntity && candidate.uniqueId != attacker.uniqueId && candidate.isValid && !candidate.isDead
                }

                val hitTarget = hit?.hitEntity as? LivingEntity
                if (hitTarget != null) {
                    if (damage > 0.0) {
                        hitTarget.damage(damage, attacker)
                    }
                    world.playSound(hitTarget.location, impactSound, 0.9f, 1.1f)
                    if (hitTarget is Player) {
                        hitTarget.playSound(hitTarget.location, impactSound, 1.0f, 1.1f)
                    }
                    onHit(attacker, hitTarget)
                    cancel()
                    return
                }

                current = next
                traveledDistance += stepDistance
                trailRenderer(current.clone())
            }
        }.runTaskTimer(context.plugin, 0L, 1L)
    }

    private fun resolveDamage(attacker: LivingEntity, definitionAttack: Double): Double {
        val attack = attacker.getAttribute(Attribute.ATTACK_DAMAGE)?.value ?: definitionAttack
        return (attack * damageMultiplier).coerceAtLeast(0.0)
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
