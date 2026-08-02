package jp.awabi2048.cccontent.features.brewery;

import com.awabi2048.ccsystem.api.action.ContentActionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BreweryCompletionContractTest {
    @Test
    void intermediateStagePublishesOnlyStageCompletion() {
        assertEquals(
            List.of(ContentActionType.BREWING_STAGE_COMPLETED),
            BrewerySettingsKt.brewingCompletionActionTypes(false)
        );
    }

    @Test
    void finalStagePublishesStageAndBrewCompletion() {
        assertEquals(
            List.of(ContentActionType.BREWING_STAGE_COMPLETED, ContentActionType.BREWING_COMPLETED),
            BrewerySettingsKt.brewingCompletionActionTypes(true)
        );
    }

    @Test
    void onlyCurrentBrewerySchemaIsRetained() {
        assertFalse(BrewerySettingsKt.breweryStateRequiresReset(5));
        assertTrue(BrewerySettingsKt.breweryStateRequiresReset(4));
        assertTrue(BrewerySettingsKt.breweryStateRequiresReset(-1));
    }
}
