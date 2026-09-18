package jp.awabi2048.cccontent.features.arena;

import org.junit.jupiter.api.Test;

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
        var model = new ArenaDemandModel(new ArenaDemandConfig(true, 30.0, 90, 1.0, 1.0, 0.25, 1.0));
        var history = List.of(
                new ArenaHistoryRecord(player, today.minusDays(89), 2, 3600),
                new ArenaHistoryRecord(player, today.minusDays(91), 4, 3600));

        assertEquals(2, model.selectDifficulty(List.of(2), history, today));
        assertEquals(1.0, model.decayFactor(0));
        assertEquals(0.5, model.decayFactor(30), 1.0e-12);
        assertEquals(0.0, model.decayFactor(90));
        assertEquals(0.0, model.decayFactor(-1));
        assertThrows(IllegalArgumentException.class, () ->
                new ArenaDemandConfig(true, 0.0, 90, 1.0, 1.0, 0.25, 1.0));
        assertThrows(IllegalArgumentException.class, () ->
                new ArenaDemandConfig(true, 30.0, 91, 1.0, 1.0, 0.25, 1.0));
        assertThrows(IllegalArgumentException.class, () ->
                new ArenaDemandConfig(true, 30.0, 90, -1.0, 1.0, 0.25, 1.0));
        assertThrows(IllegalArgumentException.class, () ->
                new ArenaDemandConfig(true, Double.POSITIVE_INFINITY, 90, 1.0, 1.0, 0.25, 1.0));
        assertThrows(IllegalArgumentException.class, () ->
                new ArenaDemandConfig(true, 30.0, 90, 0.0, 1.0, 0.0, 1.0));
    }

    @Test
    void estimatesActivePlayerCohortInsteadOfCandidatePopularity() {
        var today = LocalDate.of(2026, 7, 13);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var model = new ArenaDemandModel(new ArenaDemandConfig(true, 30.0, 90, 1.0, 1.0, 0.25, 1.0));
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
    void disabledDemandIgnoresHistoryAndWeight() {
        var today = LocalDate.of(2026, 7, 13);
        var player = UUID.randomUUID();
        var model = new ArenaDemandModel(new ArenaDemandConfig(false, 30.0, 90, 1.0, 1.0, 0.25, 1.0));
        var history = List.of(
                new ArenaHistoryRecord(player, today, 4, 7200),
                new ArenaHistoryRecord(player, today, 4, 7200));

        assertFalse(model.isEnabled());
        // 無効時は履歴があっても候補中心を目標とし、需要重みは一律1.0になる。
        assertEquals(2.0, model.estimateTargetDifficulty(List.of(1, 2, 3), history, today), 1.0e-12);
        assertEquals(1.0, model.selectionWeight(1, 3.0), 1.0e-12);
        assertEquals(1.0, model.selectionWeight(3, 1.0), 1.0e-12);
        // 単一候補は履歴に関わらず確定で選ばれる。
        assertEquals(2, model.selectDifficulty(List.of(2), history, today));
    }
}
