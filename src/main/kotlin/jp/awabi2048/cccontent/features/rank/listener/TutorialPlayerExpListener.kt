package jp.awabi2048.cccontent.features.rank.listener

import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRankManager
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskChecker
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskLoader
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerExpChangeEvent

/**
 * プレイヤーがバニラEXPを獲得したときのリスナー
 * - バニラEXPの累計を加算
 * - タスク完了判定を実行
 * - 自動ランクアップ
 * - データを保存
 */
class TutorialPlayerExpListener(
    private val rankManager: TutorialRankManager,
    private val taskChecker: TutorialTaskChecker,
    private val taskLoader: TutorialTaskLoader,
    private val storage: RankStorage
) : Listener {
    
    @EventHandler
    fun onPlayerExpChange(event: PlayerExpChangeEvent) {
        // 経験値を失った場合は処理しない
        if (event.amount <= 0) {
            return
        }
        
        val player = event.player
        val uuid = player.uniqueId
        
        val tutorial = rankManager.getPlayerTutorial(uuid)
        
        // バニラEXPを加算
        tutorial.taskProgress.vanillaExp += event.amount
        
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
