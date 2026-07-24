package jp.awabi2048.cccontent.features.resourcecollection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChiselHitPolicyTest {
    @Test
    void hitCoordinatesAreQuantizedToOneSixteenthBlockPixels() {
        assertEquals(0.0, ChiselHitPolicy.INSTANCE.quantizedDistancePixels(0.02, -0.02));
        assertEquals(1.0, ChiselHitPolicy.INSTANCE.quantizedDistancePixels(1.0 / 16.0, 0.0));
        assertEquals(
            Math.sqrt(5.0),
            ChiselHitPolicy.INSTANCE.quantizedDistancePixels(2.0 / 16.0, 1.0 / 16.0),
            0.000001
        );
    }

    @Test
    void precisionBonusNeverExpandsTheRadiusBeyondThreePixels() {
        assertEquals(2.0, ChiselHitPolicy.INSTANCE.tolerancePixels(0.0));
        assertEquals(2.32, ChiselHitPolicy.INSTANCE.tolerancePixels(0.02), 0.000001);
        assertEquals(2.64, ChiselHitPolicy.INSTANCE.tolerancePixels(0.04), 0.000001);
        assertEquals(3.0, ChiselHitPolicy.INSTANCE.tolerancePixels(1.0));
    }

    @Test
    void blockDamageTracksConsumedAttempts() {
        assertEquals(0.1f, ChiselHitPolicy.INSTANCE.blockDamageProgress(0, 3));
        assertEquals(1.0f / 3.0f, ChiselHitPolicy.INSTANCE.blockDamageProgress(1, 3));
        assertEquals(2.0f / 3.0f, ChiselHitPolicy.INSTANCE.blockDamageProgress(2, 3));
        assertEquals(1.0f, ChiselHitPolicy.INSTANCE.blockDamageProgress(3, 3));
    }
}
