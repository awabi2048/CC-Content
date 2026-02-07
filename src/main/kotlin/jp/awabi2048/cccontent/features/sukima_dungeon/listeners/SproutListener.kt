package jp.awabi2048.cccontent.features.sukima_dungeon.listeners

import jp.awabi2048.cccontent.features.sukima_dungeon.SproutManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.java.JavaPlugin

class SproutListener(private val plugin: JavaPlugin) : Listener {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action == Action.LEFT_CLICK_BLOCK || event.action == Action.RIGHT_CLICK_BLOCK) {
            val block = event.clickedBlock ?: return
            
            // Check if it's a world sprout
            if (SproutManager.isSproutBlock(block)) {
                // Handle break
                event.isCancelled = true // Prevent normal breaking/interaction
                SproutManager.breakSprout(plugin, event.player, block)
            }
        }
    }
}
