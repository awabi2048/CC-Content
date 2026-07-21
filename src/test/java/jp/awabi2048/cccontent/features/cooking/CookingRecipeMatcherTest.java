package jp.awabi2048.cccontent.features.cooking;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CookingRecipeMatcherTest {
    private CookingRecipeDefinition recipe(String id, CookingStation station, CookingHeat heat, Map<String, Integer> ingredients, int water) {
        return new CookingRecipeDefinition(
            id, station, "BASIC", CookingTier.BASIC, heat, ingredients, water, 8, 10, CookingResultKind.ITEM
        );
    }

    @Test
    void exactCurrentHeatAlwaysBeatsOppositeHeatAndApproximate() {
        var current = recipe("current", CookingStation.PAN, CookingHeat.NORMAL, Map.of("potato", 2), 0);
        var opposite = recipe("opposite", CookingStation.PAN, CookingHeat.HIGH, Map.of("potato", 2), 0);
        var result = CookingRecipeMatcher.INSTANCE.select(
            List.of(opposite, current), CookingStation.PAN, Map.of("potato", 2),
            CookingHeat.NORMAL, 0, 0.0, new CookingMatchSettings()
        );

        assertInstanceOf(CookingMatchResult.Selected.class, result);
        assertEquals("current", ((CookingMatchResult.Selected) result).getMatch().getRecipe().getId());
    }

    @Test
    void cauldronScaleComesOnlyFromWaterLevel() {
        var recipe = recipe("soup", CookingStation.CAULDRON, CookingHeat.NORMAL, Map.of("potato", 2), 1);
        var selected = (CookingMatchResult.Selected) CookingRecipeMatcher.INSTANCE.select(
            List.of(recipe), CookingStation.CAULDRON, Map.of("potato", 6),
            CookingHeat.NORMAL, 3, 0.0, new CookingMatchSettings()
        );
        assertEquals(3, selected.getMatch().getScale());
        assertEquals(CookingMatchResult.NoMatch.INSTANCE, CookingRecipeMatcher.INSTANCE.select(
            List.of(recipe), CookingStation.CAULDRON, Map.of("potato", 4),
            CookingHeat.NORMAL, 1, 0.0, new CookingMatchSettings()
        ));
    }

    @Test
    void unknownRatioAndAmbiguityAreRejected() {
        var first = recipe("first", CookingStation.PAN, CookingHeat.NORMAL, Map.of("potato", 10), 0);
        var second = recipe("second", CookingStation.PAN, CookingHeat.NORMAL, Map.of("potato", 9, "salt", 1), 0);
        var ambiguous = CookingRecipeMatcher.INSTANCE.select(
            List.of(first, second), CookingStation.PAN, Map.of("potato", 10, "salt", 1),
            CookingHeat.NORMAL, 0, 0.0, new CookingMatchSettings()
        );
        assertInstanceOf(CookingMatchResult.Ambiguous.class, ambiguous);

        assertEquals(CookingMatchResult.NoMatch.INSTANCE, CookingRecipeMatcher.INSTANCE.select(
            List.of(first), CookingStation.PAN, Map.of("potato", 10, "dirt", 3),
            CookingHeat.NORMAL, 0, 0.0, new CookingMatchSettings()
        ));
    }

    @Test
    void cuttingAcceptsOnlyExactIntegerMultiples() {
        var cut = recipe("cut", CookingStation.CUTTING, null, Map.of("onion", 1), 0);
        var selected = (CookingMatchResult.Selected) CookingRecipeMatcher.INSTANCE.select(
            List.of(cut), CookingStation.CUTTING, Map.of("onion", 12), null, 0, 0.0,
            new CookingMatchSettings()
        );
        assertEquals(12, selected.getMatch().getScale());
        assertEquals(CookingMatchResult.NoMatch.INSTANCE, CookingRecipeMatcher.INSTANCE.select(
            List.of(cut), CookingStation.CUTTING, Map.of("onion", 12, "dirt", 1), null, 0, 0.0,
            new CookingMatchSettings()
        ));
    }
}
