package jp.awabi2048.cccontent.mob.ability

import jp.awabi2048.cccontent.mob.MobAttackContext
import jp.awabi2048.cccontent.mob.MobCombustContext
import jp.awabi2048.cccontent.mob.MobDamagedContext
import jp.awabi2048.cccontent.mob.MobDeathContext
import jp.awabi2048.cccontent.mob.MobGenericDamagedContext
import jp.awabi2048.cccontent.mob.MobRuntimeContext
import jp.awabi2048.cccontent.mob.MobSpawnContext

interface MobAbilityRuntime

interface MobAbility {
    val id: String

    fun createRuntime(context: MobSpawnContext): MobAbilityRuntime? = null

    fun onSpawn(context: MobSpawnContext, runtime: MobAbilityRuntime?) {}

    fun onTick(context: MobRuntimeContext, runtime: MobAbilityRuntime?) {}

    fun onAttack(context: MobAttackContext, runtime: MobAbilityRuntime?) {}

    fun onDamaged(context: MobDamagedContext, runtime: MobAbilityRuntime?) {}

    fun onGenericDamaged(context: MobGenericDamagedContext, runtime: MobAbilityRuntime?) {}

    fun onDeath(context: MobDeathContext, runtime: MobAbilityRuntime?) {}

    fun onCombust(context: MobCombustContext, runtime: MobAbilityRuntime?) {}
}
