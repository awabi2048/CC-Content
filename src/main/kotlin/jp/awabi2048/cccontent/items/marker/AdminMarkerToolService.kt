package jp.awabi2048.cccontent.items.marker

import jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager
import jp.awabi2048.cccontent.features.sukima_dungeon.isSukimaDungeonWorld
import jp.awabi2048.cccontent.items.PoisonousPotatoComponentPack
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.EntityType
import org.bukkit.entity.Marker
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.UUID

class AdminMarkerToolService(private val plugin: JavaPlugin) : Listener {
    data class MarkerToolMode(
        val id: String,
        val tag: String,
        val displayName: String,
        val particle: Particle,
        val previewColor: Color
    )

    private data class MarkerToolDefinition(
        val toolId: String,
        val displayName: String,
        val itemModel: NamespacedKey,
        val modes: List<MarkerToolMode>,
        val unavailableMessage: (Player) -> String?
    )

    private data class DeletionPreview(
        val marker: Marker,
        val mode: MarkerToolMode,
        val block: Block,
        val distance: Double
    )

    companion object {
        private const val PREVIEW_OUTLINE_STEP = 0.125
        private const val MARKER_OUTLINE_STEP = 0.25
        private const val BLOCK_OUTLINE_DUST_SIZE = 0.5f
        private const val DELETE_PREVIEW_DUST_SIZE = 0.75f
        private const val PREVIEW_INTERVAL_TICKS = 2L
        private const val MARKER_PARTICLE_INTERVAL_TICKS = 10L
    }

    private val toolIdKey = NamespacedKey(plugin, "admin_marker_tool_type")
    private val modeIdKey = NamespacedKey(plugin, "admin_marker_tool_mode")
    private val lastSwitchTime = mutableMapOf<UUID, Long>()
    private val definitions = listOf(
        MarkerToolDefinition(
            toolId = "sukima_dungeon.marker_tool",
            displayName = "§dスキマ管理マーカーツール",
            itemModel = NamespacedKey.minecraft("blaze_rod"),
            modes = listOf(
                MarkerToolMode("mob", "sd.marker.mob", "§cMOB", Particle.FLAME, Color.fromRGB(255, 96, 96)),
                MarkerToolMode("npc", "sd.marker.npc", "§aNPC", Particle.HAPPY_VILLAGER, Color.fromRGB(96, 255, 128)),
                MarkerToolMode("item", "sd.marker.item", "§bITEM", Particle.SOUL_FIRE_FLAME, Color.fromRGB(96, 224, 255)),
                MarkerToolMode("sprout", "sd.marker.sprout", "§dSPROUT", Particle.GLOW, Color.fromRGB(255, 128, 224)),
                MarkerToolMode("spawn", "sd.marker.spawn", "§fSPAWN", Particle.CLOUD, Color.fromRGB(240, 240, 240))
            ),
            unavailableMessage = { player ->
                if (isSukimaDungeonWorld(player.world)) {
                    "§c[Marker] §fダンジョン内ではマーカー設置ツールを使用できません。"
                } else {
                    null
                }
            }
        ),
        MarkerToolDefinition(
            toolId = "arena.marker_tool",
            displayName = "§6アリーナ管理マーカーツール",
            itemModel = NamespacedKey.minecraft("blaze_rod"),
            modes = listOf(
                MarkerToolMode("mob", "arena.marker.mob", "§cMOB", Particle.FLAME, Color.fromRGB(255, 96, 96)),
                MarkerToolMode("entrance", "arena.marker.entrance", "§aENTRANCE", Particle.HAPPY_VILLAGER, Color.fromRGB(96, 255, 128)),
                MarkerToolMode("door_block", "arena.marker.door_block", "§eDOOR", Particle.WAX_OFF, Color.fromRGB(255, 224, 96)),
                MarkerToolMode("barrier_core", "arena.marker.barrier_core", "§bBARRIER", Particle.END_ROD, Color.fromRGB(96, 224, 255))
            ),
            unavailableMessage = { null }
        )
    ).associateBy { it.toolId }

    fun start() {
        startPreviewTask()
        startMarkerParticleTask()
    }

