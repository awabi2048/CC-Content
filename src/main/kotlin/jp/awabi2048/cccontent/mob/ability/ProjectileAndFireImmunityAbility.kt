package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobCombustContext
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobGenericDamagedContext
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageEvent

class ProjectileAndFireImmunityAbility(
    override val id: String
) : MobAbility {

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        if (context.event.damager is Projectile) {
            context.event.isCancelled = true
        }
    }

    override fun onGenericDamaged(context: MobGenericDamagedContext, runtime: MobAbilityRuntime?) {
        if (FIRE_DAMAGE_CAUSES.contains(context.event.cause)) {
            context.event.isCancelled = true
        }
    }

    override fun onCombust(context: MobCombustContext, runtime: MobAbilityRuntime?) {
        context.event.isCancelled = true
    }

    private companion object {
        val FIRE_DAMAGE_CAUSES = setOf(
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.HOT_FLOOR,
            EntityDamageEvent.DamageCause.CAMPFIRE
        )
    }
}
