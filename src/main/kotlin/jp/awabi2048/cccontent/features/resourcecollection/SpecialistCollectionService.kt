package jp.awabi2048.cccontent.features.resourcecollection

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.action.ContentAction
import com.awabi2048.ccsystem.api.action.ContentActionType
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
        val expiresAt: Instant
    )

    private val chiselSessions = mutableMapOf<UUID, ChiselSession>()
    private val occupiedBlocks = mutableMapOf<BlockKey, UUID>()
    private val gatheringTargets = mutableMapOf<UUID, MutableMap<BlockKey, GatheringTarget>>()
    private val gatheringCooldowns = mutableMapOf<UUID, Instant>()
    private val forestTargets = mutableMapOf<UUID, MutableMap<BlockKey, ForestProductTarget>>()
    private val forestCooldowns = mutableMapOf<UUID, Instant>()
    private val tillableMaterials = setOf(Material.DIRT, Material.GRASS_BLOCK, Material.DIRT_PATH)
    private val surfaceGatheringStore = SurfaceGatheringStore(
        plugin.dataFolder.resolve("data/resource_collection/surface_gathering.yml")
    )

    fun shutdown() {
        chiselSessions.keys.toList().forEach { cancelChisel(it, null) }
        occupiedBlocks.clear()
        gatheringTargets.clear()
        gatheringCooldowns.clear()
        forestTargets.clear()
        forestCooldowns.clear()
        surfaceGatheringStore.save()
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
                ifEnabled(ResourceOperation.LUMBERJACK_TIMBER_PROCESSING) {
                    processPlacedLog(event, block, precise = true)
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
            player.sendMessage(message(player, "resource_collection.error.miner_required"))
            return
        }
        if (!isReadyNatural(block) ||
            ResourceMaterialPolicy.classify(block.type, block.blockData) != ResourceCollectionKind.MINERAL) {
            player.sendMessage(message(player, "resource_collection.error.natural_resource_required"))
            return
        }
        val result = MineralCompanionPolicy.inspect(
            block.world.environment,
            block.biome.key.toString(),
            block.y
        )
        val placeholders = arrayOf<Pair<String, Any>>(
            "altitude" to localizedEnum(player, "resource_collection.inspection.altitude", result.altitude.name),
            "biome" to localizedEnum(player, "resource_collection.inspection.biome", result.biome.name),
            "material" to CCSystem.getAPI().getI18nString(
                player,
                "custom_items.resource.${result.resourceId}.name"
            )
        )
        val messageKey = if (profile.detailedInspectionEnabled) {
            "resource_collection.inspection.detailed"
        } else {
            "resource_collection.inspection.basic"
        }
        player.sendMessage(message(player, messageKey, *placeholders))
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
            player.sendMessage(message(player, "resource_collection.error.ready_world_required"))
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
            block.breakNaturally(ItemStack(Material.IRON_HOE), true)
            if (settings.isOperationEnabled(ResourceOperation.FARMER_AUTOMATIC_REPLANT) &&
                profile.automaticReplantEnabled) {
                replantCrop(player, block, cropType, profile)
            }
            processed++
        }
        if (processed <= 0) return
        damageCultivationTool(player, processed, profile.durabilitySaveChance)
        awardFarmerArea(player, ContentActionType.CROP_HARVESTED, processed, originMaterial, "harvest")
        player.sendMessage(message(player, "resource_collection.cultivation.harvested", "count" to processed))
    }

    private fun handleAreaTilling(event: PlayerInteractEvent, origin: Block) {
        val player = event.player
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? FarmerSkillProfile ?: return
        if (!profile.areaTillingEnabled || origin.type !in tillableMaterials) return
        event.isCancelled = true
        if (!isReadyWorld(origin)) {
            player.sendMessage(message(player, "resource_collection.error.ready_world_required"))
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
                block.type = Material.FARMLAND
                CCSystem.getAPI().getNaturalOriginRegistry().markPlayerPlaced(block)
                processed++
            }
            if (processed <= 0) return@Runnable
            damageCultivationTool(player, processed, profile.durabilitySaveChance)
            awardFarmerArea(player, ContentActionType.SOIL_TILLED, processed, originMaterial, "tilling")
            player.sendMessage(message(player, "resource_collection.cultivation.tilled", "count" to processed))
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
            player.sendMessage(message(player, "resource_collection.error.natural_resource_required"))
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
            player.sendMessage(message(player, "resource_collection.error.protected"))
            return
        }
        val completeTreeBatch = expectedKind == ResourceCollectionKind.FOREST && processed == targets.size
        if (treeRoot && completeTreeBatch &&
            settings.isOperationEnabled(ResourceOperation.LUMBERJACK_HEARTWOOD)) {
            giveResource(player, "heartwood", 1)
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
        player.sendMessage(message(player, "resource_collection.batch.completed", "count" to processed))
        player.playSound(player.location, Sound.BLOCK_DEEPSLATE_BREAK, 0.8f, 1.0f)
    }

    private fun breakBatchBlock(
        block: Block,
        tool: ItemStack,
        player: Player,
        automaticCollection: Boolean
    ): Boolean {
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
            player.sendMessage(message(player, "resource_collection.error.farmer_required"))
            return
        }
        if (!isReadyWorld(origin) ||
            (!ResourceMaterialPolicy.isWildVegetation(origin.type) &&
                !ResourceMaterialPolicy.isVegetationBase(origin.type)) ||
            !CCSystem.getAPI().getNaturalOriginRegistry().isNatural(origin)) {
            player.sendMessage(message(player, "resource_collection.error.natural_vegetation_required"))
            return
        }
        val now = CCSystem.getAPI().getSharedClockService().now().toInstant()
        val cooldownUntil = gatheringCooldowns[player.uniqueId]
        if (cooldownUntil != null && cooldownUntil.isAfter(now)) {
            player.sendMessage(message(player, "resource_collection.gathering.cooldown"))
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
        gatheringTargets[player.uniqueId] = targets
        val cooldownSeconds = profile.inspectionCooldownSeconds.takeIf { it > 0 } ?: 45
        gatheringCooldowns[player.uniqueId] = now.plusSeconds(cooldownSeconds.toLong())
        val messageKey = when {
            targets.isEmpty() -> "resource_collection.gathering.no_discoveries"
            profile.detailedInspectionEnabled -> "resource_collection.gathering.inspected_detailed"
            else -> "resource_collection.gathering.inspected"
        }
        player.sendMessage(
            message(
                player,
                messageKey,
                "count" to targets.size,
                "season" to localizedEnum(player, "resource_collection.gathering.season", season.name),
                "uses" to discoveredDefinitions
                    .map { definition -> CCSystem.getAPI().getI18nString(player, definition.useNameKey) }
                    .distinct()
                    .joinToString("、"),
                "groups" to discoveredDefinitions
                    .map { definition -> CCSystem.getAPI().getI18nString(player, definition.vegetationGroupNameKey) }
                    .distinct()
                    .joinToString("、")
            )
        )
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
        completePlantGathering(player, target, profile, event.blockState.type)
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
            player.sendMessage(message(player, "resource_collection.gathering.surface_depleted"))
            return
        }
        targets.remove(block.key())
        completePlantGathering(player, target, profile, block.type)
    }

    private fun completePlantGathering(
        player: Player,
        target: GatheringTarget,
        profile: FarmerSkillProfile,
        sourceMaterial: Material
    ) {
        val amount = profile.guaranteedSpecialistPlantYield.coerceAtLeast(1) +
            if (random.nextDouble() < profile.specialistPlantExtraChance) 1 else 0
        giveCustomItem(player, target.customItemId, amount)
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
        player.sendMessage(message(player, "resource_collection.gathering.completed", "amount" to amount))
    }

    private fun inspectForestProducts(event: PlayerInteractEvent, origin: Block) {
        event.isCancelled = true
        val player = event.player
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? LumberjackSkillProfile
        if (profile == null || profile.inspectionRadius <= 0 ||
            !RankReleasePolicy.canAccessProfession(player, Profession.LUMBERJACK)) {
            player.sendMessage(message(player, "resource_collection.error.lumberjack_required"))
            return
        }
        if (!isReadyNatural(origin) ||
            ResourceMaterialPolicy.classify(origin.type, origin.blockData) != ResourceCollectionKind.FOREST) {
            player.sendMessage(message(player, "resource_collection.error.natural_tree_required"))
            return
        }
        val now = CCSystem.getAPI().getSharedClockService().now().toInstant()
        if (forestCooldowns[player.uniqueId]?.isAfter(now) == true) {
            player.sendMessage(message(player, "resource_collection.forest.cooldown"))
            return
        }
        val expiresAt = now.plusSeconds(60)
        val targets = mutableMapOf<BlockKey, ForestProductTarget>()
        val discoveredNames = linkedSetOf<String>()
        val radius = profile.inspectionRadius.coerceIn(2, 8)
        for (x in -radius..radius) for (y in -radius..radius) for (z in -radius..radius) {
            val block = origin.getRelative(x, y, z)
            if (!isReadyNatural(block)) continue
            val definition = forestProducts.select(
                origin.type,
                block.type,
                block.biome.key.toString(),
                profile.discoveryChanceBonus,
                random
            ) ?: continue
            if (CustomItemManager.getItem(definition.customItemId) == null) {
                plugin.logger.warning(
                    "[Resource Collection] 存在しない林産物アイテムを無視しました: ${definition.customItemId}"
                )
                continue
            }
            if (!CCSystem.getAPI().hasI18nKey(definition.displayNameKey)) {
                plugin.logger.warning(
                    "[Resource Collection] 存在しない林産物言語キーを無視しました: ${definition.displayNameKey}"
                )
                continue
            }
            targets[block.key()] = ForestProductTarget(definition.customItemId, definition.id, expiresAt)
            if (profile.exactMaterialInspectionEnabled) {
                discoveredNames.add(CCSystem.getAPI().getI18nString(player, definition.displayNameKey))
            }
            player.spawnParticle(Particle.WAX_ON, block.location.add(0.5, 0.6, 0.5), 3, 0.16, 0.15, 0.16, 0.0)
        }
        forestTargets[player.uniqueId] = targets
        forestCooldowns[player.uniqueId] = now.plusSeconds(profile.inspectionCooldownSeconds.coerceAtLeast(1).toLong())
        val messageKey = when {
            targets.isEmpty() -> "resource_collection.forest.no_discoveries"
            profile.exactMaterialInspectionEnabled -> "resource_collection.forest.inspected_exact"
            else -> "resource_collection.forest.inspected"
        }
        player.sendMessage(
            message(
                player,
                messageKey,
                "count" to targets.size,
                "products" to discoveredNames.joinToString("、")
            )
        )
        player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.05f)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onForestProductDrops(event: BlockDropItemEvent) {
        if (!settings.isOperationEnabled(ResourceOperation.LUMBERJACK_FOREST_PRODUCTS)) return
        val player = event.player
        if (CustomItemManager.identify(player.inventory.itemInMainHand)?.fullId != "resource.forest_knife") return
        val targets = forestTargets[player.uniqueId] ?: return
        val target = targets.remove(event.block.key()) ?: return
        val now = CCSystem.getAPI().getSharedClockService().now().toInstant()
        if (target.expiresAt.isBefore(now)) return
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? LumberjackSkillProfile ?: return
        val amount = 1 + if (random.nextDouble() < profile.extraForestProductChance) 1 else 0
        giveCustomItem(player, target.customItemId, amount)
        if (player.gameMode != GameMode.CREATIVE && random.nextDouble() >= profile.durabilitySaveChance) {
            player.damageItemStack(EquipmentSlot.HAND, 1)
        }
        awardSpecialist(
            player,
            ContentActionType.TREE_PROCESSED,
            amount > 1,
            event.blockState.type,
            mapOf("forest_product_definition" to target.definitionId)
        )
        player.sendMessage(message(player, "resource_collection.forest.completed", "amount" to amount))
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
            player.sendMessage(message(player, "resource_collection.error.miner_required"))
            return
        }
        if (ResourceMaterialPolicy.classify(block.type, block.blockData) != ResourceCollectionKind.MINERAL ||
            !isReadyNatural(block)) {
            player.sendMessage(message(player, "resource_collection.error.natural_resource_required"))
            return
        }
        val key = block.key()
        val current = chiselSessions[player.uniqueId]
        if (current == null) {
            val owner = occupiedBlocks[key]
            if (owner != null && owner != player.uniqueId) {
                player.sendMessage(message(player, "resource_collection.error.in_use"))
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
            player.sendMessage(message(player, "resource_collection.chisel.started"))
            return
        }
        if (current.blockKey != key || current.face != event.blockFace) {
            cancelChisel(player.uniqueId, "resource_collection.chisel.cancelled")
            return
        }
        val interaction = event.interactionPoint ?: return
        val tolerance = 0.22 + profile.precisionToleranceBonus
        val distance = interaction.distance(current.target)
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
        if (current.attempts >= 3) {
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
            cancelChisel(player.uniqueId, "resource_collection.chisel.cancelled")
            return
        }
        if (!validateChiselState(player, session, block)) {
            cancelChisel(player.uniqueId, "resource_collection.chisel.cancelled")
            return
        }
        if (!callProtectedBreak(block, player)) {
            cancelChisel(player.uniqueId, "resource_collection.error.protected")
            return
        }
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
        block.breakNaturally(ItemStack(Material.IRON_PICKAXE), true)
        if (player.gameMode != GameMode.CREATIVE) player.damageItemStack(EquipmentSlot.HAND, 1)
        if (specialAmount > 0) {
            val result = MineralCompanionPolicy.inspect(
                block.world.environment,
                block.biome.key.toString(),
                block.y
            )
            giveResource(player, result.resourceId, specialAmount)
        }
        awardSpecialist(
            player,
            ContentActionType.MINERAL_EXTRACTED,
            average >= profile.topEvaluationThreshold,
            session.originalMaterial
        )
        player.sendMessage(message(player, "resource_collection.chisel.completed", "amount" to specialAmount))
        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.9f, 1.2f)
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
            Runnable { cancelChisel(session.playerId, "resource_collection.chisel.timeout") },
            50L
        )
    }

    private fun showChiselTarget(player: Player, session: ChiselSession) {
        player.spawnParticle(Particle.END_ROD, session.target, 5, 0.025, 0.025, 0.025, 0.0)
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BELL, 0.55f, 1.65f)
    }

    private fun randomTarget(block: Block, face: BlockFace): Location {
        val center = block.location.add(0.5, 0.5, 0.5)
        val normal = face.direction.normalize().multiply(0.505)
        val (firstAxis, secondAxis) = faceAxes(face)
        val firstOffset = random.nextDouble() * 0.56 - 0.28
        val secondOffset = random.nextDouble() * 0.56 - 0.28
        return center.add(normal).add(firstAxis.multiply(firstOffset)).add(secondAxis.multiply(secondOffset))
    }

    private fun faceAxes(face: BlockFace): Pair<Vector, Vector> = when (face) {
        BlockFace.UP, BlockFace.DOWN -> Vector(1, 0, 0) to Vector(0, 0, 1)
        BlockFace.EAST, BlockFace.WEST -> Vector(0, 1, 0) to Vector(0, 0, 1)
        else -> Vector(1, 0, 0) to Vector(0, 1, 0)
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
            giveResource(player, "bark", 1)
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
            player.sendMessage(message(player, "resource_collection.error.lumberjack_required"))
            return
        }
        if (!isReadyWorld(block) || CCSystem.getAPI().getNaturalOriginRegistry().isNatural(block)) {
            player.sendMessage(message(player, "resource_collection.error.placed_log_required"))
            return
        }
        val planks = plankType(block.type) ?: run {
            player.sendMessage(message(player, "resource_collection.error.stripped_log_required"))
            return
        }
        if (!callProtectedBreak(block, player)) {
            player.sendMessage(message(player, "resource_collection.error.protected"))
            return
        }
        val originalMaterial = block.type
        block.type = Material.AIR
        if (precise) {
            giveResource(player, "timber_beam", profile.timberYield.takeIf { it > 0 } ?: 1)
        } else {
            val item = ItemStack(planks, profile.plankYield.takeIf { it > 0 } ?: 4)
            player.inventory.addItem(item).values.forEach { player.world.dropItemNaturally(player.location, it) }
        }
        if (player.gameMode != GameMode.CREATIVE) player.damageItemStack(EquipmentSlot.HAND, 1)
        awardSpecialist(player, ContentActionType.TREE_PROCESSED, false, originalMaterial)
        player.sendMessage(message(player, "resource_collection.woodworking.completed"))
        player.playSound(player.location, Sound.BLOCK_WOOD_BREAK, 0.8f, if (precise) 1.4f else 1.0f)
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

    private fun giveResource(player: Player, id: String, amount: Int) {
        giveCustomItem(player, "resource.$id", amount)
    }

    private fun giveCustomItem(player: Player, fullId: String, amount: Int) {
        if (amount <= 0) return
        val item = CustomItemManager.createItemForPlayer(fullId, player, amount) ?: return
        player.inventory.addItem(item).values.forEach { player.world.dropItemNaturally(player.location, it) }
    }

    private fun isReadyNatural(block: Block): Boolean =
        isReadyWorld(block) && CCSystem.getAPI().getNaturalOriginRegistry().isNatural(block)

    private fun isReadyWorld(block: Block): Boolean =
        CCSystem.getAPI().getResourceWorldLifecycleService().isReady(block.world.key)

    private fun cancelChisel(playerId: UUID, messageKey: String?) {
        val session = chiselSessions.remove(playerId) ?: return
        session.timeout?.cancel()
        occupiedBlocks.remove(session.blockKey)
        if (messageKey != null) Bukkit.getPlayer(playerId)?.sendMessage(message(Bukkit.getPlayer(playerId), messageKey))
    }

    @EventHandler(ignoreCancelled = true)
    fun onOtherBlockBreak(event: BlockBreakEvent) {
        val owner = occupiedBlocks[event.block.key()] ?: return
        if (owner != event.player.uniqueId) cancelChisel(owner, "resource_collection.chisel.cancelled")
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        cancelChisel(event.player.uniqueId, null)
        gatheringTargets.remove(event.player.uniqueId)
        gatheringCooldowns.remove(event.player.uniqueId)
        forestTargets.remove(event.player.uniqueId)
        forestCooldowns.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onWorldChanged(event: PlayerChangedWorldEvent) =
        cancelChisel(event.player.uniqueId, "resource_collection.chisel.cancelled")

    @EventHandler
    fun onHeldItemChanged(event: PlayerItemHeldEvent) =
        cancelChisel(event.player.uniqueId, "resource_collection.chisel.cancelled")

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

    private fun message(player: Player?, key: String, vararg values: Pair<String, Any>): Component =
        Component.text(CCSystem.getAPI().getI18nString(player, key, values.toMap()).replace('&', '§'))
}
