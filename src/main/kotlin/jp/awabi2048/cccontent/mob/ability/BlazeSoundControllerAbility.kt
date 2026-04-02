package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import org.bukkit.Sound
import kotlin.random.Random

class BlazeSoundControllerAbility(
    override val id: String,
    private val pitch: Float,
    private val hurtVolume: Float = 1.0f,
    private val ambientVolume: Float = 0.85f,
    private val deathVolume: Float = 1.0f,
    private val ambientIntervalMinTicks: Long = 80L,
    private val ambientIntervalMaxTicks: Long = 140L
) : MobAbility {

    data class Runtime(var ambientCooldownTicks: Long = 0L) : MobAbilityRuntime

    override fun createRuntime(context: MobSpawnContext): MobAbilityRuntime {
        return Runtime(nextAmbientCooldown())
    }

    override fun onSpawn(context: MobSpawnContext, runtime: MobAbilityRuntime?) {
        context.entity.isSilent = true
    }

    override fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {
        val rt = runtime as? Runtime ?: return
        val entity = context.entity
        if (!entity.isSilent) {
            entity.isSilent = true
        }
        if (!context.isCombatActive()) {
            return
        }

        if (rt.ambientCooldownTicks > 0L) {
            rt.ambientCooldownTicks = (rt.ambientCooldownTicks - context.tickDelta).coerceAtLeast(0L)
            return
        }

        entity.world.playSound(entity.location, Sound.ENTITY_BLAZE_AMBIENT, ambientVolume, pitch)
        rt.ambientCooldownTicks = nextAmbientCooldown()
    }

    override fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {
        val entity = context.entity
        entity.world.playSound(entity.location, Sound.ENTITY_BLAZE_HURT, hurtVolume, pitch)
    }

    override fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {
        val entity = context.entity
        entity.world.playSound(entity.location, Sound.ENTITY_BLAZE_DEATH, deathVolume, pitch)
    }

    private fun nextAmbientCooldown(): Long {
        val min = ambientIntervalMinTicks.coerceAtLeast(10L)
        val max = ambientIntervalMaxTicks.coerceAtLeast(min)
        return if (max == min) min else Random.nextLong(min, max + 1L)
    }
}
