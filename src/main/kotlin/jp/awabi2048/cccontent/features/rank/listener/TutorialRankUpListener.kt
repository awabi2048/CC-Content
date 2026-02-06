package jp.awabi2048.cccontent.features.rank.listener

import jp.awabi2048.cccontent.features.rank.event.TutorialRankUpEvent
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/**
 * ランクアップイベントをリッスンして、メッセージと効果音を送信するリスナー
 */
class TutorialRankUpListener : Listener {
    
    @EventHandler
    fun onTutorialRankUp(event: TutorialRankUpEvent) {
        val player = event.player
        val oldRank = event.oldRank
        val newRank = event.newRank
        
        // ランクアップメッセージを複数行で送信
        player.sendMessage("§6§l==========================================")
        player.sendMessage("§a§lランクアップ！")
        player.sendMessage("§f${oldRank.name} → ${newRank.name}")
        player.sendMessage("§6§l==========================================")
        
        // 効果音を再生
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
    }
}
