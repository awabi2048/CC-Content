package jp.awabi2048.cccontent;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingConfigurationTest {
    private static final Path CONFIG = Path.of("src/main/resources/config/fishing/fish.yml");

    @Test
    void definesConditionedCatchesAndClickGame() {
        var config = YamlConfiguration.loadConfiguration(CONFIG.toFile());
        assertTrue(config.getInt("minigame.click_count") > 0);
        assertTrue(config.getLong("minigame.timeout_ticks") > 0);
        assertFalse(config.getConfigurationSection("fish").getKeys(false).isEmpty());
        for (String fish : config.getConfigurationSection("fish").getKeys(false)) {
            assertTrue(config.isConfigurationSection("fish." + fish + ".quality"));
            assertTrue(config.contains("fish." + fish + ".bobber_y.min"));
            assertTrue(config.contains("fish." + fish + ".bobber_y.max"));
        }
    }
}
