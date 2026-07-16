package jp.awabi2048.cccontent.features.rank;

import jp.awabi2048.cccontent.features.rank.profession.Profession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankReleasePolicyTest {
    @Test
    void adminBypassesProfessionAndSkillRelease() {
        assertTrue(RankReleasePolicy.INSTANCE.isProfessionAccessible(true, Profession.MINER));
        assertTrue(RankReleasePolicy.INSTANCE.isSkillSystemAccessible(true));
    }

    @Test
    void noProfessionIsPublicInitially() {
        assertFalse(RankReleasePolicy.INSTANCE.isProfessionPublic(Profession.MINER));
        assertFalse(RankReleasePolicy.INSTANCE.isProfessionAccessible(false, Profession.MINER));
    }

    @Test
    void skillsArePrivateInitially() {
        assertFalse(RankReleasePolicy.INSTANCE.isSkillSystemAccessible(false));
    }
}
