package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.features.fishing.FishingSearchStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FishingSearchStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void searchTargetPersistsAndCanBeCleared() {
        UUID playerId = UUID.randomUUID();
        Path file = temporaryDirectory.resolve("search.yml");
        var store = new FishingSearchStore(file.toFile());
        store.set(playerId, "salmon");
        assertEquals("salmon", new FishingSearchStore(file.toFile()).get(playerId));

        store.set(playerId, null);
        assertNull(new FishingSearchStore(file.toFile()).get(playerId));
    }
}
