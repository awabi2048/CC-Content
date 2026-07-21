package jp.awabi2048.cccontent.features.fishing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CatchJournalStoreTest {
    @TempDir Path temp;

    @Test
    void keepsNewestSixtyThreeAndRoundTripsBestFields() {
        var file = temp.resolve("catch_journal.yml").toFile();
        var player = UUID.randomUUID();
        var store = new CatchJournalStore(file);
        UUID newest = null;
        for (int i = 0; i < 64; i++) {
            newest = UUID.randomUUID();
            store.add(new CatchJournalRecord(
                newest, "mackerel", 700 + i, 30 + i, FishQuality.RARE,
                1_000L + i, player, "angler", "minecraft:resource", "minecraft:ocean",
                "SUMMER", false
            ));
        }

        assertEquals(63, store.recent(player).size());
        assertEquals(newest, store.recent(player).getFirst().getRecordId());
        var loaded = new CatchJournalStore(file);
        assertEquals(63, loaded.recent(player).size());
        assertEquals(763, loaded.recent(player).getFirst().getWeightGrams());
    }

    @Test
    void gyotakuCanOnlyBeIssuedOnce() {
        var player = UUID.randomUUID();
        var recordId = UUID.randomUUID();
        var store = new CatchJournalStore(temp.resolve("journal.yml").toFile());
        store.add(new CatchJournalRecord(
            recordId, "cod", 100, 20, FishQuality.COMMON, 1L,
            player, "angler", "minecraft:resource", "minecraft:river", "SPRING", false
        ));

        assertTrue(store.markGyotakuIssued(player, recordId));
        assertFalse(store.markGyotakuIssued(player, recordId));
        assertTrue(new CatchJournalStore(temp.resolve("journal.yml").toFile())
            .find(player, recordId).getGyotakuIssued());
    }
}
