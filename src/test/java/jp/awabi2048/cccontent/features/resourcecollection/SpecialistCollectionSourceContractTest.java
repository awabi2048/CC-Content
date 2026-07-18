package jp.awabi2048.cccontent.features.resourcecollection;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialistCollectionSourceContractTest {
    @Test
    void specialistOperationsRespectSharedOriginProtectionAndExperienceBoundaries() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/resourcecollection/SpecialistCollectionService.kt"
        ));
        String normalRewards = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/resourcecollection/ResourceCollectionFeature.kt"
        ));

        assertTrue(source.contains("getResourceWorldLifecycleService().isReady"));
        assertTrue(source.contains("getNaturalOriginRegistry().isNatural"));
        assertTrue(source.contains("BlockBreakEvent(block, player)"));
        assertTrue(source.contains("ProfessionExperience.SPECIALIST_ACTION"));
        assertTrue(source.contains("event.interactionPoint"));
        assertTrue(source.contains("event.isCancelled = true"));
        assertTrue(source.contains(".coerceIn(1, 24)"));
        assertTrue(source.contains("ProfessionExperience.batchExperience(processed)"));
        assertTrue(source.contains("profile.batchProcessingEnabled"));
        assertTrue(source.contains("giveResource(player, \"heartwood\", 1)"));
        assertTrue(source.contains("resource.gathering_guide"));
        assertTrue(source.contains("resource.gathering_sickle"));
        assertTrue(source.contains("gatheringTargets[player.uniqueId] = targets"));
        assertFalse(source.contains("Enchantment.FORTUNE"));
        assertFalse(normalRewards.contains("addProfessionExp"));
        assertFalse(normalRewards.contains("CraftItemEvent"));
    }

    @Test
    void rankNormalExperienceSkipsSyntheticSpecialistBreakEvents() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/rank/job/ProfessionMinerExpListener.kt"
        ));
        assertTrue(source.contains("SpecialistCollectionService.isInternalBreak"));
    }
}
