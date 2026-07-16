package jp.awabi2048.cccontent.features.party

import com.awabi2048.ccsystem.api.event.PlayerLeftClickPlayerEvent
import jp.awabi2048.cccontent.gui.SimpleConfirmationDialog
import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

class PartyMenuListener(private val controller: PartyController) : Listener {
    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? PartyMenuHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return
        val party = controller.service.get(holder.partyId) ?: return
        when (event.rawSlot) {
            20 -> if (party.leader == player.uniqueId) PartyMenu(controller).showSettingsDialog(player, party)
            22 -> if (party.leader == player.uniqueId) {
                val updated = controller.service.update(party.id, player.uniqueId, recruiting = !party.recruiting)
                if (updated.recruiting) controller.broadcastRecruitment(updated)
                controller.openMenu(player)
            }
            24 -> if (party.leader == player.uniqueId) SimpleConfirmationDialog.show(
                player,
                Component.text(controller.text(player, "party.dialog.disband.title")),
                Component.text(controller.text(player, "party.dialog.disband.body")),
                Component.text(controller.text(player, "party.dialog.submit")),
                Component.text(controller.text(player, "party.dialog.cancel")),
                { controller.service.disband(party.id, it.uniqueId); it.closeInventory() }
            )
            38 -> {
                controller.message(player, if (controller.toggleChat(player)) "party.chat_channel.enabled" else "party.chat_channel.disabled")
                controller.openMenu(player)
            }
            42 -> if (party.members.size < party.capacity) {
                if (event.isRightClick) PartyMenu(controller).showInviteDialog(player) else beginClickInvite(player)
            } else Unit
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is PartyMenuHolder) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerLeftClick(event: PlayerLeftClickPlayerEvent) {
        if (!controller.interactionClaims.isClaimedBy(event.player.uniqueId, CLAIM_OWNER)) return
        event.isCancelled = true
        controller.interactionClaims.release(event.player.uniqueId, CLAIM_OWNER)
        val party = controller.service.partyOf(event.player.uniqueId) ?: return
        runCatching { controller.service.invite(party.id, event.player.uniqueId, event.target.uniqueId) }
            .onSuccess { controller.message(event.player, "party.invite_sent", mapOf("player" to event.target.name)) }
            .onFailure { controller.message(event.player, "party.error.invalid_action") }
    }

    private fun beginClickInvite(player: org.bukkit.entity.Player) {
        if (!controller.interactionClaims.tryClaim(player.uniqueId, CLAIM_OWNER)) {
            controller.message(player, "party.interaction.busy")
            return
        }
        player.closeInventory()
        controller.message(player, "party.interaction.invite_waiting")
    }

    companion object {
        const val CLAIM_OWNER = "cc-content:party-invite"
    }
}
