package jp.awabi2048.cccontent.items.sukima_dungeon

import jp.awabi2048.cccontent.items.sukima_dungeon.common.MessageManager
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

/**
 * タリスマンリスナー
 * 元の SukimaDungeon の TalismanListener を再現
 */
class TalismanListener(
    private val plugin: JavaPlugin,
    private val messageManager: MessageManager
) : Listener {
    
    private val escapeGuiTitle = Component.text("§8脱出確認")
    
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return
        
        // タリスマンアイテムか確認
        if (!isTalismanItem(item)) return
        
        // アクションが右クリックか確認
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return
        }
        
        event.isCancelled = true
        
        // ダンジョン内チェック（仮実装）
        if (!isInDungeon(player)) {
            messageManager.sendError(player, "error.not_in_dungeon")
            return
        }
        
        // エスケープ確認GUIを表示
        showEscapeConfirmationGui(player)
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = event.inventory
        
        // エスケープ確認GUIか確認
        if (inventory.size != 27) return
        if (event.view.title() != escapeGuiTitle) return
        
        event.isCancelled = true
        
        val clickedItem = event.currentItem ?: return
        
        when (clickedItem.type) {
            Material.LIME_WOOL -> {
                // 脱出確定
                performEscape(player)
                player.closeInventory()
            }
            Material.RED_WOOL -> {
                // キャンセル
                messageManager.send(player, "talisman.cancelled")
                player.closeInventory()
            }
            else -> {}
        }
    }
    
    /**
     * タリスマンアイテムかどうかを判定
     */
    private fun isTalismanItem(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        
        val meta = item.itemMeta ?: return false
        val pdc = meta.persistentDataContainer
        
        val itemType = pdc.get(
            NamespacedKey(plugin, "sukima_item"),
            PersistentDataType.STRING
        )
        
        return itemType == "talisman"
    }
    
    /**
     * ダンジョン内にいるかどうか（仮実装）
     */
    private fun isInDungeon(player: Player): Boolean {
        // 仮実装：常にtrueを返す
        return true
    }
    
    /**
     * エスケープ確認GUIを表示
     */
    private fun showEscapeConfirmationGui(player: Player) {
        val gui = Bukkit.createInventory(null, 27, escapeGuiTitle)
        
        // 背景を黒色のガラスで埋める
        val background = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            val meta = itemMeta
            meta?.displayName(Component.text(" "))
            itemMeta = meta
        }
        
        for (i in 0 until 27) {
            gui.setItem(i, background)
        }
        
        // 確認メッセージ
        val infoItem = ItemStack(Material.PAPER).apply {
            val meta = itemMeta
            meta?.displayName(Component.text("§6§l脱出確認"))
            meta?.lore(listOf(
                Component.text("§7スキマダンジョンから脱出します"),
                Component.text("§7本当に脱出しますか？"),
                Component.text(""),
                Component.text("§e左: §a脱出する"),
                Component.text("§e右: §cキャンセル")
            ))
            itemMeta = meta
        }
        gui.setItem(13, infoItem)
        
        // 脱出ボタン（緑色羊毛）
        val escapeButton = ItemStack(Material.LIME_WOOL).apply {
            val meta = itemMeta
            meta?.displayName(Component.text("§a§l脱出する"))
            meta?.lore(listOf(
                Component.text("§7クリックで脱出します"),
                Component.text("§7ダンジョンから退出します")
            ))
            itemMeta = meta
        }
        gui.setItem(11, escapeButton)
        
        // キャンセルボタン（赤色羊毛）
        val cancelButton = ItemStack(Material.RED_WOOL).apply {
            val meta = itemMeta
            meta?.displayName(Component.text("§c§lキャンセル"))
            meta?.lore(listOf(
                Component.text("§7クリックでキャンセルします"),
                Component.text("§7ダンジョンに残ります")
            ))
            itemMeta = meta
        }
        gui.setItem(15, cancelButton)
        
        player.openInventory(gui)
    }
    
    /**
     * 脱出を実行
     */
    private fun performEscape(player: Player) {
        // ダンジョンから脱出するロジック
        // TODO: 実際の脱出ロジックを実装（スポーン地点へのテレポートなど）
        
        messageManager.sendTalismanEscape(player)
        messageManager.send(player, "talisman.use")
        
        // 仮実装：プレイヤーにメッセージを送信
        player.sendMessage(Component.text("§aダンジョンから脱出しました！"))
        
        // タリスマンを消費（!consumable 設定があるため、手動で削除）
        val itemInHand = player.inventory.itemInMainHand
        if (isTalismanItem(itemInHand)) {
            val amount = itemInHand.amount
            if (amount > 1) {
                itemInHand.amount = amount - 1
            } else {
                player.inventory.setItemInMainHand(ItemStack(Material.AIR))
            }
        }
    }
}