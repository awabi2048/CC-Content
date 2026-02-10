package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.features.rank.skill.handlers.UnlockBatchBreakHandler
import net.kyori.adventure.text.Component
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerToggleSneakEvent

class BatchBreakToggleListener : Listener {

    @EventHandler
    fun onPlayerSneakDoubleTap(event: PlayerToggleSneakEvent) {
        if (!event.isSneaking) {
            return
        }

        val player = event.player

        val playerUuid = player.uniqueId
        if (!UnlockBatchBreakHandler.markSneakClickAndCheckDoubleClick(playerUuid)) {
            return
        }

        val mode = UnlockBatchBreakHandler.BatchBreakMode.fromTool(player.inventory.itemInMainHand.type) ?: return

        if (!UnlockBatchBreakHandler.canUseMode(playerUuid, mode)) {
            player.sendActionBar(Component.text("${mode.modeKey}: 利用不可"))
            return
        }

        val enabled = UnlockBatchBreakHandler.toggleMode(playerUuid, mode)
        player.playSound(
            player.location,
            Sound.UI_BUTTON_CLICK,
            0.8f,
            if (enabled) 1.2f else 0.8f
        )
        player.sendActionBar(Component.text("${mode.modeKey}: ${if (enabled) "ON" else "OFF"}"))
    }
}
