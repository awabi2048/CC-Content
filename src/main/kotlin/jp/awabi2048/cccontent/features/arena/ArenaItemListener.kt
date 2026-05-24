package jp.awabi2048.cccontent.features.arena

import jp.awabi2048.cccontent.items.CustomItemManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * Arena アイテムのイベント処理
 * 各アイテムの機能はPhase 4で詳細実装予定
 */
class ArenaItemListener : Listener {
    
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand
        
        // カスタムアイテムを識別
        val customItem = CustomItemManager.identify(item) ?: return
        if (customItem.feature != "arena") return
        
        customItem.onRightClick(player, event)
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val item = event.currentItem ?: return
        val customItem = CustomItemManager.identify(item) ?: return
        
        if (customItem.feature == "arena") {
            customItem.onInventoryClick(event.whoClicked as Player, event)
        }
    }
    
}
