package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItemManager
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent

/**
 * GulliverLight アイテムのイベント処理
 */
class GulliverItemListener : Listener {
    
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand
        
        // カスタムアイテムを識別
        val customItem = CustomItemManager.identify(item) ?: return
        if (customItem.feature != "misc") return
        
        // アイテムの種類による処理
        when (customItem.id) {
            "big_light" -> handleBigLight(player, event)
            "small_light" -> handleSmallLight(player, event)
        }
    }
    
    private fun handleBigLight(player: Player, event: PlayerInteractEvent) {
        // Shift + 右クリック: スケールをリセット
        if (player.isSneaking && event.action.name.contains("RIGHT")) {
            resetScale(player)
            return
        }
        
        // 右クリック長押しの場合のみ処理
        if (event.action.name.contains("RIGHT")) {
            if (player.isHandRaised) {
                // スケール増加
                increaseScale(player, 0.05, 5.0)
            }
        }
    }
    
    private fun handleSmallLight(player: Player, event: PlayerInteractEvent) {
        // Shift + 右クリック: スケールをリセット
        if (player.isSneaking && event.action.name.contains("RIGHT")) {
            resetScale(player)
            return
        }
        
        // 右クリック長押しの場合のみ処理
        if (event.action.name.contains("RIGHT")) {
            if (player.isHandRaised) {
                // スケール減少
                decreaseScale(player, 0.05, 0.1)
            }
        }
    }
    
    private fun increaseScale(player: Player, increment: Double, max: Double) {
        val scale = player.getAttribute(Attribute.GENERIC_SCALE)?.value ?: 1.0
        val newScale = (scale + increment).coerceAtMost(max)
        
        player.getAttribute(Attribute.GENERIC_SCALE)?.baseValue = newScale
        
        if (newScale >= max) {
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 0.5f)
        } else {
            player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f)
        }
    }
    
    private fun decreaseScale(player: Player, decrement: Double, min: Double) {
        val scale = player.getAttribute(Attribute.GENERIC_SCALE)?.value ?: 1.0
        val newScale = (scale - decrement).coerceAtLeast(min)
        
        player.getAttribute(Attribute.GENERIC_SCALE)?.baseValue = newScale
        
        if (newScale <= min) {
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 0.5f)
        } else {
            player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f)
        }
    }
    
    private fun resetScale(player: Player) {
        player.getAttribute(Attribute.GENERIC_SCALE)?.baseValue = 1.0
        player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f)
        player.sendMessage("§aスケールをリセットしました")
    }
}
