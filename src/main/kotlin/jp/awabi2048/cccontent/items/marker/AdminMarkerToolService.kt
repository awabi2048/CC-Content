@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.items.marker

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import jp.awabi2048.cccontent.features.arena.ArenaI18n
import jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager
import jp.awabi2048.cccontent.features.sukima_dungeon.isSukimaDungeonWorld
import jp.awabi2048.cccontent.items.PoisonousPotatoComponentPack
import jp.awabi2048.cccontent.structure.SchemStructureService
import jp.awabi2048.cccontent.structure.StructureSchemas
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.title.Title
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
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.time.Duration
import java.util.UUID
import kotlin.math.round

class AdminMarkerToolService(private val plugin: JavaPlugin) : Listener {
    data class MarkerToolMode(
        val id: String,
        val tag: String,
        val nameKey: String,
        val fallbackName: String,
        val particle: Particle,
        val previewColor: Color
    )

    private data class MarkerToolDefinition(
        val toolId: String,
        val displayName: String,
        val itemModel: NamespacedKey,
        val messageKeyPrefix: String,
        val modes: List<MarkerToolMode>,
        val unavailableMessage: (Player) -> String?
    )

    private data class DeletionPreview(
        val marker: Marker,
        val mode: MarkerToolMode,
        val location: Location,
        val distance: Double
    )

    companion object {
        private const val PREVIEW_OUTLINE_STEP = 0.125
        private const val MARKER_OUTLINE_STEP = 0.25
        private const val BLOCK_OUTLINE_DUST_SIZE = 0.5f
        private const val DELETE_PREVIEW_DUST_SIZE = 0.75f
        private const val PREVIEW_INTERVAL_TICKS = 2L
        private const val MARKER_PARTICLE_INTERVAL_TICKS = 10L
        private const val ARENA_LIFT_STRUCTURE_PATH = "structures/arena/lift.schem"
        private const val MODE_SWITCH_COOLDOWN_MILLIS = 50L
        private const val DELETE_COOLDOWN_MILLIS = 150L
    }

