package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobAttackContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import kotlin.math.roundToLong

class AttackBackstepAbility(
    override val id: String,
    private val cooldownTicks: Long = 50L,
    private val horizontalSpeed: Double = 0.9,
    private val verticalSpeed: Double = 0.32,
    private val requireDirectMelee: Boolean = true
) : MobAbility {
    data class Runtime(var cooldownRemainingTicks: Long = 0L) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime()
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        if (rt.cooldownRemainingTicks > 0L) {
            rt.cooldownRemainingTicks = (rt.cooldownRemainingTicks - context.tickDelta).coerceAtLeast(0L)
        }
    }

    override fun onAttack(context: MobAttackContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        if (context.event.isCancelled) return
        if (!context.isCombatActive()) return
        if (rt.cooldownRemainingTicks > 0L) return
        if (requireDirectMelee && context.event.damager.uniqueId != context.entity.uniqueId) return

        val target = context.target ?: return
        if (!target.isValid || target.isDead || target.world.uid != context.entity.world.uid) return

        launchBackstep(context.entity, target)

        val baseCooldown = cooldownTicks.coerceAtLeast(1L)
        rt.cooldownRemainingTicks =
            (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }

    private fun launchBackstep(attacker: LivingEntity, target: LivingEntity) {
        val away = attacker.location.toVector().subtract(target.location.toVector()).setY(0.0)
        if (away.lengthSquared() <= 1.0e-6) return

        attacker.velocity = away.normalize().multiply(horizontalSpeed).setY(verticalSpeed)
        attacker.world.playSound(attacker.location, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.8f, 1.35f)
    }
}
