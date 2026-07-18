package jp.awabi2048.cccontent.features.resourcecollection

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.action.ContentAction
import com.awabi2048.ccsystem.api.action.ContentActionType
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.RankReleasePolicy
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.profile.LumberjackSkillProfile
import jp.awabi2048.cccontent.features.rank.profession.profile.MinerSkillProfile
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
import java.util.UUID

class SpecialistCollectionService(
    private val plugin: JavaPlugin,
    private val rankManager: RankManager,
    private val random: Random = Random()
) : Listener {
    companion object {
        private val internalBreaks = mutableSetOf<String>()

        fun isInternalBreak(playerId: UUID, block: Block): Boolean =
            internalBreaks.contains(internalBreakKey(playerId, block))

        private fun internalBreakKey(playerId: UUID, block: Block): String =
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
        var timeout: BukkitTask? = null
    )

    private val chiselSessions = mutableMapOf<UUID, ChiselSession>()
    private val occupiedBlocks = mutableMapOf<BlockKey, UUID>()

    fun shutdown() {
        chiselSessions.keys.toList().forEach { cancelChisel(it, null) }
        occupiedBlocks.clear()
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChiselBlockDamage(event: BlockDamageEvent) {
        if (CustomItemManager.identify(event.player.inventory.itemInMainHand)?.fullId == "resource.chisel") {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSpecialistInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val block = event.clickedBlock ?: return
        val customId = CustomItemManager.identify(event.player.inventory.itemInMainHand)?.fullId
        if (event.action == Action.LEFT_CLICK_BLOCK && customId == "resource.chisel") {
            handleChiselClick(event, block)
            return
        }
        if (event.action != Action.RIGHT_CLICK_BLOCK || !event.player.isSneaking) return
        when (customId) {
            "resource.woodworking_hatchet" -> processPlacedLog(event, block, precise = false)
            "resource.woodworking_knife" -> processPlacedLog(event, block, precise = true)
            else -> scheduleBarkReward(event, block)
        }
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
        current.scoreTotal += (1.0 - distance / tolerance).coerceIn(0.0, 1.0)
        current.attempts++
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
            profile.topEvaluationExtraMaterial
        )
        session.timeout?.cancel()
        chiselSessions.remove(player.uniqueId)
        occupiedBlocks.remove(session.blockKey)
        block.breakNaturally(ItemStack(Material.IRON_PICKAXE), true)
        if (player.gameMode != GameMode.CREATIVE) player.damageItemStack(EquipmentSlot.HAND, 1)
        if (specialAmount > 0) giveResource(player, mineralResourceId(block, session.originalMaterial), specialAmount)
        awardSpecialist(player, ContentActionType.MINERAL_EXTRACTED, average >= 0.90, session.originalMaterial)
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
        material: Material
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
                metadata = mapOf("material" to material.key.toString(), "specialist" to "true")
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
        if (amount <= 0) return
        val item = CustomItemManager.createItemForPlayer("resource.$id", player, amount) ?: return
        player.inventory.addItem(item).values.forEach { player.world.dropItemNaturally(player.location, it) }
    }

    private fun mineralResourceId(block: Block, original: Material): String = when {
        block.world.environment == org.bukkit.World.Environment.NETHER -> "sulfur"
        block.y < 0 -> "calcite_fragment"
        original == Material.COAL_ORE || original == Material.DEEPSLATE_COAL_ORE -> "rock_salt"
        else -> "mica_flake"
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
    fun onQuit(event: PlayerQuitEvent) = cancelChisel(event.player.uniqueId, null)

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
