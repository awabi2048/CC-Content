package jp.awabi2048.cccontent.features.party

import java.util.UUID

data class PartyConfiguration(
    val defaultCapacity: Int = 6,
    val inviteExpirationMillis: Long = 60_000L,
    val offlineGraceMillis: Long = 300_000L
) {
    constructor(defaultCapacity: Int, inviteExpirationMillis: Long) : this(defaultCapacity, inviteExpirationMillis, 300_000L)

    init {
        require(defaultCapacity in 1..99) { "party.default_capacity must be between 1 and 99" }
        require(inviteExpirationMillis > 0) { "party.invite_expiration_millis must be positive" }
        require(offlineGraceMillis > 0) { "party.offline_grace_millis must be positive" }
    }
}

data class PartySnapshot(
    val id: UUID,
    val name: String,
    val description: String,
    val capacity: Int,
    val recruiting: Boolean,
    val leader: UUID,
    val members: Set<UUID>
)

data class PartyInvite(
    val partyId: UUID,
    val inviter: UUID,
    val invitee: UUID,
    val expiresAtMillis: Long
)

data class PendingPartyDeparture(
    val partyId: UUID,
    val member: UUID,
    val expiresAtMillis: Long
)

data class PartyChatMessage(
    val partyId: UUID,
    val sender: UUID,
    val message: String,
    val sentAtMillis: Long
)

interface PartyEventSink {
    fun onPartyUpdated(party: PartySnapshot) = Unit
    fun onInviteCreated(invite: PartyInvite) = Unit
    fun onMemberJoined(party: PartySnapshot, member: UUID) = Unit
    fun onMemberLeft(party: PartySnapshot, member: UUID) = Unit
    fun onMemberDeparturePending(party: PartySnapshot, member: UUID, expiresAtMillis: Long) = Unit
    fun onMemberDepartureCancelled(party: PartySnapshot, member: UUID) = Unit
    fun onMemberKicked(party: PartySnapshot, member: UUID) = Unit
    fun onLeaderTransferred(party: PartySnapshot, previousLeader: UUID, newLeader: UUID) = Unit
    fun onPartyDisbanded(party: PartySnapshot) = Unit
    fun onPartyMessage(message: PartyChatMessage, recipients: Set<UUID>) = Unit
}

object NoopPartyEventSink : PartyEventSink
