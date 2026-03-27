package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Particle
import org.bukkit.Sound
import kotlin.math.roundToLong
import kotlin.random.Random

class BoomerangAbility(
    override val id: String = "boomerang"
) : MobAbility {
    data class Runtime(
        var cooldownTicks: Long = 0L,
        var searchPhaseOffsetSteps: Int = 0
    ) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(searchPhaseOffsetSteps = Random.nextInt(0, SEARCH_PHASE_VARIANTS))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        if (abilityRuntime.cooldownTicks > 0L) {
            abilityRuntime.cooldownTicks -= 10L
        }

        if (!context.isCombatActive() || abilityRuntime.cooldownTicks > 0L) {
            return
        }

        val loadSnapshot = context.loadSnapshot
        if (!MobAbilityUtils.shouldProcessSearchTick(context.activeMob.tickCount, loadSnapshot.searchIntervalMultiplier, abilityRuntime.searchPhaseOffsetSteps)) {
            return
        }
        if (Random.nextDouble() < loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        val entity = context.entity
        val target = MobAbilityUtils.resolveTarget(entity) ?: return
        if (target.world.uid != entity.world.uid) {
            return
        }

        val delta = target.eyeLocation.toVector().subtract(entity.eyeLocation.toVector())
        val distance = delta.length()
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
            return
        }

        val service = BoomerangService.getInstance(context.plugin)
        if (service.hasActive(entity.uniqueId)) {
            return
        }

        val velocityPerTick = delta.normalize().multiply(BOOMERANG_SPEED_PER_TICK)
        val launched = service.launch(
            BoomerangService.LaunchSpec(
                ownerId = entity.uniqueId,
                targetId = target.uniqueId,
                start = entity.eyeLocation.add(entity.location.direction.clone().multiply(0.35)),
                velocityPerTick = velocityPerTick,
                damage = FIXED_DAMAGE,
                maxLifetimeTicks = BOOMERANG_MAX_LIFETIME_TICKS
            )
        )
        if (!launched) {
            return
        }

        entity.world.spawnParticle(Particle.SWEEP_ATTACK, entity.location.add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0)
        entity.world.playSound(entity.location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.05f)
        val baseCooldown = BOOMERANG_COOLDOWN_TICKS
        abilityRuntime.cooldownTicks =
            (baseCooldown * loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        BoomerangService.getInstance(context.plugin).cancelByOwner(context.entity.uniqueId)
    }

    private companion object {
        const val SEARCH_PHASE_VARIANTS = 16
        const val BOOMERANG_COOLDOWN_TICKS = 60L
        const val BOOMERANG_MAX_LIFETIME_TICKS = 60L
        const val BOOMERANG_SPEED_PER_TICK = 3.0
        const val FIXED_DAMAGE = 4.0
        const val MIN_DISTANCE = 2.0
        const val MAX_DISTANCE = 20.0
    }
}
