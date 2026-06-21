@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.sukima_dungeon.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec

import jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

class TalismanConfirmGui : InventoryHolder {
    private var inventory: Inventory? = null

    override fun getInventory(): Inventory = inventory ?: Bukkit.createInventory(this, 27, "Confirm")

    fun open(player: Player) {
        val title = MessageManager.getMessage(player, "gui_talisman_confirm_title")
        inventory = Bukkit.createInventory(this, 27, title)
        val inv = inventory!!

        jp.awabi2048.cccontent.gui.GuiMenuItems.fillFramed(inv)

        // Confirm Button (Slot 11) - Escape
        val confirmItem = createGuiItem(
            Material.MINECART,
            MessageManager.getMessage(player, "gui_talisman_confirm_button"),
            *MessageManager.getList(player, "gui_talisman_confirm_button_lore").toTypedArray()
        )
        inv.setItem(11, confirmItem)

        // Cancel Button (Slot 15) - Stay
        val cancelItem = createGuiItem(
            Material.BARRIER,
            MessageManager.getMessage(player, "gui_talisman_cancel_button")
        )
        inv.setItem(15, cancelItem)
        
        player.openInventory(inv)
    }

    private fun createGuiItem(material: Material, name: String, vararg lore: String): ItemStack {
        return jp.awabi2048.cccontent.gui.GuiMenuItems.icon(material, name, lore.toList())
    }
}
