package jp.awabi2048.cccontent;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingConfigurationTest {
    private static final Path CONFIG = Path.of("src/main/resources/config/fishing/fish.yml");

    @Test
    void definesConditionedCatchesBaitsRodsAndFightGame() {
        var config = YamlConfiguration.loadConfiguration(CONFIG.toFile());
        assertTrue(config.getInt("config_version") == 4);
        assertFalse(config.contains("schema_version"));
        assertTrue(config.getBoolean("bait.consume_on_valid_session"));
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
        assertFalse(config.getConfigurationSection("bait.definitions").getKeys(false).isEmpty());
        assertFalse(config.getConfigurationSection("rod").getKeys(false).isEmpty());
        assertFalse(config.getConfigurationSection("fish").getKeys(false).isEmpty());
        var expectedFish = Set.of(
            "cod", "salmon", "pufferfish", "tropical_fish", "ancient_coelacanth",
            "crucian_carp", "carp", "trout", "ayu", "catfish", "eel", "smelt",
            "sardine", "horse_mackerel", "mackerel", "sea_bass", "flounder",
            "sea_bream", "tuna", "grouper", "anglerfish"
        );
        assertTrue(config.getConfigurationSection("fish").getKeys(false).equals(expectedFish));
        for (String fish : config.getConfigurationSection("fish").getKeys(false)) {
            assertTrue(config.isConfigurationSection("fish." + fish + ".quality"));
            assertTrue(config.getConfigurationSection("fish." + fish + ".quality").getKeys(false)
                .equals(Set.of("common", "rare", "legendary")));
            assertTrue(config.contains("fish." + fish + ".water.type"));
            assertTrue(config.contains("fish." + fish + ".water.depth.min"));
            assertTrue(config.contains("fish." + fish + ".water.depth.max"));
            assertTrue(config.contains("fish." + fish + ".water.width.min"));
            assertTrue(config.contains("fish." + fish + ".water.width.max"));
            int minimumSize = config.getInt("fish." + fish + ".size_cm.min");
            int maximumSize = config.getInt("fish." + fish + ".size_cm.max");
            int minimumWeight = config.getInt("fish." + fish + ".weight_grams.min");
            int maximumWeight = config.getInt("fish." + fish + ".weight_grams.max");
            assertTrue(minimumSize > 0 && minimumSize <= maximumSize);
            assertTrue(minimumWeight > 0 && minimumWeight <= maximumWeight);
            assertFalse(config.contains("fish." + fish + ".bobber_y"));
            assertFalse(config.contains("fish." + fish + ".origin_distance"));
            assertTrue(config.contains("fish." + fish + ".fight.target_center"));
            assertTrue(config.getDouble("fish." + fish + ".fight.drift_per_step") > 0);
            assertTrue(config.getDouble("fish." + fish + ".fight.direction_persistence") > 0);
            assertTrue(config.getDouble("fish." + fish + ".fight.direction_persistence") <= 1);
            var preferredSeasons = Set.copyOf(config.getStringList("fish." + fish + ".preferred_seasons"));
            var excludedSeasons = Set.copyOf(config.getStringList("fish." + fish + ".excluded_seasons"));
            assertTrue(java.util.Collections.disjoint(preferredSeasons, excludedSeasons));
        }
        assertTrue(Set.copyOf(config.getStringList("fish.cod.biomes")).equals(
            Set.of("ocean", "cold_ocean", "deep_ocean", "deep_cold_ocean", "river")
        ));
        assertTrue(Set.copyOf(config.getStringList("fish.pufferfish.biomes")).equals(
            Set.of("warm_ocean", "lukewarm_ocean")
        ));
        assertTrue(config.getInt("fish.ancient_coelacanth.size_cm.min") >
            config.getInt("fish.tropical_fish.size_cm.max"));
        assertTrue(config.getInt("fish.ancient_coelacanth.weight_grams.min") >
            config.getInt("fish.tropical_fish.weight_grams.max"));
        assertTrue(config.getDouble("fish.flounder.fight.drift_per_step") <
            config.getDouble("fish.mackerel.fight.drift_per_step"));
        assertTrue(config.getDouble("fish.tuna.fight.duration_multiplier") >
            config.getDouble("fish.crucian_carp.fight.duration_multiplier"));
        assertTrue(Set.copyOf(config.getStringList("fish.ayu.preferred_seasons")).equals(Set.of("summer")));
        assertTrue(Set.copyOf(config.getStringList("fish.ayu.excluded_seasons")).equals(Set.of("winter")));
        assertTrue(Set.copyOf(config.getStringList("fish.salmon.excluded_seasons")).equals(Set.of("spring")));
        assertTrue(Set.copyOf(config.getStringList("fish.smelt.excluded_seasons")).equals(Set.of("summer")));
    }
}
