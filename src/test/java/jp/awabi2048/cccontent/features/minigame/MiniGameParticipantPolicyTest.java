package jp.awabi2048.cccontent.features.minigame;

import jp.awabi2048.cccontent.features.minigame.core.MiniGameParticipantPolicy;
import jp.awabi2048.cccontent.features.minigame.core.MiniGameSupportedGames;
import jp.awabi2048.cccontent.features.party.PartySnapshot;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniGameParticipantPolicyTest {
    private static final UUID LEADER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID OUTSIDER = UUID.randomUUID();

    @Test
    void acceptsOnlyTheAdministratorsPartyMembersInTheGameWorld() {
        PartySnapshot party = new PartySnapshot(UUID.randomUUID(), "party", "", 6, false, LEADER, Set.of(LEADER, MEMBER, OUTSIDER));
        assertEquals(Set.of(LEADER, MEMBER), MiniGameParticipantPolicy.INSTANCE.select(
                LEADER, party, Set.of(LEADER, MEMBER), Set.of(LEADER, MEMBER), Set.of()));
    }

    @Test
    void nonLeaderCannotAdministerThePartyGame() {
        PartySnapshot party = new PartySnapshot(UUID.randomUUID(), "party", "", 6, false, LEADER, Set.of(LEADER, MEMBER));
        assertThrows(IllegalArgumentException.class, () -> MiniGameParticipantPolicy.INSTANCE.select(
                MEMBER, party, Set.of(LEADER, MEMBER), Set.of(LEADER, MEMBER), Set.of()));
    }

    @Test
    void unknownGameIdsAreNotSupported() {
        assertTrue(MiniGameSupportedGames.INSTANCE.getIds().contains("race"));
        assertTrue(!MiniGameSupportedGames.INSTANCE.getIds().contains("unknown"));
    }
}
