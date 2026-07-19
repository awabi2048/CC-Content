package jp.awabi2048.cccontent.features.resourcecollection

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.action.ContentAction
import com.awabi2048.ccsystem.api.action.ContentActionType
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.RankReleasePolicy
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.profile.LumberjackSkillProfile
import jp.awabi2048.cccontent.features.rank.profession.profile.MinerSkillProfile
import jp.awabi2048.cccontent.features.rank.profession.profile.FarmerSkillProfile
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionExperience
import jp.awabi2048.cccontent.items.CustomItemManager
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.Random
import java.util.ArrayDeque
import java.util.UUID
import java.time.Instant

class SpecialistCollectionService(
    private val plugin: JavaPlugin,
    private val rankManager: RankManager,
    private val settings: ResourceCollectionSettings,
    private val seasonalPlants: SeasonalPlantRegistry,
    private val forestProducts: ForestProductRegistry,
    private val random: Random = Random()
) : Listener {
    companion object {
        private val internalBreaks = mutableSetOf<String>()
        private val internalInteractions = mutableSetOf<String>()

        fun isInternalBreak(playerId: UUID, block: Block): Boolean =
            internalBreaks.contains(internalBreakKey(playerId, block))

        private fun internalBreakKey(playerId: UUID, block: Block): String =
            "$playerId:${block.world.uid}:${block.x}:${block.y}:${block.z}"

        private fun internalInteractionKey(playerId: UUID, block: Block): String =
            "$playerId:${block.world.uid}:${block.x}:${block.y}:${block.z}"
    }
    private data class BlockKey(val worldId: UUID, val x: Int, val y: Int, val z: Int)
    private data class ChiselSession(
        val playerId: UUID,
        val blockKey: BlockKey,
        val originalMaterial: Material,
        val face: BlockFace,
        val startedAt: Location,
        var target: Location,
        var attempts: Int = 0,
        var scoreTotal: Double = 0.0,
        var ignoredFailures: Int = 0,
        var timeout: BukkitTask? = null
    )
    private data class GatheringTarget(
        val customItemId: String,
        val definitionId: String,
        val expiresAt: Instant,
        val surface: Boolean
    )
    private data class ForestProductTarget(
        val customItemId: String,
        val definitionId: String,
        val expiresAt: Instant,
        val root: BlockKey,
        val species: TreeSpecies,
        val targetKind: ForestProductTargetKind
    )

    private val chiselSessions = mutableMapOf<UUID, ChiselSession>()
    private val occupiedBlocks = mutableMapOf<BlockKey, UUID>()
    private val gatheringTargets = mutableMapOf<UUID, MutableMap<BlockKey, GatheringTarget>>()
    private val gatheringCooldowns = mutableMapOf<UUID, Instant>()
    private val forestTargets = mutableMapOf<UUID, MutableMap<BlockKey, ForestProductTarget>>()
    private val forestCooldowns = mutableMapOf<UUID, Instant>()
    private val feedbackTimestamps = mutableMapOf<Pair<UUID, String>, Instant>()
    private val tillableMaterials = setOf(Material.DIRT, Material.GRASS_BLOCK, Material.DIRT_PATH)
    private val surfaceGatheringStore = SurfaceGatheringStore(
        plugin.dataFolder.resolve("data/resource_collection/surface_gathering.yml")
    )
    private val forestProductHarvestStore = ForestProductHarvestStore(
        plugin.dataFolder.resolve("data/resource_collection/forest_product_harvests.yml")
    )

    fun shutdown() {
        chiselSessions.keys.toList().forEach(::cancelChisel)
        occupiedBlocks.clear()
        gatheringTargets.clear()
        gatheringCooldowns.clear()
        forestTargets.clear()
        forestCooldowns.clear()
        feedbackTimestamps.clear()
        surfaceGatheringStore.save()
        forestProductHarvestStore.save()
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onProfessionWorkSpeed(event: BlockDamageEvent) {
        val kind = ResourceMaterialPolicy.classify(event.block.type, event.block.blockData) ?: return
        val heldMaterial = event.player.inventory.itemInMainHand.type
        val profile = rankManager.getTypedProfessionProfile(event.player.uniqueId)
        val (tier, operation) = when {
            kind == ResourceCollectionKind.MINERAL && heldMaterial.name.endsWith("_PICKAXE") &&
                profile is MinerSkillProfile ->
                profile.workSpeedLevel to ResourceOperation.MINER_WORK_SPEED
            kind == ResourceCollectionKind.FOREST && heldMaterial.name.endsWith("_AXE") &&
                profile is LumberjackSkillProfile ->
                profile.workSpeedLevel to ResourceOperation.LUMBERJACK_WORK_SPEED
            kind == ResourceCollectionKind.CROP && heldMaterial.name.endsWith("_HOE") &&
                profile is FarmerSkillProfile ->
                profile.workSpeedLevel to ResourceOperation.FARMER_WORK_SPEED
            else -> return
        }
        if (tier <= 0 || !settings.isOperationEnabled(operation)) return
        if (event.player.getPotionEffect(PotionEffectType.HASTE) != null) return
        event.player.addPotionEffect(
            PotionEffect(PotionEffectType.HASTE, 40, tier - 1, false, false, false)
        )
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChiselBlockDamage(event: BlockDamageEvent) {
        val customId = CustomItemManager.identify(event.player.inventory.itemInMainHand)?.fullId
        val shouldCancel = when (customId) {
            "resource.chisel" -> settings.isOperationEnabled(ResourceOperation.MINER_CHISEL)
            "resource.mining_hammer" ->
                settings.isOperationEnabled(ResourceOperation.MINER_BATCH) &&
                (rankManager.getTypedProfessionProfile(event.player.uniqueId) as? MinerSkillProfile)
                    ?.batchProcessingEnabled == true &&
                    ResourceMaterialPolicy.classify(event.block.type, event.block.blockData) == ResourceCollectionKind.MINERAL
            "resource.felling_axe" ->
                settings.isOperationEnabled(ResourceOperation.LUMBERJACK_BATCH) &&
                (rankManager.getTypedProfessionProfile(event.player.uniqueId) as? LumberjackSkillProfile)
                    ?.batchProcessingEnabled == true &&
                    ResourceMaterialPolicy.classify(event.block.type, event.block.blockData) == ResourceCollectionKind.FOREST
            "resource.cultivation_hoe" ->
                settings.isOperationEnabled(ResourceOperation.FARMER_AREA_HARVEST) &&
                (rankManager.getTypedProfessionProfile(event.player.uniqueId) as? FarmerSkillProfile)
                    ?.areaHarvestEnabled == true &&
                    ResourceMaterialPolicy.classify(event.block.type, event.block.blockData) == ResourceCollectionKind.CROP
            else -> false
        }
        if (shouldCancel) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSpecialistInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val block = event.clickedBlock ?: return
        if (internalInteractions.contains(internalInteractionKey(event.player.uniqueId, block))) return
        val customId = CustomItemManager.identify(event.player.inventory.itemInMainHand)?.fullId
        if (event.action == Action.LEFT_CLICK_BLOCK && customId == "resource.chisel" &&
            settings.isOperationEnabled(ResourceOperation.MINER_CHISEL)) {
            handleChiselClick(event, block)
            return
        }
        if (event.action == Action.LEFT_CLICK_BLOCK && customId == "resource.mining_hammer" &&
            settings.isOperationEnabled(ResourceOperation.MINER_BATCH)) {
            handleBatchBreak(event, block, ResourceCollectionKind.MINERAL)
            return
        }
        if (event.action == Action.LEFT_CLICK_BLOCK && customId == "resource.felling_axe" &&
            settings.isOperationEnabled(ResourceOperation.LUMBERJACK_BATCH)) {
            handleBatchBreak(event, block, ResourceCollectionKind.FOREST)
            return
        }
        if (event.action == Action.LEFT_CLICK_BLOCK && customId == "resource.cultivation_hoe" &&
            settings.isOperationEnabled(ResourceOperation.FARMER_AREA_HARVEST)) {
            handleAreaHarvest(event, block)
            return
        }
        if (event.action != Action.RIGHT_CLICK_BLOCK || !event.player.isSneaking) return
        when (customId) {
            "resource.geology_guide" ->
                ifEnabled(ResourceOperation.MINER_INSPECTION) { inspectMineralVein(event, block) }
            "resource.woodworking_hatchet" ->
                ifEnabled(ResourceOperation.LUMBERJACK_TIMBER_PROCESSING) {
                    processPlacedLog(event, block, precise = false)
                }
            "resource.woodworking_knife" ->
                if (!tryHarvestForestProduct(event, block)) {
                    ifEnabled(ResourceOperation.LUMBERJACK_TIMBER_PROCESSING) {
                        processPlacedLog(event, block, precise = true)
                    }
                }
            "resource.forest_guide" ->
                ifEnabled(ResourceOperation.LUMBERJACK_FOREST_PRODUCTS) { inspectForestProducts(event, block) }
            "resource.gathering_guide" ->
                ifEnabled(ResourceOperation.FARMER_WILD_GATHERING) { inspectVegetation(event, block) }
            "resource.gathering_sickle" ->
                ifEnabled(ResourceOperation.FARMER_SURFACE_GATHERING) { handleSurfaceGathering(event, block) }
            "resource.cultivation_hoe" ->
                ifEnabled(ResourceOperation.FARMER_AREA_TILLING) { handleAreaTilling(event, block) }
            else -> ifEnabled(ResourceOperation.LUMBERJACK_BARK) { scheduleBarkReward(event, block) }
        }
    }

    private inline fun ifEnabled(operation: ResourceOperation, action: () -> Unit) {
        if (settings.isOperationEnabled(operation)) action()
    }

    private fun inspectMineralVein(event: PlayerInteractEvent, block: Block) {
        event.isCancelled = true
        val player = event.player
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? MinerSkillProfile
        if (profile == null || !profile.expertOperationUnlocked ||
            !RankReleasePolicy.canAccessProfession(player, Profession.MINER)) {
            return
        }
        if (!isReadyNatural(block) ||
            ResourceMaterialPolicy.classify(block.type, block.blockData) != ResourceCollectionKind.MINERAL) {
            return
        }
        val result = MineralCompanionPolicy.inspect(
            block.world.environment,
            block.biome.key.toString(),
            block.y
        )
        player.swingMainHand()
        val materialHint = if (profile.detailedInspectionEnabled) {
            CCSystem.getAPI().getI18nString(player, "custom_items.resource.${result.resourceId}.name")
        } else {
            text(player, "resource_collection.display.hint.companion_minerals")
        }
        sendAppraisal(
            player,
            "resource_collection.display.heading.mineral",
            listOf(
                GuiLoreLine.Data(
                    text(player, "resource_collection.display.data.altitude"),
                    localizedEnum(player, "resource_collection.inspection.altitude", result.altitude.name),
                    "§f"
                ),
                GuiLoreLine.Data(
                    text(player, "resource_collection.display.data.region"),
                    localizedEnum(player, "resource_collection.inspection.biome", result.biome.name),
                    "§f"
                ),
                GuiLoreLine.Data(
                    text(player, "resource_collection.display.data.collectible_items"),
                    materialHint,
                    "§a"
                )
            )
        )
        player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 0.75f, 0.9f)
    }

    private fun localizedEnum(player: Player, prefix: String, value: String): String =
        CCSystem.getAPI().getI18nString(player, "$prefix.${value.lowercase()}")

    private fun handleAreaHarvest(event: PlayerInteractEvent, origin: Block) {
        val player = event.player
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? FarmerSkillProfile ?: return
        if (!profile.areaHarvestEnabled ||
            ResourceMaterialPolicy.classify(origin.type, origin.blockData) != ResourceCollectionKind.CROP) return
        event.isCancelled = true
        if (!isReadyWorld(origin)) {
            return
        }
        val originMaterial = origin.type
        val radius = profile.operationRadius.coerceIn(1, 3)
        val candidates = buildList {
            for (x in -radius..radius) for (z in -radius..radius) {
                val block = origin.getRelative(x, 0, z)
                if (ResourceMaterialPolicy.classify(block.type, block.blockData) == ResourceCollectionKind.CROP) {
                    add(block)
                }
            }
        }
        var processed = 0
        for (block in candidates) {
            val cropType = block.type
            if (!callProtectedBreak(block, player)) continue
            playNaturalBreakEffect(block)
            block.breakNaturally(ItemStack(Material.IRON_HOE), true)
            if (settings.isOperationEnabled(ResourceOperation.FARMER_AUTOMATIC_REPLANT) &&
                profile.automaticReplantEnabled) {
                replantCrop(player, block, cropType, profile)
            }
            processed++
        }
        if (processed <= 0) return
        player.swingMainHand()
        damageCultivationTool(player, processed, profile.durabilitySaveChance)
        awardFarmerArea(player, ContentActionType.CROP_HARVESTED, processed, originMaterial, "harvest")
        sendCountCollectionResult(
            player,
            "resource_collection.cultivation.harvested",
            "resource_collection.display.heading.harvest_result",
            processed
        )
    }

    private fun handleAreaTilling(event: PlayerInteractEvent, origin: Block) {
        val player = event.player
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? FarmerSkillProfile ?: return
        if (!profile.areaTillingEnabled || origin.type !in tillableMaterials) return
        event.isCancelled = true
        if (!isReadyWorld(origin)) {
            return
        }
        val originMaterial = origin.type
        val radius = profile.operationRadius.coerceIn(1, 3)
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!player.isOnline ||
                CustomItemManager.identify(player.inventory.itemInMainHand)?.fullId != "resource.cultivation_hoe") {
                return@Runnable
            }
            var processed = 0
            for (x in -radius..radius) for (z in -radius..radius) {
                val block = origin.getRelative(x, 0, z)
                if (block.type !in tillableMaterials || block.getRelative(BlockFace.UP).type != Material.AIR) continue
                if (!callProtectedInteract(block, player)) continue
                playNaturalBreakEffect(block, particleCount = 4)
                block.type = Material.FARMLAND
                CCSystem.getAPI().getNaturalOriginRegistry().markPlayerPlaced(block)
                processed++
            }
            if (processed <= 0) return@Runnable
            player.swingMainHand()
            damageCultivationTool(player, processed, profile.durabilitySaveChance)
            awardFarmerArea(player, ContentActionType.SOIL_TILLED, processed, originMaterial, "tilling")
            sendCountCollectionResult(
                player,
                "resource_collection.cultivation.tilled",
                "resource_collection.display.heading.tilling_result",
                processed
            )
        })
    }

    private fun replantCrop(player: Player, block: Block, cropType: Material, profile: FarmerSkillProfile) {
        val seed = cropSeed(cropType) ?: return
        if (!consumeSeedForReplant(player, seed, profile)) return
        block.type = cropType
        val data = block.blockData as? org.bukkit.block.data.Ageable ?: return
        data.age = 0
        block.blockData = data
        CCSystem.getAPI().getNaturalOriginRegistry().markPlayerPlaced(block)
    }

    private fun consumeSeedForReplant(player: Player, seed: Material, profile: FarmerSkillProfile): Boolean {
        if (player.gameMode == GameMode.CREATIVE) return true
        val slot = player.inventory.contents.indexOfFirst { it?.type == seed && it.amount > 0 }
        if (slot < 0) return false
        val stack = player.inventory.getItem(slot) ?: return false
        if (profile.seedReserveEnabled && stack.amount <= 1) return false
        if (random.nextDouble() < profile.seedSaveChance) return true
        if (stack.amount <= 1) player.inventory.setItem(slot, null) else stack.amount -= 1
        return true
    }

    private fun damageCultivationTool(player: Player, count: Int, saveChance: Double) {
        if (player.gameMode == GameMode.CREATIVE) return
        repeat(count) {
            if (random.nextDouble() >= saveChance) player.damageItemStack(EquipmentSlot.HAND, 1)
        }
    }

    private fun awardFarmerArea(
        player: Player,
        actionType: ContentActionType,
        processed: Int,
        material: Material,
        operation: String
    ) {
        val experience = ProfessionExperience.batchExperience(processed)
        rankManager.addProfessionExp(player.uniqueId, experience)
        rankManager.recordProfessionCycleAction(player.uniqueId)
        CCSystem.getAPI().getContentActionDispatcher().publish(
            ContentAction(
                actionId = UUID.randomUUID(),
                schemaVersion = 1,
                occurredAt = CCSystem.getAPI().getSharedClockService().now().toInstant(),
                playerId = player.uniqueId,
                actionType = actionType,
                amount = processed.toLong(),
                worldKey = player.world.key,
                metadata = mapOf(
                    "material" to material.key.toString(),
                    "operation" to operation,
                    "experience" to experience.toString()
                )
            )
        )
    }

    private fun callProtectedInteract(block: Block, player: Player): Boolean {
        val key = internalInteractionKey(player.uniqueId, block)
        internalInteractions.add(key)
        return try {
            val event = PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_BLOCK,
                player.inventory.itemInMainHand,
                block,
                BlockFace.UP,
                EquipmentSlot.HAND
            )
            Bukkit.getPluginManager().callEvent(event)
            event.useInteractedBlock() != Event.Result.DENY &&
                event.useItemInHand() != Event.Result.DENY
        } finally {
            internalInteractions.remove(key)
        }
    }

    private fun handleBatchBreak(
        event: PlayerInteractEvent,
        origin: Block,
        expectedKind: ResourceCollectionKind
    ) {
        val player = event.player
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId)
        val enabled = when {
            expectedKind == ResourceCollectionKind.MINERAL && profile is MinerSkillProfile ->
                profile.batchProcessingEnabled
            expectedKind == ResourceCollectionKind.FOREST && profile is LumberjackSkillProfile ->
                profile.batchProcessingEnabled
            else -> false
        }
        if (!enabled || ResourceMaterialPolicy.classify(origin.type, origin.blockData) != expectedKind) return
        event.isCancelled = true
        if (!isReadyNatural(origin)) {
            return
        }
        val originalMaterial = origin.type
        val treeRoot = expectedKind == ResourceCollectionKind.FOREST && isTreeRoot(origin)
        val maximum = when (profile) {
            is MinerSkillProfile -> profile.maximumBatchSize
            is LumberjackSkillProfile -> profile.maximumBatchSize
            else -> 1
        }.coerceIn(1, 24)
        val targets = collectConnectedNatural(
            origin,
            expectedKind,
            maximum,
            includeDiagonalTrunks = profile is LumberjackSkillProfile && profile.multiTrunkRecognitionImproved,
            optimizedSearch = profile is MinerSkillProfile && profile.optimizedSearchEnabled
        )
        val leaves = if (expectedKind == ResourceCollectionKind.FOREST &&
            settings.isOperationEnabled(ResourceOperation.LUMBERJACK_LEAF_CLEANUP) &&
            profile is LumberjackSkillProfile && profile.leafCleanupEnabled) {
            collectTreeLeaves(origin, targets, maximum = 256)
        } else {
            emptyList()
        }
        var processed = 0
        for (block in targets) {
            if (!callProtectedBreak(block, player)) continue
            val tool = if (expectedKind == ResourceCollectionKind.MINERAL) {
                ItemStack(Material.IRON_PICKAXE)
            } else {
                ItemStack(Material.IRON_AXE)
            }
            val automaticCollection = profile is MinerSkillProfile && profile.automaticCollectionEnabled
            if (breakBatchBlock(block, tool, player, automaticCollection)) processed++
        }
        if (processed <= 0) {
            return
        }
        player.swingMainHand()
        val completeTreeBatch = expectedKind == ResourceCollectionKind.FOREST && processed == targets.size
        if (treeRoot && completeTreeBatch &&
            settings.isOperationEnabled(ResourceOperation.LUMBERJACK_HEARTWOOD)) {
            dropResourceNaturally(player, "heartwood", 1, origin.location)
        }
        if (completeTreeBatch && profile is LumberjackSkillProfile) {
            if (settings.isOperationEnabled(ResourceOperation.LUMBERJACK_LEAF_CLEANUP) &&
                profile.leafCleanupEnabled) {
                cleanupTreeLeaves(player, leaves)
            }
            if (settings.isOperationEnabled(ResourceOperation.LUMBERJACK_AUTOMATIC_REPLANT) &&
                profile.automaticReplantEnabled && treeRoot) {
                replantTree(player, origin, originalMaterial)
            }
        }
        val durabilitySaveChance = when (profile) {
            is MinerSkillProfile -> profile.durabilitySaveChance
            is LumberjackSkillProfile -> profile.durabilitySaveChance
            else -> 0.0
        }
        if (player.gameMode != GameMode.CREATIVE) {
            repeat(processed) {
                if (random.nextDouble() >= durabilitySaveChance) player.damageItemStack(EquipmentSlot.HAND, 1)
            }
        }
        awardBatch(player, expectedKind, processed, originalMaterial)
        sendCountCollectionResult(
            player,
            "resource_collection.batch.completed",
            if (expectedKind == ResourceCollectionKind.MINERAL) {
                "resource_collection.display.heading.mineral_batch_result"
            } else {
                "resource_collection.display.heading.forest_batch_result"
            },
            processed
        )
    }

    private fun breakBatchBlock(
        block: Block,
        tool: ItemStack,
        player: Player,
        automaticCollection: Boolean
    ): Boolean {
        playNaturalBreakEffect(block)
        if (!automaticCollection) return block.breakNaturally(tool, true)
        val drops = block.getDrops(tool, player).map(ItemStack::clone)
        block.type = Material.AIR
        drops.forEach { item ->
            player.inventory.addItem(item).values.forEach { overflow ->
                player.world.dropItemNaturally(player.location, overflow)
            }
        }
        return true
    }

    private fun collectConnectedNatural(
        origin: Block,
        kind: ResourceCollectionKind,
        maximum: Int,
        includeDiagonalTrunks: Boolean = false,
        optimizedSearch: Boolean = false
    ): List<Block> {
        val queue = ArrayDeque<Block>()
        val visited = mutableSetOf<BlockKey>()
        val result = mutableListOf<Block>()
        queue.add(origin)
        while (queue.isNotEmpty() && result.size < maximum) {
            val block = queue.removeFirst()
            if (!visited.add(block.key())) continue
            if (ResourceMaterialPolicy.classify(block.type, block.blockData) != kind || !isReadyNatural(block)) continue
            result += block
            val faces = listOf(
                BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
                BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
            )
            val neighbors = faces.map(block::getRelative)
            val orderedNeighbors = if (optimizedSearch) {
                neighbors.sortedBy { neighbor -> if (neighbor.type == origin.type) 0 else 1 }
            } else {
                neighbors
            }
            orderedNeighbors.forEach(queue::add)
            if (includeDiagonalTrunks && kind == ResourceCollectionKind.FOREST) {
                for (x in -1..1) for (z in -1..1) {
                    if (x == 0 && z == 0) continue
                    queue.add(block.getRelative(x, 0, z))
                }
            }
        }
        return result
    }

    private fun collectTreeLeaves(origin: Block, trunks: List<Block>, maximum: Int): List<Block> {
        val result = mutableListOf<Block>()
        val visited = mutableSetOf<BlockKey>()
        val queue = ArrayDeque<Block>()
        trunks.forEach { trunk ->
            for (x in -1..1) for (y in -1..1) for (z in -1..1) {
                if (x == 0 && y == 0 && z == 0) continue
                queue.add(trunk.getRelative(x, y, z))
            }
        }
        while (queue.isNotEmpty() && result.size < maximum) {
            val block = queue.removeFirst()
            if (!visited.add(block.key())) continue
            if (kotlin.math.abs(block.x - origin.x) > 8 ||
                block.y !in (origin.y - 2)..(origin.y + 18) ||
                kotlin.math.abs(block.z - origin.z) > 8) continue
            if (!ResourceMaterialPolicy.isLeaf(block.type) || !isReadyNatural(block)) continue
            result += block
            listOf(
                BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
                BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
            ).forEach { queue.add(block.getRelative(it)) }
        }
        return result
    }

    private fun cleanupTreeLeaves(player: Player, leaves: List<Block>): Int {
        var processed = 0
        for (leaf in leaves) {
            if (!ResourceMaterialPolicy.isLeaf(leaf.type) || !callProtectedBreak(leaf, player)) continue
            playNaturalBreakEffect(leaf, particleCount = 6)
            leaf.breakNaturally()
            processed++
        }
        return processed
    }

    private fun replantTree(player: Player, root: Block, originalMaterial: Material): Boolean {
        if (!root.type.isAir || !ResourceMaterialPolicy.canPlantTreeOn(root.getRelative(BlockFace.DOWN).type)) return false
        val sapling = ResourceMaterialPolicy.treeReplantMaterial(originalMaterial) ?: return false
        if (player.gameMode != GameMode.CREATIVE && !hasItem(player, sapling)) return false

        val replacedState = root.state
        root.type = sapling
        val placeEvent = BlockPlaceEvent(
            root,
            replacedState,
            root.getRelative(BlockFace.DOWN),
            ItemStack(sapling),
            player,
            true,
            EquipmentSlot.HAND
        )
        Bukkit.getPluginManager().callEvent(placeEvent)
        if (placeEvent.isCancelled || !placeEvent.canBuild()) {
            root.type = Material.AIR
            return false
        }
        if (player.gameMode != GameMode.CREATIVE && !consumeOne(player, sapling)) {
            root.type = Material.AIR
            return false
        }
        CCSystem.getAPI().getNaturalOriginRegistry().markPlayerPlaced(root)
        player.playSound(root.location, Sound.BLOCK_ROOTED_DIRT_PLACE, 0.8f, 1.1f)
        return true
    }

    private fun hasItem(player: Player, material: Material): Boolean =
        player.inventory.contents.any { it?.type == material && it.amount > 0 }

    private fun consumeOne(player: Player, material: Material): Boolean {
        val slot = player.inventory.contents.indexOfFirst { it?.type == material && it.amount > 0 }
        if (slot < 0) return false
        val stack = player.inventory.getItem(slot) ?: return false
        if (stack.amount <= 1) player.inventory.setItem(slot, null) else stack.amount -= 1
        return true
    }

    private fun isTreeRoot(block: Block): Boolean =
        ResourceMaterialPolicy.classify(block.getRelative(BlockFace.DOWN).type, block.getRelative(BlockFace.DOWN).blockData) !=
            ResourceCollectionKind.FOREST

    private fun awardBatch(player: Player, kind: ResourceCollectionKind, processed: Int, material: Material) {
        val experience = ProfessionExperience.batchExperience(processed)
        rankManager.addProfessionExp(player.uniqueId, experience)
        rankManager.recordProfessionCycleAction(player.uniqueId)
        val actionType = if (kind == ResourceCollectionKind.MINERAL) {
            ContentActionType.MINERAL_EXTRACTED
        } else {
            ContentActionType.TREE_PROCESSED
        }
        CCSystem.getAPI().getContentActionDispatcher().publish(
            ContentAction(
                actionId = UUID.randomUUID(),
                schemaVersion = 1,
                occurredAt = CCSystem.getAPI().getSharedClockService().now().toInstant(),
                playerId = player.uniqueId,
                actionType = actionType,
                amount = processed.toLong(),
                worldKey = player.world.key,
                metadata = mapOf(
                    "material" to material.key.toString(),
                    "batch" to "true",
                    "experience" to experience.toString()
                )
            )
        )
    }

    private fun inspectVegetation(event: PlayerInteractEvent, origin: Block) {
        event.isCancelled = true
        val player = event.player
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? FarmerSkillProfile
        if (profile == null || !profile.expertOperationUnlocked ||
            !RankReleasePolicy.canAccessProfession(player, Profession.FARMER)) {
            return
        }
        if (!isReadyWorld(origin) ||
            (!ResourceMaterialPolicy.isWildVegetation(origin.type) &&
                !ResourceMaterialPolicy.isVegetationBase(origin.type)) ||
            !CCSystem.getAPI().getNaturalOriginRegistry().isNatural(origin)) {
            return
        }
        val now = CCSystem.getAPI().getSharedClockService().now().toInstant()
        val cooldownUntil = gatheringCooldowns[player.uniqueId]
        if (cooldownUntil != null && cooldownUntil.isAfter(now)) {
            return
        }
        val radius = profile.inspectionRadius.coerceAtLeast(2)
        val expiresAt = now.plusSeconds(60)
        val season = CCSystem.getAPI().getSeasonService().currentSeason()
        val targets = mutableMapOf<BlockKey, GatheringTarget>()
        val discoveredDefinitions = linkedSetOf<SeasonalPlantDefinition>()
        for (x in -radius..radius) for (y in -2..2) for (z in -radius..radius) {
            val block = origin.getRelative(x, y, z)
            if (!ResourceMaterialPolicy.isWildVegetation(block.type)) continue
            if (!CCSystem.getAPI().getNaturalOriginRegistry().isNatural(block)) continue
            val definition = seasonalPlants.select(
                season,
                block.type,
                block.biome.key.toString(),
                block.y,
                random
            ) ?: continue
            if (CustomItemManager.getItem(definition.customItemId) == null) {
                plugin.logger.warning(
                    "[Resource Collection] 存在しない季節植物アイテムを無視しました: ${definition.customItemId}"
                )
                continue
            }
            targets[block.key()] = GatheringTarget(definition.customItemId, definition.id, expiresAt, surface = false)
            discoveredDefinitions += definition
            player.spawnParticle(
                Particle.HAPPY_VILLAGER,
                block.location.add(0.5, 0.7, 0.5),
                3,
                0.18,
                0.15,
                0.18,
                0.0
            )
        }
        if (targets.isEmpty() && ResourceMaterialPolicy.isVegetationBase(origin.type) &&
            surfaceGatheringStore.isAvailable(origin, now)) {
            val definition = seasonalPlants.select(
                season,
                origin.type,
                origin.biome.key.toString(),
                origin.y,
                random
            )
            if (definition != null && CustomItemManager.getItem(definition.customItemId) != null) {
                targets[origin.key()] = GatheringTarget(
                    definition.customItemId,
                    definition.id,
                    expiresAt,
                    surface = true
                )
                discoveredDefinitions += definition
                player.spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    origin.location.add(0.5, 1.05, 0.5),
                    3,
                    0.18,
                    0.08,
                    0.18,
                    0.0
                )
            }
        }
        player.swingMainHand()
        gatheringTargets[player.uniqueId] = targets
        val cooldownSeconds = profile.inspectionCooldownSeconds.takeIf { it > 0 } ?: 45
        gatheringCooldowns[player.uniqueId] = now.plusSeconds(cooldownSeconds.toLong())
        val collectibleHint = when {
            targets.isEmpty() -> text(player, "resource_collection.display.hint.vegetation_none")
            profile.detailedInspectionEnabled -> discoveredDefinitions
                .map { definition ->
                    CCSystem.getAPI().getI18nString(
                        player,
                        "custom_items.${definition.customItemId}.name"
                    )
                }
                .distinct()
                .joinToString(text(player, "resource_collection.display.list_separator"))
            else -> text(player, "resource_collection.display.hint.seasonal_plants")
        }
        val appraisalLines = buildList {
            add(GuiLoreLine.Data(
                text(player, "resource_collection.display.data.candidate_count"),
                targets.size,
                "§f"
            ))
            add(GuiLoreLine.Data(
                text(player, "resource_collection.display.data.season"),
                localizedEnum(player, "resource_collection.gathering.season", season.name),
                "§f"
            ))
            add(GuiLoreLine.Data(
                text(player, "resource_collection.display.data.collectible_items"),
                collectibleHint,
                "§a"
            ))
            if (profile.detailedInspectionEnabled && discoveredDefinitions.isNotEmpty()) {
                add(GuiLoreLine.Data(
                    text(player, "resource_collection.display.data.uses"),
                    discoveredDefinitions
                        .map { definition -> CCSystem.getAPI().getI18nString(player, definition.useNameKey) }
                        .distinct()
                        .joinToString(text(player, "resource_collection.display.list_separator")),
                    "§f"
                ))
                add(GuiLoreLine.Data(
                    text(player, "resource_collection.display.data.vegetation_group"),
                    discoveredDefinitions
                        .map { definition ->
                            CCSystem.getAPI().getI18nString(player, definition.vegetationGroupNameKey)
                        }
                        .distinct()
                        .joinToString(text(player, "resource_collection.display.list_separator")),
                    "§f"
                ))
            }
        }
        sendAppraisal(player, "resource_collection.display.heading.vegetation", appraisalLines)
        player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.25f)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGatheringDrops(event: BlockDropItemEvent) {
        if (!settings.isOperationEnabled(ResourceOperation.FARMER_WILD_GATHERING)) return
        val player = event.player
        if (CustomItemManager.identify(player.inventory.itemInMainHand)?.fullId != "resource.gathering_sickle") return
        val targets = gatheringTargets[player.uniqueId] ?: return
        val target = targets.remove(event.block.key()) ?: return
        if (target.surface) return
        val now = CCSystem.getAPI().getSharedClockService().now().toInstant()
        if (target.expiresAt.isBefore(now)) return
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? FarmerSkillProfile ?: return
        completePlantGathering(player, target, profile, event.blockState.type, event.block.location)
    }

    private fun handleSurfaceGathering(event: PlayerInteractEvent, block: Block) {
        val player = event.player
        val targets = gatheringTargets[player.uniqueId] ?: return
        val target = targets[block.key()]?.takeIf(GatheringTarget::surface) ?: return
        val now = CCSystem.getAPI().getSharedClockService().now().toInstant()
        if (target.expiresAt.isBefore(now)) {
            targets.remove(block.key())
            return
        }
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? FarmerSkillProfile ?: return
        if (!isReadyNatural(block) || !ResourceMaterialPolicy.isVegetationBase(block.type)) return
        event.isCancelled = true
        if (!surfaceGatheringStore.claim(block, now, seasonalPlants.surfaceRecoverySeconds)) {
            targets.remove(block.key())
            sendSubjectiveFeedback(player, "resource_collection.gathering.surface_depleted")
            return
        }
        player.swingMainHand()
        targets.remove(block.key())
        completePlantGathering(player, target, profile, block.type, block.location)
    }

    private fun completePlantGathering(
        player: Player,
        target: GatheringTarget,
        profile: FarmerSkillProfile,
        sourceMaterial: Material,
        sourceLocation: Location
    ) {
        val amount = profile.guaranteedSpecialistPlantYield.coerceAtLeast(1) +
            if (random.nextDouble() < profile.specialistPlantExtraChance) 1 else 0
        dropCustomItemNaturally(player, target.customItemId, amount, sourceLocation)
        sendCustomCollectionResult(
            player,
            "resource_collection.gathering.completed",
            target.customItemId,
            amount
        )
        if (player.gameMode != GameMode.CREATIVE && random.nextDouble() >= profile.durabilitySaveChance) {
            player.damageItemStack(EquipmentSlot.HAND, 1)
        }
        awardSpecialist(
            player,
            ContentActionType.PLANT_GATHERED,
            amount > 1,
            sourceMaterial,
            mapOf(
                "seasonal_plant_definition" to target.definitionId,
                "surface_gathering" to target.surface.toString()
            )
        )
    }

    private fun inspectForestProducts(event: PlayerInteractEvent, origin: Block) {
        event.isCancelled = true
        val player = event.player
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? LumberjackSkillProfile
        if (profile == null || profile.inspectionRadius <= 0 ||
            !RankReleasePolicy.canAccessProfession(player, Profession.LUMBERJACK)) {
            return
        }
        val species = TreeSpecies.fromTrunk(origin.type)
        if (!isReadyNatural(origin) || species == null) {
            return
        }
        val treeBlocks = collectTreeTrunks(origin, species)
        val root = treeBlocks.minWithOrNull(
            compareBy<Block>({ it.y }, { it.location.distanceSquared(origin.location) }, { it.x }, { it.z })
        )
        if (root == null || isAzaleaTree(species, treeBlocks) ||
            forestProductHarvestStore.isHarvested(root, species)) {
            val emptyResultKey = if (root != null &&
                forestProductHarvestStore.isHarvested(root, species)) {
                "resource_collection.display.hint.forest_harvested"
            } else {
                "resource_collection.display.hint.forest_none"
            }
            sendAppraisal(
                player,
                "resource_collection.display.heading.forest",
                listOf(
                    GuiLoreLine.Data(
                        text(player, "resource_collection.display.data.candidate_count"),
                        0,
                        "§f"
                    ),
                    GuiLoreLine.Data(
                        text(player, "resource_collection.display.data.collectible_items"),
                        text(player, emptyResultKey),
                        "§a"
                    )
                )
            )
            player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.05f)
            return
        }
        val now = CCSystem.getAPI().getSharedClockService().now().toInstant()
        if (forestCooldowns[player.uniqueId]?.isAfter(now) == true) {
            return
        }
        val expiresAt = now.plusSeconds(60)
        val targets = mutableMapOf<BlockKey, ForestProductTarget>()
        val discoveredNames = linkedSetOf<String>()
        val resolution = forestProducts.resolve(
            species,
            root.biome.key.toString(),
            root.world.seed,
            root.x,
            root.y,
            root.z,
            profile.discoveryChanceBonus
        )
        if (resolution != null) {
            val targetBlock = selectForestProductTarget(root, species, treeBlocks, resolution.type)
            if (targetBlock != null && CustomItemManager.getItem(resolution.type.itemId) == null) {
                plugin.logger.warning(
                    "[Resource Collection] 存在しない林産物アイテムを無視しました: ${resolution.type.itemId}"
                )
            }
            if (targetBlock != null && !CCSystem.getAPI().hasI18nKey(resolution.type.displayNameKey)) {
                plugin.logger.warning(
                    "[Resource Collection] 存在しない林産物言語キーを無視しました: ${resolution.type.displayNameKey}"
                )
            }
            if (targetBlock != null &&
                CustomItemManager.getItem(resolution.type.itemId) != null &&
                CCSystem.getAPI().hasI18nKey(resolution.type.displayNameKey)) {
                targets[targetBlock.key()] = ForestProductTarget(
                    resolution.type.itemId,
                    resolution.type.name.lowercase(),
                    expiresAt,
                    root.key(),
                    species,
                    resolution.type.targetKind
                )
                if (profile.exactMaterialInspectionEnabled) {
                    discoveredNames.add(
                        CCSystem.getAPI().getI18nString(player, resolution.type.displayNameKey)
                    )
                }
                player.spawnParticle(
                    Particle.WAX_ON,
                    targetBlock.location.add(0.5, 0.6, 0.5),
                    3,
                    0.16,
                    0.15,
                    0.16,
                    0.0
                )
            }
        }
        player.swingMainHand()
        forestTargets[player.uniqueId] = targets
        forestCooldowns[player.uniqueId] = now.plusSeconds(profile.inspectionCooldownSeconds.coerceAtLeast(1).toLong())
        val collectibleHint = when {
            targets.isEmpty() -> text(player, "resource_collection.display.hint.forest_none")
            profile.exactMaterialInspectionEnabled ->
                discoveredNames.joinToString(text(player, "resource_collection.display.list_separator"))
            else -> text(player, "resource_collection.display.hint.forest_products")
        }
        sendAppraisal(
            player,
            "resource_collection.display.heading.forest",
            listOf(
                GuiLoreLine.Data(
                    text(player, "resource_collection.display.data.candidate_count"),
                    targets.size,
                    "§f"
                ),
                GuiLoreLine.Data(
                    text(player, "resource_collection.display.data.collectible_items"),
                    collectibleHint,
                    "§a"
                )
            )
        )
        player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.05f)
    }

    private fun tryHarvestForestProduct(event: PlayerInteractEvent, block: Block): Boolean {
        if (!settings.isOperationEnabled(ResourceOperation.LUMBERJACK_FOREST_PRODUCTS)) return false
        val player = event.player
        val targets = forestTargets[player.uniqueId] ?: return false
        val target = targets.remove(block.key()) ?: return false
        event.isCancelled = true
        val now = CCSystem.getAPI().getSharedClockService().now().toInstant()
        if (target.expiresAt.isBefore(now)) return true
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? LumberjackSkillProfile ?: return true
        val root = target.root.resolve() ?: return true
        val validTarget = when (target.targetKind) {
            ForestProductTargetKind.LOG -> block.type == target.species.log
            ForestProductTargetKind.LEAF -> block.type == target.species.leaves
        }
        if (!validTarget || !isReadyNatural(block)) {
            return true
        }
        if (forestProductHarvestStore.isHarvested(root, target.species)) {
            sendSubjectiveFeedback(player, "resource_collection.forest.harvested")
            return true
        }
        val allowed = when (target.targetKind) {
            ForestProductTargetKind.LOG -> callProtectedInteract(block, player)
            ForestProductTargetKind.LEAF -> callProtectedBreak(block, player)
        }
        if (!allowed) {
            return true
        }
        if (!forestProductHarvestStore.claim(root, target.species)) {
            sendSubjectiveFeedback(player, "resource_collection.forest.harvested")
            return true
        }
        player.swingMainHand()
        val originalMaterial = block.type
        when (target.targetKind) {
            ForestProductTargetKind.LOG -> {
                val stripped = strippedType(block.type) ?: return true
                playNaturalBreakEffect(block, particleCount = 8)
                block.type = stripped
            }
            ForestProductTargetKind.LEAF -> {
                playNaturalBreakEffect(block, particleCount = 6)
                block.breakNaturally(player.inventory.itemInMainHand, true)
            }
        }
        dropCustomItemNaturally(player, target.customItemId, 1, block.location)
        sendCustomCollectionResult(
            player,
            "resource_collection.forest.completed",
            target.customItemId,
            1
        )
        if (player.gameMode != GameMode.CREATIVE && random.nextDouble() >= profile.durabilitySaveChance) {
            player.damageItemStack(EquipmentSlot.HAND, 1)
        }
        awardSpecialist(
            player,
            ContentActionType.TREE_PROCESSED,
            false,
            originalMaterial,
            mapOf("forest_product_definition" to target.definitionId)
        )
        return true
    }

    private fun collectTreeTrunks(origin: Block, species: TreeSpecies): List<Block> {
        val queue = ArrayDeque<Block>()
        val visited = mutableSetOf<BlockKey>()
        val result = mutableListOf<Block>()
        queue.add(origin)
        while (queue.isNotEmpty() && result.size < 256) {
            val block = queue.removeFirst()
            if (!visited.add(block.key()) || !species.isTrunk(block.type) || !isReadyNatural(block)) continue
            result.add(block)
            for (x in -1..1) for (y in -1..1) for (z in -1..1) {
                if (x == 0 && y == 0 && z == 0) continue
                queue.add(block.getRelative(x, y, z))
            }
        }
        return result
    }

    private fun isAzaleaTree(species: TreeSpecies, trunks: List<Block>): Boolean =
        species == TreeSpecies.OAK && trunks.any { trunk ->
            (-2..2).any { x -> (-2..2).any { y -> (-2..2).any { z ->
                trunk.getRelative(x, y, z).type in setOf(
                    Material.AZALEA_LEAVES,
                    Material.FLOWERING_AZALEA_LEAVES
                )
            } } }
        }

    private fun selectForestProductTarget(
        root: Block,
        species: TreeSpecies,
        trunks: List<Block>,
        product: ForestProductType
    ): Block? {
        if (product.targetKind == ForestProductTargetKind.LEAF) {
            val leaves = linkedMapOf<BlockKey, Block>()
            trunks.forEach { trunk ->
                for (x in -2..2) for (y in -2..2) for (z in -2..2) {
                    val candidate = trunk.getRelative(x, y, z)
                    if (candidate.type == species.leaves && isReadyNatural(candidate)) {
                        leaves.putIfAbsent(candidate.key(), candidate)
                    }
                }
            }
            return stableTarget(leaves.values.toList(), root, species)
        }
        val unstripped = trunks.filter { it.type == species.log }
        val preferredHeight = unstripped.filter { it.y - root.y in 1..3 }
        val exposed = unstripped.filter(::isExposedTrunk)
        val nonRoot = unstripped.filter { it.key() != root.key() }
        return stableTarget(
            preferredHeight.ifEmpty { exposed }.ifEmpty { nonRoot }.ifEmpty { unstripped },
            root,
            species
        )
    }

    private fun isExposedTrunk(block: Block): Boolean =
        listOf(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)
            .map(block::getRelative)
            .any { it.type.isAir || ResourceMaterialPolicy.isLeaf(it.type) }

    private fun stableTarget(candidates: List<Block>, root: Block, species: TreeSpecies): Block? {
        if (candidates.isEmpty()) return null
        val sorted = candidates.sortedWith(compareBy<Block>({ it.y }, { it.x }, { it.z }))
        var value = root.world.seed xor root.x.toLong().shl(32) xor root.y.toLong().shl(16) xor
            root.z.toLong() xor species.ordinal.toLong()
        value = value xor (value ushr 33)
        return sorted[Math.floorMod(value, sorted.size.toLong()).toInt()]
    }

    private fun cropSeed(crop: Material): Material? = when (crop) {
        Material.WHEAT -> Material.WHEAT_SEEDS
        Material.CARROTS -> Material.CARROT
        Material.POTATOES -> Material.POTATO
        Material.BEETROOTS -> Material.BEETROOT_SEEDS
        Material.NETHER_WART -> Material.NETHER_WART
        Material.SWEET_BERRY_BUSH -> Material.SWEET_BERRIES
        else -> null
    }

    private fun handleChiselClick(event: PlayerInteractEvent, block: Block) {
        event.isCancelled = true
        val player = event.player
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? MinerSkillProfile
        if (profile == null || !profile.expertOperationUnlocked ||
            !RankReleasePolicy.canAccessProfession(player, Profession.MINER)) {
            return
        }
        if (ResourceMaterialPolicy.classify(block.type, block.blockData) != ResourceCollectionKind.MINERAL ||
            !isReadyNatural(block)) {
            return
        }
        val key = block.key()
        val current = chiselSessions[player.uniqueId]
        if (current == null) {
            val owner = occupiedBlocks[key]
            if (owner != null && owner != player.uniqueId) {
                return
            }
            val session = ChiselSession(
                player.uniqueId,
                key,
                block.type,
                event.blockFace,
                player.location.clone(),
                randomTarget(block, event.blockFace)
            )
            chiselSessions[player.uniqueId] = session
            occupiedBlocks[key] = player.uniqueId
            showChiselTarget(player, session)
            scheduleChiselTimeout(session)
            return
        }
        if (current.blockKey != key || current.face != event.blockFace) {
            cancelChisel(player.uniqueId)
            return
        }
        val interaction = resolveChiselInteractionPoint(event, block, current.face) ?: return
        val tolerance = ChiselHitPolicy.tolerancePixels(profile.precisionToleranceBonus)
        val distance = chiselDistancePixels(current.target, interaction, current.face)
        val attempt = ChiselAttemptPolicy.evaluate(
            distance,
            tolerance,
            profile.ignoredMinorFailures - current.ignoredFailures
        )
        if (attempt.consumesIgnoredFailure) current.ignoredFailures++
        if (attempt.countsAsAttempt) {
            current.scoreTotal += attempt.score
            current.attempts++
        }
        current.timeout?.cancel()
        if (current.attempts >= settings.chisel.targetCount) {
            completeChisel(player, current, profile)
        } else {
            current.target = randomTarget(block, current.face)
            showChiselTarget(player, current)
            scheduleChiselTimeout(current)
            player.playSound(player.location, Sound.BLOCK_STONE_HIT, 0.65f, 1.35f)
        }
    }

    private fun completeChisel(player: Player, session: ChiselSession, profile: MinerSkillProfile) {
        val block = session.blockKey.resolve() ?: run {
            cancelChisel(player.uniqueId)
            return
        }
        if (!validateChiselState(player, session, block)) {
            cancelChisel(player.uniqueId)
            return
        }
        if (!callProtectedBreak(block, player)) {
            cancelChisel(player.uniqueId)
            return
        }
        player.swingMainHand()
        val average = session.scoreTotal / session.attempts.coerceAtLeast(1)
        val specialAmount = ChiselRewardPolicy.specialMaterialCount(
            average,
            profile.minimumSpecialMaterialStandardEnabled,
            profile.topEvaluationExtraMaterial,
            profile.topEvaluationThreshold
        )
        session.timeout?.cancel()
        chiselSessions.remove(player.uniqueId)
        occupiedBlocks.remove(session.blockKey)
        playNaturalBreakEffect(block)
        block.breakNaturally(ItemStack(Material.IRON_PICKAXE), true)
        if (player.gameMode != GameMode.CREATIVE) player.damageItemStack(EquipmentSlot.HAND, 1)
        if (specialAmount > 0) {
            val result = MineralCompanionPolicy.inspect(
                block.world.environment,
                block.biome.key.toString(),
                block.y
            )
            dropResourceNaturally(player, result.resourceId, specialAmount, block.location)
            sendCustomCollectionResult(
                player,
                "resource_collection.chisel.completed",
                "resource.${result.resourceId}",
                specialAmount
            )
        } else {
            sendCountCollectionResult(
                player,
                "resource_collection.chisel.completed",
                "resource_collection.display.heading.chisel_result",
                0,
                "resource_collection.display.data.special_materials"
            )
        }
        awardSpecialist(
            player,
            ContentActionType.MINERAL_EXTRACTED,
            average >= profile.topEvaluationThreshold,
            session.originalMaterial
        )
    }

    private fun validateChiselState(player: Player, session: ChiselSession, block: Block): Boolean =
        player.isOnline &&
            player.world.uid == session.blockKey.worldId &&
            player.location.distanceSquared(session.startedAt) <= 36.0 &&
            CustomItemManager.identify(player.inventory.itemInMainHand)?.fullId == "resource.chisel" &&
            block.type == session.originalMaterial &&
            isReadyNatural(block)

    private fun scheduleChiselTimeout(session: ChiselSession) {
        session.timeout = plugin.server.scheduler.runTaskLater(
            plugin,
            Runnable { cancelChisel(session.playerId) },
            settings.chisel.targetTimeoutTicks
        )
    }

    private fun showChiselTarget(player: Player, session: ChiselSession) {
        player.spawnParticle(
            Particle.CRIT,
            session.target,
            settings.chisel.particleCount,
            0.02,
            0.02,
            0.02,
            0.0
        )
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BELL, 0.55f, 1.65f)
    }

    private fun randomTarget(block: Block, face: BlockFace): Location {
        val center = block.location.add(0.5, 0.5, 0.5)
        val normal = face.direction.normalize().multiply(0.501)
        val (firstAxis, secondAxis) = faceAxes(face)
        val firstOffset = targetPixelOffset()
        val secondOffset = targetPixelOffset()
        return center.add(normal).add(firstAxis.multiply(firstOffset)).add(secondAxis.multiply(secondOffset))
    }

    private fun targetPixelOffset(): Double {
        val pixelIndex = random.nextInt(10) + 3
        return (pixelIndex - 7.5) * ChiselHitPolicy.PIXEL_SIZE_BLOCKS
    }

    private fun faceAxes(face: BlockFace): Pair<Vector, Vector> = when (face) {
        BlockFace.UP, BlockFace.DOWN -> Vector(1, 0, 0) to Vector(0, 0, 1)
        BlockFace.EAST, BlockFace.WEST -> Vector(0, 1, 0) to Vector(0, 0, 1)
        else -> Vector(1, 0, 0) to Vector(0, 1, 0)
    }

    private fun resolveChiselInteractionPoint(
        event: PlayerInteractEvent,
        block: Block,
        expectedFace: BlockFace
    ): Location? {
        event.interactionPoint?.let { return it }
        val eye = event.player.eyeLocation
        val hit = block.world.rayTraceBlocks(
            eye,
            eye.direction,
            6.0,
            FluidCollisionMode.NEVER,
            true
        ) ?: return null
        if (hit.hitBlock?.key() != block.key() || hit.hitBlockFace != expectedFace) return null
        return hit.hitPosition.toLocation(block.world)
    }

    private fun chiselDistancePixels(target: Location, interaction: Location, face: BlockFace): Double {
        val delta = interaction.toVector().subtract(target.toVector())
        val (firstAxis, secondAxis) = faceAxes(face)
        return ChiselHitPolicy.quantizedDistancePixels(
            delta.dot(firstAxis),
            delta.dot(secondAxis)
        )
    }

    private fun scheduleBarkReward(event: PlayerInteractEvent, block: Block) {
        val player = event.player
        if (event.useInteractedBlock() == Event.Result.DENY) return
        if (!player.inventory.itemInMainHand.type.name.endsWith("_AXE")) return
        val stripped = strippedType(block.type) ?: return
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? LumberjackSkillProfile ?: return
        if (!profile.expertOperationUnlocked || !RankReleasePolicy.canAccessProfession(player, Profession.LUMBERJACK)) return
        if (!isReadyWorld(block)) return
        val key = block.key()
        plugin.server.scheduler.runTask(plugin, Runnable {
            val changed = key.resolve() ?: return@Runnable
            if (changed.type != stripped) return@Runnable
            dropResourceNaturally(player, "bark", 1, changed.location)
            sendCustomCollectionResult(
                player,
                "resource_collection.woodworking.completed",
                "resource.bark",
                1
            )
            awardSpecialist(player, ContentActionType.TREE_PROCESSED, false, stripped)
            player.playSound(player.location, Sound.ITEM_AXE_STRIP, 0.7f, 1.25f)
        })
    }

    private fun processPlacedLog(event: PlayerInteractEvent, block: Block, precise: Boolean) {
        event.isCancelled = true
        val player = event.player
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? LumberjackSkillProfile
        if (profile == null || !profile.expertOperationUnlocked ||
            !RankReleasePolicy.canAccessProfession(player, Profession.LUMBERJACK)) {
            return
        }
        if (!isReadyWorld(block) || CCSystem.getAPI().getNaturalOriginRegistry().isNatural(block)) {
            return
        }
        val planks = plankType(block.type) ?: run {
            sendSubjectiveFeedback(player, "resource_collection.woodworking.not_ready")
            return
        }
        if (!callProtectedBreak(block, player)) {
            return
        }
        player.swingMainHand()
        val originalMaterial = block.type
        playNaturalBreakEffect(block)
        block.type = Material.AIR
        if (precise) {
            val amount = profile.timberYield.takeIf { it > 0 } ?: 1
            dropResourceNaturally(
                player,
                "timber_beam",
                amount,
                block.location
            )
            sendCustomCollectionResult(
                player,
                "resource_collection.woodworking.completed",
                "resource.timber_beam",
                amount
            )
        } else {
            val amount = profile.plankYield.takeIf { it > 0 } ?: 4
            val item = ItemStack(planks, amount)
            block.world.dropItemNaturally(block.location.add(0.5, 0.5, 0.5), item)
            sendCountCollectionResult(
                player,
                "resource_collection.woodworking.completed",
                "resource_collection.display.heading.woodworking_result",
                amount
            )
        }
        if (player.gameMode != GameMode.CREATIVE) player.damageItemStack(EquipmentSlot.HAND, 1)
        awardSpecialist(player, ContentActionType.TREE_PROCESSED, false, originalMaterial)
    }

    private fun awardSpecialist(
        player: Player,
        actionType: ContentActionType,
        highQuality: Boolean,
        material: Material,
        additionalMetadata: Map<String, String> = emptyMap()
    ) {
        val experience = ProfessionExperience.SPECIALIST_ACTION +
            if (highQuality) ProfessionExperience.HIGH_QUALITY_BONUS else 0L
        rankManager.addProfessionExp(player.uniqueId, experience)
        rankManager.recordProfessionCycleAction(player.uniqueId, specialist = true, highQuality = highQuality)
        CCSystem.getAPI().getContentActionDispatcher().publish(
            ContentAction(
                actionId = UUID.randomUUID(),
                schemaVersion = 1,
                occurredAt = CCSystem.getAPI().getSharedClockService().now().toInstant(),
                playerId = player.uniqueId,
                actionType = actionType,
                amount = 1L,
                worldKey = player.world.key,
                metadata = mapOf("material" to material.key.toString(), "specialist" to "true") + additionalMetadata
            )
        )
    }

    private fun callProtectedBreak(block: Block, player: Player): Boolean {
        val key = internalBreakKey(player.uniqueId, block)
        internalBreaks.add(key)
        return try {
            val event = BlockBreakEvent(block, player)
            event.expToDrop = 0
            Bukkit.getPluginManager().callEvent(event)
            !event.isCancelled
        } finally {
            internalBreaks.remove(key)
        }
    }

    private fun dropResourceNaturally(player: Player, id: String, amount: Int, source: Location) =
        dropCustomItemNaturally(player, "resource.$id", amount, source)

    private fun sendAppraisal(player: Player, headingKey: String, data: List<GuiLoreLine>) {
        val spec = GuiLoreSpec.Blocks(
            listOf(
                GuiLoreBlock(listOf(
                    GuiLoreLine.StyledText(text(player, headingKey), "§e", false)
                )),
                GuiLoreBlock(data)
            )
        )
        CCSystem.getAPI().getLoreService().render(spec).forEach(player::sendMessage)
    }

    private fun sendCustomCollectionResult(
        player: Player,
        completionKey: String,
        customItemId: String,
        amount: Int
    ) {
        player.sendMessage(message(player, completionKey))
        val descriptionKey = "custom_items.$customItemId.description"
        val itemLines = buildList {
            add(GuiLoreLine.StyledText(
                text(player, "custom_items.$customItemId.name"),
                "§a",
                false
            ))
            if (CCSystem.getAPI().hasI18nKey(descriptionKey)) {
                add(GuiLoreLine.Text(text(player, descriptionKey)))
            }
        }
        val spec = GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(itemLines),
            GuiLoreBlock(listOf(
                GuiLoreLine.Data(
                    text(player, "resource_collection.display.data.amount"),
                    amount,
                    "§f"
                )
            ))
        ))
        CCSystem.getAPI().getLoreService().render(spec).forEach(player::sendMessage)
    }

    private fun sendCountCollectionResult(
        player: Player,
        completionKey: String,
        headingKey: String,
        count: Int,
        dataKey: String = "resource_collection.display.data.processed_count"
    ) {
        player.sendMessage(message(player, completionKey))
        val spec = GuiLoreSpec.Blocks(listOf(
            GuiLoreBlock(listOf(
                GuiLoreLine.StyledText(text(player, headingKey), "§e", false)
            )),
            GuiLoreBlock(listOf(
                GuiLoreLine.Data(text(player, dataKey), count, "§f")
            ))
        ))
        CCSystem.getAPI().getLoreService().render(spec).forEach(player::sendMessage)
    }

    private fun sendSubjectiveFeedback(player: Player, key: String) {
        val now = CCSystem.getAPI().getSharedClockService().now().toInstant()
        val timestampKey = player.uniqueId to key
        val lastSent = feedbackTimestamps[timestampKey]
        if (lastSent != null && lastSent.plusSeconds(2).isAfter(now)) return
        feedbackTimestamps[timestampKey] = now
        player.sendMessage(message(player, key))
    }

    private fun dropCustomItemNaturally(player: Player, fullId: String, amount: Int, source: Location) {
        if (amount <= 0) return
        val item = CustomItemManager.createItemForPlayer(fullId, player, amount) ?: return
        val world = source.world ?: return
        world.dropItemNaturally(source.clone().add(0.5, 0.5, 0.5), item)
    }

    private fun playNaturalBreakEffect(block: Block, particleCount: Int = 12) {
        val blockData = block.blockData.clone()
        val location = block.location.add(0.5, 0.5, 0.5)
        block.world.spawnParticle(
            Particle.BLOCK,
            location,
            particleCount,
            0.28,
            0.28,
            0.28,
            0.04,
            blockData
        )
        val soundGroup = blockData.soundGroup
        block.world.playSound(
            location,
            soundGroup.breakSound,
            soundGroup.volume.coerceAtMost(1.0f),
            soundGroup.pitch
        )
    }

    private fun isReadyNatural(block: Block): Boolean =
        isReadyWorld(block) && CCSystem.getAPI().getNaturalOriginRegistry().isNatural(block)

    private fun isReadyWorld(block: Block): Boolean =
        CCSystem.getAPI().getResourceWorldLifecycleService().isReady(block.world.key)

    private fun cancelChisel(playerId: UUID) {
        val session = chiselSessions.remove(playerId) ?: return
        session.timeout?.cancel()
        occupiedBlocks.remove(session.blockKey)
    }

    @EventHandler(ignoreCancelled = true)
    fun onOtherBlockBreak(event: BlockBreakEvent) {
        val owner = occupiedBlocks[event.block.key()] ?: return
        if (owner != event.player.uniqueId) cancelChisel(owner)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        cancelChisel(event.player.uniqueId)
        gatheringTargets.remove(event.player.uniqueId)
        gatheringCooldowns.remove(event.player.uniqueId)
        forestTargets.remove(event.player.uniqueId)
        forestCooldowns.remove(event.player.uniqueId)
        feedbackTimestamps.keys.removeIf { it.first == event.player.uniqueId }
    }

    @EventHandler
    fun onWorldChanged(event: PlayerChangedWorldEvent) =
        cancelChisel(event.player.uniqueId)

    @EventHandler
    fun onHeldItemChanged(event: PlayerItemHeldEvent) =
        cancelChisel(event.player.uniqueId)

    private fun Block.key(): BlockKey = BlockKey(world.uid, x, y, z)
    private fun BlockKey.resolve(): Block? = Bukkit.getWorld(worldId)?.getBlockAt(x, y, z)

    private fun strippedType(material: Material): Material? = runCatching {
        Material.valueOf(
            when {
                material.name.endsWith("_LOG") -> "STRIPPED_${material.name}"
                material.name.endsWith("_WOOD") -> "STRIPPED_${material.name}"
                material.name.endsWith("_STEM") -> "STRIPPED_${material.name}"
                material.name.endsWith("_HYPHAE") -> "STRIPPED_${material.name}"
                else -> return null
            }
        )
    }.getOrNull()

    private fun plankType(material: Material): Material? {
        if (!material.name.startsWith("STRIPPED_")) return null
        val species = material.name.removePrefix("STRIPPED_")
            .removeSuffix("_LOG")
            .removeSuffix("_WOOD")
            .removeSuffix("_STEM")
            .removeSuffix("_HYPHAE")
        return Material.matchMaterial("${species}_PLANKS")
    }

    private fun text(player: Player?, key: String, vararg values: Pair<String, Any>): String =
        CCSystem.getAPI().getI18nString(player, key, values.toMap()).replace('&', '§')

    private fun message(player: Player?, key: String, vararg values: Pair<String, Any>): Component =
        Component.text(text(player, key, *values))
}
