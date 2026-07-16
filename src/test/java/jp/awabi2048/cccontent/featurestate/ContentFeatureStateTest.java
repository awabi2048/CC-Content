package jp.awabi2048.cccontent.featurestate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContentFeatureStateTest {
    @Test
    void listsKnownFeaturesInDefinitionOrderWithDisplayNamesAndDependencies() {
        var statuses = new ContentFeatureState().statuses();

        assertEquals(List.of("arena", "rank", "brewery", "cooking", "fishing", "resource_collection",
                "sukima_dungeon", "party", "minigame"), statuses.stream().map(s -> s.getFeature().getId()).toList());
        assertEquals("Resource Collection", statuses.get(5).getFeature().getDisplayName());
        assertEquals(List.of("rank"), statuses.get(4).getFeature().getDependencies());
    }

    @Test
    void rejectsUnknownIdsAndDoesNotUseAliases() {
        var state = new ContentFeatureState();

        var unknown = state.enable("resource-collection");
        var uppercase = state.enable("RANK");

        assertEquals(FeatureStateResultType.UNKNOWN_FEATURE, unknown.getResultType());
        assertNull(unknown.getFeatureId());
        assertEquals(FeatureStateResultType.CHANGED, uppercase.getResultType());
    }

    @Test
    void requiresDependenciesBeforeEnabling() {
        var state = new ContentFeatureState();

        var result = state.enable("fishing");

        assertEquals(FeatureStateResultType.MISSING_DEPENDENCY, result.getResultType());
        assertEquals(List.of("rank"), result.getRelatedFeatureIds());
        assertEquals(List.of(), state.enabledFeatureIds());
    }

    @Test
    void refusesDisablingAnEnabledDependencyAndReportsDependents() {
        var state = new ContentFeatureState(Set.of("rank", "fishing", "resource_collection"));

        var result = state.disable("rank");

        assertEquals(FeatureStateResultType.ENABLED_DEPENDENCY, result.getResultType());
        assertEquals(List.of("fishing", "resource_collection"), result.getRelatedFeatureIds());
        assertEquals(List.of("rank", "fishing", "resource_collection"), state.enabledFeatureIds());
    }

    @Test
    void reportsNoChangeForRepeatedTransitions() {
        var state = new ContentFeatureState();

        assertEquals(FeatureStateResultType.NO_CHANGE, state.disable("arena").getResultType());
        assertEquals(FeatureStateResultType.CHANGED, state.enable("arena").getResultType());
        assertEquals(FeatureStateResultType.NO_CHANGE, state.enable("arena").getResultType());
        assertEquals(FeatureStateResultType.CHANGED, state.disable("arena").getResultType());
        assertEquals(FeatureStateResultType.NO_CHANGE, state.disable("arena").getResultType());
    }
}
