package jp.awabi2048.cccontent.features.cooking;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedCookingResourceValidationTest {
    private static final File ROOT = new File("src/main/resources");

    @Test
    void bundledConfigurationContainsEverySpecifiedInteractiveRecipe() {
        UnifiedCookingConfiguration configuration = UnifiedCookingConfigurationLoader.load(ROOT);

        assertEquals(100, configuration.getIngredients().size());
        assertEquals(35, configuration.getCuttingRecipes().size());
        assertEquals(53, configuration.getRecipes().size());
        assertEquals(48, configuration.getRecipes().values().stream()
            .filter(recipe -> recipe.getDefinition().getExperience() > 0).count());
        assertTrue(configuration.getCuttingRecipes().containsKey("fillet_pufferfish"));
        assertFalse(configuration.getCuttingRecipes().containsKey("fillet_ancient_coelacanth"));
        assertTrue(configuration.getRecipes().keySet().containsAll(Set.of(
            "cooked_rice", "tomato_sauce", "potato_soup", "pork_soup",
            "coffee", "adventurers_hotpot", "soul_fire_tuna_steak"
        )));
    }

    @Test
    void oldNonAlcoholicBreweryRecipesAreAllOwnedByCooking() {
        UnifiedCookingConfiguration configuration = UnifiedCookingConfigurationLoader.load(ROOT);
        assertTrue(configuration.getRecipes().keySet().containsAll(Set.of(
            "pork_soup", "potato_soup", "coffee", "rd_smoothie", "yl_smoothie",
            "pl_smoothie", "mix_smoothie", "or_smoothie", "bl_smoothie",
            "vt_smoothie", "gr_smoothie"
        )));
    }

    @Test
    void failureResultsUseOnlyTheSixSpecifiedSharedItems() {
        UnifiedCookingConfiguration configuration = UnifiedCookingConfigurationLoader.load(ROOT);
        assertEquals(Set.of(
            "cooking.burnt_solid_food", "cooking.burnt_bowl_food", "cooking.burnt_bottle_liquid",
            "cooking.undercooked_solid_food", "cooking.undercooked_bowl_food",
            "cooking.underprepared_bottle_liquid"
        ), configuration.getRecipes().values().stream()
            .map(recipe -> recipe.getFailureResult().getCustomItemId())
            .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void breweryPreparationsShareTheCookingCauldronMatcher() {
        UnifiedCookingConfiguration cooking = UnifiedCookingConfigurationLoader.load(ROOT);
        var preparations = BreweryPreparationConfigurationLoader.INSTANCE.load(ROOT, cooking.getIngredients());
        assertEquals(26, preparations.size());
        assertTrue(preparations.values().stream().allMatch(it ->
            it.getRecipe().getStation() == CookingStation.CAULDRON &&
                it.getRecipe().getWaterUnits() == 3 && it.getRecipe().getResultKind() == CookingResultKind.BOTTLE));
        assertEquals(CookingHeat.HIGH, preparations.get("chorus_spirit_wash").getRecipe().getHeat());
    }
}
