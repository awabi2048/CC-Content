package jp.awabi2048.cccontent.util

import jp.awabi2048.cccontent.CCContent
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

fun InventoryClickEvent.cancelWithDebug(source: String) {
    val player = this.whoClicked as? Player ?: return
    val parts = source.split(": ", limit = 2)
    val path = parts[0]
    val reason = parts.getOrElse(1) { "unspecified" }
    CCContent.instance.logger.info("[ClickCancel] player=${player.name} | source=${path} | reason=${reason} | title=${this.view.title()} | slot=${this.slot} | click=${this.click} | action=${this.action}")
    this.isCancelled = true
}

fun InventoryDragEvent.cancelWithDebug(source: String) {
    val player = this.whoClicked as? Player ?: return
    val parts = source.split(": ", limit = 2)
    val path = parts[0]
    val reason = parts.getOrElse(1) { "unspecified" }
    CCContent.instance.logger.info("[DragCancel] player=${player.name} | source=${path} | reason=${reason} | title=${this.view.title()} | slots=${this.rawSlots}")
    this.isCancelled = true
}
