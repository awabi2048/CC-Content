package jp.awabi2048.cccontent.features.rank.tutorial.task

import java.util.UUID

/**
 * プレイヤーのタスク進捗管理クラス
 * 各ランクのタスク完了状況を追跡します
 */
data class TaskProgress(
    /** プレイヤーのUUID */
    val playerUuid: UUID,
    
    /** 対象ランク */
    val rankId: String,
    
    /** プレイ時間（分） */
    var playTime: Long = 0L,
    
    /** モブ討伐数（モブタイプ -> 数） */
    var mobKills: MutableMap<String, Int> = mutableMapOf(),
    
    /** ブロック採掘数（ブロックタイプ -> 数） */
    var blockMines: MutableMap<String, Int> = mutableMapOf(),
    
    /** 累計バニラEXP */
    var vanillaExp: Long = 0L,
    
    /** ボス討伐数（ボスタイプ -> 数） */
    var bossKills: MutableMap<String, Int> = mutableMapOf(),
    
    /** アイテム所持数（Material名 -> 数） */
    var items: MutableMap<String, Int> = mutableMapOf()
) {
    /**
     * 特定のモブ討伐数を取得
     */
    fun getMobKillCount(mobType: String): Int = mobKills[mobType] ?: 0
    
    /**
     * モブ討伐数を追加
     */
    fun addMobKill(mobType: String, amount: Int = 1) {
        mobKills[mobType] = mobKills.getOrDefault(mobType, 0) + amount
    }
    
    /**
     * 特定のブロック採掘数を取得
     */
    fun getBlockMineCount(blockType: String): Int = blockMines[blockType] ?: 0
    
    /**
     * ブロック採掘数を追加
     */
    fun addBlockMine(blockType: String, amount: Int = 1) {
        blockMines[blockType] = blockMines.getOrDefault(blockType, 0) + amount
    }
    
    /**
     * 特定のボス討伐数を取得
     */
    fun getBossKillCount(bossType: String): Int = bossKills[bossType] ?: 0
    
    /**
     * ボス討伐数を追加
     */
    fun addBossKill(bossType: String, amount: Int = 1) {
        bossKills[bossType] = bossKills.getOrDefault(bossType, 0) + amount
    }
    
    /**
     * 特定のアイテム所持数を取得
     */
    fun getItemCount(material: String): Int = items[material] ?: 0
    
    /**
     * アイテム所持数を設定
     */
    fun setItemCount(material: String, amount: Int) {
        items[material] = amount
    }
    
    /**
     * すべてのタスク進捗をリセット
     */
    fun reset() {
        playTime = 0L
        mobKills.clear()
        blockMines.clear()
        vanillaExp = 0L
        bossKills.clear()
        items.clear()
    }
}
