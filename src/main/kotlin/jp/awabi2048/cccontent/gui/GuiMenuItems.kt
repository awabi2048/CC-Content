package jp.awabi2048.cccontent.gui

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
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return hideTooltip(item)
        meta.displayName(legacy(name))
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return hideTooltip(item)
    }

    fun icon(material: Material, name: String, lore: List<String> = emptyList()): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(legacy(name))
        if (lore.isNotEmpty()) {
            meta.lore(lore.map { legacy(it) })
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return item
    }

    fun backButton(name: String = "§c戻る", lore: List<String> = emptyList()): ItemStack {
        return icon(Material.REDSTONE, name, lore)
    }

    fun hideTooltip(item: ItemStack): ItemStack {
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build())
        return item
    }

    fun fillFramed(inventory: Inventory) {
        val size = inventory.size
        val footerStart = size - 9
        val header = backgroundPane(Material.BLACK_STAINED_GLASS_PANE)
        val middle = backgroundPane(Material.GRAY_STAINED_GLASS_PANE)
        for (slot in 0 until size) {
            inventory.setItem(slot, if (slot < 9 || slot >= footerStart) header else middle)
        }
    }

    fun separator(lines: Collection<String>): String {
        val maxWidth = lines.maxOfOrNull { displayWidth(stripColor(it)) } ?: 0
        val separatorWidth = displayWidth("―").coerceAtLeast(1)
        val count = ((((maxWidth + separatorWidth - 1) / separatorWidth) * 3 + 1) / 2).coerceAtLeast(1)
        return "§8§m" + "―".repeat(count)
    }

    private fun stripColor(text: String): String = text.replace(Regex("[§&][0-9A-FK-ORa-fk-or]"), "")

    private fun displayWidth(text: String): Int = text.sumOf { char ->
        when {
            char.code in 0x20..0x7E -> 6
            char.code in 0xFF61..0xFF9F -> 6
            else -> 12
        }
    }

    private fun legacy(text: String): Component {
        return LegacyComponentSerializer.legacySection().deserialize(text).decoration(TextDecoration.ITALIC, false)
    }
}
