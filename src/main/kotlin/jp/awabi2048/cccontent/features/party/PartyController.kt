package jp.awabi2048.cccontent.features.party

import com.awabi2048.ccsystem.CCSystem
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Sound
import java.util.UUID

class PartyController(
    private val plugin: JavaPlugin,
    settings: PartySettings
) : PartyEventSink, AutoCloseable {
    val defaultCapacity: Int = settings.defaultCapacity
    private var expiryTask: org.bukkit.scheduler.BukkitTask? = null
    val interactionClaims = CCSystem.getAPI().getPlayerInteractionClaimService()
    private val chatChannel: PartyChatChannel = if (Bukkit.getPluginManager().isPluginEnabled("LunaChat")) {
        LunaPartyChatChannel()
    } else {
        DisabledPartyChatChannel
    }
    val feature = PartyFeature(
        PartyConfiguration(settings.defaultCapacity, settings.inviteExpirationMillis, settings.offlineGraceSeconds * 1000L),
        this,
        store = PartyStore(plugin.dataFolder.toPath().resolve("data/party.yml"))
    )
    val service: PartyService get() = feature.service

    fun processExpiredDepartures() {
        service.processExpiredDepartures(Bukkit.getOnlinePlayers().mapTo(HashSet(), Player::getUniqueId))
    }

    init {
        service.list().forEach(chatChannel::synchronize)
        expiryTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            processExpiredDepartures()
            service.purgeExpiredInvites()
        }, 1L, 20L)
    }

    fun message(player: Player, key: String, placeholders: Map<String, Any> = emptyMap()) {
        player.sendMessage(CCSystem.getAPI().getI18nString(player, key, placeholders))
    }

    fun text(player: Player, key: String, placeholders: Map<String, Any> = emptyMap()): String =
        CCSystem.getAPI().getI18nString(player, key, placeholders)

    fun openMenu(player: Player) = PartyMenu(this).open(player)

    fun toggleChat(player: Player): Boolean {
        val party = service.partyOf(player.uniqueId) ?: error("not_in_party")
        return chatChannel.toggle(player, party)
    }

    fun isPartyChatActive(player: Player, party: PartySnapshot): Boolean = chatChannel.isActive(player, party)

    fun resetChat(player: Player) = chatChannel.reset(player)

    fun synchronizeChat(party: PartySnapshot) = chatChannel.synchronize(party)

    fun broadcastRecruitment(party: PartySnapshot) {
        Bukkit.getOnlinePlayers().filter { service.partyOf(it.uniqueId) == null }.forEach { player ->
            val text = text(player, "party.recruiting_notice", mapOf("party" to party.name, "count" to party.members.size, "capacity" to party.capacity))
            player.sendMessage(Component.text(text).clickEvent(ClickEvent.runCommand("/party _join ${party.id}")))
        }
    }

    fun resolveOnline(name: String): Player? = Bukkit.getPlayerExact(name)

    fun playerName(id: UUID): String = Bukkit.getPlayer(id)?.name ?: id.toString().take(8)

    fun notifyParty(party: PartySnapshot, key: String, placeholders: Map<String, Any> = emptyMap()) {
        party.members.mapNotNull(Bukkit::getPlayer).forEach { message(it, key, placeholders) }
    }

    override fun onInviteCreated(invite: PartyInvite) {
        Bukkit.getPlayer(invite.invitee)?.let {
            val text = text(it, "party.invited", mapOf("party" to service.get(invite.partyId)?.name.orEmpty(), "inviter" to playerName(invite.inviter)))
            it.sendMessage(Component.text(text).clickEvent(ClickEvent.runCommand("/party _accept ${invite.partyId}")))
            it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.3f)
        }
    }

    override fun onPartyUpdated(party: PartySnapshot) {
        chatChannel.synchronize(party)
    }

    override fun onMemberJoined(party: PartySnapshot, member: UUID) {
        notifyParty(party, "party.member_joined", mapOf("player" to playerName(member)))
    }

    override fun onMemberLeft(party: PartySnapshot, member: UUID) {
        Bukkit.getPlayer(member)?.let(chatChannel::reset)
        notifyParty(party, "party.member_left", mapOf("player" to playerName(member)))
    }

    override fun onMemberKicked(party: PartySnapshot, member: UUID) {
        Bukkit.getPlayer(member)?.let(chatChannel::reset)
        Bukkit.getPlayer(member)?.let { message(it, "party.kicked") }
        notifyParty(party, "party.member_kicked", mapOf("player" to playerName(member)))
    }

    override fun onLeaderTransferred(party: PartySnapshot, previousLeader: UUID, newLeader: UUID) {
        notifyParty(party, "party.leader_transferred", mapOf("player" to playerName(newLeader)))
    }

    override fun onPartyDisbanded(party: PartySnapshot) {
        chatChannel.remove(party.id)
        notifyParty(party, "party.disbanded")
    }

    override fun onPartyMessage(message: PartyChatMessage, recipients: Set<UUID>) {
        val sender = playerName(message.sender)
        recipients.mapNotNull(Bukkit::getPlayer).forEach {
            this.message(it, "party.chat", mapOf("sender" to sender, "message" to message.message))
        }
    }

    override fun close() {
        expiryTask?.cancel()
        expiryTask = null
        chatChannel.close()
        feature.close()
    }
}
