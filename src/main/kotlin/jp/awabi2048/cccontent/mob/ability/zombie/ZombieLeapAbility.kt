package jp.awabi2048.cccontent.mob.ability.zombie

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.ability.MobAbility
import jp.awabi2048.cccontent.mob.ability.MobAbilityRuntime
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.Sound
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.roundToLong
import kotlin.random.Random

class ZombieLeapAbility : MobAbility {
    override val id: String = "zombie_leap"

    data class Runtime(var leapCooldownTicks: Long = 0L) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime()
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val abilityRuntime = runtime as? Runtime ?: return
        if (abilityRuntime.leapCooldownTicks > 0L) {
            abilityRuntime.leapCooldownTicks -= 10L
        }

        if (!context.isCombatActive()) return
        if (abilityRuntime.leapCooldownTicks > 0L) return

        val loadSnapshot = context.loadSnapshot
        if (context.activeMob.tickCount % (10L * loadSnapshot.searchIntervalMultiplier.toLong()) != 0L) {
            return
        }
        if (Random.nextDouble() < loadSnapshot.abilityExecutionSkipChance) {
            return
        }

        val entity = context.entity
        if (!entity.isOnGround) return

        val target = resolveTarget(entity) ?: return
        val distanceSquared = entity.location.distanceSquared(target.location)
        if (distanceSquared < LEAP_MIN_RANGE_SQUARED || distanceSquared > LEAP_MAX_RANGE_SQUARED) return

        faceTowards(entity, target)

        val delta = target.location.toVector().subtract(entity.location.toVector())
        val horizontal = delta.clone().setY(0.0)
        if (horizontal.lengthSquared() < 0.0001) return

        entity.world.playSound(entity.location, Sound.ENTITY_IRON_GOLEM_STEP, 1.0f, 0.8f)
        entity.velocity = horizontal.normalize().multiply(LEAP_HORIZONTAL_SPEED).setY(LEAP_VERTICAL_SPEED)

        abilityRuntime.leapCooldownTicks =
            (LEAP_COOLDOWN_TICKS * loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(LEAP_COOLDOWN_TICKS)
    }

    private fun resolveTarget(entity: LivingEntity): LivingEntity? {
        val target = (entity as? Mob)?.target as? LivingEntity ?: return null
        if (!target.isValid || target.isDead) {
            return null
        }
        return target
    }

    private fun faceTowards(entity: LivingEntity, target: LivingEntity) {
        val direction = target.eyeLocation.toVector().subtract(entity.eyeLocation.toVector())
        if (direction.lengthSquared() < 0.0001) return

        val normalized = direction.normalize()
        val yaw = Math.toDegrees(atan2(-normalized.x, normalized.z)).toFloat()
        val pitch = Math.toDegrees(-asin(normalized.y)).toFloat()
        entity.setRotation(yaw, pitch)
    }

    private companion object {
        const val LEAP_COOLDOWN_TICKS = 80L
        const val LEAP_MIN_RANGE_SQUARED = 16.0
        const val LEAP_MAX_RANGE = 8.0
        const val LEAP_MAX_RANGE_SQUARED = LEAP_MAX_RANGE * LEAP_MAX_RANGE
        const val LEAP_HORIZONTAL_SPEED = 0.9
        const val LEAP_VERTICAL_SPEED = 0.45
    }
}
