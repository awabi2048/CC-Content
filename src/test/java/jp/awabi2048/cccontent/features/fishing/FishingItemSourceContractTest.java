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

    @Test
    void rodDurabilityUsesOnlyTheStandardDamageableState() throws Exception {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        assertTrue(source.contains("item.itemMeta as Damageable"));
        assertTrue(source.contains("meta.setMaxDamage(definition.maxDurability)"));
        assertTrue(source.contains("meta.damage = (meta.damage + 1).coerceAtMost(rod.maxDurability)"));
        assertTrue(source.contains("return broken"));
        assertFalse(source.contains("player.damageItemStack(EquipmentSlot.HAND, 1)"));
        assertFalse(source.contains("rodDamage"));
        assertFalse(source.contains("fishing_rod_damage"));
    }

    @Test
    void brokenRodIsPreservedAndOnlyNotifiesWhenItBreaks() throws Exception {
        String items = Files.readString(SOURCE, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String feature = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/fishing/FishingFeature.kt"
        ), StandardCharsets.UTF_8).replace("\r\n", "\n");
        String startCast = feature.substring(
            feature.indexOf("private fun startCast"),
            feature.indexOf("private fun onBite")
        );
        String catchResolution = feature.substring(
            feature.indexOf("val preserveDurability"),
            feature.indexOf("private fun fail")
        );
        String damageRod = items.substring(
            items.indexOf("fun damageRod"),
            items.indexOf("fun resolveBait")
        );

        assertTrue(damageRod.contains("meta.damage = (meta.damage + 1).coerceAtMost(rod.maxDurability)"));
        assertFalse(damageRod.contains("Material.AIR"));
        assertTrue(startCast.contains("if (!items.isUsableRod(rodItem))"));
        assertFalse(startCast.contains("fishing.error.rod_broken"));
        assertTrue(catchResolution.contains(
            "if (!preserveDurability && items.damageRod(player)) {\n" +
                "            player.sendMessage(message(player, \"fishing.error.rod_broken\"))"
        ));
    }
}
