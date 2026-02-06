package jp.awabi2048.cccontent.features.rank.listener

import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskChecker
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/**
 * プレイヤーがログアウトしたときのリスナー
 * - プレイ時間を加算
 * - タスク完了判定を実行
 * - 自動ランクアップ
 * - データを保存
 */
class TutorialPlayerQuitListener(
    private val rankManager: RankManager,
    private val taskChecker: TutorialTaskChecker,
    private val taskLoader: jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskLoader,
    private val storage: RankStorage
) : Listener {
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId
        
        val tutorial = rankManager.getPlayerTutorial(uuid)
        
        // 最後のプレイ時間（Join時から現在までのプレイ時間）を加算
        // PlayTimeTrackerTask で1分単位で加算されているため、残りの秒数のみを加算
        val playedTime = (System.currentTimeMillis() - tutorial.lastPlayTime) / 60000  // ミリ秒 → 分
        if (playedTime > 0) {
            tutorial.taskProgress.playTime += playedTime
        }
        
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
