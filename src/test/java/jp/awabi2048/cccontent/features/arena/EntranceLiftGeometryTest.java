package jp.awabi2048.cccontent.features.arena;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntranceLiftGeometryTest {
    @Test
    void verticalKeepsCanonicalFootprint() {
        assertEquals(5, EntranceLiftGeometry.INSTANCE.footprintSizeX(5, 3, false));
        assertEquals(3, EntranceLiftGeometry.INSTANCE.footprintSizeZ(5, 3, false));
        assertEquals(0, EntranceLiftGeometry.INSTANCE.rotationQuarter(false));
    }

    @Test
    void horizontalSwapsFootprintAndRotatesPaste() {
        assertEquals(3, EntranceLiftGeometry.INSTANCE.footprintSizeX(5, 3, true));
        assertEquals(5, EntranceLiftGeometry.INSTANCE.footprintSizeZ(5, 3, true));
        assertEquals(
            EntranceLiftGeometry.HORIZONTAL_ROTATION_QUARTER,
            EntranceLiftGeometry.INSTANCE.rotationQuarter(true)
        );
    }

    @Test
    void horizontalDetectedBySubTag() {
        assertTrue(EntranceLiftGeometry.INSTANCE.isHorizontal(
            Set.of("arena.marker.lift", "arena.marker.lift.horizontal")
        ));
        assertFalse(EntranceLiftGeometry.INSTANCE.isHorizontal(
            Set.of("arena.marker.lift", "arena.marker.lift.vertical")
        ));
        assertFalse(EntranceLiftGeometry.INSTANCE.isHorizontal(Set.of("arena.marker.lift")));
        assertFalse(EntranceLiftGeometry.INSTANCE.isHorizontal(Set.of()));
    }
}
