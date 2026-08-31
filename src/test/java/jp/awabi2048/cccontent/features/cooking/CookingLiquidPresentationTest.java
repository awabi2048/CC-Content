package jp.awabi2048.cccontent.features.cooking;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CookingLiquidPresentationTest {
    @Test
    void eachLogicalUnitUsesOneDisplaySlotAndTwoHundredMillibuckets() {
        var contents = CookingLiquidContents.Companion.of(Map.of("soy_milk", 1, "bittern", 1));

        assertEquals(2, contents.getTotal());
        assertEquals(400, CookingLiquidVolume.toMillibuckets(contents.getTotal()));
        assertEquals(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            CookingLiquidPresentation.INSTANCE.paneFor(contents));
    }

    @Test
    void emptyDisplayUsesWhiteAndSingleLiquidsKeepTheirOwnColorFamily() {
        assertEquals(Material.WHITE_STAINED_GLASS_PANE,
            CookingLiquidPresentation.INSTANCE.paneFor(CookingLiquidContents.Companion.empty()));
        assertEquals(Material.CYAN_STAINED_GLASS_PANE,
            CookingLiquidPresentation.INSTANCE.paneFor(
                CookingLiquidContents.Companion.of(Map.of("sea_water", 5))));
        assertEquals(Material.GRAY_STAINED_GLASS_PANE,
            CookingLiquidPresentation.INSTANCE.paneFor(
                CookingLiquidContents.Companion.of(Map.of("bittern", 1))));
    }
}
