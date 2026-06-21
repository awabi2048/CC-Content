package jp.awabi2048.cccontent.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiFrameSection
import com.awabi2048.ccsystem.api.gui.GuiFrameSpec
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.TooltipDisplay
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.util.UUID

open class OwnedMenuHolder(
    val ownerId: UUID
) : InventoryHolder {
    lateinit var backingInventory: Inventory

    override fun getInventory(): Inventory = backingInventory
}

object GuiMenuItems {
    fun backgroundPane(material: Material, name: String = " "): ItemStack {
        return CCSystem.getAPI().getGuiElementService().item(decorationSpec(material, name))
    }

    fun icon(material: Material, name: String, lore: List<String> = emptyList(), frame: GuiLoreFrame = GuiLoreFrame.NONE): ItemStack {
        return CCSystem.getAPI().getGuiElementService().item(
            GuiItemSpec(
                material = material,
                name = GuiNameSpec.Text(name, GuiNameStyle.DEFAULT),
                lore = if (lore.isEmpty()) GuiLoreSpec.None else GuiLoreSpec.Auto(lore, frame),
                role = GuiElementRole.CONTENT,
                amount = 1
            )
        )
    }

    fun backButton(name: String = "§c戻る", lore: List<String> = emptyList()): ItemStack {
        return icon(Material.REDSTONE, name, lore)
    }

    fun hideTooltip(item: ItemStack): ItemStack {
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build())
        return item
    }

    fun fillFramed(inventory: Inventory) {
        val frame = decorationSpec(Material.BLACK_STAINED_GLASS_PANE)
        CCSystem.getAPI().getGuiElementService().applyFrame(
            inventory,
            GuiFrameSpec(
                header = GuiFrameSection.Row(frame),
                footer = GuiFrameSection.Row(frame),
                emptySlot = decorationSpec(Material.GRAY_STAINED_GLASS_PANE)
            )
        )
    }

    fun fillEmpty(inventory: Inventory, material: Material = Material.GRAY_STAINED_GLASS_PANE) {
        CCSystem.getAPI().getGuiElementService().fillEmpty(inventory, decorationSpec(material))
    }

    private fun decorationSpec(material: Material, name: String = " "): GuiItemSpec = GuiItemSpec(
        material = material,
        name = if (name.isEmpty()) GuiNameSpec.Empty else GuiNameSpec.Text(name, GuiNameStyle.DEFAULT),
        lore = GuiLoreSpec.None,
        role = GuiElementRole.DECORATION,
        amount = 1
    )

    private fun legacy(text: String): Component {
        return LegacyComponentSerializer.legacySection().deserialize(text).decoration(TextDecoration.ITALIC, false)
    }
}
