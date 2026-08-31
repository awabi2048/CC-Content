package jp.awabi2048.cccontent.features.cooking

import jp.awabi2048.cccontent.gui.ManagedMenuPresenter

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import jp.awabi2048.cccontent.features.environment.CollectionEnvironmentResolver
import jp.awabi2048.cccontent.features.environment.SurfaceWaterRegionAnalyzer
import jp.awabi2048.cccontent.features.catalog.CatalogStore
import jp.awabi2048.cccontent.features.catalog.CatalogType
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.profession.profile.CookSkillProfile
import jp.awabi2048.cccontent.features.rank.profession.profile.BrewerSkillProfile
import jp.awabi2048.cccontent.features.brewery.item.BreweryItemCodec
import jp.awabi2048.cccontent.gui.GuiMenuItems
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.persistence.ContentPdcKeys
import jp.awabi2048.cccontent.util.PotionEffectTypeResolver
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Levelled
import org.bukkit.block.data.type.Campfire
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.CauldronLevelChangeEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlin.math.roundToInt

internal class UnifiedCookingController(
    private val plugin: JavaPlugin,
    private val rankManagerProvider: () -> RankManager?,
    private val catalogStore: CatalogStore,
    private val configuration: UnifiedCookingConfiguration,
    private val breweryPreparations: Map<String, BreweryPreparationDefinition>,
    private val environmentResolver: CollectionEnvironmentResolver = CollectionEnvironmentResolver.defaults()
) : Listener {
    private val resolver = CookingIngredientResolver(configuration.ingredients.values)
    private val store = CookingStateStore(File(plugin.dataFolder, "data/cooking/state.yml"))
    private val breweryCodec = BreweryItemCodec(plugin)
    private val surfaceWaterRegionAnalyzer = SurfaceWaterRegionAnalyzer(environmentResolver)
    private val stations = store.load()
    private val locks = mutableMapOf<CookingStationKey, UUID>()
    private var task: BukkitTask? = null
    private var dirty = false
    private var ticks = 0L

    fun initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable(::tick), 1L, 1L)
    }

    fun shutdown() {
        Bukkit.getOnlinePlayers().forEach(::closeIfOpen)
        task?.cancel()
        task = null
        flush()
        locks.clear()
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND || event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        if (handleLiquidContainer(event, block)) return
        val station = detectStation(event.player, block) ?: return
        if (event.useInteractedBlock() == Event.Result.DENY || event.useItemInHand() == Event.Result.DENY) return
        event.isCancelled = true
        open(event.player, CookingStationKey.from(block), station)
    }

    /** 海水バケツは通常の水入りバケツとして使えるため、採取結果だけを差し替えます。 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSeaWaterBucketFill(event: PlayerBucketFillEvent) {
        if (event.getBucket() != Material.WATER_BUCKET) return
        val source = event.getBlockClicked()
        if (!surfaceWaterRegionAnalyzer.hasMinimumSurfaceWater(source)) return
        val seaWaterBucket = CustomItemManager.createItem(CookingLiquidIds.SEA_WATER_BUCKET) ?: return
        event.setItemStack(seaWaterBucket)
    }

    /** カスタム液体を入れた釜へのバニラの水位変更を止め、論理状態との不整合を防ぎます。 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCauldronLevelChange(event: CauldronLevelChangeEvent) {
        val key = CookingStationKey.from(event.block)
        val data = stations[key] ?: return
        val contents = CookingLiquidContents.of(data.liquidContents)
        if (data.session != null || contents.containsNonWater()) {
            event.isCancelled = true
            return
        }
        if (contents.isEmpty()) return
        // 最後の水を汲み切った場合、newState は通常の CAULDRON になり
        // Levelled ではありません。その場合も保存済みの通常水を消します。
        val level = (event.newState.blockData as? Levelled)?.level ?: 0
        stations[key] = if (level <= 0) data.copy(liquidContents = emptyMap()) else
            data.copy(liquidContents = mapOf(CookingLiquidIds.WATER to level))
        dirty = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? UnifiedCookingHolder ?: return
        val player = event.whoClicked as? Player ?: return
        if (player.uniqueId != holder.ownerId) { event.isCancelled = true; return }
        if (event.click in FORBIDDEN_CLICKS || player.gameMode == GameMode.CREATIVE) {
            event.isCancelled = true
            return
        }
        val raw = event.rawSlot
        val session = stations[holder.stationKey]?.session
        if (raw in holder.outputIndices && session != null) {
            event.isCancelled = true
            collectSolid(player, event, holder, raw, session)
            return
        }
        if (raw in holder.liquidSlots && session?.state == CookingProcessState.READY_LIQUID) {
            event.isCancelled = true
            if (event.click == ClickType.LEFT) collectLiquid(player, event, holder, session)
            return
        }
        if (raw == UnifiedCookingLayout.CLOSE) {
            event.isCancelled = true
            if (event.click == ClickType.LEFT) ManagedMenuPresenter.close(player)
            return
        }
        if (raw == UnifiedCookingLayout.START) {
            event.isCancelled = true
            if (event.click == ClickType.LEFT) start(player, event.view.topInventory, holder)
            return
        }
        if (raw == UnifiedCookingLayout.CANCEL) {
            event.isCancelled = true
            if (event.click == ClickType.LEFT) cancel(player, holder)
            return
        }
        val allowed = holder.inputSlots
        if (raw in 0 until event.view.topInventory.size && raw !in allowed) event.isCancelled = true
        if (session != null && raw in allowed) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? UnifiedCookingHolder ?: return
        if (event.rawSlots.any { it < event.view.topInventory.size && it !in holder.inputSlots }) {
            event.isCancelled = true
        }
        if (stations[holder.stationKey]?.session != null && event.rawSlots.any { it in holder.inputSlots }) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? UnifiedCookingHolder ?: return
        persistWorkspace(event.inventory, holder)
        locks.remove(holder.stationKey, holder.ownerId)
    }

    @EventHandler fun onQuit(event: PlayerQuitEvent) = closeIfOpen(event.player)
    @EventHandler fun onTeleport(event: PlayerTeleportEvent) = closeIfOpen(event.player)
    @EventHandler fun onDeath(event: PlayerDeathEvent) = closeIfOpen(event.player)

    @EventHandler(ignoreCancelled = true)
    fun onConsume(event: PlayerItemConsumeEvent) {
        val recipeId = event.item.itemMeta?.persistentDataContainer
            ?.get(ContentPdcKeys.cookingRecipeId, PersistentDataType.STRING) ?: return
        val recipe = configuration.recipes[recipeId] ?: return
        recipe.result.effects.forEach { effect ->
            val type = PotionEffectTypeResolver.resolve(effect.type)
                ?: error("Unknown cooking potion effect: ${effect.type}")
            event.player.addPotionEffect(PotionEffect(type, effect.durationSeconds * 20, effect.amplifier))
        }
        recipe.result.container?.let { container ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val remainder = ItemStack(container)
                event.player.inventory.addItem(remainder).values.forEach { leftover ->
                    event.player.world.dropItemNaturally(event.player.location, leftover)
                }
            })
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        val key = CookingStationKey.from(event.block)
        val data = stations[key] ?: return
        stations.remove(key)
        locks.remove(key)?.let { Bukkit.getPlayer(it)?.let(ManagedMenuPresenter::close) }
        dropContents(event.block, data)
        dirty = true
        flush()
    }

    private fun detectStation(player: Player, block: Block): CookingStation? {
        val mainHand = player.inventory.itemInMainHand
        val emptyHand = mainHand.type.isAir
        if (player.isSneaking && emptyHand && CuttingPolicy.boardClass(block.type) != null) return CookingStation.CUTTING
        if (CustomItemManager.identify(mainHand)?.fullId == "cooking.frying_pan" && heat(block) != null) {
            return CookingStation.PAN
        }
        val customLiquidInCauldron = stations[CookingStationKey.from(block)]
            ?.liquidContents
            ?.keys
            ?.any { it != CookingLiquidIds.WATER } == true
        val pendingCauldronSession = stations[CookingStationKey.from(block)]
            ?.let { it.equipment == CookingStation.CAULDRON && it.session != null } == true
        if (player.isSneaking && emptyHand && block.type == Material.WATER_CAULDRON &&
            (heat(block.getRelative(BlockFace.DOWN)) != null || customLiquidInCauldron || pendingCauldronSession)) {
            return CookingStation.CAULDRON
        }
        if (player.isSneaking && emptyHand && pendingCauldronSession && block.type == Material.CAULDRON) {
            return CookingStation.CAULDRON
        }
        return null
    }

    /**
     * 海水・豆乳・にがりの容器は、通常の調理投入欄ではなく釜へ直接注ぎます。
     * ここで論理液体を更新し、登録済みの入力構成になった時だけ加工を開始します。
     */
    private fun handleLiquidContainer(event: PlayerInteractEvent, block: Block): Boolean {
        if (block.type != Material.CAULDRON && block.type != Material.WATER_CAULDRON) return false
        val item = event.item ?: return false
        val customId = CustomItemManager.identify(item)?.fullId ?: return false
        val input = when (customId) {
            CookingLiquidIds.SEA_WATER_BUCKET -> CookingLiquidInput(CookingLiquidIds.SEA_WATER, 3, Material.BUCKET)
            CookingLiquidIds.SOY_MILK_BOTTLE -> CookingLiquidInput(CookingLiquidIds.SOY_MILK, 1, Material.GLASS_BOTTLE)
            CookingLiquidIds.NIGARI_BOTTLE -> CookingLiquidInput(CookingLiquidIds.BITTERN, 1, Material.GLASS_BOTTLE)
            else -> return false
        }
        event.isCancelled = true
        val key = CookingStationKey.from(block)
        val existing = stations[key]
        if (existing?.session != null) {
            event.player.sendMessage(message(event.player, "cooking.error.in_use"))
            return true
        }
        val current = currentLiquidContents(block, existing)
        val next = if (input.liquidId == CookingLiquidIds.SEA_WATER && !current.isEmpty()) {
            null
        } else {
            runCatching { current.plus(input.liquidId, input.amount) }.getOrNull()
        }
        if (next == null || !next.isPotentialInputFor(configuration.liquidRecipes.values)) {
            event.player.sendMessage(message(event.player, "cooking.error.liquid_mismatch"))
            return true
        }
        val updated = (existing ?: PersistedCookingStation(CookingStation.CAULDRON)).copy(
            equipment = CookingStation.CAULDRON,
            liquidContents = next.amounts
        )
        stations[key] = updated
        setCauldronContents(block, next)
        consumeHeldItem(event.player, EquipmentSlot.HAND, input.remainder)
        dirty = true
        flush()

        val recipe = CookingLiquidRecipeMatcher.find(next, configuration.liquidRecipes.values)
        if (recipe != null && stationHeat(block, CookingStation.CAULDRON) == recipe.heat) {
            startLiquidProcess(event.player, key, block, recipe, next)
        }
        return true
    }

    private data class CookingLiquidInput(
        val liquidId: String,
        val amount: Int,
        val remainder: Material
    )

    private fun currentLiquidContents(
        block: Block,
        persisted: PersistedCookingStation? = stations[CookingStationKey.from(block)]
    ): CookingLiquidContents {
        if (persisted?.liquidContents?.isNotEmpty() == true) {
            return CookingLiquidContents.of(persisted.liquidContents)
        }
        val level = waterLevel(block)
        return if (block.type == Material.WATER_CAULDRON && level > 0) {
            CookingLiquidContents.of(mapOf(CookingLiquidIds.WATER to level))
        } else {
            CookingLiquidContents.empty()
        }
    }

    private fun setCauldronContents(block: Block, contents: CookingLiquidContents) {
        if (contents.isEmpty()) {
            block.type = Material.CAULDRON
            return
        }
        if (block.type != Material.WATER_CAULDRON) block.type = Material.WATER_CAULDRON
        setCauldronLevel(block, contents.total)
    }

    private fun setCauldronLevel(block: Block, level: Int) {
        if (level <= 0) {
            block.type = Material.CAULDRON
            return
        }
        if (block.type != Material.WATER_CAULDRON) block.type = Material.WATER_CAULDRON
        val levelled = block.blockData as? Levelled ?: return
        levelled.level = level.coerceIn(levelled.minimumLevel, levelled.maximumLevel)
        block.blockData = levelled
    }

    private fun consumeHeldItem(player: Player, hand: EquipmentSlot, remainder: Material) {
        val held = player.inventory.getItem(hand)
        if (held.amount > 1) {
            held.amount -= 1
            player.inventory.setItem(hand, held)
        } else {
            player.inventory.setItem(hand, ItemStack(remainder))
        }
    }

    private fun startLiquidProcess(
        player: Player,
        key: CookingStationKey,
        block: Block,
        recipe: UnifiedLiquidCookingRecipe,
        contents: CookingLiquidContents
    ): Boolean {
        if (stations[key]?.session != null || contents.amounts != recipe.liquidInputs) return false
        val heat = stationHeat(block, CookingStation.CAULDRON) ?: return false
        if (heat != recipe.heat) return false
        val session = CookingStationStateMachine.start(
            recipe.stationRecipe,
            recipe.snapshot,
            player.uniqueId.toString(),
            1,
            heat,
            emptyList(),
            0.0
        )
        val current = stations[key] ?: PersistedCookingStation(CookingStation.CAULDRON)
        stations[key] = current.copy(
            equipment = CookingStation.CAULDRON,
            session = session,
            liquidContents = contents.amounts,
            experienceAwarded = false,
            starterCatalogAwarded = false
        )
        dirty = true
        flush()
        if (player.openInventory.topInventory.holder is UnifiedCookingHolder) {
            val holder = player.openInventory.topInventory.holder as UnifiedCookingHolder
            if (holder.stationKey == key) render(player, player.openInventory.topInventory, holder)
        }
        player.sendMessage(message(player, "cooking.process.started"))
        return true
    }

    private fun open(player: Player, key: CookingStationKey, equipment: CookingStation) {
        val owner = locks.putIfAbsent(key, player.uniqueId)
        if (owner != null && owner != player.uniqueId) {
            player.sendMessage(message(player, "cooking.error.in_use"))
            return
        }
        val current = stations[key]
        if (current != null && current.equipment != equipment) {
            locks.remove(key, player.uniqueId)
            return
        }
        val pan = if (equipment == CookingStation.PAN) player.inventory.itemInMainHand.serializeAsBytes() else null
        val holder = UnifiedCookingHolder(player.uniqueId, key, equipment, player.inventory.heldItemSlot, pan)
        val title = when (equipment) {
            CookingStation.CUTTING -> message(player, "cooking.ui.title.cutting")
            CookingStation.PAN -> message(player, "cooking.ui.title.pan")
            CookingStation.CAULDRON -> message(player, "cooking.ui.title.cauldron")
            else -> error("Unsupported interactive cooking station")
        }
        val inventory = Bukkit.createInventory(holder, 45, Component.text(title))
        holder.backingInventory = inventory
        render(player, inventory, holder)
        ManagedMenuPresenter.open(
            player,
            inventory,
            menuId = "cooking_${holder.equipment.name.lowercase()}",
            policy = ManagedMenuPresenter.inputPolicy(holder.inputSlots),
        )
    }

    private fun render(player: Player, inventory: Inventory, holder: UnifiedCookingHolder) {
        CCSystem.getAPI().getGuiLayoutService().applyStandardFrame(inventory)
        holder.inputSlots.forEach { inventory.setItem(it, null) }
        val data = stations[holder.stationKey]
        data?.workspaceItems?.forEach { (slot, encoded) ->
            if (slot in holder.inputSlots) inventory.setItem(slot, decode(encoded))
        }
        holder.outputIndices.clear()
        holder.liquidSlots.clear()
        data?.session?.let { session -> renderSessionOutputs(player, inventory, holder, session) }
        inventory.setItem(UnifiedCookingLayout.CLOSE, icon(Material.BARRIER, message(player, "cooking.ui.close")))
        inventory.setItem(UnifiedCookingLayout.INFO, icon(Material.BOOK, message(player, "cooking.ui.info")))
        inventory.setItem(UnifiedCookingLayout.START, icon(
            if (data?.session == null) Material.LIME_CONCRETE else Material.BARRIER,
            message(player, "cooking.ui.start")
        ))
        inventory.setItem(UnifiedCookingLayout.STATE, stateIcon(player, data?.session))
        if (holder.equipment != CookingStation.CUTTING) {
            inventory.setItem(UnifiedCookingLayout.CANCEL, icon(Material.RED_CONCRETE, message(player, "cooking.ui.cancel")))
            inventory.setItem(UnifiedCookingLayout.HEAT, heatIcon(player, holder))
        }
    }

    private fun start(player: Player, inventory: Inventory, holder: UnifiedCookingHolder) {
        if (stations[holder.stationKey]?.session != null) return
        when (holder.equipment) {
            CookingStation.CUTTING -> startCutting(player, inventory, holder)
            CookingStation.PAN, CookingStation.CAULDRON -> startTimed(player, inventory, holder)
            else -> Unit
        }
    }

    private fun renderSessionOutputs(
        player: Player,
        inventory: Inventory,
        holder: UnifiedCookingHolder,
        session: CookingStationSession
    ) {
        if (session.state == CookingProcessState.READY_ITEM || session.state == CookingProcessState.CANCELLED_RETURN) {
            val slots = if (holder.equipment == CookingStation.CUTTING) {
                UnifiedCookingLayout.CUTTING_WORK
            } else {
                UnifiedCookingLayout.TIMED_OUTPUT_ORDER
            }
            session.outputStacks.take(slots.size).forEachIndexed { index, output ->
                val slot = slots[index]
                inventory.setItem(slot, outputItem(output, player))
                holder.outputIndices[slot] = index
            }
        }
        if (session.state == CookingProcessState.READY_LIQUID) {
            session.outputStacks.take(UnifiedCookingLayout.TIMED_OUTPUT_ORDER.size).forEachIndexed { index, output ->
                val slot = UnifiedCookingLayout.TIMED_OUTPUT_ORDER[index]
                inventory.setItem(slot, outputItem(output, player))
                holder.outputIndices[slot] = index
            }
            val reservoir = session.reservoir ?: return
            val pane = session.recipeSnapshot.liquidPaneMaterial?.let(Material::matchMaterial)
                ?: Material.GRAY_STAINED_GLASS_PANE
            val visibleSlotCount = when {
                reservoir.remaining * 3 > reservoir.maximum * 2 -> 5
                reservoir.remaining * 3 > reservoir.maximum -> 3
                else -> 1
            }
            // 固形成果物と液体成果物を同時に表示できるよう、固形物で使用中の
            // スロットを除外してから液体領域を描画します。
            UnifiedCookingLayout.LIQUID_OUTPUT_ORDER
                .filterNot(holder.outputIndices::containsKey)
                .take(visibleSlotCount)
                .forEach { slot ->
                    inventory.setItem(slot, icon(pane, message(player, "cooking.ui.liquid_result")))
                    holder.liquidSlots += slot
                }
        }
    }

    private fun collectSolid(
        player: Player,
        event: InventoryClickEvent,
        holder: UnifiedCookingHolder,
        rawSlot: Int,
        session: CookingStationSession
    ) {
        val index = holder.outputIndices[rawSlot] ?: return
        val output = session.outputStacks.getOrNull(index) ?: return
        val item = outputItem(output, player)
        val delivered = if (event.isShiftClick) {
            if (!canFit(player, item)) false else {
                player.inventory.addItem(item)
                true
            }
        } else {
            val cursor = event.cursor
            when {
                cursor.type.isAir -> { player.setItemOnCursor(item); true }
                cursor.isSimilar(item) && cursor.amount + item.amount <= cursor.maxStackSize -> {
                    cursor.amount += item.amount
                    player.setItemOnCursor(cursor)
                    true
                }
                else -> false
            }
        }
        if (!delivered) return
        val next = CookingStationStateMachine.collectSolid(session, index) ?: return
        consumeNewWater(holder, session, next)
        updateAfterCollection(holder, next, player)
    }

    private fun collectLiquid(
        player: Player,
        event: InventoryClickEvent,
        holder: UnifiedCookingHolder,
        session: CookingStationSession
    ) {
        val reservoir = session.reservoir ?: return
        val required = Material.matchMaterial(reservoir.containerMaterial) ?: return
        val cursor = event.cursor
        if (cursor.type != required || cursor.amount <= 0) {
            player.sendMessage(message(player, "cooking.error.container_required"))
            return
        }
        val result = CustomItemManager.createItemForPlayer(reservoir.customItemId, player, 1) ?: return
        if (cursor.amount == 1) {
            player.setItemOnCursor(result)
        } else {
            if (!canFit(player, result)) return
            player.inventory.addItem(result)
            cursor.amount -= 1
            player.setItemOnCursor(cursor)
        }
        val next = CookingStationStateMachine.collectLiquid(session) ?: return
        consumeNewWater(holder, session, next)
        consumeLiquidContents(holder, session)
        updateAfterCollection(holder, next, player)
    }

    private fun consumeLiquidContents(holder: UnifiedCookingHolder, session: CookingStationSession) {
        val recipe = configuration.liquidRecipes[session.recipeId] ?: return
        val liquidId = recipe.residualLiquids.keys.singleOrNull() ?: return
        val key = holder.stationKey
        val current = stations[key]?.liquidContents?.let(CookingLiquidContents::of) ?: return
        val remaining = current.minus(liquidId, 1)
        setCauldronContents(key.blockIfLoaded() ?: return, remaining)
        stations[key] = stations[key]?.copy(liquidContents = remaining.amounts) ?: return
    }

    private fun updateAfterCollection(holder: UnifiedCookingHolder, next: CookingStationSession, player: Player) {
        val current = stations[holder.stationKey] ?: return
        val successful = configuration.recipes[next.recipeId]?.definition?.experience?.let { it > 0 } == true &&
            !next.failureCommitted
        val collectorId = player.uniqueId.toString()
        val firstCollection = collectorId !in current.collectorIds
        if (successful && firstCollection) {
            catalogStore.record(player.uniqueId, CatalogType.COOKING, next.recipeId, obtained = true)
        }
        if (next.state == CookingProcessState.IDLE) stations.remove(holder.stationKey)
        else stations[holder.stationKey] = current.copy(
            session = next,
            collectorIds = if (firstCollection) current.collectorIds + collectorId else current.collectorIds
        )
        dirty = true
        flush()
        render(player, player.openInventory.topInventory, holder)
    }

    private fun consumeNewWater(
        holder: UnifiedCookingHolder,
        before: CookingStationSession,
        after: CookingStationSession
    ) {
        val difference = after.consumedWaterUnits - before.consumedWaterUnits
        if (difference <= 0 || holder.equipment != CookingStation.CAULDRON) return
        val block = holder.stationKey.blockIfLoaded() ?: return
        val levelled = block.blockData as? Levelled ?: return
        val remaining = levelled.level - difference
        if (remaining <= 0) block.type = Material.CAULDRON
        else {
            levelled.level = remaining
            block.blockData = levelled
        }
    }

    private fun outputItem(output: CookingOutputStack, player: Player?): ItemStack = when (output.kind) {
        CookingOutputKind.CUSTOM_ITEM -> breweryOutput(output.customItemId, player, output.amount)
            ?: CustomItemManager.createItemForPlayer(output.customItemId, player, output.amount)
            ?: error("Unknown cooking output: ${output.customItemId}")
        CookingOutputKind.SERIALIZED_ITEM -> decode(output.customItemId).also { it.amount = output.amount }
        CookingOutputKind.MATERIAL -> ItemStack(Material.valueOf(output.customItemId), output.amount)
    }

    private fun breweryOutput(id: String, player: Player?, amount: Int): ItemStack? {
        if (id.startsWith("brewery.prepared:")) {
            val parts = id.split(':')
            require(parts.size == 4)
            return breweryCodec.createPreparedBottle(parts[1], parts[2], parts[3].toInt(), player).also { it.amount = amount }
        }
        if (id.startsWith("brewery.failed_")) {
            return ItemStack(Material.POTION, amount).also { item ->
                item.editMeta { meta ->
                    meta.displayName(Component.text(message(player ?: return@editMeta, "brewery.item.name.muddy")))
                    meta.setItemModel(org.bukkit.NamespacedKey("kota_server", "custom_item/brewery/${id.removePrefix("brewery.")}"))
                    meta.persistentDataContainer.set(ContentPdcKeys.customItemId, PersistentDataType.STRING, id)
                }
            }
        }
        return null
    }

    private fun canFit(player: Player, item: ItemStack): Boolean {
        var remaining = item.amount
        player.inventory.storageContents.forEach { existing ->
            if (existing == null || existing.type.isAir) remaining -= item.maxStackSize
            else if (existing.isSimilar(item)) remaining -= existing.maxStackSize - existing.amount
        }
        return remaining <= 0
    }

    private fun startCutting(player: Player, inventory: Inventory, holder: UnifiedCookingHolder) {
        val block = holder.stationKey.blockIfLoaded() ?: return
        val board = CuttingPolicy.boardClass(block.type) ?: return
        val knife = inventory.getItem(UnifiedCookingLayout.TOOL) ?: return
        if (CustomItemManager.identify(knife)?.fullId != "cooking.knife") return
        val actualItems = UnifiedCookingLayout.CUTTING_WORK.mapNotNull(inventory::getItem).filter(::realItem)
        if (actualItems.isEmpty()) return
        val recipe = configuration.cuttingRecipes.values.firstOrNull { candidate ->
            actualItems.all { item ->
                resolver.resolve(item)?.id == candidate.inputIngredientId ||
                    CustomItemManager.identify(item)?.fullId == candidate.outputCustomItemId
            } && actualItems.any { resolver.resolve(it)?.id == candidate.inputIngredientId }
        } ?: return
        val amount = actualItems.filter { resolver.resolve(it)?.id == recipe.inputIngredientId }.sumOf(ItemStack::getAmount)
        val ingredientId = recipe.inputIngredientId
        val slots = UnifiedCookingLayout.CUTTING_WORK.map { slot ->
            val item = inventory.getItem(slot)
            val id = item?.let { CustomItemManager.identify(it)?.fullId }
            if (id == recipe.outputCustomItemId) CuttingSlot(id, item.amount, item.maxStackSize)
            else CuttingSlot(null, 0, item?.maxStackSize ?: 64)
        }
        val execution = CuttingPolicy.execute(recipe, ingredientId, amount, board, slots) ?: return
        if (!damageTool(knife, execution.knifeDamage)) inventory.setItem(UnifiedCookingLayout.TOOL, null)
        UnifiedCookingLayout.CUTTING_WORK.forEach(inventory::clear)
        execution.resultingSlots.forEachIndexed { index, result ->
            if (result.customItemId != null) {
                inventory.setItem(
                    UnifiedCookingLayout.CUTTING_WORK[index],
                    CustomItemManager.createItemForPlayer(result.customItemId, player, result.amount)
                )
            }
        }
        persistWorkspace(inventory, holder)
        player.sendMessage(message(player, "cooking.process.completed"))
        render(player, inventory, holder)
    }

    private fun startTimed(player: Player, inventory: Inventory, holder: UnifiedCookingHolder) {
        val block = holder.stationKey.blockIfLoaded() ?: return
        if (holder.equipment == CookingStation.PAN && !samePan(player, holder)) {
            player.sendMessage(message(player, "cooking.error.no_pan")); return
        }
        val items = UnifiedCookingLayout.TIMED_WORK.mapNotNull(inventory::getItem).filter(::realItem)
        if (holder.equipment == CookingStation.CAULDRON && items.isEmpty()) {
            val contents = currentLiquidContents(block)
            if (contents.containsNonWater()) {
                val recipe = CookingLiquidRecipeMatcher.find(contents, configuration.liquidRecipes.values)
                if (recipe == null) {
                    player.sendMessage(message(player, "cooking.error.recipe_not_found"))
                    return
                }
                val currentHeat = stationHeat(block, CookingStation.CAULDRON) ?: run {
                    player.sendMessage(message(player, "cooking.error.no_heat")); return
                }
                if (currentHeat != recipe.heat) {
                    player.sendMessage(message(player, "cooking.error.heat_required"))
                    return
                }
                startLiquidProcess(player, holder.stationKey, block, recipe, contents)
                return
            }
        }
        val currentHeat = stationHeat(block, holder.equipment) ?: run {
            player.sendMessage(message(player, "cooking.error.no_heat")); return
        }
        if (items.isEmpty()) return
        val actual = resolver.aggregate(items)
        val unknown = items.filter { resolver.resolve(it) == null }.sumOf(ItemStack::getAmount)
        if (unknown > 0) {
            player.sendMessage(message(player, "cooking.error.recipe_not_found")); return
        }
        val water = if (holder.equipment == CookingStation.CAULDRON) waterLevel(block) else 0
        val typedProfile = rankManagerProvider()?.getTypedProfessionProfile(player.uniqueId)
        val cookProfile = typedProfile as? CookSkillProfile
        val brewerProfile = typedProfile as? BrewerSkillProfile
        val cookingMatch = CookingRecipeMatcher.select(
            configuration.recipes.values.map { it.definition }, holder.equipment, actual, currentHeat, water,
            cookProfile?.mismatchPenaltyReduction ?: 0.0, configuration.settings.matching
        )
        val breweryMatch = CookingRecipeMatcher.select(
            breweryPreparations.values.map { it.recipe }, holder.equipment, actual, currentHeat, water, 0.0,
            configuration.settings.matching.copy(
                maximumTotalError = (configuration.settings.matching.maximumTotalError +
                    (brewerProfile?.conditionToleranceBonus ?: 0.0)).coerceAtMost(0.50)
            )
        )
        val candidates = listOf(cookingMatch, breweryMatch).mapNotNull { (it as? CookingMatchResult.Selected)?.match }
            .sortedWith(compareByDescending<CookingRecipeMatch> { it.exact }.thenBy { it.effectiveError })
        val selected = candidates.firstOrNull() ?: run {
            if (cookingMatch is CookingMatchResult.Ambiguous || breweryMatch is CookingMatchResult.Ambiguous) {
                player.sendMessage(message(player, "cooking.error.ambiguous_recipe"))
            } else {
                player.sendMessage(message(player, "cooking.error.recipe_not_found")); return
            }
            return
        }
        if (candidates.size > 1 &&
            kotlin.math.abs(candidates[0].effectiveError - candidates[1].effectiveError) < configuration.settings.matching.ambiguityMargin) {
            player.sendMessage(message(player, "cooking.error.ambiguous_recipe")); return
        }
        val preparation = selected.recipe.id.removePrefix("brewery:")
            .takeIf { selected.recipe.id.startsWith("brewery:") }
            ?.let(breweryPreparations::get)
        if (preparation == null && !tierAvailable(selected.recipe.tier, cookProfile)) {
            player.sendMessage(message(player, "cooking.error.tier_locked")); return
        }
        if (preparation != null && !breweryGroupAvailable(preparation.group, brewerProfile)) {
            player.sendMessage(message(player, "cooking.error.tier_locked")); return
        }
        val stored = items.map { item ->
            val ingredient = requireNotNull(resolver.resolve(item))
            val remainder = ingredient.containerRemainder
            CookingStoredInput(
                ingredient.id, item.amount, encode(item), remainder?.material?.name,
                (remainder?.amount ?: 0) * item.amount
            )
        }
        val snapshot = if (preparation != null) {
            val quality = ((1.0 - selected.effectiveError) * 100.0).roundToInt() +
                (brewerProfile?.qualityStabilityBonus ?: 0) +
                (if (selected.exact) brewerProfile?.exactConditionQualityBonus ?: 0 else 0)
            CookingRecipeSnapshot(
                "brewery.prepared:${preparation.familyId}:${preparation.preparationId}:${quality.coerceIn(0, 100)}",
                1, preparation.failureResultId, selected.recipe.durationSeconds, requireNotNull(selected.recipe.heat),
                3, CookingResultKind.BOTTLE, Material.GLASS_BOTTLE.name, preparation.liquidPane.name, 0
            )
        } else {
            val configured = configuration.recipes.getValue(selected.recipe.id)
            CookingRecipeSnapshot(
                configured.result.customItemId, configured.result.amountPerScale, configured.failureResult.customItemId,
                selected.recipe.durationSeconds, requireNotNull(selected.recipe.heat), selected.recipe.waterUnits,
                selected.recipe.resultKind, configured.result.container?.name, configured.result.liquidPane?.name,
                selected.recipe.experience
            )
        }
        val session = CookingStationStateMachine.start(
            selected.recipe, snapshot, player.uniqueId.toString(), selected.scale, currentHeat, stored,
            if (preparation != null) 0.0 else cookProfile?.processingTimeReduction ?: 0.0
        )
        holder.inputSlots.forEach(inventory::clear)
        if (holder.equipment == CookingStation.PAN) {
            player.damageItemStack(EquipmentSlot.HAND, selected.scale)
        }
        stations[holder.stationKey] = PersistedCookingStation(holder.equipment, session)
        dirty = true
        flush()
        render(player, inventory, holder)
        player.sendMessage(message(player, "cooking.process.started"))
    }

    private fun cancel(player: Player, holder: UnifiedCookingHolder) {
        val data = stations[holder.stationKey] ?: return
        val session = data.session ?: return
        if (configuration.liquidRecipes.containsKey(session.recipeId)) {
            // 液体処理は入力をすでに釜へ移しているため、取消時は入力液体を残して再実行可能にします。
            stations[holder.stationKey] = data.copy(session = null)
            dirty = true
            flush()
            player.sendMessage(message(player, "cooking.process.cancelled"))
            render(player, player.openInventory.topInventory, holder)
            return
        }
        val cancelled = CookingStationStateMachine.cancel(session) ?: return
        stations[holder.stationKey] = data.copy(session = cancelled)
        dirty = true
        flush()
        player.sendMessage(message(player, "cooking.process.cancelled"))
        render(player, player.openInventory.topInventory, holder)
    }

    private fun tick() {
        ticks++
        stations.entries.toList().forEach { (key, data) ->
            val session = data.session ?: return@forEach
            if (session.state !in PROCESSING_STATES) return@forEach
            val block = key.blockIfLoaded() ?: return@forEach
            val currentHeat = stationHeat(block, data.equipment)
            when (val step = CookingStationStateMachine.tick(session, currentHeat)) {
                is CookingStationStep.Updated -> if (step.session != session) {
                    configuration.liquidRecipes[session.recipeId]?.let { recipe ->
                        updateLiquidProcessingLevel(block, recipe, step.session)
                    }
                    stations[key] = data.copy(session = step.session)
                    dirty = true
                }
                is CookingStationStep.Completed -> {
                    val liquidRecipe = configuration.liquidRecipes[session.recipeId]
                    val finished = if (liquidRecipe != null) {
                        updateLiquidProcessingLevel(block, liquidRecipe, step.session)
                        val residual = CookingLiquidContents.of(liquidRecipe.residualLiquids)
                        setCauldronContents(block, residual)
                        CookingStationStateMachine.finishLiquid(step.session, liquidRecipe)
                    } else {
                        val definition = configuration.recipes[session.recipeId]?.definition
                            ?: snapshotDefinition(data.equipment, step.session)
                        CookingStationStateMachine.finish(step.session, definition)
                    }
                    val successful = !finished.failureCommitted && finished.recipeSnapshot.experience > 0
                    val starter = UUID.fromString(finished.starterId)
                    if (successful && !data.experienceAwarded) {
                        rankManagerProvider()?.addProfessionExp(starter, finished.recipeSnapshot.experience)
                    }
                    if (successful && !data.starterCatalogAwarded) {
                        catalogStore.record(starter, CatalogType.COOKING, finished.recipeId, obtained = false)
                    }
                    stations[key] = data.copy(
                        session = finished,
                        liquidContents = if (liquidRecipe != null) {
                            CookingLiquidContents.of(liquidRecipe.residualLiquids).amounts
                        } else {
                            data.liquidContents
                        },
                        experienceAwarded = data.experienceAwarded || successful,
                        starterCatalogAwarded = data.starterCatalogAwarded || successful
                    )
                    dirty = true
                    flush()
                    locks[key]?.let(Bukkit::getPlayer)?.let { player ->
                        val holder = player.openInventory.topInventory.holder as? UnifiedCookingHolder
                        if (holder?.stationKey == key) render(player, player.openInventory.topInventory, holder)
                    }
                }
            }
        }
        if (dirty && ticks % configuration.settings.flushIntervalTicks == 0L) flush()
    }

    private fun updateLiquidProcessingLevel(
        block: Block,
        recipe: UnifiedLiquidCookingRecipe,
        session: CookingStationSession
    ) {
        if (recipe.processingLevels.isEmpty()) return
        val elapsed = session.totalTicks - session.remainingTicks
        val stage = ((elapsed * recipe.processingLevels.size) / session.totalTicks)
            .toInt()
            .coerceIn(0, recipe.processingLevels.lastIndex)
        setCauldronLevel(block, recipe.processingLevels[stage])
    }

    private fun snapshotDefinition(equipment: CookingStation, session: CookingStationSession): CookingRecipeDefinition =
        CookingRecipeDefinition(
            session.recipeId, equipment, "SNAPSHOT", CookingTier.BASIC, session.recipeSnapshot.expectedHeat,
            mapOf("snapshot" to 1), session.recipeSnapshot.waterUnits, session.recipeSnapshot.durationSeconds,
            session.recipeSnapshot.experience, session.recipeSnapshot.resultKind
        )

    private fun persistWorkspace(inventory: Inventory, holder: UnifiedCookingHolder) {
        if (stations[holder.stationKey]?.session != null) return
        val current = stations[holder.stationKey]
        val items = holder.inputSlots.mapNotNull { slot ->
            inventory.getItem(slot)?.takeIf(::realItem)?.let { slot to encode(it) }
        }.toMap()
        if (items.isEmpty()) {
            if (current?.liquidContents?.isNotEmpty() == true) {
                stations[holder.stationKey] = current.copy(workspaceItems = emptyMap())
            } else {
                stations.remove(holder.stationKey)
            }
        } else {
            stations[holder.stationKey] = (current ?: PersistedCookingStation(holder.equipment)).copy(
                equipment = holder.equipment,
                workspaceItems = items
            )
        }
        dirty = true
        flush()
    }

    private fun dropContents(block: Block, data: PersistedCookingStation) {
        data.workspaceItems.values.map(::decode).forEach { block.world.dropItemNaturally(block.location, it) }
        data.session?.originalInputs?.forEach { block.world.dropItemNaturally(block.location, decode(it.serializedItem)) }
        data.session?.outputStacks?.forEach { output -> block.world.dropItemNaturally(block.location, outputItem(output, null)) }
        // READY_LIQUID の残液は session.reservoir が所有するため二重に出さず、
        // 釜へ投入済みの処理中液体と、未処理の待機液体だけを容器へ戻します。
        if (data.session == null || data.session.state in PROCESSING_STATES) {
            dropLogicalLiquidContents(block, data.liquidContents)
        }
        data.session?.reservoir?.let { reservoir ->
            CustomItemManager.createItem(reservoir.customItemId, reservoir.remaining)?.let { item ->
                block.world.dropItemNaturally(block.location, item)
            }
        }
    }

    private fun dropLogicalLiquidContents(block: Block, amounts: Map<String, Int>) {
        amounts.forEach { (liquidId, amount) ->
            val customItemId = when (liquidId) {
                CookingLiquidIds.SEA_WATER -> CookingLiquidIds.SEA_WATER_BUCKET
                CookingLiquidIds.SOY_MILK -> CookingLiquidIds.SOY_MILK_BOTTLE
                CookingLiquidIds.BITTERN -> CookingLiquidIds.NIGARI_BOTTLE
                // 通常の水はバニラの釜に戻るだけで、回収アイテムを生成しません。
                CookingLiquidIds.WATER -> return@forEach
                else -> return@forEach
            }
            val containerCount = if (liquidId == CookingLiquidIds.SEA_WATER) {
                (amount + 2) / 3
            } else {
                amount
            }
            repeat(containerCount) {
                CustomItemManager.createItem(customItemId)?.let { item ->
                    block.world.dropItemNaturally(block.location, item)
                }
            }
        }
    }

    private fun stationHeat(block: Block, equipment: CookingStation): CookingHeat? = when (equipment) {
        CookingStation.PAN -> heat(block)
        CookingStation.CAULDRON -> heat(block.getRelative(BlockFace.DOWN))
        else -> null
    }

    private fun heat(block: Block): CookingHeat? {
        val data = block.blockData as? Campfire ?: return null
        if (!data.isLit) return null
        return if (block.type == Material.SOUL_CAMPFIRE) CookingHeat.HIGH else CookingHeat.NORMAL
    }

    private fun waterLevel(block: Block): Int = (block.blockData as? Levelled)?.level ?: 0

    private fun samePan(player: Player, holder: UnifiedCookingHolder): Boolean {
        val expected = holder.panBytes ?: return false
        return player.inventory.heldItemSlot == holder.panSlot &&
            player.inventory.itemInMainHand.serializeAsBytes().contentEquals(expected)
    }

    private fun tierAvailable(tier: CookingTier, profile: CookSkillProfile?): Boolean = when (tier) {
        CookingTier.BASIC -> true
        CookingTier.INTERMEDIATE -> profile?.level?.let { it >= 15 } == true
        CookingTier.ADVANCED -> profile?.level?.let { it >= 35 } == true
        CookingTier.TOP -> profile?.level?.let { it >= 50 } == true
    }

    private fun breweryGroupAvailable(group: String, profile: BrewerSkillProfile?): Boolean = when (group) {
        "BASIC" -> true
        "INTERMEDIATE" -> profile?.intermediateRecipeUnlocked == true
        "ADVANCED" -> profile?.advancedRecipeUnlocked == true
        "TOP" -> profile?.topRecipeUnlocked == true
        "HERBAL" -> profile?.herbalRecipeUnlocked == true
        "WILD_AND_FUNGI" -> profile?.wildAndFungiRecipeUnlocked == true
        else -> false
    }

    private fun damageTool(item: ItemStack, amount: Int): Boolean {
        val meta = item.itemMeta as? Damageable ?: return false
        val maximum = meta.maxDamage
        val next = meta.damage + amount
        if (next >= maximum) return false
        meta.damage = next
        item.itemMeta = meta
        return true
    }

    private fun closeIfOpen(player: Player) {
        if (player.openInventory.topInventory.holder is UnifiedCookingHolder) ManagedMenuPresenter.close(player)
    }

    private fun flush() {
        if (!dirty) return
        store.save(stations)
        dirty = false
    }

    private fun stateIcon(player: Player, session: CookingStationSession?): ItemStack {
        val material = when (session?.state ?: CookingProcessState.IDLE) {
            CookingProcessState.IDLE -> Material.GRAY_DYE
            CookingProcessState.PROCESSING_NORMAL -> Material.LIGHT_BLUE_DYE
            CookingProcessState.PROCESSING_FAILURE -> Material.RED_DYE
            CookingProcessState.PAUSED_NO_HEAT, CookingProcessState.PAUSED_WRONG_HEAT -> Material.YELLOW_DYE
            CookingProcessState.READY_ITEM, CookingProcessState.READY_LIQUID -> Material.LIME_DYE
            CookingProcessState.CANCELLED_RETURN -> Material.RED_DYE
        }
        return icon(material, message(player, "cooking.ui.state"))
    }

    private fun heatIcon(player: Player, holder: UnifiedCookingHolder): ItemStack {
        val block = holder.stationKey.blockIfLoaded()
        val current = block?.let { stationHeat(it, holder.equipment) }
        return icon(if (current == CookingHeat.HIGH) Material.SOUL_CAMPFIRE else Material.CAMPFIRE, message(player, "cooking.ui.heat"))
    }

    private fun icon(material: Material, name: String): ItemStack =
        GuiMenuItems.icon(material, name, emptyList<GuiLoreLine>())

    private fun realItem(item: ItemStack): Boolean = !item.type.isAir && item.type !in UI_MATERIALS
    private fun encode(item: ItemStack): String = Base64.getEncoder().encodeToString(item.serializeAsBytes())
    private fun decode(encoded: String): ItemStack = ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded))
    private fun message(player: Player, key: String): String = CCSystem.getAPI().getLocalized(
        player,
        jp.awabi2048.cccontent.util.ContentLocalizationKeys.text(key, "cooking."),
    )


    companion object {
        private val FORBIDDEN_CLICKS = setOf(
            ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND, ClickType.DOUBLE_CLICK,
            ClickType.CREATIVE, ClickType.UNKNOWN
        )
        private val UI_MATERIALS = setOf(
            Material.BLACK_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE,
            Material.BARRIER, Material.LIME_CONCRETE, Material.RED_CONCRETE,
            Material.BOOK, Material.CLOCK, Material.GRAY_DYE, Material.LIGHT_BLUE_DYE,
            Material.YELLOW_DYE, Material.LIME_DYE, Material.RED_DYE
        )
        private val PROCESSING_STATES = setOf(
            CookingProcessState.PROCESSING_NORMAL, CookingProcessState.PROCESSING_FAILURE,
            CookingProcessState.PAUSED_NO_HEAT, CookingProcessState.PAUSED_WRONG_HEAT
        )
    }
}

