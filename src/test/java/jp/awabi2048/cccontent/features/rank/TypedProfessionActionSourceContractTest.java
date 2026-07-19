package jp.awabi2048.cccontent.features.rank;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedProfessionActionSourceContractTest {
    @Test
    void gatheringActionsUseCodeDefinedExperienceAndSharedDispatcher() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/rank/job/ProfessionMinerExpListener.kt"
        ));

        assertTrue(source.contains("ProfessionExperience.NORMAL_ACTION"));
        assertTrue(source.contains("ContentActionType.MINERAL_EXTRACTED"));
        assertTrue(source.contains("ContentActionType.TREE_PROCESSED"));
        assertTrue(source.contains("ContentActionType.CROP_HARVESTED"));
        assertTrue(source.contains("getNaturalOriginRegistry().isNatural(event.block)"));
        assertTrue(source.contains("ResourceMaterialPolicy.classify"));
        assertTrue(source.contains("recordProfessionCycleAction"));
        assertFalse(source.contains("YamlConfiguration"));
        assertFalse(source.contains("job_exp.yml"));
        assertFalse(source.contains("mock_exp"));
        assertFalse(source.contains("IgnoreBlockStore"));
    }

    @Test
    void legacySkillTreesAreRegisteredForOnlyFourProfessions() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/CCContent.kt"
        ));

        assertTrue(source.contains("Profession.values().filterNot { it.usesTypedProfile }"));
        assertFalse(source.contains("FisherBonusHandler.all().forEach"));
        assertFalse(source.contains("SkillEffectRegistry.register(FarmerAreaHarvestingHandler"));
    }
}
