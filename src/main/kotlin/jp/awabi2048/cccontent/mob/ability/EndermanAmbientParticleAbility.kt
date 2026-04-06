package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobRuntimeContext
import org.bukkit.Color
import org.bukkit.Particle
import kotlin.random.Random

class EndermanAmbientParticleAbility(
    override val id: String,
    private val intervalTicks: Long = 2L,
    private val portalCount: Int = 3,
    private val dustCount: Int = 2,
    private val innerColor: Color,
    private val outerColor: Color,
    private val dustSize: Float = 0.85f
) : MobAbility {

    data class Runtime(var phaseOffset: Int = 0) : MobAbilityRuntime

    override fun tickIntervalTicks(): Long = 1L

    override fun createRuntime(context: jp.awabi2048.cccontent.mob.MobSpawnContext): MobAbilityRuntime {
        val interval = intervalTicks.coerceAtLeast(1L).toInt()
        return Runtime(phaseOffset = Random.nextInt(0, interval))
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val interval = intervalTicks.coerceAtLeast(1L)
        if ((context.activeMob.tickCount + rt.phaseOffset).mod(interval) != 0L) {
            return
        }

        val entity = context.entity
        val world = entity.world
        val center = entity.location.clone().add(0.0, 1.05, 0.0)

        world.spawnParticle(Particle.PORTAL, center, portalCount.coerceAtLeast(1), 0.35, 0.45, 0.35, 0.01)
        world.spawnParticle(
            Particle.DUST_COLOR_TRANSITION,
            center,
            dustCount.coerceAtLeast(1),
            0.28,
            0.36,
            0.28,
            0.0,
            Particle.DustTransition(innerColor, outerColor, dustSize)
        )
    }
}
