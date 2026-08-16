package jp.awabi2048.cccontent.items.arena;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaMobTokenLocalizationTest {
    @Test
    void everySupportedCategoryHasAStaticGeneratedKey() {
        Set<String> expected = Set.of(
            "skeleton", "zombie", "creeper", "piglin", "wither_skeleton", "ender_dragon",
            "husk", "iron_golem", "guardian", "elder_guardian", "drowned", "silverfish",
            "spider", "witch", "blaze", "magma_cube", "slime", "bogged", "stray", "bat",
            "enderman", "endermite", "shulker", "spirit", "frog", "boomerang"
        );

        assertEquals(expected, ArenaMobTokenLocalization.INSTANCE.supportedCategoryIds());
        expected.forEach(category -> {
            var key = ArenaMobTokenLocalization.INSTANCE.nameKey(category);
            assertEquals("custom_items.arena.mob_token.token_names." + category, key.getId());
        });
        assertEquals(
            "custom_items.arena.mob_token.lore",
            ArenaMobTokenLocalization.INSTANCE.getLoreKey().getId()
        );
    }

    @Test
    void aliasesResolveBeforeStaticLocalizationLookup() {
        assertEquals("spider", ArenaMobTokenItem.Companion.resolveTokenCategoryTypeId("cave_spider"));
        assertEquals("spirit", ArenaMobTokenItem.Companion.resolveTokenCategoryTypeId("ashen_spirit"));
        assertEquals("shulker", ArenaMobTokenItem.Companion.resolveTokenCategoryTypeId("shulker_mimic"));
        assertTrue(ArenaMobTokenItem.Companion.supportsTokenCategoryTypeId("water_spirit"));
    }
}
