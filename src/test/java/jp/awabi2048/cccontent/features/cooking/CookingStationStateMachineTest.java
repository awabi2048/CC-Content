package jp.awabi2048.cccontent.features.cooking;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CookingStationStateMachineTest {
    private static final CookingStoredInput INPUT =
        new CookingStoredInput("cut_potato", 2, "serialized", null, 0);

    @Test
    void processingTimeReductionIsFixedAtStart() {
        CookingRecipeDefinition recipe = recipe(CookingStation.PAN, CookingResultKind.ITEM, 0);
        CookingStationSession started = CookingStationStateMachine.start(
            recipe, snapshot(recipe), "player", 1, CookingHeat.HIGH, List.of(INPUT), 0.25
        );

        assertEquals(60, started.getTotalTicks());
        CookingStationSession progressed = ((CookingStationStep.Updated)
            CookingStationStateMachine.tick(started, CookingHeat.HIGH)).getSession();
        assertEquals(60, progressed.getTotalTicks());
        assertEquals(59, progressed.getRemainingTicks());
    }

    @Test
    void wrongHeatCommitsFailureAndNeverRecoversToNormal() {
        CookingRecipeDefinition recipe = recipe(CookingStation.PAN, CookingResultKind.ITEM, 0);
        CookingStationSession started = CookingStationStateMachine.start(
            recipe, snapshot(recipe), "player", 2, CookingHeat.NORMAL, List.of(INPUT), 0.0
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
            snapshot(recipe(CookingStation.PAN, CookingResultKind.ITEM, 0)),
            "player", 1, CookingHeat.HIGH, List.of(INPUT), 0.25
        );
        CookingStationSession paused = ((CookingStationStep.Updated)
            CookingStationStateMachine.tick(started, null)).getSession();
        assertEquals(CookingProcessState.PAUSED_NO_HEAT, paused.getState());
        assertEquals(started.getRemainingTicks(), paused.getRemainingTicks());
        assertEquals(60, started.getTotalTicks());
    }

    @Test
    void equipmentWithoutHeatProcessesWithoutAHeatPauseOrFailure() {
        CookingRecipeDefinition recipe = new CookingRecipeDefinition(
            "soy_sauce",
            CookingStation.FERMENTATION,
            "INTERMEDIATE_MATERIAL",
            CookingTier.BASIC,
            null,
            Map.of("soy_sauce_moromi", 1),
            0,
            1,
            20,
            CookingResultKind.ITEM
        );
        CookingStationSession started = CookingStationStateMachine.start(
            recipe,
            new CookingRecipeSnapshot(
                "cooking.soy_sauce", 1, "cooking.burnt_solid_food", recipe.getDurationSeconds(),
                null, 0, CookingResultKind.ITEM, null, null, recipe.getExperience(), null, null
            ),
            "player",
            1,
            null,
            List.of(INPUT),
            0.0
        );

        CookingStationSession progressing = started;
        CookingStationStep step;
        do {
            step = CookingStationStateMachine.tick(progressing, null);
            progressing = step instanceof CookingStationStep.Updated updated
                ? updated.getSession()
                : ((CookingStationStep.Completed) step).getSession();
        } while (step instanceof CookingStationStep.Updated);
        CookingStationSession finished = CookingStationStateMachine.finish(progressing, recipe);

        assertFalse(started.getFailureCommitted());
        assertEquals(CookingProcessState.READY_ITEM, finished.getState());
        assertEquals("cooking.soy_sauce", finished.getOutputStacks().getFirst().getCustomItemId());
    }

    @Test
    void failureAndNormalOutputsAreSeparated() {
        CookingRecipeDefinition recipe = recipe(CookingStation.CAULDRON, CookingResultKind.BOWL, 1);
        CookingStationSession normal = CookingStationStateMachine.start(
            recipe, snapshot(recipe), "player", 2, CookingHeat.HIGH, List.of(INPUT), 0.0
        );
        while (normal.getRemainingTicks() > 0) {
            CookingStationStep step = CookingStationStateMachine.tick(normal, CookingHeat.HIGH);
            normal = step instanceof CookingStationStep.Updated updated
                ? updated.getSession()
                : ((CookingStationStep.Completed) step).getSession();
        }
        CookingStationSession finished = CookingStationStateMachine.finish(
            normal, recipe
        );
        assertEquals(CookingProcessState.READY_LIQUID, finished.getState());
        assertEquals(2, finished.getReservoir().getMaximum());
        assertFalse(finished.getReservoir().getFailed());
        CookingStationSession oneCollected = CookingStationStateMachine.collectLiquid(finished, true);
        assertNotNull(oneCollected);
        assertEquals(1, oneCollected.getConsumedWaterUnits());
        assertEquals(1, oneCollected.getReservoir().getRemaining());
    }

    @Test
    void cancellationIsUnavailableForFailureProcessing() {
        CookingStationSession failure = CookingStationStateMachine.start(
            recipe(CookingStation.PAN, CookingResultKind.ITEM, 0),
            snapshot(recipe(CookingStation.PAN, CookingResultKind.ITEM, 0)),
            "player", 1, CookingHeat.NORMAL, List.of(INPUT), 0.0
        );
        assertNull(CookingStationStateMachine.cancel(failure));
    }

    @Test
    void liquidCollectionRequiresACollectableLiquidAndNoActiveProcessing() {
        assertTrue(CookingStationStateMachine.canCollectLiquid(null, true));
        assertFalse(CookingStationStateMachine.canCollectLiquid(null, false));

        CookingStationSession processing = CookingStationStateMachine.start(
            recipe(CookingStation.PAN, CookingResultKind.ITEM, 0),
            snapshot(recipe(CookingStation.PAN, CookingResultKind.ITEM, 0)),
            "player", 1, CookingHeat.HIGH, List.of(INPUT), 0.0
        );
        assertFalse(CookingStationStateMachine.canCollectLiquid(processing, true));
    }

    @Test
    void containerRemaindersRemainAfterLastLiquidServing() {
        CookingRecipeDefinition recipe = recipe(CookingStation.CAULDRON, CookingResultKind.BOTTLE, 1);
        CookingStoredInput honey = new CookingStoredInput("honey", 1, "serialized", "GLASS_BOTTLE", 1);
        CookingStationSession session = CookingStationStateMachine.start(
            recipe, snapshot(recipe), "player", 1, CookingHeat.HIGH, List.of(honey), 1.0
        );
        CookingStationStep.Completed completed = (CookingStationStep.Completed)
            CookingStationStateMachine.tick(session, CookingHeat.HIGH);
        CookingStationSession ready = CookingStationStateMachine.finish(completed.getSession(), recipe);
        CookingStationSession afterLiquid = CookingStationStateMachine.collectLiquid(ready, true);
        assertNotNull(afterLiquid);
        assertEquals(CookingProcessState.READY_ITEM, afterLiquid.getState());
        assertEquals(CookingOutputKind.MATERIAL, afterLiquid.getOutputStacks().getFirst().getKind());
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

    private static CookingRecipeSnapshot snapshot(CookingRecipeDefinition recipe) {
        return new CookingRecipeSnapshot(
            "cooking.soup", 1, "cooking.burnt_bowl_food", recipe.getDurationSeconds(),
            recipe.getHeat(), recipe.getWaterUnits(), recipe.getResultKind(),
            recipe.getResultKind() == CookingResultKind.ITEM ? null : "BOWL",
            recipe.getResultKind() == CookingResultKind.ITEM ? null : "YELLOW_STAINED_GLASS_PANE",
            recipe.getExperience(), null, null
        );
    }
}
