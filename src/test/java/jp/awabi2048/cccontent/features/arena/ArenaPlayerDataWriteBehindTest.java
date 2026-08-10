package jp.awabi2048.cccontent.features.arena;

import jp.awabi2048.cccontent.features.arena.mission.ArenaPlayerDataWriteBehind;
import jp.awabi2048.cccontent.features.arena.mission.ArenaPlayerMissionSnapshot;
import jp.awabi2048.cccontent.features.arena.mission.ArenaPlayerSnapshotWriter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaPlayerDataWriteBehindTest {
    @Test
    void closeFlushesLatestSnapshotToAtomicYamlFile() throws Exception {
        var directory = Files.createTempDirectory("arena-player-close-flush").toFile();
        var playerId = UUID.randomUUID();
        var persistence = new ArenaPlayerDataWriteBehind(directory, 60_000, 10);

        persistence.submit(playerId, snapshot(42));

        assertTrue(persistence.closeAndFlush(2_000));
        var yaml = YamlConfiguration.loadConfiguration(directory.toPath().resolve(playerId + ".yml").toFile());
        assertEquals(42, yaml.getInt("arena.total_mob_kill_count"));
        assertFalse(directory.toPath().resolve(playerId + ".yml.tmp").toFile().exists());
    }

    @Test
    void keepsNewerRevisionDirtyWhileOlderRevisionIsSaving() throws Exception {
        var directory = Files.createTempDirectory("arena-player-write-behind").toFile();
        var playerId = UUID.randomUUID();
        var firstWriteStarted = new CountDownLatch(1);
        var releaseFirstWrite = new CountDownLatch(1);
        var writes = new CopyOnWriteArrayList<Integer>();
        ArenaPlayerSnapshotWriter writer = (file, snapshot) -> {
            writes.add(snapshot.getTotalMobKillCount());
            if (writes.size() == 1) {
                firstWriteStarted.countDown();
                try {
                    assertTrue(releaseFirstWrite.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("test writer was interrupted", error);
                }
            }
        };
        var persistence = new ArenaPlayerDataWriteBehind(directory, 0, 10, writer, (id, error) -> {});

        persistence.submit(playerId, snapshot(1));
        assertTrue(firstWriteStarted.await(2, TimeUnit.SECONDS));
        persistence.submit(playerId, snapshot(2));
        persistence.submit(playerId, snapshot(3));
        assertTrue(persistence.isDirty(playerId));
        releaseFirstWrite.countDown();

        assertTrue(persistence.flush(2_000));
        assertEquals(List.of(1, 3), writes);
        assertFalse(persistence.isDirty(playerId));
        assertTrue(persistence.closeAndFlush(2_000));
    }

    @Test
    void retriesFailedSnapshotWithoutLosingLatestState() throws Exception {
        var directory = Files.createTempDirectory("arena-player-retry").toFile();
        var playerId = UUID.randomUUID();
        var failFirst = new AtomicBoolean(true);
        var writes = new CopyOnWriteArrayList<Integer>();
        ArenaPlayerSnapshotWriter writer = (file, snapshot) -> {
            if (failFirst.getAndSet(false)) {
                throw new IllegalStateException("injected failure");
            }
            writes.add(snapshot.getTotalMobKillCount());
        };
        var persistence = new ArenaPlayerDataWriteBehind(directory, 0, 10, writer, (id, error) -> {});

        persistence.submit(playerId, snapshot(7));

        assertTrue(persistence.flush(2_000));
        assertEquals(List.of(7), writes);
        assertFalse(persistence.isDirty(playerId));
        assertTrue(persistence.closeAndFlush(2_000));
    }

    private ArenaPlayerMissionSnapshot snapshot(int killCount) {
        return new ArenaPlayerMissionSnapshot(
                0,
                killCount,
                0,
                0,
                0,
                false,
                false,
                "paper",
                List.of(),
                Map.of());
    }
}
