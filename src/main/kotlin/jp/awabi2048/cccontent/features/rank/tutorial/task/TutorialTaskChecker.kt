package jp.awabi2048.cccontent.features.rank.tutorial.task

import org.bukkit.entity.Player

/**
 * チュートリアルランクのタスク完了状況を判定するインターフェース
 */
interface TutorialTaskChecker {
    
    /**
     * すべてのタスクが完了したか判定
     * @param progress プレイヤーのタスク進捗
     * @param requirement このランクの要件
     * @param player プレイヤーオブジェクト
     * @return すべてのタスク完了時true
     */
    fun isAllTasksComplete(
        progress: TaskProgress,
        requirement: TaskRequirement,
        player: Player
    ): Boolean
    
    /**
     * プレイ時間タスクが完了したか判定
     */
    fun isPlayTimeComplete(progress: TaskProgress, required: Int): Boolean
    
    /**
     * モブ討伐タスクが完了したか判定
     */
    fun isMobKillsComplete(progress: TaskProgress, required: Map<String, Int>): Boolean
    
    /**
     * ブロック採掘タスクが完了したか判定
     */
    fun isBlockMinesComplete(progress: TaskProgress, required: Map<String, Int>): Boolean
    
    /**
     * バニラEXPタスクが完了したか判定
     */
    fun isVanillaExpComplete(progress: TaskProgress, required: Long): Boolean
    
    /**
     * アイテム所持タスクが完了したか判定
     */
    fun isItemsComplete(player: Player, required: Map<String, Int>): Boolean
    
    /**
     * ボス討伐タスクが完了したか判定
     */
    fun isBossKillsComplete(progress: TaskProgress, required: Map<String, Int>): Boolean
    
    /**
     * タスク進捗度合を取得（0.0～1.0）
     * @return 進捗度（0.0～1.0）
     */
    fun getProgress(
        progress: TaskProgress,
        requirement: TaskRequirement,
        player: Player
    ): Double
}
