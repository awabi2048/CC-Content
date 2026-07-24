package jp.awabi2048.cccontent.features.rank.profession.profile

import jp.awabi2048.cccontent.features.rank.profession.Profession

/**
 * 取得済みスキルを、各専門コンテンツが扱う型付きプロフィールへ変換します。
 *
 * レベルは表示用の値であり、能力の解放判定には使用しません。
 */
object TypedProfessionProfileResolver {
    fun resolve(
        profession: Profession,
        level: Int,
        acquiredSkills: Set<String>,
        toggles: ProfessionFeatureToggles = ProfessionFeatureToggles.defaultsFor(profession)
    ): TypedProfessionProfile {
        require(profession.usesTypedAbilityAdapter) { "${profession.id} does not use a typed ability adapter" }
        val skills = acquiredSkills.map(String::lowercase).toSet()
        return when (profession) {
            Profession.MINER -> miner(level, skills, toggles)
            Profession.LUMBERJACK -> lumberjack(level, skills, toggles)
            Profession.FARMER -> farmer(level, skills, toggles)
            Profession.FISHER -> fisher(level, skills, toggles)
            Profession.BREWER -> brewer(level, skills)
            Profession.COOK -> cook(level, skills)
            else -> error("unsupported typed profession: ${profession.id}")
        }
    }

    private fun miner(level: Int, skills: Set<String>, toggles: ProfessionFeatureToggles): MinerSkillProfile {
        val precision = "fortune_2" in skills || "fortune_3" in skills
        val tunnel = !precision && skills.any { it == "speed_2" || it.startsWith("mineall_") || it.startsWith("blastmine_") }
        return MinerSkillProfile(
            level = level,
            specialization = when {
                tunnel -> ProfessionSpecialization.TUNNEL_MINING
                precision -> ProfessionSpecialization.PRECISION_MINING
                else -> null
            },
            expertOperationUnlocked = "mineall_1" in skills,
            batchProcessingEnabled = "mineall_1" in skills && toggles.batchProcessingEnabled,
            maximumBatchSize = when {
                "mineall_3" in skills -> 24
                "mineall_2" in skills -> 16
                "mineall_1" in skills -> 8
                else -> 1
            },
            workSpeedLevel = listOf("speed_1", "speed_2").count { it in skills },
            durabilitySaveChance = if ("durability_1" in skills) 0.10 else 0.0,
            ordinaryExtraDropChance = listOf("fortune_1", "fortune_2", "fortune_3").count { it in skills } * 0.01,
            automaticCollectionEnabled = "mineall_3" in skills,
            optimizedSearchEnabled = "mineall_3" in skills,
            precisionToleranceBonus = if ("fortune_3" in skills) 0.04 else if ("fortune_2" in skills) 0.02 else 0.0,
            topEvaluationThreshold = if ("fortune_3" in skills) 0.88 else 0.90,
            detailedInspectionEnabled = "fortune_2" in skills,
            minimumSpecialMaterialStandardEnabled = "fortune_3" in skills,
            topEvaluationExtraMaterial = if ("fortune_3" in skills) 1 else 0,
            ignoredMinorFailures = if ("fortune_3" in skills) 1 else 0
        )
    }

