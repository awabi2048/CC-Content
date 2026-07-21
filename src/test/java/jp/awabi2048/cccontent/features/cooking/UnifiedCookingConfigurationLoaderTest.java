package jp.awabi2048.cccontent.features.cooking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedCookingConfigurationLoaderTest {
    @TempDir Path temp;

    @Test
    void loadsVersionThreeSettingsAndRejectsLegacySettings() throws Exception {
        Path current = temp.resolve("current.yml");
        Files.writeString(current, """
            config_version: 3
            enabled: true
            matching:
              maximum_excess_ratio_per_ingredient: 0.50
              maximum_unknown_ratio: 0.20
              maximum_total_error: 0.30
              ambiguity_margin: 0.10
            equipment:
              pan_max_scale: 5
              cauldron_max_scale: 3
            state:
              flush_interval_ticks: 100
            """);

        UnifiedCookingSettings settings = UnifiedCookingConfigurationLoader.loadSettings(current.toFile());

        assertEquals(5, settings.getPanMaxScale());
        assertEquals(0.10, settings.getMatching().getAmbiguityMargin());

        Path legacy = temp.resolve("legacy.yml");
        Files.writeString(legacy, """
            config_version: 1
            enabled: true
            settings:
              minimum_similarity: 0.5
            """);
        assertThrows(IllegalArgumentException.class,
            () -> UnifiedCookingConfigurationLoader.loadSettings(legacy.toFile()));
    }

    @Test
    void rejectsMultipleIngredientMatchers() throws Exception {
        Path file = temp.resolve("ingredients.yml");
        Files.writeString(file, """
            config_version: 2
            ingredients:
              cod:
                matcher:
                  fish_id: cod
                  material: COD
                display_name_key: fishing.catalog.item.cod
                container_remainder: null
            """);

        assertThrows(IllegalArgumentException.class,
            () -> UnifiedCookingConfigurationLoader.loadIngredients(file.toFile()));
    }

    @Test
    void rejectsNormalizedRecipeConflicts() {
        CookingRecipeDefinition first = recipe("first", Map.of("a", 2, "b", 4), 2);
        CookingRecipeDefinition second = recipe("second", Map.of("a", 1, "b", 2), 1);

        assertThrows(IllegalArgumentException.class,
            () -> UnifiedCookingConfigurationLoader.validateRecipeConflicts(List.of(first, second)));
    }

    private static CookingRecipeDefinition recipe(String id, Map<String, Integer> ingredients, int water) {
        return new CookingRecipeDefinition(
            id,
            CookingStation.CAULDRON,
            "BASIC",
            CookingTier.BASIC,
            CookingHeat.NORMAL,
            ingredients,
            water,
            8,
            12,
            CookingResultKind.BOWL
        );
    }
}
