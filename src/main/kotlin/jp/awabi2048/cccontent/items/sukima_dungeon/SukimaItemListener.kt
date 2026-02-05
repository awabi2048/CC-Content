package jp.awabi2048.cccontent.items.sukima_dungeon

import jp.awabi2048.cccontent.items.CustomItemManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * SukimaDungeon アイテムのイベント処理
 * 各アイテムの機能はPhase 4で詳細実装予定
 */
class SukimaItemListener : Listener {
    
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand
        
        // カスタムアイテムを識別
        val customItem = CustomItemManager.identify(item) ?: return
        if (customItem.feature != "sukima_dungeon") return
        
        // アイテムの種類による処理（スタブ）
        when (customItem.id) {
            "sprout" -> handleSprout(player, event)
            "compass" -> handleCompass(player, event)
            "talisman" -> handleTalisman(player, event)
        }
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val item = event.currentItem ?: return
        val customItem = CustomItemManager.identify(item) ?: return
        
        if (customItem.feature == "sukima_dungeon") {
            customItem.onInventoryClick(event.whoClicked as Player, event)
        }
    }
    
    private fun handleSprout(player: Player, event: PlayerInteractEvent) {
        // TODO: スプラウトのハンドリング
        // ダンジョン内の成長プロセス
    }
    
    private fun handleCompass(player: Player, event: PlayerInteractEvent) {
        // TODO: コンパスのハンドリング
        // ダンジョン内の方向指示
    }
    
    private fun handleTalisman(player: Player, event: PlayerInteractEvent) {
        // TODO: タリスマンのハンドリング
        // おあげちゃんの力
    }
}
