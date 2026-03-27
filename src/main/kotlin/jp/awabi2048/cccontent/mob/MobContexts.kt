package jp.awabi2048.cccontent.mob

import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

data class MobSpawnOptions(
    val featureId: String,
    val sessionKey: String? = null,
    val combatActiveProvider: (() -> Boolean)? = null,
    val metadata: Map<String, String> = emptyMap()
)

enum class MobLoadLevel {
    NORMAL,
    WARM,
    HOT,
    CRITICAL
}

data class MobLoadSnapshot(
    val level: MobLoadLevel,
    val score: Double,
    val abilityCooldownMultiplier: Double,
    val abilityExecutionSkipChance: Double,
    val searchIntervalMultiplier: Int,
    val spawnIntervalMultiplier: Double,
    val spawnSkipChance: Double
) {
    companion object {
        val DEFAULT = MobLoadSnapshot(
            level = MobLoadLevel.NORMAL,
            score = 0.0,
            abilityCooldownMultiplier = 1.0,
            abilityExecutionSkipChance = 0.0,
            searchIntervalMultiplier = 1,
            spawnIntervalMultiplier = 1.0,
            spawnSkipChance = 0.0
        )
    }
}

data class MobSpawnThrottle(
    val intervalMultiplier: Double,
    val skipChance: Double
)

data class ActiveMob(
    val entityId: UUID,
    val definition: MobDefinition,
    val mobType: MobType,
    val featureId: String,
    val sessionKey: String,
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

    val sessionKey: String?
        get() = options.sessionKey

    fun isCombatActive(): Boolean {
        return options.combatActiveProvider?.invoke() ?: true
    }
}

data class MobRuntimeContext(
    val plugin: JavaPlugin,
    val entity: LivingEntity,
    val activeMob: ActiveMob,
    val loadSnapshot: MobLoadSnapshot
) {
    val definition: MobDefinition
        get() = activeMob.definition
    val mobType: MobType
        get() = activeMob.mobType
    val featureId: String
        get() = activeMob.featureId

    val sessionKey: String
        get() = activeMob.sessionKey

    fun isCombatActive(): Boolean {
        return activeMob.isCombatActive()
    }
}

data class MobAttackContext(
    val plugin: JavaPlugin,
    val entity: LivingEntity,
    val activeMob: ActiveMob,
    val event: EntityDamageByEntityEvent,
    val target: LivingEntity?,
    val loadSnapshot: MobLoadSnapshot
) {
    val definition: MobDefinition
        get() = activeMob.definition

    fun isCombatActive(): Boolean {
        return activeMob.isCombatActive()
    }

    val sessionKey: String
        get() = activeMob.sessionKey
}

data class MobDamagedContext(
    val plugin: JavaPlugin,
    val entity: LivingEntity,
    val activeMob: ActiveMob,
    val event: EntityDamageByEntityEvent,
    val attacker: LivingEntity?,
    val loadSnapshot: MobLoadSnapshot
) {
    val definition: MobDefinition
        get() = activeMob.definition

    fun isCombatActive(): Boolean {
        return activeMob.isCombatActive()
    }

    val sessionKey: String
        get() = activeMob.sessionKey
}

data class MobDeathContext(
    val plugin: JavaPlugin,
    val entity: LivingEntity,
    val activeMob: ActiveMob,
    val event: EntityDeathEvent,
    val loadSnapshot: MobLoadSnapshot
) {
    val definition: MobDefinition
        get() = activeMob.definition

    val sessionKey: String
        get() = activeMob.sessionKey
}

data class MobShootBowContext(
    val plugin: JavaPlugin,
    val entity: LivingEntity,
    val activeMob: ActiveMob,
    val event: EntityShootBowEvent,
    val loadSnapshot: MobLoadSnapshot
) {
    val definition: MobDefinition
        get() = activeMob.definition

    val sessionKey: String
        get() = activeMob.sessionKey

    fun isCombatActive(): Boolean {
        return activeMob.isCombatActive()
    }
}
