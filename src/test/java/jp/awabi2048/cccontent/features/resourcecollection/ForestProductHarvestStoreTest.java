package jp.awabi2048.cccontent.features.resourcecollection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ForestProductHarvestStoreTest {
    @Test
    void treeKeysRoundTripWithNegativeCoordinates() {
        var key = new ForestProductHarvestStore.TreeKey(
            "9fb7d4f0-d635-4c37-813b-286590c2276f",
            -123456789L,
            -20,
            72,
            15,
            TreeSpecies.CHERRY
        );

        assertEquals(key, ForestProductHarvestStore.TreeKey.Companion.decode(key.encoded()));
    }

    @Test
    void invalidTreeKeysAreIgnored() {
        assertNull(ForestProductHarvestStore.TreeKey.Companion.decode("invalid"));
        assertNull(ForestProductHarvestStore.TreeKey.Companion.decode(
            "world,seed,1,2,3,OAK"
        ));
    }
}
