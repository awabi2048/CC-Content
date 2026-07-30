package jp.awabi2048.cccontent.features.sukima_dungeon.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiInputGesture
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import jp.awabi2048.cccontent.gui.GuiMenuItems
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

internal object SukimaGuiItems {
    fun icon(material: Material, name: String, lore: List<GuiLoreLine> = emptyList()): ItemStack {
        return GuiMenuItems.icon(material, name, lore, if (lore.isEmpty()) GuiLoreFrame.NONE else GuiLoreFrame.BOTH)
    }

    fun singleAction(player: Player, action: String): GuiLoreLine.Interaction {
        val operation = CCSystem.getAPI().getI18nString(player, "lore.click.any")
        return GuiLoreLine.Interaction(player, GuiInputGesture.Described(operation), action)
    }
}
