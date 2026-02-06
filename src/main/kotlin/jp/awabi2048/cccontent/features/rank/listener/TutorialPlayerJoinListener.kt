package jp.awabi2048.cccontent.features.rank.listener

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.tutorial.PlayerTutorialRank
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import jp.awabi2048.cccontent.features.rank.tutorial.task.TaskProgress
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

/**
 * プレイヤーがサーバーに参加したときのリスナー
 * - 初回参加時は Newbie で初期化
 * - プレイ時間計測の開始
 */
class TutorialPlayerJoinListener(
    private val rankManager: RankManager,
    private val resetExistingPlayers: Boolean
) : Listener {
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId
        
        val tutorial = rankManager.getPlayerTutorial(uuid)
        
        // 初回プレイヤーまたは設定で既存プレイヤーをリセットする場合
        if (tutorial.currentRank == TutorialRank.NEWBIE && tutorial.taskProgress.playTime == 0L) {
            // 初回プレイヤー - そのまま Newbie で開始
            tutorial.lastPlayTime = System.currentTimeMillis()
        } else if (resetExistingPlayers && tutorial.currentRank != TutorialRank.NEWBIE) {
            // 既存プレイヤーをリセット
            tutorial.currentRank = TutorialRank.NEWBIE
            tutorial.taskProgress = TaskProgress(uuid, TutorialRank.NEWBIE.name)
            tutorial.lastPlayTime = System.currentTimeMillis()
        } else {
            // 既存プレイヤー - プレイ時間計測の開始
            tutorial.lastPlayTime = System.currentTimeMillis()
        }
    }
}
