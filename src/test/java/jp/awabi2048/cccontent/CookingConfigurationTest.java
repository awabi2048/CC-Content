package jp.awabi2048.cccontent;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CookingConfigurationTest {
    private static final Path ROOT = Path.of("src/main/resources/config/cooking");

    @Test
    void cookingConfigUsesUnifiedVersionThreeContract() {
        var config = YamlConfiguration.loadConfiguration(ROOT.resolve("config.yml").toFile());
        assertEquals(3, config.getInt("config_version"));
        assertTrue(config.getBoolean("enabled"));
        assertEquals(0.50, config.getDouble("matching.maximum_excess_ratio_per_ingredient"));
        assertEquals(0.20, config.getDouble("matching.maximum_unknown_ratio"));
        assertEquals(0.30, config.getDouble("matching.maximum_total_error"));
        assertEquals(0.10, config.getDouble("matching.ambiguity_margin"));
        assertFalse(config.contains("settings.minimum_similarity"));
        assertFalse(config.contains("settings.ingredient_slots_by_level"));
    }

    @Test
    void cookingOwnsAllFourUnifiedConfigurationFiles() {
        assertTrue(Files.isRegularFile(ROOT.resolve("config.yml")));
        assertTrue(Files.isRegularFile(ROOT.resolve("ingredients.yml")));
        assertTrue(Files.isRegularFile(ROOT.resolve("cutting.yml")));
        assertTrue(Files.isRegularFile(ROOT.resolve("recipe.yml")));
    }

    @Test
    void bundledRecipesUseOnlyInteractiveStationsAndFormalModels() {
        var recipes = YamlConfiguration.loadConfiguration(ROOT.resolve("recipe.yml").toFile());
        var section = recipes.getConfigurationSection("recipes");
        assertNotNull(section);
        assertEquals(53, section.getKeys(false).size());
        for (String id : section.getKeys(false)) {
            assertTrue(Set.of("PAN", "CAULDRON").contains(recipes.getString("recipes." + id + ".equipment")));
            assertTrue(recipes.getString("recipes." + id + ".result.item_model")
                .startsWith("kota_server:custom_item/cooking/"));
            assertFalse(recipes.contains("recipes." + id + ".completion"));
            assertFalse(recipes.contains("recipes." + id + ".quality"));
            assertFalse(recipes.contains("recipes." + id + ".score"));
        }
    }

}
