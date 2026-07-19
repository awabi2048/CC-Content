package jp.awabi2048.cccontent.features.resourcecollection;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalResourceBonusPolicyTest {
    @Test
    void playerOriginNeverProducesBonusResources() {
        assertFalse(NormalResourceBonusPolicy.INSTANCE.succeeds(1.0, false, new Random(0)));
    }

    @Test
    void chanceUsesOneIndependentRollWithoutFortuneInput() {
        Random alwaysLow = new Random() {
            @Override
            public double nextDouble() {
                return 0.01;
            }
        };
        Random alwaysHigh = new Random() {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };

        assertTrue(NormalResourceBonusPolicy.INSTANCE.succeeds(0.05, true, alwaysLow));
        assertFalse(NormalResourceBonusPolicy.INSTANCE.succeeds(0.05, true, alwaysHigh));
        assertThrows(IllegalArgumentException.class,
            () -> NormalResourceBonusPolicy.INSTANCE.succeeds(1.01, true, alwaysLow));
    }
}
