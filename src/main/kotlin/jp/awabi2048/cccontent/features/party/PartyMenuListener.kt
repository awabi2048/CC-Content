package jp.awabi2048.cccontent.features.party

import com.awabi2048.ccsystem.api.event.PlayerLeftClickPlayerEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class PartyMenuListener(private val controller: PartyController) : Listener {
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

    companion object {
        const val CLAIM_OWNER = "cc-content:party-invite"
    }
}
