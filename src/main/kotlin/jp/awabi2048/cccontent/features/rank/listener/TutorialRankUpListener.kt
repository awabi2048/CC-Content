package jp.awabi2048.cccontent.features.rank.listener

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.event.TutorialRankUpEvent
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/**
 * ランクアップイベントをリッスンして、メッセージと効果音を送信するリスナー
 * ATTAINER到達時に職業選択GUIを開く
 */
class TutorialRankUpListener(
    private val rankManager: RankManager,
    private val professionSelector: ProfessionSelector
) : Listener {
    
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
        
        // ATTAINERに到達した場合、職業選択GUIを開く
        if (newRank == TutorialRank.ATTAINER) {
            // 既に職業を選択済みの場合はスキップ
            if (!rankManager.hasProfession(player.uniqueId)) {
                player.sendMessage("")
                player.sendMessage("§6§l[重要] §e職業を選択してください！")
                player.sendMessage("§7/gui profession §fまたは §7/rankmenu §fで職業を選択できます")
                player.sendMessage("")
                
                // 少し遅延させてGUIを開く
                org.bukkit.Bukkit.getScheduler().runTaskLater(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(this::class.java),
                    Runnable {
                        professionSelector.openProfessionSelectionGui(player)
                    },
                    20L // 1秒後
                )
            }
        }
    }
}
