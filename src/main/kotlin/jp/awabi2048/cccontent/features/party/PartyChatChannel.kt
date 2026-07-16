package jp.awabi2048.cccontent.features.party

import com.github.ucchyocean.lc3.LunaChatBukkit
import com.github.ucchyocean.lc3.member.ChannelMember
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

interface PartyChatChannel : AutoCloseable {
    fun synchronize(party: PartySnapshot)
    fun toggle(player: Player, party: PartySnapshot): Boolean
    fun isActive(player: Player, party: PartySnapshot): Boolean
    fun remove(partyId: UUID)
    fun reset(player: Player)
}

object DisabledPartyChatChannel : PartyChatChannel {
    override fun synchronize(party: PartySnapshot) = Unit
    override fun toggle(player: Player, party: PartySnapshot): Boolean = false
    override fun isActive(player: Player, party: PartySnapshot): Boolean = false
    override fun remove(partyId: UUID) = Unit
    override fun reset(player: Player) = Unit
    override fun close() = Unit
}

class LunaPartyChatChannel : PartyChatChannel {
    private val api = LunaChatBukkit.getInstance().lunaChatAPI
    private val activeDefaults = mutableMapOf<UUID, String>()

    override fun synchronize(party: PartySnapshot) {
        val channelName = channelName(party.id)
        val channel = api.getChannel(channelName) ?: api.createChannel(channelName)
        channel.alias = party.name
        channel.description = "CC-Content Party"
        channel.isVisible = false
        channel.format = "&b[Party] &f%player: %msg"

        val expected = party.members.mapNotNull(Bukkit::getPlayer).map(ChannelMember::getChannelMember).toSet()
        channel.members.filter { it !in expected }.forEach(channel::removeMember)
        expected.filter { it !in channel.members }.forEach(channel::addMember)
    }

    override fun toggle(player: Player, party: PartySnapshot): Boolean {
        synchronize(party)
        val channelName = channelName(party.id)
        return if (activeDefaults[player.uniqueId] == channelName) {
            api.removeDefaultChannel(player.name)
            activeDefaults.remove(player.uniqueId)
            false
        } else {
            api.setDefaultChannel(player.name, channelName)
            activeDefaults[player.uniqueId] = channelName
            true
        }
    }

    override fun isActive(player: Player, party: PartySnapshot): Boolean =
        activeDefaults[player.uniqueId] == channelName(party.id)

    override fun remove(partyId: UUID) {
        val name = channelName(partyId)
        activeDefaults.filterValues { it == name }.keys.toList().forEach { playerId ->
            Bukkit.getPlayer(playerId)?.let { api.removeDefaultChannel(it.name) }
            activeDefaults.remove(playerId)
        }
        api.removeChannel(name)
    }

    override fun reset(player: Player) {
        if (activeDefaults.remove(player.uniqueId) != null) api.removeDefaultChannel(player.name)
    }

    override fun close() {
        activeDefaults.keys.toList().mapNotNull(Bukkit::getPlayer).forEach(::reset)
        api.channels.map { it.name }.filter { it.startsWith(CHANNEL_PREFIX) }.forEach(api::removeChannel)
    }

    private fun channelName(partyId: UUID): String = CHANNEL_PREFIX + partyId.toString().replace("-", "")

    companion object {
        private const val CHANNEL_PREFIX = "ccparty_"
    }
}
