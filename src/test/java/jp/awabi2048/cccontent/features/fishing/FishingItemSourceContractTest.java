package jp.awabi2048.cccontent.features.fishing;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingItemSourceContractTest {
    private static final Path SOURCE = Path.of(
        "src/main/kotlin/jp/awabi2048/cccontent/features/fishing/FishingItems.kt"
    );

    @Test
    void baitUsesTheCommonCustomItemBaseWithoutDetailedLore() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        assertTrue(source.contains("override val itemModel = NamespacedKey.minecraft(definition.material.key.key)"));
        assertTrue(source.contains("val item = ItemStack(Material.POISONOUS_POTATO, amount)"));
        assertTrue(source.contains("CustomItemI18n.text("));
        assertFalse(source.contains("private fun baitLore"));
    }
}
