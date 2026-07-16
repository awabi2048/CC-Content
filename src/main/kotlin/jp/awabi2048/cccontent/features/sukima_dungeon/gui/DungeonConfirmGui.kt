@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.sukima_dungeon.gui

import com.awabi2048.ccsystem.api.gui.GuiLoreLine

import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonTier
import jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class DungeonConfirmGui(
    val tier: DungeonTier,
    val themeName: String,
    val sizeKey: String,
    val isMultiplayer: Boolean
) : InventoryHolder {
    private var inventory: Inventory? = null

    override fun getInventory(): Inventory = inventory ?: Bukkit.createInventory(this, 27, "Confirm")

    fun open(player: Player) {
        val title = MessageManager.getMessage(player, "gui_entrance_confirm_title")
        inventory = Bukkit.createInventory(this, 27, title)
        val inv = inventory!!

        jp.awabi2048.cccontent.gui.GuiMenuItems.fillFramed(inv)

        // Confirm Button (Slot 11)
        val confirmItem = createGuiItem(
            Material.END_CRYSTAL,
            MessageManager.getMessage(player, "gui_entrance_confirm_button"),
            MessageManager.getList(player, "gui_entrance_confirm_button_lore").map(GuiLoreLine::Text) +
                SukimaGuiItems.singleAction(player, MessageManager.getMessage(player, "gui_entrance_confirm_action"))
        )
        inv.setItem(11, confirmItem)

        // Cancel Button (Slot 15)
        val cancelItem = createGuiItem(
            Material.BARRIER,
            MessageManager.getMessage(player, "gui_entrance_cancel_button")
        )
        inv.setItem(15, cancelItem)
        
        player.openInventory(inv)
    }

    private fun createGuiItem(material: Material, name: String, lore: List<GuiLoreLine> = emptyList()) =
        SukimaGuiItems.icon(material, name, lore)
}
