package jp.awabi2048.cccontent.features.rank.profession.profile

import jp.awabi2048.cccontent.features.rank.profession.Profession

object TypedProfessionProfileResolver {
    fun resolve(
        profession: Profession,
        level: Int,
        specializationId: String?,
        toggles: ProfessionFeatureToggles = ProfessionFeatureToggles.defaultsFor(profession)
    ): TypedProfessionProfile {
        require(profession.usesTypedProfile) { "${profession.id} does not use a typed profile" }
        val safeLevel = level.coerceIn(0, TypedProfessionLevelCurve.MAX_LEVEL)
        val specialization = resolveSpecialization(profession, safeLevel, specializationId)
        return when (profession) {
            Profession.MINER -> miner(safeLevel, specialization, toggles)
            Profession.LUMBERJACK -> lumberjack(safeLevel, specialization, toggles)
            Profession.FARMER -> farmer(safeLevel, specialization, toggles)
            Profession.FISHER -> fisher(safeLevel, specialization, toggles)
            Profession.BREWER -> brewer(safeLevel, specialization)
            Profession.COOK -> cook(safeLevel, specialization)
            else -> error("unsupported typed profession: ${profession.id}")
        }
    }

    private fun resolveSpecialization(
        profession: Profession,
        level: Int,
        specializationId: String?
    ): ProfessionSpecialization? {
        if (specializationId == null) return null
        require(level >= ProfessionSpecialization.UNLOCK_LEVEL) {
            "specialization requires level ${ProfessionSpecialization.UNLOCK_LEVEL}"
        }
        return requireNotNull(ProfessionSpecialization.fromId(profession, specializationId)) {
            "unknown specialization for ${profession.id}: $specializationId"
        }
    }

    private fun miner(level: Int, spec: ProfessionSpecialization?, toggles: ProfessionFeatureToggles): MinerSkillProfile {
        val tunnel = spec == ProfessionSpecialization.TUNNEL_MINING
        val precision = spec == ProfessionSpecialization.PRECISION_MINING
        return MinerSkillProfile(
            level = level,
            specialization = spec,
            expertOperationUnlocked = level >= 5,
            batchProcessingEnabled = tunnel && level >= 15 && toggles.batchProcessingEnabled,
            maximumBatchSize = when {
                !tunnel -> if (level >= 5) 8 else 1
                level >= 45 -> 24
                level >= 35 -> 20
                level >= 25 -> 16
                else -> 12
            },
            workSpeedLevel = tier(level, 5, 10, 45),
            durabilitySaveChance = stepped(level, 25 to 0.10, 50 to 0.20),
            ordinaryExtraDropChance = stepped(level, 0 to 0.02, 35 to 0.035, 50 to 0.05),
            automaticCollectionEnabled = tunnel && level >= 50,
            optimizedSearchEnabled = tunnel && level >= 50,
            precisionToleranceBonus = if (!precision) 0.0 else stepped(level, 15 to 0.02, 45 to 0.04),
            detailedInspectionEnabled = precision && level >= 25,
            minimumSpecialMaterialStandardEnabled = precision && level >= 35,
            topEvaluationExtraMaterial = if (precision && level >= 50) 1 else 0,
            ignoredMinorFailures = if (precision && level >= 50) 1 else 0
        )
    }

    private fun lumberjack(level: Int, spec: ProfessionSpecialization?, toggles: ProfessionFeatureToggles): LumberjackSkillProfile {
        val felling = spec == ProfessionSpecialization.FELLING
        val utilization = spec == ProfessionSpecialization.WOOD_UTILIZATION
        return LumberjackSkillProfile(
            level = level,
            specialization = spec,
            expertOperationUnlocked = level >= 5,
            batchProcessingEnabled = felling && level >= 15 && toggles.batchProcessingEnabled,
            maximumBatchSize = when {
                !felling -> if (level >= 5) 8 else 1
                level >= 45 -> 24
                level >= 25 -> 16
                else -> 12
            },
            workSpeedLevel = tier(level, 5, 10, 45),
            durabilitySaveChance = stepped(level, 25 to 0.10, 50 to 0.20),
            ordinaryExtraDropChance = stepped(level, 0 to 0.02, 35 to 0.035, 50 to 0.05),
            leafCleanupEnabled = felling && level >= 50 && toggles.leafCleanupEnabled,
            automaticReplantEnabled = felling && level >= 35 && toggles.automaticReplantEnabled,
            multiTrunkRecognitionImproved = felling && level >= 50,
            plankYield = if (!utilization) 0 else if (level >= 45) 6 else 5,
            timberYield = if (!utilization) 0 else if (level >= 45) 2 else 1,
            inspectionRadius = if (utilization && level >= 25) 4 else 0,
            inspectionCooldownSeconds = if (utilization && level >= 25) 45 else 0,
            discoveryChanceBonus = if (utilization && level >= 35) 0.10 else 0.0,
            extraForestProductChance = if (utilization && level >= 50) 0.20 else 0.0,
            exactMaterialInspectionEnabled = utilization && level >= 50
        )
    }

