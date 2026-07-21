package jp.awabi2048.cccontent.features.resourcecollection;

import com.awabi2048.ccsystem.api.time.Season;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SeasonalPlantRegistryTest {
    private final SeasonalPlantDefinition springFern = new SeasonalPlantDefinition(
        "approved_spring_fern",
        "resource.approved_spring_fern",
        "resource_collection.test.use",
        "resource_collection.test.group",
        Map.of(Season.SPRING, 1, Season.SUMMER, 0, Season.AUTUMN, 0, Season.WINTER, 0),
        Set.of(Material.FERN),
        Set.of("minecraft:plains"),
        60,
        100,
        "kota_server:custom_item/resource/approved_spring_fern"
    );

    @Test
    void disabledRegistryNeverCreatesCandidate() {
        var registry = SeasonalPlantRegistry.Companion.of(false, List.of(springFern));

        assertNull(registry.select(Season.SPRING, Material.FERN, "minecraft:plains", 64, new Random(1)));
    }

    @Test
    void matchesSeasonMaterialBiomeAndHeightTogether() {
        var registry = SeasonalPlantRegistry.Companion.of(true, List.of(springFern));

        assertEquals(springFern, registry.select(Season.SPRING, Material.FERN, "minecraft:plains", 64, new Random(1)));
        assertNull(registry.select(Season.SUMMER, Material.FERN, "minecraft:plains", 64, new Random(1)));
        assertNull(registry.select(Season.SPRING, Material.SHORT_GRASS, "minecraft:plains", 64, new Random(1)));
        assertNull(registry.select(Season.SPRING, Material.FERN, "minecraft:forest", 64, new Random(1)));
        assertNull(registry.select(Season.SPRING, Material.FERN, "minecraft:plains", 40, new Random(1)));
    }
}
