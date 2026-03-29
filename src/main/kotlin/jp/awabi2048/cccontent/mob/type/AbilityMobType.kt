package jp.awabi2048.cccontent.mob.type

import jp.awabi2048.cccontent.mob.CustomMobRuntime
import jp.awabi2048.cccontent.mob.MobAttackContext
import jp.awabi2048.cccontent.mob.MobCombustContext
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobGenericDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobShootBowContext
import jp.awabi2048.cccontent.mob.MobSpawnContext
import jp.awabi2048.cccontent.mob.MobType
import jp.awabi2048.cccontent.mob.ability.MobAbility
import jp.awabi2048.cccontent.mob.ability.MobAbilityRuntime
import org.bukkit.entity.EntityType

abstract class AbilityMobType(
    override val id: String,
    override val baseEntityType: EntityType,
    private val abilities: List<MobAbility>
) : MobType {

    data class Runtime(val abilityRuntimes: List<MobAbilityRuntime?>) : CustomMobRuntime

    override fun createRuntime(context: MobSpawnContext): CustomMobRuntime {
        return Runtime(abilities.map { ability -> ability.createRuntime(context) })
    }

    override fun onSpawn(context: MobSpawnContext, runtime: CustomMobRuntime?) {
        forEachAbility(runtime) { ability, abilityRuntime ->
            ability.onSpawn(context, abilityRuntime)
        }
    }

    override fun onTick(context: MobRuntimeContext, runtime: CustomMobRuntime?) {
        forEachAbility(runtime) { ability, abilityRuntime ->
            ability.onTick(context, abilityRuntime)
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

    override fun onShootBow(context: MobShootBowContext, runtime: CustomMobRuntime?) {
        forEachAbility(runtime) { ability, abilityRuntime ->
            ability.onShootBow(context, abilityRuntime)
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
