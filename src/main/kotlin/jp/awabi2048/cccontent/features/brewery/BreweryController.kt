@file:Suppress("DEPRECATION", "SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")

package jp.awabi2048.cccontent.features.brewery

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuIconAction
import com.awabi2048.ccsystem.api.gui.GuiMenuIconData
import com.awabi2048.ccsystem.api.gui.GuiMenuIconSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.config.CoreConfigManager
import jp.awabi2048.cccontent.features.brewery.item.BreweryItemCodec
import jp.awabi2048.cccontent.features.brewery.barrel.BarrelMatchResult
import jp.awabi2048.cccontent.features.brewery.barrel.BreweryBarrel
import jp.awabi2048.cccontent.features.brewery.barrel.BreweryBarrelMatcher
import jp.awabi2048.cccontent.features.brewery.barrel.BreweryBarrelRegistry
import jp.awabi2048.cccontent.features.brewery.barrel.BreweryBarrelStore
import jp.awabi2048.cccontent.features.brewery.model.BarrelSize
import jp.awabi2048.cccontent.features.brewery.model.BreweryLocationKey
import jp.awabi2048.cccontent.features.brewery.model.BrewStage
import jp.awabi2048.cccontent.features.brewery.model.FirePower
import jp.awabi2048.cccontent.features.brewery.BreweryIntoxicationMath
import jp.awabi2048.cccontent.features.brewery.BreweryIntoxicationState
import jp.awabi2048.cccontent.features.catalog.CatalogItem
import jp.awabi2048.cccontent.features.catalog.CatalogStore
import jp.awabi2048.cccontent.features.catalog.CatalogType
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Barrel
import org.bukkit.block.BrewingStand
import org.bukkit.block.Sign
import org.bukkit.block.data.type.Campfire
import org.bukkit.block.sign.Side
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.persistence.PersistentDataType
import jp.awabi2048.cccontent.util.cancelWithDebug
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.io.File
import java.util.UUID
import java.nio.charset.StandardCharsets
import kotlin.random.Random
import kotlin.math.ceil
import kotlin.math.floor

class BreweryController(private val plugin: JavaPlugin, private val catalogStore: CatalogStore) : Listener {
    private val settingsLoader = BrewerySettingsLoader(plugin)
    private val codec = BreweryItemCodec(plugin)
    private val stateFile = File(plugin.dataFolder, "data/brewery/state.yml")
    private val barrelMatcher = BreweryBarrelMatcher()
    private val barrelRegistry = BreweryBarrelRegistry()
    private val barrelStore = BreweryBarrelStore(
        File(plugin.dataFolder, "data/brewery/barrels.yml"),
        barrelMatcher,
        plugin.logger
    )
    private val filterRecipeKey = NamespacedKey(plugin, "brewery_sample_filter")
    private val mockClockKey = NamespacedKey(plugin, "brewery_mock_clock")
    private val uiKindKey = NamespacedKey(plugin, "brewery_ui_kind")
    private val rankManager = (plugin as? CCContent)?.getRankManager()

    private var settings: BrewerySettings = settingsLoader.loadSettings()
    private var recipes: Map<String, BreweryRecipe> = settingsLoader.loadRecipes()
    private var tickerTask: BukkitTask? = null
    private var autosaveTask: BukkitTask? = null
    private var dirty: Boolean = false

    private val fermentationStates = mutableMapOf<BreweryLocationKey, FermentationState>()
    private val distillationStates = mutableMapOf<BreweryLocationKey, DistillationState>()
    private val agingStates = mutableMapOf<BreweryLocationKey, AgingState>()
    private val machineLocks = mutableMapOf<BreweryLocationKey, java.util.UUID>()
    private val intoxicationStates = mutableMapOf<UUID, BreweryIntoxicationState>()

    private data class BarrelSignContext(
        val size: BarrelSize,
        val woodType: String,
        val barrelId: UUID
    )

    companion object {
        private val FERMENT_INPUT_SLOTS = listOf(20, 21, 22, 23, 24)
        private const val FERMENT_YEAST_SLOT = 37
        private const val FERMENT_FUEL_SLOT = 38
        private const val FERMENT_START_SLOT = 40
        private const val FERMENT_CLOCK_SLOT = 42

        private val DISTILL_INPUT_SLOTS = listOf(20, 22, 24)
        private const val DISTILL_FILTER_SLOT = 38
        private const val DISTILL_START_SLOT = 40
        private const val DISTILL_CLOCK_SLOT = 42

        private val BIG_AGING_INPUT_SLOTS = (10..16).toList() + (19..25).toList() + (28..34).toList()
        private const val BIG_AGING_BARREL_SLOT = 38
        private const val BIG_AGING_CORE_SLOT = 40
        private const val BIG_AGING_CLOCK_SLOT = 42

        private val SMALL_AGING_INPUT_SLOTS = (10..16).toList()
        private const val SMALL_AGING_BARREL_SLOT = 20
        private const val SMALL_AGING_CORE_SLOT = 22
        private const val SMALL_AGING_CLOCK_SLOT = 24
    }

    fun initialize() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        settings = settingsLoader.loadSettings()
        recipes = settingsLoader.loadRecipes()
        barrelRegistry.replaceAll(barrelStore.load())
        barrelStore.save(barrelRegistry.all())
        loadState()
        registerSampleFilterRecipe()

