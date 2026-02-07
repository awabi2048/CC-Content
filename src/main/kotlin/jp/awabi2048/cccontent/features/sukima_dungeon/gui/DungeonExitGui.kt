package jp.awabi2048.cccontent.features.sukima_dungeon.gui

import jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager
import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.ItemFlag

class DungeonExitGui : InventoryHolder {
    private var inventory: Inventory? = null

    override fun getInventory(): Inventory = inventory ?: Bukkit.createInventory(this, 27, "Exit")

    fun open(player: Player) {
        val title = MessageManager.getMessage(player, "gui_exit_title")
        inventory = Bukkit.createInventory(this, 27, title)
        val inv = inventory!!

        val blackGlass = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ")
        val blackMeta = blackGlass.itemMeta
        blackMeta?.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        blackGlass.itemMeta = blackMeta

        val grayGlass = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ")
        val grayMeta = grayGlass.itemMeta
        grayMeta?.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        grayGlass.itemMeta = grayMeta

        // Fill Header & Footer (Row 0 and 2)
        for (i in 0..8) inv.setItem(i, blackGlass)
        for (i in 18..26) inv.setItem(i, blackGlass)

        // Fill Middle Row (Row 1)
        for (i in 9..17) inv.setItem(i, grayGlass)

        // Confirm Button (Slot 11)
        val confirmItem = createGuiItem(
            Material.MINECART,
            MessageManager.getMessage(player, "gui_exit_button"),
            *MessageManager.getList(player, "gui_exit_button_lore").toTypedArray()
        )
        inv.setItem(11, confirmItem)

        // Cancel Button (Slot 15)
        val cancelItem = createGuiItem(
            Material.BARRIER,
            MessageManager.getMessage(player, "gui_exit_cancel_button")
        )
        inv.setItem(15, cancelItem)
        
        player.openInventory(inv)
    }

    private fun createGuiItem(material: Material, name: String, vararg lore: String): ItemStack {
        val item = ItemStack(material, 1)
        val meta: ItemMeta? = item.itemMeta
        meta?.setDisplayName(name)
        if (lore.isNotEmpty()) {
            meta?.lore = lore.toList()
        }
        item.itemMeta = meta
        return item
    }
}
