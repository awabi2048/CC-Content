package jp.awabi2048.cccontent.features.arena.quest

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID
import jp.awabi2048.cccontent.features.arena.ArenaI18n

data class ArenaDailyQuestEntry(
    val index: Int,
    val missionTypeId: String,
    val difficultyScore: Double,
    val difficultyId: String,
    val themeId: String,
    val maxParticipants: Int = 6
)

data class ArenaDailyQuestSet(
    val dateKey: String,
    val generatedAtMillis: Long,
    val quests: List<ArenaDailyQuestEntry>
)

data class ArenaPlayerQuestData(
    var totalClearCount: Int = 0,
    var totalMobKillCount: Int = 0,
    var barrierRestartCount: Int = 0,
    val completedByDate: MutableMap<String, MutableSet<Int>> = mutableMapOf()
) {
    fun isCompleted(dateKey: String, questIndex: Int): Boolean {
        return completedByDate[dateKey]?.contains(questIndex) == true
    }

    fun markCompleted(dateKey: String, questIndex: Int): Boolean {
        val completed = completedByDate.getOrPut(dateKey) { mutableSetOf() }
        if (!completed.add(questIndex)) {
            return false
        }
        totalClearCount += 1
        return true
    }

    fun addMobKillCount(amount: Int = 1) {
        if (amount <= 0) return
        totalMobKillCount += amount
    }

    fun addBarrierRestartCount(amount: Int = 1) {
        if (amount <= 0) return
        barrierRestartCount += amount
    }
}

data class ArenaDifficultyDefinition(
    val id: String,
    val difficultyRange: ClosedFloatingPointRange<Double>,
    val display: String
)

enum class ArenaQuestMissionType(
    val id: String,
    val displayNameKey: String,
    val missionGuideHintsKey: String
) {
    BARRIER_RESTART(
        id = "barrier_restart",
        displayNameKey = "arena.quest.mission.barrier_restart.name",
        missionGuideHintsKey = "arena.quest.mission.barrier_restart.hints"
    ),
    BARRIER_DEPLOY(
        id = "barrier_deploy",
        displayNameKey = "arena.quest.mission.barrier_deploy.name",
        missionGuideHintsKey = "arena.quest.mission.barrier_deploy.hints"
    ),
    SWEEP(
        id = "sweep",
        displayNameKey = "arena.quest.mission.sweep.name",
        missionGuideHintsKey = "arena.quest.mission.sweep.hints"
    ),
    BOSS(
        id = "boss",
        displayNameKey = "arena.quest.mission.boss.name",
        missionGuideHintsKey = "arena.quest.mission.boss.hints"
    );

    companion object {
        private val BY_ID = entries.associateBy { it.id }

        fun fromId(id: String): ArenaQuestMissionType? = BY_ID[id]
    }
}

data class ArenaQuestModifiers(
    val charactorId: String,
    val displayName: String,
    val spawnIntervalMultiplier: Double,
    val maxSummonCountMultiplier: Double,
    val clearMobCountMultiplier: Double,
    val mobHealthMultiplier: Double,
    val mobAttackMultiplier: Double
) {
    companion object {
        val NONE = ArenaQuestModifiers(
            charactorId = "none",
            displayName = "特になし",
            spawnIntervalMultiplier = 1.0,
            maxSummonCountMultiplier = 1.0,
            clearMobCountMultiplier = 1.0,
            mobHealthMultiplier = 1.0,
            mobAttackMultiplier = 1.0
        )
    }
}

data class ArenaActiveQuestRecord(
    val dateKey: String,
    val questIndex: Int,
    val quest: ArenaDailyQuestEntry
)

object ArenaQuestLayout {
    const val MENU_SIZE = 54
    const val CONFIRM_SIZE = 45

    val MENU_TITLE: String
        get() = ArenaI18n.text(null, "arena.ui.menu_title", "§8アリーナ掲示板")

    val CONFIRM_TITLE: String
        get() = ArenaI18n.text(null, "arena.ui.confirm_title", "§8参加確認")

    val MENU_QUEST_SLOTS = listOf(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34)
    const val MENU_PLAYER_SLOT = 47
    const val MENU_INFO_SLOT = 49
    const val MENU_REFRESH_SLOT = 51

    const val CONFIRM_OK_SLOT = 20
    const val CONFIRM_QUEST_SLOT = 22
    const val CONFIRM_CANCEL_SLOT = 24

    fun questIndexForSlot(slot: Int): Int? {
        return MENU_QUEST_SLOTS.indexOf(slot).takeIf { it >= 0 }
    }
}

class ArenaQuestMenuHolder(
    val ownerId: UUID,
    val dateKey: String
) : InventoryHolder {
    var backingInventory: Inventory? = null

    override fun getInventory(): Inventory {
        return backingInventory ?: Bukkit.createInventory(this, ArenaQuestLayout.MENU_SIZE, ArenaQuestLayout.MENU_TITLE)
    }
}

class ArenaQuestConfirmHolder(
    val ownerId: UUID,
    val dateKey: String,
    val questIndex: Int
) : InventoryHolder {
    var backingInventory: Inventory? = null

    override fun getInventory(): Inventory {
        return backingInventory ?: Bukkit.createInventory(this, ArenaQuestLayout.CONFIRM_SIZE, ArenaQuestLayout.CONFIRM_TITLE)
    }
}
