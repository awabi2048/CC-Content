package jp.awabi2048.cccontent;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;
import jp.awabi2048.cccontent.features.cooking.CookingFeatureKt;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CookingConfigurationTest {
    private static final Path ROOT = Path.of("src/main/resources/config/cooking");
    private static final Path LANG_ROOT = Path.of("../cc-system/src/main/resources/lang");

    @Test
    void cookingConfigDefinesFiveUnlockedSlotsAndMinimumSimilarity() {
        var config = YamlConfiguration.loadConfiguration(ROOT.resolve("config.yml").toFile());
        assertTrue(config.getBoolean("enabled"));
        assertEquals(0.5, config.getDouble("settings.minimum_similarity"));
        assertEquals(5, config.getInt("settings.ingredient_slots_by_level.5"));
    }

    @Test
    void cookingConfigUsesStrictLevelTableValues() {
        var section = YamlConfiguration.loadConfiguration(ROOT.resolve("config.yml").toFile())
            .getConfigurationSection("settings.ingredient_slots_by_level");
        assertNotNull(section);
        assertEquals(java.util.Set.of("1", "2", "3", "4", "5"), section.getKeys(false));
        for (var key : section.getKeys(false)) {
            assertTrue(key.matches("[1-9][0-9]*"));
            assertTrue(section.getInt(key) >= 1 && section.getInt(key) <= 5);
        }
    }

    @Test
    void cookingUiUsesFixedInventorySlots() throws Exception {
        var source = java.nio.file.Files.readString(Path.of("src/main/kotlin/jp/awabi2048/cccontent/features/cooking/CookingFeature.kt"));
        assertTrue(source.contains("const val START = 49"));
        assertTrue(source.contains("const val CANCEL = 45"));
        assertTrue(source.contains("const val INFO = 4"));
        assertTrue(source.contains("listOf(20, 21, 22, 23, 24)"));
        assertTrue(source.contains("listOf(30, 31, 32)"));
    }

    @Test
    void cookingStartOnlyAcceptsLeftAndRightClick() {
        assertTrue(CookingFeatureKt.cookingStartClickAllowed(ClickType.LEFT));
        assertTrue(CookingFeatureKt.cookingStartClickAllowed(ClickType.RIGHT));
        assertFalse(CookingFeatureKt.cookingStartClickAllowed(ClickType.MIDDLE));
        assertFalse(CookingFeatureKt.cookingStartClickAllowed(ClickType.DROP));
    }

    @Test
    void cookingConsumptionPreservesSurplusAmounts() {
        var remainder = CookingFeatureKt.cookingInputRemainders(
            java.util.Map.of("wheat", 5, "salt", 2),
            java.util.Map.of("wheat", 2, "salt", 1)
        );
        assertEquals(java.util.Map.of("wheat", 3, "salt", 1), remainder);
        assertNull(CookingFeatureKt.cookingInputRemainders(
            java.util.Map.of("wheat", 1), java.util.Map.of("wheat", 2)
        ));
    }

    @Test
    void cookingActionAndInfoKeysUseSharedLoreRules() {
        var ja = YamlConfiguration.loadConfiguration(LANG_ROOT.resolve("ja_jp/content/cooking.yml").toFile());
        var en = YamlConfiguration.loadConfiguration(LANG_ROOT.resolve("en_us/content/cooking.yml").toFile());
        assertEquals("調理を開始", ja.getString("cooking.ui.start_action"));
        assertEquals("Start cooking", en.getString("cooking.ui.start_action"));
        assertEquals("料理情報", ja.getString("cooking.ui.info"));
        assertEquals("Cooking Information", en.getString("cooking.ui.info"));
        assertFalse(ja.contains("cooking.ui.cancel"));
        assertFalse(en.contains("cooking.ui.cancel"));
    }

    @Test
    void cookingRuntimeIsOwnedByTheStationAndDestroyedWithoutDrops() throws Exception {
        var source = java.nio.file.Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/cooking/CookingFeature.kt"));
        assertTrue(source.contains("mutableMapOf<CookingStationKey, ActiveCooking>()"));
        assertTrue(source.contains("mutableMapOf<CookingStationKey, PendingItems>()"));
        assertTrue(source.contains("station.blockIfLoaded()"));
        assertTrue(source.contains("currentFirePower(block) != session.startedFirePower"));
        assertTrue(source.contains("mutableMapOf<CookingStationKey, PendingLiquid>()"));
        assertTrue(source.contains("invalidateStation(CookingStationKey.from(event.block))"));
        assertFalse(source.contains("dropItemNaturally"));
        assertFalse(source.contains("completionAt"));
    }

    @Test
    void cookingStartConsumesTheWholeAcceptedInputBatch() throws Exception {
        var source = java.nio.file.Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/cooking/CookingFeature.kt"));
        assertTrue(source.contains("CookingHolder.INPUT_SLOTS.forEach { inventory.setItem(it, null) }"));
        assertTrue(source.contains("val starterId: UUID"));
        assertTrue(source.contains("catalogStore.record(session.starterId"));
        assertTrue(source.contains("catalogStore.record(player.uniqueId"));
    }

    @Test
    void sampleRecipesUseFormalItemModels() {
        var recipes = YamlConfiguration.loadConfiguration(ROOT.resolve("recipe.yml").toFile());
        var section = recipes.getConfigurationSection("recipes");
        assertNotNull(section);
        assertEquals(4, section.getKeys(false).size());
        for (var id : section.getKeys(false)) {
            assertTrue(java.util.Set.of("PAN", "CAULDRON")
                .contains(recipes.getString("recipes." + id + ".equipment")));
            var model = recipes.getString("recipes." + id + ".result.item_model");
            assertNotNull(model);
            assertTrue(model.startsWith("kota_server:custom_item/cooking/"));
            assertTrue(recipes.getInt("recipes." + id + ".exp") > 0);
            var material = recipes.getString("recipes." + id + ".result.material");
            assertNotNull(material);
            assertTrue(java.util.Set.of(
                    "POISONOUS_POTATO", "BAKED_POTATO", "BREAD", "COOKED_COD", "COOKIE"
                ).contains(material),
                "non-vanilla-use custom result must use POISONOUS_POTATO: " + id);
        }
    }

    @Test
    void panAndCauldronUseSeparateEquipmentPaths() throws Exception {
        var config = YamlConfiguration.loadConfiguration(ROOT.resolve("config.yml").toFile());
        assertFalse(config.contains("settings.station"));

        var recipes = YamlConfiguration.loadConfiguration(ROOT.resolve("recipe.yml").toFile());
        assertEquals("CAULDRON", recipes.getString("recipes.rustic_stew.equipment"));
        assertEquals("PAN", recipes.getString("recipes.grilled_cod.equipment"));

        var source = java.nio.file.Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/cooking/CookingFeature.kt"));
        assertTrue(source.contains("block.type == Material.WATER_CAULDRON"));
        assertTrue(source.contains("block.getRelative(org.bukkit.block.BlockFace.DOWN)"));
        assertTrue(source.contains("Material.CAMPFIRE, Material.SOUL_CAMPFIRE"));
    }

    @Test
    void cookingResultsUseLocalizedStructuredNameAndLore() throws Exception {
        var source = java.nio.file.Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/cooking/CookingFeature.kt"));
        assertTrue(source.contains("GuiLoreSpec.Blocks"));
        assertTrue(source.contains("cooking.recipe_description.$recipeId"));
        assertFalse(source.contains("NamespacedKey(\"cccontent\", \"cooking_completion\")"));
        assertFalse(source.contains("cooking.item.data.completion"));

        var ja = YamlConfiguration.loadConfiguration(LANG_ROOT.resolve("ja_jp/content/cooking.yml").toFile());
        var en = YamlConfiguration.loadConfiguration(LANG_ROOT.resolve("en_us/content/cooking.yml").toFile());
        for (String id : java.util.List.of("rustic_stew", "sweet_bread", "grilled_cod", "carrot_cookie")) {
            assertTrue(ja.isString("cooking.recipe." + id));
            assertTrue(en.isString("cooking.recipe." + id));
            assertTrue(ja.isString("cooking.recipe_description." + id));
            assertTrue(en.isString("cooking.recipe_description." + id));
        }
    }
}
