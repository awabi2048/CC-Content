package jp.awabi2048.cccontent.mob

import org.bukkit.entity.EntityType

interface CustomMobRuntime

interface MobType {
    val id: String
    val baseEntityType: EntityType
    val isCustom: Boolean
        get() = true

    fun createRuntime(context: MobSpawnContext): CustomMobRuntime? = null

    fun applyDefaultEquipment(context: MobSpawnContext, runtime: CustomMobRuntime?) {}

    fun onSpawn(context: MobSpawnContext, runtime: CustomMobRuntime?) {}

    fun onTick(context: MobRuntimeContext, runtime: CustomMobRuntime?) {}

    fun onAttack(context: MobAttackContext, runtime: CustomMobRuntime?) {}

    fun onDamaged(context: MobDamagedContext, runtime: CustomMobRuntime?) {}

    fun onDeath(context: MobDeathContext, runtime: CustomMobRuntime?) {}

    fun onShootBow(context: MobShootBowContext, runtime: CustomMobRuntime?) {}
}

data class VanillaMobType(
    override val id: String,
    override val baseEntityType: EntityType
) : MobType {
    override val isCustom: Boolean = false
}