    private fun lumberjack(level: Int, skills: Set<String>, toggles: ProfessionFeatureToggles): LumberjackSkillProfile {
        val utilization = "harvest_2" in skills || "harvest_3" in skills
        val felling = !utilization && skills.any { it == "speed_2" || it.startsWith("cut_all_") || it == "wind_gust" || it == "replant" }
        return LumberjackSkillProfile(
            level = level,
            specialization = when {
                felling -> ProfessionSpecialization.FELLING
                utilization -> ProfessionSpecialization.WOOD_UTILIZATION
                else -> null
            },
            expertOperationUnlocked = "cut_all_1" in skills,
            batchProcessingEnabled = "cut_all_1" in skills && toggles.batchProcessingEnabled,
            maximumBatchSize = when {
                "cut_all_3" in skills -> 24
                "cut_all_2" in skills -> 16
                "cut_all_1" in skills -> 8
                else -> 1
            },
            workSpeedLevel = listOf("speed_1", "speed_2").count { it in skills },
            durabilitySaveChance = if ("cut_all_3" in skills) 0.20 else if ("cut_all_2" in skills) 0.10 else 0.0,
            ordinaryExtraDropChance = listOf("harvest_1", "harvest_2", "harvest_3").count { it in skills } * 0.01,
            leafCleanupEnabled = "cut_all_3" in skills && toggles.leafCleanupEnabled,
            automaticReplantEnabled = "replant" in skills && toggles.automaticReplantEnabled,
            multiTrunkRecognitionImproved = "cut_all_3" in skills,
            plankYield = if ("harvest_3" in skills) 6 else if ("harvest_2" in skills) 5 else 0,
            timberYield = if ("harvest_3" in skills) 2 else if ("harvest_2" in skills) 1 else 0,
            inspectionRadius = if ("harvest_2" in skills) 4 else 0,
            inspectionCooldownSeconds = if ("harvest_2" in skills) 45 else 0,
            discoveryChanceBonus = if ("harvest_3" in skills) 0.10 else 0.0,
            extraForestProductChance = if ("harvest_3" in skills) 0.20 else 0.0,
            exactMaterialInspectionEnabled = "harvest_3" in skills
        )
    }

    private fun farmer(level: Int, skills: Set<String>, toggles: ProfessionFeatureToggles): FarmerSkillProfile {
        val cultivation = skills.any { it == "area_tilling" || it == "area_harvesting" || it == "auto_replanting" }
        val wild = "tool" in skills
        return FarmerSkillProfile(
            level = level,
            specialization = when {
                wild -> ProfessionSpecialization.WILD_GATHERING
                cultivation -> ProfessionSpecialization.CULTIVATION
                else -> null
            },
            expertOperationUnlocked = "area_tilling" in skills,
            cropExtraDropChance = listOf("harvest_1", "harvest_2", "harvest_3").count { it in skills } * 0.04,
            byproductChance = if ("special_loot_table_2" in skills) 0.15 else if ("special_loot_table_1" in skills) 0.10 else 0.05,
            workSpeedLevel = if ("harvest_1" in skills) 1 else 0,
            durabilitySaveChance = if ("harvest_3" in skills) 0.20 else if ("harvest_2" in skills) 0.10 else 0.0,
            areaTillingEnabled = "area_tilling" in skills && toggles.areaTillingEnabled,
            areaHarvestEnabled = "area_harvesting" in skills && toggles.areaHarvestEnabled,
            automaticReplantEnabled = "auto_replanting" in skills && toggles.automaticReplantEnabled,
            operationRadius = if ("harvest_3" in skills) 3 else if ("harvest_2" in skills) 2 else if ("area_tilling" in skills) 1 else 0,
            seedReserveEnabled = "auto_replanting" in skills,
            matureCropSelectionEnabled = "harvest_2" in skills,
            batchExperiencePenaltyReduced = "harvest_3" in skills,
            seedSaveChance = if ("harvest_3" in skills) 0.20 else 0.0,
            inspectionRadius = if ("tool" in skills) 4 else 0,
            inspectionCooldownSeconds = if ("special_loot_table_2" in skills) 22 else if ("tool" in skills) 45 else 0,
            specialistPlantExtraChance = if ("special_loot_table_2" in skills) 0.20 else if ("special_loot_table_1" in skills) 0.10 else 0.0,
            detailedInspectionEnabled = "special_loot_table_1" in skills,
            guaranteedSpecialistPlantYield = if ("special_loot_table_2" in skills) 1 else 0
        )
    }

