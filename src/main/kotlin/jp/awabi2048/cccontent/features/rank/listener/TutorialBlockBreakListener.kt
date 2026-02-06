package jp.awabi2048.cccontent.features.rank.listener

import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskChecker
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskLoader
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent

/**
 * ブロックが採掘されたときのリスナー
 * - ブロック採掘数をカウント
 * - タスク完了判定を実行
 * - 自動ランクアップ
 * - データを保存
 */
class TutorialBlockBreakListener(
    private val rankManager: RankManager,
    private val taskChecker: TutorialTaskChecker,
    private val taskLoader: TutorialTaskLoader,
    private val storage: RankStorage
) : Listener {
    
    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val block = event.block
        val blockType = block.type.name.lowercase()
        
        val tutorial = rankManager.getPlayerTutorial(uuid)
        
        // ブロック採掘数をカウント
        tutorial.taskProgress.addBlockMine(blockType)
        
        // タスク完了判定
        val requirement = taskLoader.getRequirement(tutorial.currentRank.name)
        if (taskChecker.isAllTasksComplete(tutorial.taskProgress, requirement, player)) {
            // すべてのタスクが完了したので、ランクアップ
            if (!tutorial.isMaxRank()) {
                rankManager.rankUpByTask(uuid)
            }
        }
        
        // データを保存
        storage.saveTutorialRank(tutorial)
    }
}
