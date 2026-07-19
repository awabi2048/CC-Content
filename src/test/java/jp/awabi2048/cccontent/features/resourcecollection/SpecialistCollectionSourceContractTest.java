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
        assertTrue(source.contains("ResourceOperation.MINER_BATCH"));
        assertTrue(source.contains("ResourceOperation.LUMBERJACK_BATCH"));
        assertTrue(source.contains("ResourceOperation.LUMBERJACK_WORK_SPEED"));
        assertTrue(source.contains("ResourceOperation.LUMBERJACK_HEARTWOOD"));
        assertTrue(source.contains("ResourceOperation.LUMBERJACK_BARK"));
        assertTrue(source.contains("ResourceOperation.LUMBERJACK_TIMBER_PROCESSING"));
        assertTrue(source.contains("ResourceOperation.LUMBERJACK_FOREST_PRODUCTS"));
        assertTrue(source.contains("ResourceOperation.LUMBERJACK_LEAF_CLEANUP"));
        assertTrue(source.contains("ResourceOperation.LUMBERJACK_AUTOMATIC_REPLANT"));
        assertTrue(source.contains("ResourceOperation.FARMER_WILD_GATHERING"));
        assertTrue(source.contains("ResourceOperation.FARMER_WORK_SPEED"));
        assertTrue(source.contains("ResourceOperation.FARMER_SURFACE_GATHERING"));
        assertTrue(source.contains("ResourceOperation.FARMER_AREA_TILLING"));
        assertTrue(source.contains("ResourceOperation.FARMER_AREA_HARVEST"));
        assertTrue(source.contains("ResourceOperation.FARMER_AUTOMATIC_REPLANT"));
        assertTrue(source.contains("MineralCompanionPolicy.inspect"));
        assertTrue(source.contains("event.isCancelled = true"));
        assertTrue(source.contains(".coerceIn(1, 24)"));
        assertTrue(source.contains("ProfessionExperience.batchExperience(processed)"));
        assertTrue(source.contains("profile.batchProcessingEnabled"));
        assertTrue(source.contains("profile.automaticCollectionEnabled"));
        assertTrue(source.contains("profile.optimizedSearchEnabled"));
        assertTrue(source.contains("ChiselAttemptPolicy.evaluate"));
        assertTrue(source.contains("profile.topEvaluationThreshold"));
        assertTrue(source.contains("PotionEffectType.HASTE"));
        assertTrue(source.contains("getPotionEffect(PotionEffectType.HASTE) != null"));
        assertTrue(source.contains("giveResource(player, \"heartwood\", 1)"));
        assertTrue(source.contains("profile.leafCleanupEnabled"));
        assertTrue(source.contains("profile.automaticReplantEnabled"));
        assertTrue(source.contains("BlockPlaceEvent("));
        assertTrue(source.contains("markPlayerPlaced(root)"));
        assertTrue(source.contains("resource.gathering_guide"));
        assertTrue(source.contains("resource.gathering_sickle"));
        assertTrue(source.contains("handleSurfaceGathering"));
        assertTrue(source.contains("surfaceGatheringStore.claim"));
        assertTrue(source.contains("\"surface_gathering\" to target.surface.toString()"));
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
        assertFalse(source.contains("resource_collection.inspection.basic"));
        assertFalse(source.contains("resource_collection.inspection.detailed"));
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
    void rankNormalExperienceSkipsSyntheticSpecialistBreakEvents() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/kotlin/jp/awabi2048/cccontent/features/rank/job/ProfessionMinerExpListener.kt"
        ));
        assertTrue(source.contains("SpecialistCollectionService.isInternalBreak"));
    }
}