    private fun farmer(level: Int, spec: ProfessionSpecialization?, toggles: ProfessionFeatureToggles): FarmerSkillProfile {
        val cultivation = spec == ProfessionSpecialization.CULTIVATION
        val wild = spec == ProfessionSpecialization.WILD_GATHERING
        return FarmerSkillProfile(
            level = level,
            specialization = spec,
            expertOperationUnlocked = level >= 5,
            cropExtraDropChance = stepped(level, 5 to 0.05, 10 to 0.075, 45 to 0.125),
            byproductChance = stepped(level, 0 to 0.05, 35 to 0.10, 50 to 0.15),
            workSpeedLevel = if (level >= 5) 1 else 0,
            durabilitySaveChance = stepped(level, 25 to 0.10, 50 to 0.20),
            areaTillingEnabled = cultivation && level >= 15 && toggles.areaTillingEnabled,
            areaHarvestEnabled = cultivation && level >= 15 && toggles.areaHarvestEnabled,
            automaticReplantEnabled = cultivation && level >= 25 && toggles.automaticReplantEnabled,
            operationRadius = if (!cultivation) 0 else if (level >= 45) 3 else 2,
            seedReserveEnabled = cultivation && level >= 25,
            matureCropSelectionEnabled = cultivation && level >= 35,
            batchExperiencePenaltyReduced = cultivation && level >= 35,
            seedSaveChance = if (cultivation && level >= 50) 0.20 else 0.0,
            inspectionRadius = if (wild && level >= 15) 4 else 0,
            inspectionCooldownSeconds = when {
                wild && level >= 45 -> 22
                wild -> 45
                else -> 0
            },
            specialistPlantExtraChance = if (!wild) 0.0 else stepped(level, 25 to 0.10, 50 to 0.20),
            detailedInspectionEnabled = wild && level >= 35,
            guaranteedSpecialistPlantYield = if (wild && level >= 50) 1 else 0
        )
    }

    private fun fisher(level: Int, spec: ProfessionSpecialization?, toggles: ProfessionFeatureToggles): FisherSkillProfile {
        val rod = spec == ProfessionSpecialization.ROD_HANDLING
        val knowledge = spec == ProfessionSpecialization.FISHING_GROUND_KNOWLEDGE
        return FisherSkillProfile(
            level = level,
            specialization = spec,
            vanillaExtraCatchChance = if (level >= 0) 0.02 else 0.0,
            waitTimeReduction = stepped(level, 5 to 0.05, 10 to 0.10, 35 to 0.15, 50 to 0.20),
            hookWindowBonus = stepped(level, 5 to 0.10, 25 to 0.15, 45 to 0.20),
            baitSaveChance = stepped(level, 10 to 0.05, 35 to 0.10, 50 to 0.15),
            durabilitySaveChance = stepped(level, 25 to 0.10, 45 to 0.20),
            fightStabilityBonus = if (!rod) 0.0 else stepped(level, 15 to 0.10, 50 to 0.25),
            fightDurationReduction = if (!rod) 0.0 else stepped(level, 25 to 0.10, 45 to 0.20),
            ignoredInstabilityEvents = if (rod && level >= 35) 1 else 0,
            baitHintEnabled = knowledge && level >= 15,
            candidateCategoryDisplayEnabled = knowledge && level >= 25,
            matchingCandidateWeightMultiplier = if (!knowledge) 1.0 else stepped(level, 35 to 1.10, 50 to 1.20),
            detailedCandidateDisplayEnabled = knowledge && level >= 45,
            incompatibleBaitWarningEnabled = knowledge && level >= 50,
            informationMode = toggles.fishingInformationMode
        )
    }