        tickerTask?.cancel()
        tickerTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            tickFermentation()
            tickDistillation()
            tickIntoxication()
            if (fermentationStates.isNotEmpty() || distillationStates.isNotEmpty() || agingStates.isNotEmpty() || intoxicationStates.isNotEmpty()) {
                markDirty()
            }
        }, 20L, 20L)

        autosaveTask?.cancel()
        autosaveTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            flushIfDirty()
        }, getFlushIntervalTicks(), getFlushIntervalTicks())
    }

    fun reload() {
        settings = settingsLoader.loadSettings()
        recipes = settingsLoader.loadRecipes()
        registerSampleFilterRecipe()
        flushNow()
    }

    fun shutdown() {
        flushNow()
        barrelStore.save(barrelRegistry.all())
        tickerTask?.cancel()
        autosaveTask?.cancel()
    }

    fun flushIfDirty() {
        if (!dirty) {
            return
        }
        saveStateInternal()
        dirty = false
    }

    @EventHandler
    fun onPlayerItemConsume(event: PlayerItemConsumeEvent) {
        val parsed = codec.parse(event.item) ?: return
        if (parsed.stage != BrewStage.AGED) return

        val recipe = recipes[parsed.recipeId] ?: return
        val now = System.currentTimeMillis()
        val state = intoxicationStates.getOrPut(event.player.uniqueId) { BreweryIntoxicationState() }
        BreweryIntoxicationMath.decay(
            state,
            now,
            settings.intoxicationDecayPerSecond,
            settings.stateRetentionSeconds
        )
        val alcohol = parsed.alcohol.coerceIn(-100.0, 100.0)
        state.alcohol = (state.alcohol + alcohol).coerceIn(0.0, 100.0)
        state.updatedAtMillis = now
        if (state.alcohol >= settings.faintThreshold) {
            state.faintUntilMillis = maxOf(state.faintUntilMillis, now + settings.faintDurationSeconds * 1000L)
        }

        recipe.finalOutputEffects.forEach { definition ->
            val resolved = definition.resolve(parsed.quality)
            event.player.addPotionEffect(
                PotionEffect(
                    resolved.type,
                    resolved.durationSeconds * 20,
                    resolved.amplifier,
                    false,
                    true,
                    true
                )
            )
        }
        recordCatalog(event.player.uniqueId, recipe.id, parsed.quality, drunk = true, obtained = false)
        applyIntoxicationEffects(event.player, state, now, forceStumble = true)
        event.player.sendMessage(
            i18n(
                event.player,
                "brewery.drink.completed",
                "recipe" to i18n(event.player, "brewery.recipe.${recipe.id}.name"),
                "alcohol" to "%.1f".format(state.alcohol)
            )
        )
        val drinkMessageKey = "brewery.recipe.${recipe.id}.drink_message"
        if (CCSystem.getAPI().hasI18nKey(drinkMessageKey)) {
            event.player.sendMessage(i18n(event.player, drinkMessageKey))
        }
        markDirty()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val state = intoxicationStates[event.player.uniqueId] ?: return
        val now = System.currentTimeMillis()
        BreweryIntoxicationMath.decay(
            state,
            now,
            settings.intoxicationDecayPerSecond,
            settings.stateRetentionSeconds
        )
        state.updatedAtMillis = now
        applyIntoxicationEffects(event.player, state, now, forceStumble = false)
        markDirty()
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        intoxicationStates[event.player.uniqueId]?.updatedAtMillis = System.currentTimeMillis()
        markDirty()
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val state = intoxicationStates[event.player.uniqueId] ?: return
        if (state.faintUntilMillis > System.currentTimeMillis() && event.from != event.to) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return

        if (isFermentationCauldron(block.type) && hasCampfireBelow(block.location)) {
            event.isCancelled = true
            openFermentation(event.player, BreweryLocationKey.fromBlock(block))
            return
        }

        if (block.state is BrewingStand && event.player.isSneaking) {
            event.isCancelled = true
            openDistillation(event.player, BreweryLocationKey.fromBlock(block))
            return
        }

        val registered = barrelRegistry.findByBlock(BreweryLocationKey.fromBlock(block))
        if (registered != null) {
            if (!event.player.hasPermission("cccontent.brewery.barrel.use")) {
                event.isCancelled = true
                return
            }
            if (registered.size == BarrelSize.BIG &&
                !settings.openLargeBarrelEverywhere &&
                BreweryLocationKey.fromBlock(block) != registered.sign
            ) {
                return
            }
            event.isCancelled = true
            openAging(
                event.player,
                registered.origin,
                BarrelSignContext(registered.size, registered.woodType, registered.id)
            )
            return
        }

        if (block.state is Barrel && settings.ageInMinecraftBarrels) {
            if (!event.player.hasPermission("cccontent.brewery.barrel.use")) {
                event.isCancelled = true
                return
            }
            event.isCancelled = true
            val key = BreweryLocationKey.fromBlock(block)
            openAging(
                event.player,
                key,
                BarrelSignContext(BarrelSize.MINECRAFT, "oak", minecraftBarrelId(key))
            )
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBarrelSignChange(event: SignChangeEvent) {
        val keyword = PlainTextComponentSerializer.plainText()
            .serialize(event.line(0) ?: net.kyori.adventure.text.Component.empty())
            .trim()
        if (settings.requireKeywordOnSigns && !keyword.equals("barrel", ignoreCase = true)) {
            return
        }
        val result = barrelMatcher.match(event.block)
        if (result !is BarrelMatchResult.Matched) {
            if (keyword.equals("barrel", ignoreCase = true)) {
                val failure = (result as BarrelMatchResult.Failed).failure
                event.player.sendMessage(
                    i18n(
                        event.player,
                        "brewery.error.barrel_structure",
                        "location" to failure.location.toSerialized(),
                        "expected" to failure.expected,
                        "actual" to failure.actual
                    )
                )
            }
            return
        }
        val permission = if (result.barrel.size == BarrelSize.BIG) {
            "cccontent.brewery.barrel.create.large"
        } else {
            "cccontent.brewery.barrel.create.small"
        }
        if (!event.player.hasPermission(permission)) {
            event.isCancelled = true
            return
        }
        if (!barrelRegistry.register(result.barrel)) {
            event.isCancelled = true
            event.player.sendMessage(i18n(event.player, "brewery.error.barrel_overlap"))
            return
        }
        barrelStore.save(barrelRegistry.all())
        event.player.sendMessage(
            i18n(
                event.player,
                "brewery.process.barrel_registered",
                "size" to result.barrel.size.name.lowercase(),
                "wood" to result.barrel.woodType
            )
        )
    }

    @EventHandler(ignoreCancelled = true)
    fun onBreweryInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder !is FermentationHolder &&
            event.view.topInventory.holder !is DistillationHolder &&
            event.view.topInventory.holder !is AgingHolder
        ) {
            return
        }
        if (event.rawSlots.any { it < event.view.topInventory.size }) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onMachineBreak(event: BlockBreakEvent) {
        val key = BreweryLocationKey.fromBlock(event.block)
        val barrel = barrelRegistry.findByBlock(key)
        if (barrel != null && !event.player.hasPermission("cccontent.brewery.barrel.break")) {
            event.isCancelled = true
            return
        }
        invalidateAt(key)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().map { BreweryLocationKey.fromBlock(it) }.distinct().forEach {
            invalidateAt(it)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().map { BreweryLocationKey.fromBlock(it) }.distinct().forEach {
            invalidateAt(it)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (event.blocks.any { hasMachineAt(BreweryLocationKey.fromBlock(it)) }) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (event.blocks.any { hasMachineAt(BreweryLocationKey.fromBlock(it)) }) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder
        val key = when (holder) {
            is FermentationHolder -> holder.locationKey
            is DistillationHolder -> holder.locationKey
            is AgingHolder -> holder.locationKey
            else -> null
        } ?: return

        val player = event.player as? Player ?: return
        val owner = machineLocks[key]
        if (owner == player.uniqueId) {
            machineLocks.remove(key)
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val holder = top.holder

        if (holder !is FermentationHolder && holder !is DistillationHolder && holder !is AgingHolder) {
            return
        }

        val player = event.whoClicked as? Player ?: return
        val locationKey = when (holder) {
            is FermentationHolder -> holder.locationKey
            is DistillationHolder -> holder.locationKey
            is AgingHolder -> holder.locationKey
            else -> null
        } ?: return

        val owner = machineLocks[locationKey]
        if (owner != null && owner != player.uniqueId) {
            event.cancelWithDebug("BreweryController.onInventoryClick: wrong_owner")
            return
        }

        if (event.clickedInventory == null) {
            event.cancelWithDebug("BreweryController.onInventoryClick: null_clicked_inv")
            return
        }

        if (event.isShiftClick) {
            event.cancelWithDebug("BreweryController.onInventoryClick: shift_click")
            handleShiftQuickMove(player, holder, event)
            return
        }

        if (event.clickedInventory != top) return

        event.cancelWithDebug("BreweryController.onInventoryClick: menu_click")
        if (event.rawSlot in setOf(FERMENT_START_SLOT, DISTILL_START_SLOT, BIG_AGING_CORE_SLOT, SMALL_AGING_CORE_SLOT) &&
            event.click !in setOf(ClickType.LEFT, ClickType.RIGHT)) {
            return
        }
        when (holder) {
            is FermentationHolder -> handleFermentationClick(player, holder.locationKey, event)
            is DistillationHolder -> handleDistillationClick(player, holder.locationKey, event)
            is AgingHolder -> handleAgingClick(player, holder.locationKey, event)
        }
        markDirty()
    }

    private fun handleShiftQuickMove(player: Player, holder: InventoryHolder, event: InventoryClickEvent) {
        val clicked = event.clickedInventory ?: return
        val top = event.view.topInventory
        val source = event.currentItem ?: return
        if (source.type.isAir || isUiPlaceholderItem(source)) return

        val sourceBefore = source.amount

        if (clicked == top) {
            if (holder is DistillationHolder && event.slot in DISTILL_INPUT_SLOTS) {
                finalizeDistilledItem(source, player)
                distillationStates[holder.locationKey]?.elapsedSecondsInCurrentStep = 0
            } else if (holder is AgingHolder && event.slot in agingInputSlots(holder)) {
                val agingState = agingStates[holder.locationKey] ?: return
                finalizeAgedItem(source, player, agingState, event.slot)
                agingState.insertedAtEpochMillis.remove(event.slot)
                agingState.angelShareEvaluatedAtMillis.remove(event.slot)
            }
            val moving = source.clone()
            val moved = moveToPlayerInventory(player, moving)
            if (moved <= 0) return
            source.amount = sourceBefore - moved
            if (source.amount <= 0) {
                clicked.setItem(event.slot, null)
            } else {
                clicked.setItem(event.slot, source)
            }
            when (holder) {
                is FermentationHolder -> fermentationStates[holder.locationKey]?.let { refreshFermentationDecor(it) }
                is DistillationHolder -> distillationStates[holder.locationKey]?.let { refreshDistillationDecor(it) }
                is AgingHolder -> agingStates[holder.locationKey]?.let { refreshAgingDecor(it) }
            }
            return
        }

        val moving = source.clone()
        val moved = when (holder) {
            is FermentationHolder -> {
                val state = fermentationStates[holder.locationKey] ?: return
                quickMoveToFermentation(state, moving)
            }

            is DistillationHolder -> {
                val state = distillationStates[holder.locationKey] ?: return
                val movedCount = quickMoveToDistillation(state, moving)
                if (movedCount > 0) {
                    state.elapsedSecondsInCurrentStep = 0
                }
                movedCount
            }

            is AgingHolder -> {
                val state = agingStates[holder.locationKey] ?: return
                quickMoveToAging(state, moving)
            }

            else -> 0
        }

        if (moved <= 0) return
        source.amount = sourceBefore - moved
        if (source.amount <= 0) {
            clicked.setItem(event.slot, null)
        } else {
            clicked.setItem(event.slot, source)
        }

        when (holder) {
            is FermentationHolder -> fermentationStates[holder.locationKey]?.let { refreshFermentationDecor(it) }
            is DistillationHolder -> distillationStates[holder.locationKey]?.let { refreshDistillationDecor(it) }
            is AgingHolder -> agingStates[holder.locationKey]?.let { refreshAgingDecor(it) }
        }
    }

    private fun moveToPlayerInventory(player: Player, item: ItemStack): Int {
        val overflow = player.inventory.addItem(item)
        val remaining = overflow.values.sumOf { it.amount }
        return (item.amount - remaining).coerceAtLeast(0)
    }

    private fun quickMoveToFermentation(state: FermentationState, item: ItemStack): Int {
        val before = item.amount

        if (firePowerFor(item.type) != null) {
            moveToSingleSlot(item, state.inventory, FERMENT_FUEL_SLOT) { firePowerFor(it.type) != null }
        }

        if (item.amount > 0) {
            moveToSlots(item, state.inventory, FERMENT_INPUT_SLOTS) {
                !isUiPlaceholderItem(it) && it.type != Material.GLASS_BOTTLE
            }
        }

        if (item.amount > 0) {
            moveToSingleSlot(item, state.inventory, FERMENT_YEAST_SLOT)
        }

        return (before - item.amount).coerceAtLeast(0)
    }

    private fun quickMoveToDistillation(state: DistillationState, item: ItemStack): Int {
        val before = item.amount

        if (codec.isSampleFilter(item)) {
            moveToSingleSlot(item, state.inventory, DISTILL_FILTER_SLOT) { codec.isSampleFilter(it) }
        }

        if (item.amount > 0) {
            moveToSlots(item, state.inventory, DISTILL_INPUT_SLOTS) {
                val parsed = codec.parse(it)
                val recipe = parsed?.recipeId?.let(recipes::get)
                parsed?.stage == BrewStage.FERMENTED && recipe != null && recipe.distillationRuns > 0
            }
        }

        return (before - item.amount).coerceAtLeast(0)
    }

    private fun quickMoveToAging(state: AgingState, item: ItemStack): Int {
        val before = item.amount
        val inputSlots = agingInputSlots(state)
        val clockSlot = if (state.size == BarrelSize.BIG) BIG_AGING_CLOCK_SLOT else SMALL_AGING_CLOCK_SLOT

        if (isClockAcceleratorItem(item)) {
            moveToSingleSlot(item, state.inventory, clockSlot) { isClockAcceleratorItem(it) }
        }

        if (item.amount > 0) {
            val emptyBefore = inputSlots.filter { state.inventory.getItem(it).isEmptyOrAir() }.toSet()
            moveToSlots(item, state.inventory, inputSlots) {
                val parsed = codec.parse(it)
                val recipe = parsed?.recipeId?.let(recipes::get)
                val stageReady = recipe != null && when {
                    recipe.distillationRuns > 0 ->
                        parsed?.stage == BrewStage.DISTILLED && parsed.distillCount == recipe.distillationRuns
                    else -> parsed?.stage == BrewStage.FERMENTED
                }
                recipe != null &&
                    recipe.agingTimeDays > 0 &&
                    stageReady &&
                    isBarrelTypeAllowed(state.barrelWoodType, recipe.agingBarrelTypes)
            }
            val now = System.currentTimeMillis()
            emptyBefore
                .filter { !state.inventory.getItem(it).isEmptyOrAir() }
                .forEach { slot ->
                    state.insertedAtEpochMillis[slot] = now
                    state.angelShareEvaluatedAtMillis[slot] = now
                    state.inventory.getItem(slot)?.let { codec.setAgingStart(it, now) }
                }
        }

        return (before - item.amount).coerceAtLeast(0)
    }

    private fun isClockAcceleratorItem(item: ItemStack): Boolean {
        if (item.type == Material.CLOCK) {
            return true
        }
        if (item.type != Material.POISONOUS_POTATO) {
            return false
        }
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(mockClockKey, PersistentDataType.BYTE)
    }

    private fun moveToSingleSlot(
        moving: ItemStack,
        inventory: Inventory,
        slot: Int,
        validator: (ItemStack) -> Boolean = { true }
    ) {
        if (moving.amount <= 0) return
        if (!validator(moving)) return

        val current = inventory.getItem(slot)
        if (current == null || current.type.isAir || isUiPlaceholderItem(current)) {
            val placeAmount = moving.amount.coerceAtMost(moving.maxStackSize.coerceAtLeast(1))
            val placed = moving.clone()
            placed.amount = placeAmount
            inventory.setItem(slot, placed)
            moving.amount -= placeAmount
            return
        }

        if (!current.isSimilar(moving)) return
        val maxStack = current.maxStackSize.coerceAtLeast(1)
        val transferable = (maxStack - current.amount).coerceAtLeast(0)
        if (transferable <= 0) return
        val moveAmount = moving.amount.coerceAtMost(transferable)
        current.amount += moveAmount
        moving.amount -= moveAmount
        inventory.setItem(slot, current)
    }

    private fun moveToSlots(
        moving: ItemStack,
        inventory: Inventory,
        slots: List<Int>,
        validator: (ItemStack) -> Boolean = { true }
    ) {
        if (moving.amount <= 0) return
        if (!validator(moving)) return

        for (slot in slots) {
            if (moving.amount <= 0) return
            val current = inventory.getItem(slot) ?: continue
            if (current.type.isAir || isUiPlaceholderItem(current)) continue
            if (!current.isSimilar(moving)) continue
            val maxStack = current.maxStackSize.coerceAtLeast(1)
            val transferable = (maxStack - current.amount).coerceAtLeast(0)
            if (transferable <= 0) continue
            val moveAmount = moving.amount.coerceAtMost(transferable)
            current.amount += moveAmount
            moving.amount -= moveAmount
            inventory.setItem(slot, current)
        }

        for (slot in slots) {
            if (moving.amount <= 0) return
            val current = inventory.getItem(slot)
            if (current != null && !current.type.isAir && !isUiPlaceholderItem(current)) continue
            val placeAmount = moving.amount.coerceAtMost(moving.maxStackSize.coerceAtLeast(1))
            val placed = moving.clone()
            placed.amount = placeAmount
            inventory.setItem(slot, placed)
            moving.amount -= placeAmount
        }
    }

    private fun openFermentation(player: Player, key: BreweryLocationKey) {
        if (!acquireLock(player, key)) return
        val state = fermentationStates.getOrPut(key) {
            val holder = FermentationHolder(key)
            val inv = Bukkit.createInventory(holder, 45, i18n(player, "brewery.ui.title.fermentation"))
            holder.backingInventory = inv
            FermentationState(key, inv)
        }
        localizeInventory(player, state)
        player.openInventory(state.inventory)
        refreshFermentationDecor(state, player)
    }

    private fun openDistillation(player: Player, key: BreweryLocationKey) {
        if (!acquireLock(player, key)) return
        val state = distillationStates.getOrPut(key) {
            val holder = DistillationHolder(key)
            val inv = Bukkit.createInventory(holder, 45, i18n(player, "brewery.ui.title.distillation"))
            holder.backingInventory = inv
            DistillationState(key, inv)
        }
        localizeInventory(player, state)
        player.openInventory(state.inventory)
        refreshDistillationDecor(state, player)
    }

    private fun openAging(player: Player, key: BreweryLocationKey, context: BarrelSignContext) {
        val size = context.size
        if (!acquireLock(player, key)) return
        val state = agingStates.getOrPut(key) {
            val invSize = if (size == BarrelSize.BIG) 45 else 27
            val holder = AgingHolder(key)
            val titleKey = when (size) {
                BarrelSize.BIG -> "brewery.ui.title.aging_big"
                BarrelSize.SMALL -> "brewery.ui.title.aging_small"
                BarrelSize.MINECRAFT -> "brewery.ui.title.aging_minecraft"
            }
            val inv = Bukkit.createInventory(holder, invSize, i18n(player, titleKey))
            holder.backingInventory = inv
            AgingState(
                key,
                size,
                inv,
                barrelWoodType = context.woodType,
                barrelId = context.barrelId
            )
        }
        if (state.size != size) {
            player.sendMessage(i18n(player, "brewery.error.barrel_size"))
            return
        }
        state.barrelWoodType = context.woodType
        state.barrelId = context.barrelId
        applyAngelShare(state)
        localizeInventory(player, state)
        player.openInventory(state.inventory)
        refreshAgingDecor(state, player)
    }

    private fun acquireLock(player: Player, key: BreweryLocationKey): Boolean {
        val owner = machineLocks[key]
        if (owner == null || owner == player.uniqueId) {
            machineLocks[key] = player.uniqueId
            return true
        }
        player.sendMessage(i18n(player, "brewery.error.machine_locked"))
        return false
    }

    private fun machinePlayer(key: BreweryLocationKey): Player? {
        return machineLocks[key]?.let { Bukkit.getPlayer(it) }
    }

    private fun handleFermentationClick(player: Player, key: BreweryLocationKey, event: InventoryClickEvent) {
        val state = fermentationStates[key] ?: return
        val slot = event.rawSlot

        if (slot == FERMENT_START_SLOT) {
            startFermentation(player, state)
            refreshFermentationDecor(state)
            return
        }

        if (slot == FERMENT_CLOCK_SLOT) {
            val moved = handleSingleSlotMove(event, setOf(FERMENT_CLOCK_SLOT)) { placing -> isClockAcceleratorItem(placing) }
            if (moved) playUiSuccessSound(player)
            refreshFermentationDecor(state)
            return
        }

        if (slot in FERMENT_INPUT_SLOTS) {
            val cursor = event.cursor
            if (cursor != null && cursor.type == Material.GLASS_BOTTLE && !cursor.type.isAir) {
                bottleFermentation(player, state, event)
                refreshFermentationDecor(state)
                return
            }
            handleFermentationInputSlotMove(event, state, slot)
            refreshFermentationDecor(state)
            return
        }

        if (slot == FERMENT_YEAST_SLOT) {
            val moved = handleSingleSlotMove(event, setOf(FERMENT_YEAST_SLOT))
            if (moved) playUiSuccessSound(player)
            refreshFermentationDecor(state)
            return
        }

        if (slot == FERMENT_FUEL_SLOT) {
            val moved = handleSingleSlotMove(
                event,
                setOf(FERMENT_FUEL_SLOT),
                placementValidator = { placing -> firePowerFor(placing.type) != null },
                allowStackPlacement = true
            )
            if (moved) playUiSuccessSound(player)
            refreshFermentationDecor(state)
            return
        }
    }

    private fun handleFermentationInputSlotMove(event: InventoryClickEvent, state: FermentationState, slot: Int) {
        val inv = state.inventory
        val current = inv.getItem(slot)
        val cursor = event.cursor
        val isPlaceholder = current != null && isFermentationPlaceholderItem(current)

        if (cursor == null || cursor.type.isAir) {
            if (current == null || current.type.isAir || isPlaceholder) return
            event.setCursor(current)
            inv.setItem(slot, null)
            return
        }

        if (current == null || current.type.isAir || isPlaceholder) {
            inv.setItem(slot, cursor.clone())
            event.setCursor(ItemStack(Material.AIR))
            return
        }

        if (!current.isSimilar(cursor)) {
            return
        }

        val maxStack = current.maxStackSize.coerceAtLeast(1)
        val transferable = (maxStack - current.amount).coerceAtLeast(0)
        if (transferable <= 0) {
            return
        }

        val moveAmount = cursor.amount.coerceAtMost(transferable)
        current.amount = current.amount + moveAmount
        cursor.amount = cursor.amount - moveAmount

        inv.setItem(slot, current)
        if (cursor.amount <= 0) {
            event.setCursor(ItemStack(Material.AIR))
        } else {
            event.setCursor(cursor)
        }
    }

    private fun handleDistillationClick(player: Player, key: BreweryLocationKey, event: InventoryClickEvent) {
        val state = distillationStates[key] ?: return
        val slot = event.rawSlot

        if (slot == DISTILL_START_SLOT) {
            if (state.running) {
                state.running = false
                state.elapsedSecondsInCurrentStep = 0
                state.sessionDistillationRuns = 0
                player.sendMessage(i18n(player, "brewery.process.distillation_stopped"))
                playUiSuccessSound(player)
                refreshDistillationDecor(state)
                return
            }

            val hasValidItem = DISTILL_INPUT_SLOTS
                .mapNotNull { state.inventory.getItem(it) }
                .mapNotNull { codec.parse(it) }
                .any { parsed ->
                    parsed.stage == BrewStage.FERMENTED &&
                        (recipes[parsed.recipeId]?.distillationRuns ?: 0) > 0
                }
            if (!hasValidItem) {
                player.sendMessage(i18n(player, "brewery.error.no_material"))
                refreshDistillationDecor(state)
                return
            }

            state.running = true
            state.elapsedSecondsInCurrentStep = 0
            state.sessionDistillationRuns = 0
            player.sendMessage(i18n(player, "brewery.process.distillation_started"))
            playStartSound(player)
            refreshDistillationDecor(state)
            return
        }

        if (slot == DISTILL_CLOCK_SLOT) {
            val moved = handleSingleSlotMove(event, setOf(DISTILL_CLOCK_SLOT)) { placing -> isClockAcceleratorItem(placing) }
            if (moved) playUiSuccessSound(player)
            refreshDistillationDecor(state)
            return
        }

        if (slot == DISTILL_FILTER_SLOT) {
            val moved = handleSingleSlotMove(event, setOf(DISTILL_FILTER_SLOT)) { placing -> codec.isSampleFilter(placing) }
            if (moved) playUiSuccessSound(player)
            refreshDistillationDecor(state)
            return
        }

        if (slot in DISTILL_INPUT_SLOTS) {
            handleDistillationInputSlot(event, state, slot)
            refreshDistillationDecor(state)
            return
        }
    }

    private fun handleAgingClick(player: Player, key: BreweryLocationKey, event: InventoryClickEvent) {
        val state = agingStates[key] ?: return
        val slot = event.rawSlot
        val inputSlots = agingInputSlots(state)
        val barrelSlot = if (state.size == BarrelSize.BIG) BIG_AGING_BARREL_SLOT else SMALL_AGING_BARREL_SLOT
        val clockSlot = if (state.size == BarrelSize.BIG) BIG_AGING_CLOCK_SLOT else SMALL_AGING_CLOCK_SLOT

        if (slot == clockSlot) {
            val moved = handleSingleSlotMove(event, setOf(clockSlot)) { placing -> isClockAcceleratorItem(placing) }
            if (moved) playUiSuccessSound(player)
            refreshAgingDecor(state)
            return
        }

        if (slot == barrelSlot) return

        if (slot in inputSlots) {
            handleAgingInputSlot(event, state, slot)
            refreshAgingDecor(state)
            return
        }

        if (slot == if (state.size == BarrelSize.BIG) BIG_AGING_CORE_SLOT else SMALL_AGING_CORE_SLOT) return
    }

    private fun startFermentation(player: Player, state: FermentationState) {
        if (state.running) {
            state.running = false
            player.sendMessage(i18n(player, "brewery.process.fermentation_stopped"))
            playUiSuccessSound(player)
            return
        }

        val fuel = state.inventory.getItem(FERMENT_FUEL_SLOT)
        val hasFuelNow = state.fuelRemainingSeconds > 0 || firePowerFor(fuel?.type) != null
        if (!hasFuelNow) {
            player.sendMessage(i18n(player, "brewery.error.no_fuel"))
            return
        }

        val inputItems = FERMENT_INPUT_SLOTS.mapNotNull { slot ->
            state.inventory.getItem(slot)?.takeUnless { it.type.isAir || isFermentationPlaceholderItem(it) }
        }
        if (inputItems.isEmpty()) {
            player.sendMessage(i18n(player, "brewery.error.no_ingredients"))
            return
        }

        val matchResult = settingsLoader.findBestRecipe(
            recipes,
            inputItems,
            settings.countDifferencePenalty,
            settings.unmatchedItemPenalty
        )
        if (matchResult == null) {
            player.sendMessage(i18n(player, "brewery.error.recipe_not_found"))
            return
        }

        val recipe = matchResult.recipe
        if (!passesSkillRequirement(player, recipe)) {
            return
        }

        if (settings.qualityDebugLog) {
            plugin.logger.info(
                "[BreweryDebug] fermentation match: player=${player.name}, recipe=${recipe.id}, typeMatch=${matchResult.typeMatchCount}, countDiff=${"%.2f".format(matchResult.countDifferenceScore)}, quality=${"%.2f".format(matchResult.quality)}, unmatched=${matchResult.unmatchedItemAmount}"
            )
        }

        val ingredientMap = mutableMapOf<Material, Int>()
        inputItems.forEach { item ->
            ingredientMap[item.type] = (ingredientMap[item.type] ?: 0) + item.amount
        }

        state.recipeId = recipe.id
        state.elapsedSeconds = 0
        state.fuelRemainingSeconds = 0
        state.producedBottleCount = 0
        state.mismatchPenaltyStepCount = 0
        state.currentFirePower = null
        state.running = true
        state.baseQuality = matchResult.quality
        state.inputIngredientCounts = ingredientMap.toMap()
        state.lastCalculatedQuality = matchResult.quality
        state.ownerUuid = player.uniqueId
        state.fermentationExpAwarded = false

        FERMENT_INPUT_SLOTS.forEach { state.inventory.setItem(it, null) }
        player.sendMessage(i18n(player, "brewery.process.fermentation_started", "recipe" to i18n(player, "brewery.recipe.${recipe.id}.name")))
        playStartSound(player)
    }

    private fun passesSkillRequirement(player: Player, recipe: BreweryRecipe): Boolean {
        if (recipe.requiredSkillLevel <= 1 && recipe.requiredSkills.isEmpty()) return true

        val profession = rankManager?.getPlayerProfession(player.uniqueId)
        if (profession == null) {
            player.sendMessage(i18n(player, "brewery.error.skill_required"))
            return false
        }

        val currentLevel = rankManager?.getCurrentProfessionLevel(player.uniqueId) ?: 1
        if (currentLevel < recipe.requiredSkillLevel) {
            player.sendMessage(i18n(player, "brewery.error.level_required", "level" to recipe.requiredSkillLevel))
            return false
        }

        val missingSkills = recipe.requiredSkills.filterNot { it in profession.acquiredSkills }
        if (missingSkills.isNotEmpty()) {
            player.sendMessage(i18n(player, "brewery.error.skills_required", "skills" to missingSkills.joinToString(", ")))
            return false
        }
        return true
    }

    private fun bottleFermentation(player: Player, state: FermentationState, event: InventoryClickEvent) {
        if (!state.running && state.elapsedSeconds <= 0) {
            player.sendMessage(i18n(player, "brewery.error.not_started"))
            return
        }

        if (state.producedBottleCount >= 3) {
            player.sendMessage(i18n(player, "brewery.error.batch_limit"))
            return
        }

        val cursor = event.cursor ?: return
        if (cursor.amount <= 0) return

        val recipe = recipes[state.recipeId]
        val quality = calculateFermentationQuality(state, recipe)
        val muddy = state.elapsedSeconds < 60
        val history = "fermentation=${state.elapsedSeconds}s;mismatchSteps=${state.mismatchPenaltyStepCount}"
        val product = codec.createFermentedBottle(state.recipeId, quality, muddy, history, recipe ?: return, player)
        if (!muddy && recipe.distillationRuns == 0 && recipe.agingTimeDays == 0) {
            codec.parse(product)?.let { parsed ->
                codec.markAged(product, parsed, quality, recipe, player)
                recordCatalog(player.uniqueId, recipe.id, quality, drunk = false, obtained = true)
            }
        }

        cursor.amount = (cursor.amount - 1).coerceAtLeast(0)
        event.setCursor(if (cursor.amount == 0) ItemStack(Material.AIR) else cursor)

        val overflow = player.inventory.addItem(product)
        overflow.values.forEach { player.world.dropItemNaturally(player.location, it) }

        state.producedBottleCount += 1
        if (!muddy && state.elapsedSeconds >= (recipe?.fermentationTime?.toLong() ?: Long.MAX_VALUE) && !state.fermentationExpAwarded) {
            awardProcessExp(player.uniqueId, settings.fermentationExp)
            state.fermentationExpAwarded = true
        }
        playUiSuccessSound(player)
        if (state.producedBottleCount >= 3) {
            state.running = false
            player.sendMessage(i18n(player, "brewery.process.bottled"))
        }
    }

    private fun handleDistillationInputSlot(event: InventoryClickEvent, state: DistillationState, slot: Int) {
        val current = state.inventory.getItem(slot)
        val cursor = event.cursor
        val isPlaceholder = current != null && isUiPlaceholderItem(current)

        if (cursor == null || cursor.type.isAir) {
            if (current == null || current.type.isAir || isPlaceholder) return
            finalizeDistilledItem(current, event.whoClicked as? Player)
            event.setCursor(current)
            state.inventory.setItem(slot, null)
            state.elapsedSecondsInCurrentStep = 0
            (event.whoClicked as? Player)?.let { playUiSuccessSound(it) }
            return
        }

        if (cursor.amount != 1) {
            (event.whoClicked as? Player)?.sendMessage(i18n(event.whoClicked as? Player, "brewery.error.single_item"))
            return
        }

        if (current != null && !current.type.isAir && !isPlaceholder) {
            return
        }

        val parsed = codec.parse(cursor)
        val recipe = parsed?.recipeId?.let(recipes::get)
        if (parsed == null ||
            parsed.stage != BrewStage.FERMENTED ||
            recipe == null ||
            recipe.distillationRuns <= 0
        ) {
            (event.whoClicked as? Player)?.sendMessage(i18n(event.whoClicked as? Player, "brewery.error.invalid_input"))
            return
        }

        state.inventory.setItem(slot, cursor.clone())
        event.setCursor(ItemStack(Material.AIR))
        state.elapsedSecondsInCurrentStep = 0
        (event.whoClicked as? Player)?.let { playUiSuccessSound(it) }
    }

    private fun handleAgingInputSlot(event: InventoryClickEvent, state: AgingState, slot: Int) {
        val current = state.inventory.getItem(slot)
        val cursor = event.cursor
        val isPlaceholder = current != null && isUiPlaceholderItem(current)

        if (cursor == null || cursor.type.isAir) {
            if (current == null || current.type.isAir || isPlaceholder) return
            val parsed = codec.parse(current)
            if (parsed != null) {
                finalizeAgedItem(current, event.whoClicked as? Player, state, slot)
            }
            state.insertedAtEpochMillis.remove(slot)
            state.angelShareEvaluatedAtMillis.remove(slot)
            event.setCursor(current)
            state.inventory.setItem(slot, null)
            (event.whoClicked as? Player)?.let { playUiSuccessSound(it) }
            return
        }

        if (cursor.amount != 1) {
            (event.whoClicked as? Player)?.sendMessage(i18n(event.whoClicked as? Player, "brewery.error.single_item"))
            return
        }

        if (current != null && !current.type.isAir && !isPlaceholder) {
            return
        }

        val parsed = codec.parse(cursor)
        val recipe = parsed?.recipeId?.let(recipes::get)
        val stageReady = recipe != null && when {
            recipe.distillationRuns > 0 ->
                parsed?.stage == BrewStage.DISTILLED && parsed.distillCount == recipe.distillationRuns
            else -> parsed?.stage == BrewStage.FERMENTED
        }
        if (parsed == null || recipe == null || recipe.agingTimeDays <= 0 || !stageReady) {
            (event.whoClicked as? Player)?.sendMessage(i18n(event.whoClicked as? Player, "brewery.error.invalid_input"))
            return
        }

        if (recipe != null && !isBarrelTypeAllowed(state.barrelWoodType, recipe.agingBarrelTypes)) {
            (event.whoClicked as? Player)?.sendMessage(i18n(event.whoClicked as? Player, "brewery.error.barrel_type", "types" to recipe.agingBarrelTypes.joinToString(", ")))
            return
        }

        state.inventory.setItem(slot, cursor.clone())
        event.setCursor(ItemStack(Material.AIR))
        val now = System.currentTimeMillis()
        state.insertedAtEpochMillis[slot] = now
        state.angelShareEvaluatedAtMillis[slot] = now
        state.inventory.getItem(slot)?.let { codec.setAgingStart(it, now) }
        (event.whoClicked as? Player)?.let { playStartSound(it) }
    }

    private fun handleSingleSlotMove(
        event: InventoryClickEvent,
        allowedSlots: Set<Int>,
        allowStackPlacement: Boolean = false,
        placementValidator: (ItemStack) -> Boolean = { true }
    ): Boolean {
        val slot = event.rawSlot
        if (slot !in allowedSlots) return false
        if (event.click == ClickType.DOUBLE_CLICK || event.click == ClickType.NUMBER_KEY) return false

        val inv = event.view.topInventory
        val current = inv.getItem(slot)
        val cursor = event.cursor
        val isPlaceholder = current != null && isUiPlaceholderItem(current)

        if (cursor == null || cursor.type.isAir) {
            if (current == null || current.type.isAir || isPlaceholder) return false
            event.setCursor(current)
            inv.setItem(slot, null)
            return true
        }

        if (!allowStackPlacement && cursor.amount != 1) {
            (event.whoClicked as? Player)?.sendMessage(i18n(event.whoClicked as? Player, "brewery.error.single_item"))
            return false
        }

        if (!placementValidator(cursor)) {
            (event.whoClicked as? Player)?.sendMessage(i18n(event.whoClicked as? Player, "brewery.error.invalid_slot"))
            return false
        }

        if (current != null && !current.type.isAir && !isPlaceholder) {
            if (!allowStackPlacement || !current.isSimilar(cursor)) {
                return false
            }

            val maxStack = current.maxStackSize.coerceAtLeast(1)
            val transferable = (maxStack - current.amount).coerceAtLeast(0)
            if (transferable <= 0) {
                return false
            }

            val moveAmount = cursor.amount.coerceAtMost(transferable)
            current.amount += moveAmount
            cursor.amount -= moveAmount
            inv.setItem(slot, current)
            if (cursor.amount <= 0) {
                event.setCursor(ItemStack(Material.AIR))
            } else {
                event.setCursor(cursor)
            }
            return true
        }

        val placeAmount = if (allowStackPlacement) {
            cursor.amount.coerceAtMost(cursor.maxStackSize.coerceAtLeast(1))
        } else {
            1
        }
        val placed = cursor.clone()
        placed.amount = placeAmount
        inv.setItem(slot, placed)

        cursor.amount -= placeAmount
        if (cursor.amount <= 0) {
            event.setCursor(ItemStack(Material.AIR))
        } else {
            event.setCursor(cursor)
        }
        return true
    }

    private fun tickFermentation() {
        for (state in fermentationStates.values) {
            if (!state.running) {
                continue
            }

            if (state.fuelRemainingSeconds <= 0) {
                val fuel = state.inventory.getItem(FERMENT_FUEL_SLOT)
                val fuelPower = firePowerFor(fuel?.type)
                if (fuel == null || fuel.type.isAir || fuelPower == null) {
                    continue
                }
                fuel.amount = (fuel.amount - 1).coerceAtLeast(0)
                if (fuel.amount <= 0) {
                    state.inventory.setItem(FERMENT_FUEL_SLOT, null)
                } else {
                    state.inventory.setItem(FERMENT_FUEL_SLOT, fuel)
                }
                state.currentFirePower = fuelPower
                state.fuelRemainingSeconds = settings.fuelSecondsPerItem
            }

            state.fuelRemainingSeconds -= 1
            state.elapsedSeconds += 1

            if (state.elapsedSeconds % 30 == 0L) {
                val recipe = recipes[state.recipeId]
                if (recipe != null && state.currentFirePower != recipe.fermentationIdealFirePower) {
                    state.mismatchPenaltyStepCount += 1
                    if (settings.qualityDebugLog) {
                        plugin.logger.info("[BreweryDebug] fermentation fire mismatch: recipe=${recipe.id}, step=${state.mismatchPenaltyStepCount}, fire=${state.currentFirePower}, ideal=${recipe.fermentationIdealFirePower}")
                    }
                }
            }
            spawnFermentationParticle(state)
            refreshFermentationDecor(state)
        }
    }

    private fun tickDistillation() {
        for (state in distillationStates.values) {
            if (!state.running) continue

            val items = DISTILL_INPUT_SLOTS.mapNotNull { slot -> state.inventory.getItem(slot)?.takeUnless { it.type.isAir } }
            if (items.isEmpty()) {
                state.running = false
                state.elapsedSecondsInCurrentStep = 0
                refreshDistillationDecor(state)
                continue
            }

            val parsedItems = items.mapNotNull { codec.parse(it) }
            if (parsedItems.isEmpty()) {
                state.running = false
                state.elapsedSecondsInCurrentStep = 0
                refreshDistillationDecor(state)
                continue
            }

            val baseSeconds = parsedItems.maxOfOrNull { recipes[it.recipeId]?.distillationTime ?: 45 } ?: 45
            val hasFilter = codec.isSampleFilter(state.inventory.getItem(DISTILL_FILTER_SLOT))
            val requiredSeconds = if (hasFilter) {
                ceil(baseSeconds * (1.0 - settings.filterSpeedBonus)).toInt().coerceAtLeast(1)
            } else {
                baseSeconds
            }
            state.lastRequiredSeconds = requiredSeconds

            state.elapsedSecondsInCurrentStep += 1
            if (state.elapsedSecondsInCurrentStep < requiredSeconds) {
                refreshDistillationDecor(state)
                continue
            }

            state.elapsedSecondsInCurrentStep = 0
            state.sessionDistillationRuns += 1
            DISTILL_INPUT_SLOTS.forEach { slot ->
                val item = state.inventory.getItem(slot) ?: return@forEach
                val parsed = codec.parse(item) ?: return@forEach
                val recipe = recipes[parsed.recipeId] ?: return@forEach
                val before = parsed.quality
                codec.incrementDistillation(item, recipe.distillationRuns, recipe.finalOutputAlcohol)
                val history = "${parsed.history};distill+1"
                codec.writeHistory(item, history)
                state.inventory.setItem(slot, item)
                val afterParsed = codec.parse(item)
                if (afterParsed != null) {
                    debugQuality("distillation-step", parsed.recipeId, before, afterParsed.quality, "sessionRuns=${state.sessionDistillationRuns}")
                }
            }

            if (hasFilter) {
                val filter = state.inventory.getItem(DISTILL_FILTER_SLOT)
                if (filter != null && !filter.type.isAir) {
                    val filterConsumption = parsedItems.mapNotNull { recipes[it.recipeId]?.distillationFilterConsumption }.maxOrNull() ?: 1
                    val broken = codec.damageFilter(filter, filterConsumption)
                    if (broken) {
                        state.inventory.setItem(DISTILL_FILTER_SLOT, null)
                    } else {
                        state.inventory.setItem(DISTILL_FILTER_SLOT, filter)
                    }
                }
            }

            refreshDistillationDecor(state)
        }
    }

    private fun calculateFermentationQuality(state: FermentationState, recipe: BreweryRecipe?): Double {
        val base = state.baseQuality
        val optimal = recipe?.fermentationTime ?: 180
        val timeScore = (20.0 - kotlin.math.abs(state.elapsedSeconds - optimal).toDouble() * 0.1).coerceIn(-20.0, 20.0)
        val mismatchPenalty = state.mismatchPenaltyStepCount * settings.mismatchFirePenaltyPer30Seconds
        val final = (base + timeScore - mismatchPenalty).coerceIn(0.0, 100.0)
        val previous = state.lastCalculatedQuality
        state.lastCalculatedQuality = final
        if (previous != final) {
            debugQuality("fermentation", state.recipeId, previous, final, "base=${"%.2f".format(base)},time=${"%.2f".format(timeScore)},mismatch=${"%.2f".format(mismatchPenalty)}")
        }
        return final
    }

    private fun calculateAgingYears(startedAt: Long, size: BarrelSize): Double {
        val elapsedSeconds = ((System.currentTimeMillis() - startedAt).coerceAtLeast(0L) / 1000.0)
        val multiplier = if (size == BarrelSize.SMALL) settings.smallBarrelSpeedMultiplier else 1.0
        return elapsedSeconds / settings.agingRealSecondsPerYear * multiplier
    }

    private fun applyAngelShare(state: AgingState) {
        if (state.size != BarrelSize.SMALL) return
        val now = System.currentTimeMillis()
        for (slot in SMALL_AGING_INPUT_SLOTS) {
            val item = state.inventory.getItem(slot) ?: continue
            if (item.type.isAir) continue
            val startedAt = state.insertedAtEpochMillis[slot] ?: continue
            val lastEvaluated = state.angelShareEvaluatedAtMillis[slot] ?: startedAt
            val elapsedSeconds = ((now - lastEvaluated).coerceAtLeast(0L) / 1000.0)
            val deltaYears = elapsedSeconds /
                settings.agingRealSecondsPerYear *
                settings.smallBarrelSpeedMultiplier
            state.angelShareEvaluatedAtMillis[slot] = now
            val expectedLoss = item.amount * deltaYears * settings.angelSharePercentPerYear / 100.0
            if (expectedLoss <= 0.0) continue

            val guaranteed = floor(expectedLoss).toInt()
            val remainder = expectedLoss - guaranteed
            val removed = (guaranteed + if (Math.random() < remainder) 1 else 0)
                .coerceIn(0, item.amount)
            if (removed <= 0) continue

            if (removed >= item.amount) {
                state.inventory.setItem(slot, ItemStack(Material.GLASS_BOTTLE, item.amount))
                state.insertedAtEpochMillis.remove(slot)
                state.angelShareEvaluatedAtMillis.remove(slot)
            } else {
                item.amount -= removed
                state.inventory.setItem(slot, item)
                state.locationKey.toLocation()?.world?.dropItemNaturally(
                    state.locationKey.toLocation()!!,
                    ItemStack(Material.GLASS_BOTTLE, removed)
                )
            }
        }
    }

    private fun refreshFermentationDecor(state: FermentationState, player: Player? = null) {
        val localePlayer = player ?: machinePlayer(state.locationKey)
        applyFermentationBackground(state)
        applyBlackFrame(state.inventory, FERMENT_INPUT_SLOTS.toSet() + setOf(FERMENT_YEAST_SLOT, FERMENT_FUEL_SLOT, FERMENT_START_SLOT, FERMENT_CLOCK_SLOT))
        val recipeName = recipes[state.recipeId]?.let { i18n(localePlayer, "brewery.recipe.${it.id}.name") }
            ?: i18n(localePlayer, "brewery.ui.data.none")
        val fireText = when (state.currentFirePower) {
            FirePower.HIGH -> i18n(localePlayer, "brewery.ui.fire.high")
            FirePower.MEDIUM -> i18n(localePlayer, "brewery.ui.fire.medium")
            FirePower.LOW -> i18n(localePlayer, "brewery.ui.fire.low")
            null -> i18n(localePlayer, "brewery.ui.fire.none")
        }
        state.inventory.setItem(FERMENT_START_SLOT, actionIcon(
            localePlayer,
            Material.CAULDRON,
            if (state.running) "brewery.ui.action.fermentation_stop" else "brewery.ui.action.fermentation_start",
            listOf(
                GuiMenuIconData(i18n(localePlayer, "brewery.ui.data.fire"), fireText, "§e"),
                GuiMenuIconData(i18n(localePlayer, "brewery.ui.data.current"), if (state.running) recipeName else i18n(localePlayer, "brewery.ui.data.none"), "§f")
            ),
            if (state.running) "brewery.ui.action.stop" else "brewery.ui.action.start"
        ))
    }

    private fun applyFermentationBackground(state: FermentationState) {
        val inventory = state.inventory
        val interactiveSlots = FERMENT_INPUT_SLOTS.toSet() + setOf(FERMENT_YEAST_SLOT, FERMENT_FUEL_SLOT, FERMENT_START_SLOT, FERMENT_CLOCK_SLOT)

        for (slot in 0 until inventory.size) {
            if (slot in interactiveSlots) {
                continue
            }
            val existing = inventory.getItem(slot)
            if (existing == null || existing.type.isAir) {
                inventory.setItem(slot, placeholder(Material.GRAY_STAINED_GLASS_PANE, "generic"))
            }
        }

        FERMENT_INPUT_SLOTS.forEach { slot ->
            val existing = inventory.getItem(slot)
            if (existing == null || existing.type.isAir) {
                inventory.setItem(slot, placeholder(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "fermentation_material"))
            }
        }

        val yeast = inventory.getItem(FERMENT_YEAST_SLOT)
        if (yeast == null || yeast.type.isAir) {
            inventory.setItem(FERMENT_YEAST_SLOT, placeholder(Material.GREEN_STAINED_GLASS_PANE, "fermentation_yeast"))
        }

        val fuel = inventory.getItem(FERMENT_FUEL_SLOT)
        if (fuel == null || fuel.type.isAir) {
            inventory.setItem(FERMENT_FUEL_SLOT, placeholder(Material.ORANGE_STAINED_GLASS_PANE, "fermentation_fuel"))
        }
        if (inventory.getItem(FERMENT_CLOCK_SLOT).isEmptyOrAir()) {
            inventory.setItem(FERMENT_CLOCK_SLOT, placeholder(Material.COMPASS, "clock"))
        }
    }

    private fun backgroundPane(material: Material): ItemStack {
        return CCSystem.getAPI().getGuiElementService().item(
            GuiItemSpec(material, GuiNameSpec.Empty, GuiLoreSpec.None, GuiElementRole.DECORATION, 1)
        ).also { markUi(it, "generic") }
    }

    private fun placeholder(material: Material, kind: String): ItemStack = backgroundPane(material).also { markUi(it, kind) }

    private fun uiItem(player: Player?, material: Material, nameKey: String, kind: String): ItemStack {
        return CCSystem.getAPI().getGuiElementService().item(
            GuiItemSpec(material, GuiNameSpec.Text(i18n(player, nameKey), com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT), GuiLoreSpec.None, GuiElementRole.DECORATION, 1)
        ).also { markUi(it, kind) }
    }

    private fun menuIcon(player: Player?, material: Material, nameKey: String, kind: String, data: List<GuiMenuIconData>, role: GuiElementRole = GuiElementRole.CONTENT): ItemStack {
        return CCSystem.getAPI().getGuiElementService().menuIcon(
            GuiMenuIconSpec(
                material = material,
                name = GuiNameSpec.Text(i18n(player, nameKey), com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT),
                role = role,
                amount = 1,
                description = emptyList(),
                data = data,
                options = emptyList(),
                warnings = emptyList(),
                dangers = emptyList(),
                actions = emptyList(),
                glint = null
            )
        ).also { markUi(it, kind) }
    }

    private fun actionIcon(
        player: Player?,
        material: Material,
        nameKey: String,
        data: List<GuiMenuIconData>,
        actionKey: String
    ): ItemStack {
        val operation = i18n(player, "lore.click.any")
        val action = i18n(player, actionKey)
        return CCSystem.getAPI().getGuiElementService().menuIcon(
            GuiMenuIconSpec(
                material = material,
                name = GuiNameSpec.Text(i18n(player, nameKey), com.awabi2048.ccsystem.api.gui.GuiNameStyle.DEFAULT),
                role = GuiElementRole.ACTION,
                amount = 1,
                description = emptyList(),
                data = data,
                options = emptyList(),
                warnings = emptyList(),
                dangers = emptyList(),
                actions = listOf(GuiMenuIconAction(operation, action, i18n(player, "lore.action_single_with_operation", "operation" to operation, "action" to action), true)),
                glint = null
            )
        ).also { markUi(it, "action") }
    }

    private fun markUi(item: ItemStack, kind: String) {
        item.editMeta { meta -> meta.persistentDataContainer.set(uiKindKey, PersistentDataType.STRING, kind) }
    }

    private fun uiKind(item: ItemStack?): String? = item?.itemMeta?.persistentDataContainer?.get(uiKindKey, PersistentDataType.STRING)

    private fun ItemStack?.isEmptyOrAir(): Boolean = this == null || this.type.isAir

    private fun isFermentationPlaceholderItem(item: ItemStack): Boolean {
        return uiKind(item)?.startsWith("fermentation_") == true || uiKind(item) == "generic"
    }

    private fun refreshDistillationDecor(state: DistillationState, player: Player? = null) {
        val localePlayer = player ?: machinePlayer(state.locationKey)
        applyDistillationBackground(state)
        applyBlackFrame(state.inventory, DISTILL_INPUT_SLOTS.toSet() + setOf(DISTILL_FILTER_SLOT, DISTILL_START_SLOT, DISTILL_CLOCK_SLOT))
        state.lastRequiredSeconds = estimateDistillationSeconds(state)
        state.inventory.setItem(DISTILL_START_SLOT, actionIcon(
            localePlayer,
            Material.BREWING_STAND,
            if (state.running) "brewery.ui.action.distillation_stop" else "brewery.ui.action.distillation_start",
            listOf(
                GuiMenuIconData(i18n(localePlayer, "brewery.ui.data.step_elapsed"), "${state.elapsedSecondsInCurrentStep}s", "§f"),
                GuiMenuIconData(i18n(localePlayer, "brewery.ui.data.session_runs"), state.sessionDistillationRuns, "§f"),
                GuiMenuIconData(i18n(localePlayer, "brewery.ui.data.duration"), "${state.lastRequiredSeconds}s", "§e")
            ),
            if (state.running) "brewery.ui.action.stop" else "brewery.ui.action.start"
        ))
    }

    private fun estimateDistillationSeconds(state: DistillationState): Int {
        val parsedItems = DISTILL_INPUT_SLOTS
            .mapNotNull { state.inventory.getItem(it) }
            .mapNotNull { codec.parse(it) }
        if (parsedItems.isEmpty()) return 45
        val baseSeconds = parsedItems.maxOfOrNull { recipes[it.recipeId]?.distillationTime ?: 45 } ?: 45
        val hasFilter = codec.isSampleFilter(state.inventory.getItem(DISTILL_FILTER_SLOT))
        return if (hasFilter) {
            ceil(baseSeconds * (1.0 - settings.filterSpeedBonus)).toInt().coerceAtLeast(1)
        } else {
            baseSeconds
        }
    }

    private fun applyDistillationBackground(state: DistillationState) {
        val inventory = state.inventory
        val interactiveSlots = DISTILL_INPUT_SLOTS.toSet() + setOf(DISTILL_FILTER_SLOT, DISTILL_START_SLOT, DISTILL_CLOCK_SLOT)

        for (slot in 0 until inventory.size) {
            if (slot in interactiveSlots) {
                continue
            }
            val existing = inventory.getItem(slot)
            if (existing == null || existing.type.isAir) {
                inventory.setItem(slot, placeholder(Material.GRAY_STAINED_GLASS_PANE, "generic"))
            }
        }

        DISTILL_INPUT_SLOTS.forEach { slot ->
            val existing = inventory.getItem(slot)
            if (existing == null || existing.type.isAir) {
                inventory.setItem(slot, placeholder(Material.YELLOW_STAINED_GLASS_PANE, "distillation_material"))
            }
        }

        val filter = inventory.getItem(DISTILL_FILTER_SLOT)
        if (filter == null || filter.type.isAir) {
            inventory.setItem(DISTILL_FILTER_SLOT, placeholder(Material.LIME_STAINED_GLASS_PANE, "distillation_filter"))
        }
        if (inventory.getItem(DISTILL_CLOCK_SLOT).isEmptyOrAir()) {
            inventory.setItem(DISTILL_CLOCK_SLOT, placeholder(Material.COMPASS, "clock"))
        }
    }

    private fun isDistillationPlaceholderItem(item: ItemStack): Boolean {
        return uiKind(item)?.startsWith("distillation_") == true || uiKind(item) == "generic"
    }

    private fun isAgingPlaceholderItem(item: ItemStack): Boolean {
        val validType = item.type == Material.GRAY_STAINED_GLASS_PANE ||
            item.type == Material.WHITE_STAINED_GLASS_PANE ||
            item.type == Material.BROWN_STAINED_GLASS_PANE ||
            item.type == Material.YELLOW_STAINED_GLASS_PANE
        if (!validType) return false
        return uiKind(item)?.startsWith("aging_") == true || uiKind(item) == "generic"
    }

    private fun isClockDisplayPlaceholder(item: ItemStack): Boolean {
        return uiKind(item) == "clock" || uiKind(item) == "aging_clock"
    }

    private fun isControlDisplayItem(item: ItemStack): Boolean {
        return uiKind(item) in setOf("action", "barrel", "aging_core")
    }

    private fun isUiPlaceholderItem(item: ItemStack): Boolean {
        return isFermentationPlaceholderItem(item) ||
            isDistillationPlaceholderItem(item) ||
            isAgingPlaceholderItem(item) ||
            isClockDisplayPlaceholder(item) ||
            isControlDisplayItem(item)
    }

    private fun refreshAgingDecor(state: AgingState, player: Player? = null) {
        val localePlayer = player ?: machinePlayer(state.locationKey)
        applyAgingBackground(state)
        val inputSlots = agingInputSlots(state)
        val barrelSlot = if (state.size == BarrelSize.BIG) BIG_AGING_BARREL_SLOT else SMALL_AGING_BARREL_SLOT
        val coreSlot = if (state.size == BarrelSize.BIG) BIG_AGING_CORE_SLOT else SMALL_AGING_CORE_SLOT
        val clockSlot = if (state.size == BarrelSize.BIG) BIG_AGING_CLOCK_SLOT else SMALL_AGING_CLOCK_SLOT
        applyBlackFrame(state.inventory, inputSlots.toSet() + setOf(barrelSlot, coreSlot, clockSlot))
        if (state.inventory.getItem(barrelSlot).isEmptyOrAir() || uiKind(state.inventory.getItem(barrelSlot)) == "barrel") {
            state.inventory.setItem(barrelSlot, uiItem(
                localePlayer,
                if (state.size == BarrelSize.BIG) Material.DARK_OAK_LOG else Material.OAK_LOG,
                when (state.size) {
                    BarrelSize.BIG -> "brewery.ui.barrel.big"
                    BarrelSize.SMALL -> "brewery.ui.barrel.small"
                    BarrelSize.MINECRAFT -> "brewery.ui.barrel.minecraft"
                },
                "barrel"
            ))
        }
        if (state.inventory.getItem(coreSlot).isEmptyOrAir() || uiKind(state.inventory.getItem(coreSlot)) == "aging_core") {
            state.inventory.setItem(coreSlot, menuIcon(
                localePlayer, Material.BARREL, "brewery.ui.aging_core", "aging_core",
                listOf(GuiMenuIconData(i18n(localePlayer, "brewery.ui.data.items"), inputSlots.count { !state.inventory.getItem(it).isEmptyOrAir() }, "§f")),
                GuiElementRole.CONTENT
            ))
        }
    }

    private fun applyAgingBackground(state: AgingState) {
        val inventory = state.inventory
        val inputSlots = agingInputSlots(state)
        val barrelSlot = if (state.size == BarrelSize.BIG) BIG_AGING_BARREL_SLOT else SMALL_AGING_BARREL_SLOT
        val coreSlot = if (state.size == BarrelSize.BIG) BIG_AGING_CORE_SLOT else SMALL_AGING_CORE_SLOT
        val clockSlot = if (state.size == BarrelSize.BIG) BIG_AGING_CLOCK_SLOT else SMALL_AGING_CLOCK_SLOT
        val interactiveSlots = inputSlots.toSet() + setOf(barrelSlot, coreSlot, clockSlot)

        for (slot in 0 until inventory.size) {
            if (slot in interactiveSlots) continue
            val existing = inventory.getItem(slot)
            if (existing == null || existing.type.isAir) {
                inventory.setItem(slot, placeholder(Material.GRAY_STAINED_GLASS_PANE, "generic"))
            }
        }

        inputSlots.forEach { slot ->
            val existing = inventory.getItem(slot)
            if (existing == null || existing.type.isAir) {
                inventory.setItem(slot, placeholder(Material.WHITE_STAINED_GLASS_PANE, "aging_material"))
            }
        }

        val barrel = inventory.getItem(barrelSlot)
        if (barrel == null || barrel.type.isAir) {
            inventory.setItem(barrelSlot, placeholder(Material.BROWN_STAINED_GLASS_PANE, "aging_barrel"))
        }

        val clock = inventory.getItem(clockSlot)
        if (clock == null || clock.type.isAir) {
            inventory.setItem(clockSlot, placeholder(Material.YELLOW_STAINED_GLASS_PANE, "aging_clock"))
        }
    }

    private fun isBarrelTypeAllowed(currentWoodType: String, allowedTypes: Set<String>): Boolean {
        return "any" in allowedTypes || currentWoodType in allowedTypes
    }

    private fun minecraftBarrelId(key: BreweryLocationKey): UUID =
        UUID.nameUUIDFromBytes("minecraft-barrel:${key.toSerialized()}".toByteArray(StandardCharsets.UTF_8))

    private fun hasMachineAt(key: BreweryLocationKey): Boolean =
        key in fermentationStates ||
            key in distillationStates ||
            key in agingStates ||
            barrelRegistry.findByBlock(key) != null

    private fun invalidateAt(key: BreweryLocationKey) {
        val registered = barrelRegistry.findByBlock(key)
        val canonicalKey = registered?.origin ?: key
        val inventories = buildList {
            fermentationStates.remove(canonicalKey)?.inventory?.let(::add)
            distillationStates.remove(canonicalKey)?.inventory?.let(::add)
            agingStates.remove(canonicalKey)?.inventory?.let(::add)
        }
        if (registered != null) {
            barrelRegistry.unregister(registered.id)
            barrelStore.save(barrelRegistry.all())
        }
        machineLocks.remove(canonicalKey)
        inventories.forEach { inventory ->
            inventory.viewers.toList().forEach { it.closeInventory() }
            inventory.clear()
        }
        if (inventories.isNotEmpty() || registered != null) {
            markDirty()
        }
    }

    private fun finalizeDistilledItem(item: ItemStack, player: Player?) {
        val parsed = codec.parse(item) ?: return
        val before = parsed.quality
        val recipe = recipes[parsed.recipeId]
        val target = recipe?.distillationRuns ?: 1
        if (recipe == null || target <= 0 || parsed.stage != BrewStage.FERMENTED) {
            return
        }
        codec.markDistilled(item, parsed, target, settings.distillationOverPenalty, recipe ?: return, player)
        if (!codec.hasStageExpAwarded(item, BrewStage.DISTILLED)) {
            player?.let { awardProcessExp(it.uniqueId, settings.distillationExp) }
            codec.markStageExpAwarded(item, BrewStage.DISTILLED)
        }
        var afterState = codec.parse(item)
        if (afterState != null &&
            afterState.distillCount == target &&
            recipe.agingTimeDays == 0
        ) {
            codec.markAged(item, afterState, afterState.quality, recipe, player)
            afterState = codec.parse(item)
            player?.let {
                recordCatalog(it.uniqueId, parsed.recipeId, afterState?.quality ?: before, drunk = false, obtained = true)
            }
        }
        val after = afterState?.quality ?: before
        debugQuality("distillation-finalize", parsed.recipeId, before, after, "targetRuns=$target,actualRuns=${parsed.distillCount}")
    }

    private fun applyBlackFrame(inventory: Inventory, protectedSlots: Set<Int>) {
        val frameSlots = (0..8) + ((inventory.size - 9) until inventory.size).toList()
        frameSlots.distinct().forEach { slot ->
            if (slot in protectedSlots) return@forEach
            val current = inventory.getItem(slot)
            if (current.isEmptyOrAir() || isUiPlaceholderItem(current!!)) {
                inventory.setItem(slot, backgroundPane(Material.BLACK_STAINED_GLASS_PANE))
            }
        }
    }

    private fun finalizeAgedItem(item: ItemStack, player: Player?, state: AgingState, slot: Int) {
        val parsed = codec.parse(item) ?: return
        if (parsed.stage == BrewStage.AGED) return
        val startedAt = state.insertedAtEpochMillis[slot] ?: System.currentTimeMillis()
        val years = calculateAgingYears(startedAt, state.size)
        val recipe = recipes[parsed.recipeId]
        val targetYears = recipe?.agingTimeDays?.coerceAtLeast(1) ?: 1
        val ageProgress = years / targetYears.toDouble()
        val finalQuality = (parsed.quality + ageProgress * 4.0).coerceIn(0.0, 100.0)
        codec.markAged(item, parsed, finalQuality, recipe ?: return, player)
        codec.clearAgingStart(item)
        if (!codec.hasStageExpAwarded(item, BrewStage.AGED)) {
            player?.let { awardProcessExp(it.uniqueId, settings.agingExp) }
            codec.markStageExpAwarded(item, BrewStage.AGED)
        }
        player?.let {
            recordCatalog(it.uniqueId, parsed.recipeId, finalQuality, drunk = false, obtained = true)
        }
        debugQuality("aging", parsed.recipeId, parsed.quality, finalQuality, "years=${"%.2f".format(years)},targetYears=$targetYears")
    }

    private fun agingInputSlots(holder: AgingHolder): List<Int> {
        val state = agingStates[holder.locationKey] ?: return emptyList()
        return agingInputSlots(state)
    }

    private fun agingInputSlots(state: AgingState): List<Int> = when (state.size) {
        BarrelSize.BIG -> BIG_AGING_INPUT_SLOTS.take(settings.barrelInventoryRowsLarge * 7)
        BarrelSize.SMALL -> SMALL_AGING_INPUT_SLOTS.take(settings.barrelInventoryRowsSmall * 7)
        BarrelSize.MINECRAFT -> SMALL_AGING_INPUT_SLOTS.take(settings.maxBrewsInMinecraftBarrels)
    }

    private fun awardProcessExp(playerUuid: UUID, amount: Long) {
        if (amount <= 0L) return
        rankManager?.addProfessionExp(playerUuid, amount)
    }

    private fun recordCatalog(
        playerUuid: UUID,
        recipeId: String,
        quality: Double,
        drunk: Boolean,
        obtained: Boolean
    ) {
        val before = catalogStore.entries(playerUuid, CatalogType.BREWERY)[recipeId]
        catalogStore.record(playerUuid, CatalogType.BREWERY, recipeId,
            qualityValue = quality, completion = null, obtained = obtained, drunk = drunk)
        if (before == null || !before.discovered) {
            val player = Bukkit.getPlayer(playerUuid)
            player?.sendMessage(i18n(player, "brewery.catalog.discovered", "recipe" to i18n(player, "brewery.recipe.$recipeId.name")))
        }
    }

    private fun tickIntoxication() {
        val now = System.currentTimeMillis()
        intoxicationStates.forEach { (uuid, state) ->
            val changed = BreweryIntoxicationMath.decay(
                state,
                now,
                settings.intoxicationDecayPerSecond,
                settings.stateRetentionSeconds
            )
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            applyIntoxicationEffects(player, state, now, forceStumble = changed)
        }
    }

    private fun applyIntoxicationEffects(
        player: Player,
        state: BreweryIntoxicationState,
        now: Long,
        forceStumble: Boolean
    ) {
        if (state.alcohol >= settings.nauseaThreshold) {
            player.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 40, 0, false, false, true))
        }
        if (state.alcohol >= settings.stumbleThreshold) {
            player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false, true))
            if (forceStumble && Random.nextBoolean()) {
                player.velocity = Vector(Random.nextDouble(-0.12, 0.12), 0.02, Random.nextDouble(-0.12, 0.12))
            }
        }
        if (state.faintUntilMillis > now) {
            player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, true))
            player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 10, false, false, true))
            player.velocity = Vector(0.0, 0.0, 0.0)
        }
    }

    private fun i18n(player: Player?, key: String, vararg placeholders: Pair<String, Any?>): String {
        return com.awabi2048.ccsystem.CCSystem.getAPI()
            .getI18nString(player, key, placeholders.associate { it.first to (it.second ?: "") })
            .replace('&', '§')
    }

    private fun playUiSuccessSound(player: Player) {
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.9f, 1.1f)
    }

    private fun playStartSound(player: Player) {
        player.playSound(player.location, Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.0f)
    }

    private fun spawnFermentationParticle(state: FermentationState) {
        val recipe = recipes[state.recipeId] ?: return
        val rawColor = recipe.fermentationParticleColor ?: return
        val location = state.locationKey.toLocation()?.add(0.5, 1.0, 0.5) ?: return
        val color = parseParticleColor(rawColor) ?: return
        val dust = Particle.DustOptions(color, 1.2f)
        location.world?.spawnParticle(Particle.DUST, location, 8, 0.2, 0.2, 0.2, 0.0, dust)
    }

    private fun parseParticleColor(raw: String): org.bukkit.Color? {
        val clean = raw.removePrefix("#")
        if (clean.length != 6) return null
        return try {
            val r = clean.substring(0, 2).toInt(16)
            val g = clean.substring(2, 4).toInt(16)
            val b = clean.substring(4, 6).toInt(16)
            org.bukkit.Color.fromRGB(r, g, b)
        } catch (_: Exception) {
            null
        }
    }

    private fun debugQuality(stage: String, recipeId: String, before: Double, after: Double, reason: String) {
        if (!settings.qualityDebugLog) return
        if (before == after) return
        plugin.logger.info(
            "[BreweryDebug] quality change: stage=$stage, recipe=$recipeId, before=${"%.2f".format(before)}, after=${"%.2f".format(after)}, delta=${"%.2f".format(after - before)}, reason=$reason"
        )
    }

    private fun firePowerFor(material: Material?): FirePower? {
        if (material == null) return null
        return when {
            material == Material.DRIED_KELP_BLOCK -> FirePower.LOW
            material == Material.BLAZE_ROD -> FirePower.HIGH
            material in settings.allowedMediumFireMaterials -> FirePower.MEDIUM
            else -> null
        }
    }

    private fun hasCampfireBelow(location: org.bukkit.Location): Boolean {
        val block = location.clone().add(0.0, -1.0, 0.0).block
        if (block.type != Material.CAMPFIRE) return false
        val data = block.blockData as? Campfire ?: return false
        return data.isLit
    }

    private fun isFermentationCauldron(type: Material): Boolean {
        return type == Material.CAULDRON || type == Material.WATER_CAULDRON
    }

    private fun registerSampleFilterRecipe() {
        Bukkit.removeRecipe(filterRecipeKey)
        val result = codec.buildSampleFilterItem(null)
        val recipe = ShapedRecipe(filterRecipeKey, result)
        recipe.shape("PSP", "SCS", "PSP")
        recipe.setIngredient('P', Material.PAPER)
        recipe.setIngredient('S', Material.STRING)
        recipe.setIngredient('C', Material.CHARCOAL)
        Bukkit.addRecipe(recipe)
    }

    private fun saveStateInternal() {
        val yml = YamlConfiguration()
        yml.set("schema_version", 3)
        fermentationStates.forEach { (key, state) ->
            val base = "fermentation.${key.toSerialized()}"
            yml.set("$base.running", state.running)
            yml.set("$base.elapsedSeconds", state.elapsedSeconds)
            yml.set("$base.fuelRemainingSeconds", state.fuelRemainingSeconds)
            yml.set("$base.producedBottleCount", state.producedBottleCount)
            yml.set("$base.recipeId", state.recipeId)
            yml.set("$base.mismatchPenaltyStepCount", state.mismatchPenaltyStepCount)
            yml.set("$base.baseQuality", state.baseQuality)
            yml.set("$base.currentFirePower", state.currentFirePower?.name)
            yml.set("$base.lastCalculatedQuality", state.lastCalculatedQuality)
            yml.set("$base.ownerUuid", state.ownerUuid?.toString())
            yml.set("$base.fermentationExpAwarded", state.fermentationExpAwarded)
            state.inputIngredientCounts.forEach { (material, count) ->
                yml.set("$base.ingredients.$material", count)
            }
            saveInventory(yml, "$base.inventory", state.inventory)
        }

        distillationStates.forEach { (key, state) ->
            val base = "distillation.${key.toSerialized()}"
            yml.set("$base.running", state.running)
            yml.set("$base.elapsedSecondsInCurrentStep", state.elapsedSecondsInCurrentStep)
            yml.set("$base.sessionDistillationRuns", state.sessionDistillationRuns)
            yml.set("$base.lastRequiredSeconds", state.lastRequiredSeconds)
            saveInventory(yml, "$base.inventory", state.inventory)
        }

        agingStates.forEach { (_, state) ->
            val base = "aging.${state.barrelId}"
            yml.set("$base.locationKey", state.locationKey.toSerialized())
            yml.set("$base.size", state.size.name)
            yml.set("$base.barrelWoodType", state.barrelWoodType)
            saveInventory(yml, "$base.inventory", state.inventory)
            state.insertedAtEpochMillis.forEach { (slot, epoch) ->
                yml.set("$base.inserted.$slot", epoch)
            }
            state.angelShareEvaluatedAtMillis.forEach { (slot, epoch) ->
                yml.set("$base.angelShareEvaluatedAt.$slot", epoch)
            }
        }

        intoxicationStates.forEach { (uuid, state) ->
            val base = "intoxication.$uuid"
            yml.set("$base.alcohol", state.alcohol)
            yml.set("$base.updatedAtMillis", state.updatedAtMillis)
            yml.set("$base.faintUntilMillis", state.faintUntilMillis)
        }
        stateFile.parentFile?.mkdirs()
        yml.save(stateFile)
    }

    private fun flushNow() {
        saveStateInternal()
        dirty = false
    }

    private fun loadState() {
        fermentationStates.clear()
        distillationStates.clear()
        agingStates.clear()
        intoxicationStates.clear()

        if (!stateFile.exists()) return
        val yml = YamlConfiguration.loadConfiguration(stateFile)
        val schemaVersion = yml.getInt("schema_version", -1)
        require(schemaVersion == 3) { "Brewery状態のschema_versionが3ではありません: $schemaVersion" }

        val fSection = yml.getConfigurationSection("fermentation")
        fSection?.getKeys(false)?.forEach { rawKey ->
            val key = BreweryLocationKey.parse(rawKey) ?: return@forEach
            val holder = FermentationHolder(key)
            val inv = Bukkit.createInventory(holder, 45, " ")
            holder.backingInventory = inv
            loadInventory(yml, "fermentation.$rawKey.inventory", inv)
            val block = key.toLocation()?.block
            if (block == null || !isFermentationCauldron(block.type) || !hasCampfireBelow(block.location)) {
                inv.clear()
                plugin.logger.warning("[Brewery] 存在しない発酵設備の状態を除外しました: $rawKey")
                return@forEach
            }
            val state = FermentationState(key, inv)
            state.running = yml.getBoolean("fermentation.$rawKey.running", false)
            state.elapsedSeconds = yml.getLong("fermentation.$rawKey.elapsedSeconds", 0L)
            state.fuelRemainingSeconds = yml.getInt("fermentation.$rawKey.fuelRemainingSeconds", 0)
            state.producedBottleCount = yml.getInt("fermentation.$rawKey.producedBottleCount", 0)
            state.recipeId = yml.getString("fermentation.$rawKey.recipeId") ?: return@forEach
            state.mismatchPenaltyStepCount = yml.getInt("fermentation.$rawKey.mismatchPenaltyStepCount", 0)
            state.baseQuality = yml.getDouble("fermentation.$rawKey.baseQuality", 40.0)
            state.lastCalculatedQuality = yml.getDouble("fermentation.$rawKey.lastCalculatedQuality", state.baseQuality)
            state.ownerUuid = yml.getString("fermentation.$rawKey.ownerUuid")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            state.fermentationExpAwarded = yml.getBoolean("fermentation.$rawKey.fermentationExpAwarded", false)
            state.currentFirePower = yml.getString("fermentation.$rawKey.currentFirePower")?.let {
                runCatching { FirePower.valueOf(it) }.getOrNull()
            }
            val ingredientsSection = yml.getConfigurationSection("fermentation.$rawKey.ingredients")
            ingredientsSection?.getKeys(false)?.forEach { materialName ->
                runCatching { Material.valueOf(materialName) }.getOrNull()?.let { material ->
                    state.inputIngredientCounts = state.inputIngredientCounts + (material to yml.getInt("fermentation.$rawKey.ingredients.$materialName", 1))
                }
            }
            fermentationStates[key] = state
            refreshFermentationDecor(state)
        }

        val dSection = yml.getConfigurationSection("distillation")
        dSection?.getKeys(false)?.forEach { rawKey ->
            val key = BreweryLocationKey.parse(rawKey) ?: return@forEach
            val holder = DistillationHolder(key)
            val inv = Bukkit.createInventory(holder, 45, " ")
            holder.backingInventory = inv
            loadInventory(yml, "distillation.$rawKey.inventory", inv)
            if (key.toLocation()?.block?.state !is BrewingStand) {
                inv.clear()
                plugin.logger.warning("[Brewery] 存在しない蒸留設備の状態を除外しました: $rawKey")
                return@forEach
            }
            val state = DistillationState(key, inv)
            state.running = yml.getBoolean("distillation.$rawKey.running", false)
            state.elapsedSecondsInCurrentStep = yml.getInt("distillation.$rawKey.elapsedSecondsInCurrentStep", 0)
            state.sessionDistillationRuns = yml.getInt("distillation.$rawKey.sessionDistillationRuns", 0)
            state.lastRequiredSeconds = yml.getInt("distillation.$rawKey.lastRequiredSeconds", 45)
            distillationStates[key] = state
            refreshDistillationDecor(state)
        }

        val aSection = yml.getConfigurationSection("aging")
        aSection?.getKeys(false)?.forEach { rawId ->
            val barrelId = runCatching { UUID.fromString(rawId) }.getOrNull() ?: return@forEach
            val base = "aging.$rawId"
            val key = BreweryLocationKey.parse(yml.getString("$base.locationKey") ?: "") ?: return@forEach
            val size = runCatching {
                BarrelSize.valueOf(yml.getString("$base.size", "BIG")!!.uppercase())
            }.getOrDefault(BarrelSize.BIG)
            val invSize = if (size == BarrelSize.BIG) 45 else 27
            val holder = AgingHolder(key)
            val inv = Bukkit.createInventory(holder, invSize, " ")
            holder.backingInventory = inv
            loadInventory(yml, "$base.inventory", inv)
            val validStructure = when (size) {
                BarrelSize.MINECRAFT -> key.toLocation()?.block?.type == Material.BARREL
                BarrelSize.SMALL,
                BarrelSize.BIG -> barrelRegistry.findById(barrelId)?.origin == key
            }
            if (!validStructure) {
                inv.clear()
                plugin.logger.warning("[Brewery] 存在しない熟成設備の状態を除外しました: $rawId")
                return@forEach
            }
            val state = AgingState(key, size, inv, barrelId = barrelId)
            state.barrelWoodType = yml.getString("$base.barrelWoodType", "any") ?: "any"
            val insertedSection = yml.getConfigurationSection("$base.inserted")
            insertedSection?.getKeys(false)?.forEach { slotRaw ->
                val slot = slotRaw.toIntOrNull() ?: return@forEach
                state.insertedAtEpochMillis[slot] = yml.getLong("$base.inserted.$slotRaw")
            }
            val evaluatedSection = yml.getConfigurationSection("$base.angelShareEvaluatedAt")
            evaluatedSection?.getKeys(false)?.forEach { slotRaw ->
                val slot = slotRaw.toIntOrNull() ?: return@forEach
                state.angelShareEvaluatedAtMillis[slot] = yml.getLong("$base.angelShareEvaluatedAt.$slotRaw")
            }
            agingStates[key] = state
            refreshAgingDecor(state)
        }

        yml.getConfigurationSection("intoxication")?.getKeys(false)?.forEach { rawUuid ->
            val uuid = runCatching { UUID.fromString(rawUuid) }.getOrNull() ?: return@forEach
            intoxicationStates[uuid] = BreweryIntoxicationState(
                alcohol = yml.getDouble("intoxication.$rawUuid.alcohol", 0.0).coerceIn(0.0, 100.0),
                updatedAtMillis = yml.getLong("intoxication.$rawUuid.updatedAtMillis", 0L),
                faintUntilMillis = yml.getLong("intoxication.$rawUuid.faintUntilMillis", 0L)
            )
        }
        dirty = false
    }

    fun catalogItems(): List<CatalogItem> = recipes.values.map { CatalogItem(it.id, Material.POTION) }

    private fun markDirty() {
        dirty = true
    }

    private fun getFlushIntervalTicks(): Long {
        val minutes = CoreConfigManager.get(plugin)
            .getLong("persistence.flush_interval_minutes", 1L)
            .coerceAtLeast(1L)
        return minutes * 60L * 20L
    }

    private fun saveInventory(yml: YamlConfiguration, path: String, inventory: Inventory) {
        yml.set("$path.size", inventory.size)
        for (slot in 0 until inventory.size) {
            yml.set("$path.slots.$slot", inventory.getItem(slot))
        }
    }

    private fun loadInventory(yml: YamlConfiguration, path: String, inventory: Inventory) {
        val section = yml.getConfigurationSection("$path.slots") ?: return
        section.getKeys(false).forEach { rawSlot ->
            val slot = rawSlot.toIntOrNull() ?: return@forEach
            val item = yml.getItemStack("$path.slots.$rawSlot")
            inventory.setItem(slot, item)
        }
    }

    private fun localizeInventory(player: Player, state: FermentationState) {
        replaceInventoryIfNeeded(player, state, "brewery.ui.title.fermentation")
    }

    private fun localizeInventory(player: Player, state: DistillationState) {
        replaceInventoryIfNeeded(player, state, "brewery.ui.title.distillation")
    }

    private fun localizeInventory(player: Player, state: AgingState) {
        replaceInventoryIfNeeded(
            player,
            state,
            if (state.size == BarrelSize.BIG) "brewery.ui.title.aging_big" else "brewery.ui.title.aging_small"
        )
    }

    private fun replaceInventoryIfNeeded(player: Player, state: FermentationState, titleKey: String) {
        val title = i18n(player, titleKey)
        val holder = state.inventory.holder as? FermentationHolder ?: return
        val replacement = Bukkit.createInventory(holder, state.inventory.size, title)
        copyInventoryContents(state.inventory, replacement)
        holder.backingInventory = replacement
        state.inventory = replacement
    }

    private fun replaceInventoryIfNeeded(player: Player, state: DistillationState, titleKey: String) {
        val title = i18n(player, titleKey)
        val holder = state.inventory.holder as? DistillationHolder ?: return
        val replacement = Bukkit.createInventory(holder, state.inventory.size, title)
        copyInventoryContents(state.inventory, replacement)
        holder.backingInventory = replacement
        state.inventory = replacement
    }

    private fun replaceInventoryIfNeeded(player: Player, state: AgingState, titleKey: String) {
        val title = i18n(player, titleKey)
        val holder = state.inventory.holder as? AgingHolder ?: return
        val replacement = Bukkit.createInventory(holder, state.inventory.size, title)
        copyInventoryContents(state.inventory, replacement)
        holder.backingInventory = replacement
        state.inventory = replacement
    }

    private fun copyInventoryContents(source: Inventory, target: Inventory) {
        for (slot in 0 until minOf(source.size, target.size)) target.setItem(slot, source.getItem(slot))
    }

    private data class FermentationState(
        val locationKey: BreweryLocationKey,
        var inventory: Inventory,
        var running: Boolean = false,
        var elapsedSeconds: Long = 0,
        var fuelRemainingSeconds: Int = 0,
        var producedBottleCount: Int = 0,
        var recipeId: String = "",
        var mismatchPenaltyStepCount: Int = 0,
        var baseQuality: Double = 40.0,
        var currentFirePower: FirePower? = null,
        var inputIngredientCounts: Map<Material, Int> = emptyMap(),
        var lastCalculatedQuality: Double = 40.0,
        var ownerUuid: UUID? = null,
        var fermentationExpAwarded: Boolean = false
    )

    private data class DistillationState(
        val locationKey: BreweryLocationKey,
        var inventory: Inventory,
        var running: Boolean = false,
        var elapsedSecondsInCurrentStep: Int = 0,
        var sessionDistillationRuns: Int = 0,
        var lastRequiredSeconds: Int = 45
    )

    private data class AgingState(
        val locationKey: BreweryLocationKey,
        val size: BarrelSize,
        var inventory: Inventory,
        val insertedAtEpochMillis: MutableMap<Int, Long> = mutableMapOf(),
        val angelShareEvaluatedAtMillis: MutableMap<Int, Long> = mutableMapOf(),
        var barrelWoodType: String = "any",
        var barrelId: UUID
    )

    private class FermentationHolder(val locationKey: BreweryLocationKey) : InventoryHolder {
        lateinit var backingInventory: Inventory
        override fun getInventory(): Inventory = backingInventory
    }

    private class DistillationHolder(val locationKey: BreweryLocationKey) : InventoryHolder {
        lateinit var backingInventory: Inventory
        override fun getInventory(): Inventory = backingInventory
    }

    private class AgingHolder(val locationKey: BreweryLocationKey) : InventoryHolder {
        lateinit var backingInventory: Inventory
        override fun getInventory(): Inventory = backingInventory
    }
}
