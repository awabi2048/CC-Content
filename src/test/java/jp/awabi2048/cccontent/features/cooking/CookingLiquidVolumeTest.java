package jp.awabi2048.cccontent.features.cooking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CookingLiquidVolumeTest {
    @Test
    void canonicalUnitUsesTwoHundredMillibuckets() {
        assertEquals(200, CookingLiquidVolume.MILLIBUCKETS_PER_UNIT);
        assertEquals(1000, CookingLiquidVolume.toMillibuckets(5));
    }

    @Test
    void logicalVolumeIsMappedToVisualCauldronLevels() {
        assertEquals(0, CookingLiquidVolume.toVanillaLevel(0));
        assertEquals(1, CookingLiquidVolume.toVanillaLevel(1));
        assertEquals(2, CookingLiquidVolume.toVanillaLevel(3));
        assertEquals(3, CookingLiquidVolume.toVanillaLevel(5));
        assertEquals(2, CookingLiquidVolume.fromVanillaLevel(1));
        assertEquals(3, CookingLiquidVolume.fromVanillaLevel(2));
        assertEquals(5, CookingLiquidVolume.fromVanillaLevel(3));
    }
}
