package jp.awabi2048.cccontent.features.resourcecollection;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreePostProcessingPolicyTest {
    @Test
    void mapsSupportedNaturalTrunksToTheirOwnReplantMaterial() {
        assertEquals(Material.OAK_SAPLING, ResourceMaterialPolicy.INSTANCE.treeReplantMaterial(Material.OAK_LOG));
        assertEquals(Material.MANGROVE_PROPAGULE, ResourceMaterialPolicy.INSTANCE.treeReplantMaterial(Material.MANGROVE_LOG));
        assertEquals(Material.CRIMSON_FUNGUS, ResourceMaterialPolicy.INSTANCE.treeReplantMaterial(Material.CRIMSON_STEM));
        assertNull(ResourceMaterialPolicy.INSTANCE.treeReplantMaterial(Material.STRIPPED_OAK_LOG));
    }

    @Test
    void leafAndPlantingPoliciesRejectUnrelatedBlocks() {
        assertTrue(ResourceMaterialPolicy.INSTANCE.isLeaf(Material.OAK_LEAVES));
        assertTrue(ResourceMaterialPolicy.INSTANCE.isLeaf(Material.NETHER_WART_BLOCK));
        assertFalse(ResourceMaterialPolicy.INSTANCE.isLeaf(Material.OAK_LOG));
        assertTrue(ResourceMaterialPolicy.INSTANCE.canPlantTreeOn(Material.PODZOL));
        assertFalse(ResourceMaterialPolicy.INSTANCE.canPlantTreeOn(Material.STONE));
    }
}
