package jp.awabi2048.cccontent.features.arena;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 原子保存へ移行したArena系ストアの回帰テスト。
 * 保存形式・内容は従来通りであること、一時ファイルが残らないことを検証する。
 */
class ArenaAtomicPersistenceTest {
    @Test
    void historyStoreRoundTripKeepsRecords() throws Exception {
        Path dir = Files.createTempDirectory("arena-history");
        var file = dir.resolve("history.yml").toFile();
        var player = UUID.randomUUID();
        var store = new ArenaHistoryStore(file);

        store.add(new ArenaHistoryRecord(player, LocalDate.of(2026, 9, 7), 3, 120L));

        var reloaded = new ArenaHistoryStore(file);
        reloaded.load();
        assertEquals(1, reloaded.all().size());
        assertEquals(player, reloaded.all().get(0).getPlayerId());
        assertEquals(3, reloaded.all().get(0).getDifficultyStar());
        assertNoTemporaryFiles(dir);
    }

    @Test
    void historyStoreOverwriteKeepsValidContent() throws Exception {
        Path dir = Files.createTempDirectory("arena-history");
        var file = dir.resolve("history.yml").toFile();
        var store = new ArenaHistoryStore(file);

        store.add(new ArenaHistoryRecord(UUID.randomUUID(), LocalDate.of(2026, 9, 6), 1, 60L));
        store.add(new ArenaHistoryRecord(UUID.randomUUID(), LocalDate.of(2026, 9, 7), 5, 300L));

        var reloaded = new ArenaHistoryStore(file);
        reloaded.load();
        assertEquals(2, reloaded.all().size());
        assertNoTemporaryFiles(dir);
    }

    @Test
    void dailyEntryStoreRoundTripKeepsDates() throws Exception {
        Path dir = Files.createTempDirectory("arena-daily");
        var file = dir.resolve("daily_entries.yml").toFile();
        var player = UUID.randomUUID();
        var today = LocalDate.of(2026, 9, 7);
        var store = new ArenaDailyEntryStore(file);

        assertTrue(store.tryReserve(player, today));

        var reloaded = new ArenaDailyEntryStore(file);
        reloaded.load();
        assertEquals(today, reloaded.lastEntryDate(player));
        assertNoTemporaryFiles(dir);
    }

    private static void assertNoTemporaryFiles(Path dir) throws Exception {
        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().contains(".tmp")));
        }
    }
}
