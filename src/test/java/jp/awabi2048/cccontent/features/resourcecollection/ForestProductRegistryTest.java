package jp.awabi2048.cccontent.features.resourcecollection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForestProductRegistryTest {
    @Test
    void resolvesTheEightApprovedProductsFromSpeciesAndRootBiome() {
        assertEquals(ForestProductType.TREE_RESIN,
            ForestProductRegistry.Companion.resolveBaseProduct(TreeSpecies.SPRUCE, "minecraft:old_growth_pine_taiga"));
        assertEquals(ForestProductType.PINE_CONE,
            ForestProductRegistry.Companion.resolveBaseProduct(TreeSpecies.SPRUCE, "minecraft:taiga"));
        assertEquals(ForestProductType.TINDER_FUNGUS,
            ForestProductRegistry.Companion.resolveBaseProduct(TreeSpecies.BIRCH, "minecraft:old_growth_birch_forest"));
        assertEquals(ForestProductType.BIRCH_OUTER_BARK,
            ForestProductRegistry.Companion.resolveBaseProduct(TreeSpecies.BIRCH, "minecraft:forest"));
        assertEquals(ForestProductType.TINDER_FUNGUS,
            ForestProductRegistry.Companion.resolveBaseProduct(TreeSpecies.OAK, "minecraft:swamp"));
        assertEquals(ForestProductType.TANNIN_BARK,
            ForestProductRegistry.Companion.resolveBaseProduct(TreeSpecies.DARK_OAK, "minecraft:dark_forest"));
        assertEquals(ForestProductType.AROMATIC_WOOD_CHIP,
            ForestProductRegistry.Companion.resolveBaseProduct(TreeSpecies.JUNGLE, "minecraft:jungle"));
        assertEquals(ForestProductType.ACACIA_GUM,
            ForestProductRegistry.Companion.resolveBaseProduct(TreeSpecies.ACACIA, "minecraft:savanna"));
        assertEquals(ForestProductType.TANNIN_BARK,
            ForestProductRegistry.Companion.resolveBaseProduct(TreeSpecies.MANGROVE, "minecraft:mangrove_swamp"));
        assertEquals(ForestProductType.AROMATIC_WOOD_CHIP,
            ForestProductRegistry.Companion.resolveBaseProduct(TreeSpecies.CHERRY, "minecraft:cherry_grove"));
        assertEquals(ForestProductType.TINDER_FUNGUS,
            ForestProductRegistry.Companion.resolveBaseProduct(TreeSpecies.PALE_OAK, "minecraft:pale_garden"));
        assertNull(ForestProductRegistry.Companion.resolveBaseProduct(
            TreeSpecies.PALE_OAK, "minecraft:forest"
        ));
    }

    @Test
    void discoveryIsStableAndTheSkillOnlyWidensTheFixedThreshold() {
        var registry = ForestProductRegistry.Companion.of(
            new ForestProductSettings(true, 0.35, 0.10, 0.0)
        );
        var first = registry.resolve(TreeSpecies.OAK, "minecraft:forest", 1234L, 10, 64, -2, 0.10);
        var repeated = registry.resolve(TreeSpecies.OAK, "minecraft:forest", 1234L, 10, 64, -2, 0.10);

        assertEquals(first, repeated);

        var guaranteed = ForestProductRegistry.Companion.of(
            new ForestProductSettings(true, 1.0, 0.0, 0.0)
        );
        assertEquals(ForestProductType.TANNIN_BARK, guaranteed.resolve(
            TreeSpecies.OAK, "minecraft:forest", 1234L, 10, 64, -2, 0.0
        ).getType());
        assertNull(ForestProductRegistry.Companion.of(
            new ForestProductSettings(false, 1.0, 0.0, 0.0)
        ).resolve(TreeSpecies.OAK, "minecraft:forest", 1234L, 10, 64, -2, 0.0));
    }

    @Test
    void burlOverrideIsRestrictedToApprovedSpecialForests() {
        var registry = ForestProductRegistry.Companion.of(
            new ForestProductSettings(true, 1.0, 0.0, 1.0)
        );

        assertEquals(ForestProductType.BURL_WOOD, registry.resolve(
            TreeSpecies.SPRUCE, "minecraft:old_growth_spruce_taiga", 1L, 0, 64, 0, 0.0
        ).getType());
        assertEquals(ForestProductType.PINE_CONE, registry.resolve(
            TreeSpecies.SPRUCE, "minecraft:taiga", 1L, 0, 64, 0, 0.0
        ).getType());
        assertTrue(ForestProductRegistry.Companion.isBurlEnvironment(
            TreeSpecies.ACACIA, "minecraft:eroded_savanna"
        ));
        assertTrue(!ForestProductRegistry.Companion.isBurlEnvironment(
            TreeSpecies.MANGROVE, "minecraft:mangrove_swamp"
        ));
    }
}
