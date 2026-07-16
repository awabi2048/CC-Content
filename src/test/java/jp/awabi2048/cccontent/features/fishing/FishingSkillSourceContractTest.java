package jp.awabi2048.cccontent.features.fishing;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingSkillSourceContractTest {
    @Test
    void fishingUsesRegisteredSkillEffectsInsteadOfSkillIds() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/fishing/FishingFeature.kt"
        ));

        assertTrue(source.contains("FisherBonusHandler.HOOK_WINDOW_EFFECT"));
        assertTrue(source.contains("FisherBonusHandler.STABILITY_EFFECT"));
        assertTrue(source.contains("FisherBonusHandler.DURATION_EFFECT"));
        assertTrue(source.contains("settings.minigame.baseHookWindowTicks * fisher.hookWindowMultiplier"));
        assertFalse(source.contains("\"patient_cast\" in skills"));
        assertFalse(source.contains("\"deep_water\" in skills"));
        assertFalse(source.contains("\"master_angler\" in skills"));
    }
}
