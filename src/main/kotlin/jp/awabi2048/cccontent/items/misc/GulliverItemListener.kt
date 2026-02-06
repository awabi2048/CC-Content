package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.CCContent
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

/**
 * GulliverLight アイテムのイベント処理
 * 統合元プラグインのGulliverListenerをそのまま移植
 */
class GulliverItemListener(private val plugin: JavaPlugin) : Listener {

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        
        val item = event.item ?: return
        val meta = item.itemMeta ?: return
        val container = meta.persistentDataContainer
        
        val isBigLight = container.has(GulliverItems.BIG_LIGHT_KEY, PersistentDataType.BYTE)
        val isSmallLight = container.has(GulliverItems.SMALL_LIGHT_KEY, PersistentDataType.BYTE)
        
        if (!isBigLight && !isSmallLight) return

        val player = event.player
        val config = GulliverConfig

        if (player.isSneaking) {
            // Reset Scale
            val scaleAttribute = player.getAttribute(Attribute.valueOf("GENERIC_SCALE"))
            if (scaleAttribute != null) {
                scaleAttribute.baseValue = 1.0
                
                val resetSoundKey = config.getResetSound()
                val vol = config.getDefaultVolume()
                val pitch = config.getDefaultPitch()
                
                // Try parse sound, fallback if invalid
                try {
                    player.playSound(player.location, resetSoundKey, vol, pitch)
                } catch (e: Exception) {
                    plugin.logger.warning("Invalid sound key: $resetSoundKey")
                }
            }
        } else {
            // Start using (just play sound, logic is in ScaleManager)
            // Prevent multiple sounds if already using (hand raised)
            if (player.isHandRaised) return

            // Check if limit is reached
            val scaleAttribute = player.getAttribute(Attribute.valueOf("GENERIC_SCALE"))
            val currentScale = scaleAttribute?.baseValue ?: 1.0
            val maxScale = config.getBigLightMaxScale()
            val minScale = config.getSmallLightMinScale()

            var isLimit = false
            if (isBigLight && currentScale >= maxScale) isLimit = true
            if (isSmallLight && currentScale <= minScale) isLimit = true

            if (isLimit) {
                val limitSound = config.getLimitSound()
                val vol = config.getDefaultVolume()
                val pitch = config.getDefaultPitch()
                player.playSound(player.location, limitSound, vol, pitch)
                return
            }
            
            val soundConfig = config.getSoundConfig("use")
            player.playSound(player.location, soundConfig.soundKey, soundConfig.volume, soundConfig.pitch)
        }
    }

    @EventHandler
    fun onConsume(event: PlayerItemConsumeEvent) {
        val item = event.item
        val meta = item.itemMeta ?: return
        val container = meta.persistentDataContainer
        
        if (container.has(GulliverItems.BIG_LIGHT_KEY, PersistentDataType.BYTE) ||
            container.has(GulliverItems.SMALL_LIGHT_KEY, PersistentDataType.BYTE)) {
            event.isCancelled = true
        }
    }
}
