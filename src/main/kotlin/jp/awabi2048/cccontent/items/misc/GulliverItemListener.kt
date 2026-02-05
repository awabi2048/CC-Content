package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItemManager
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent

/**
 * GulliverLight アイテムのイベント処理
 */
class GulliverItemListener : Listener {
    
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand
        
        // カスタムアイテムを識別
        val customItem = CustomItemManager.identify(item) ?: return
        if (customItem.feature != "misc") return
        
        // アイテムの種類による処理
        when (customItem.id) {
            "big_light" -> handleBigLight(player, event)
            "small_light" -> handleSmallLight(player, event)
        }
    }
    
    private fun handleBigLight(player: Player, event: PlayerInteractEvent) {
        // Shift + 右クリック: スケールをリセット
        if (player.isSneaking && event.action.name.contains("RIGHT")) {
            resetScale(player)
            return
        }
        
        // 右クリック長押しの場合のみ処理
        if (event.action.name.contains("RIGHT")) {
            if (player.isHandRaised) {
                // スケール増加
                player.sendMessage("§aビッグライトの機能は将来実装予定です")
            }
        }
    }
    
    private fun handleSmallLight(player: Player, event: PlayerInteractEvent) {
        // Shift + 右クリック: スケールをリセット
        if (player.isSneaking && event.action.name.contains("RIGHT")) {
            resetScale(player)
            return
        }
        
        // 右クリック長押しの場合のみ処理
        if (event.action.name.contains("RIGHT")) {
            if (player.isHandRaised) {
                // スケール減少
                player.sendMessage("§aスモールライトの機能は将来実装予定です")
            }
        }
    }
    
    private fun resetScale(player: Player) {
        player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f)
        player.sendMessage("§aスケールをリセットしました")
    }
}
