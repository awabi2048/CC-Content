package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.structure.CardinalDirection;
import jp.awabi2048.cccontent.structure.CcStructureSize;
import jp.awabi2048.cccontent.structure.LoadedSchemEntity;
import jp.awabi2048.cccontent.structure.StructureMarkerValidation;
import jp.awabi2048.cccontent.structure.StructureMarkerValidator;
import jp.awabi2048.cccontent.structure.StructureSchemas;
import jp.awabi2048.cccontent.structure.StructureTransform;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureMarkerValidatorTest {
    private static LoadedSchemEntity inMarker(double x, double z) {
        return new LoadedSchemEntity(
            StructureSchemas.MARKER_ENTITY_TYPE,
            x,
            0,
            z,
            Set.of(StructureSchemas.ARENA_CONNECTION_IN_TAG)
        );
    }

    private static LoadedSchemEntity outMarker(double x, double z) {
        return new LoadedSchemEntity(
            StructureSchemas.MARKER_ENTITY_TYPE,
            x,
            0,
            z,
            Set.of(StructureSchemas.ARENA_CONNECTION_OUT_TAG)
        );
    }

    @Test
    void arenaEntranceAcceptsOutMarkerOnNorthBoundary() {
        StructureMarkerValidation result = StructureMarkerValidator.INSTANCE.validateArena(
            StructureSchemas.INSTANCE.arena("entrance"),
            List.of(outMarker(2, 0)),
            new CcStructureSize(5, 3, 5)
        );
        assertTrue(result.isValid());
    }

    @Test
    void arenaStraightAcceptsInAndOutMarkers() {
        StructureMarkerValidation result = StructureMarkerValidator.INSTANCE.validateArena(
            StructureSchemas.INSTANCE.arena("straight"),
            List.of(inMarker(2, 0), outMarker(2, 4)),
            new CcStructureSize(5, 3, 5)
        );
        assertTrue(result.isValid());
    }

    @Test
    void arenaRejectsWrongEntityType() {
        LoadedSchemEntity wrongType = new LoadedSchemEntity(
            "minecraft:armor_stand",
            2,
            0,
            0,
            Set.of(StructureSchemas.ARENA_CONNECTION_OUT_TAG)
        );
        StructureMarkerValidation wrongTypeResult = StructureMarkerValidator.INSTANCE.validateArena(
            StructureSchemas.INSTANCE.arena("entrance"),
            List.of(wrongType),
            new CcStructureSize(5, 3, 5)
        );
        assertFalse(wrongTypeResult.isValid());
    }

    @Test
    void arenaRejectsDuplicateSide() {
        StructureMarkerValidation duplicateResult = StructureMarkerValidator.INSTANCE.validateArena(
            StructureSchemas.INSTANCE.arena("entrance"),
            List.of(outMarker(1, 0), outMarker(3, 0)),
            new CcStructureSize(5, 3, 5)
        );
        assertFalse(duplicateResult.isValid());
    }

    @Test
    void arenaDetectsTransformFromInOutMarkers() {
        var sides = new jp.awabi2048.cccontent.structure.DirectionalConnectionSides(
            Set.of(CardinalDirection.NORTH),
            Set.of(CardinalDirection.SOUTH)
        );
        StructureTransform result = StructureMarkerValidator.INSTANCE.detectArenaTransform(
            StructureSchemas.INSTANCE.arena("straight"),
            sides,
            CardinalDirection.NORTH
        );
        assertNotNull(result);
        assertEquals(0, result.getNormalizedQuarter());
        assertFalse(result.getMirrorX());
    }

    @Test
    void arenaDetectsReversedTransformFromSwappedMarkers() {
        var sides = new jp.awabi2048.cccontent.structure.DirectionalConnectionSides(
            Set.of(CardinalDirection.SOUTH),
            Set.of(CardinalDirection.NORTH)
        );
        StructureTransform result = StructureMarkerValidator.INSTANCE.detectArenaTransform(
            StructureSchemas.INSTANCE.arena("straight"),
            sides,
            CardinalDirection.NORTH
        );
        assertNotNull(result);
        assertEquals(2, result.getNormalizedQuarter());
        assertFalse(result.getMirrorX());
    }

    @Test
    void arenaCornerDetectsTransformFromAllFourLeftTurns() {
        CardinalDirection[][] cases = {
            {CardinalDirection.NORTH, CardinalDirection.EAST},
            {CardinalDirection.EAST, CardinalDirection.SOUTH},
            {CardinalDirection.SOUTH, CardinalDirection.WEST},
            {CardinalDirection.WEST, CardinalDirection.NORTH}
        };
        int[] expectedQuarters = {0, 1, 2, 3};
        for (int i = 0; i < cases.length; i++) {
            var sides = new jp.awabi2048.cccontent.structure.DirectionalConnectionSides(
                Set.of(cases[i][0]),
                Set.of(cases[i][1])
            );
            StructureTransform result = StructureMarkerValidator.INSTANCE.detectArenaTransform(
                StructureSchemas.INSTANCE.arena("corner"),
                sides,
                CardinalDirection.NORTH
            );
            assertNotNull(result, "corner transform for in=" + cases[i][0] + " out=" + cases[i][1]);
            assertEquals(expectedQuarters[i], result.getNormalizedQuarter(),
                "corner quarter for in=" + cases[i][0] + " out=" + cases[i][1]);
            assertFalse(result.getMirrorX(),
                "corner should not be mirrored for in=" + cases[i][0] + " out=" + cases[i][1]);
        }
    }

    @Test
    void arenaCornerDetectsTransformForMirroredRightTurns() {
        CardinalDirection[][] cases = {
            {CardinalDirection.NORTH, CardinalDirection.WEST},
            {CardinalDirection.EAST, CardinalDirection.NORTH},
            {CardinalDirection.SOUTH, CardinalDirection.EAST},
            {CardinalDirection.WEST, CardinalDirection.SOUTH}
        };
        int[] expectedQuarters = {0, 1, 2, 3};
        for (int i = 0; i < cases.length; i++) {
            var sides = new jp.awabi2048.cccontent.structure.DirectionalConnectionSides(
                Set.of(cases[i][0]),
                Set.of(cases[i][1])
            );
            StructureTransform result = StructureMarkerValidator.INSTANCE.detectArenaTransform(
                StructureSchemas.INSTANCE.arena("corner"),
                sides,
                CardinalDirection.NORTH
            );
            assertNotNull(result, "mirrored corner for in=" + cases[i][0] + " out=" + cases[i][1]);
            assertEquals(expectedQuarters[i], result.getNormalizedQuarter(),
                "mirrored corner quarter for in=" + cases[i][0] + " out=" + cases[i][1]);
            assertTrue(result.getMirrorX(),
                "mirrored corner should have mirrorX for in=" + cases[i][0] + " out=" + cases[i][1]);
        }
    }

    @Test
    void arenaStraightDetectsTransformFromAllFourOppositeDirections() {
        CardinalDirection[][] cases = {
            {CardinalDirection.NORTH, CardinalDirection.SOUTH},
            {CardinalDirection.EAST, CardinalDirection.WEST},
            {CardinalDirection.SOUTH, CardinalDirection.NORTH},
            {CardinalDirection.WEST, CardinalDirection.EAST}
        };
        int[] expectedQuarters = {0, 1, 2, 3};
        for (int i = 0; i < cases.length; i++) {
            var sides = new jp.awabi2048.cccontent.structure.DirectionalConnectionSides(
                Set.of(cases[i][0]),
                Set.of(cases[i][1])
            );
            StructureTransform result = StructureMarkerValidator.INSTANCE.detectArenaTransform(
                StructureSchemas.INSTANCE.arena("straight"),
                sides,
                CardinalDirection.NORTH
            );
            assertNotNull(result, "straight transform for in=" + cases[i][0] + " out=" + cases[i][1]);
            assertEquals(expectedQuarters[i], result.getNormalizedQuarter(),
                "straight quarter for in=" + cases[i][0] + " out=" + cases[i][1]);
            assertFalse(result.getMirrorX(),
                "straight should not be mirrored for in=" + cases[i][0] + " out=" + cases[i][1]);
        }
    }

    @Test
    void sukimaRequiresMinecraftMarkerEntity() {
        LoadedSchemEntity wrongType = new LoadedSchemEntity(
            "minecraft:armor_stand",
            1,
            0,
            1,
            Set.of("sd.marker.spawn")
        );
        StructureMarkerValidation result = StructureMarkerValidator.INSTANCE.validateSukima(
            StructureSchemas.INSTANCE.sukima("entrance"),
            List.of(wrongType)
        );
        assertFalse(result.isValid());
    }
}