    fun createTool(toolId: String, player: Player? = null): ItemStack {
        val definition = definitions[toolId] ?: error("Unknown marker tool: $toolId")
        val item = ItemStack(Material.POISONOUS_POTATO)
        PoisonousPotatoComponentPack.applyNonConsumable(item)
        val meta = item.itemMeta ?: return item
        meta.setItemModel(definition.itemModel)
        meta.setDisplayName(definition.displayName)
        meta.persistentDataContainer.set(toolIdKey, PersistentDataType.STRING, definition.toolId)
        meta.persistentDataContainer.set(modeIdKey, PersistentDataType.STRING, definition.modes.first().id)
        updateLore(meta, definition, definition.modes.first(), player)
        item.itemMeta = meta
        return item
    }

    fun isTool(item: ItemStack?, toolId: String): Boolean {
        val meta = item?.itemMeta ?: return false
        return meta.persistentDataContainer.get(toolIdKey, PersistentDataType.STRING) == toolId
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val item = event.item ?: return
        val definition = resolveDefinition(item) ?: return
        if (event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK) return

        event.isCancelled = true
        val player = event.player
        definition.unavailableMessage(player)?.let {
            player.sendMessage(it)
            return
        }

        if (player.isSneaking) {
            val preview = resolveDeletionPreview(player, definition)
            if (preview != null) {
                preview.marker.remove()
                player.sendMessage("§b[Marker] ${preview.mode.displayName} §fマーカーを削除しました。")
                player.playSound(player.location, Sound.BLOCK_ANVIL_BREAK, 0.5f, 2.0f)
                return
            }
        }

        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val clickedBlock = event.clickedBlock ?: return
        val blockFace = event.blockFace
        val targetBlock = clickedBlock.getRelative(blockFace)
        val mode = getMode(item, definition)
        spawnMarker(targetBlock, mode)
        player.sendMessage("§b[Marker] ${mode.displayName} §fマーカーを設置しました。")
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f)
    }

    @EventHandler
    fun onSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand
        val definition = resolveDefinition(item) ?: return

        definition.unavailableMessage(player)?.let {
            player.sendMessage(it)
            return
        }

        val now = System.currentTimeMillis()
        val last = lastSwitchTime.getOrDefault(player.uniqueId, 0L)
        if (now - last < 50) {
            event.isCancelled = true
            return
        }
        lastSwitchTime[player.uniqueId] = now
        event.isCancelled = true

        val currentMode = getMode(item, definition)
        val modes = definition.modes
        val currentIndex = modes.indexOfFirst { it.id == currentMode.id }.coerceAtLeast(0)
        val newMode = modes[(currentIndex + 1) % modes.size]

        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(modeIdKey, PersistentDataType.STRING, newMode.id)
        updateLore(meta, definition, newMode, player)
        item.itemMeta = meta
        player.sendActionBar(Component.text("§7設置モードを ${newMode.displayName} §7に変更しました。"))
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
    }

    fun cleanupMarkers(world: World, tagPrefix: String) {
        world.entities.forEach { entity ->
            val hasTag = entity.scoreboardTags.any { it.startsWith(tagPrefix) }
            val isOldArmorStand = entity is org.bukkit.entity.ArmorStand && listOf("MOB", "NPC", "ITEM", "SPROUT", "SPAWN").contains(entity.customName)
            if (hasTag || isOldArmorStand) {
                entity.remove()
            }
        }
    }

    private fun resolveDefinition(item: ItemStack): MarkerToolDefinition? {
        val toolId = item.itemMeta?.persistentDataContainer?.get(toolIdKey, PersistentDataType.STRING) ?: return null
        return definitions[toolId]
    }

    private fun getMode(item: ItemStack, definition: MarkerToolDefinition): MarkerToolMode {
        val modeId = item.itemMeta?.persistentDataContainer?.get(modeIdKey, PersistentDataType.STRING)
        return definition.modes.firstOrNull { it.id == modeId } ?: definition.modes.first()
    }

    private fun updateLore(meta: ItemMeta, definition: MarkerToolDefinition, mode: MarkerToolMode, player: Player?) {
        val bar = if (definition.toolId == "sukima_dungeon.marker_tool") {
            MessageManager.getMessage(player, "common_bar")
        } else {
            "§8----------------"
        }
        meta.lore = listOf(
            bar,
            "§f§l| §7現在のモード §a${mode.displayName}",
            "",
            "§e右クリック(ブロック)§7 クリック面の外側にマーカーを設置",
            "§eShift + 右クリック§7 視線上のマーカーを削除",
            "§eFキー§7 次のモードへ変更",
            bar
        )
    }

    private fun spawnMarker(targetBlock: Block, mode: MarkerToolMode) {
        val world = targetBlock.world
        val markerLocation = targetBlock.location.add(0.5, 0.5, 0.5)
        val marker = world.spawnEntity(markerLocation, EntityType.MARKER) as Marker
        marker.addScoreboardTag(mode.tag)
    }

    private fun startPreviewTask() {
        object : BukkitRunnable() {
            override fun run() {
                for (player in plugin.server.onlinePlayers) {
                    val item = player.inventory.itemInMainHand
                    val definition = resolveDefinition(item) ?: continue
                    if (definition.unavailableMessage(player) != null) continue

                    if (player.isSneaking) {
                        val preview = resolveDeletionPreview(player, definition)
                        if (preview != null) {
                            drawDustBlockOutline(preview.block, Color.fromRGB(255, 64, 64), DELETE_PREVIEW_DUST_SIZE)
                            continue
                        }
                        val placementPreview = resolvePlacementPreview(player) ?: continue
                        val mode = getMode(item, definition)
                        drawDustBlockOutline(placementPreview, mode.previewColor, BLOCK_OUTLINE_DUST_SIZE)
                    } else {
                        val preview = resolvePlacementPreview(player) ?: continue
                        val mode = getMode(item, definition)
                        drawDustBlockOutline(preview, mode.previewColor, BLOCK_OUTLINE_DUST_SIZE)
                    }
                }
            }
        }.runTaskTimer(plugin, PREVIEW_INTERVAL_TICKS, PREVIEW_INTERVAL_TICKS)
    }

    private fun startMarkerParticleTask() {
        object : BukkitRunnable() {
            override fun run() {
                for (player in plugin.server.onlinePlayers) {
                    val definition = resolveDefinition(player.inventory.itemInMainHand) ?: continue
                    val tags = definition.modes.map { it.tag }.toSet()
                    player.getNearbyEntities(16.0, 16.0, 16.0)
                        .filterIsInstance<Marker>()
                        .forEach { marker ->
                            val mode = definition.modes.firstOrNull { marker.scoreboardTags.contains(it.tag) } ?: return@forEach
                            if (marker.scoreboardTags.none { it in tags }) return@forEach
                            drawParticleBlockOutline(marker.location.block, mode.particle)
                        }
                }
            }
        }.runTaskTimer(plugin, MARKER_PARTICLE_INTERVAL_TICKS, MARKER_PARTICLE_INTERVAL_TICKS)
    }

    private fun resolvePlacementPreview(player: Player): Block? {
        val reach = interactionReach(player)
        val rayTrace = player.world.rayTraceBlocks(
            player.eyeLocation,
            player.eyeLocation.direction,
            reach,
            FluidCollisionMode.NEVER,
            true
        ) ?: return null
        val hitBlock = rayTrace.hitBlock ?: return null
        val hitFace = rayTrace.hitBlockFace ?: return null
        return hitBlock.getRelative(hitFace)
    }

    private fun resolveDeletionPreview(player: Player, definition: MarkerToolDefinition): DeletionPreview? {
        val reach = interactionReach(player)
        val start = player.eyeLocation.toVector()
        val direction = player.eyeLocation.direction.clone().normalize()
        val blockingHit = player.world.rayTraceBlocks(
            player.eyeLocation,
            direction,
            reach,
            FluidCollisionMode.NEVER,
            true
        )

        return player.getNearbyEntities(reach + 1.0, reach + 1.0, reach + 1.0)
            .filterIsInstance<Marker>()
            .mapNotNull { marker ->
                val mode = definition.modes.firstOrNull { marker.scoreboardTags.contains(it.tag) } ?: return@mapNotNull null
                val block = marker.location.block
                val box = BoundingBox.of(block)
                val hitDistance = intersectRay(box, start, direction, reach) ?: return@mapNotNull null
                if (isBlockedBefore(blockingHit?.hitBlock, block, hitDistance, blockingHit?.hitPosition?.distance(start))) {
                    return@mapNotNull null
                }
                DeletionPreview(marker, mode, block, hitDistance)
            }
            .minByOrNull { it.distance }
    }

    private fun isBlockedBefore(hitBlock: Block?, targetBlock: Block, targetDistance: Double, blockingDistance: Double?): Boolean {
        if (hitBlock == null || blockingDistance == null) return false
        if (sameBlock(hitBlock, targetBlock)) return false
        return blockingDistance + 1.0E-4 < targetDistance
    }

    private fun sameBlock(first: Block, second: Block): Boolean {
        return first.world.uid == second.world.uid &&
            first.x == second.x &&
            first.y == second.y &&
            first.z == second.z
    }

    private fun interactionReach(player: Player): Double {
        val attribute = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)?.value
        return (attribute ?: 4.5).coerceAtLeast(1.0)
    }

    private fun intersectRay(box: BoundingBox, start: Vector, direction: Vector, maxDistance: Double): Double? {
        var tMin = 0.0
        var tMax = maxDistance

        fun updateAxis(startCoord: Double, dirCoord: Double, minCoord: Double, maxCoord: Double): Boolean {
            if (kotlin.math.abs(dirCoord) < 1.0E-9) {
                return startCoord in minCoord..maxCoord
            }
            var t1 = (minCoord - startCoord) / dirCoord
            var t2 = (maxCoord - startCoord) / dirCoord
            if (t1 > t2) {
                val swap = t1
                t1 = t2
                t2 = swap
            }
            if (t1 > tMin) tMin = t1
            if (t2 < tMax) tMax = t2
            return tMin <= tMax
        }

        if (!updateAxis(start.x, direction.x, box.minX, box.maxX)) return null
        if (!updateAxis(start.y, direction.y, box.minY, box.maxY)) return null
        if (!updateAxis(start.z, direction.z, box.minZ, box.maxZ)) return null
        return if (tMax >= 0.0 && tMin <= maxDistance) tMin.coerceAtLeast(0.0) else null
    }

    private fun drawDustBlockOutline(block: Block, color: Color, size: Float) {
        val world = block.world
        val dust = Particle.DustOptions(color, size)
        for ((x, y, z) in outlinePoints(block, PREVIEW_OUTLINE_STEP)) {
            world.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }
    }

    private fun drawParticleBlockOutline(block: Block, particle: Particle) {
        val world = block.world
        for ((x, y, z) in outlinePoints(block, MARKER_OUTLINE_STEP)) {
            world.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun outlinePoints(block: Block, stepSize: Double): Set<Triple<Double, Double, Double>> {
        val minX = block.x.toDouble()
        val minY = block.y.toDouble()
        val minZ = block.z.toDouble()
        val maxX = minX + 1.0
        val maxY = minY + 1.0
        val maxZ = minZ + 1.0
        val points = mutableSetOf<Triple<Double, Double, Double>>()

        fun addEdge(from: Triple<Double, Double, Double>, to: Triple<Double, Double, Double>) {
            val dx = to.first - from.first
            val dy = to.second - from.second
            val dz = to.third - from.third
            val length = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy), kotlin.math.abs(dz))
            val steps = maxOf(1, kotlin.math.ceil(length / stepSize).toInt())
            for (step in 0..steps) {
                val progress = step.toDouble() / steps.toDouble()
                points.add(
                    Triple(
                        from.first + dx * progress,
                        from.second + dy * progress,
                        from.third + dz * progress
                    )
                )
            }
        }

        val corners = mapOf(
            "000" to Triple(minX, minY, minZ),
            "001" to Triple(minX, minY, maxZ),
            "010" to Triple(minX, maxY, minZ),
            "011" to Triple(minX, maxY, maxZ),
            "100" to Triple(maxX, minY, minZ),
            "101" to Triple(maxX, minY, maxZ),
            "110" to Triple(maxX, maxY, minZ),
            "111" to Triple(maxX, maxY, maxZ)
        )

        addEdge(corners.getValue("000"), corners.getValue("001"))
        addEdge(corners.getValue("000"), corners.getValue("010"))
        addEdge(corners.getValue("000"), corners.getValue("100"))
        addEdge(corners.getValue("111"), corners.getValue("110"))
        addEdge(corners.getValue("111"), corners.getValue("101"))
        addEdge(corners.getValue("111"), corners.getValue("011"))
        addEdge(corners.getValue("001"), corners.getValue("011"))
        addEdge(corners.getValue("001"), corners.getValue("101"))
        addEdge(corners.getValue("010"), corners.getValue("011"))
        addEdge(corners.getValue("010"), corners.getValue("110"))
        addEdge(corners.getValue("100"), corners.getValue("101"))
        addEdge(corners.getValue("100"), corners.getValue("110"))

        return points
    }
}
