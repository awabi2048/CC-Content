package jp.awabi2048.cccontent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CustomItemMaterialPolicySourceContractTest {
    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/kotlin").resolve(relativePath), StandardCharsets.UTF_8);
    }

    @Test
    void customOnlyItemsUsePoisonousPotatoAndItemModels() throws Exception {
        String resources = source(
            "jp/awabi2048/cccontent/features/resourcecollection/ResourceCollectionItems.kt"
        );
        String minigame = source(
            "jp/awabi2048/cccontent/features/minigame/core/MiniGameCustomItems.kt"
        );
        String prestige = source(
            "jp/awabi2048/cccontent/features/rank/prestige/PrestigeToken.kt"
        );
        String fishing = source(
            "jp/awabi2048/cccontent/features/fishing/FishingItems.kt"
        );
        String customHead = source(
            "jp/awabi2048/cccontent/items/misc/CustomHeadItem.kt"
        );

        assertTrue(resources.contains("ItemStack(Material.POISONOUS_POTATO"));
        assertTrue(resources.contains("meta.setItemModel(itemModel)"));
        assertTrue(resources.contains("resourceIdKey, PersistentDataType.STRING, definition.id"));
        assertFalse(resources.contains("base = Material."));

        assertTrue(minigame.contains("ItemStack(Material.POISONOUS_POTATO"));
        assertFalse(minigame.contains("ItemStack(Material.CLOCK"));
        assertFalse(minigame.contains("ItemStack(Material.AMETHYST_SHARD"));

        assertTrue(prestige.contains("ItemStack(Material.POISONOUS_POTATO)"));
        assertTrue(prestige.contains("setItemModel(NamespacedKey.minecraft(\"paper\"))"));
        assertFalse(prestige.contains("ItemStack(Material.PAPER)"));

        assertTrue(fishing.contains("meta.setItemModel(NamespacedKey.minecraft(\"book\"))"));
        assertFalse(fishing.contains("ItemStack(Material.BOOK)"));

        assertTrue(customHead.contains("ItemStack(Material.POISONOUS_POTATO"));
        assertTrue(customHead.contains("meta.setItemModel(NamespacedKey.minecraft(modelMaterial.key.key))"));
    }

    @Test
    void resourceActionsSwingAndNonGuiLoreUsesGrayText() throws Exception {
        String resources = source(
            "jp/awabi2048/cccontent/features/resourcecollection/ResourceCollectionItems.kt"
        );
        String service = source(
            "jp/awabi2048/cccontent/features/resourcecollection/SpecialistCollectionService.kt"
        );

        assertTrue(resources.contains("NamedTextColor.GRAY"));
        assertTrue(service.contains("player.swingMainHand()"));
        assertTrue(service.contains("processPlacedLog"));
        assertTrue(service.contains("tryHarvestForestProduct"));
    }

    @Test
    void playerFacingSukimaItemTextComesFromLanguageKeys() throws Exception {
        String items = source(
            "jp/awabi2048/cccontent/features/sukima_dungeon/CustomItemManager.kt"
        );

        assertTrue(items.contains("\"item_world_sprout_name\""));
        assertTrue(items.contains("\"item_world_sprout_lore\""));
        assertTrue(items.contains("\"item_compass_name\""));
        assertTrue(items.contains("\"item_compass_lore\""));
        assertFalse(items.contains("meta.setDisplayName(\"§dワールドの芽\")"));
        assertFalse(items.contains("meta.setDisplayName(\"§bワールドの芽コンパス"));
    }
}
