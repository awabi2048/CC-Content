package jp.awabi2048.cccontent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CookingBreweryCorrectnessSourceContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void cookingKeepsResultsInEquipmentAndTracksCollectors() throws Exception {
        String cooking = source("src/main/kotlin/jp/awabi2048/cccontent/features/cooking/CookingFeature.kt");

        assertTrue(cooking.contains("PendingItems("));
        assertTrue(cooking.contains("PendingLiquid("));
        assertTrue(cooking.contains("publishCookingCollectionAction"));
        assertTrue(cooking.contains("(pending.keys + pendingLiquids.keys).distinct()"));
        assertTrue(cooking.contains("isRecipeUnlocked(match.recipe.group, typedProfile as CookSkillProfile)"));
    }

    @Test
    void breweryPersistsStageOwnershipAndRejectsUnsafeMachineAccess() throws Exception {
        String controller = source("src/main/kotlin/jp/awabi2048/cccontent/features/brewery/BreweryController.kt");
        String codec = source("src/main/kotlin/jp/awabi2048/cccontent/features/brewery/item/BreweryItemCodec.kt");

        assertTrue(controller.contains("InventoryMoveItemEvent"));
        assertTrue(controller.contains("recordBreweryCollection"));
        assertTrue(controller.contains("aging_not_ready"));
        assertTrue(controller.contains("schema_version\", 5"));
        assertTrue(controller.contains("blockIfLoaded()"));
        assertTrue(codec.contains("brewery_starter_id"));
        assertTrue(codec.contains("brewery_collectors"));
        assertTrue(codec.contains("CURRENT_SCHEMA_VERSION = 4"));
    }

    @Test
    void experimentalClockItemIsNotRegistered() throws Exception {
        String plugin = source("src/main/kotlin/jp/awabi2048/cccontent/CCContent.kt");
        String items = source("src/main/kotlin/jp/awabi2048/cccontent/items/brewery/BreweryItems.kt");

        assertFalse(plugin.contains("BreweryMockClockItem"));
        assertFalse(items.contains("BreweryMockClockItem"));
        assertFalse(items.contains("brewery.mock_clock"));
    }
}
