package jp.awabi2048.cccontent.features.arena.mission

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID
import jp.awabi2048.cccontent.features.arena.ArenaI18n

data class ArenaMissionEntry(
    val index: Int,
    val missionTypeId: String,
    val themeId: String,
    val promoted: Boolean,
    val difficultyStar: Int,
    val maxParticipants: Int = 6
)

data class ArenaMissionSet(
    val generatedAtMillis: Long,
    val missions: List<ArenaMissionEntry>
)

data class ArenaPlayerMissionData(
    var totalMissionClearCount: Int = 0,
    var totalMobKillCount: Int = 0,
    var totalStrongEnemyKillCount: Int = 0,
    var totalOverEnchantSuccessCount: Int = 0,
    var barrierRestartCount: Int = 0,
    var lobbyVisited: Boolean = false,
    var lobbyTutorialCompleted: Boolean = false,
    var licenseTier: ArenaLicenseTier = ArenaLicenseTier.PAPER,
    val completedMissionIndices: MutableSet<Int> = mutableSetOf(),
    val enchantShardKillCounters: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
) {
    fun isCompleted(missionIndex: Int): Boolean {
        return completedMissionIndices.contains(missionIndex)
    }

    fun markCompleted(missionIndex: Int): Boolean {
        if (!completedMissionIndices.add(missionIndex)) {
            return false
        }
        totalMissionClearCount += 1
        return true
    }

    fun clearCurrentMissionCompletions() {
        completedMissionIndices.clear()
    }

    fun addMobKillCount(amount: Int = 1) {
        if (amount <= 0) return
        totalMobKillCount += amount
    }

    fun addStrongEnemyKillCount(amount: Int = 1) {
        if (amount <= 0) return
        totalStrongEnemyKillCount += amount
    }

    fun addOverEnchantSuccessCount(amount: Int = 1) {
        if (amount <= 0) return
        totalOverEnchantSuccessCount += amount
    }

    fun addBarrierRestartCount(amount: Int = 1) {
        if (amount <= 0) return
        barrierRestartCount += amount
    }

    fun getEnchantShardKillCount(shardKey: String, mobDefinitionId: String): Int {
        return enchantShardKillCounters[shardKey]?.get(mobDefinitionId)?.coerceAtLeast(0) ?: 0
    }

    fun recordEnchantShardFailure(shardKey: String, mobDefinitionId: String, attemptCount: Int) {
        if (attemptCount <= 0) return
        enchantShardKillCounters.getOrPut(shardKey) { mutableMapOf() }[mobDefinitionId] = attemptCount
    }

    fun resetEnchantShardCounter(shardKey: String, mobDefinitionId: String) {
        val byMob = enchantShardKillCounters[shardKey] ?: return
        byMob.remove(mobDefinitionId)
        if (byMob.isEmpty()) {
            enchantShardKillCounters.remove(shardKey)
        }
    }

    fun setLobbyVisited() {
        lobbyVisited = true
    }

    fun setLobbyTutorialCompleted() {
        lobbyVisited = true
        lobbyTutorialCompleted = true
    }
}

enum class ArenaLicenseTier(
    val id: String,
    val maxDifficultyStar: Int,
    val displayNameKey: String
) {
    PAPER(
        id = "paper",
        maxDifficultyStar = 1,
        displayNameKey = "arena.ui.license.tier.paper"
    ),
    BRONZE(
        id = "bronze",
        maxDifficultyStar = 2,
        displayNameKey = "arena.ui.license.tier.bronze"
    ),
    SILVER(
        id = "silver",
        maxDifficultyStar = 3,
        displayNameKey = "arena.ui.license.tier.silver"
    ),
    GOLD(
        id = "gold",
        maxDifficultyStar = 4,
        displayNameKey = "arena.ui.license.tier.gold"
    );

    fun next(): ArenaLicenseTier? {
        val index = entries.indexOf(this)
        if (index < 0 || index + 1 >= entries.size) return null
        return entries[index + 1]
    }

    companion object {
        private val BY_ID = entries.associateBy { it.id }

        fun fromId(id: String): ArenaLicenseTier? {
            return BY_ID[id.trim().lowercase()]
        }

        fun requiredTierForDifficultyStar(star: Int): ArenaLicenseTier {
            if (star <= 0) return PAPER
            return entries.firstOrNull { star <= it.maxDifficultyStar } ?: GOLD
        }
    }
}

data class ArenaLicenseRequirement(
    val targetTier: ArenaLicenseTier,
    val requiredMissionClearCount: Int,
    val requiredMobKillCount: Int,
    val requiredStrongEnemyKillCount: Int
)

