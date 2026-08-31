package jp.awabi2048.cccontent.features.cooking;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookingLiquidRecipeTest {
    private static final File ROOT = new File("src/main/resources");

    @Test
    void bundledLiquidRecipesUseTheApprovedDurationsAndHeat() {
        var configuration = UnifiedCookingConfigurationLoader.load(ROOT);
        var salt = configuration.getLiquidRecipes().get("liquid:sea_salt");
        var tofu = configuration.getLiquidRecipes().get("liquid:tofu");

        assertNotNull(salt);
        assertNotNull(tofu);
        assertEquals(CookingHeat.NORMAL, salt.getHeat());
        assertEquals(60, salt.getDurationSeconds());
        assertEquals(Map.of("sea_water", 5), salt.getLiquidInputs());
        assertEquals(Map.of("bittern", 1), salt.getResidualLiquids());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE.name(), salt.getSnapshot().getLiquidPaneMaterial());
        assertTrue(salt.getCollectSolidResultWithLiquid());
        assertEquals(30, tofu.getDurationSeconds());
        assertEquals(Map.of("soy_milk", 1, "bittern", 1), tofu.getLiquidInputs());
        assertEquals(4, tofu.getResult().getAmountPerScale());
    }

    @Test
    void mixedLiquidStateAcceptsOnlyRegisteredRecipePrefixes() {
        var configuration = UnifiedCookingConfigurationLoader.load(ROOT);
        var recipes = configuration.getLiquidRecipes().values();
        var soy = CookingLiquidContents.Companion.of(Map.of("soy_milk", 1));
        var water = CookingLiquidContents.Companion.of(Map.of("water", 1));
        var mixed = soy.plus("bittern", 1);

        assertTrue(soy.isPotentialInputFor(recipes));
        assertTrue(mixed.getAmounts().equals(Map.of("bittern", 1, "soy_milk", 1)));
        assertEquals("liquid:tofu", CookingLiquidRecipeMatcher.INSTANCE.find(mixed, recipes).getId());
        assertFalse(water.isPotentialInputFor(recipes));
        assertEquals(2, mixed.getTotal());
    }

    @Test
    void saltCompletionProducesSolidAndCollectibleBittern() {
        var recipe = UnifiedCookingConfigurationLoader.load(ROOT)
            .getLiquidRecipes().get("liquid:sea_salt");
        var started = CookingStationStateMachine.INSTANCE.start(
            recipe.getStationRecipe(), recipe.getSnapshot(), "player", 1, CookingHeat.NORMAL,
            List.of(), 0.0
        );
        var current = started;
        while (current.getRemainingTicks() > 0) {
            var step = CookingStationStateMachine.INSTANCE.tick(current, CookingHeat.NORMAL);
            current = step instanceof CookingStationStep.Updated updated
                ? updated.getSession()
                : ((CookingStationStep.Completed) step).getSession();
        }

        var finished = CookingStationStateMachine.INSTANCE.finishLiquid(current, recipe);
        assertEquals(CookingProcessState.READY_LIQUID, finished.getState());
        assertEquals("cooking.salt", finished.getOutputStacks().getFirst().getCustomItemId());
        assertEquals(1, finished.getReservoir().getRemaining());
        assertEquals("cooking.nigari_bottle", finished.getReservoir().getCustomItemId());
        assertEquals(Material.GLASS_BOTTLE.name(), finished.getReservoir().getContainerMaterial());

        var collected = CookingStationStateMachine.INSTANCE.collectLiquid(finished, true);
        assertNotNull(collected);
        assertEquals(CookingProcessState.IDLE, collected.getState());
        assertTrue(collected.getOutputStacks().isEmpty());
    }
}
