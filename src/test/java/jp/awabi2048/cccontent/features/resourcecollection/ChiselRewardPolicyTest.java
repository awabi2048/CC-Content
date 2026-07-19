package jp.awabi2048.cccontent.features.resourcecollection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChiselRewardPolicyTest {
    @Test
    void accuracyBandsProduceBoundedSpecialMaterialCounts() {
        assertEquals(0, ChiselRewardPolicy.INSTANCE.specialMaterialCount(0.20, false, 0));
        assertEquals(1, ChiselRewardPolicy.INSTANCE.specialMaterialCount(0.40, false, 0));
        assertEquals(2, ChiselRewardPolicy.INSTANCE.specialMaterialCount(0.70, false, 0));
        assertEquals(3, ChiselRewardPolicy.INSTANCE.specialMaterialCount(0.90, false, 0));
        assertEquals(4, ChiselRewardPolicy.INSTANCE.specialMaterialCount(0.95, false, 1));
    }

    @Test
    void precisionMinimumAppliesOnlyBelowTheStandardBand() {
        assertEquals(1, ChiselRewardPolicy.INSTANCE.specialMaterialCount(0.10, true, 0));
        assertEquals(1, ChiselRewardPolicy.INSTANCE.specialMaterialCount(0.50, true, 0));
        assertEquals(4, ChiselRewardPolicy.INSTANCE.specialMaterialCount(0.88, false, 1, 0.88));
        assertThrows(IllegalArgumentException.class,
            () -> ChiselRewardPolicy.INSTANCE.specialMaterialCount(1.1, false, 0));
    }

    @Test
    void oneMinorFailureCanBeIgnoredWithoutCountingAnAttempt() {
        ChiselAttemptResult ignored = ChiselAttemptPolicy.INSTANCE.evaluate(0.25, 0.22, 1);
        assertEquals(0.0, ignored.getScore());
        assertEquals(false, ignored.getCountsAsAttempt());
        assertEquals(true, ignored.getConsumesIgnoredFailure());

        ChiselAttemptResult consumed = ChiselAttemptPolicy.INSTANCE.evaluate(0.25, 0.22, 0);
        assertEquals(true, consumed.getCountsAsAttempt());
        assertEquals(false, consumed.getConsumesIgnoredFailure());

        ChiselAttemptResult major = ChiselAttemptPolicy.INSTANCE.evaluate(0.40, 0.22, 1);
        assertEquals(true, major.getCountsAsAttempt());
        assertEquals(false, major.getConsumesIgnoredFailure());
    }
}
