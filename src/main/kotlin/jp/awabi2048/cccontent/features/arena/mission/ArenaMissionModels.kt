package jp.awabi2048.cccontent.features.arena.mission

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID
import jp.awabi2048.cccontent.features.arena.ArenaI18n

data class ArenaDailyMissionEntry(
    val index: Int,
    val missionTypeId: String,
    val difficultyId: String,
    val themeId: String,
    val maxParticipants: Int = 6
)

data class ArenaDailyMissionSet(
    val dateKey: String,
    val generatedAtMillis: Long,
    val missions: List<ArenaDailyMissionEntry>
)

data class ArenaPlayerMissionData(
    var totalMissionClearCount: Int = 0,
    var totalMobKillCount: Int = 0,
    var totalStrongEnemyKillCount: Int = 0,
    var totalOverEnchantSuccessCount: Int = 0,
    var barrierRestartCount: Int = 0,
    var licenseTier: ArenaLicenseTier = ArenaLicenseTier.PAPER,
    val completedByDate: MutableMap<String, MutableSet<Int>> = mutableMapOf()
) {
    fun isCompleted(dateKey: String, missionIndex: Int): Boolean {
        return completedByDate[dateKey]?.contains(missionIndex) == true
    }

    fun markCompleted(dateKey: String, missionIndex: Int): Boolean {
        val completed = completedByDate.getOrPut(dateKey) { mutableSetOf() }
        if (!completed.add(missionIndex)) {
            return false
        }
        totalMissionClearCount += 1
        return true
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
}

data class ArenaDifficultyDefinition(
    val id: String,
    val display: String,
    val starLevel: Int
)

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
    val dateKey: String,
    val missionIndex: Int,
    val mission: ArenaDailyMissionEntry
)

object ArenaMissionLayout {
    const val MENU_SIZE = 54
    const val CONFIRM_SIZE = 45

    val MENU_TITLE: String
        get() = ArenaI18n.text(null, "arena.ui.menu_title", "§8アリーナ掲示板")

    val CONFIRM_TITLE: String
        get() = ArenaI18n.text(null, "arena.ui.confirm_title", "§8参加確認")

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
    val ownerId: UUID,
    val dateKey: String
) : InventoryHolder {
    var backingInventory: Inventory? = null

    override fun getInventory(): Inventory {
        return backingInventory ?: Bukkit.createInventory(this, ArenaMissionLayout.MENU_SIZE, ArenaMissionLayout.MENU_TITLE)
    }
}

class ArenaMissionConfirmHolder(
    val ownerId: UUID,
    val dateKey: String,
    val missionIndex: Int
) : InventoryHolder {
    var backingInventory: Inventory? = null

    override fun getInventory(): Inventory {
        return backingInventory ?: Bukkit.createInventory(this, ArenaMissionLayout.CONFIRM_SIZE, ArenaMissionLayout.CONFIRM_TITLE)
    }
}
