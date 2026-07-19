package jp.awabi2048.cccontent;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreweryConfigurationTest {
    private static final Path CONFIG = Path.of("src/main/resources/config/brewery/config.yml");
    private static final Path RECIPES = Path.of("src/main/resources/config/brewery/recipe.yml");

    @Test
    void usesOnlyTheVersionedConfigurationShape() {
        var config = YamlConfiguration.loadConfiguration(CONFIG.toFile());
        var recipes = YamlConfiguration.loadConfiguration(RECIPES.toFile());

        assertEquals(2, config.getInt("config_version"));
        assertFalse(config.contains("schema_version"));
        assertTrue(config.isConfigurationSection("brewery"));
        assertFalse(config.contains("settings"));
        assertEquals(2, recipes.getInt("config_version"));
        assertFalse(recipes.contains("schema_version"));
        assertTrue(recipes.isConfigurationSection("recipes"));
        assertEquals(35, recipes.getConfigurationSection("recipes").getKeys(false).size());

        for (String key : recipes.getKeys(true)) {
            assertFalse(key.toLowerCase().endsWith("alchol"), "legacy alcohol typo remains: " + key);
        }
    }

    @Test
    void recipesPreserveZeroStepRequirements() {
        var recipes = YamlConfiguration.loadConfiguration(RECIPES.toFile());
        assertEquals(0, recipes.getInt("recipes.wheatbeer.distillation.runs"));
        assertEquals(0, recipes.getInt("recipes.vodka.aging.time_days"));
        assertTrue(recipes.getInt("recipes.whisky.distillation.runs") > 0);
    }

    @Test
    void sampleRecipesExposeTypedPotionEffects() {
        var recipes = YamlConfiguration.loadConfiguration(RECIPES.toFile());
        var effects = recipes.getStringList("recipes.moonshine.final_output.effects");

        assertFalse(effects.isEmpty());
        for (String effect : effects) {
            String[] parts = effect.split("/");
            assertEquals(3, parts.length);
            assertTrue(parts[0].matches("[A-Z_]+"));
            assertTrue(parts[1].matches("\\d+(?:-\\d+)?"));
            assertTrue(parts[2].matches("\\d+(?:-\\d+)?"));
        }
    }

    @Test
    void recipeConfigKeepsDisplayTextOutOfGameplayConfig() {
        var recipes = YamlConfiguration.loadConfiguration(RECIPES.toFile());
        for (String key : recipes.getKeys(true)) {
            assertFalse(key.endsWith(".name"));
            assertFalse(key.endsWith(".description"));
            assertFalse(key.equals("recipes.wheatbeer.name"));
        }
    }

    @Test
    void codecUsesStructuredLoreAndNoAutomaticLegacyLore() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/kotlin/jp/awabi2048/cccontent/features/brewery/item/BreweryItemCodec.kt"));
        assertFalse(source.contains("GuiLoreSpec.Auto"));
        assertFalse(source.contains("GuiLoreLine.Raw"));
        assertTrue(source.contains("GuiLoreSpec.Blocks"));
        assertTrue(source.contains("meta.displayName(Component.text("));
        assertFalse(source.contains("meta.setDisplayName("));
        assertTrue(source.contains("\"brewery.product.$recipeId\""));
    }

    @Test
    void qualityTierBoundariesRemainFixed() {
        assertEquals("low", jp.awabi2048.cccontent.features.brewery.BrewerySettingsKt.breweryQualityTier(33.99));
        assertEquals("standard", jp.awabi2048.cccontent.features.brewery.BrewerySettingsKt.breweryQualityTier(34.0));
        assertEquals("standard", jp.awabi2048.cccontent.features.brewery.BrewerySettingsKt.breweryQualityTier(66.99));
        assertEquals("high", jp.awabi2048.cccontent.features.brewery.BrewerySettingsKt.breweryQualityTier(67.0));
    }

    @Test
    void destroyedEquipmentDiscardsAllInternalContents() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/brewery/BreweryController.kt"));
        assertTrue(source.contains("private fun invalidateAt(key: BreweryLocationKey)"));
        assertTrue(source.contains("inventory.clear()"));
        assertFalse(source.contains("dropInventoryContents"));
        int start = source.indexOf("private fun invalidateAt(key: BreweryLocationKey)");
        int end = source.indexOf("private fun finalizeDistilledItem", start);
        assertTrue(start >= 0 && end > start);
        assertFalse(source.substring(start, end).contains("dropItemNaturally"));
    }
}
