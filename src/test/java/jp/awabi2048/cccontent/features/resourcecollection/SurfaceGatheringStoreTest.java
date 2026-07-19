package jp.awabi2048.cccontent.features.resourcecollection;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SurfaceGatheringStoreTest {
    @Test
    void areaKeysRoundTripNegativeChunkCoordinates() {
        var key = new SurfaceGatheringStore.AreaKey(UUID.randomUUID(), -12, 34);

        assertEquals(key, SurfaceGatheringStore.AreaKey.Companion.decode(key.encoded()));
    }

    @Test
    void invalidAreaKeysAreIgnored() {
        assertNull(SurfaceGatheringStore.AreaKey.Companion.decode("invalid"));
        assertNull(SurfaceGatheringStore.AreaKey.Companion.decode(
            UUID.randomUUID() + "_x_1"
        ));
    }
}
