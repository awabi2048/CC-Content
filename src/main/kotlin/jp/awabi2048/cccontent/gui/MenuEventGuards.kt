package jp.awabi2048.cccontent.gui

import jp.awabi2048.cccontent.util.cancelWithDebug
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

object MenuEventGuards {
    fun topSlotClicked(event: InventoryClickEvent): Boolean {
        return event.rawSlot in 0 until event.view.topInventory.size
    }

    fun ownedTopClick(event: InventoryClickEvent, holder: OwnedMenuHolder, source: String): Player? {
        // Keep owner and top-inventory checks in one place so feature menus share the same click boundary.
        event.cancelWithDebug(source)
        val player = event.whoClicked as? Player ?: return null
        if (player.uniqueId != holder.ownerId) return null
        if (!topSlotClicked(event)) return null
        return player
    }

    fun cancelTopDrag(event: InventoryDragEvent, source: String): Boolean {
        if (event.rawSlots.none { it in 0 until event.view.topInventory.size }) return false
        event.cancelWithDebug(source)
        return true
    }

    fun cancelOwnedTopDrag(event: InventoryDragEvent, holder: OwnedMenuHolder, source: String): Boolean {
        // Drag events can cross inventories; cancel when a menu slot is touched or ownership is wrong.
        val player = event.whoClicked as? Player
        val touchesTopInventory = event.rawSlots.any { it in 0 until event.view.topInventory.size }
        if (!touchesTopInventory && player?.uniqueId == holder.ownerId) return false
        event.cancelWithDebug(source)
        return true
    }
}
