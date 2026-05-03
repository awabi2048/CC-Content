package jp.awabi2048.cccontent.features.arena

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.TooltipDisplay
import org.bukkit.Material
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

object ArenaMenuItems {
    fun difficultyStars(starCount: Int): String {
        val effective = starCount.coerceAtLeast(1)
        return if (effective >= 4) {
            "§4★★★★"
        } else {
            "§c${"★".repeat(effective)}§7${"☆".repeat(3 - effective)}"
        }
    }

    fun backgroundPane(material: Material, name: String = " "): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return hideTooltip(item)
        meta.setDisplayName(name)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        item.itemMeta = meta
        return hideTooltip(item)
    }

    fun hideTooltip(item: ItemStack): ItemStack {
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build())
        return item
    }
}
