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
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
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
import org.bukkit.block.data.Bisected

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
    private data class GatheringPlantSnapshot(
        val anchor: BlockKey,
        val physicalBlocks: List<GatheringBlockSnapshot>
    )
    private data class GatheringBlockSnapshot(
        val key: BlockKey,
        val material: Material,
        val blockData: String
    )
    private data class GatheringTarget(
        val patchId: String,
        val customItemId: String,
        val definitionId: String,
        val expiresAt: Instant,
        val anchor: BlockKey,
        val plants: List<GatheringPlantSnapshot>
    )
    private data class GatheringSession(
        val patchId: String,
        val heldSlot: Int,
        val heldItem: ItemStack,
        val task: BukkitTask
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
    private val gatheringTargets = mutableMapOf<UUID, MutableMap<String, GatheringTarget>>()
    private val gatheringTargetBlocks = mutableMapOf<UUID, MutableMap<BlockKey, String>>()
    private val gatheringLocks = mutableMapOf<String, UUID>()
    private val gatheringSessions = mutableMapOf<UUID, GatheringSession>()
    private val gatheringCooldowns = mutableMapOf<UUID, Instant>()
    private val forestTargets = mutableMapOf<UUID, MutableMap<BlockKey, ForestProductTarget>>()
    private val forestCooldowns = mutableMapOf<UUID, Instant>()
    private val feedbackTimestamps = mutableMapOf<Pair<UUID, String>, Instant>()
    private val tillableMaterials = setOf(Material.DIRT, Material.GRASS_BLOCK, Material.DIRT_PATH)
    private val forestProductHarvestStore = ForestProductHarvestStore(
        plugin.dataFolder.resolve("data/resource_collection/forest_product_harvests.yml")
    )

    fun shutdown() {
        chiselSessions.keys.toList().forEach(::cancelChisel)
        occupiedBlocks.clear()
        gatheringTargets.clear()
        gatheringTargetBlocks.clear()
        gatheringSessions.values.forEach { it.task.cancel() }
        gatheringSessions.clear()
        gatheringLocks.clear()
        gatheringCooldowns.clear()
        forestTargets.clear()
        forestCooldowns.clear()
        feedbackTimestamps.clear()
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
        val shouldCancel = customId == "resource.chisel" &&
            settings.isOperationEnabled(ResourceOperation.MINER_CHISEL)
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
        if (event.action == Action.RIGHT_CLICK_BLOCK && customId == "resource.gathering_sickle") {
            ifEnabled(ResourceOperation.FARMER_WILD_GATHERING) { startGathering(event, block) }
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
        if (profile == null || profile.level < 5 || !profile.expertOperationUnlocked ||
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onAreaHarvest(event: BlockBreakEvent) {
        val player = event.player
        val origin = event.block
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? FarmerSkillProfile ?: return
        if (!profile.areaHarvestEnabled ||
            !settings.isOperationEnabled(ResourceOperation.FARMER_AREA_HARVEST) ||
            !player.inventory.itemInMainHand.type.name.endsWith("_HOE") ||
            ResourceMaterialPolicy.classify(origin.type, origin.blockData) != ResourceCollectionKind.CROP) return
        if (!isReadyWorld(origin)) {
            return
        }
        val originMaterial = origin.type
        val radius = profile.operationRadius.coerceIn(1, 3)
        val candidates = buildList {
            for (x in -radius..radius) for (z in -radius..radius) {
                val block = origin.getRelative(x, 0, z)
                if (block != origin &&
                    ResourceMaterialPolicy.classify(block.type, block.blockData) == ResourceCollectionKind.CROP) {
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
        damageCultivationTool(player, processed, profile.durabilitySaveChance)
        awardFarmerArea(player, ContentActionType.CROP_HARVESTED, processed, originMaterial, "harvest")
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAreaTilling(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND || event.action != Action.RIGHT_CLICK_BLOCK) return
        val origin = event.clickedBlock ?: return
        val player = event.player
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? FarmerSkillProfile ?: return
        if (!profile.areaTillingEnabled ||
            !settings.isOperationEnabled(ResourceOperation.FARMER_AREA_TILLING) ||
            !player.inventory.itemInMainHand.type.name.endsWith("_HOE") ||
            origin.type !in tillableMaterials) return
        if (!isReadyWorld(origin)) {
            return
        }
        val originMaterial = origin.type
        val radius = profile.operationRadius.coerceIn(1, 3)
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!player.isOnline ||
                !player.inventory.itemInMainHand.type.name.endsWith("_HOE")) {
                return@Runnable
            }
            var processed = 0
            for (x in -radius..radius) for (z in -radius..radius) {
                val block = origin.getRelative(x, 0, z)
                if (block == origin) continue
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

    private fun inspectVegetation(event: PlayerInteractEvent, origin: Block) {
        event.isCancelled = true
        val player = event.player
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? FarmerSkillProfile
        if (profile == null || !profile.expertOperationUnlocked ||
            !RankReleasePolicy.canAccessProfession(player, Profession.FARMER)) {
            return
        }
        if (!isReadyWorld(origin) ||
            !ResourceMaterialPolicy.isWildVegetation(origin.type) ||
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
        val targets = mutableMapOf<String, GatheringTarget>()
        val targetBlocks = mutableMapOf<BlockKey, String>()
        val logicalPlants = mutableMapOf<GatheringPlantPosition, GatheringPlantSnapshot>()
        val candidates = mutableListOf<GatheringPatchCandidate>()
        val discoveredDefinitions = linkedSetOf<SeasonalPlantDefinition>()
        for (x in -radius..radius) for (y in -2..2) for (z in -radius..radius) {
            val anchor = logicalVegetationAnchor(origin.getRelative(x, y, z)) ?: continue
            val group = GatheringVegetationGroup.from(anchor.type) ?: continue
            if (logicalPlants.containsKey(anchor.position())) continue
            val physical = logicalVegetationBlocks(anchor)
            if (physical.any { !CCSystem.getAPI().getNaturalOriginRegistry().isNatural(it) }) continue
            logicalPlants[anchor.position()] = GatheringPlantSnapshot(
                anchor.key(),
                physical.map { GatheringBlockSnapshot(it.key(), it.type, it.blockData.asString) }
            )
            candidates += GatheringPatchCandidate(anchor.position(), group)
        }
        for (patch in GatheringPatchModel.build(origin.world.uid, candidates)) {
            val anchorBlock = origin.world.getBlockAt(patch.anchor.x, patch.anchor.y, patch.anchor.z)
            val seed = GatheringPatchModel.stableSeed(origin.world.seed, origin.world.uid, patch.anchor, season)
            val definition = seasonalPlants.selectStable(
                season, anchorBlock.type, anchorBlock.biome.key.toString(), anchorBlock.y, seed
            ) ?: continue
            if (CustomItemManager.getItem(definition.customItemId) == null) {
                plugin.logger.warning(
                    "[Resource Collection] 存在しない季節植物アイテムを無視しました: ${definition.customItemId}"
                )
                continue
            }
            val plants = patch.plants.mapNotNull(logicalPlants::get)
            if (plants.isEmpty()) continue
            targets[patch.id] = GatheringTarget(
                patch.id, definition.customItemId, definition.id, expiresAt, anchorBlock.key(), plants
            )
            plants.flatMap(GatheringPlantSnapshot::physicalBlocks).forEach { targetBlocks[it.key] = patch.id }
            discoveredDefinitions += definition
            plants.forEach { plant ->
                plant.anchor.resolve()?.let { targetBlock ->
                    player.spawnParticle(
                        Particle.HAPPY_VILLAGER, targetBlock.location.add(0.5, 0.7, 0.5),
                        3, 0.18, 0.15, 0.18, 0.0
                    )
                }
            }
        }
        player.swingMainHand()
        gatheringTargets[player.uniqueId] = targets
        gatheringTargetBlocks[player.uniqueId] = targetBlocks
        val cooldownSeconds = profile.inspectionCooldownSeconds.takeIf { it > 0 } ?: 45
        gatheringCooldowns[player.uniqueId] = now.plusSeconds(cooldownSeconds.toLong())
        if (targets.isEmpty()) {
            player.sendMessage(message(player, "resource_collection.display.hint.vegetation_none"))
            player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.25f)
            return
        }
        val collectibleHint = if (profile.detailedInspectionEnabled) {
            discoveredDefinitions
                .map { definition ->
                    CCSystem.getAPI().getI18nString(
                        player,
                        "custom_items.${definition.customItemId}.name"
                    )
                }
                .distinct()
                .joinToString(text(player, "resource_collection.display.list_separator"))
        } else {
            text(player, "resource_collection.display.hint.seasonal_plants")
        }
        val appraisalLines = buildList {
            add(GuiLoreLine.Data(
                text(player, "resource_collection.display.data.candidate_count"),
                targets.values.sumOf { it.plants.size },
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

    private fun startGathering(event: PlayerInteractEvent, block: Block) {
        val player = event.player
        val profile = rankManager.getTypedProfessionProfile(player.uniqueId) as? FarmerSkillProfile ?: return
        if (profile.level < 5 || !profile.expertOperationUnlocked ||
            !RankReleasePolicy.canAccessProfession(player, Profession.FARMER)) return
        val patchId = gatheringTargetBlocks[player.uniqueId]?.get(block.key()) ?: return
        val target = gatheringTargets[player.uniqueId]?.get(patchId) ?: return
        if (target.expiresAt.isBefore(CCSystem.getAPI().getSharedClockService().now().toInstant())) return
        val owner = gatheringLocks.putIfAbsent(patchId, player.uniqueId)
        if (owner != null && owner != player.uniqueId) return
        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.ALLOW)
        cancelGathering(player.uniqueId)
        gatheringLocks[patchId] = player.uniqueId
        val heldSlot = player.inventory.heldItemSlot
        val held = player.inventory.itemInMainHand.clone()
        lateinit var task: BukkitTask
        var ticks = 0
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            ticks++
            val targetedPatch = player.getTargetBlockExact(6, FluidCollisionMode.NEVER)?.key()?.let { key ->
                gatheringTargetBlocks[player.uniqueId]?.get(key)
            }
            val valid = player.isOnline && player.world.uid == block.world.uid &&
                CustomItemManager.identify(player.inventory.itemInMainHand)?.fullId == "resource.gathering_sickle" &&
                player.inventory.heldItemSlot == heldSlot && player.inventory.itemInMainHand.isSimilar(held) &&
                player.hasActiveItem() && targetedPatch == patchId &&
                validateGatheringTarget(player, target, checkProtection = false)
            if (!valid) {
                cancelGathering(player.uniqueId)
                return@Runnable
            }
            if (ticks >= 40) {
                player.clearActiveItem()
                task.cancel()
                gatheringSessions.remove(player.uniqueId)
                if (validateGatheringTarget(player, target, checkProtection = true)) {
                    completePlantGathering(player, target, profile)
                }
                gatheringLocks.remove(patchId, player.uniqueId)
            }
        }, 1L, 1L)
        gatheringSessions[player.uniqueId] = GatheringSession(patchId, heldSlot, held, task)
    }

    @EventHandler(ignoreCancelled = false)
    fun onGatheringSickleConsume(event: PlayerItemConsumeEvent) {
        if (CustomItemManager.identify(event.item)?.fullId == "resource.gathering_sickle") event.isCancelled = true
    }

    private fun validateGatheringTarget(player: Player, target: GatheringTarget, checkProtection: Boolean): Boolean {
        val anchor = target.anchor.resolve() ?: return false
        if (gatheringLocks[target.patchId] != player.uniqueId || !isReadyWorld(anchor)) return false
        return target.plants.flatMap(GatheringPlantSnapshot::physicalBlocks).all { snapshot ->
            val current = snapshot.key.resolve() ?: return@all false
            current.type == snapshot.material && current.blockData.asString == snapshot.blockData &&
                CCSystem.getAPI().getNaturalOriginRegistry().isNatural(current) &&
                (!checkProtection || callProtectedBreak(current, player))
        }
    }

    private fun completePlantGathering(
        player: Player,
        target: GatheringTarget,
        profile: FarmerSkillProfile
    ) {
        val baseYield = when (target.plants.size) { in 1..4 -> 1; in 5..8 -> 2; else -> 3 }
        val amount = baseYield + profile.guaranteedSpecialistPlantYield.coerceAtLeast(0) +
            if (random.nextDouble() < profile.specialistPlantExtraChance) 1 else 0
        val anchor = target.anchor.resolve() ?: return
        target.plants.flatMap(GatheringPlantSnapshot::physicalBlocks).forEach { snapshot ->
            val current = snapshot.key.resolve() ?: return
            playNaturalBreakEffect(current, 4)
            current.type = if (snapshot.material == Material.SEAGRASS || snapshot.material == Material.TALL_SEAGRASS) {
                Material.WATER
            } else {
                Material.AIR
            }
        }
        giveCustomItemOrDrop(player, target.customItemId, amount, anchor.location)
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
            target.plants.first().physicalBlocks.first().material,
            mapOf(
                "seasonal_plant_definition" to target.definitionId,
                "seasonal_patch" to "true"
            )
        )
        gatheringTargets[player.uniqueId]?.remove(target.patchId)
        gatheringTargetBlocks[player.uniqueId]?.entries?.removeIf { it.value == target.patchId }
    }

    private fun giveCustomItemOrDrop(player: Player, fullId: String, amount: Int, source: Location) {
        val item = CustomItemManager.createItemForPlayer(fullId, player, amount) ?: return
        player.inventory.addItem(item).values.forEach { leftover ->
            source.world?.dropItemNaturally(source.clone().add(0.5, 0.5, 0.5), leftover)
        }
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
            player.sendMessage(message(player, emptyResultKey))
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
        if (targets.isEmpty()) {
            player.sendMessage(message(player, "resource_collection.display.hint.forest_none"))
            player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.05f)
            return
        }
        val collectibleHint = if (profile.exactMaterialInspectionEnabled) {
            discoveredNames.joinToString(text(player, "resource_collection.display.list_separator"))
        } else {
            text(player, "resource_collection.display.hint.forest_products")
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

    private fun logicalVegetationAnchor(block: Block): Block? {
        if (GatheringVegetationGroup.from(block.type) == null) return null
        val bisected = block.blockData as? Bisected ?: return block
        return if (bisected.half == Bisected.Half.TOP) block.getRelative(BlockFace.DOWN) else block
    }

    private fun logicalVegetationBlocks(anchor: Block): List<Block> {
        val bisected = anchor.blockData as? Bisected ?: return listOf(anchor)
        if (bisected.half != Bisected.Half.BOTTOM) return emptyList()
        val upper = anchor.getRelative(BlockFace.UP)
        val upperData = upper.blockData as? Bisected
        return if (upper.type == anchor.type && upperData?.half == Bisected.Half.TOP) listOf(anchor, upper) else emptyList()
    }

    private fun Block.position(): GatheringPlantPosition = GatheringPlantPosition(x, y, z)

    private fun cancelGathering(playerId: UUID) {
        val session = gatheringSessions.remove(playerId) ?: return
        session.task.cancel()
        gatheringLocks.remove(session.patchId, playerId)
    }

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
        cancelGathering(event.player.uniqueId)
        gatheringTargets.remove(event.player.uniqueId)
        gatheringTargetBlocks.remove(event.player.uniqueId)
        gatheringCooldowns.remove(event.player.uniqueId)
        forestTargets.remove(event.player.uniqueId)
        forestCooldowns.remove(event.player.uniqueId)
        feedbackTimestamps.keys.removeIf { it.first == event.player.uniqueId }
    }

    @EventHandler
    fun onWorldChanged(event: PlayerChangedWorldEvent) {
        cancelChisel(event.player.uniqueId)
        cancelGathering(event.player.uniqueId)
    }

    @EventHandler
    fun onHeldItemChanged(event: PlayerItemHeldEvent) {
        cancelChisel(event.player.uniqueId)
        cancelGathering(event.player.uniqueId)
    }

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
