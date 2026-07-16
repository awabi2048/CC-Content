package jp.awabi2048.cccontent;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingConfigurationTest {
    private static final Path CONFIG = Path.of("src/main/resources/config/fishing/fish.yml");

    @Test
    void definesConditionedCatchesBaitsRodsAndFightGame() {
        var config = YamlConfiguration.loadConfiguration(CONFIG.toFile());
        assertTrue(config.getInt("schema_version") == 9);
        assertTrue(config.getLong("minigame.fight_interval_ticks") == 1);
        assertTrue(config.getLong("minigame.hook_window_ticks") > 0);
        assertTrue(config.getLong("minigame.fight_duration_ticks") > 0);
        assertTrue(config.getLong("minigame.status_message_ticks") == 60);
        assertTrue(config.getDouble("minigame.input_step") == 3.0);
        assertTrue(config.getDouble("minigame.resistance_smoothing") > 0);
        assertTrue(config.getDouble("minigame.lateral_smoothing") > 0);
        for (String rod : config.getConfigurationSection("rod").getKeys(false)) {
            assertTrue(config.contains("rod." + rod + ".power_multiplier"));
            assertTrue(config.contains("rod." + rod + ".finesse_multiplier"));
            assertFalse(config.contains("rod." + rod + ".hook_window_multiplier"));
            assertFalse(config.contains("rod." + rod + ".fight_duration_multiplier"));
        }
        assertFalse(config.getConfigurationSection("bait").getKeys(false).isEmpty());
        assertFalse(config.getConfigurationSection("rod").getKeys(false).isEmpty());
        assertFalse(config.getConfigurationSection("fish").getKeys(false).isEmpty());
        var expectedResistance = Map.of(
            "cod", 10.5,
            "salmon", 15.0,
            "pufferfish", 21.0,
            "tropical_fish", 27.0,
            "ancient_coelacanth", 36.0
        );
        for (String fish : config.getConfigurationSection("fish").getKeys(false)) {
            assertTrue(config.isConfigurationSection("fish." + fish + ".quality"));
            assertTrue(config.getConfigurationSection("fish." + fish + ".quality").getKeys(false)
                .equals(Set.of("common", "rare", "legendary")));
            assertTrue(config.contains("fish." + fish + ".water.type"));
            assertTrue(config.contains("fish." + fish + ".water.depth.min"));
            assertTrue(config.contains("fish." + fish + ".water.depth.max"));
            assertTrue(config.contains("fish." + fish + ".water.width.min"));
            assertTrue(config.contains("fish." + fish + ".water.width.max"));
            assertFalse(config.contains("fish." + fish + ".bobber_y"));
            assertFalse(config.contains("fish." + fish + ".origin_distance"));
            assertTrue(config.contains("fish." + fish + ".fight.target_center"));
            assertTrue(config.getDouble("fish." + fish + ".fight.drift_per_step") == expectedResistance.get(fish));
        }
    }
}
