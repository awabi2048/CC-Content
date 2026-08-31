package jp.awabi2048.cccontent.features.cooking;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookingLiquidPresentationTest {
    @Test
    void eachLogicalUnitUsesOneDisplaySlotAndTwoHundredMillibuckets() {
        var contents = CookingLiquidContents.Companion.of(Map.of("soy_milk", 1, "bittern", 1));

        assertEquals(2, contents.getTotal());
        assertEquals(400, CookingLiquidVolume.toMillibuckets(contents.getTotal()));
        assertFalse(CookingLiquidPresentation.INSTANCE.isCollectable(contents));
    }

    @Test
    void liquidDefinitionsExposeCollectionContractsAndWeightedColor() {
        var seaWater = CookingLiquidContents.Companion.of(Map.of("sea_water", 5));
        var seaColor = CookingLiquidPresentation.INSTANCE.colorFor(seaWater);

        assertTrue(CookingLiquidPresentation.INSTANCE.isCollectable(seaWater));
        assertEquals("cooking.sea_water_bucket",
            CookingLiquidPresentation.INSTANCE.recoveryFor(seaWater).getCustomItemId());
        assertEquals(35, seaColor.getRed());
        assertEquals(105, seaColor.getGreen());
        assertEquals(180, seaColor.getBlue());
        assertFalse(CookingLiquidPresentation.INSTANCE.isCollectable(
            CookingLiquidContents.Companion.of(Map.of("soy_milk", 1, "bittern", 1))));
    }

    @Test
    void collectionContractConsumesOnlyOneBottleEquivalentAtATime() {
        var soyMilk = CookingLiquidContents.Companion.of(Map.of("soy_milk", 2));
        var recovery = CookingLiquidPresentation.INSTANCE.recoveryFor(soyMilk);

        assertNotNull(recovery);
        assertEquals(Map.of("soy_milk", 1), recovery.getConsumedAmounts());
        assertEquals(
            Map.of("soy_milk", 1),
            soyMilk.minusAll(recovery.getConsumedAmounts()).getAmounts()
        );
    }
}
