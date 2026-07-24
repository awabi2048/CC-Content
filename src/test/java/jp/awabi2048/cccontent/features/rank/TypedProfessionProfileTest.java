package jp.awabi2048.cccontent.features.rank;

import jp.awabi2048.cccontent.features.rank.profession.Profession;
import jp.awabi2048.cccontent.features.rank.profession.profile.FarmerSkillProfile;
import jp.awabi2048.cccontent.features.rank.profession.profile.FisherSkillProfile;
import jp.awabi2048.cccontent.features.rank.profession.profile.LumberjackSkillProfile;
import jp.awabi2048.cccontent.features.rank.profession.profile.MinerSkillProfile;
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionFeatureToggles;
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionSpecialization;
import jp.awabi2048.cccontent.features.rank.profession.profile.TypedProfessionProfileResolver;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedProfessionProfileTest {
    @Test
    void levelAloneDoesNotUnlockSpecialistAbilities() {
        MinerSkillProfile profile = (MinerSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.MINER,
            50,
            Set.of("initial"),
            ProfessionFeatureToggles.Companion.defaultsFor(Profession.MINER)
        );

        assertEquals(1, profile.getMaximumBatchSize());
        assertEquals(0, profile.getWorkSpeedLevel());
        assertFalse(profile.getExpertOperationUnlocked());
        assertFalse(profile.getAutomaticCollectionEnabled());
    }

    @Test
    void minerSkillsProjectToCollectionProfile() {
        ProfessionFeatureToggles toggles = ProfessionFeatureToggles.Companion.defaultsFor(Profession.MINER);
        toggles.setBatchProcessingEnabled(true);
        MinerSkillProfile profile = (MinerSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.MINER,
            50,
            Set.of("initial", "speed_1", "speed_2", "durability_1", "mineall_1", "mineall_2", "mineall_3"),
            toggles
        );

        assertEquals(ProfessionSpecialization.TUNNEL_MINING, profile.getSpecialization());
        assertEquals(24, profile.getMaximumBatchSize());
        assertEquals(2, profile.getWorkSpeedLevel());
        assertTrue(profile.getBatchProcessingEnabled());
        assertTrue(profile.getAutomaticCollectionEnabled());
    }

    @Test
    void operationTogglesStillControlUnlockedSkills() {
        ProfessionFeatureToggles toggles = ProfessionFeatureToggles.Companion.defaultsFor(Profession.LUMBERJACK);
        LumberjackSkillProfile disabled = (LumberjackSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.LUMBERJACK,
            50,
            Set.of("initial", "cut_all_1", "cut_all_2", "cut_all_3", "replant"),
            toggles
        );
        assertFalse(disabled.getBatchProcessingEnabled());
        assertTrue(disabled.getAutomaticReplantEnabled());

        toggles.setBatchProcessingEnabled(true);
        toggles.setAutomaticReplantEnabled(true);
        LumberjackSkillProfile enabled = (LumberjackSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.LUMBERJACK,
            50,
            Set.of("initial", "cut_all_1", "cut_all_2", "cut_all_3", "replant"),
            toggles
        );
        assertTrue(enabled.getBatchProcessingEnabled());
        assertTrue(enabled.getAutomaticReplantEnabled());
    }

    @Test
    void farmerAndFisherMilestonesComeFromAcquiredSkills() {
        ProfessionFeatureToggles farmerToggles = ProfessionFeatureToggles.Companion.defaultsFor(Profession.FARMER);
        farmerToggles.setAreaTillingEnabled(true);
        farmerToggles.setAreaHarvestEnabled(true);
        FarmerSkillProfile farmer = (FarmerSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.FARMER,
            50,
            Set.of("initial", "area_tilling", "area_harvesting", "special_loot_table_1", "special_loot_table_2"),
            farmerToggles
        );
        assertTrue(farmer.getAreaTillingEnabled());
        assertTrue(farmer.getAreaHarvestEnabled());
        assertEquals(1, farmer.getGuaranteedSpecialistPlantYield());

        FisherSkillProfile fisher = (FisherSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.FISHER,
            50,
            Set.of("initial", "patient_cast", "deep_water", "master_angler"),
            ProfessionFeatureToggles.Companion.defaultsFor(Profession.FISHER)
        );
        assertEquals(1.20, fisher.getMatchingCandidateWeightMultiplier());
        assertTrue(fisher.getIncompatibleBaitWarningEnabled());
    }
}
