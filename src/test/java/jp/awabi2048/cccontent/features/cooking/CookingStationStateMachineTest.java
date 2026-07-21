package jp.awabi2048.cccontent.features.cooking;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CookingStationStateMachineTest {
    private static final CookingStoredInput INPUT =
        new CookingStoredInput("cut_potato", 2, "serialized", null, 0);

    @Test
    void wrongHeatCommitsFailureAndNeverRecoversToNormal() {
        CookingRecipeDefinition recipe = recipe(CookingStation.PAN, CookingResultKind.ITEM, 0);
        CookingStationSession started = CookingStationStateMachine.start(
            recipe, "player", 2, CookingHeat.NORMAL, List.of(INPUT), 0.0
        );
        assertTrue(started.getFailureCommitted());
        assertEquals(CookingProcessState.PROCESSING_FAILURE, started.getState());

        CookingStationSession paused = ((CookingStationStep.Updated)
            CookingStationStateMachine.tick(started, CookingHeat.HIGH)).getSession();
        assertEquals(CookingProcessState.PAUSED_WRONG_HEAT, paused.getState());
        assertEquals(started.getRemainingTicks(), paused.getRemainingTicks());

        CookingStationSession resumed = ((CookingStationStep.Updated)
            CookingStationStateMachine.tick(paused, CookingHeat.NORMAL)).getSession();
        assertEquals(CookingProcessState.PROCESSING_FAILURE, resumed.getState());
    }

    @Test
    void extinguishedHeatPausesWithoutProgress() {
        CookingStationSession started = CookingStationStateMachine.start(
            recipe(CookingStation.PAN, CookingResultKind.ITEM, 0),
            "player", 1, CookingHeat.HIGH, List.of(INPUT), 0.25
        );
        CookingStationSession paused = ((CookingStationStep.Updated)
            CookingStationStateMachine.tick(started, null)).getSession();
        assertEquals(CookingProcessState.PAUSED_NO_HEAT, paused.getState());
        assertEquals(started.getRemainingTicks(), paused.getRemainingTicks());
        assertEquals(60, started.getTotalTicks());
    }

    @Test
    void failureAndNormalOutputsAreSeparated() {
        CookingRecipeDefinition recipe = recipe(CookingStation.CAULDRON, CookingResultKind.BOWL, 1);
        CookingStationSession normal = CookingStationStateMachine.start(
            recipe, "player", 2, CookingHeat.HIGH, List.of(INPUT), 0.0
        );
        while (normal.getRemainingTicks() > 0) {
            CookingStationStep step = CookingStationStateMachine.tick(normal, CookingHeat.HIGH);
            normal = step instanceof CookingStationStep.Updated updated
                ? updated.getSession()
                : ((CookingStationStep.Completed) step).getSession();
        }
        CookingStationSession finished = CookingStationStateMachine.finish(
            normal, recipe, "cooking.soup", "cooking.burnt_bowl_food", "BOWL"
        );
        assertEquals(CookingProcessState.READY_LIQUID, finished.getState());
        assertEquals(2, finished.getReservoir().getMaximum());
        assertFalse(finished.getReservoir().getFailed());
        CookingStationSession oneCollected = CookingStationStateMachine.collectLiquid(finished);
        assertNotNull(oneCollected);
        assertEquals(1, oneCollected.getConsumedWaterUnits());
        assertEquals(1, oneCollected.getReservoir().getRemaining());
    }

    @Test
    void cancellationIsUnavailableForFailureProcessing() {
        CookingStationSession failure = CookingStationStateMachine.start(
            recipe(CookingStation.PAN, CookingResultKind.ITEM, 0),
            "player", 1, CookingHeat.NORMAL, List.of(INPUT), 0.0
        );
        assertNull(CookingStationStateMachine.cancel(failure));
    }

    private static CookingRecipeDefinition recipe(
        CookingStation station,
        CookingResultKind kind,
        int water
    ) {
        return new CookingRecipeDefinition(
            "recipe", station, "BASIC", CookingTier.BASIC, CookingHeat.HIGH,
            Map.of("cut_potato", 2), water, 4, 8, kind
        );
    }
}
