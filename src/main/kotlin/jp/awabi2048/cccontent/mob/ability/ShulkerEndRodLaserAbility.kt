package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Shulker
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.roundToLong
import kotlin.random.Random

class ShulkerEndRodLaserAbility(
    override val id: String,
    private val cooldownTicks: Long = 40L,
    private val chargeTicks: Long = 20L,
    private val burstCount: Int = 2,
    private val burstIntervalTicks: Long = 5L,
    private val minRange: Double = 2.5,
    private val maxRange: Double = 20.0,
    private val hitRadius: Double = 0.6,
    private val damageMultiplier: Double = 0.95,
    private val allowBurstAlternative: Boolean = false,
    private val altBurstCount: Int = 3,
    private val altBurstIntervalTicks: Long = 5L,
    private val altProjectileDamageMultiplier: Double = 0.7,
    private val altProjectileSpeedPerTick: Double = 0.25,
    private val altHomingStrength: Double = 0.15,
    private val altNoiseStrength: Double = 0.15,
    private val altMaxLifeTicks: Long = 70L,
    private val altHitRadius: Double = 0.58,
    private val altLaunchVelocityY: Double = 0.70
) : MobAbility {

    data class Runtime(
        var cooldownRemainingTicks: Long = 0L,
        var chargingTicksRemaining: Long = 0L,
        var lockedTargetId: java.util.UUID? = null,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        if (rt.cooldownRemainingTicks > 0L) {
            rt.cooldownRemainingTicks = (rt.cooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        }

        if (!context.isCombatActive()) {
            rt.chargingTicksRemaining = 0L
            rt.lockedTargetId = null
            return
        }

        val shooter = context.entity
        val target = resolveTarget(shooter, rt.lockedTargetId) ?: run {
            rt.chargingTicksRemaining = 0L
            rt.lockedTargetId = null
            return
        }

        if (rt.chargingTicksRemaining > 0L) {
            processCharge(context, rt, shooter, target)
            return
        }

        if (rt.cooldownRemainingTicks > 0L) {
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

        val distance = shooter.location.distance(target.location)
        if (distance < minRange || distance > maxRange || !shooter.hasLineOfSight(target)) {
            return
        }

        if (allowBurstAlternative && shooter is Shulker && Random.nextBoolean()) {
            fireAlternativeBurst(context, shooter, target)
            applyCooldown(context, rt)
            return
        }

        rt.lockedTargetId = target.uniqueId
        rt.chargingTicksRemaining = chargeTicks.coerceAtLeast(1L)
        shooter.world.playSound(shooter.location, Sound.BLOCK_BEACON_AMBIENT, 0.8f, 1.8f)
    }

    private fun processCharge(
        context: MobRuntimeContext,
        runtime: Runtime,
        shooter: LivingEntity,
        target: Player
    ) {
        MobAbilityUtils.faceTowards(shooter, target)
        renderTelegraph(shooter, target)

        runtime.chargingTicksRemaining = (runtime.chargingTicksRemaining - context.tickDelta).coerceAtLeast(0L)
        if (runtime.chargingTicksRemaining > 0L) {
            return
        }

        repeat(burstCount.coerceAtLeast(1)) { index ->
            org.bukkit.Bukkit.getScheduler().runTaskLater(context.plugin, Runnable {
                val aliveShooter = org.bukkit.Bukkit.getEntity(shooter.uniqueId) as? LivingEntity ?: return@Runnable
                if (!aliveShooter.isValid || aliveShooter.isDead) return@Runnable
                val aliveTarget = org.bukkit.Bukkit.getEntity(target.uniqueId) as? Player ?: return@Runnable
                if (!aliveTarget.isValid || aliveTarget.isDead || aliveTarget.world.uid != aliveShooter.world.uid) {
                    return@Runnable
                }
                fireSingleBurst(context, aliveShooter, aliveTarget)
            }, burstIntervalTicks.coerceAtLeast(1L) * index.toLong())
        }

        runtime.lockedTargetId = null
        applyCooldown(context, runtime)
    }

    private fun fireSingleBurst(context: MobRuntimeContext, shooter: LivingEntity, target: Player) {
        val world = shooter.world
        val origin = shooter.eyeLocation
        val direction = target.eyeLocation.toVector().subtract(origin.toVector())
            .takeIf { it.lengthSquared() > 0.0001 }
            ?.normalize()
            ?: shooter.location.direction.clone().normalize()

        val blockHit = world.rayTraceBlocks(origin, direction, maxRange, FluidCollisionMode.NEVER, true)
        val limit = blockHit?.hitPosition?.toLocation(world)?.distance(origin) ?: maxRange
        renderBeam(world, origin, direction, limit)

        val hit = world.rayTraceEntities(origin, direction, limit, hitRadius) { candidate ->
            candidate is LivingEntity && candidate.uniqueId != shooter.uniqueId && candidate.isValid && !candidate.isDead
        }?.hitEntity as? LivingEntity

        if (hit != null) {
            val damage = (context.definition.attack * damageMultiplier).coerceAtLeast(0.0)
            if (damage > 0.0) {
                MobService.getInstance(context.plugin)?.issueDirectDamagePermit(shooter.uniqueId, hit.uniqueId)
                hit.damage(damage, shooter)
            }
            world.spawnParticle(Particle.ENCHANTED_HIT, hit.location.clone().add(0.0, 1.0, 0.0), 16, 0.12, 0.12, 0.12, 0.0)
            world.playSound(hit.location, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.85f, 1.7f)
        }

        world.playSound(shooter.location, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.9f)
    }

    private fun renderTelegraph(shooter: LivingEntity, target: Player) {
        val world = shooter.world
        val from = shooter.eyeLocation
        val to = target.eyeLocation
        val direction = to.toVector().subtract(from.toVector())
        if (direction.lengthSquared() < 0.0001) return
        val normalized = direction.normalize()
        val distance = from.distance(to).coerceAtMost(maxRange)
        val steps = (distance / 0.5).toInt().coerceAtLeast(1)
        var cursor = from.clone()
        repeat(steps) {
            world.spawnParticle(Particle.END_ROD, cursor, 1, 0.02, 0.02, 0.02, 0.0)
            cursor = cursor.add(normalized.clone().multiply(0.5))
        }
    }

    private fun renderBeam(world: org.bukkit.World, origin: Location, direction: Vector, distance: Double) {
        val steps = (distance / 0.35).toInt().coerceAtLeast(1)
        var cursor = origin.clone()
        repeat(steps) {
            world.spawnParticle(Particle.END_ROD, cursor, 1, 0.01, 0.01, 0.01, 0.0)
            world.spawnParticle(Particle.ENCHANTED_HIT, cursor, 1, 0.01, 0.01, 0.01, 0.0)
            cursor = cursor.add(direction.clone().multiply(0.35))
        }
    }

    private fun resolveTarget(shooter: LivingEntity, targetId: java.util.UUID?): Player? {
        val fixed = targetId?.let { org.bukkit.Bukkit.getEntity(it) as? Player }
        if (fixed != null && fixed.isValid && !fixed.isDead && fixed.world.uid == shooter.world.uid) {
            return fixed
        }
        val mobTarget = MobAbilityUtils.resolveTarget(shooter) as? Player
        if (mobTarget != null && mobTarget.isValid && !mobTarget.isDead && mobTarget.world.uid == shooter.world.uid) {
            return mobTarget
        }
        return shooter.getNearbyEntities(24.0, 24.0, 24.0)
            .asSequence()
            .mapNotNull { it as? Player }
            .filter { it.isValid && !it.isDead && it.world.uid == shooter.world.uid }
            .minByOrNull { it.location.distanceSquared(shooter.location) }
    }

    private fun applyCooldown(context: MobRuntimeContext, runtime: Runtime) {
        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        runtime.cooldownRemainingTicks = (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier)
            .roundToLong()
            .coerceAtLeast(baseCooldown)
    }

    private fun fireAlternativeBurst(context: MobRuntimeContext, shooter: Shulker, target: Player) {
        val finalDamage = (context.definition.attack * altProjectileDamageMultiplier).coerceAtLeast(0.0)
        repeat(altBurstCount.coerceAtLeast(1)) { index ->
            val delay = altBurstIntervalTicks.coerceAtLeast(1L) * index.toLong()
            org.bukkit.Bukkit.getScheduler().runTaskLater(context.plugin, Runnable {
                val aliveShooter = org.bukkit.Bukkit.getEntity(shooter.uniqueId) as? Shulker ?: return@Runnable
                val victim = org.bukkit.Bukkit.getEntity(target.uniqueId) as? Player ?: return@Runnable
                if (!aliveShooter.isValid || aliveShooter.isDead || !victim.isValid || victim.isDead || aliveShooter.world.uid != victim.world.uid) {
                    return@Runnable
                }
                launchAlternativeHomingShot(context, aliveShooter, victim, finalDamage)
            }, delay)
        }
        shooter.world.playSound(shooter.location, Sound.ENTITY_SHULKER_SHOOT, 1.0f, 1.2f)
    }

    private fun launchAlternativeHomingShot(context: MobRuntimeContext, shooter: Shulker, target: Player, damage: Double) {
        val world = shooter.world
        var position = shooter.eyeLocation.clone().add(shooter.location.direction.clone().multiply(0.45))
        var velocity = target.eyeLocation.toVector().subtract(position.toVector())
            .takeIf { it.lengthSquared() > 0.0001 }
            ?.normalize()
            ?.multiply(altProjectileSpeedPerTick)
            ?: shooter.location.direction.clone().normalize().multiply(altProjectileSpeedPerTick)

        world.playSound(position, Sound.ENTITY_SHULKER_BULLET_HURT, 0.65f, 1.55f)

        object : BukkitRunnable() {
            var life = altMaxLifeTicks

            override fun run() {
                if (!shooter.isValid || shooter.isDead || life <= 0L) {
                    cancel()
                    return
                }

                val liveTarget = org.bukkit.Bukkit.getEntity(target.uniqueId) as? Player
                if (liveTarget != null && liveTarget.isValid && !liveTarget.isDead && liveTarget.world.uid == world.uid) {
                    val desired = liveTarget.eyeLocation.toVector().subtract(position.toVector())
                    if (desired.lengthSquared() > 0.0001) {
                        val wanted = desired.normalize().multiply(altProjectileSpeedPerTick)
                        velocity = velocity.multiply((1.0 - altHomingStrength).coerceIn(0.0, 1.0))
                            .add(wanted.multiply(altHomingStrength.coerceIn(0.0, 1.0)))
                    }
                }

                val noisy = velocity.add(randomNoiseVector().multiply(altNoiseStrength))
                velocity = if (noisy.lengthSquared() > 0.0001) {
                    noisy.normalize().multiply(altProjectileSpeedPerTick)
                } else {
                    Vector(0.0, 0.0, 0.0)
                }

                val blockHit = world.rayTraceBlocks(
                    position,
                    velocity.clone().normalize(),
                    velocity.length(),
                    FluidCollisionMode.NEVER,
                    true
                )
                if (blockHit != null) {
                    world.spawnParticle(Particle.ENCHANTED_HIT, blockHit.hitPosition.toLocation(world), 8, 0.08, 0.08, 0.08, 0.0)
                    world.playSound(blockHit.hitPosition.toLocation(world), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.5f, 2.0f)
                    cancel()
                    return
                }

                val next = position.clone().add(velocity)
                val hit = world.getNearbyEntities(next, altHitRadius, altHitRadius, altHitRadius)
                    .asSequence()
                    .mapNotNull { it as? Player }
                    .firstOrNull { it.isValid && !it.isDead && it.world.uid == world.uid }

                if (hit != null) {
                    if (damage > 0.0) {
                        MobService.getInstance(context.plugin)?.issueDirectDamagePermit(shooter.uniqueId, hit.uniqueId)
                        hit.damage(damage, shooter)
                    }
                    hit.velocity = hit.velocity.clone().apply { y = altLaunchVelocityY }
                    world.spawnParticle(Particle.ENCHANTED_HIT, hit.location.clone().add(0.0, 1.0, 0.0), 16, 0.12, 0.12, 0.12, 0.0)
                    world.playSound(hit.location, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.75f, 1.9f)
                    cancel()
                    return
                }

                position = next
                world.spawnParticle(Particle.END_ROD, position, 1, 0.01, 0.01, 0.01, 0.0)
                world.spawnParticle(Particle.ENCHANTED_HIT, position, 2, 0.03, 0.03, 0.03, 0.0)
                life -= 1L
            }
        }.runTaskTimer(context.plugin, 1L, 1L)
    }

    private fun randomNoiseVector(): Vector {
        return Vector(
            Random.nextDouble(-1.0, 1.0),
            Random.nextDouble(-0.75, 0.75),
            Random.nextDouble(-1.0, 1.0)
        )
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
