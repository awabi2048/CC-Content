package jp.awabi2048.cccontent.features.minigame;

import jp.awabi2048.cccontent.features.minigame.core.MiniGameEndReason;
import jp.awabi2048.cccontent.features.minigame.core.MiniGameId;
import jp.awabi2048.cccontent.features.minigame.core.MiniGameMarker;
import jp.awabi2048.cccontent.features.minigame.core.MiniGameMarkerType;
import jp.awabi2048.cccontent.features.minigame.core.MiniGameResult;
import jp.awabi2048.cccontent.features.minigame.core.MiniGameSession;
import jp.awabi2048.cccontent.features.minigame.race.RaceCourse;
import jp.awabi2048.cccontent.features.minigame.race.RaceSession;
import jp.awabi2048.cccontent.features.minigame.race.RaceVisitStatus;
import jp.awabi2048.cccontent.features.minigame.hideandseek.HideAndSeekSession;
import jp.awabi2048.cccontent.features.minigame.chase.ChaseSession;
import jp.awabi2048.cccontent.features.minigame.colosseum.ColosseumRoundStatus;
import jp.awabi2048.cccontent.features.minigame.colosseum.ColosseumSession;
import jp.awabi2048.cccontent.features.minigame.endergolf.EnderGolfCourse;
import jp.awabi2048.cccontent.features.minigame.endergolf.EnderGolfLanding;
import jp.awabi2048.cccontent.features.minigame.endergolf.EnderGolfSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MiniGameModelTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void raceRequiresStartThenEveryCheckpointThenGoal() {
        MiniGameId game = new MiniGameId(WORLD, "race");
        UUID start = UUID.randomUUID();
        UUID checkpoint = UUID.randomUUID();
        UUID goal = UUID.randomUUID();
        RaceCourse course = RaceCourse.Companion.fromMarkers(List.of(
                new MiniGameMarker(start, game, OWNER, MiniGameMarkerType.START, null),
                new MiniGameMarker(checkpoint, game, OWNER, MiniGameMarkerType.CHECKPOINT, 1),
                new MiniGameMarker(goal, game, OWNER, MiniGameMarkerType.GOAL, null)
        ));
        RaceSession session = new RaceSession(game, List.of(PLAYER), course, 60_000L);
        session.start(1_000L);

        assertEquals(RaceVisitStatus.WRONG_ORDER, session.visit(PLAYER, goal, 2_000L).getStatus());
        assertEquals(RaceVisitStatus.STARTED, session.visit(PLAYER, start, 3_000L).getStatus());
        assertEquals(RaceVisitStatus.CHECKPOINT_REACHED, session.visit(PLAYER, checkpoint, 4_000L).getStatus());
        assertEquals(RaceVisitStatus.FINISHED, session.visit(PLAYER, goal, 8_500L).getStatus());
        assertTrue(session.allParticipantsFinished());

        MiniGameResult result = session.result(8_500L, MiniGameEndReason.COMPLETED);
        assertEquals(1, result.getEntries().get(0).getRank());
        assertEquals(7_500L, result.getEntries().get(0).getElapsedMillis());
    }

    @Test
    void commonSessionRanksCompletedParticipantsAndLeavesOthersUnranked() {
        MiniGameSession session = new MiniGameSession(
                new MiniGameId(WORLD, "shared"),
                jp.awabi2048.cccontent.features.minigame.core.MiniGameType.RACE,
                List.of(PLAYER, OTHER),
                10_000L,
                0L,
                new LinkedHashMap<>()
        );
        session.start(100L);
        session.recordCompletion(OTHER, 900L, null);
        session.recordCompletion(PLAYER, 400L, null);

        MiniGameResult result = session.result(1_500L, MiniGameEndReason.COMPLETED);
        assertEquals(PLAYER, result.getEntries().get(0).getPlayerUuid());
        assertEquals(1, result.getEntries().get(0).getRank());
        assertEquals(OTHER, result.getEntries().get(1).getPlayerUuid());
        assertEquals(2, result.getEntries().get(1).getRank());
    }

    @Test
    void timeoutMarksIncompleteParticipantsWithoutInventingTimes() {
        MiniGameSession session = new MiniGameSession(
                new MiniGameId(WORLD, "timeout"),
                jp.awabi2048.cccontent.features.minigame.core.MiniGameType.RACE,
                List.of(PLAYER),
                1_000L,
                0L,
                new LinkedHashMap<>()
        );
        session.start(5_000L);

        assertNull(session.tick(5_999L));
        MiniGameResult result = session.tick(6_000L);
        assertNotNull(result);
        assertEquals(MiniGameEndReason.TIME_LIMIT, result.getReason());
        assertFalse(result.getEntries().get(0).getCompleted());
        assertNull(result.getEntries().get(0).getElapsedMillis());
        assertNull(result.getEntries().get(0).getRank());
    }

    @Test
    void hideAndSeekEndsWhenEveryRunnerIsCaptured() {
        MiniGameId game = new MiniGameId(WORLD, "hideandseek");
        HideAndSeekSession session = new HideAndSeekSession(
                game, List.of(OWNER, PLAYER, OTHER), 10_000L, java.util.Set.of(OWNER), 0L);
        session.start(100L);

        assertEquals("HUNTER", session.getRoles().get(OWNER).name());
        assertEquals("CAPTURED", session.capture(OWNER, PLAYER, 1_000L).name());
        assertFalse(session.allRunnersCaptured());
        assertEquals("CAPTURED", session.capture(OWNER, OTHER, 2_000L).name());
        assertTrue(session.allRunnersCaptured());
        MiniGameResult result = session.result(2_000L, MiniGameEndReason.COMPLETED);
        assertEquals(MiniGameEndReason.COMPLETED, result.getReason());
        assertEquals(900L, result.getEntries().stream().filter(e -> e.getPlayerUuid().equals(PLAYER)).findFirst().orElseThrow().getElapsedMillis());
    }

    @Test
    void chaseCapturesOnceAndRecordsTimeoutForFreeRunners() {
        MiniGameId game = new MiniGameId(WORLD, "chase");
        ChaseSession session = new ChaseSession(game, List.of(OWNER, PLAYER, OTHER), 1_000L, java.util.Set.of(OWNER));
        session.start(100L);

        assertTrue(session.capture(OWNER, PLAYER, 500L));
        assertFalse(session.capture(OWNER, PLAYER, 600L));
        MiniGameResult result = session.tick(1_100L);
        assertNotNull(result);
        assertEquals(MiniGameEndReason.TIME_LIMIT, result.getReason());
        assertTrue(result.getEntries().stream().anyMatch(e -> e.getPlayerUuid().equals(PLAYER) && e.getCompleted()));
        assertTrue(result.getEntries().stream().anyMatch(e -> e.getPlayerUuid().equals(OTHER) && !e.getCompleted()));
    }

    @Test
    void colosseumAwardsRoundsAndEndsAtConfiguredFirstToScore() {
        MiniGameId game = new MiniGameId(WORLD, "colosseum");
        ColosseumSession session = new ColosseumSession(game, List.of(PLAYER, OTHER), 60_000L, 2);
        session.start(100L);

        assertEquals(ColosseumRoundStatus.ROUND_WON, session.recordDeath(OTHER, 1_000L));
        assertEquals(ColosseumRoundStatus.MATCH_WON, session.recordDeath(OTHER, 2_000L));
        assertEquals(2, session.getRoundWins().get(PLAYER));
        assertEquals(2, session.result(2_000L, MiniGameEndReason.COMPLETED).getEntries().get(0).getScore());
    }

    @Test
    void enderGolfRequiresEachHoleStartAndRanksByStrokes() {
        MiniGameId game = new MiniGameId(WORLD, "endergolf");
        UUID start = UUID.randomUUID();
        UUID cup = UUID.randomUUID();
        UUID water = UUID.randomUUID();
        EnderGolfCourse course = EnderGolfCourse.Companion.fromMarkers(List.of(
                new MiniGameMarker(start, game, OWNER, MiniGameMarkerType.START, 1),
                new MiniGameMarker(cup, game, OWNER, MiniGameMarkerType.CUP, 1),
                new MiniGameMarker(water, game, OWNER, MiniGameMarkerType.WATER_HAZARD, 1)
        ));
        EnderGolfSession session = new EnderGolfSession(game, List.of(PLAYER), course, 60_000L);
        session.start(100L);

        assertEquals(EnderGolfLanding.IGNORED, session.recordLanding(PLAYER, cup, MiniGameMarkerType.CUP, 200L));
        assertEquals(EnderGolfLanding.STARTED, session.beginHole(PLAYER, start));
        assertTrue(session.registerThrow(PLAYER));
        assertEquals(EnderGolfLanding.WATER_HAZARD, session.recordLanding(PLAYER, water, MiniGameMarkerType.WATER_HAZARD, 300L));
        assertTrue(session.registerThrow(PLAYER));
        assertEquals(EnderGolfLanding.CUP, session.recordLanding(PLAYER, cup, MiniGameMarkerType.CUP, 500L));
        assertEquals(3, session.result(500L, MiniGameEndReason.COMPLETED).getEntries().get(0).getScore());
    }

    @Test
    void individualGamesContinueUntilEveryParticipantFinishesOrWithdraws() {
        MiniGameId game = new MiniGameId(WORLD, "race-withdrawal");
        UUID start = UUID.randomUUID();
        UUID goal = UUID.randomUUID();
        RaceCourse course = RaceCourse.Companion.fromMarkers(List.of(
                new MiniGameMarker(start, game, OWNER, MiniGameMarkerType.START, null),
                new MiniGameMarker(goal, game, OWNER, MiniGameMarkerType.GOAL, null)
        ));
        RaceSession session = new RaceSession(game, List.of(PLAYER, OTHER), course, 60_000L);
        session.start(100L);
        session.visit(PLAYER, start, 200L);
        session.visit(PLAYER, goal, 300L);

        assertFalse(session.allParticipantsResolved());
        assertTrue(session.withdraw(OTHER));
        assertTrue(session.allParticipantsResolved());
        MiniGameResult result = session.result(300L, MiniGameEndReason.PLAYER_LEFT);
        assertFalse(result.getEntries().stream().filter(e -> e.getPlayerUuid().equals(OTHER)).findFirst().orElseThrow().getCompleted());
    }

    @Test
    void teamGameEndsOnlyWhenWithdrawalMakesOutcomeImpossible() {
        MiniGameId game = new MiniGameId(WORLD, "chase-withdrawal");
        UUID secondHunter = UUID.randomUUID();
        ChaseSession session = new ChaseSession(
                game,
                List.of(OWNER, secondHunter, PLAYER, OTHER),
                10_000L,
                java.util.Set.of(OWNER, secondHunter)
        );
        session.start(100L);

        assertFalse(session.withdraw(OWNER));
        assertTrue(session.withdraw(secondHunter));
    }

    @Test
    void hideAndSeekRejectsCapturesDuringPreparation() {
        MiniGameId game = new MiniGameId(WORLD, "hideandseek-preparation");
        HideAndSeekSession session = new HideAndSeekSession(
                game,
                List.of(OWNER, PLAYER),
                10_000L,
                java.util.Set.of(OWNER),
                60_000L
        );
        session.start(1_000L);

        assertEquals("IGNORED", session.capture(OWNER, PLAYER, 60_999L).name());
        assertEquals("CAPTURED", session.capture(OWNER, PLAYER, 61_000L).name());
    }
}
