package jp.awabi2048.cccontent;

import jp.awabi2048.cccontent.config.ContentConfigScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentConfigScopeTest {
    @Test
    void classifiesConfigResourcesByFailureBoundary() {
        assertEquals("core", ContentConfigScope.featureIdForResource("config/core.yml"));
        assertEquals("arena", ContentConfigScope.featureIdForResource("config/arena/settings.yml"));
        assertEquals("seasonal", ContentConfigScope.featureIdForResource("config/seasonal/events.yml"));
        assertEquals("custom_items", ContentConfigScope.featureIdForResource("config/custom_item/gulliver_light.yml"));
        assertEquals("oage_shrine", ContentConfigScope.featureIdForResource("config/npc/oage_shrine.yml"));
        assertEquals("shared", ContentConfigScope.featureIdForResource("config/mob_definition.yml"));
    }

    @Test
    void extractsFeatureFromValidationMessage() {
        String error = "[resource config validation] invalid boolean\n"
            + "  file: D:\\server\\plugins\\CC-Content\\config\\fishing\\fish.yml\n"
            + "  key: enabled";
        assertEquals("fishing", ContentConfigScope.featureIdFromValidationError(error));
        assertEquals("core", ContentConfigScope.featureIdFromValidationError("unknown failure"));
    }
}
