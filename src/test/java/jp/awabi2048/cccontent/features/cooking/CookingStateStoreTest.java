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
    void roundTripsVersionThreeStationSnapshot() throws Exception {
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
            CookingStation.CAULDRON, session, true, false, Set.of("collector")
        );

        store.save(Map.of(key, expected));
        Map<CookingStationKey, PersistedCookingStation> loaded = store.load();

        assertEquals(expected, loaded.get(key));
        assertTrue(Files.readString(file).contains("schema_version: 3"));
        assertTrue(Files.readString(file).contains("recipe_snapshot:"));
    }

    @Test
    void rejectsUnknownSchema() throws Exception {
        Path file = temp.resolve("state.yml");
        Files.writeString(file, "schema_version: 2\n");
        assertThrows(IllegalArgumentException.class, () -> new CookingStateStore(file.toFile()).load());
    }
}
