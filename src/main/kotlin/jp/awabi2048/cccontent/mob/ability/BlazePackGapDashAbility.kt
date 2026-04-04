package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobService
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.util.Vector
import kotlin.math.roundToLong
import kotlin.random.Random

class BlazePackGapDashAbility(
    override val id: String,
    private val requiredBlazeCount: Int = 3,
    private val targetCheckRadius: Double = 8.0,
    private val triggerMinDistance: Double = 4.0,
    private val triggerMaxDistance: Double = 20.0,
    private val dashSpeed: Double = 1.08,
    private val dashDurationTicks: Long = 10L,
    private val cooldownTicks: Long = 92L,
    private val requireLineOfSight: Boolean = true
) : MobAbility {

    data class Runtime(
        var cooldownTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0,
        var dashingTicksRemaining: Long = 0L,
        var dashDirection: Vector = Vector(0.0, 0.0, 0.0)
    ) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return

        if (rt.cooldownTicks > 0L) {
            rt.cooldownTicks = (rt.cooldownTicks - context.tickDelta).coerceAtLeast(0L)
        }

        if (rt.dashingTicksRemaining > 0L) {
            processDash(context.entity, rt)
            rt.dashingTicksRemaining = (rt.dashingTicksRemaining - context.tickDelta).coerceAtLeast(0L)
            return
        }

        if (!context.isCombatActive() || rt.cooldownTicks > 0L) {
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
        if (entity.type != EntityType.BLAZE) {
            return
        }

        val target = MobAbilityUtils.resolveTarget(entity) ?: return
        val distance = entity.location.distance(target.location)
        if (distance < triggerMinDistance || distance > triggerMaxDistance) {
            return
        }
        if (requireLineOfSight && !entity.hasLineOfSight(target)) {
            return
        }

        val nearbyBlazeCount = countNearbyArenaBlazes(context, target)
        if (nearbyBlazeCount >= requiredBlazeCount) {
            return
        }

        val direction = target.location.toVector().subtract(entity.location.toVector()).setY(0.0)
        if (direction.lengthSquared() < 0.0001) {
            return
        }

        rt.dashDirection = direction.normalize()
        rt.dashingTicksRemaining = dashDurationTicks
        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        rt.cooldownTicks =
            (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)

        entity.world.playSound(entity.location, Sound.ENTITY_BLAZE_AMBIENT, 0.65f, 1.2f)
        entity.world.spawnParticle(Particle.FLAME, entity.location.clone().add(0.0, 1.0, 0.0), 30, 0.8, 0.5, 0.8, 0.01)
        entity.world.spawnParticle(Particle.SMOKE, entity.location.clone().add(0.0, 1.0, 0.0), 16, 0.7, 0.4, 0.7, 0.02)
    }

    private fun processDash(entity: LivingEntity, runtime: Runtime) {
        val horizontal = runtime.dashDirection.clone().multiply(dashSpeed)
        entity.velocity = entity.velocity.multiply(0.25).add(horizontal.multiply(0.75)).setY(0.08)
        entity.world.spawnParticle(Particle.FLAME, entity.location.clone().add(0.0, 1.0, 0.0), 4, 0.45, 0.3, 0.45, 0.005)
    }

    private fun countNearbyArenaBlazes(context: MobRuntimeContext, target: LivingEntity): Int {
        val service = MobService.getInstance(context.plugin) ?: return 0
        return target.getNearbyEntities(targetCheckRadius, targetCheckRadius, targetCheckRadius)
            .asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { it.type == EntityType.BLAZE }
            .count { entity ->
                val active = service.getActiveMob(entity.uniqueId) ?: return@count false
                active.mobType.id.startsWith("blaze_")
            }
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
    }
}
