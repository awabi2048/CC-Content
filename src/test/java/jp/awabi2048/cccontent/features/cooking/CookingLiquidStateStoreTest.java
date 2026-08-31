package jp.awabi2048.cccontent.features.cooking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookingLiquidStateStoreTest {
    @TempDir Path temp;

    @Test
    void persistsIdleLogicalLiquidContents() throws Exception {
        Path file = temp.resolve("liquid-state.yml");
        var key = new CookingStationKey(UUID.randomUUID(), 4, 64, -9);
        var expected = new PersistedCookingStation(
            CookingStation.CAULDRON, null, Map.of(), false, false, java.util.Set.of(),
            Map.of("soy_milk", 1)
        );

        var store = new CookingStateStore(file.toFile());
        store.save(Map.of(key, expected));

        assertEquals(expected, store.load().get(key));
        assertTrue(Files.readString(file).contains("liquid_contents:"));
    }
}
