package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.scheduler.BukkitRunnable
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
    private val shootSound: Sound = Sound.ENTITY_SPIDER_HURT,
    private val trailRenderer: (Location) -> Unit,
    private val onHit: (attacker: LivingEntity, target: LivingEntity) -> Unit = { _, _ -> }
) : MobAbility {

    data class Runtime(
        var shotCooldownTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        if (abilityRuntime.shotCooldownTicks > 0L) {
            abilityRuntime.shotCooldownTicks -= 10L
        }

        if (!context.isCombatActive()) return
        if (abilityRuntime.shotCooldownTicks > 0L) return

        if (!MobAbilityUtils.shouldProcessSearchTick(context.activeMob.tickCount, context.loadSnapshot.searchIntervalMultiplier, abilityRuntime.searchPhaseOffsetSteps)) {
            return
        }
        if (Random.nextDouble() < context.loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        val attacker = context.entity
        val target = MobAbilityUtils.resolveTarget(attacker) ?: return
        if (!attacker.hasLineOfSight(target)) return

        val distance = attacker.location.distance(target.location)
        if (distance < minRange || distance > maxRange) return

        MobAbilityUtils.faceTowards(attacker, target)
        launchProjectile(context, attacker, target)

        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        abilityRuntime.shotCooldownTicks =
            (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    private fun launchProjectile(context: MobRuntimeContext, attacker: LivingEntity, target: LivingEntity) {
        val world = attacker.world
        val start = attacker.eyeLocation.clone()
        val direction = target.eyeLocation.toVector().subtract(start.toVector())
        if (direction.lengthSquared() < 0.0001) return

        val normalized = direction.normalize()
        val stepDistance = (speedBlocksPerSecond / 20.0).coerceAtLeast(0.01)
        var traveledDistance = 0.0
        var current = start.add(normalized.clone().multiply(0.3))
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