data class ArenaStatusSnapshot(
    val currentMissionGeneratedAtMillis: Long?,
    val hasCurrentMissionSet: Boolean,
    val loadedPlayerRecords: Int,
    val lobbyProgressCount: Int,
    val lobbyTutorialCompletedCount: Int,
    val activeMissionCount: Int,
    val strongEnemyMobTypeCount: Int,
    val generateCount: Int,
    val themeCount: Int
)

enum class ArenaMissionType(
    val id: String,
    val displayNameKey: String,
    val missionGuideHintsKey: String
) {
    BARRIER_RESTART(
        id = "barrier_restart",
        displayNameKey = "arena.mission.type.barrier_restart.name",
        missionGuideHintsKey = "arena.mission.type.barrier_restart.hints"
    ),
    BARRIER_DEPLOY(
        id = "barrier_deploy",
        displayNameKey = "arena.mission.type.barrier_deploy.name",
        missionGuideHintsKey = "arena.mission.type.barrier_deploy.hints"
    ),
    SWEEP(
        id = "sweep",
        displayNameKey = "arena.mission.type.sweep.name",
        missionGuideHintsKey = "arena.mission.type.sweep.hints"
    ),
    BOSS(
        id = "boss",
        displayNameKey = "arena.mission.type.boss.name",
        missionGuideHintsKey = "arena.mission.type.boss.hints"
    ),
    CLEARING(
        id = "clearing",
        displayNameKey = "arena.mission.type.clearing.name",
        missionGuideHintsKey = "arena.mission.type.clearing.hints"
    );

    companion object {
        private val BY_ID = entries.associateBy { it.id }

        fun fromId(id: String): ArenaMissionType? = BY_ID[id]
    }
}

data class ArenaMissionModifiers(
    val charactorId: String,
    val displayName: String,
    val spawnIntervalMultiplier: Double,
    val maxSummonCountMultiplier: Double,
    val clearMobCountMultiplier: Double,
    val mobHealthMultiplier: Double,
    val mobAttackMultiplier: Double
) {
    companion object {
        val NONE = ArenaMissionModifiers(
            charactorId = "none",
            displayName = "特になし",
            spawnIntervalMultiplier = 1.0,
            maxSummonCountMultiplier = 1.0,
            clearMobCountMultiplier = 1.0,
            mobHealthMultiplier = 1.0,
            mobAttackMultiplier = 1.0
        )

        val CLEARING = ArenaMissionModifiers(
            charactorId = "clearing",
            displayName = "掃討戦",
            spawnIntervalMultiplier = 1.0,
            maxSummonCountMultiplier = 2.0,
            clearMobCountMultiplier = 2.0,
            mobHealthMultiplier = 0.5,
            mobAttackMultiplier = 1.0
        )
    }
}

data class ArenaActiveMissionRecord(
    val missionIndex: Int,
    val mission: ArenaMissionEntry
)

object ArenaMissionLayout {
    const val MENU_SIZE = 54
    const val CONFIRM_SIZE = 45

    val MENU_TITLE: String
        get() = ArenaI18n.text(null, "arena.ui.menu_title")

    val CONFIRM_TITLE: String
        get() = ArenaI18n.text(null, "arena.ui.confirm_title")

    val MENU_MISSION_SLOTS = listOf(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34)
    const val MENU_PLAYER_SLOT = 47
    const val MENU_INFO_SLOT = 49
    const val MENU_REFRESH_SLOT = 51

    const val CONFIRM_OK_SLOT = 20
    const val CONFIRM_MISSION_SLOT = 22
    const val CONFIRM_CANCEL_SLOT = 24

    fun missionIndexForSlot(slot: Int): Int? {
        return MENU_MISSION_SLOTS.indexOf(slot).takeIf { it >= 0 }
    }
}

class ArenaMissionMenuHolder(
    val ownerId: UUID
) : InventoryHolder {
    var backingInventory: Inventory? = null

    override fun getInventory(): Inventory {
        return backingInventory ?: Bukkit.createInventory(this, ArenaMissionLayout.MENU_SIZE, ArenaMissionLayout.MENU_TITLE)
    }
}

class ArenaMissionConfirmHolder(
    val ownerId: UUID,
    val missionIndex: Int
) : InventoryHolder {
    var backingInventory: Inventory? = null

    override fun getInventory(): Inventory {
        return backingInventory ?: Bukkit.createInventory(this, ArenaMissionLayout.CONFIRM_SIZE, ArenaMissionLayout.CONFIRM_TITLE)
    }
}
