package jp.awabi2048.cccontent.features.arena

import jp.awabi2048.cccontent.gui.GuiMenuItems
import org.bukkit.Material
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
        return GuiMenuItems.backgroundPane(material, name)
    }

    fun hideTooltip(item: ItemStack): ItemStack {
        return GuiMenuItems.hideTooltip(item)
    }
}
