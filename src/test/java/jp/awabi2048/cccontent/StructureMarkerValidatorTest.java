package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.structure.CardinalDirection;
import jp.awabi2048.cccontent.structure.CcStructureSize;
import jp.awabi2048.cccontent.structure.LoadedSchemEntity;
import jp.awabi2048.cccontent.structure.StructureMarkerValidation;
import jp.awabi2048.cccontent.structure.StructureMarkerValidator;
import jp.awabi2048.cccontent.structure.StructureSchemas;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void arenaDetectsFacingFromInOutMarkers() {
        jp.awabi2048.cccontent.structure.DirectionalConnectionSides sides =
            new jp.awabi2048.cccontent.structure.DirectionalConnectionSides(
                Set.of(CardinalDirection.NORTH),
                Set.of(CardinalDirection.SOUTH)
            );
        CardinalDirection facing = StructureMarkerValidator.INSTANCE.detectArenaFacing(
            StructureSchemas.INSTANCE.arena("straight"),
            sides,
            CardinalDirection.NORTH
        );
        assertTrue(facing == CardinalDirection.NORTH);
    }

    @Test
    void arenaDetectsReversedFacingFromSwappedMarkers() {
        jp.awabi2048.cccontent.structure.DirectionalConnectionSides sides =
            new jp.awabi2048.cccontent.structure.DirectionalConnectionSides(
                Set.of(CardinalDirection.SOUTH),
                Set.of(CardinalDirection.NORTH)
            );
        CardinalDirection facing = StructureMarkerValidator.INSTANCE.detectArenaFacing(
            StructureSchemas.INSTANCE.arena("straight"),
            sides,
            CardinalDirection.NORTH
        );
        assertTrue(facing == CardinalDirection.SOUTH);
    }

    @Test
    void arenaCornerDetectsFacingFromAllFourAdjacentDirections() {
        CardinalDirection[][] cases = {
            {CardinalDirection.NORTH, CardinalDirection.EAST},
            {CardinalDirection.EAST, CardinalDirection.SOUTH},
            {CardinalDirection.SOUTH, CardinalDirection.WEST},
            {CardinalDirection.WEST, CardinalDirection.NORTH}
        };
        CardinalDirection[] expectedFacings = {
            CardinalDirection.NORTH,
            CardinalDirection.EAST,
            CardinalDirection.SOUTH,
            CardinalDirection.WEST
        };
        for (int i = 0; i < cases.length; i++) {
            jp.awabi2048.cccontent.structure.DirectionalConnectionSides sides =
                new jp.awabi2048.cccontent.structure.DirectionalConnectionSides(
                    Set.of(cases[i][0]),
                    Set.of(cases[i][1])
                );
            CardinalDirection facing = StructureMarkerValidator.INSTANCE.detectArenaFacing(
                StructureSchemas.INSTANCE.arena("corner"),
                sides,
                CardinalDirection.NORTH
            );
            assertTrue(facing == expectedFacings[i],
                "corner facing for in=" + cases[i][0] + " out=" + cases[i][1] +
                    " expected=" + expectedFacings[i] + " actual=" + facing);
        }
    }

    @Test
    void arenaStraightDetectsFacingFromAllFourOppositeDirections() {
        CardinalDirection[][] cases = {
            {CardinalDirection.NORTH, CardinalDirection.SOUTH},
            {CardinalDirection.EAST, CardinalDirection.WEST},
            {CardinalDirection.SOUTH, CardinalDirection.NORTH},
            {CardinalDirection.WEST, CardinalDirection.EAST}
        };
        CardinalDirection[] expectedFacings = {
            CardinalDirection.NORTH,
            CardinalDirection.EAST,
            CardinalDirection.SOUTH,
            CardinalDirection.WEST
        };
        for (int i = 0; i < cases.length; i++) {
            jp.awabi2048.cccontent.structure.DirectionalConnectionSides sides =
                new jp.awabi2048.cccontent.structure.DirectionalConnectionSides(
                    Set.of(cases[i][0]),
                    Set.of(cases[i][1])
                );
            CardinalDirection facing = StructureMarkerValidator.INSTANCE.detectArenaFacing(
                StructureSchemas.INSTANCE.arena("straight"),
                sides,
                CardinalDirection.NORTH
            );
            assertTrue(facing == expectedFacings[i],
                "straight facing for in=" + cases[i][0] + " out=" + cases[i][1] +
                    " expected=" + expectedFacings[i] + " actual=" + facing);
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