internal object UnifiedCookingLayout {
    const val INFO = 4
    const val CLOSE = 36
    const val TOOL = 38
    const val CANCEL = 38
    const val START = 40
    const val STATE = 42
    const val HEAT = 44
    val CUTTING_WORK = listOf(11, 12, 13, 14, 15, 20, 21, 22, 23, 24)
    val TIMED_WORK = listOf(20, 21, 22, 23, 24)
    val TIMED_OUTPUT_ORDER = listOf(22, 21, 23, 20, 24)
    val LIQUID_OUTPUT_ORDER = listOf(21, 23, 20, 24, 22)
}

internal class UnifiedCookingHolder(
    val ownerId: UUID,
    val stationKey: CookingStationKey,
    val equipment: CookingStation,
    val panSlot: Int,
    val panBytes: ByteArray?
) : InventoryHolder {
    lateinit var backingInventory: Inventory
    val outputIndices = mutableMapOf<Int, Int>()
    val liquidSlots = mutableSetOf<Int>()
    val inputSlots: List<Int> = if (equipment == CookingStation.CUTTING) {
        UnifiedCookingLayout.CUTTING_WORK + UnifiedCookingLayout.TOOL
    } else {
        UnifiedCookingLayout.TIMED_WORK
    }
    override fun getInventory(): Inventory = backingInventory
}
