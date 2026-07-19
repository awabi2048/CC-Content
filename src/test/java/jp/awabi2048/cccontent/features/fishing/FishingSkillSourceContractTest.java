package jp.awabi2048.cccontent.features.fishing;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingSkillSourceContractTest {
    @Test
    void fishingUsesTypedProfileForEveryProfessionAbility() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/fishing/FishingFeature.kt"
        ));

        assertTrue(source.contains("as? FisherSkillProfile"));
        assertTrue(source.contains("settings.minigame.baseHookWindowTicks * fisher.hookWindowMultiplier"));
        assertTrue(source.contains("fisher.waitTimeReduction"));
        assertTrue(source.contains("fisher.baitSaveChance"));
        assertTrue(source.contains("fisher.durabilitySaveChance"));
        assertTrue(source.contains("fisher.vanillaExtraCatchChance"));
        assertTrue(source.contains("fisher.ignoredInstabilityEvents"));
        assertTrue(source.contains("ContentActionType.FISH_CAUGHT"));
        assertTrue(source.contains("ContentActionType.VANILLA_FISH_CAUGHT"));
        assertTrue(source.contains("ProfessionExperience.FIRST_DISCOVERY_BONUS"));
        assertTrue(source.contains("GuiLoreSpec.Blocks"));
        assertTrue(source.contains("fishing.dictionary.hint.candidates"));
        assertFalse(source.contains("§7・§f"));
        assertFalse(source.contains("SkillEffectEngine"));
        assertFalse(source.contains("FisherBonusHandler"));
        assertFalse(source.contains("catchData.exp"));

        String settings = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/fishing/FishingSettings.kt"
        ));
        assertFalse(settings.contains("job_exp.yml"));
        assertFalse(settings.contains("fisherExp"));
    }
}
