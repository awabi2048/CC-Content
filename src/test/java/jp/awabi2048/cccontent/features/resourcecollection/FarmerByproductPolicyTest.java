package jp.awabi2048.cccontent.features.resourcecollection;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FarmerByproductPolicyTest {
    @Test
    void matureCropByproductsFollowTheResourceCollectionSpecification() {
        assertEquals("straw", ResourceMaterialPolicy.INSTANCE.bonusItemId(
            ResourceCollectionKind.CROP, Material.WHEAT
        ));
        assertEquals("sprouted_potato", ResourceMaterialPolicy.INSTANCE.bonusItemId(
            ResourceCollectionKind.CROP, Material.POTATOES
        ));
        assertEquals("vegetable_leaves", ResourceMaterialPolicy.INSTANCE.bonusItemId(
            ResourceCollectionKind.CROP, Material.CARROTS
        ));
        assertEquals("vegetable_leaves", ResourceMaterialPolicy.INSTANCE.bonusItemId(
            ResourceCollectionKind.CROP, Material.BEETROOTS
        ));
        assertEquals("cocoa_pulp", ResourceMaterialPolicy.INSTANCE.bonusItemId(
            ResourceCollectionKind.CROP, Material.COCOA
        ));
        assertEquals("wart_fiber", ResourceMaterialPolicy.INSTANCE.bonusItemId(
            ResourceCollectionKind.CROP, Material.NETHER_WART
        ));
        assertNull(ResourceMaterialPolicy.INSTANCE.bonusItemId(
            ResourceCollectionKind.CROP, Material.MELON
        ));
    }
}
