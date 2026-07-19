package jp.awabi2048.cccontent.features.rank.profession.profile

import jp.awabi2048.cccontent.features.rank.profession.Profession

sealed interface TypedProfessionProfile {
    val profession: Profession
    val level: Int
    val specialization: ProfessionSpecialization?
}

data class MinerSkillProfile(
    override val level: Int,
    override val specialization: ProfessionSpecialization?,
    val expertOperationUnlocked: Boolean,
    val batchProcessingEnabled: Boolean,
    val maximumBatchSize: Int,
    val workSpeedLevel: Int,
    val durabilitySaveChance: Double,
    val ordinaryExtraDropChance: Double,
    val automaticCollectionEnabled: Boolean,
    val optimizedSearchEnabled: Boolean,
    val precisionToleranceBonus: Double,
    val topEvaluationThreshold: Double,
    val detailedInspectionEnabled: Boolean,
    val minimumSpecialMaterialStandardEnabled: Boolean,
    val topEvaluationExtraMaterial: Int,
    val ignoredMinorFailures: Int
) : TypedProfessionProfile {
    override val profession = Profession.MINER
}

data class LumberjackSkillProfile(
    override val level: Int,
    override val specialization: ProfessionSpecialization?,
    val expertOperationUnlocked: Boolean,
    val batchProcessingEnabled: Boolean,
    val maximumBatchSize: Int,
    val workSpeedLevel: Int,
    val durabilitySaveChance: Double,
    val ordinaryExtraDropChance: Double,
    val leafCleanupEnabled: Boolean,
    val automaticReplantEnabled: Boolean,
    val multiTrunkRecognitionImproved: Boolean,
    val plankYield: Int,
    val timberYield: Int,
    val inspectionRadius: Int,
    val inspectionCooldownSeconds: Int,
    val discoveryChanceBonus: Double,
    val extraForestProductChance: Double,
    val exactMaterialInspectionEnabled: Boolean
) : TypedProfessionProfile {
    override val profession = Profession.LUMBERJACK
}

data class FarmerSkillProfile(
    override val level: Int,
    override val specialization: ProfessionSpecialization?,
    val expertOperationUnlocked: Boolean,
    val cropExtraDropChance: Double,
    val byproductChance: Double,
    val workSpeedLevel: Int,
    val durabilitySaveChance: Double,
    val areaTillingEnabled: Boolean,
    val areaHarvestEnabled: Boolean,
    val automaticReplantEnabled: Boolean,
    val operationRadius: Int,
    val seedReserveEnabled: Boolean,
    val matureCropSelectionEnabled: Boolean,
    val batchExperiencePenaltyReduced: Boolean,
    val seedSaveChance: Double,
    val inspectionRadius: Int,
    val inspectionCooldownSeconds: Int,
    val specialistPlantExtraChance: Double,
    val detailedInspectionEnabled: Boolean,
    val guaranteedSpecialistPlantYield: Int
) : TypedProfessionProfile {
    override val profession = Profession.FARMER
}

data class FisherSkillProfile(
    override val level: Int,
    override val specialization: ProfessionSpecialization?,
    val vanillaExtraCatchChance: Double,
    val waitTimeReduction: Double,
    val hookWindowBonus: Double,
    val baitSaveChance: Double,
    val durabilitySaveChance: Double,
    val fightStabilityBonus: Double,
    val fightDurationReduction: Double,
    val ignoredInstabilityEvents: Int,
    val baitHintEnabled: Boolean,
    val candidateCategoryDisplayEnabled: Boolean,
    val matchingCandidateWeightMultiplier: Double,
    val detailedCandidateDisplayEnabled: Boolean,
    val incompatibleBaitWarningEnabled: Boolean,
    val informationMode: FishingInformationMode
) : TypedProfessionProfile {
    override val profession = Profession.FISHER
}

data class BrewerSkillProfile(
    override val level: Int,
    override val specialization: ProfessionSpecialization?,
    val basicRecipeUnlocked: Boolean,
    val intermediateRecipeUnlocked: Boolean,
    val advancedRecipeUnlocked: Boolean,
    val topRecipeUnlocked: Boolean,
    val processingTimeReduction: Double,
    val failurePenaltyReduction: Double,
    val qualityStabilityBonus: Int,
    val materialLossReduction: Double,
    val conditionToleranceBonus: Double,
    val exactConditionQualityBonus: Int,
    val herbalRecipeUnlocked: Boolean,
    val wildAndFungiRecipeUnlocked: Boolean,
    val minimumQuality: Int,
    val fuelReduction: Double,
    val agingTimeReduction: Double,
    val distillationYieldBonus: Int
) : TypedProfessionProfile {
    override val profession = Profession.BREWER
}

data class CookSkillProfile(
    override val level: Int,
    override val specialization: ProfessionSpecialization?,
    val basicRecipeUnlocked: Boolean,
    val intermediateRecipeUnlocked: Boolean,
    val advancedRecipeUnlocked: Boolean,
    val topRecipeUnlocked: Boolean,
    val processingTimeReduction: Double,
    val minimumCompletion: Int,
    val ingredientSlots: Int,
    val mismatchPenaltyReduction: Double,
    val materialSaveChance: Double,
    val extraCompletionChance: Double,
    val seasoningEffectMultiplier: Double,
    val exactMatchCompletionBonus: Int
) : TypedProfessionProfile {
    override val profession = Profession.COOK
}
