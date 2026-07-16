package jp.awabi2048.cccontent.features.minigame;

import jp.awabi2048.cccontent.features.minigame.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MiniGameHistoryStoreTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @TempDir Path tempDir;

    @Test
    void retainsLatestRecordsAndCalculatesBestSeparately() {
        MiniGameHistoryStore store = new MiniGameHistoryStore(new File(tempDir.toFile(), "records.yml"), 2);
        MiniGameId game = new MiniGameId(WORLD, "endergolf");
        store.append(result(game, 100L, PLAYER, 5, 500L), 1_000L);
        store.append(result(game, 200L, OTHER, 3, 900L), 2_000L);
        store.append(result(game, 300L, PLAYER, 4, 800L), 3_000L);

        assertEquals(2, store.history(game).size());
        assertEquals(3_000L, store.history(game).get(0).getRecordedAtMillis());
        assertEquals(4, store.personalBest(game, PLAYER).getEntry().getScore());
        assertEquals(OTHER, store.topRecords(game, 2).get(0).getEntry().getPlayerUuid());
    }

    private static MiniGameResult result(
            MiniGameId game, long startedAt, UUID player, int score, long elapsed) {
        return new MiniGameResult(
                game,
                MiniGameType.ENDERGOLF,
                startedAt,
                startedAt + elapsed,
                MiniGameEndReason.COMPLETED,
                List.of(new MiniGameResultEntry(player, true, elapsed, 1, score))
        );
    }
}
