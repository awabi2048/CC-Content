package jp.awabi2048.cccontent.items.sukima_dungeon

import jp.awabi2048.cccontent.features.sukima_dungeon.gui.DungeonEntranceGui
import jp.awabi2048.cccontent.items.sukima_dungeon.common.ConfigManager
import jp.awabi2048.cccontent.items.sukima_dungeon.common.DungeonManager
import jp.awabi2048.cccontent.items.sukima_dungeon.common.MessageManager
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

/**
 * ダンジョン進入リスナー
 * ブックマーク右クリック検知とGUIハンドリング
 */
class EntranceListener(
    private val plugin: JavaPlugin,
    private val dungeonManager: DungeonManager,
    private val configManager: ConfigManager,
    private val messageManager: MessageManager,
    private val entranceGui: DungeonEntranceGui
) : Listener {
    
    /**
     * ブックマークアイテムの右クリックを検知
     */
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand
        
        // 右クリックかどうかチェック
        if (!event.action.isRightClick) return
        
        // ブックマークアイテムかどうか確認
        if (!isBookmarkItem(item)) return
        
        event.isCancelled = true
        
        // ブックマークのティアを取得
        val tier = getBookmarkTier(item)
        if (tier == null) {
            messageManager.sendError(player, "error.invalid_bookmark")
            return
        }
        
        // GUI を開く
        entranceGui.openEntranceGui(player, tier)
    }
    
    /**
     * GUI内のインベントリクリックを処理
     */
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val slot = event.slot
        
        // GUI内のクリックを処理
        if (entranceGui.handleGuiClick(player, slot)) {
            event.isCancelled = true
        }
    }
    
    /**
     * ブックマークアイテムかどうかを判定
     */
    private fun isBookmarkItem(item: org.bukkit.inventory.ItemStack): Boolean {
        if (item.type != org.bukkit.Material.POISONOUS_POTATO) return false
        
        val meta = item.itemMeta ?: return false
        val pdc = meta.persistentDataContainer
        
        val itemType = pdc.get(
            NamespacedKey(plugin, "sukima_item"),
            PersistentDataType.STRING
        )
        
        return itemType == "bookmark"
    }
    
    /**
     * ブックマークのティアを取得
     */
    private fun getBookmarkTier(item: org.bukkit.inventory.ItemStack): String? {
        val meta = item.itemMeta ?: return null
        val pdc = meta.persistentDataContainer
        
        val tier = pdc.get(
            NamespacedKey(plugin, "bookmark_tier"),
            PersistentDataType.STRING
        )
        
        return tier
    }
}
