package jp.awabi2048.cccontent.features.processing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingRecipeMatcherTest {
    private static final RecipeMatchPolicy POLICY = new RecipeMatchPolicy(3, 0.25, 1, 0.01);

    @Test
    void recognizesSafeIntegerBatchesAndConsumesTheAcceptedInput() {
        var recipe = recipe("stew", Map.of("potato", 2, "carrot", 1));
        var result = ProcessingRecipeMatcher.INSTANCE.match(
            List.of(recipe),
            "cauldron",
            ProcessingFirePower.NORMAL,
            Map.of("potato", 6, "carrot", 3),
            POLICY
        );

        var matched = assertInstanceOf(RecipeMatchResult.Matched.class, result);
        assertEquals(3, matched.getCandidate().getBatches());
        assertEquals(Map.of("potato", 6, "carrot", 3), matched.getCandidate().getConsumedAmounts());
    }

    @Test
    void rejectsAmountsBelowOneCompleteBatch() {
        var result = ProcessingRecipeMatcher.INSTANCE.match(
            List.of(recipe("stew", Map.of("potato", 2, "carrot", 1))),
            "cauldron",
            ProcessingFirePower.NORMAL,
            Map.of("potato", 1, "carrot", 1),
            POLICY
        );

        assertInstanceOf(RecipeMatchResult.NoMatch.class, result);
    }

    @Test
    void limitsRecognizedBatchesToTheEquipmentCapacity() {
        var result = ProcessingRecipeMatcher.INSTANCE.match(
            List.of(recipe("stew", Map.of("potato", 2, "carrot", 1))),
            "cauldron",
            ProcessingFirePower.NORMAL,
            Map.of("potato", 8, "carrot", 4),
            new RecipeMatchPolicy(3, 0.5, 0, 0.01)
        );

        var matched = assertInstanceOf(RecipeMatchResult.Matched.class, result);
        assertEquals(3, matched.getCandidate().getBatches());
    }

    @Test
    void rejectsExcessAndUndefinedIngredientsBeyondTheirLimits() {
        var recipe = recipe("stew", Map.of("potato", 2, "carrot", 1));
        assertInstanceOf(RecipeMatchResult.NoMatch.class, ProcessingRecipeMatcher.INSTANCE.match(
            List.of(recipe), "cauldron", ProcessingFirePower.NORMAL,
            Map.of("potato", 4, "carrot", 1), POLICY
        ));
        assertInstanceOf(RecipeMatchResult.NoMatch.class, ProcessingRecipeMatcher.INSTANCE.match(
            List.of(recipe), "cauldron", ProcessingFirePower.NORMAL,
            Map.of("potato", 2, "carrot", 1, "dirt", 2), POLICY
        ));
    }

    @Test
    void firePowerIsPartOfRecipeSelection() {
        var recipe = recipe("stew", Map.of("potato", 2));
        assertInstanceOf(RecipeMatchResult.NoMatch.class, ProcessingRecipeMatcher.INSTANCE.match(
            List.of(recipe), "cauldron", ProcessingFirePower.HIGH, Map.of("potato", 2), POLICY
        ));
    }

    @Test
    void reportsCandidatesInsideTheAmbiguityThreshold() {
        var first = recipe("a", Map.of("wheat", 2, "sugar", 1));
        var second = recipe("b", Map.of("wheat", 3, "sugar", 1));
        var result = ProcessingRecipeMatcher.INSTANCE.match(
            List.of(second, first),
            "cauldron",
            ProcessingFirePower.NORMAL,
            Map.of("wheat", 5, "sugar", 2),
            new RecipeMatchPolicy(3, 1.0, 0, 1.0)
        );

        assertInstanceOf(RecipeMatchResult.Ambiguous.class, result);
    }

    @Test
    void rejectsEquivalentNormalizedRecipeRatiosAtLoadTime() {
        var first = recipe("a", Map.of("wheat", 2, "sugar", 1));
        var second = recipe("b", Map.of("wheat", 4, "sugar", 2));

        assertThrows(
            IllegalStateException.class,
            () -> ProcessingRecipeMatcher.INSTANCE.validateNoConflicts(List.of(first, second))
        );
    }

    @Test
    void usesRecipeIdAsTheStableFinalOrder() {
        var a = recipe("a", Map.of("wheat", 1));
        var b = recipe("b", Map.of("wheat", 1, "optional", 1));
        var result = ProcessingRecipeMatcher.INSTANCE.match(
            List.of(b, a),
            "cauldron",
            ProcessingFirePower.NORMAL,
            Map.of("wheat", 1),
            new RecipeMatchPolicy(3, 0.0, 0, 0.0)
        );

        var matched = assertInstanceOf(RecipeMatchResult.Matched.class, result);
        assertEquals("a", matched.getCandidate().getRecipe().getId());
    }

    private static ProcessingRecipe recipe(String id, Map<String, Integer> ingredients) {
        return new ProcessingRecipe(id, "cauldron", ProcessingFirePower.NORMAL, ingredients);
    }
}
