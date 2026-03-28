package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.math.roundToLong

class AreaEffectPulseAbility(
    override val id: String,
    private val intervalTicks: Long,
    private val radius: Double,
    private val effectType: PotionEffectType,
    private val effectDurationTicks: Int,
    private val effectAmplifier: Int
) : MobAbility {

    data class Runtime(var cooldownTicks: Long = 0L) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(cooldownTicks = intervalTicks.coerceAtLeast(1L))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val pulseRuntime = runtime as? Runtime ?: return
        if (pulseRuntime.cooldownTicks > 0L) {
            pulseRuntime.cooldownTicks -= 10L
            return
        }
        if (!context.isCombatActive()) return

        val baseCooldown = intervalTicks.coerceAtLeast(1L)
        pulseRuntime.cooldownTicks =
            (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)

        val source = context.entity
        val nearbyPlayers = source.getNearbyEntities(radius, radius, radius)
            .asSequence()
            .filterIsInstance<Player>()
            .filter { it.isValid && !it.isDead }
            .toList()

        if (nearbyPlayers.isEmpty()) return

        val effect = PotionEffect(
            effectType,
            effectDurationTicks.coerceAtLeast(1),
            effectAmplifier.coerceAtLeast(0),
            false,
            true,
            true
        )

        nearbyPlayers.forEach { player ->
            player.addPotionEffect(effect)
        }
    }
}
