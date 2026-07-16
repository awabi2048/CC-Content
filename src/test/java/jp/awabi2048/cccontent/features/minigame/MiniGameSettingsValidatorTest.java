package jp.awabi2048.cccontent.features.minigame;

import jp.awabi2048.cccontent.features.minigame.core.MiniGameSettingsValidator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MiniGameSettingsValidatorTest {
    private static Map<String, Object> settings() {
        return Map.of(
            "default_game_id", "race",
            "defaults", Map.of("time_limit_seconds", 300, "marker_radius", 1.75)
        );
    }

    private static Map<String, Map<String, Object>> gameConfigs() {
        return Map.of(
            "hideandseek", Map.of("time_limit_seconds", 300, "hunter_count", 1, "preparation_seconds", 60),
            "chase", Map.of("time_limit_seconds", 300, "hunter_count", 1),
            "colosseum", Map.of("time_limit_seconds", 600, "first_to", 3),
            "endergolf", Map.of("time_limit_seconds", 900)
        );
    }

    @Test
    void acceptsTheCompleteSupportedConfiguration() {
        assertDoesNotThrow(() -> MiniGameSettingsValidator.INSTANCE.validateSettings(settings(), gameConfigs()));
    }

    @Test
    void rejectsMissingTypeRangeAndUnknownSettings() {
        assertThrows(IllegalArgumentException.class, () -> MiniGameSettingsValidator.INSTANCE.validateGame(
            "chase", Map.of("time_limit_seconds", 300)));
        assertThrows(IllegalArgumentException.class, () -> MiniGameSettingsValidator.INSTANCE.validateGame(
            "chase", Map.of("time_limit_seconds", 29, "hunter_count", 1)));
        assertThrows(IllegalArgumentException.class, () -> MiniGameSettingsValidator.INSTANCE.validateGame(
            "chase", Map.of("time_limit_seconds", 300, "hunter_count", 1, "unknown", 1)));
        assertThrows(IllegalArgumentException.class, () -> MiniGameSettingsValidator.INSTANCE.validateSettings(
            Map.of("default_game_id", "unknown", "defaults", Map.of("time_limit_seconds", 300, "marker_radius", 1.75)), gameConfigs()));
    }

    @Test
    void rejectsInvalidStateOverridesAndUnknownGameIds() {
        assertThrows(IllegalArgumentException.class, () -> MiniGameSettingsValidator.INSTANCE.validateState(Map.of(
            "games", Map.of("00000000-0000-0000-0000-000000000001", Map.of(
                "race", Map.of("time_limit_seconds", 29)
            ))
        )));
        assertThrows(IllegalArgumentException.class, () -> MiniGameSettingsValidator.INSTANCE.validateState(Map.of(
            "games", Map.of("00000000-0000-0000-0000-000000000001", Map.of(
                "unsupported", Map.of("time_limit_seconds", 300)
            ))
        )));
    }
}
