package jp.awabi2048.cccontent.features.party

import java.util.UUID

class PartyService @JvmOverloads constructor(
    private val configuration: PartyConfiguration = PartyConfiguration(),
    private val eventSink: PartyEventSink = NoopPartyEventSink,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val store: PartyStore? = null
) : AutoCloseable {
    private val parties = linkedMapOf<UUID, MutableParty>()
    private val memberParty = mutableMapOf<UUID, UUID>()
    private val invites = mutableMapOf<Pair<UUID, UUID>, PartyInvite>()
    private val pendingDepartures = linkedMapOf<UUID, PendingPartyDeparture>()
    private var enabled = true

    init {
        restore()
    }

    @JvmOverloads
    fun create(leader: UUID, name: String, description: String = "", capacity: Int = configuration.defaultCapacity, recruiting: Boolean = false): PartySnapshot {
        requireEnabled()
        requireText(name, "name")
        require(capacity in 1..99) { "party capacity must be between 1 and 99" }
        require(memberParty[leader] == null) { "player already belongs to a party" }
        val party = MutableParty(UUID.randomUUID(), name, description, capacity, recruiting, leader, linkedSetOf(leader))
        parties[party.id] = party
        memberParty[leader] = party.id
        persist()
        return party.snapshot()
    }

    fun get(partyId: UUID): PartySnapshot? = parties[partyId]?.snapshot()
    fun partyOf(member: UUID): PartySnapshot? = memberParty[member]?.let(::get)
    fun list(): List<PartySnapshot> = parties.values.map { it.snapshot() }
    fun recruitingParties(): List<PartySnapshot> = parties.values.filter { it.recruiting && it.members.size < it.capacity }.map { it.snapshot() }
    fun invitesFor(invitee: UUID, atMillis: Long = nowMillis()): List<PartyInvite> =
        invites.values.filter { it.invitee == invitee && it.expiresAtMillis > atMillis }.sortedByDescending { it.expiresAtMillis }

    fun update(partyId: UUID, actor: UUID, name: String? = null, description: String? = null, recruiting: Boolean? = null): PartySnapshot {
        requireEnabled(); val party = ownedParty(partyId, actor)
        name?.let { requireText(it, "name"); party.name = it }
        description?.let { party.description = it }
        recruiting?.let { party.recruiting = it }
        persist()
        return publish(party)
    }

    fun invite(partyId: UUID, inviter: UUID, invitee: UUID, atMillis: Long = nowMillis()): PartyInvite {
        requireEnabled(); val party = parties[partyId] ?: error("party not found")
        require(inviter in party.members) { "inviter is not a party member" }
        require(memberParty[invitee] == null) { "invitee already belongs to a party" }
        require(party.members.size < party.capacity) { "party is full" }
        val invite = PartyInvite(partyId, inviter, invitee, atMillis + configuration.inviteExpirationMillis)
        invites[partyId to invitee] = invite
        persist()
        eventSink.onInviteCreated(invite)
        return invite
    }

    fun acceptInvite(invitee: UUID, partyId: UUID, atMillis: Long = nowMillis()): PartySnapshot {
        requireEnabled(); val party = parties[partyId] ?: error("party not found")
        val inviteKey = partyId to invitee
        val invite = invites[inviteKey] ?: error("party invite not found")
        require(atMillis < invite.expiresAtMillis) { "party invite has expired" }
        require(memberParty[invitee] == null) { "player already belongs to a party" }
        require(party.members.size < party.capacity) { "party is full" }
        invites.remove(inviteKey)
        pendingDepartures.remove(invitee)
        party.members += invitee; memberParty[invitee] = partyId
        persist()
        val snapshot = publish(party); eventSink.onMemberJoined(snapshot, invitee); return snapshot
    }

    fun join(member: UUID, partyId: UUID): PartySnapshot {
        requireEnabled(); val party = parties[partyId] ?: error("party not found")
        require(party.recruiting) { "party is not recruiting" }
        require(memberParty[member] == null) { "player already belongs to a party" }
        require(party.members.size < party.capacity) { "party is full" }
        pendingDepartures.remove(member)
        party.members += member; memberParty[member] = partyId
        persist()
        val snapshot = publish(party); eventSink.onMemberJoined(snapshot, member); return snapshot
    }

    fun leave(member: UUID): PartySnapshot? {
        requireEnabled(); val party = memberParty[member]?.let { parties[it] } ?: return null
        require(member != party.leader) { "leader must transfer leadership or disband before leaving" }
        party.members -= member; memberParty -= member
        pendingDepartures.remove(member)
        persist()
        val snapshot = publish(party); eventSink.onMemberLeft(snapshot, member); return snapshot
    }

    fun scheduleDeparture(member: UUID, atMillis: Long = nowMillis()): PendingPartyDeparture? {
        requireEnabled()
        val party = memberParty[member]?.let { parties[it] } ?: return null
        val departure = PendingPartyDeparture(party.id, member, atMillis + configuration.offlineGraceMillis)
        pendingDepartures[member] = departure
        persist()
        eventSink.onMemberDeparturePending(party.snapshot(), member, departure.expiresAtMillis)
        return departure
    }

    fun cancelPendingDeparture(member: UUID, atMillis: Long = nowMillis()): Boolean {
        requireEnabled()
        val pending = pendingDepartures[member] ?: return false
        if (pending.expiresAtMillis <= atMillis) return false
        pendingDepartures.remove(member)
        persist()
        parties[pending.partyId]?.let { eventSink.onMemberDepartureCancelled(it.snapshot(), member) }
        return true
    }

    fun processExpiredDepartures(onlineMembers: Set<UUID>, atMillis: Long = nowMillis()) {
        requireEnabled()
        pendingDepartures.values.filter { it.expiresAtMillis <= atMillis }.toList().forEach { pending ->
            pendingDepartures.remove(pending.member)
            val party = parties[pending.partyId]
            if (party == null || pending.member !in party.members) {
                persist()
                return@forEach
            }
            if (pending.member == party.leader) {
                val replacement = party.members
                    .asSequence()
                    .filter { it != pending.member && it in onlineMembers }
                    .firstOrNull()
                    ?: party.members.firstOrNull { it != pending.member }
                if (replacement == null) {
                    val snapshot = party.snapshot()
                    parties.remove(party.id)
                    party.members.forEach { memberParty.remove(it) }
                    pendingDepartures.entries.removeIf { it.value.partyId == party.id }
                    invites.keys.removeIf { it.first == party.id }
                    persist()
                    eventSink.onPartyDisbanded(snapshot)
                    return@forEach
                }
                val previousLeader = party.leader
                party.leader = replacement
                persist()
                eventSink.onLeaderTransferred(party.snapshot(), previousLeader, replacement)
            }
            removeExpiredMember(party, pending.member)
        }
    }

    fun kick(partyId: UUID, actor: UUID, target: UUID): PartySnapshot {
        requireEnabled(); val party = ownedParty(partyId, actor)
        require(target != party.leader && party.members.remove(target)) { "target is not a removable party member" }
        memberParty -= target
        pendingDepartures.remove(target)
        persist()
        val snapshot = publish(party); eventSink.onMemberKicked(snapshot, target); return snapshot
    }

    fun transferLeadership(partyId: UUID, actor: UUID, newLeader: UUID): PartySnapshot {
        requireEnabled(); val party = ownedParty(partyId, actor)
        require(newLeader in party.members) { "new leader must be a party member" }
        val previous = party.leader; party.leader = newLeader
        persist()
        val snapshot = publish(party); eventSink.onLeaderTransferred(snapshot, previous, newLeader); return snapshot
    }

    fun disband(partyId: UUID, actor: UUID): PartySnapshot {
        requireEnabled(); val party = ownedParty(partyId, actor); val snapshot = party.snapshot()
        parties -= partyId
        party.members.forEach { memberParty -= it }
        pendingDepartures.entries.removeIf { it.value.partyId == partyId }
        invites.keys.removeIf { it.first == partyId }
        persist()
        eventSink.onPartyDisbanded(snapshot); return snapshot
    }

    @JvmOverloads
    fun sendChat(partyId: UUID, sender: UUID, message: String, atMillis: Long = nowMillis()): PartyChatMessage {
        requireEnabled(); val party = parties[partyId] ?: error("party not found")
        require(sender in party.members) { "sender is not a party member" }
        require(message.isNotBlank()) { "party message must not be blank" }
        val chat = PartyChatMessage(partyId, sender, message, atMillis)
        eventSink.onPartyMessage(chat, party.members.toSet()); return chat
    }

    fun purgeExpiredInvites(atMillis: Long = nowMillis()) { if (invites.entries.removeIf { it.value.expiresAtMillis <= atMillis }) persist() }
    fun isEnabled(): Boolean = enabled

    override fun close() { disable() }
    fun disable() {
        if (!enabled) return
        persist()
        enabled = false
    }

    private fun ownedParty(id: UUID, actor: UUID): MutableParty = parties[id]?.also { require(it.leader == actor) { "only the party leader may perform this operation" } } ?: error("party not found")
    private fun publish(party: MutableParty): PartySnapshot = party.snapshot().also(eventSink::onPartyUpdated)
    private fun requireEnabled() { check(enabled) { "party feature is disabled" } }
    private fun requireText(value: String, field: String) { require(value.isNotBlank()) { "party $field must not be blank" } }

    private fun persist() = store?.save(PartyStoreState(
        parties.values.map { it.snapshot() },
        invites.values.toList(),
        pendingDepartures.values.toList()
    ))

    private fun restore() {
        val state = store?.load() ?: return
        state.parties.forEach { snapshot ->
            require(snapshot.members.all { memberParty[it] == null }) { "party member belongs to multiple parties" }
            parties[snapshot.id] = MutableParty(snapshot.id, snapshot.name, snapshot.description, snapshot.capacity, snapshot.recruiting, snapshot.leader, snapshot.members.toCollection(LinkedHashSet()))
            snapshot.members.forEach { memberParty[it] = snapshot.id }
        }
        val validInvites = state.invites.filter { invite ->
            invite.expiresAtMillis > nowMillis() && invite.partyId in parties && invite.invitee !in memberParty
        }
        validInvites.forEach { invites[it.partyId to it.invitee] = it }
        state.pendingDepartures.forEach { pending ->
            if (pending.partyId in parties && memberParty[pending.member] == pending.partyId) {
                pendingDepartures[pending.member] = pending
            }
        }
    }

    private fun removeExpiredMember(party: MutableParty, member: UUID) {
        if (!party.members.remove(member)) return
        memberParty.remove(member)
        persist()
        val snapshot = party.snapshot()
        if (party.members.isEmpty()) {
            parties.remove(party.id)
            invites.keys.removeIf { it.first == party.id }
            persist()
            eventSink.onPartyDisbanded(snapshot)
        } else {
            eventSink.onMemberLeft(snapshot, member)
            eventSink.onPartyUpdated(snapshot)
        }
    }

    private data class MutableParty(val id: UUID, var name: String, var description: String, val capacity: Int, var recruiting: Boolean, var leader: UUID, val members: LinkedHashSet<UUID>) {
        fun snapshot() = PartySnapshot(id, name, description, capacity, recruiting, leader, LinkedHashSet(members))
    }
}
