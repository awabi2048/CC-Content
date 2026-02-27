package jp.awabi2048.cccontent.items

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent

class CustomItemInteractionListener : Listener {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        val customItem = CustomItemManager.identify(item) ?: return
        val player = event.player

        when (event.action) {
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> customItem.onRightClick(player, event)
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> customItem.onLeftClick(player, event)
            else -> Unit
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        val targets = linkedSetOf<CustomItem>()
        event.currentItem?.let { item ->
            CustomItemManager.identify(item)?.let { targets.add(it) }
        }
        event.cursor?.let { item ->
            CustomItemManager.identify(item)?.let { targets.add(it) }
        }
        if (event.click == ClickType.NUMBER_KEY) {
            player.inventory.getItem(event.hotbarButton)?.let { item ->
                CustomItemManager.identify(item)?.let { targets.add(it) }
            }
        }

        for (customItem in targets) {
            customItem.onInventoryClick(player, event)
        }
    }
}
