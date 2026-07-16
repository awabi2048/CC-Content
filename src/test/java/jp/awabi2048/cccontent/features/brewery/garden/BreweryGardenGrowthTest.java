package jp.awabi2048.cccontent.features.brewery.garden;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreweryGardenGrowthTest {
    private final GardenPlant plant = new GardenPlant(
            "apple",
            "custom_items.brewery.garden_seed_apple.name",
            "custom_items.brewery.garden_fruit_apple.name",
            List.of(
                    new GardenStage(0, Material.SWEET_BERRY_BUSH, "age=0", 10),
                    new GardenStage(1, Material.SWEET_BERRY_BUSH, "age=1", 10),
                    new GardenStage(2, Material.SWEET_BERRY_BUSH, "age=3", 10)
            ),
            0
    );

    @Test
    void elapsedTimeAdvancesEveryDueStage() {
        GardenPlantState state = new GardenPlantState("apple", 0, 0, 10_000);

        assertFalse(GardenGrowth.INSTANCE.advance(plant, state, 9_999));
        assertTrue(GardenGrowth.INSTANCE.advance(plant, state, 20_000));
        assertEquals(2, state.getStage());
        assertEquals(Long.MAX_VALUE, state.getNextGrowthAtMillis());
    }

    @Test
    void harvestReturnsToConfiguredRegrowthStage() {
        GardenPlantState state = GardenGrowth.INSTANCE.regrow(plant, 50_000);

        assertEquals("apple", state.getPlantId());
        assertEquals(0, state.getStage());
        assertEquals(60_000, state.getNextGrowthAtMillis());
    }
}
