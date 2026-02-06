package jp.awabi2048.cccontent.items.arena

import jp.awabi2048.cccontent.items.CustomItemManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * KotaArena アイテムのイベント処理
 * 各アイテムの機能はCustomItemインターフェースを通じて実装
 */
class ArenaItemListener : Listener {
    
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand
        
        // カスタムアイテムを識別
        val customItem = CustomItemManager.identify(item) ?: return
        if (customItem.feature != "arena") return
        
        // 右クリック判定
        when {
            event.action.toString().contains("RIGHT") -> {
                customItem.onRightClick(player, event)
            }
            event.action.toString().contains("LEFT") -> {
                customItem.onLeftClick(player, event)
            }
        }
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val item = event.currentItem ?: return
        val player = event.whoClicked as? Player ?: return
        
        val customItem = CustomItemManager.identify(item) ?: return
        
        if (customItem.feature == "arena") {
            customItem.onInventoryClick(player, event)
        }
    }
}
