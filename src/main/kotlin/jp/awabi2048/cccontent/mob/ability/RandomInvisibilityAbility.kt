package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.random.Random

class RandomInvisibilityAbility(
    override val id: String,
    private val spawnChance: Double = 0.12,
    private val damagedChance: Double = 0.16,
    private val durationTicks: Int = 60,
    private val amplifier: Int = 0
) : MobAbility {

    override fun onSpawn(context: MobSpawnContext, runtime: MobAbilityRuntime?) {
        if (Random.nextDouble() >= spawnChance.coerceIn(0.0, 1.0)) return
        applyInvisibility(context.entity)
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        if (!context.isCombatActive()) return
        if (context.event.isCancelled) return
        if (Random.nextDouble() >= damagedChance.coerceIn(0.0, 1.0)) return
        applyInvisibility(context.entity)
    }

    private fun applyInvisibility(entity: org.bukkit.entity.LivingEntity) {
        val ticks = durationTicks.coerceAtLeast(1)
        entity.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, ticks, amplifier.coerceAtLeast(0), false, true, true))
    }
}
