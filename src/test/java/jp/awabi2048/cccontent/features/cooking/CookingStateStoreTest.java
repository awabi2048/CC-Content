package jp.awabi2048.cccontent.features.cooking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CookingStateStoreTest {
    @TempDir Path temp;

    @Test
    void roundTripsCurrentStationSnapshot() throws Exception {
        Path file = temp.resolve("state.yml");
        CookingStateStore store = new CookingStateStore(file.toFile());
        CookingStationKey key = new CookingStationKey(UUID.randomUUID(), -12, 64, 33);
        CookingRecipeSnapshot snapshot = new CookingRecipeSnapshot(
            "cooking.potato_soup", 1, "cooking.burnt_bowl_food", 8,
            CookingHeat.NORMAL, 1, CookingResultKind.BOWL, "BOWL",
            "YELLOW_STAINED_GLASS_PANE", 12, null, null
        );
        CookingStationSession session = new CookingStationSession(
            "potato_soup", snapshot, UUID.randomUUID().toString(), 2, CookingHeat.NORMAL,
            false, List.of(new CookingStoredInput("cut_potato", 6, "serialized", "BUCKET", 1)),
            2, 160, 73, CookingProcessState.PAUSED_NO_HEAT, List.of(), null, 0
        );
        PersistedCookingStation expected = new PersistedCookingStation(
            CookingStation.CAULDRON, session, Map.of(20, "idle-item"), true, false, Set.of("collector")
        );

        store.save(Map.of(key, expected));
        Map<CookingStationKey, PersistedCookingStation> loaded = store.load();

        assertEquals(expected, loaded.get(key));
        assertTrue(Files.readString(file).contains("schema_version: 4"));
        assertTrue(Files.readString(file).contains("recipe_snapshot:"));
    }

    @Test
    void roundTripsIdleWorkspaceWithoutSyntheticSession() {
        Path file = temp.resolve("idle.yml");
        CookingStateStore store = new CookingStateStore(file.toFile());
        CookingStationKey key = new CookingStationKey(UUID.randomUUID(), 1, 2, 3);
        PersistedCookingStation expected = new PersistedCookingStation(
            CookingStation.CUTTING, null, Map.of(11, "input", 38, "knife"), false, false, Set.of()
        );
        store.save(Map.of(key, expected));
        assertEquals(expected, store.load().get(key));
    }

    @Test
    void replacesUnknownSchemaWithEmptyCurrentState() throws Exception {
        Path file = temp.resolve("state.yml");
        Files.writeString(file, "schema_version: 2\nstations:\n  legacy:\n    status: PROCESSING_NORMAL\n");

        assertTrue(new CookingStateStore(file.toFile()).load().isEmpty());
        String replaced = Files.readString(file);
        assertTrue(replaced.contains("schema_version: 4"));
        assertFalse(replaced.contains("legacy"));
    }

    @Test
    void migratesLegacyThreeLevelLiquidAmountsToCanonicalUnits() throws Exception {
        Path file = temp.resolve("legacy-volume.yml");
        Files.writeString(file, """
            schema_version: 3
            stations:
              station:
                station: 00000000-0000-0000-0000-000000000001;1;2;3
                equipment: CAULDRON
                status: IDLE
                liquid_contents:
                  sea_water: 3
                workspace_items: []
              station2:
                station: 00000000-0000-0000-0000-000000000002;1;2;3
                equipment: CAULDRON
                status: IDLE
                liquid_contents:
                  water: 2
                workspace_items: []
            """);

        Map<CookingStationKey, PersistedCookingStation> loaded = new CookingStateStore(file.toFile()).load();
        assertEquals(Map.of("sea_water", 5), loaded.values().stream()
            .filter(station -> station.getLiquidContents().containsKey("sea_water"))
            .findFirst().orElseThrow().getLiquidContents());
        assertEquals(Map.of("water", 3), loaded.values().stream()
            .filter(station -> station.getLiquidContents().containsKey("water"))
            .findFirst().orElseThrow().getLiquidContents());
        assertTrue(Files.readString(file).contains("schema_version: 4"));
    }
}
