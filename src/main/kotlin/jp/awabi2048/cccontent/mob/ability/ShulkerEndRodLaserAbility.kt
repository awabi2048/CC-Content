package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.roundToLong
import kotlin.random.Random

class ShulkerEndRodLaserAbility(
    override val id: String,
    private val cooldownTicks: Long = 100L,
    private val chargeTicks: Long = 20L,
    private val burstCount: Int = 2,
    private val burstIntervalTicks: Long = 5L,
    private val minRange: Double = 2.5,
    private val maxRange: Double = 20.0,
    private val hitRadius: Double = 0.6,
    private val damageMultiplier: Double = 0.95
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
        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        runtime.cooldownRemainingTicks = (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
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
        return MobAbilityUtils.resolveTarget(shooter) as? Player
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
