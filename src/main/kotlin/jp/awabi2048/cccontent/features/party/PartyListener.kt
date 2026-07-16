package jp.awabi2048.cccontent.features.party

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerJoinEvent

class PartyListener(private val controller: PartyController) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val member = event.player.uniqueId
        controller.processExpiredDepartures()
        controller.service.cancelPendingDeparture(member)
        controller.service.partyOf(member)?.let(controller::synchronizeChat)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        controller.interactionClaims.release(event.player.uniqueId, PartyMenuListener.CLAIM_OWNER)
        controller.resetChat(event.player)
        runCatching { controller.service.scheduleDeparture(event.player.uniqueId) }
    }
}
