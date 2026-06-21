@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.sukima_dungeon.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec

import jp.awabi2048.cccontent.features.sukima_dungeon.PortalSession
import jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager
import jp.awabi2048.cccontent.features.sukima_dungeon.listeners.EntranceListener
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.ItemFlag

import jp.awabi2048.cccontent.features.sukima_dungeon.generator.StructureLoader

class DungeonJoinGui(val portal: PortalSession, private val loader: StructureLoader) : InventoryHolder {
    private var inventory: Inventory? = null

    override fun getInventory(): Inventory = inventory ?: Bukkit.createInventory(this, 27, "Join")

    fun open(player: Player) {
        val title = MessageManager.getMessage(player, "gui_join_title")
        inventory = Bukkit.createInventory(this, 27, title)
        val inv = inventory!!

        jp.awabi2048.cccontent.gui.GuiMenuItems.fillFramed(inv)

        // Join Button (Slot 13 or 11/15)
        val themeName = portal.themeName
        val theme = loader.getTheme(themeName)
        val isRandom = themeName == "random"
        val randomDisplayName = MessageManager.getMessage(player, "gui_theme_random") // Should retrieve "???"
        
        val baseDisplayName = if (isRandom) randomDisplayName else theme?.getDisplayName(player) ?: themeName
        val displaySuffix = if (isRandom) "" else "の世界"
        val fullDisplayName = "$baseDisplayName$displaySuffix"
        
        val sizeName = portal.sizeKey
        
        val buttonMaterial = if (portal.isReady) Material.END_CRYSTAL else Material.STRUCTURE_VOID
        val buttonName = if (portal.isReady) MessageManager.getMessage(player, "gui_join_button_ready") else MessageManager.getMessage(player, "gui_join_button_wait")
        val lore = MessageManager.getList(player, "gui_join_button_lore").map { 
            it.replace("{theme}", fullDisplayName).replace("{size}", sizeName)
        }

        val joinItem = createGuiItem(buttonMaterial, buttonName, *lore.toTypedArray())
        inv.setItem(13, joinItem)

        // Close Button (Slot 22 - though it's already filled by blackGlass, let's put it at 26)
        val closeItem = createGuiItem(Material.BARRIER, MessageManager.getMessage(player, "gui_join_cancel_button"))
        inv.setItem(22, closeItem)
        
        player.openInventory(inv)
    }

    private fun createGuiItem(material: Material, name: String, vararg lore: String): ItemStack {
        return jp.awabi2048.cccontent.gui.GuiMenuItems.icon(material, name, lore.toList())
    }
}
