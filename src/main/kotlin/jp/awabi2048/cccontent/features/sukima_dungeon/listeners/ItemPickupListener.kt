package jp.awabi2048.cccontent.features.sukima_dungeon.listeners

import jp.awabi2048.cccontent.features.sukima_dungeon.items.ItemManager
import jp.awabi2048.cccontent.features.sukima_dungeon.CustomItemManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Interaction
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class ItemPickupListener(private val plugin: JavaPlugin) : Listener {

    @EventHandler
    fun onInteract(event: PlayerInteractEntityEvent) {
        if (event.rightClicked !is Interaction) return
        
        val interaction = event.rightClicked as Interaction
        val pdc = interaction.persistentDataContainer
        val ns = NamespacedKey(plugin, ItemManager.KEY_ITEM_TYPE)
        val nsName = NamespacedKey(plugin, ItemManager.KEY_ITEM_NAME)
        
        if (!pdc.has(ns, PersistentDataType.STRING)) return
        
        val matName = pdc.get(ns, PersistentDataType.STRING)
        val itemName = pdc.get(nsName, PersistentDataType.STRING) ?: "Item"
        
        if (matName != null) {
            val player = event.player
            
            // Get original ItemStack from display to preserve metadata
            val displayKey = NamespacedKey(plugin, "display_uuid")
            var itemStack: ItemStack? = null
            var display: org.bukkit.entity.Entity? = null
            
            if (pdc.has(displayKey, PersistentDataType.STRING)) {
                val uuidStr = pdc.get(displayKey, PersistentDataType.STRING)
                if (uuidStr != null) {
                    val uuid = UUID.fromString(uuidStr)
                    display = Bukkit.getEntity(uuid)
                    if (display is org.bukkit.entity.ItemDisplay) {
                        itemStack = display.itemStack
                    }
                }
            }
            
            // Fallback to material if display not found (shouldn't happen)
            if (itemStack == null) {
                val material = Material.valueOf(matName)
                itemStack = ItemStack(material)
                val meta = itemStack.itemMeta
                if (meta != null) {
                    meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', itemName))
                    itemStack.itemMeta = meta
                }
            }
            
            // Give item
            CustomItemManager.markAsDungeonItem(itemStack)
            player.inventory.addItem(itemStack)
            
            // Sound
            player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f)
            
            // Message
            player.sendMessage("${itemName} §7を拾いました。")
            
            // Remove entities
            interaction.remove()
            display?.remove()
        }
    }
}
