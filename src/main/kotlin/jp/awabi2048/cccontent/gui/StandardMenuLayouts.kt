package jp.awabi2048.cccontent.gui

import org.bukkit.inventory.Inventory

data class ConfirmationMenuLayout(
    val size: Int,
    val previewSlot: Int,
    val confirmSlot: Int,
    val cancelSlot: Int
)

object StandardMenuLayouts {
    const val SIZE_45 = 45
    const val SIZE_54 = 54
    const val BACK_SLOT_45 = 40
    const val FOOTER_LEFT_SLOT_54 = 45
    const val FOOTER_CENTER_SLOT_54 = 49
    const val BACK_SLOT_54 = FOOTER_CENTER_SLOT_54

    val CONFIRM_45 = ConfirmationMenuLayout(
        size = SIZE_45,
        previewSlot = 22,
        confirmSlot = 20,
        cancelSlot = 24
    )

    fun applyStandardFrame(inventory: Inventory) {
        GuiMenuItems.fillFramed(inventory)
    }
}
