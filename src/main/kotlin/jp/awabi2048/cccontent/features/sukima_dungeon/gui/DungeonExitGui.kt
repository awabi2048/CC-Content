@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.sukima_dungeon.gui

import com.awabi2048.ccsystem.api.gui.GuiLoreLine

import jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager
import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class DungeonExitGui : InventoryHolder {
    private var inventory: Inventory? = null

    override fun getInventory(): Inventory = inventory ?: Bukkit.createInventory(this, 27, "Exit")

    fun open(player: Player) {
        val title = MessageManager.getMessage(player, "gui_exit_title")
        inventory = Bukkit.createInventory(this, 27, title)
        val inv = inventory!!

        jp.awabi2048.cccontent.gui.GuiMenuItems.fillFramed(inv)

        // Confirm Button (Slot 11)
        val confirmItem = createGuiItem(
            Material.MINECART,
            MessageManager.getMessage(player, "gui_exit_button"),
            MessageManager.getList(player, "gui_exit_button_lore").map(GuiLoreLine::Warning) +
                SukimaGuiItems.singleAction(player, MessageManager.getMessage(player, "gui_exit_action"))
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

    private fun createGuiItem(material: Material, name: String, lore: List<GuiLoreLine> = emptyList()) =
        SukimaGuiItems.icon(material, name, lore)
}
