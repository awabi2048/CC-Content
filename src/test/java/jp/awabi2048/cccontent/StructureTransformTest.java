package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.structure.CardinalDirection;
import jp.awabi2048.cccontent.structure.StructureBounds2D;
import jp.awabi2048.cccontent.structure.StructurePoint2D;
import jp.awabi2048.cccontent.structure.StructureSchemas;
import jp.awabi2048.cccontent.structure.StructureTransform;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
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
    void rawPointTransformMatchesWorldEditPasteTransform() {
        for (int quarter = 0; quarter < 4; quarter++) {
            for (boolean mirrored : new boolean[] { false, true }) {
                StructureTransform transform = new StructureTransform(quarter, mirrored);
                AffineTransform worldEditTransform = new AffineTransform();
                if (mirrored) {
                    worldEditTransform = worldEditTransform.scale(-1.0, 1.0, 1.0);
                }
                if (quarter != 0) {
                    worldEditTransform = worldEditTransform.rotateY(quarter * 90.0);
                }

                for (double x : new double[] { 0.0, 1.0, 12.5 }) {
                    for (double z : new double[] { 0.0, 2.0, 15.5 }) {
                        StructurePoint2D actual = transform.applyRawPoint(x, z);
                        Vector3 expected = worldEditTransform.apply(Vector3.at(x, 0.0, z));
                        assertEquals(expected.x(), actual.getX(), 1.0e-9);
                        assertEquals(expected.z(), actual.getZ(), 1.0e-9);
                    }
                }
            }
        }
    }

    @Test
    void mirroredMarkerEntityPointKeepsBlockCenterAlignment() {
        StructureTransform transform = new StructureTransform(0, true);

        StructurePoint2D westMarker = transform.applyLocalMarkerEntityPoint(0.5, 2.5, 5, 5);
        StructurePoint2D eastMarker = transform.applyLocalMarkerEntityPoint(4.5, 2.5, 5, 5);

        assertEquals(4.5, westMarker.getX(), 1.0e-9);
        assertEquals(2.5, westMarker.getZ(), 1.0e-9);
        assertEquals(0.5, eastMarker.getX(), 1.0e-9);
        assertEquals(2.5, eastMarker.getZ(), 1.0e-9);
    }

    @Test
    void canonicalSchemasUseTheSameNorthFacingContract() {
        assertEquals(CardinalDirection.NORTH, StructureSchemas.INSTANCE.arena("goal").getInSide());
        assertEquals(CardinalDirection.NORTH, StructureSchemas.INSTANCE.arena("pedestal_room").getInSide());
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
