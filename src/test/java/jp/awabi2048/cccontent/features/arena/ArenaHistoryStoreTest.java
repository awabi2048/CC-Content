package jp.awabi2048.cccontent.features.arena;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaHistoryStoreTest {
    @Test
    void storesEachDaySeparatelyAndLoadsOnlyRequestedWindow() throws Exception {
        var root = Files.createTempDirectory("arena-history-daily").toFile();
        var historyDirectory = root.toPath().resolve("history").toFile();
        var today = LocalDate.of(2026, 8, 10);
        var recentPlayer = UUID.randomUUID();
        var oldPlayer = UUID.randomUUID();
        var store = new ArenaHistoryStore(historyDirectory, null);

        store.addAll(List.of(
                new ArenaHistoryRecord(recentPlayer, today, 2, 100),
                new ArenaHistoryRecord(oldPlayer, today.minusDays(90), 4, 200)));

        assertTrue(historyDirectory.toPath().resolve("2026-08-10.yml").toFile().isFile());
        assertTrue(historyDirectory.toPath().resolve("2026-05-12.yml").toFile().isFile());

        var reloaded = new ArenaHistoryStore(historyDirectory, null);
        reloaded.load(today, 90);

        assertEquals(List.of(new ArenaHistoryRecord(recentPlayer, today, 2, 100)), reloaded.all());
    }

    @Test
    void appendsAWholeSessionWithOneDailyDocument() throws Exception {
        var root = Files.createTempDirectory("arena-history-session").toFile();
        var historyDirectory = root.toPath().resolve("history").toFile();
        var today = LocalDate.of(2026, 8, 10);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var store = new ArenaHistoryStore(historyDirectory, null);

        store.addAll(List.of(
                new ArenaHistoryRecord(first, today, 3, 120),
                new ArenaHistoryRecord(second, today, 3, 120)));

        var yaml = YamlConfiguration.loadConfiguration(historyDirectory.toPath().resolve("2026-08-10.yml").toFile());
        assertEquals("2026-08-10", yaml.getString("date"));
        assertEquals(2, yaml.getMapList("records").size());
    }

    @Test
    void migratesLegacyHistoryOnlyOnce() throws Exception {
        var root = Files.createTempDirectory("arena-history-legacy").toFile();
        var historyDirectory = root.toPath().resolve("history").toFile();
        var legacyFile = root.toPath().resolve("history.yml").toFile();
        var player = UUID.randomUUID();
        var yaml = new YamlConfiguration();
        yaml.set("records", List.of(java.util.Map.of(
                "player", player.toString(),
                "date", "2026-08-10",
                "difficulty_star", 2,
                "duration_seconds", 90)));
        yaml.save(legacyFile);

        var store = new ArenaHistoryStore(historyDirectory, legacyFile);
        store.load(LocalDate.of(2026, 8, 10), 90);

        assertEquals(List.of(new ArenaHistoryRecord(player, LocalDate.of(2026, 8, 10), 2, 90)), store.all());
        assertFalse(legacyFile.exists());
        assertTrue(root.toPath().resolve("history.yml.migrated.bak").toFile().isFile());

        var reloaded = new ArenaHistoryStore(historyDirectory, legacyFile);
        reloaded.load(LocalDate.of(2026, 8, 10), 90);
        assertEquals(1, reloaded.all().size());
    }
}
