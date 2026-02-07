package jp.awabi2048.cccontent.features.sukima_dungeon.listeners

import jp.awabi2048.cccontent.features.sukima_dungeon.*
import jp.awabi2048.cccontent.features.sukima_dungeon.gui.TalismanConfirmGui
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.java.JavaPlugin

class TalismanListener(private val plugin: JavaPlugin) : Listener {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return

        if (CustomItemManager.isTalismanItem(item)) {
            if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
                if (player.world.name.startsWith("dungeon_")) {
                    TalismanConfirmGui().open(player)
                    player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
                } else {
                    player.sendMessage(MessageManager.getMessage(player, "talisman_cannot_use_here"))
                }
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        
        // Handle TalismanConfirmGui clicks
        if (event.inventory.holder is TalismanConfirmGui) {
            // Apply cooldown
            if (MenuCooldownManager.checkAndSetCooldown(player.uniqueId)) {
                event.isCancelled = true
                return
            }

            event.isCancelled = true
            val slot = event.rawSlot
            when (slot) {
                11 -> {
                    // Escape Button
                    player.closeInventory()
                    
                    // Consume item
                    val inv = player.inventory
                    for (i in 0 until inv.size) {
                        val invItem = inv.getItem(i)
                        if (CustomItemManager.isTalismanItem(invItem)) {
                            invItem!!.amount = invItem.amount - 1
                            break
                        }
                    }

                    // System message
                    player.sendMessage(MessageManager.getMessage(player, "prefix") + "§a脱出シーケンスを開始しました。5秒後に帰還します。")
                    player.playSound(player.location, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f)

                    // 1 second later: Oage-chan's message
                    plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        if (player.isOnline) {
                            val msg = MessageManager.getMessage(player, "oage_evacuate")
                            MessageManager.sendOageMessage(player, " 「$msg」")
                        }
                    }, 20L)

                    // 5 seconds later: Final escape
                    plugin.server.scheduler.runTaskLater(plugin, Runnable {
                        if (player.isOnline) {
                            escapeDungeon(player)
                            player.playSound(player.location, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f)
                            player.playSound(player.location, org.bukkit.Sound.UI_TOAST_IN, 1.0f, 1.0f)
                        }
                    }, 100L)
                }
                15 -> {
                    // Stay Button
                    player.closeInventory()
                    player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
                }
            }
            return
        }

        // Inventory restrictions
        val currentItem = event.currentItem
        val cursorItem = event.cursor
        val action = event.action
        
        val isTalismanInSlot = CustomItemManager.isTalismanItem(currentItem)
        val isTalismanOnCursor = CustomItemManager.isTalismanItem(cursorItem)
        val isTalismanInHotbar = event.click == ClickType.NUMBER_KEY && CustomItemManager.isTalismanItem(player.inventory.getItem(event.hotbarButton))

        if (isTalismanInSlot || isTalismanOnCursor || isTalismanInHotbar) {
            // Block dropping via Q or clicking outside
            if (action == InventoryAction.DROP_ALL_CURSOR || action == InventoryAction.DROP_ALL_SLOT ||
                action == InventoryAction.DROP_ONE_CURSOR || action == InventoryAction.DROP_ONE_SLOT ||
                event.click == ClickType.WINDOW_BORDER_LEFT || event.click == ClickType.WINDOW_BORDER_RIGHT) {
                event.isCancelled = true
                return
            }

            // Block moving to other inventory (Chest, etc.)
            val clickedInventory = event.clickedInventory ?: return
            val topInventory = event.view.topInventory
            
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                // Shift-click: block if moving from player inventory TO external inventory
                if (clickedInventory == event.view.bottomInventory && topInventory.type != InventoryType.CRAFTING && topInventory.type != InventoryType.PLAYER) {
                    event.isCancelled = true
                    return
                }
            }
            
            // Clicking/Placing into top inventory
            if (clickedInventory == topInventory && topInventory.type != InventoryType.CRAFTING && topInventory.type != InventoryType.PLAYER) {
                 event.isCancelled = true
                 return
            }
            
            // Number key swap to top inventory
            if (event.click == ClickType.NUMBER_KEY && clickedInventory == topInventory && topInventory.type != InventoryType.CRAFTING && topInventory.type != InventoryType.PLAYER) {
                event.isCancelled = true
                return
            }
        }
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (CustomItemManager.isTalismanItem(event.itemDrop.itemStack)) {
            event.isCancelled = true
        }
    }

    private fun escapeDungeon(player: Player) {
        DungeonManager.escapeDungeon(player.world)
    }
}
