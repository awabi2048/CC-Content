package jp.awabi2048.cccontent.features.cooking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CookingIngredientResolverTest {
    private final CookingIngredientResolver resolver = new CookingIngredientResolver(List.of(
        ingredient("custom", CookingIngredientMatcherType.CUSTOM_ITEM_ID, "cooking.cut_cod"),
        ingredient("fish", CookingIngredientMatcherType.FISH_ID, "cod"),
        ingredient("resource", CookingIngredientMatcherType.RESOURCE_ID, "rice"),
        ingredient("material", CookingIngredientMatcherType.MATERIAL, "COD")
    ));

    @Test
    void resolvesInCanonicalPriorityOrder() {
        assertEquals(
            "custom",
            resolver.resolveIds("cooking.cut_cod", "cod", "rice", "COD").getId()
        );
    }

    @Test
    void fallsBackThroughFishResourceAndMaterial() {
        assertEquals("fish", resolver.resolveIds(null, "cod", null, "COD").getId());
        assertEquals("resource", resolver.resolveIds(null, null, "rice", "POISONOUS_POTATO").getId());
        assertEquals("material", resolver.resolveIds(null, null, null, "COD").getId());
        assertNull(resolver.resolveIds(null, null, null, "DIAMOND"));
    }

    private static UnifiedCookingIngredient ingredient(
        String id,
        CookingIngredientMatcherType type,
        String value
    ) {
        return new UnifiedCookingIngredient(
            id, new CookingIngredientMatcher(type, value), "key." + id, null
        );
    }
}
