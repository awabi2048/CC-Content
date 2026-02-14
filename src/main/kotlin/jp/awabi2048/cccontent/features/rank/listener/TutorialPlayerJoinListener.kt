package jp.awabi2048.cccontent.features.rank.listener

import jp.awabi2048.cccontent.features.rank.RankManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

/**
 * プレイヤーがサーバーに参加したときのリスナー
 * - プレイ時間計測の開始
 */
class TutorialPlayerJoinListener(
    private val rankManager: RankManager
) : Listener {
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val tutorial = rankManager.getPlayerTutorial(player.uniqueId)
        
        // Join時は常にプレイ時間計測の起点だけ更新
        // 既存プレイヤーのランクリセットは仕様から削除
        tutorial.lastPlayTime = System.currentTimeMillis()
    }
}
