package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobAttackContext
import org.bukkit.entity.LivingEntity

class MeleeKnockbackBoostAbility(
    override val id: String,
    private val horizontalBoost: Double = 3.5,
    private val verticalBoost: Double = 0.15
) : MobAbility {

    override fun onAttack(context: MobAttackContext, runtime: MobAbilityRuntime?) {
        val target = context.target ?: return
        if (!isDirectMeleeAttack(context)) return

        val direction = target.location.toVector().subtract(context.entity.location.toVector()).setY(0.0)
        if (direction.lengthSquared() < 0.0001) {
            return
        }

        val additional = direction.normalize().multiply(horizontalBoost).setY(verticalBoost)
        target.velocity = target.velocity.add(additional)
    }

    private fun isDirectMeleeAttack(context: MobAttackContext): Boolean {
        val damager = context.event.damager as? LivingEntity ?: return false
        return damager.uniqueId == context.entity.uniqueId
    }
}
