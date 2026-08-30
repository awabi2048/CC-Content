package jp.awabi2048.cccontent.features.environment;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionEnvironmentResolverTest {
    private final CollectionEnvironmentResolver resolver = CollectionEnvironmentResolver.Companion.defaults();

    @Test
    void positionConditionsShareTemperateAndVerticalClassification() {
        var conditions = resolver.conditions(
            "minecraft:flower_forest", World.Environment.NORMAL, 64
        );

        assertEquals(ClimateRegion.TEMPERATE, conditions.getClimate());
        assertTrue(resolver.isInBiomeGroup("minecraft:flower_forest", "temperate"));
        assertTrue(conditions.hasVerticalRegion("wild_gathering"));
    }

    @Test
    void soybeanTemperateGroupIncludesTheExpandedNormalBiomeSet() {
        assertTrue(resolver.isInBiomeGroup("minecraft:birch_forest", "temperate"));
        assertTrue(resolver.isInBiomeGroup("minecraft:dark_forest", "temperate"));
        assertTrue(resolver.isInBiomeGroup("minecraft:cherry_grove", "temperate"));
        assertTrue(!resolver.isInBiomeGroup("minecraft:taiga", "temperate"));
    }

    @Test
    void oceanFamilyIsAnExplicitSharedCondition() {
        var conditions = resolver.conditions("minecraft:deep_warm_ocean", World.Environment.NORMAL, 32);

        assertEquals(WaterRegion.OCEAN, conditions.getWater());
        assertTrue(resolver.isOceanFamily(conditions));
    }
}
