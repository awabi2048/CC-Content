package jp.awabi2048.cccontent.features.rank.tutorial.task

import java.util.UUID

/**
 * チュートリアルランクの進捗。
 *
 * 固定要件に対応する項目を明示的に持ち、設定キーの打ち間違いで経路が壊れないようにする。
 */
data class TaskProgress(
    val playerUuid: UUID,
    val rankId: String,
    var playTime: Long = 0L,
    var mobKills: MutableMap<String, Int> = mutableMapOf(),
    var blockMines: MutableMap<String, Int> = mutableMapOf(),
    var vanillaExp: Long = 0L,
    var bossKills: MutableMap<String, Int> = mutableMapOf(),
    var items: MutableMap<String, Int> = mutableMapOf(),
    var myWorldCreated: Boolean = false,
    var activeOverworldTime: Long = 0L,
    var diamondOresMined: Int = 0,
    var netherPortalIgnited: Boolean = false,
    var activeNetherResourceTime: Long = 0L,
    var enderEyesCrafted: Int = 0,
    var endPortalOpened: Boolean = false
) {
    fun getMobKillCount(mobType: String): Int = mobKills[mobType] ?: 0

    fun addMobKill(mobType: String, amount: Int = 1) {
        mobKills[mobType] = mobKills.getOrDefault(mobType, 0) + amount
    }

    fun getBlockMineCount(blockType: String): Int = blockMines[blockType] ?: 0

    fun addBlockMine(blockType: String, amount: Int = 1) {
        blockMines[blockType] = blockMines.getOrDefault(blockType, 0) + amount
    }

    fun getBossKillCount(bossType: String): Int = bossKills[bossType] ?: 0

    fun addBossKill(bossType: String, amount: Int = 1) {
        bossKills[bossType] = bossKills.getOrDefault(bossType, 0) + amount
    }

    fun getItemCount(material: String): Int = items[material] ?: 0

    fun setItemCount(material: String, amount: Int) {
        items[material] = amount
    }

    fun reset() {
        playTime = 0L
        mobKills.clear()
        blockMines.clear()
        vanillaExp = 0L
        bossKills.clear()
        items.clear()
        myWorldCreated = false
        activeOverworldTime = 0L
        diamondOresMined = 0
        netherPortalIgnited = false
        activeNetherResourceTime = 0L
        enderEyesCrafted = 0
        endPortalOpened = false
    }
}