    private fun brewer(level: Int, spec: ProfessionSpecialization?): BrewerSkillProfile {
        val fermentation = spec == ProfessionSpecialization.FERMENTATION
        val distillation = spec == ProfessionSpecialization.DISTILLATION_AGING
        return BrewerSkillProfile(
            level = level,
            specialization = spec,
            basicRecipeUnlocked = true,
            intermediateRecipeUnlocked = spec != null && level >= 15,
            advancedRecipeUnlocked = spec != null && level >= 35,
            topRecipeUnlocked = spec != null && level >= 50,
            processingTimeReduction = stepped(level, 0 to 0.05, 5 to 0.10, 25 to 0.15, 45 to 0.20),
            failurePenaltyReduction = stepped(level, 5 to 0.10, 50 to 0.20),
            qualityStabilityBonus = steppedInt(level, 10 to 3, 35 to 5, 50 to 8),
            materialLossReduction = if (level >= 25) 0.10 else 0.0,
            conditionToleranceBonus = if (spec == null) 0.0 else stepped(level, 15 to 0.10, 35 to 0.20),
            exactConditionQualityBonus = if (!fermentation) 0 else steppedInt(level, 25 to 5, 50 to 10),
            herbalRecipeUnlocked = fermentation && level >= 25,
            wildAndFungiRecipeUnlocked = fermentation && level >= 35,
            minimumQuality = when {
                spec == null -> 0
                level >= 50 -> 85
                level >= 45 -> 75
                else -> 0
            },
            fuelReduction = if (distillation && level >= 25) 0.10 else 0.0,
            agingTimeReduction = if (distillation && level >= 45) 0.10 else 0.0,
            distillationYieldBonus = if (distillation && level >= 50) 1 else 0
        )
    }

    private fun cook(level: Int, spec: ProfessionSpecialization?): CookSkillProfile {
        val bulk = spec == ProfessionSpecialization.BULK_COOKING
        val precision = spec == ProfessionSpecialization.PRECISION_COOKING
        return CookSkillProfile(
            level = level,
            specialization = spec,
            basicRecipeUnlocked = true,
            intermediateRecipeUnlocked = spec != null && level >= 15,
            advancedRecipeUnlocked = spec != null && level >= 35,
            topRecipeUnlocked = spec != null && level >= 50,
            processingTimeReduction = stepped(level, 0 to 0.05, 5 to 0.10, 25 to 0.15, 45 to 0.20),
            minimumCompletion = when {
                bulk && level >= 50 -> 80
                bulk && level >= 45 -> 75
                precision && level >= 50 -> 85
                precision && level >= 45 -> 80
                level >= 50 -> 60
                level >= 5 -> 40
                else -> 0
            },
            ingredientSlots = when {
                level >= 40 -> 5
                level >= 25 -> 4
                level >= 10 -> 3
                else -> 2
            },
            mismatchPenaltyReduction = stepped(level, 35 to 0.10, 50 to 0.20),
            materialSaveChance = if (!bulk) 0.0 else stepped(level, 15 to 0.05, 25 to 0.10, 50 to 0.15),
            extraCompletionChance = if (!bulk) 0.0 else stepped(level, 35 to 0.10, 45 to 0.15, 50 to 0.25),
            seasoningEffectMultiplier = if (precision && level >= 15) 1.10 else 1.0,
            exactMatchCompletionBonus = if (!precision) 0 else steppedInt(level, 25 to 5, 35 to 10, 50 to 15)
        )
    }

    private fun tier(level: Int, vararg thresholds: Int): Int = thresholds.count { level >= it }

    private fun stepped(level: Int, vararg values: Pair<Int, Double>): Double =
        values.filter { level >= it.first }.maxByOrNull { it.first }?.second ?: 0.0

    private fun steppedInt(level: Int, vararg values: Pair<Int, Int>): Int =
        values.filter { level >= it.first }.maxByOrNull { it.first }?.second ?: 0
}
