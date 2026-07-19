package jp.awabi2048.cccontent.util

import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

fun InventoryClickEvent.cancelWithDebug(@Suppress("UNUSED_PARAMETER") source: String) {
    this.isCancelled = true
}

fun InventoryDragEvent.cancelWithDebug(@Suppress("UNUSED_PARAMETER") source: String) {
    this.isCancelled = true
}
