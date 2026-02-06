package jp.awabi2048.cccontent.features.rank.tutorial.task

import org.bukkit.entity.Player

/**
 * チュートリアルランクのタスク完了状況を判定する実装クラス
 */
class TutorialTaskCheckerImpl : TutorialTaskChecker {
    
    /**
     * すべてのタスクが完了したか判定
     */
    override fun isAllTasksComplete(
        progress: TaskProgress,
        requirement: TaskRequirement,
        player: Player
    ): Boolean {
        // 要件が空（最終ランク）の場合は常に完了
        if (requirement.isEmpty()) {
            return true
        }
        
        return isPlayTimeComplete(progress, requirement.playTimeMin) &&
                isMobKillsComplete(progress, requirement.mobKills) &&
                isBlockMinesComplete(progress, requirement.blockMines) &&
                isVanillaExpComplete(progress, requirement.vanillaExp) &&
                isItemsComplete(player, requirement.itemsRequired) &&
                isBossKillsComplete(progress, requirement.bossKills)
    }
    
    /**
     * プレイ時間タスクが完了したか判定
     */
    override fun isPlayTimeComplete(progress: TaskProgress, required: Int): Boolean {
        if (required <= 0) {
            return true
        }
        return progress.playTime >= required
    }
    
    /**
     * モブ討伐タスクが完了したか判定
     */
    override fun isMobKillsComplete(progress: TaskProgress, required: Map<String, Int>): Boolean {
        if (required.isEmpty()) {
            return true
        }
        
        for ((mobType, count) in required) {
            if (progress.getMobKillCount(mobType) < count) {
                return false
            }
        }
        return true
    }
    
    /**
     * ブロック採掘タスクが完了したか判定
     */
    override fun isBlockMinesComplete(progress: TaskProgress, required: Map<String, Int>): Boolean {
        if (required.isEmpty()) {
            return true
        }
        
        for ((blockType, count) in required) {
            if (progress.getBlockMineCount(blockType) < count) {
                return false
            }
        }
        return true
    }
    
    /**
     * バニラEXPタスクが完了したか判定
     */
    override fun isVanillaExpComplete(progress: TaskProgress, required: Long): Boolean {
        if (required <= 0) {
            return true
        }
        return progress.vanillaExp >= required
    }
    
    /**
     * アイテム所持タスクが完了したか判定
     */
    override fun isItemsComplete(player: Player, required: Map<String, Int>): Boolean {
        if (required.isEmpty()) {
            return true
        }
        
        for ((material, count) in required) {
            // Material 名を大文字で統一して比較
            val materialName = material.uppercase()
            val totalCount = player.inventory.contents
                .filterNotNull()
                .filter { it.type.name.uppercase() == materialName }
                .sumOf { it.amount }
            
            if (totalCount < count) {
                return false
            }
        }
        return true
    }
    
    /**
     * ボス討伐タスクが完了したか判定
     */
    override fun isBossKillsComplete(progress: TaskProgress, required: Map<String, Int>): Boolean {
        if (required.isEmpty()) {
            return true
        }
        
        for ((bossType, count) in required) {
            if (progress.getBossKillCount(bossType) < count) {
                return false
            }
        }
        return true
    }
    
    /**
     * タスク進捗度合を取得（0.0～1.0）
     */
    override fun getProgress(
        progress: TaskProgress,
        requirement: TaskRequirement,
        player: Player
    ): Double {
        // 要件が空の場合は常に完了
        if (requirement.isEmpty()) {
            return 1.0
        }
        
        var completedTasks = 0
        var totalTasks = 0
        
        // プレイ時間タスク
        if (requirement.playTimeMin > 0) {
            totalTasks++
            if (isPlayTimeComplete(progress, requirement.playTimeMin)) {
                completedTasks++
            }
        }
        
        // モブ討伐タスク
        if (requirement.mobKills.isNotEmpty()) {
            totalTasks++
            if (isMobKillsComplete(progress, requirement.mobKills)) {
                completedTasks++
            }
        }
        
        // ブロック採掘タスク
        if (requirement.blockMines.isNotEmpty()) {
            totalTasks++
            if (isBlockMinesComplete(progress, requirement.blockMines)) {
                completedTasks++
            }
        }
        
        // バニラEXPタスク
        if (requirement.vanillaExp > 0) {
            totalTasks++
            if (isVanillaExpComplete(progress, requirement.vanillaExp)) {
                completedTasks++
            }
        }
        
        // アイテムタスク
        if (requirement.itemsRequired.isNotEmpty()) {
            totalTasks++
            if (isItemsComplete(player, requirement.itemsRequired)) {
                completedTasks++
            }
        }
        
        // ボス討伐タスク
        if (requirement.bossKills.isNotEmpty()) {
            totalTasks++
            if (isBossKillsComplete(progress, requirement.bossKills)) {
                completedTasks++
            }
        }
        
        return if (totalTasks == 0) 1.0 else (completedTasks.toDouble() / totalTasks.toDouble()).coerceIn(0.0, 1.0)
    }
}
