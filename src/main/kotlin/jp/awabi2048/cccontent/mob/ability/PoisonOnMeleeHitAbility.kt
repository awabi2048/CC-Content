package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobAttackContext
import org.bukkit.entity.LivingEntity
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.random.Random

class PoisonOnMeleeHitAbility(
    override val id: String,
    private val chance: Double,
    private val durationTicks: Int,
    private val amplifier: Int
) : MobAbility {

    override fun onAttack(context: MobAttackContext, runtime: MobAbilityRuntime?) {
        val target = context.target ?: return
        if (!isDirectMeleeAttack(context)) return
        if (Random.nextDouble() >= chance.coerceIn(0.0, 1.0)) return

        target.addPotionEffect(
            PotionEffect(
                PotionEffectType.POISON,
                durationTicks.coerceAtLeast(1),
                amplifier.coerceAtLeast(0),
                false,
                true,
                true
            )
        )
    }

    private fun isDirectMeleeAttack(context: MobAttackContext): Boolean {
        val damager = context.event.damager as? LivingEntity ?: return false
        return damager.uniqueId == context.entity.uniqueId
    }
}
