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
        
        // アイテムの種類による処理（スタブ）
        when (customItem.id) {
            "ticket" -> handleTicket(player, event)
            "medal" -> handleMedal(player, event)
            "prize" -> handlePrize(player, event)
        }
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val item = event.currentItem ?: return
        val customItem = CustomItemManager.identify(item) ?: return
        
        if (customItem.feature == "arena") {
            customItem.onInventoryClick(event.whoClicked as Player, event)
        }
    }
    
    private fun handleTicket(player: Player, event: PlayerInteractEvent) {
        // TODO: チケットのハンドリング
        // アリーナへの参加処理
    }
    
    private fun handleMedal(player: Player, event: PlayerInteractEvent) {
        // TODO: メダルのハンドリング
        // メダル情報の確認
    }
    
    private fun handlePrize(player: Player, event: PlayerInteractEvent) {
        // TODO: 報酬箱のハンドリング
        // 報酬内容の確認
    }
}
