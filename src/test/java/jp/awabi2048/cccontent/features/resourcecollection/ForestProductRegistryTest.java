package jp.awabi2048.cccontent.features.resourcecollection;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ForestProductRegistryTest {
    private final ForestProductDefinition definition = new ForestProductDefinition(
        "approved_resin",
        "resource.approved_resin",
        "custom_items.resource.approved_resin.name",
        Set.of(Material.OAK_LOG),
        Set.of(Material.OAK_LEAVES),
        Set.of("minecraft:forest"),
        1.0,
        1
    );

    @Test
    void disabledOrMismatchedRegistryDoesNotDiscoverAnything() {
        assertNull(ForestProductRegistry.Companion.of(false, List.of(definition)).select(
            Material.OAK_LOG, Material.OAK_LEAVES, "minecraft:forest", 0.0, new Random(1)
        ));
        assertNull(ForestProductRegistry.Companion.of(true, List.of(definition)).select(
            Material.BIRCH_LOG, Material.OAK_LEAVES, "minecraft:forest", 0.0, new Random(1)
        ));
    }

    @Test
    void matchingApprovedDefinitionCanBeSelected() {
        assertEquals(definition, ForestProductRegistry.Companion.of(true, List.of(definition)).select(
            Material.OAK_LOG, Material.OAK_LEAVES, "minecraft:forest", 0.0, new Random(1)
        ));
    }
}
