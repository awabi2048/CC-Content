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
        assertTrue(source.contains("resource.geology_guide"));
        assertTrue(source.contains("inspectMineralVein"));
        assertTrue(source.contains("ResourceOperation.MINER_INSPECTION"));
        assertTrue(source.contains("ResourceOperation.MINER_WORK_SPEED"));
        assertTrue(source.contains("ResourceOperation.MINER_CHISEL"));
        assertFalse(source.contains("ResourceOperation.MINER_BATCH"));
        assertFalse(source.contains("ResourceOperation.LUMBERJACK_BATCH"));
        assertFalse(source.contains("resource.mining_hammer"));
        assertFalse(source.contains("resource.felling_axe"));
        assertFalse(source.contains("resource.cultivation_hoe"));
        assertTrue(source.contains("ResourceOperation.LUMBERJACK_WORK_SPEED"));
        assertTrue(source.contains("ResourceOperation.LUMBERJACK_BARK"));
        assertTrue(source.contains("ResourceOperation.LUMBERJACK_TIMBER_PROCESSING"));
        assertTrue(source.contains("ResourceOperation.LUMBERJACK_FOREST_PRODUCTS"));
        assertTrue(source.contains("ResourceOperation.FARMER_WILD_GATHERING"));
        assertTrue(source.contains("ResourceOperation.FARMER_WORK_SPEED"));
        assertFalse(source.contains("ResourceOperation.FARMER_SURFACE_GATHERING"));
        assertTrue(source.contains("ResourceOperation.FARMER_AREA_TILLING"));
        assertTrue(source.contains("ResourceOperation.FARMER_AREA_HARVEST"));
        assertTrue(source.contains("ResourceOperation.FARMER_AUTOMATIC_REPLANT"));
        assertTrue(source.contains("MineralCompanionPolicy.inspect"));
        assertTrue(source.contains("event.isCancelled = true"));
        assertTrue(source.contains("ProfessionExperience.batchExperience(processed)"));
        assertTrue(source.contains("ChiselAttemptPolicy.evaluate"));
        assertTrue(source.contains("profile.topEvaluationThreshold"));
        assertTrue(source.contains("PotionEffectType.HASTE"));
        assertTrue(source.contains("getPotionEffect(PotionEffectType.HASTE) != null"));
        assertTrue(source.contains("dropCustomItemNaturally"));
        assertTrue(source.contains("dropItemNaturally"));
        assertTrue(source.contains("Particle.BLOCK"));
        assertTrue(source.contains("blockData.soundGroup"));
        assertTrue(source.contains("resource.gathering_guide"));
        assertTrue(source.contains("resource.gathering_sickle"));
        assertFalse(source.contains("handleSurfaceGathering"));
        assertFalse(source.contains("surfaceGatheringStore.claim"));
        assertTrue(source.contains("resource.forest_guide"));
        assertTrue(source.contains("resource.woodworking_knife"));
        assertTrue(source.contains("forestTargets[player.uniqueId] = targets"));
        assertTrue(source.contains("ForestProductHarvestStore"));
        assertTrue(source.contains("forestProducts.resolve"));
        assertTrue(source.contains("block.type = stripped"));
        assertTrue(source.contains("gatheringTargets[player.uniqueId] = targets"));
        assertTrue(source.contains("getSeasonService().currentSeason()"));
        assertTrue(source.contains("profile.detailedInspectionEnabled"));
        assertTrue(source.contains("GuiLoreSpec.Blocks"));
        assertTrue(source.contains("resource_collection.display.heading.mineral"));
        assertTrue(source.contains("resource_collection.display.heading.forest"));
        assertTrue(source.contains("resource_collection.display.heading.vegetation"));
        assertTrue(source.contains("resource_collection.display.data.collectible_items"));
        assertTrue(source.contains("sendCustomCollectionResult("));
        assertTrue(source.contains("sendCountCollectionResult("));
        assertTrue(source.contains("\"custom_items.$customItemId.name\""));
        assertTrue(source.contains("\"custom_items.$customItemId.description\""));
        assertTrue(source.contains("resource_collection.display.data.amount"));
        assertTrue(source.contains("resource_collection.display.data.processed_count"));
        assertTrue(source.contains("resource_collection.display.hint.vegetation_none"));
        assertTrue(source.contains("resource_collection.display.hint.forest_none"));
        assertTrue(source.contains("resource_collection.display.hint.forest_harvested"));
        assertTrue(source.contains(
            "player.sendMessage(message(player, \"resource_collection.display.hint.vegetation_none\"))"
        ));
        assertTrue(source.contains(
            "player.sendMessage(message(player, \"resource_collection.display.hint.forest_none\"))"
        ));
        assertTrue(source.contains("player.sendMessage(message(player, emptyResultKey))"));
        assertTrue(source.contains("sendSubjectiveFeedback("));
        assertTrue(source.contains("feedbackTimestamps"));
        assertFalse(source.contains("resource_collection.inspection.basic"));
        assertFalse(source.contains("resource_collection.inspection.detailed"));
        assertFalse(source.contains("resource_collection.error."));
        assertFalse(source.contains("resource_collection.gathering.cooldown"));
        assertFalse(source.contains("resource_collection.forest.cooldown"));
        assertFalse(source.contains("resource_collection.gathering.no_discoveries"));
        assertFalse(source.contains("resource_collection.forest.no_discoveries"));
        assertFalse(source.contains("resource_collection.chisel.started"));
        assertFalse(source.contains("resource_collection.chisel.cancelled"));
        assertFalse(source.contains("resource_collection.chisel.timeout"));
        assertTrue(source.contains("definition.useNameKey"));
        assertTrue(source.contains("definition.vegetationGroupNameKey"));
        assertFalse(source.contains("now().monthValue"));
        assertTrue(source.contains("profile.areaTillingEnabled"));
        assertTrue(source.contains("profile.areaHarvestEnabled"));
        assertTrue(source.contains("profile.automaticReplantEnabled"));
        assertTrue(source.contains("profile.seedReserveEnabled"));
        assertTrue(source.contains("ContentActionType.SOIL_TILLED"));
        assertTrue(source.contains("callProtectedInteract(block, player)"));
        assertTrue(source.contains("getNaturalOriginRegistry().markPlayerPlaced(block)"));
        assertFalse(source.contains("Enchantment.FORTUNE"));
        assertFalse(normalRewards.contains("addProfessionExp"));
        assertFalse(normalRewards.contains("CraftItemEvent"));
    }

    @Test
    void batchBreakingBelongsToTheRankSkillHandler() throws Exception {
        String handler = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/rank/skill/handlers/UnlockBatchBreakHandler.kt"
        ));
        String listener = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/rank/skill/listeners/BlockBreakEffectListener.kt"
        ));
        String items = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/resourcecollection/ResourceCollectionItems.kt"
        ));

        assertTrue(listener.contains("UnlockBatchBreakHandler.applyTypedProfile(event, typedProfile)"));
        assertTrue(handler.contains("profile.batchProcessingEnabled"));
        assertTrue(handler.contains("profile.maximumBatchSize"));
        assertTrue(handler.contains("profile.automaticCollectionEnabled"));
        assertTrue(handler.contains("profile.leafCleanupEnabled"));
        assertTrue(handler.contains("profile.automaticReplantEnabled"));
        assertTrue(handler.contains("ReplantHandler.replantBatch"));
        assertTrue(handler.contains("BatchBreakMode.fromTool(player.inventory.itemInMainHand.type)"));
        assertFalse(handler.contains("MineAll:"));
        assertFalse(handler.contains("CutAll:"));
        assertFalse(items.contains("Definition(\"mining_hammer\""));
        assertFalse(items.contains("Definition(\"felling_axe\""));
        assertFalse(items.contains("Definition(\"cultivation_hoe\""));
    }

    @Test
    void rankNormalExperienceSkipsSyntheticSpecialistBreakEvents() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/rank/job/ProfessionMinerExpListener.kt"
        ));
        assertTrue(source.contains("SpecialistCollectionService.isInternalBreak"));
    }

    @Test
    void emptyAppraisalsShowOnlyTheSubjectiveResult() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/resourcecollection/SpecialistCollectionService.kt"
        )).replace("\r\n", "\n");
        String vegetation = source.substring(
            source.indexOf("private fun inspectVegetation"),
            source.indexOf("fun onGatheringDrops")
        );
        String forest = source.substring(
            source.indexOf("private fun inspectForestProducts"),
            source.indexOf("private fun tryHarvestForestProduct")
        );

        assertTrue(vegetation.contains(
            "if (targets.isEmpty()) {\n" +
                "            player.sendMessage(message(player, \"resource_collection.display.hint.vegetation_none\"))"
        ));
        assertTrue(
            vegetation.indexOf("if (targets.isEmpty())") <
                vegetation.indexOf("sendAppraisal(player, \"resource_collection.display.heading.vegetation\"")
        );
        assertTrue(forest.contains("player.sendMessage(message(player, emptyResultKey))"));
        assertTrue(forest.contains(
            "if (targets.isEmpty()) {\n" +
                "            player.sendMessage(message(player, \"resource_collection.display.hint.forest_none\"))"
        ));
    }
}
