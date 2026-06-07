package jp.awabi2048.cccontent.items.misc

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityCombustEvent

class FireproofListener : Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    fun onEntityCombust(event: EntityCombustEvent) {
        val player = event.entity as? Player ?: return
        if (FireproofLeggingsItem.isWearing(player)) {
            event.isCancelled = true
        }
    }
}