package jp.awabi2048.cccontent.features.resourcecollection;

import com.awabi2048.ccsystem.api.time.Season;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GatheringPatchModelTest {
    private static final UUID WORLD = UUID.fromString("12345678-1234-5678-9abc-def012345678");

    @Test
    void mapsOnlySpecifiedVegetationMaterials() {
        assertEquals(GatheringVegetationGroup.GRASS, GatheringVegetationGroup.Companion.from(Material.TALL_GRASS));
        assertEquals(GatheringVegetationGroup.FERN, GatheringVegetationGroup.Companion.from(Material.FERN));
        assertEquals(GatheringVegetationGroup.AQUATIC_GRASS, GatheringVegetationGroup.Companion.from(Material.SEAGRASS));
        assertEquals(GatheringVegetationGroup.FLOWER, GatheringVegetationGroup.Companion.from(Material.POPPY));
        assertNull(GatheringVegetationGroup.Companion.from(Material.VINE));
        assertNull(GatheringVegetationGroup.Companion.from(Material.KELP));
        assertNull(GatheringVegetationGroup.Companion.from(Material.BROWN_MUSHROOM));
    }

    @Test
    void separatesCellsGroupsAndDisconnectedComponents() {
        List<GatheringPatchCandidate> candidates = List.of(
            candidate(0, 0, 0, GatheringVegetationGroup.GRASS),
            candidate(1, 1, 0, GatheringVegetationGroup.GRASS),
            candidate(3, 0, 3, GatheringVegetationGroup.GRASS),
            candidate(4, 0, 3, GatheringVegetationGroup.GRASS),
            candidate(0, 0, 1, GatheringVegetationGroup.FERN)
        );

        List<GatheringPatch> patches = GatheringPatchModel.build(WORLD, candidates);

        assertEquals(4, patches.size());
        GatheringPatch grass = patches.stream()
            .filter(it -> it.getGroup() == GatheringVegetationGroup.GRASS && it.getCellX() == 0 && it.getPlants().size() == 2)
            .findFirst().orElseThrow();
        assertEquals(new GatheringPlantPosition(0, 0, 0), grass.getAnchor());
        assertEquals(WORLD + ":0:0:0:GRASS:0:0:0", grass.getId());
    }

    @Test
    void usesFloorDivisionForNegativeCoordinates() {
        GatheringPatch patch = GatheringPatchModel.build(
            WORLD,
            List.of(candidate(-1, -1, -1, GatheringVegetationGroup.GRASS))
        ).getFirst();

        assertEquals(-1, patch.getCellX());
        assertEquals(-1, patch.getCellY());
        assertEquals(-1, patch.getCellZ());
    }

    @Test
    void capsConnectedComponentAtFirstSixteenBreadthFirstPlants() {
        List<GatheringPatchCandidate> candidates = new ArrayList<>();
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    candidates.add(candidate(x, y, z, GatheringVegetationGroup.GRASS));
                }
            }
        }

        List<GatheringPatch> patches = GatheringPatchModel.build(WORLD, candidates);

        assertEquals(1, patches.size());
        assertEquals(16, patches.getFirst().getPlants().size());
        assertEquals(new GatheringPlantPosition(0, 0, 0), patches.getFirst().getAnchor());
    }

    @Test
    void stableDrawDoesNotDependOnCallerOrInvocationCount() {
        GatheringPlantPosition anchor = new GatheringPlantPosition(7, 64, -3);
        long seed = GatheringPatchModel.stableSeed(987654321L, WORLD, anchor, Season.AUTUMN);

        Integer first = GatheringPatchModel.weightedIndex(List.of(2, 5, 9), seed);
        Integer second = GatheringPatchModel.weightedIndex(List.of(2, 5, 9), seed);

        assertNotNull(first);
        assertEquals(first, second);
        assertNull(GatheringPatchModel.weightedIndex(List.of(0, 0), seed));
    }

    private static GatheringPatchCandidate candidate(int x, int y, int z, GatheringVegetationGroup group) {
        return new GatheringPatchCandidate(new GatheringPlantPosition(x, y, z), group);
    }
}
