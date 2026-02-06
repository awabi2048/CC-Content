package jp.awabi2048.cccontent.features.rank.tutorial.task

/**
 * ランクアップのためのタスク要件を定義するクラス
 */
data class TaskRequirement(
    /** 必要なプレイ時間（分） */
    val playTimeMin: Int = 0,
    
    /** 必要なモブ討伐数（モブタイプ -> 数） */
    val mobKills: Map<String, Int> = emptyMap(),
    
    /** 必要なブロック採掘数（ブロックタイプ -> 数） */
    val blockMines: Map<String, Int> = emptyMap(),
    
    /** 必要なバニラEXP */
    val vanillaExp: Long = 0L,
    
    /** 必要なアイテム（Material名 -> 数） */
    val itemsRequired: Map<String, Int> = emptyMap(),
    
    /** 必要なボス討伐数（ボスタイプ -> 数） */
    val bossKills: Map<String, Int> = emptyMap()
) {
    /**
     * すべてのタスク要件が空（要件なし）かどうかを判定
     */
    fun isEmpty(): Boolean {
        return playTimeMin == 0 &&
                mobKills.isEmpty() &&
                blockMines.isEmpty() &&
                vanillaExp == 0L &&
                itemsRequired.isEmpty() &&
                bossKills.isEmpty()
    }
    
    /**
     * 必要なモブ討伐数を取得
     */
    fun getRequiredMobKills(mobType: String): Int = mobKills[mobType] ?: 0
    
    /**
     * 必要なブロック採掘数を取得
     */
    fun getRequiredBlockMines(blockType: String): Int = blockMines[blockType] ?: 0
    
    /**
     * 必要なボス討伐数を取得
     */
    fun getRequiredBossKills(bossType: String): Int = bossKills[bossType] ?: 0
    
    /**
     * 必要なアイテム数を取得
     */
    fun getRequiredItemCount(material: String): Int = itemsRequired[material] ?: 0
}
