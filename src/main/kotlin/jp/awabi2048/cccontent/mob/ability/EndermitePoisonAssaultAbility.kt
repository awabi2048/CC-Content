package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobAttackContext
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class EndermitePoisonAssaultAbility(
    override val id: String,
    private val witherDurationTicks: Int = 70,
    private val witherAmplifier: Int = 0,
    private val slownessDurationTicks: Int = 70,
    private val slownessAmplifier: Int = 1
) : MobAbility {

    override fun onAttack(context: MobAttackContext, runtime: MobAbilityRuntime?) {
        if (context.event.isCancelled) return
        val target = context.target as? Player ?: return
        if (!target.isValid || target.isDead || target.world.uid != context.entity.world.uid) return

        target.addPotionEffect(PotionEffect(PotionEffectType.WITHER, witherDurationTicks, witherAmplifier, false, true, true))
        target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, slownessDurationTicks, slownessAmplifier, false, true, true))

        val keepHorizontal = target.velocity.clone()
        org.bukkit.Bukkit.getScheduler().runTask(context.plugin, Runnable {
            if (!target.isValid || target.isDead) return@Runnable
            val current = target.velocity
            target.velocity = current.setX(keepHorizontal.x).setZ(keepHorizontal.z)
        })
    }
}
