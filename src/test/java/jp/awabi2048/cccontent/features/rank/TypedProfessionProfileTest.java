package jp.awabi2048.cccontent.features.rank;

import jp.awabi2048.cccontent.features.rank.profession.Profession;
import jp.awabi2048.cccontent.features.rank.profession.ProfessionProgressionMode;
import jp.awabi2048.cccontent.features.rank.profession.profile.CookSkillProfile;
import jp.awabi2048.cccontent.features.rank.profession.profile.FisherSkillProfile;
import jp.awabi2048.cccontent.features.rank.profession.profile.MinerSkillProfile;
import jp.awabi2048.cccontent.features.rank.profession.profile.LumberjackSkillProfile;
import jp.awabi2048.cccontent.features.rank.profession.profile.FarmerSkillProfile;
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionExperience;
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionFeatureToggles;
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionSpecialization;
import jp.awabi2048.cccontent.features.rank.profession.profile.TypedProfessionLevelCurve;
import jp.awabi2048.cccontent.features.rank.profession.profile.TypedProfessionProfileResolver;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedProfessionProfileTest {
    @Test
    void exactlySixProfessionsUseTypedProfiles() {
        Set<Profession> actual = Stream.of(Profession.values())
            .filter(Profession::getUsesTypedProfile)
            .collect(Collectors.toSet());

        assertEquals(Set.of(
            Profession.MINER,
            Profession.LUMBERJACK,
            Profession.FARMER,
            Profession.FISHER,
            Profession.BREWER,
            Profession.COOK
        ), actual);
        assertEquals(ProfessionProgressionMode.LEGACY_SKILL_TREE, Profession.SWORDSMAN.getProgressionMode());
    }

    @Test
    void typedLevelCurveStartsAtZeroAndCapsAtFifty() {
        assertEquals(0, TypedProfessionLevelCurve.INSTANCE.calculateLevel(Profession.MINER, 0));
        assertEquals(0, TypedProfessionLevelCurve.INSTANCE.calculateLevel(Profession.MINER, 9));
        assertEquals(1, TypedProfessionLevelCurve.INSTANCE.calculateLevel(Profession.MINER, 10));
        assertEquals(0, TypedProfessionLevelCurve.INSTANCE.calculateLevel(Profession.FISHER, 99));
        assertEquals(1, TypedProfessionLevelCurve.INSTANCE.calculateLevel(Profession.FISHER, 100));

        long maxExp = TypedProfessionLevelCurve.INSTANCE.requiredTotalExp(Profession.COOK, 50);
        assertEquals(50, TypedProfessionLevelCurve.INSTANCE.calculateLevel(Profession.COOK, maxExp));
        assertEquals(0, TypedProfessionLevelCurve.INSTANCE.expToNextLevel(Profession.COOK, maxExp));
        assertThrows(IllegalArgumentException.class,
            () -> TypedProfessionLevelCurve.INSTANCE.calculateLevel(Profession.WARRIOR, 0));
    }

    @Test
    void specializationRequiresLevelFifteenAndMustMatchProfession() {
        assertThrows(IllegalArgumentException.class, () -> TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.MINER,
            14,
            ProfessionSpecialization.TUNNEL_MINING.getId(),
            ProfessionFeatureToggles.Companion.defaultsFor(Profession.MINER)
        ));
        assertThrows(IllegalArgumentException.class, () -> TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.MINER,
            15,
            ProfessionSpecialization.FELLING.getId(),
            ProfessionFeatureToggles.Companion.defaultsFor(Profession.MINER)
        ));
    }

    @Test
    void minerProfileResolvesCommonAndSpecializedAbilities() {
        ProfessionFeatureToggles toggles = ProfessionFeatureToggles.Companion.defaultsFor(Profession.MINER);
        toggles.setBatchProcessingEnabled(true);
        MinerSkillProfile profile = (MinerSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.MINER,
            50,
            ProfessionSpecialization.TUNNEL_MINING.getId(),
            toggles
        );

        assertEquals(24, profile.getMaximumBatchSize());
        assertEquals(3, profile.getWorkSpeedLevel());
        assertEquals(0.20, profile.getDurabilitySaveChance());
        assertEquals(0.05, profile.getOrdinaryExtraDropChance());
        assertTrue(profile.getBatchProcessingEnabled());
        assertTrue(profile.getAutomaticCollectionEnabled());
        assertTrue(profile.getOptimizedSearchEnabled());

        MinerSkillProfile precision = (MinerSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.MINER,
            50,
            ProfessionSpecialization.PRECISION_MINING.getId(),
            toggles
        );
        assertEquals(0.88, precision.getTopEvaluationThreshold());
        assertEquals(1, precision.getIgnoredMinorFailures());
    }

    @Test
    void riskyCollectionOperationsUnlockAtLevelFiveForEitherBranch() {
        ProfessionFeatureToggles minerToggles = ProfessionFeatureToggles.Companion.defaultsFor(Profession.MINER);
        minerToggles.setBatchProcessingEnabled(true);
        MinerSkillProfile commonMiner = (MinerSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.MINER, 5, null, minerToggles
        );
        MinerSkillProfile precisionMiner = (MinerSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.MINER, 15, ProfessionSpecialization.PRECISION_MINING.getId(), minerToggles
        );
        assertTrue(commonMiner.getBatchProcessingEnabled());
        assertEquals(8, commonMiner.getMaximumBatchSize());
        assertTrue(precisionMiner.getBatchProcessingEnabled());
        assertEquals(8, precisionMiner.getMaximumBatchSize());

        ProfessionFeatureToggles lumberjackToggles =
            ProfessionFeatureToggles.Companion.defaultsFor(Profession.LUMBERJACK);
        lumberjackToggles.setBatchProcessingEnabled(true);
        LumberjackSkillProfile utilization =
            (LumberjackSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
                Profession.LUMBERJACK,
                15,
                ProfessionSpecialization.WOOD_UTILIZATION.getId(),
                lumberjackToggles
            );
        assertTrue(utilization.getBatchProcessingEnabled());
        assertEquals(8, utilization.getMaximumBatchSize());

        ProfessionFeatureToggles farmerToggles =
            ProfessionFeatureToggles.Companion.defaultsFor(Profession.FARMER);
        farmerToggles.setAreaTillingEnabled(true);
        farmerToggles.setAreaHarvestEnabled(true);
        FarmerSkillProfile commonFarmer =
            (FarmerSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
                Profession.FARMER, 5, null, farmerToggles
            );
        assertTrue(commonFarmer.getAreaTillingEnabled());
        assertTrue(commonFarmer.getAreaHarvestEnabled());
        assertTrue(commonFarmer.getAutomaticReplantEnabled());
        assertEquals(1, commonFarmer.getOperationRadius());
    }

    @Test
    void riskyCollectionOperationTogglesRemainOffByDefault() {
        MinerSkillProfile miner = (MinerSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.MINER,
            50,
            ProfessionSpecialization.TUNNEL_MINING.getId(),
            ProfessionFeatureToggles.Companion.defaultsFor(Profession.MINER)
        );
        LumberjackSkillProfile lumberjack =
            (LumberjackSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
                Profession.LUMBERJACK,
                50,
                ProfessionSpecialization.FELLING.getId(),
                ProfessionFeatureToggles.Companion.defaultsFor(Profession.LUMBERJACK)
            );
        assertFalse(miner.getBatchProcessingEnabled());
        assertFalse(lumberjack.getBatchProcessingEnabled());
    }

    @Test
    void fishingAndCookingProfilesResolveFinalMilestones() {
        FisherSkillProfile fisher = (FisherSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.FISHER,
            50,
            ProfessionSpecialization.FISHING_GROUND_KNOWLEDGE.getId(),
            ProfessionFeatureToggles.Companion.defaultsFor(Profession.FISHER)
        );
        assertEquals(0.20, fisher.getWaitTimeReduction());
        assertEquals(0.15, fisher.getBaitSaveChance());
        assertEquals(1.20, fisher.getMatchingCandidateWeightMultiplier());
        assertTrue(fisher.getIncompatibleBaitWarningEnabled());

        CookSkillProfile cook = (CookSkillProfile) TypedProfessionProfileResolver.INSTANCE.resolve(
            Profession.COOK,
            50,
            ProfessionSpecialization.BULK_COOKING.getId(),
            ProfessionFeatureToggles.Companion.defaultsFor(Profession.COOK)
        );
        assertEquals(5, cook.getIngredientSlots());
        assertEquals(80, cook.getMinimumCompletion());
        assertEquals(0.25, cook.getExtraCompletionChance());
        assertEquals(0.15, cook.getMaterialSaveChance());
        assertFalse(cook.getTopRecipeUnlocked() && cook.getSpecialization() == null);
    }

    @Test
    void batchExperienceUsesDiminishingReturnsAndNormalActionCap() {
        assertEquals(0, ProfessionExperience.INSTANCE.batchExperience(0, 1));
        assertEquals(1, ProfessionExperience.INSTANCE.batchExperience(1, 1));
        assertEquals(6, ProfessionExperience.INSTANCE.batchExperience(8, 1));
        assertEquals(16, ProfessionExperience.INSTANCE.batchExperience(100, 1));
    }
}
