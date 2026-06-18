package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.structure.CardinalDirection;
import jp.awabi2048.cccontent.structure.StructureBounds2D;
import jp.awabi2048.cccontent.structure.StructurePoint2D;
import jp.awabi2048.cccontent.structure.StructureSchemas;
import jp.awabi2048.cccontent.structure.StructureTransform;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureTransformTest {
    @Test
    void worldEditPositiveQuarterRotatesEastToNorth() {
        StructureTransform transform = new StructureTransform(1, false);
        assertEquals(CardinalDirection.NORTH, transform.applyDirection(CardinalDirection.EAST));
        assertEquals(CardinalDirection.WEST, transform.applyDirection(CardinalDirection.NORTH));
    }

    @Test
    void rotationBetweenNormalizesEveryFacingToNorth() {
        for (CardinalDirection facing : CardinalDirection.getEntries()) {
            int quarter = StructureTransform.Companion.rotationBetween(facing, CardinalDirection.NORTH);
            assertEquals(
                CardinalDirection.NORTH,
                new StructureTransform(quarter, false).applyDirection(facing)
            );
        }
    }

    @Test
    void rectangularBoundsRemainPositiveAfterLocalTranslation() {
        StructureTransform transform = new StructureTransform(1, false);
        StructureBounds2D bounds = transform.bounds(2, 4);
        assertEquals(4, bounds.getWidth());
        assertEquals(2, bounds.getDepth());

        StructurePoint2D corner = transform.applyLocalPoint(1, 3, 2, 4);
        assertEquals(3.0, corner.getX());
        assertEquals(0.0, corner.getZ());
    }

    @Test
    void fourQuarterRotationsReturnEveryDirection() {
        for (CardinalDirection direction : CardinalDirection.getEntries()) {
            assertEquals(direction, new StructureTransform(4, false).applyDirection(direction));
        }
    }

    @Test
    void mirrorCompositionMatchesWorldEditScaleThenRotate() {
        StructureTransform transform = new StructureTransform(1, true);
        StructurePoint2D east = transform.applyRawPoint(1, 0);
        StructurePoint2D south = transform.applyRawPoint(0, 1);
        assertEquals(0.0, east.getX(), 1.0e-9);
        assertEquals(-1.0, east.getZ(), 1.0e-9);
        assertEquals(-1.0, south.getX(), 1.0e-9);
        assertEquals(0.0, south.getZ(), 1.0e-9);
    }

    @Test
    void canonicalSchemasUseTheSameNorthFacingContract() {
        assertEquals(CardinalDirection.NORTH, StructureSchemas.INSTANCE.arena("goal").getEntrySide());
        assertEquals(CardinalDirection.NORTH, StructureSchemas.INSTANCE.arena("pedestal_room").getEntrySide());
        assertNotNull(StructureTransform.Companion.rotationMatching(
            StructureSchemas.INSTANCE.sukima("corner").getCanonicalOpenings(),
            Set.of(CardinalDirection.SOUTH, CardinalDirection.WEST)
        ));
    }

    @Test
    void arenaOpenFramesDoNotRequireConnectionMarkers() {
        assertFalse(StructureSchemas.INSTANCE.arenaRequiresConnectionMarkers("corridor.open_1"));
        assertFalse(StructureSchemas.INSTANCE.arenaRequiresConnectionMarkers("corner.variant.open_12"));
        assertTrue(StructureSchemas.INSTANCE.arenaRequiresConnectionMarkers("corridor.closed"));
        assertTrue(StructureSchemas.INSTANCE.arenaRequiresConnectionMarkers("corner.variant.closed"));
        assertTrue(StructureSchemas.INSTANCE.arenaRequiresConnectionMarkers("corridor.open_0"));
    }
}
