package jp.awabi2048.cccontent.features.rank.tutorial.task

import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskChecker
import jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskLoader
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin

/**
 * プレイ時間を1分ごとに更新するタスク
 * サーバーのオンラインプレイヤーのプレイ時間を自動的に加算・保存する
 */
class PlayTimeTrackerTask(
    private val plugin: Plugin,
    private val rankManager: RankManager,
    private val taskChecker: TutorialTaskChecker,
    private val taskLoader: TutorialTaskLoader,
    private val storage: RankStorage
) : Runnable {
    
    override fun run() {
        try {
            // オンラインのプレイヤーを取得
            val onlinePlayers = Bukkit.getOnlinePlayers()
            
            for (player in onlinePlayers) {
                val uuid = player.uniqueId
                val tutorial = rankManager.getPlayerTutorial(uuid)
                
                // プレイ時間を1分加算
                tutorial.taskProgress.playTime += 1
                
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * タスクを開始
     * @return タスクID
     */
    fun start(): Int {
        // 1分ごと（20 tick * 60 = 1200 tick）に実行
        return plugin.server.scheduler.scheduleSyncRepeatingTask(
            plugin,
            this,
            0L,      // 初回実行までの遅延（tick）
            1200L    // 繰り返し間隔（1分 = 1200 tick）
        )
    }
    
    /**
     * タスクを停止
     * @param taskId タスクID
     */
    fun stop(taskId: Int) {
        plugin.server.scheduler.cancelTask(taskId)
    }
}
