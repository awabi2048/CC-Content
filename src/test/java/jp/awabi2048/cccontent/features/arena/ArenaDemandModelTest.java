package jp.awabi2048.cccontent.features.arena;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaDemandModelTest {
    @Test
    void appliesNinetyDayCutoffAndAcceptsOnlyStrictConfig() {
        var today = LocalDate.of(2026, 7, 13);
        var player = UUID.randomUUID();
        var model = new ArenaDemandModel(new ArenaDemandConfig(30.0, 90, 1.0, 1.0, 0.25, 1.0));
        var history = List.of(
                new ArenaHistoryRecord(player, today.minusDays(89), 2, 3600),
                new ArenaHistoryRecord(player, today.minusDays(91), 4, 3600));

        assertEquals(2, model.selectDifficulty(List.of(2), history, today));
        assertEquals(1.0, model.decayFactor(0));
        assertEquals(0.5, model.decayFactor(30), 1.0e-12);
        assertEquals(0.0, model.decayFactor(90));
        assertEquals(0.0, model.decayFactor(-1));
        assertThrows(IllegalArgumentException.class, () ->
                new ArenaDemandConfig(0.0, 90, 1.0, 1.0, 0.25, 1.0));
        assertThrows(IllegalArgumentException.class, () ->
                new ArenaDemandConfig(30.0, 91, 1.0, 1.0, 0.25, 1.0));
        assertThrows(IllegalArgumentException.class, () ->
                new ArenaDemandConfig(30.0, 90, -1.0, 1.0, 0.25, 1.0));
        assertThrows(IllegalArgumentException.class, () ->
                new ArenaDemandConfig(Double.POSITIVE_INFINITY, 90, 1.0, 1.0, 0.25, 1.0));
        assertThrows(IllegalArgumentException.class, () ->
                new ArenaDemandConfig(30.0, 90, 0.0, 1.0, 0.0, 1.0));
    }

    @Test
    void estimatesActivePlayerCohortInsteadOfCandidatePopularity() {
        var today = LocalDate.of(2026, 7, 13);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var model = new ArenaDemandModel(new ArenaDemandConfig(30.0, 90, 1.0, 1.0, 0.25, 1.0));
        var history = List.of(
                new ArenaHistoryRecord(first, today, 1, 3600),
                new ArenaHistoryRecord(first, today, 1, 3600),
                new ArenaHistoryRecord(second, today, 3, 3600));

        assertEquals(1.8333333333, model.estimateTargetDifficulty(List.of(1, 2, 3), history, today), 1.0e-9);
        assertTrue(model.selectionWeight(2, 1.6666666667) > model.selectionWeight(3, 1.6666666667));
        assertTrue(model.selectionWeight(2, 1.6666666667) > model.selectionWeight(1, 1.6666666667));
        assertEquals(Math.exp(-Math.abs(3.0 - 1.6666666667)),
                model.selectionWeight(3, 1.6666666667), 1.0e-12);
    }

    @Test
    void dailyEntryReservationSurvivesReload() throws Exception {
        var file = Files.createTempFile("arena-daily", ".yml").toFile();
        var player = UUID.randomUUID();
        var today = LocalDate.of(2026, 7, 13);
        var store = new ArenaDailyEntryStore(file);

        assertTrue(store.tryReserve(player, today));
        assertFalse(store.tryReserve(player, today));

        var reloaded = new ArenaDailyEntryStore(file);
        reloaded.load();
        assertFalse(reloaded.tryReserve(player, today));
        assertTrue(reloaded.tryReserve(player, today.plusDays(1)));
    }

    @Test
    void reservesMultiplePlayersAtomicallyAtTheSameLocalDate() throws Exception {
        var file = Files.createTempFile("arena-daily-group", ".yml").toFile();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var today = LocalDate.of(2026, 7, 13);
        var store = new ArenaDailyEntryStore(file);

        assertTrue(store.tryReserve(first, today));
        assertFalse(store.tryReserveAll(List.of(first, second), today));
        assertEquals(null, store.lastEntryDate(second));
        assertTrue(store.tryReserveAll(List.of(first, second), today.plusDays(1)));
    }
}
