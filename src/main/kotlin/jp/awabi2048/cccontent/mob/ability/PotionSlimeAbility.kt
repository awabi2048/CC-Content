package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.ability.MobAbilityRuntime
import jp.awabi2048.cccontent.mob.MobAttackContext
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.AreaEffectCloud
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class PotionSlimeAbility(
    override val id: String,
    private val effectType: PotionEffectType,
    private val effectDurationTicks: Int = 80,
    private val effectAmplifier: Int = 0,
    private val cloudRadius: Float = 3.0f,
    private val cloudDurationTicks: Int = 100,
    private val cloudColor: Color? = null
) : MobAbility {

    override fun onAttack(context: MobAttackContext, runtime: MobAbilityRuntime?) {
        val target = context.target ?: return
        target.addPotionEffect(PotionEffect(effectType, effectDurationTicks, effectAmplifier, false, true, true))
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val location = context.entity.location
        val world = location.world ?: return

        world.spawnParticle(Particle.ITEM_SLIME, location, 25, 1.0, 0.5, 1.0, 0.1)
        world.playSound(location, Sound.ENTITY_SLIME_DEATH, 1.0f, 0.8f)

        val cloud = world.spawn(location, AreaEffectCloud::class.java)
        cloud.addCustomEffect(PotionEffect(effectType, effectDurationTicks, effectAmplifier), true)
        cloud.radius = cloudRadius
        cloud.duration = cloudDurationTicks
        cloud.setWaitTime(10)
        cloudColor?.let { cloud.setColor(it) }
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val color = cloudColor ?: return
        val location = context.entity.location
        val world = location.world ?: return

        world.spawnParticle(
            Particle.DUST,
            location.clone().add(0.0, 0.5, 0.0),
            3,
            0.4, 0.3, 0.4,
            0.0,
            Particle.DustOptions(color, 0.8f)
        )
    }
}
