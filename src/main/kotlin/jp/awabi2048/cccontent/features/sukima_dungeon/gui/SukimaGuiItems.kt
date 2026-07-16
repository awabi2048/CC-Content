package jp.awabi2048.cccontent.features.sukima_dungeon.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import jp.awabi2048.cccontent.gui.GuiMenuItems
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

internal object SukimaGuiItems {
    fun icon(material: Material, name: String, lore: List<GuiLoreLine> = emptyList()): ItemStack {
        return GuiMenuItems.icon(material, name, lore, if (lore.isEmpty()) GuiLoreFrame.NONE else GuiLoreFrame.BOTH)
    }

    fun singleAction(player: Player, action: String): GuiLoreLine.SingleAction {
        val operation = CCSystem.getAPI().getI18nString(player, "lore.click.any")
        val resolved = CCSystem.getAPI().getI18nString(
            player,
            "lore.action_single_with_operation",
            mapOf("operation" to operation, "action" to action)
        )
        return GuiLoreLine.SingleAction(operation, action, resolved)
    }
}
