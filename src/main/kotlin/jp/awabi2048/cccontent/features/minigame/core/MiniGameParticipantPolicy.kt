package jp.awabi2048.cccontent.features.minigame.core

import jp.awabi2048.cccontent.features.party.PartySnapshot
import java.util.UUID

/** 開始者が管理するパーティーのメンバーだけを、同一ワールドの参加者として確定する。 */
object MiniGameParticipantPolicy {
    fun select(
        administrator: UUID,
        party: PartySnapshot,
        onlinePlayers: Set<UUID>,
        worldMembers: Set<UUID>,
        activePlayers: Set<UUID>
    ): Set<UUID> {
        require(party.leader == administrator) { "only the party leader may administer a mini-game" }
        return party.members
            .intersect(onlinePlayers)
            .intersect(worldMembers)
            .minus(activePlayers)
    }
}
