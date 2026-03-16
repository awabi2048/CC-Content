package jp.awabi2048.cccontent.mob

import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

data class MobSpawnOptions(
    val featureId: String,
    val combatActiveProvider: (() -> Boolean)? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class ActiveMob(
    val entityId: UUID,
    val definition: MobDefinition,
    val mobType: MobType,
    val featureId: String,
    val combatActiveProvider: (() -> Boolean)?,
    val metadata: Map<String, String>,
    val runtime: CustomMobRuntime?,
    var tickCount: Long = 0L
) {
    fun isCombatActive(): Boolean {
        return combatActiveProvider?.invoke() ?: true
    }
}

data class MobSpawnContext(
    val plugin: JavaPlugin,
    val entity: LivingEntity,
    val definition: MobDefinition,
    val mobType: MobType,
    val options: MobSpawnOptions
) {
    val featureId: String
        get() = options.featureId

    fun isCombatActive(): Boolean {
        return options.combatActiveProvider?.invoke() ?: true
    }
}

data class MobRuntimeContext(
    val plugin: JavaPlugin,
    val entity: LivingEntity,
    val activeMob: ActiveMob
) {
    val definition: MobDefinition
        get() = activeMob.definition
    val mobType: MobType
        get() = activeMob.mobType
    val featureId: String
        get() = activeMob.featureId

    fun isCombatActive(): Boolean {
        return activeMob.isCombatActive()
    }
}

data class MobAttackContext(
    val plugin: JavaPlugin,
    val entity: LivingEntity,
    val activeMob: ActiveMob,
    val event: EntityDamageByEntityEvent,
    val target: LivingEntity?
) {
    val definition: MobDefinition
        get() = activeMob.definition

    fun isCombatActive(): Boolean {
        return activeMob.isCombatActive()
    }
}

data class MobDamagedContext(
    val plugin: JavaPlugin,
    val entity: LivingEntity,
    val activeMob: ActiveMob,
    val event: EntityDamageByEntityEvent,
    val attacker: LivingEntity?
) {
    val definition: MobDefinition
        get() = activeMob.definition

    fun isCombatActive(): Boolean {
        return activeMob.isCombatActive()
    }
}

data class MobDeathContext(
    val plugin: JavaPlugin,
    val entity: LivingEntity,
    val activeMob: ActiveMob,
    val event: EntityDeathEvent
) {
    val definition: MobDefinition
        get() = activeMob.definition
}
