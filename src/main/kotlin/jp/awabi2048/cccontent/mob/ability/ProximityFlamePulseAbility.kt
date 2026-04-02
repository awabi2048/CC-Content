package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Particle
import kotlin.math.roundToLong

class ProximityFlamePulseAbility(
    override val id: String,
    private val intervalTicks: Long = 30L,
    private val radius: Double = 5.0,
    private val damage: Double = 8.0,
    private val fireTicks: Int = 60,
    private val particleCount: Int = 140,
    private val particleSpreadX: Double = 8.0,
    private val particleSpreadY: Double = 8.0,
    private val particleSpreadZ: Double = 8.0
) : MobAbility {

    data class Runtime(var cooldownTicks: Long = 0L) : MobAbilityRuntime

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        return Runtime(cooldownTicks = intervalTicks.coerceAtLeast(1L))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        if (rt.cooldownTicks > 0L) {
            rt.cooldownTicks = (rt.cooldownTicks - context.tickDelta).coerceAtLeast(0L)
            return
        }
        if (!context.isCombatActive()) {
            return
        }

        val source = context.entity
        val target = MobAbilityUtils.resolveTarget(source) ?: return
        if (!target.isValid || target.isDead || target.world.uid != source.world.uid) {
            return
        }
        if (source.location.distanceSquared(target.location) > radius * radius) {
            return
        }

        target.damage(damage.coerceAtLeast(0.0), source)
        target.fireTicks = maxOf(target.fireTicks, fireTicks.coerceAtLeast(0))

        source.world.spawnParticle(
            Particle.FLAME,
            source.location.clone().add(0.0, 1.0, 0.0),
            particleCount.coerceAtLeast(1),
            particleSpreadX,
            particleSpreadY,
            particleSpreadZ,
            0.0
        )
        source.world.playSound(source.location, "minecraft:entity.blaze.shoot", 1.0f, 0.5f)

        val baseCooldown = intervalTicks.coerceAtLeast(1L)
        rt.cooldownTicks =
            (baseCooldown * context.loadSnapshot.abilityCooldownMultiplier).roundToLong().coerceAtLeast(baseCooldown)
    }
}
