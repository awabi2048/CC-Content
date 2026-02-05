package jp.awabi2048.cccontent.items.arena

import jp.awabi2048.cccontent.items.CustomItemManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * KotaArena アイテムのイベント処理
 * 各アイテムの機能はPhase 3で詳細実装予定
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
            "soul_bottle" -> handleSoulBottle(player, event)
            "booster" -> handleBooster(player, event)
            "mob_drop_sack" -> handleMobDropSack(player, event)
            "hunter_talisman" -> handleHunterTalisman(player, event)
            "golem_talisman" -> handleGolemTalisman(player, event)
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
    
    private fun handleSoulBottle(player: Player, event: PlayerInteractEvent) {
        // TODO: 魂の瓶のハンドリング
        // アリーナゲーム中のみ機能
    }
    
    private fun handleBooster(player: Player, event: PlayerInteractEvent) {
        // TODO: ブースターのハンドリング
        // ステータス上昇効果
    }
    
    private fun handleMobDropSack(player: Player, event: PlayerInteractEvent) {
        // TODO: モブドロップサックのハンドリング
        // ドロップ自動回収
    }
    
    private fun handleHunterTalisman(player: Player, event: PlayerInteractEvent) {
        // TODO: ハンタータリスマンのハンドリング
        // ハンター職向けバフ
    }
    
    private fun handleGolemTalisman(player: Player, event: PlayerInteractEvent) {
        // TODO: ゴーレムタリスマンのハンドリング
        // ゴーレム職向けバフ
    }
}
