package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.features.rank.skill.handlers.UnlockBatchBreakHandler
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class BatchBreakPreviewListener : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onLeftClickBlock(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) {
            return
        }

        if (event.action != Action.LEFT_CLICK_BLOCK) {
            return
        }

        val clicked = event.clickedBlock ?: return
        UnlockBatchBreakHandler.previewForPlayer(event.player, clicked)
    }
}
