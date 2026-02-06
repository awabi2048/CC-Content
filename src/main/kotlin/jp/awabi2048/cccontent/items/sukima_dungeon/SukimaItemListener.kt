package jp.awabi2048.cccontent.items.sukima_dungeon

import jp.awabi2048.cccontent.items.CustomItemManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * SukimaDungeon アイテムのイベント処理
 * 各アイテムの機能詳細実装は Phase 5-2 以降で行う
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
            "compass_tier1" -> handleCompass(player, event, 1)
            "compass_tier2" -> handleCompass(player, event, 2)
            "compass_tier3" -> handleCompass(player, event, 3)
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
        // ダンジョン内で MANGROVE_PROPAGULE ブロックを破壊
    }
    
    private fun handleCompass(player: Player, event: PlayerInteractEvent, tier: Int) {
        // TODO: コンパスのハンドリング
        // Shift 長押しで探知開始
        // Tier ごとにパラメータが異なる
    }
    
    private fun handleTalisman(player: Player, event: PlayerInteractEvent) {
        // TODO: タリスマンのハンドリング
        // 右クリックで脱出確認 GUI を表示
    }
}
