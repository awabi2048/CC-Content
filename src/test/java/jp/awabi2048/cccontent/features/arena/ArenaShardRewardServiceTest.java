package jp.awabi2048.cccontent.features.arena;

import jp.awabi2048.cccontent.items.arena.ArenaEnchantShardRegistry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArenaShardRewardServiceTest {
    @Test
    void rejectsShardAssignedToMultipleDifficulties() {
        var config = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                1:
                  entries:
                    - {id: sharpness_1, tier: 1, type: limit_breaking, weight: 1}
                2:
                  entries:
                    - {id: sharpness_1, tier: 1, type: limit_breaking, weight: 1}
                3:
                  entries:
                    - {id: power_2, tier: 2, type: limit_breaking, weight: 1}
                4:
                  entries:
                    - {id: fire_aspect_3, tier: 3, type: limit_breaking, weight: 1}
                """));

        assertThrows(IllegalArgumentException.class, () -> ArenaShardRewardService.Companion.load(
                config, ArenaEnchantShardRegistry.INSTANCE.getDefinitions()));
    }

    @Test
    void eachDifficultyUsesOnlyItsOwnTable() {
        var config = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                1:
                  entries:
                    - id: sharpness_1
                      tier: 1
                      type: limit_breaking
                      weight: 1
                2:
                  entries:
                    - id: sharpness_2
                      tier: 2
                      type: limit_breaking
                      weight: 1
                3:
                  entries:
                    - id: power_2
                      tier: 2
                      type: limit_breaking
                      weight: 1
                4:
                  entries:
                    - id: fire_aspect_3
                      tier: 3
                      type: limit_breaking
                      weight: 1
                """));

        var service = ArenaShardRewardService.Companion.load(config, ArenaEnchantShardRegistry.INSTANCE.getDefinitions());

        assertEquals("sharpness_1", service.entriesForDifficulty(1).get(0).getDefinition().getKey());
        assertEquals("sharpness_2", service.entriesForDifficulty(2).get(0).getDefinition().getKey());
        assertEquals("fire_aspect_3", service.entriesForDifficulty(4).get(0).getDefinition().getKey());
    }

    @Test
    void rejectsUnknownDifficultyAndShardIds() {
        var unknownDifficulty = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                1:
                  entries:
                    - id: sharpness_1
                      tier: 1
                      type: limit_breaking
                      weight: 1
                5:
                  entries:
                    - id: sharpness_1
                      tier: 1
                      type: limit_breaking
                      weight: 1
                """));
        assertThrows(IllegalArgumentException.class, () -> ArenaShardRewardService.Companion.load(
                unknownDifficulty, ArenaEnchantShardRegistry.INSTANCE.getDefinitions()));

        var unknownShard = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                1:
                  entries:
                    - id: missing_shard
                      tier: 1
                      type: limit_breaking
                      weight: 1
                2:
                  entries:
                    - id: sharpness_2
                      tier: 2
                      type: limit_breaking
                      weight: 1
                3:
                  entries:
                    - id: power_2
                      tier: 2
                      type: limit_breaking
                      weight: 1
                4:
                  entries:
                    - id: fire_aspect_3
                      tier: 3
                      type: limit_breaking
                      weight: 1
                """));
        assertThrows(IllegalStateException.class, () -> ArenaShardRewardService.Companion.load(
                unknownShard, ArenaEnchantShardRegistry.INSTANCE.getDefinitions()));
    }

    @Test
    void rejectsEmptyTablesAndHighTierAtLowDifficulty() {
        var empty = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                1:
                  entries: []
                2:
                  entries:
                    - id: sharpness_2
                      tier: 2
                      type: limit_breaking
                      weight: 1
                3:
                  entries:
                    - id: power_2
                      tier: 2
                      type: limit_breaking
                      weight: 1
                4:
                  entries:
                    - id: fire_aspect_3
                      tier: 3
                      type: limit_breaking
                      weight: 1
                """));
        assertThrows(IllegalArgumentException.class, () -> ArenaShardRewardService.Companion.load(
                empty, ArenaEnchantShardRegistry.INSTANCE.getDefinitions()));

        var highTier = YamlConfiguration.loadConfiguration(new java.io.StringReader("""
                1:
                  entries:
                    - id: sharpness_2
                      tier: 2
                      type: limit_breaking
                      weight: 1
                2:
                  entries:
                    - id: sharpness_2
                      tier: 2
                      type: limit_breaking
                      weight: 1
                3:
                  entries:
                    - id: power_2
                      tier: 2
                      type: limit_breaking
                      weight: 1
                4:
                  entries:
                    - id: fire_aspect_3
                      tier: 3
                      type: limit_breaking
                      weight: 1
                """));
        assertThrows(IllegalArgumentException.class, () -> ArenaShardRewardService.Companion.load(
                highTier, ArenaEnchantShardRegistry.INSTANCE.getDefinitions()));
    }
}