    private fun fisher(level: Int, skills: Set<String>, toggles: ProfessionFeatureToggles) = FisherSkillProfile(
        level = level,
        specialization = if ("deep_water" in skills) ProfessionSpecialization.FISHING_GROUND_KNOWLEDGE else null,
        vanillaExtraCatchChance = 0.02,
        waitTimeReduction = if ("master_angler" in skills) 0.20 else if ("patient_cast" in skills) 0.10 else 0.0,
        hookWindowBonus = if ("patient_cast" in skills) 0.20 else 0.0,
        baitSaveChance = if ("master_angler" in skills) 0.15 else 0.0,
        durabilitySaveChance = if ("master_angler" in skills) 0.20 else 0.0,
        fightStabilityBonus = if ("deep_water" in skills) 0.15 else 0.0,
        fightDurationReduction = if ("master_angler" in skills) 0.15 else 0.0,
        ignoredInstabilityEvents = if ("master_angler" in skills) 1 else 0,
        baitHintEnabled = "deep_water" in skills,
        candidateCategoryDisplayEnabled = "deep_water" in skills,
        matchingCandidateWeightMultiplier = if ("master_angler" in skills) 1.20 else 1.0,
        detailedCandidateDisplayEnabled = "master_angler" in skills,
        incompatibleBaitWarningEnabled = "master_angler" in skills,
        informationMode = toggles.fishingInformationMode
    )

    private fun brewer(level: Int, skills: Set<String>) = BrewerSkillProfile(
        level = level,
        specialization = null,
        basicRecipeUnlocked = "skill1" in skills,
        intermediateRecipeUnlocked = "skill2" in skills || "skill3" in skills,
        advancedRecipeUnlocked = "skill7" in skills || "skill8" in skills || "skill9" in skills,
        topRecipeUnlocked = "skill12" in skills,
        processingTimeReduction = listOf("skill5", "skill10").count { it in skills } * 0.10,
        failurePenaltyReduction = if ("skill10" in skills) 0.20 else if ("skill5" in skills) 0.10 else 0.0,
        qualityStabilityBonus = if ("skill11" in skills) 8 else if ("skill8" in skills) 5 else 0,
        materialLossReduction = if ("skill6" in skills) 0.10 else 0.0,
        conditionToleranceBonus = if ("skill7" in skills) 0.20 else 0.0,
        exactConditionQualityBonus = if ("skill8" in skills) 10 else 0,
        herbalRecipeUnlocked = "skill7" in skills,
        wildAndFungiRecipeUnlocked = "skill9" in skills,
        minimumQuality = if ("skill12" in skills) 85 else if ("skill11" in skills) 75 else 0,
        fuelReduction = if ("skill6" in skills) 0.10 else 0.0,
        agingTimeReduction = if ("skill10" in skills) 0.10 else 0.0,
        distillationYieldBonus = if ("skill12" in skills) 1 else 0
    )

    private fun cook(level: Int, skills: Set<String>) = CookSkillProfile(
        level = level,
        specialization = null,
        basicRecipeUnlocked = "skill1" in skills,
        intermediateRecipeUnlocked = "skill2" in skills || "skill4" in skills,
        advancedRecipeUnlocked = "skill6" in skills || "skill8" in skills,
        topRecipeUnlocked = "skill9" in skills,
        processingTimeReduction = if ("skill8" in skills) 0.20 else if ("skill3" in skills) 0.10 else 0.0,
        minimumCompletion = if ("skill9" in skills) 85 else if ("skill8" in skills) 75 else if ("skill1" in skills) 40 else 0,
        ingredientSlots = if ("skill9" in skills) 5 else if ("skill6" in skills) 4 else if ("skill2" in skills) 3 else 2,
        mismatchPenaltyReduction = if ("skill9" in skills) 0.20 else if ("skill7" in skills) 0.10 else 0.0,
        materialSaveChance = if ("skill5" in skills) 0.15 else 0.0,
        extraCompletionChance = if ("skill8" in skills) 0.25 else 0.0,
        seasoningEffectMultiplier = if ("skill4" in skills) 1.10 else 1.0,
        exactMatchCompletionBonus = if ("skill7" in skills) 15 else if ("skill6" in skills) 10 else 0
    )
}