    private val toolIdKey = NamespacedKey(plugin, "admin_marker_tool_type")
    private val modeIdKey = NamespacedKey(plugin, "admin_marker_tool_mode")
    private val structureService = SchemStructureService(plugin)
    private val lastSwitchTime = mutableMapOf<UUID, Long>()
    private val lastDeleteTime = mutableMapOf<UUID, Long>()
    private var cachedArenaLiftSize: Triple<Int, Int, Int>? = null
    private val definitions = listOf(
        MarkerToolDefinition(
            toolId = "sukima_dungeon.marker_tool",
            displayName = "§dスキマ管理マーカーツール",
            itemModel = NamespacedKey.minecraft("blaze_rod"),
            messageKeyPrefix = "marker",
            modes = listOf(
                MarkerToolMode("mob", "sd.marker.mob", "modes.mob", "§cモブ", Particle.FLAME, Color.fromRGB(255, 96, 96)),
                MarkerToolMode("npc", "sd.marker.npc", "modes.npc", "§aNPC", Particle.HAPPY_VILLAGER, Color.fromRGB(96, 255, 128)),
                MarkerToolMode("item", "sd.marker.item", "modes.item", "§bアイテム", Particle.SOUL_FIRE_FLAME, Color.fromRGB(96, 224, 255)),
                MarkerToolMode("sprout", "sd.marker.sprout", "modes.sprout", "§d芽", Particle.GLOW, Color.fromRGB(255, 128, 224)),
                MarkerToolMode("spawn", "sd.marker.spawn", "modes.spawn", "§fスポーン", Particle.CLOUD, Color.fromRGB(240, 240, 240))
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
            toolId = "arena.structure_marker_tool",
            displayName = "§6アリーナ構造マーカーツール",
            itemModel = NamespacedKey.minecraft("blaze_rod"),
            messageKeyPrefix = "marker",
            modes = listOf(
                MarkerToolMode("mob", "arena.marker.mob", "modes.mob", "§cモブ", Particle.FLAME, Color.fromRGB(255, 96, 96)),
                MarkerToolMode("checkpoint", "arena.marker.checkpoint", "modes.checkpoint", "§aチェックポイント", Particle.HAPPY_VILLAGER, Color.fromRGB(96, 255, 128)),
                MarkerToolMode("connection_in", StructureSchemas.ARENA_CONNECTION_IN_TAG, "modes.connection_in", "§6接続(入口)", Particle.CRIT, Color.fromRGB(255, 176, 64)),
                MarkerToolMode("connection_out", StructureSchemas.ARENA_CONNECTION_OUT_TAG, "modes.connection_out", "§b接続(出口)", Particle.CRIT, Color.fromRGB(96, 176, 255)),
                MarkerToolMode("door_block", "arena.marker.door_block", "modes.door_block", "§e扉", Particle.WAX_OFF, Color.fromRGB(255, 224, 96)),
                MarkerToolMode("barrier_core", "arena.marker.barrier_core", "modes.barrier_core", "§b結界核", Particle.END_ROD, Color.fromRGB(96, 224, 255)),
                MarkerToolMode("barrier_point", "arena.marker.barrier_point", "modes.barrier_point", "§3結界起動点", Particle.GLOW, Color.fromRGB(128, 200, 255)),
                MarkerToolMode("pedestal", "arena.marker.pedestal", "modes.pedestal", "§d祭壇", Particle.END_ROD, Color.fromRGB(220, 160, 255))
            ),
            unavailableMessage = { null }
        ),
        MarkerToolDefinition(
            toolId = "arena.other_marker_tool",
            displayName = "§6アリーナ補助マーカーツール",
            itemModel = NamespacedKey.minecraft("blaze_rod"),
            messageKeyPrefix = "marker",
            modes = listOf(
                MarkerToolMode("join_area", "arena.marker.join_area", "modes.join_area", "§f参加エリア", Particle.END_ROD, Color.fromRGB(240, 240, 240)),
                MarkerToolMode("lobby", "arena.marker.lobby", "modes.lobby", "§bロビー(帰還)", Particle.SOUL, Color.fromRGB(96, 224, 255)),
                MarkerToolMode("lobby_main", "arena.marker.lobby_main", "modes.lobby_main", "§bロビー中央", Particle.SOUL_FIRE_FLAME, Color.fromRGB(96, 200, 255)),
                MarkerToolMode("lobby_tutorial_start", "arena.marker.lobby_tutorial_start", "modes.lobby_tutorial_start", "§eチュートリアル開始", Particle.WAX_OFF, Color.fromRGB(255, 216, 96)),
                MarkerToolMode("lobby_tutorial_step", "arena.marker.lobby_tutorial_step", "modes.lobby_tutorial_step", "§6チュートリアル途中", Particle.GLOW, Color.fromRGB(255, 176, 64))
            ),
            unavailableMessage = { null }
        ),
        MarkerToolDefinition(
            toolId = "arena.lift_tool",
            displayName = "§6アリーナリフト設置ツール",
            itemModel = NamespacedKey.minecraft("blaze_rod"),
            messageKeyPrefix = "marker",
            modes = listOf(
                MarkerToolMode("lift", "arena.marker.lift", "modes.lift", "§dリフト", Particle.END_ROD, Color.fromRGB(216, 128, 255))
            ),
            unavailableMessage = { null }
        ),
        MarkerToolDefinition(
            toolId = "arena.mechanic_marker_tool",
            displayName = "§6アリーナギミックマーカーツール",
            itemModel = NamespacedKey.minecraft("blaze_rod"),
            messageKeyPrefix = "marker",
            modes = listOf(
                // Theme mechanics interpret these tags directly; the marker tool only preserves builder intent.
                MarkerToolMode("nether_track_path_left", "arena.marker.mechanic.nether.track_path.left", "modes.nether_track_path_left", "§cネザー: トロッコ経路L", Particle.FLAME, Color.fromRGB(255, 96, 64)),
                MarkerToolMode("nether_track_path_right", "arena.marker.mechanic.nether.track_path.right", "modes.nether_track_path_right", "§cネザー: トロッコ経路R", Particle.SOUL_FIRE_FLAME, Color.fromRGB(255, 128, 96)),
                MarkerToolMode("nether_magma_vent", "arena.marker.mechanic.nether.magma_vent", "modes.nether_magma_vent", "§6ネザー: マグマ噴出口", Particle.LAVA, Color.fromRGB(255, 144, 32)),
                MarkerToolMode("ocean_geyser", "arena.marker.mechanic.ocean_monument.geyser", "modes.ocean_geyser", "§b海底神殿: 間欠泉", Particle.BUBBLE_COLUMN_UP, Color.fromRGB(96, 224, 255)),
                MarkerToolMode("ocean_whirlpool", "arena.marker.mechanic.ocean_monument.whirlpool", "modes.ocean_whirlpool", "§3海底神殿: 渦潮", Particle.BUBBLE, Color.fromRGB(64, 160, 255)),
                MarkerToolMode("natura_stalactite", "arena.marker.mechanic.natura.stalactite", "modes.natura_stalactite", "§7繁茂洞窟: 鍾乳石", Particle.CRIT, Color.fromRGB(180, 180, 180)),
                MarkerToolMode("natura_mist", "arena.marker.mechanic.natura.mist", "modes.natura_mist", "§a繁茂洞窟: 霧", Particle.SPORE_BLOSSOM_AIR, Color.fromRGB(120, 200, 80))
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

        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val clickedBlock = event.clickedBlock ?: return
        val blockFace = event.blockFace
        val mode = getMode(item, definition)
        val placementLocation = resolvePlacementLocation(player, clickedBlock, blockFace)
        val marker = spawnMarker(placementLocation, mode, alignedYaw(player.location.yaw))
        if (definition.toolId == "arena.other_marker_tool" && mode.id == "lobby_tutorial_step") {
            val world = placementLocation.world
            if (world != null && marker != null) {
                val maxIndex = world.getEntitiesByClass(Marker::class.java)
                    .asSequence()
                    .filter { it.uniqueId != marker.uniqueId }
                    .filter { it.scoreboardTags.contains("arena.marker.lobby_tutorial_step") }
                    .mapNotNull { existing ->
                        existing.scoreboardTags
                            .firstOrNull { it.startsWith("arena.marker.lobby_tutorial_step.index.") }
                            ?.removePrefix("arena.marker.lobby_tutorial_step.index.")
                            ?.toIntOrNull()
                    }
                    .maxOrNull()
                    ?: 0
                val nextIndex = maxIndex + 1
                marker.addScoreboardTag("arena.marker.lobby_tutorial_step.index.$nextIndex")
                player.sendMessage(
                    text(
                        definition,
                        player,
                        "tutorial_step_number_assigned",
                        "§b[Marker] §fチュートリアル途中マーカー番号 §e{index} §fを設定しました。",
                        "index" to nextIndex
                    )
                )
            }
        }
        player.sendMessage(text(definition, player, "placed", "§b[Marker] {mode} §fマーカーを設置しました。", "mode" to getModeDisplayName(player, definition, mode)))
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f)
    }

    @EventHandler
    fun onSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand
        val definition = resolveDefinition(item) ?: return
        event.isCancelled = true

        definition.unavailableMessage(player)?.let {
            player.sendMessage(it)
            return
        }

        val now = System.currentTimeMillis()
        val last = lastDeleteTime.getOrDefault(player.uniqueId, 0L)
        if (now - last < DELETE_COOLDOWN_MILLIS) {
            return
        }
        lastDeleteTime[player.uniqueId] = now

        val preview = resolveDeletionPreview(player, definition)
        if (preview == null) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.6f, 0.7f)
            return
        }
        preview.marker.remove()
        player.sendMessage(text(definition, player, "removed", "§b[Marker] {mode} §fマーカーを削除しました。", "mode" to getModeDisplayName(player, definition, preview.mode)))
        player.playSound(player.location, Sound.BLOCK_ANVIL_BREAK, 0.5f, 2.0f)
    }

    @EventHandler
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        if (!player.isSneaking) return

        val item = player.inventory.itemInMainHand
        val definition = resolveDefinition(item) ?: return
        event.isCancelled = true

        definition.unavailableMessage(player)?.let {
            player.sendMessage(it)
            return
        }

        val now = System.currentTimeMillis()
        val last = lastSwitchTime.getOrDefault(player.uniqueId, 0L)
        if (now - last < MODE_SWITCH_COOLDOWN_MILLIS) {
            return
        }
        lastSwitchTime[player.uniqueId] = now

        val forwardDistance = (event.newSlot - event.previousSlot).floorMod(9)
        val step = if (forwardDistance in 1..4) 1 else -1
        switchMode(player, item, definition, step)
    }

    private fun switchMode(player: Player, item: ItemStack, definition: MarkerToolDefinition, step: Int) {
        val currentMode = getMode(item, definition)
        val modes = definition.modes
        val currentIndex = modes.indexOfFirst { it.id == currentMode.id }.coerceAtLeast(0)
        val newMode = modes[(currentIndex + step).floorMod(modes.size)]

        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(modeIdKey, PersistentDataType.STRING, newMode.id)
        updateLore(meta, definition, newMode, player)
        item.itemMeta = meta
        player.sendActionBar(Component.text(text(definition, player, "mode_changed", "§7設置モードを {mode} §7に変更しました。", "mode" to getModeDisplayName(player, definition, newMode))))
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
    }

    fun cleanupMarkers(world: World, tagPrefix: String) {
        world.entities.forEach { entity ->
            val hasTag = entity.scoreboardTags.any { it.startsWith(tagPrefix) }
            if (hasTag) {
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

    private fun getModeDisplayName(player: Player?, definition: MarkerToolDefinition, mode: MarkerToolMode): String {
        return text(definition, player, mode.nameKey, mode.fallbackName)
    }

    private fun text(
        definition: MarkerToolDefinition,
        player: Player?,
        keySuffix: String,
        fallback: String,
        vararg placeholders: Pair<String, Any?>
    ): String {
        val fullKey = if (definition.toolId.startsWith("arena.")) {
            "arena.${definition.messageKeyPrefix}.$keySuffix"
        } else {
            "${definition.messageKeyPrefix}.$keySuffix"
        }

        return when (definition.toolId) {
            "arena.structure_marker_tool", "arena.other_marker_tool", "arena.lift_tool", "arena.mechanic_marker_tool" ->
                ArenaI18n.text(player, fullKey, *placeholders)
            "sukima_dungeon.marker_tool" -> MessageManager.getMessage(
                player,
                fullKey,
                placeholders.associate { it.first to (it.second?.toString() ?: "null") }
            )
            else -> fallback
        }
    }

    private fun updateLore(meta: ItemMeta, definition: MarkerToolDefinition, mode: MarkerToolMode, player: Player?) {
        val modeName = getModeDisplayName(player, definition, mode)
        meta.lore(
            CCSystem.getAPI().getLoreService().render(
                GuiLoreSpec.Blocks(listOf(GuiLoreBlock(buildList {
                    add(GuiLoreLine.Raw(text(definition, player, "current_mode", "§f§l| §7現在のモード §a{mode}", "mode" to modeName)))
                    add(GuiLoreLine.Spacer)
                    add(GuiLoreLine.Raw(text(definition, player, "usage.place", "§e右クリック(ブロック)§7 クリック面の外側にマーカーを設置")))
                    add(GuiLoreLine.Raw(text(definition, player, "usage.delete", "§eFキー§7 視線上のマーカーを削除")))
                    add(GuiLoreLine.Raw(text(definition, player, "usage.switch", "§eShift + ホットバースクロール§7 前後のモードへ変更")))
                })))
            )
        )
    }

    private fun spawnMarker(markerLocation: Location, mode: MarkerToolMode, facingYaw: Float): Marker? {
        val world = markerLocation.world
        if (world == null) return null
        val marker = world.spawnEntity(markerLocation, EntityType.MARKER) as Marker
        marker.addScoreboardTag(mode.tag)
        // 設置時の向き（yaw）を記録。connection_in/out 等の方向を持つマーカーで参照される。
        marker.addScoreboardTag("marker.facing.${facingYaw.toInt().mod(360)}")
        return marker
    }

    private fun resolvePlacementLocation(player: Player, clickedBlock: Block, blockFace: BlockFace): Location {
        if (player.isSneaking) {
            val rayTrace = placementRayTrace(player)
            val hitBlock = rayTrace?.hitBlock
            val hitFace = rayTrace?.hitBlockFace
            if (hitBlock != null && hitFace != null) {
                return snapMarkerToFace(hitBlock, hitFace, rayTrace.hitPosition)
            }
        }

        val rayTrace = placementRayTrace(player)
        val hitPosition = if (rayTrace?.hitBlock == clickedBlock && rayTrace.hitBlockFace == blockFace) {
            rayTrace.hitPosition
        } else {
            null
        }
        return if (hitPosition != null) {
            snapMarkerToFace(clickedBlock, blockFace, hitPosition)
        } else {
            markerFaceCenter(clickedBlock, blockFace)
        }
    }

    private fun placementRayTrace(player: Player) = player.world.rayTraceBlocks(
        player.eyeLocation,
        player.eyeLocation.direction,
        interactionReach(player),
        FluidCollisionMode.NEVER,
        true
    )

    private fun markerFaceCenter(block: Block, face: BlockFace): Location {
        val centerHit = Vector(block.x + 0.5, block.y + 0.5, block.z + 0.5)
        return snapMarkerToFace(block, face, centerHit)
    }

    private fun snapMarkerToFace(block: Block, face: BlockFace, hitPosition: Vector): Location {
        return when {
            face.modX != 0 -> Location(
                block.world,
                block.x + 0.5 + face.modX,
                snapFaceGrid(hitPosition.y, block.y),
                snapFaceGrid(hitPosition.z, block.z)
            )
            face.modY != 0 -> Location(
                block.world,
                snapFaceGrid(hitPosition.x, block.x),
                block.y + 0.5 + face.modY,
                snapFaceGrid(hitPosition.z, block.z)
            )
            face.modZ != 0 -> Location(
                block.world,
                snapFaceGrid(hitPosition.x, block.x),
                snapFaceGrid(hitPosition.y, block.y),
                block.z + 0.5 + face.modZ
            )
            else -> block.location.clone().add(0.5, 0.5, 0.5)
        }
    }

    private fun snapFaceGrid(value: Double, blockCoord: Int): Double {
        val local = value - blockCoord.toDouble()
        val snapped = round(local * 2.0) / 2.0
        return blockCoord.toDouble() + snapped.coerceIn(0.0, 0.5)
    }

    private fun startPreviewTask() {
        object : BukkitRunnable() {
            override fun run() {
                for (player in plugin.server.onlinePlayers) {
                    val item = player.inventory.itemInMainHand
                    val definition = resolveDefinition(item) ?: continue
                    if (definition.unavailableMessage(player) != null) continue

                    val mode = getMode(item, definition)
                    val modeName = getModeDisplayName(player, definition, mode)
                    player.sendActionBar(Component.text("§7§l| §f$modeName"))

                    val deletionPreview = resolveDeletionPreview(player, definition)
                    if (deletionPreview != null) {
                        drawDustLocationOutline(deletionPreview.location, Color.fromRGB(255, 64, 64), DELETE_PREVIEW_DUST_SIZE)
                        showDeletionSubtitle(player, definition, deletionPreview.mode)
                        continue
                    }

                    val preview = resolvePlacementPreview(player) ?: continue
                    drawPlacementPreview(preview, mode, alignedYaw(player.location.yaw))
                }
            }
        }.runTaskTimer(plugin, PREVIEW_INTERVAL_TICKS, PREVIEW_INTERVAL_TICKS)
    }

    private fun drawPlacementPreview(location: Location, mode: MarkerToolMode, facingYaw: Float) {
        if (mode.id == "lift") {
            val world = location.world ?: return
            val liftSize = resolveArenaLiftSize()
            if (liftSize != null) {
                val minX = location.blockX.toDouble()
                val minY = location.blockY.toDouble()
                val minZ = location.blockZ.toDouble()
                val maxX = minX + liftSize.first.toDouble()
                val maxY = minY + liftSize.second.toDouble()
                val maxZ = minZ + liftSize.third.toDouble()
                drawDustOutline(world, minX, minY, minZ, maxX, maxY, maxZ, mode.previewColor, BLOCK_OUTLINE_DUST_SIZE)
                return
            }
        }
        drawDustLocationCubeOutline(location, 0.5, mode.previewColor, BLOCK_OUTLINE_DUST_SIZE)
        // 保存される facing と見た目がずれないよう、プレビューの矢印も4方向へ丸める。
        drawDirectionArrow(location, facingYaw, mode.previewColor)
    }

    /**
     * 水平面上に方向を示す矢印を描画する。
     * MWM の WorldSettingsListener.spawnDirectionArrow と同じ形状（主軸＋左右の矢羽）。
     */
    private fun drawDirectionArrow(location: Location, yaw: Float, color: Color) {
        val world = location.world ?: return
        val rad = Math.toRadians(alignedYaw(yaw).toDouble())
        val forwardX = -kotlin.math.sin(rad)
        val forwardZ = kotlin.math.cos(rad)

        val dust = Particle.DustOptions(color, BLOCK_OUTLINE_DUST_SIZE)
        val centerX = location.blockX + 0.5
        val centerY = location.blockY + 0.65
        val centerZ = location.blockZ + 0.5

        // 主軸の中央をブロック中央に合わせる。
        val tailX = centerX - forwardX * 0.55
        val tailZ = centerZ - forwardZ * 0.55
        val tipX = centerX + forwardX * 0.55
        val tipZ = centerZ + forwardZ * 0.55
        drawDustLine(world, tailX, centerY, tailZ, tipX, centerY, tipZ, dust)

        // 矢羽: 先端から左右に広がる2本
        val baseX = tipX - forwardX * 0.4
        val baseZ = tipZ - forwardZ * 0.4
        val sideX = -forwardZ * 0.2
        val sideZ = forwardX * 0.2
        drawDustLine(world, tipX, centerY, tipZ, baseX + sideX, centerY, baseZ + sideZ, dust)
        drawDustLine(world, tipX, centerY, tipZ, baseX - sideX, centerY, baseZ - sideZ, dust)
    }

    private fun drawDustLine(
        world: World,
        fromX: Double, fromY: Double, fromZ: Double,
        toX: Double, toY: Double, toZ: Double,
        dust: Particle.DustOptions
    ) {
        val dx = toX - fromX
        val dy = toY - fromY
        val dz = toZ - fromZ
        val length = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy), kotlin.math.abs(dz))
        val steps = maxOf(1, kotlin.math.ceil(length / PREVIEW_OUTLINE_STEP).toInt())
        for (step in 0..steps) {
            val progress = step.toDouble() / steps.toDouble()
            world.spawnParticle(
                Particle.DUST,
                fromX + dx * progress,
                fromY + dy * progress,
                fromZ + dz * progress,
                1, 0.0, 0.0, 0.0, 0.0, dust
            )
        }
    }

    private fun resolveArenaLiftSize(): Triple<Int, Int, Int>? {
        cachedArenaLiftSize?.let { return it }
        val structure = structureService.load(ARENA_LIFT_STRUCTURE_PATH) ?: return null
        val size = structure.size
        return Triple(size.x, size.y, size.z).also { cachedArenaLiftSize = it }
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
                            drawMarkerParticleOutline(marker.location, mode)
                        }
                }
            }
        }.runTaskTimer(plugin, MARKER_PARTICLE_INTERVAL_TICKS, MARKER_PARTICLE_INTERVAL_TICKS)
    }

    private fun resolvePlacementPreview(player: Player): Location? {
        val reach = interactionReach(player)
        val rayTrace = player.world.rayTraceBlocks(
            player.eyeLocation,
            player.eyeLocation.direction,
            reach,
            FluidCollisionMode.NEVER,
            true
        ) ?: return null
        val hitBlock = rayTrace.hitBlock ?: return null
        val face = rayTrace.hitBlockFace ?: return null
        return snapMarkerToFace(hitBlock, face, rayTrace.hitPosition)
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
                val location = marker.location.clone()
                val box = markerBoundingBox(location)
                val hitDistance = intersectRay(box, start, direction, reach) ?: return@mapNotNull null
                if (isBlockedBefore(blockingHit?.hitBlock, location, hitDistance, blockingHit?.hitPosition?.distance(start))) {
                    return@mapNotNull null
                }
                DeletionPreview(marker, mode, location, hitDistance)
            }
            .minByOrNull { it.distance }
    }

    private fun markerBoundingBox(location: Location): BoundingBox {
        return BoundingBox(
            location.x - 0.25,
            location.y - 0.25,
            location.z - 0.25,
            location.x + 0.25,
            location.y + 0.25,
            location.z + 0.25
        )
    }

    private fun isBlockedBefore(hitBlock: Block?, targetLocation: Location, targetDistance: Double, blockingDistance: Double?): Boolean {
        if (hitBlock == null || blockingDistance == null) return false
        val targetBlock = targetLocation.block
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

    private fun drawDustLocationOutline(location: Location, color: Color, size: Float) {
        drawDustLocationCubeOutline(location, 0.25, color, size)
    }

    private fun drawDustLocationCubeOutline(location: Location, halfSize: Double, color: Color, size: Float) {
        val world = location.world ?: return
        drawDustOutline(
            world,
            location.x - halfSize,
            location.y - halfSize,
            location.z - halfSize,
            location.x + halfSize,
            location.y + halfSize,
            location.z + halfSize,
            color,
            size
        )
    }

    private fun drawDustOutline(
        world: World,
        minX: Double,
        minY: Double,
        minZ: Double,
        maxX: Double,
        maxY: Double,
        maxZ: Double,
        color: Color,
        size: Float
    ) {
        val dust = Particle.DustOptions(color, size)
        for ((x, y, z) in outlinePoints(minX, minY, minZ, maxX, maxY, maxZ, PREVIEW_OUTLINE_STEP)) {
            world.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
        }
    }

    private fun drawMarkerParticleOutline(location: Location, mode: MarkerToolMode) {
        drawDustLocationCubeOutline(location, 0.32, mode.previewColor, 0.55f)
        drawParticleLocationOutline(location, mode.particle)
    }

    private fun drawParticleLocationOutline(location: Location, particle: Particle) {
        val world = location.world ?: return
        for ((x, y, z) in outlinePoints(
            location.x - 0.5,
            location.y - 0.5,
            location.z - 0.5,
            location.x + 0.5,
            location.y + 0.5,
            location.z + 0.5,
            MARKER_OUTLINE_STEP
        )) {
            world.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun outlinePoints(
        minX: Double,
        minY: Double,
        minZ: Double,
        maxX: Double,
        maxY: Double,
        maxZ: Double,
        stepSize: Double
    ): Set<Triple<Double, Double, Double>> {
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

    private fun showDeletionSubtitle(player: Player, definition: MarkerToolDefinition, mode: MarkerToolMode) {
        val modeName = getModeDisplayName(player, definition, mode)
        player.showTitle(
            Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacySection().deserialize("§c削除対象: §f$modeName"),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(250), Duration.ZERO)
            )
        )
    }

    private fun alignedYaw(yaw: Float): Float {
        return (round(yaw / 90.0f) * 90.0f).toInt().mod(360).toFloat()
    }

    private fun Int.floorMod(size: Int): Int {
        val mod = this % size
        return if (mod < 0) mod + size else mod
    }
}
