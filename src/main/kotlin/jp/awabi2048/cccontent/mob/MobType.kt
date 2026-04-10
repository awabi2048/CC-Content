package jp.awabi2048.cccontent.mob

import org.bukkit.entity.Entity
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

    fun onGenericDamaged(context: MobGenericDamagedContext, runtime: CustomMobRuntime?) {}

    fun onDeath(context: MobDeathContext, runtime: CustomMobRuntime?) {}

    fun onCombust(context: MobCombustContext, runtime: CustomMobRuntime?) {}

    fun hasCustomRangedAttack(): Boolean = false
}

interface EntityMobType {
    val id: String
    val baseEntityType: EntityType
    val isCustom: Boolean
        get() = true

    fun createRuntime(context: EntityMobSpawnContext): CustomMobRuntime? = null

    fun onSpawn(context: EntityMobSpawnContext, runtime: CustomMobRuntime?) {}

    fun onTick(context: EntityMobRuntimeContext, runtime: CustomMobRuntime?) {}

    fun onDamaged(context: EntityMobDamagedContext, runtime: CustomMobRuntime?) {}

    fun onDeath(context: EntityMobDeathContext, runtime: CustomMobRuntime?) {}
}

data class VanillaEntityMobType(
    override val id: String,
    override val baseEntityType: EntityType
) : EntityMobType {
    override val isCustom: Boolean = false
}

fun EntityMobType.matches(entity: Entity): Boolean {
    return entity.type == baseEntityType
}

data class VanillaMobType(
    override val id: String,
    override val baseEntityType: EntityType
) : MobType {
    override val isCustom: Boolean = false
}
