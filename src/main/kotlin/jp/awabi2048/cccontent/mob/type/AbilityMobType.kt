package jp.awabi2048.cccontent.mob.type

import jp.awabi2048.cccontent.mob.CustomMobRuntime
import jp.awabi2048.cccontent.mob.MobAttackContext
import jp.awabi2048.cccontent.mob.MobCombustContext
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobGenericDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import jp.awabi2048.cccontent.mob.MobType
import jp.awabi2048.cccontent.mob.ability.CustomRangedAttackAbility
import jp.awabi2048.cccontent.mob.ability.MobAbility
import jp.awabi2048.cccontent.mob.ability.MobAbilityRuntime
import jp.awabi2048.cccontent.mob.ability.RangedAttackAbility
import org.bukkit.entity.EntityType
import kotlin.random.Random

abstract class AbilityMobType(
    override val id: String,
    override val baseEntityType: EntityType,
    private val abilities: List<MobAbility>
) : MobType {

    override fun hasCustomRangedAttack(): Boolean {
        return abilities.any { it is RangedAttackAbility || it is CustomRangedAttackAbility }
    }

    data class AbilityTickState(var nextDueTick: Long)

    data class Runtime(
        val abilityRuntimes: List<MobAbilityRuntime?>,
        val abilityTickStates: List<AbilityTickState>
    ) : CustomMobRuntime

    override fun createRuntime(context: MobSpawnContext): CustomMobRuntime {
        val runtimes = abilities.map { ability -> ability.createRuntime(context) }
        val tickStates = abilities.map { ability ->
            val interval = ability.tickIntervalTicks().coerceAtLeast(1L)
            AbilityTickState(nextDueTick = if (interval <= 1L) 0L else Random.nextLong(interval))
        }
        return Runtime(runtimes, tickStates)
    }

    override fun onSpawn(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        forEachAbility(runtime) { ability, abilityRuntime ->
            ability.onSpawn(context, abilityRuntime)
        }
    }

    override fun onTick(context: MobRuntimeContext, runtime: CustomMobRuntime?) {
        val typedRuntime = runtime as? Runtime
        val abilityRuntimes = typedRuntime?.abilityRuntimes
        val tickStates = typedRuntime?.abilityTickStates
        abilities.forEachIndexed { index, ability ->
            val tickState = tickStates?.getOrNull(index)
            if (tickState == null) {
                ability.onTick(context, abilityRuntimes?.getOrNull(index))
                return@forEachIndexed
            }

            val currentTick = context.activeMob.tickCount
            if (currentTick < tickState.nextDueTick) {
                return@forEachIndexed
            }

            val baseInterval = ability.tickIntervalTicks().coerceAtLeast(1L)
            val effectiveInterval = if (ability.useLoadAdaptiveTickInterval()) {
                baseInterval * context.loadSnapshot.searchIntervalMultiplier.coerceAtLeast(1)
            } else {
                baseInterval
            }
            tickState.nextDueTick = currentTick + effectiveInterval.coerceAtLeast(1L)
            ability.onTick(context.copy(tickDelta = effectiveInterval.coerceAtLeast(1L)), abilityRuntimes?.getOrNull(index))
        }
    }

    override fun onAttack(context: MobAttackContext, runtime: CustomMobRuntime?) {
        forEachAbility(runtime) { ability, abilityRuntime ->
            ability.onAttack(context, abilityRuntime)
        }
    }

    override fun onDamaged(context: MobDamagedContext, runtime: CustomMobRuntime?) {
        forEachAbility(runtime) { ability, abilityRuntime ->
            ability.onDamaged(context, abilityRuntime)
        }
    }

    override fun onGenericDamaged(context: MobGenericDamagedContext, runtime: CustomMobRuntime?) {
        forEachAbility(runtime) { ability, abilityRuntime ->
            ability.onGenericDamaged(context, abilityRuntime)
        }
    }

    override fun onDeath(context: MobDeathContext, runtime: CustomMobRuntime?) {
        forEachAbility(runtime) { ability, abilityRuntime ->
            ability.onDeath(context, abilityRuntime)
        }
    }

    override fun onCombust(context: MobCombustContext, runtime: CustomMobRuntime?) {
        forEachAbility(runtime) { ability, abilityRuntime ->
            ability.onCombust(context, abilityRuntime)
        }
    }

    private inline fun forEachAbility(
        runtime: CustomMobRuntime?,
        block: (ability: MobAbility, runtime: MobAbilityRuntime?) -> Unit
    ) {
        val abilityRuntimes = (runtime as? Runtime)?.abilityRuntimes
        abilities.forEachIndexed { index, ability ->
            block(ability, abilityRuntimes?.getOrNull(index))
        }
    }
}
