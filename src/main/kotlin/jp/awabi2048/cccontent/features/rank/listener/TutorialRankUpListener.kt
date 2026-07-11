package jp.awabi2048.cccontent.features.rank.listener

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.event.TutorialRankUpEvent
import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/**
 * チュートリアルランクアップ時の通知を担当する。
 *
 * Attainer到達時も職業選択は自動で開かず、プレイヤーが自分で次の導線を選べるよう案内だけ送る。
 */
class TutorialRankUpListener(
    private val rankManager: RankManager,
    private val messageProvider: MessageProvider
) : Listener {

    @EventHandler
    fun onTutorialRankUp(event: TutorialRankUpEvent) {
        val player = event.player
        val oldRank = event.oldRank
        val newRank = event.newRank

        player.sendMessage("§6§l==========================================")
        player.sendMessage(messageProvider.getMessage("message.rank_up", "rank" to newRank.name))
        player.sendMessage("§f${oldRank.name} → ${newRank.name}")
        player.sendMessage("§6§l==========================================")
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)

        if (newRank == TutorialRank.ATTAINER && !rankManager.hasProfession(player.uniqueId)) {
            messageProvider.getMessageList("tutorial_rank.attainer.reached_messages").forEach(player::sendMessage)
        }
    }
}
