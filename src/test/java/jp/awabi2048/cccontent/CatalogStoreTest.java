package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.features.catalog.CatalogStore;
import jp.awabi2048.cccontent.features.catalog.CatalogType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogStoreTest {
    @Test
    void allThreeCatalogsShareOnePersistentFile() throws Exception {
        var file = Files.createTempFile("catalog", ".yml").toFile();
        var player = UUID.randomUUID();
        var store = new CatalogStore(file);

        store.record(player, CatalogType.FISHING, "cod", "rare", 160.0, null, 120, true, false);
        store.record(player, CatalogType.COOKING, "stew", null, null, 92, null, true, false);
        store.record(player, CatalogType.BREWERY, "ale", null, 88.0, null, null, false, true);

        var reloaded = new CatalogStore(file);
        assertEquals(1L, reloaded.entries(player, CatalogType.FISHING).get("cod").getObtainedCount());
        assertEquals(92, reloaded.entries(player, CatalogType.COOKING).get("stew").getBestCompletion());
        assertEquals(88.0, reloaded.entries(player, CatalogType.BREWERY).get("ale").getBestQuality(), 0.0001);
        assertTrue(reloaded.entries(player, CatalogType.FISHING).get("cod").getDiscovered());
        Files.deleteIfExists(file.toPath());
    }

    @Test
    void repeatedRecordsUpdateCountBestValuesAndQualityCounts() throws Exception {
        var file = Files.createTempFile("catalog", ".yml").toFile();
        var player = UUID.randomUUID();
        var store = new CatalogStore(file);

        store.record(player, CatalogType.FISHING, "cod", "common", 25.0, null, 120, true, false);
        store.record(player, CatalogType.FISHING, "cod", "rare", 75.0, null, 80, true, false);
        store.record(player, CatalogType.FISHING, "cod", "rare", 50.0, null, 150, true, false);

        var entry = store.entries(player, CatalogType.FISHING).get("cod");
        assertEquals(3L, entry.getObtainedCount());
        assertEquals(75.0, entry.getBestQuality());
        assertEquals(150, entry.getMaximumWeight());
        assertEquals(80, entry.getMinimumWeight());
        assertEquals(1L, entry.getQualityCounts().get("common"));
        assertEquals(2L, entry.getQualityCounts().get("rare"));

        var reloaded = new CatalogStore(file);
        var restored = reloaded.entries(player, CatalogType.FISHING).get("cod");
        assertEquals(3L, restored.getObtainedCount());
        assertEquals(75.0, restored.getBestQuality());
        assertEquals(2L, restored.getQualityCounts().get("rare"));
        assertEquals(0L, restored.getDrunkCount());
        Files.deleteIfExists(file.toPath());
    }

    @Test
    void breweryStoresObtainedAndDrunkCountsSeparately() throws Exception {
        var file = Files.createTempFile("catalog", ".yml").toFile();
        var player = UUID.randomUUID();
        var store = new CatalogStore(file);

        store.record(player, CatalogType.BREWERY, "ale", null, 80.0, null, null, true, false);
        store.record(player, CatalogType.BREWERY, "ale", null, 90.0, null, null, false, true);

        var entry = new CatalogStore(file).entries(player, CatalogType.BREWERY).get("ale");
        assertEquals(1L, entry.getObtainedCount());
        assertEquals(1L, entry.getDrunkCount());
        Files.deleteIfExists(file.toPath());
    }
}
