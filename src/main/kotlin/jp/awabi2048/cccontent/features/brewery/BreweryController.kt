package jp.awabi2048.cccontent.features.brewery

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.brewery.item.BreweryItemCodec
import jp.awabi2048.cccontent.features.brewery.model.BarrelSize
import jp.awabi2048.cccontent.features.brewery.model.BreweryLocationKey
import jp.awabi2048.cccontent.features.brewery.model.FirePower
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
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor

class BreweryController(private val plugin: JavaPlugin) : Listener {
    private val settingsLoader = BrewerySettingsLoader(plugin)
    private val codec = BreweryItemCodec(plugin)
    private val stateFile = File(plugin.dataFolder, "brewery/state.yml")
    private val filterRecipeKey = NamespacedKey(plugin, "brewery_sample_filter")
    private val rankManager = (plugin as? CCContent)?.getRankManager()

    private var settings: BrewerySettings = settingsLoader.loadSettings()
    private var recipes: Map<String, BreweryRecipe> = settingsLoader.loadRecipes()
    private var tickerTask: BukkitTask? = null
    private var autosaveTask: BukkitTask? = null

    private val fermentationStates = mutableMapOf<BreweryLocationKey, FermentationState>()
    private val distillationStates = mutableMapOf<BreweryLocationKey, DistillationState>()
    private val agingStates = mutableMapOf<BreweryLocationKey, AgingState>()
    private val machineLocks = mutableMapOf<BreweryLocationKey, java.util.UUID>()

    private data class BarrelSignContext(
        val size: BarrelSize,
        val woodType: String
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
        loadState()
        registerSampleFilterRecipe()

        tickerTask?.cancel()
        tickerTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            tickFermentation()
            tickDistillation()
        }, 20L, 20L)

        autosaveTask?.cancel()
        autosaveTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            saveState()
        }, 20L * 30L, 20L * 30L)
    }

    fun reload() {
        settings = settingsLoader.loadSettings()
        recipes = settingsLoader.loadRecipes()
        registerSampleFilterRecipe()
        saveState()
    }

    fun shutdown() {
        saveState()
        tickerTask?.cancel()
        autosaveTask?.cancel()
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

        if (block.state is Barrel) {
            val barrelContext = detectBarrelSignContext(block.location)
            if (barrelContext != null) {
                event.isCancelled = true
                openAging(event.player, BreweryLocationKey.fromBlock(block), barrelContext)
            }
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
            event.isCancelled = true
            return
        }

        if (event.clickedInventory == null) {
            event.isCancelled = true
            return
        }

        if (event.isShiftClick) {
            event.isCancelled = true
            handleShiftQuickMove(player, holder, event)
            return
        }

        if (event.clickedInventory != top) return

        event.isCancelled = true
        when (holder) {
            is FermentationHolder -> handleFermentationClick(player, holder.locationKey, event)
            is DistillationHolder -> handleDistillationClick(player, holder.locationKey, event)
            is AgingHolder -> handleAgingClick(player, holder.locationKey, event)
        }
    }

    private fun handleShiftQuickMove(player: Player, holder: InventoryHolder, event: InventoryClickEvent) {
        val clicked = event.clickedInventory ?: return
        val top = event.view.topInventory
        val source = event.currentItem ?: return
        if (source.type.isAir || isUiPlaceholderItem(source)) return

        val sourceBefore = source.amount

        if (clicked == top) {
            if (holder is DistillationHolder && event.slot in DISTILL_INPUT_SLOTS) {
                finalizeDistilledItem(source)
                distillationStates[holder.locationKey]?.elapsedSecondsInCurrentStep = 0
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
                parsed != null && parsed.stage != jp.awabi2048.cccontent.features.brewery.model.BrewStage.AGED
            }
        }

        return (before - item.amount).coerceAtLeast(0)
    }

    private fun quickMoveToAging(state: AgingState, item: ItemStack): Int {
        val before = item.amount
        val inputSlots = if (state.size == BarrelSize.BIG) BIG_AGING_INPUT_SLOTS else SMALL_AGING_INPUT_SLOTS
        val clockSlot = if (state.size == BarrelSize.BIG) BIG_AGING_CLOCK_SLOT else SMALL_AGING_CLOCK_SLOT

        if (item.type == Material.CLOCK) {
            moveToSingleSlot(item, state.inventory, clockSlot) { it.type == Material.CLOCK }
        }

        if (item.amount > 0) {
            moveToSlots(item, state.inventory, inputSlots) { codec.parse(it) != null }
        }

        return (before - item.amount).coerceAtLeast(0)
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
            val inv = Bukkit.createInventory(holder, 45, "発酵")
            holder.backingInventory = inv
            FermentationState(key, inv)
        }
        refreshFermentationDecor(state)
        player.openInventory(state.inventory)
    }

    private fun openDistillation(player: Player, key: BreweryLocationKey) {
        if (!acquireLock(player, key)) return
        val state = distillationStates.getOrPut(key) {
            val holder = DistillationHolder(key)
            val inv = Bukkit.createInventory(holder, 45, "蒸留")
            holder.backingInventory = inv
            DistillationState(key, inv)
        }
        refreshDistillationDecor(state)
        player.openInventory(state.inventory)
    }

    private fun openAging(player: Player, key: BreweryLocationKey, context: BarrelSignContext) {
        val size = context.size
        if (!acquireLock(player, key)) return
        val state = agingStates.getOrPut(key) {
            val invSize = if (size == BarrelSize.BIG) 45 else 27
            val holder = AgingHolder(key)
            val inv = Bukkit.createInventory(holder, invSize, if (size == BarrelSize.BIG) "熟成（大樽）" else "熟成（小樽）")
            holder.backingInventory = inv
            AgingState(key, size, inv, barrelWoodType = context.woodType)
        }
        if (state.size != size) {
            player.sendMessage("§c樽サイズが一致しません。再設置してください。")
            return
        }
        state.barrelWoodType = context.woodType
        applyAngelShare(state)
        refreshAgingDecor(state)
        player.openInventory(state.inventory)
    }

    private fun acquireLock(player: Player, key: BreweryLocationKey): Boolean {
        val owner = machineLocks[key]
        if (owner == null || owner == player.uniqueId) {
            machineLocks[key] = player.uniqueId
            return true
        }
        player.sendMessage("§cこの設備は他のプレイヤーが使用中です。")
        return false
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
            val moved = handleSingleSlotMove(event, setOf(FERMENT_CLOCK_SLOT)) { placing -> placing.type == Material.CLOCK }
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
                player.sendMessage("§e蒸留を停止しました。")
                playUiSuccessSound(player)
                refreshDistillationDecor(state)
                return
            }

            val hasValidItem = DISTILL_INPUT_SLOTS
                .mapNotNull { state.inventory.getItem(it) }
                .mapNotNull { codec.parse(it) }
                .any { it.stage != jp.awabi2048.cccontent.features.brewery.model.BrewStage.AGED }
            if (!hasValidItem) {
                player.sendMessage("§c蒸留素材がないため開始できません。")
                refreshDistillationDecor(state)
                return
            }

            state.running = true
            state.elapsedSecondsInCurrentStep = 0
            state.sessionDistillationRuns = 0
            player.sendMessage("§a蒸留を開始しました。")
            playStartSound(player)
            refreshDistillationDecor(state)
            return
        }

        if (slot == DISTILL_CLOCK_SLOT) {
            val moved = handleSingleSlotMove(event, setOf(DISTILL_CLOCK_SLOT)) { placing -> placing.type == Material.CLOCK }
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
        val inputSlots = if (state.size == BarrelSize.BIG) BIG_AGING_INPUT_SLOTS else SMALL_AGING_INPUT_SLOTS
        val barrelSlot = if (state.size == BarrelSize.BIG) BIG_AGING_BARREL_SLOT else SMALL_AGING_BARREL_SLOT
        val clockSlot = if (state.size == BarrelSize.BIG) BIG_AGING_CLOCK_SLOT else SMALL_AGING_CLOCK_SLOT

        if (slot == clockSlot) {
            val moved = handleSingleSlotMove(event, setOf(clockSlot)) { placing -> placing.type == Material.CLOCK }
            if (moved) playUiSuccessSound(player)
            refreshAgingDecor(state)
            return
        }

        if (slot == barrelSlot) {
            event.whoClicked.sendMessage("§7このスロットは樽種表示用です。")
            return
        }

        if (slot in inputSlots) {
            handleAgingInputSlot(event, state, slot)
            refreshAgingDecor(state)
            return
        }

        if (slot == if (state.size == BarrelSize.BIG) BIG_AGING_CORE_SLOT else SMALL_AGING_CORE_SLOT) {
            event.whoClicked.sendMessage("§7熟成コア（表示のみ）")
        }
    }

    private fun startFermentation(player: Player, state: FermentationState) {
        if (state.running) {
            state.running = false
            player.sendMessage("§e発酵を停止しました。")
            playUiSuccessSound(player)
            return
        }

        val fuel = state.inventory.getItem(FERMENT_FUEL_SLOT)
        val hasFuelNow = state.fuelRemainingSeconds > 0 || firePowerFor(fuel?.type) != null
        if (!hasFuelNow) {
            player.sendMessage("§c燃料がないため発酵を開始できません。")
            return
        }

        val inputItems = FERMENT_INPUT_SLOTS.mapNotNull { slot ->
            state.inventory.getItem(slot)?.takeUnless { it.type.isAir || isFermentationPlaceholderItem(it) }
        }
        if (inputItems.isEmpty()) {
            player.sendMessage("§c材料スロットにアイテムを入れてください。")
            return
        }

        val matchResult = settingsLoader.findBestRecipe(recipes, inputItems)
        if (matchResult == null) {
            player.sendMessage("§c一致するレシピが見つかりません。")
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
        state.currentBrewingItemName = recipe.name
        state.lastCalculatedQuality = matchResult.quality

        FERMENT_INPUT_SLOTS.forEach { state.inventory.setItem(it, null) }
        player.sendMessage("§a発酵を開始しました。§7(レシピ: ${recipe.name})")
        playStartSound(player)
    }

    private fun passesSkillRequirement(player: Player, recipe: BreweryRecipe): Boolean {
        if (recipe.requiredSkillLevel <= 1 && recipe.requiredSkills.isEmpty()) return true

        val profession = rankManager?.getPlayerProfession(player.uniqueId)
        if (profession == null) {
            player.sendMessage("§cこのレシピの実行には職業情報が必要です。")
            return false
        }

        if (profession.currentLevel < recipe.requiredSkillLevel) {
            player.sendMessage("§c必要職業レベル不足: Lv${recipe.requiredSkillLevel} が必要です。")
            return false
        }

        val missingSkills = recipe.requiredSkills.filterNot { it in profession.acquiredSkills }
        if (missingSkills.isNotEmpty()) {
            player.sendMessage("§c必要スキル不足: ${missingSkills.joinToString(", ")}")
            return false
        }
        return true
    }

    private fun bottleFermentation(player: Player, state: FermentationState, event: InventoryClickEvent) {
        if (!state.running && state.elapsedSeconds <= 0) {
            player.sendMessage("§c発酵が開始されていません。")
            return
        }

        if (state.producedBottleCount >= 3) {
            player.sendMessage("§eこのバッチは最大3本までです。")
            return
        }

        val cursor = event.cursor ?: return
        if (cursor.amount <= 0) return

        val recipe = recipes[state.recipeId]
        val quality = calculateFermentationQuality(state, recipe)
        val muddy = state.elapsedSeconds < 60
        val history = "fermentation=${state.elapsedSeconds}s;mismatchSteps=${state.mismatchPenaltyStepCount}"
        val product = codec.createFermentedBottle(state.recipeId, quality, muddy, history, recipe)

        cursor.amount = (cursor.amount - 1).coerceAtLeast(0)
        event.setCursor(if (cursor.amount == 0) ItemStack(Material.AIR) else cursor)

        val overflow = player.inventory.addItem(product)
        overflow.values.forEach { player.world.dropItemNaturally(player.location, it) }

        state.producedBottleCount += 1
        playUiSuccessSound(player)
        if (state.producedBottleCount >= 3) {
            state.running = false
            player.sendMessage("§a瓶詰めが完了しました。")
        }
    }

    private fun handleDistillationInputSlot(event: InventoryClickEvent, state: DistillationState, slot: Int) {
        val current = state.inventory.getItem(slot)
        val cursor = event.cursor
        val isPlaceholder = current != null && isUiPlaceholderItem(current)

        if (cursor == null || cursor.type.isAir) {
            if (current == null || current.type.isAir || isPlaceholder) return
            finalizeDistilledItem(current)
            event.setCursor(current)
            state.inventory.setItem(slot, null)
            state.elapsedSecondsInCurrentStep = 0
            (event.whoClicked as? Player)?.let { playUiSuccessSound(it) }
            return
        }

        if (cursor.amount != 1) {
            (event.whoClicked as? Player)?.sendMessage("§cスタック不可です。1個ずつ入れてください。")
            return
        }

        if (current != null && !current.type.isAir && !isPlaceholder) {
            return
        }

        val parsed = codec.parse(cursor)
        if (parsed == null || parsed.stage == jp.awabi2048.cccontent.features.brewery.model.BrewStage.AGED) {
            (event.whoClicked as? Player)?.sendMessage("§c発酵済みの醸造物のみ投入できます。")
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
                val startedAt = state.insertedAtEpochMillis[slot] ?: System.currentTimeMillis()
                val years = calculateAgingYears(startedAt, state.size)
                val finalQuality = (parsed.quality + years * 4.0).coerceIn(0.0, 100.0)
                val recipe = recipes[parsed.recipeId]
                codec.markAged(current, parsed, finalQuality, recipe)
                codec.clearAgingStart(current)
                debugQuality("aging", parsed.recipeId, parsed.quality, finalQuality, "years=${"%.2f".format(years)}")
            }
            state.insertedAtEpochMillis.remove(slot)
            event.setCursor(current)
            state.inventory.setItem(slot, null)
            (event.whoClicked as? Player)?.let { playUiSuccessSound(it) }
            return
        }

        if (cursor.amount != 1) {
            (event.whoClicked as? Player)?.sendMessage("§cスタック不可です。1個ずつ入れてください。")
            return
        }

        if (current != null && !current.type.isAir && !isPlaceholder) {
            return
        }

        val parsed = codec.parse(cursor)
        if (parsed == null) {
            (event.whoClicked as? Player)?.sendMessage("§c醸造物のみ投入できます。")
            return
        }

        val recipe = recipes[parsed.recipeId]
        state.locationKey.toLocation()?.let { loc ->
            detectBarrelSignContext(loc)?.let { ctx -> state.barrelWoodType = ctx.woodType }
        }
        if (recipe != null && !isBarrelTypeAllowed(state.barrelWoodType, recipe.agingBarrelTypes)) {
            (event.whoClicked as? Player)?.sendMessage("§cこのレシピは ${recipe.agingBarrelTypes.joinToString(", ")} 看板樽のみ対応です。")
            return
        }

        state.inventory.setItem(slot, cursor.clone())
        event.setCursor(ItemStack(Material.AIR))
        val now = System.currentTimeMillis()
        state.insertedAtEpochMillis[slot] = now
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
            (event.whoClicked as? Player)?.sendMessage("§cスタック不可です。1個ずつ入れてください。")
            return false
        }

        if (!placementValidator(cursor)) {
            (event.whoClicked as? Player)?.sendMessage("§cこのスロットには入れられません。")
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
        for (slot in SMALL_AGING_INPUT_SLOTS) {
            val item = state.inventory.getItem(slot) ?: continue
            if (item.type.isAir) continue
            val startedAt = state.insertedAtEpochMillis[slot] ?: continue
            val years = calculateAgingYears(startedAt, BarrelSize.SMALL)
            val probability = years * settings.angelSharePercentPerYear
            if (probability <= 0.0) continue

            val guaranteed = floor(probability / 100.0).toInt()
            val remainder = probability % 100.0
            val shouldEmpty = guaranteed >= 1 || Math.random() * 100.0 < remainder
            if (!shouldEmpty) continue

            state.inventory.setItem(slot, ItemStack(Material.GLASS_BOTTLE))
            state.insertedAtEpochMillis.remove(slot)
        }
    }

    private fun refreshFermentationDecor(state: FermentationState) {
        applyFermentationBackground(state)
        applyHeaderFooter(
            inventory = state.inventory,
            protectedSlots = FERMENT_INPUT_SLOTS.toSet() + setOf(FERMENT_YEAST_SLOT, FERMENT_FUEL_SLOT, FERMENT_START_SLOT, FERMENT_CLOCK_SLOT),
            headerText = "§3§l発酵システム",
            footerText = "§7素材を入れて中央で開始"
        )

        val start = ItemStack(Material.CAULDRON)
        val startMeta = start.itemMeta
        startMeta?.setDisplayName(if (state.running) "§e発酵停止" else "§a発酵開始")
        val recipeName = recipes[state.recipeId]?.name ?: state.currentBrewingItemName
        val fireText = when (state.currentFirePower) {
            FirePower.HIGH -> "高"
            FirePower.MEDIUM -> "中"
            FirePower.LOW -> "低"
            null -> "未点火"
        }
        startMeta?.lore(
            listOf(
                net.kyori.adventure.text.Component.text("§7火の強さ: $fireText"),
                net.kyori.adventure.text.Component.text("§7現在煮込み中: ${if (state.running) recipeName else "なし"}")
            )
        )
        start.itemMeta = startMeta
        state.inventory.setItem(FERMENT_START_SLOT, start)

        val clock = state.inventory.getItem(FERMENT_CLOCK_SLOT) ?: ItemStack(Material.COMPASS)
        val clockMeta = clock.itemMeta
        clockMeta?.setDisplayName("§b時計（モック）")
        val elapsed = state.elapsedSeconds
        clockMeta?.lore(
            listOf(
                net.kyori.adventure.text.Component.text("§7経過: ${elapsed}s"),
                net.kyori.adventure.text.Component.text("§7判定(30秒切り捨て): ${(elapsed / 30) * 30}s")
            )
        )
        clock.itemMeta = clockMeta
        state.inventory.setItem(FERMENT_CLOCK_SLOT, clock)
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
                inventory.setItem(slot, backgroundPane(Material.GRAY_STAINED_GLASS_PANE, " "))
            }
        }

        FERMENT_INPUT_SLOTS.forEach { slot ->
            val existing = inventory.getItem(slot)
            if (existing == null || existing.type.isAir) {
                inventory.setItem(slot, backgroundPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§b材料スロット"))
            }
        }

        val yeast = inventory.getItem(FERMENT_YEAST_SLOT)
        if (yeast == null || yeast.type.isAir) {
            inventory.setItem(FERMENT_YEAST_SLOT, backgroundPane(Material.GREEN_STAINED_GLASS_PANE, "§a酵母スロット（モック）"))
        }

        val fuel = inventory.getItem(FERMENT_FUEL_SLOT)
        if (fuel == null || fuel.type.isAir) {
            inventory.setItem(FERMENT_FUEL_SLOT, backgroundPane(Material.ORANGE_STAINED_GLASS_PANE, "§6燃料スロット"))
        }
    }

    private fun backgroundPane(material: Material, name: String): ItemStack {
        val pane = ItemStack(material)
        val meta = pane.itemMeta
        meta?.setDisplayName(name)
        pane.itemMeta = meta
        return pane
    }

    private fun isFermentationPlaceholderItem(item: ItemStack): Boolean {
        val validType = item.type == Material.GRAY_STAINED_GLASS_PANE ||
            item.type == Material.LIGHT_BLUE_STAINED_GLASS_PANE ||
            item.type == Material.GREEN_STAINED_GLASS_PANE ||
            item.type == Material.ORANGE_STAINED_GLASS_PANE
        if (!validType) return false

        val name = item.itemMeta?.displayName ?: return false
        return name == " " || name.contains("材料スロット") || name.contains("酵母スロット") || name.contains("燃料スロット")
    }

    private fun refreshDistillationDecor(state: DistillationState) {
        applyDistillationBackground(state)
        state.lastRequiredSeconds = estimateDistillationSeconds(state)
        applyHeaderFooter(
            inventory = state.inventory,
            protectedSlots = DISTILL_INPUT_SLOTS.toSet() + setOf(DISTILL_FILTER_SLOT, DISTILL_START_SLOT, DISTILL_CLOCK_SLOT),
            headerText = "§6§l蒸留システム",
            footerText = "§7黄色スロットに醸造物を投入"
        )

        val start = ItemStack(Material.BREWING_STAND)
        val meta = start.itemMeta
        meta?.setDisplayName(if (state.running) "§e蒸留停止" else "§a蒸留開始")
        meta?.lore(
            listOf(
                net.kyori.adventure.text.Component.text("§7現在ステップ経過: ${state.elapsedSecondsInCurrentStep}s"),
                net.kyori.adventure.text.Component.text("§7現在セッション蒸留回数: ${state.sessionDistillationRuns}"),
                net.kyori.adventure.text.Component.text("§71回の蒸留時間: ${state.lastRequiredSeconds}s")
            )
        )
        start.itemMeta = meta
        state.inventory.setItem(DISTILL_START_SLOT, start)

        val clock = state.inventory.getItem(DISTILL_CLOCK_SLOT) ?: ItemStack(Material.COMPASS)
        val clockMeta = clock.itemMeta
        clockMeta?.setDisplayName("§b時計（モック）")
        clock.itemMeta = clockMeta
        state.inventory.setItem(DISTILL_CLOCK_SLOT, clock)
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
                inventory.setItem(slot, backgroundPane(Material.GRAY_STAINED_GLASS_PANE, " "))
            }
        }

        DISTILL_INPUT_SLOTS.forEach { slot ->
            val existing = inventory.getItem(slot)
            if (existing == null || existing.type.isAir) {
                inventory.setItem(slot, backgroundPane(Material.YELLOW_STAINED_GLASS_PANE, "§e蒸留スロット"))
            }
        }

        val filter = inventory.getItem(DISTILL_FILTER_SLOT)
        if (filter == null || filter.type.isAir) {
            inventory.setItem(DISTILL_FILTER_SLOT, backgroundPane(Material.LIME_STAINED_GLASS_PANE, "§aフィルタースロット"))
        }
    }

    private fun applyHeaderFooter(
        inventory: Inventory,
        protectedSlots: Set<Int>,
        headerText: String,
        footerText: String
    ) {
        val headerSlots = 0..8
        for (slot in headerSlots) {
            if (slot in protectedSlots) continue
            inventory.setItem(slot, backgroundPane(Material.BLACK_STAINED_GLASS_PANE, headerText))
        }

        val footerSlots = (inventory.size - 9) until inventory.size
        for (slot in footerSlots) {
            if (slot in protectedSlots) continue
            if (inventory.getItem(slot) == null || isUiPlaceholderItem(inventory.getItem(slot)!!)) {
                inventory.setItem(slot, backgroundPane(Material.BLACK_STAINED_GLASS_PANE, footerText))
            }
        }
    }

    private fun isDistillationPlaceholderItem(item: ItemStack): Boolean {
        val validType = item.type == Material.GRAY_STAINED_GLASS_PANE ||
            item.type == Material.YELLOW_STAINED_GLASS_PANE ||
            item.type == Material.LIME_STAINED_GLASS_PANE
        if (!validType) return false
        val name = item.itemMeta?.displayName ?: return false
        return name == " " || name.contains("蒸留スロット") || name.contains("フィルタースロット")
    }

    private fun isHeaderFooterItem(item: ItemStack): Boolean {
        if (item.type != Material.BLACK_STAINED_GLASS_PANE) return false
        val name = item.itemMeta?.displayName ?: return false
        return name.contains("発酵システム") ||
            name.contains("素材を入れて") ||
            name.contains("蒸留システム") ||
            name.contains("黄色スロット") ||
            name.contains("熟成システム") ||
            name.contains("白スロットで熟成")
    }

    private fun isAgingPlaceholderItem(item: ItemStack): Boolean {
        val validType = item.type == Material.GRAY_STAINED_GLASS_PANE ||
            item.type == Material.WHITE_STAINED_GLASS_PANE ||
            item.type == Material.BROWN_STAINED_GLASS_PANE ||
            item.type == Material.YELLOW_STAINED_GLASS_PANE
        if (!validType) return false
        val name = item.itemMeta?.displayName ?: return false
        return name == " " ||
            name.contains("熟成スロット") ||
            name.contains("樽表示スロット") ||
            name.contains("時計スロット")
    }

    private fun isClockDisplayPlaceholder(item: ItemStack): Boolean {
        if (item.type != Material.COMPASS) return false
        val name = item.itemMeta?.displayName ?: return false
        return name.contains("時計") && name.contains("モック")
    }

    private fun isControlDisplayItem(item: ItemStack): Boolean {
        val name = item.itemMeta?.displayName ?: return false
        return name.contains("発酵開始") ||
            name.contains("発酵停止") ||
            name.contains("蒸留開始") ||
            name.contains("蒸留停止") ||
            name.contains("熟成コア") ||
            name.contains("大樽") ||
            name.contains("小樽")
    }

    private fun isUiPlaceholderItem(item: ItemStack): Boolean {
        return isFermentationPlaceholderItem(item) ||
            isDistillationPlaceholderItem(item) ||
            isAgingPlaceholderItem(item) ||
            isHeaderFooterItem(item) ||
            isClockDisplayPlaceholder(item) ||
            isControlDisplayItem(item)
    }

    private fun refreshAgingDecor(state: AgingState) {
        applyAgingBackground(state)
        val inputSlots = if (state.size == BarrelSize.BIG) BIG_AGING_INPUT_SLOTS else SMALL_AGING_INPUT_SLOTS
        val barrelSlot = if (state.size == BarrelSize.BIG) BIG_AGING_BARREL_SLOT else SMALL_AGING_BARREL_SLOT
        val coreSlot = if (state.size == BarrelSize.BIG) BIG_AGING_CORE_SLOT else SMALL_AGING_CORE_SLOT
        val clockSlot = if (state.size == BarrelSize.BIG) BIG_AGING_CLOCK_SLOT else SMALL_AGING_CLOCK_SLOT
        applyHeaderFooter(
            inventory = state.inventory,
            protectedSlots = inputSlots.toSet() + setOf(barrelSlot, coreSlot, clockSlot),
            headerText = "§6§l熟成システム",
            footerText = "§7白スロットで熟成・取り出し"
        )

        

        val barrel = ItemStack(if (state.size == BarrelSize.BIG) Material.DARK_OAK_LOG else Material.OAK_LOG)
        val barrelMeta = barrel.itemMeta
        barrelMeta?.setDisplayName(if (state.size == BarrelSize.BIG) "§f大樽" else "§f小樽")
        barrel.itemMeta = barrelMeta
        state.inventory.setItem(barrelSlot, barrel)

        val core = ItemStack(Material.BARREL)
        val coreMeta = core.itemMeta
        coreMeta?.setDisplayName("§6熟成コア")
        core.itemMeta = coreMeta
        state.inventory.setItem(coreSlot, core)

        val clock = state.inventory.getItem(clockSlot) ?: ItemStack(Material.COMPASS)
        val clockMeta = clock.itemMeta
        clockMeta?.setDisplayName("§bふしぎな時計（モック）")
        clockMeta?.lore(listOf(net.kyori.adventure.text.Component.text("§7投入のみ可能（効果なし）")))
        clock.itemMeta = clockMeta
        state.inventory.setItem(clockSlot, clock)
    }

    private fun applyAgingBackground(state: AgingState) {
        val inventory = state.inventory
        val inputSlots = if (state.size == BarrelSize.BIG) BIG_AGING_INPUT_SLOTS else SMALL_AGING_INPUT_SLOTS
        val barrelSlot = if (state.size == BarrelSize.BIG) BIG_AGING_BARREL_SLOT else SMALL_AGING_BARREL_SLOT
        val coreSlot = if (state.size == BarrelSize.BIG) BIG_AGING_CORE_SLOT else SMALL_AGING_CORE_SLOT
        val clockSlot = if (state.size == BarrelSize.BIG) BIG_AGING_CLOCK_SLOT else SMALL_AGING_CLOCK_SLOT
        val interactiveSlots = inputSlots.toSet() + setOf(barrelSlot, coreSlot, clockSlot)

        for (slot in 0 until inventory.size) {
            if (slot in interactiveSlots) continue
            val existing = inventory.getItem(slot)
            if (existing == null || existing.type.isAir) {
                inventory.setItem(slot, backgroundPane(Material.GRAY_STAINED_GLASS_PANE, " "))
            }
        }

        inputSlots.forEach { slot ->
            val existing = inventory.getItem(slot)
            if (existing == null || existing.type.isAir) {
                inventory.setItem(slot, backgroundPane(Material.WHITE_STAINED_GLASS_PANE, "§f熟成スロット"))
            }
        }

        val barrel = inventory.getItem(barrelSlot)
        if (barrel == null || barrel.type.isAir) {
            inventory.setItem(barrelSlot, backgroundPane(Material.BROWN_STAINED_GLASS_PANE, "§6樽表示スロット"))
        }

        val clock = inventory.getItem(clockSlot)
        if (clock == null || clock.type.isAir) {
            inventory.setItem(clockSlot, backgroundPane(Material.YELLOW_STAINED_GLASS_PANE, "§e時計スロット"))
        }
    }

    private fun detectBarrelSignContext(location: org.bukkit.Location): BarrelSignContext? {
        val world = location.world ?: return null
        val offsets = listOf(
            org.bukkit.util.Vector(1, 0, 0),
            org.bukkit.util.Vector(-1, 0, 0),
            org.bukkit.util.Vector(0, 0, 1),
            org.bukkit.util.Vector(0, 0, -1),
            org.bukkit.util.Vector(0, 1, 0),
            org.bukkit.util.Vector(0, -1, 0),
            org.bukkit.util.Vector(2, 0, 0),
            org.bukkit.util.Vector(-2, 0, 0),
            org.bukkit.util.Vector(0, 0, 2),
            org.bukkit.util.Vector(0, 0, -2),
            org.bukkit.util.Vector(0, 2, 0),
            org.bukkit.util.Vector(0, -2, 0)
        )
        for (offset in offsets) {
            val block = world.getBlockAt(
                location.blockX + offset.blockX,
                location.blockY + offset.blockY,
                location.blockZ + offset.blockZ
            )
            val sign = block.state as? Sign ?: continue
            val side = sign.getSide(Side.FRONT)
            val line = PlainTextComponentSerializer.plainText()
                .serialize(side.line(0))
                .trim()
                .lowercase()
            val woodType = signWoodType(block.type) ?: continue
            if (line == "barrel small") return BarrelSignContext(BarrelSize.SMALL, woodType)
            if (line == "barrel big") return BarrelSignContext(BarrelSize.BIG, woodType)
        }
        return null
    }

    private fun signWoodType(material: Material): String? {
        val key = material.name.lowercase()
        return when {
            key.contains("oak") && !key.contains("dark") && !key.contains("pale") -> "oak"
            key.contains("spruce") -> "spruce"
            key.contains("birch") -> "birch"
            key.contains("jungle") -> "jungle"
            key.contains("acacia") -> "acacia"
            key.contains("dark_oak") -> "dark_oak"
            key.contains("mangrove") -> "mangrove"
            key.contains("cherry") -> "cherry"
            key.contains("pale_oak") -> "pale_oak"
            key.contains("crimson") -> "crimson"
            key.contains("warped") -> "warped"
            else -> null
        }
    }

    private fun isBarrelTypeAllowed(currentWoodType: String, allowedTypes: Set<String>): Boolean {
        return "any" in allowedTypes || currentWoodType in allowedTypes
    }

    private fun finalizeDistilledItem(item: ItemStack) {
        val parsed = codec.parse(item) ?: return
        val before = parsed.quality
        val recipe = recipes[parsed.recipeId]
        val target = recipe?.distillationRuns ?: 1
        codec.markDistilled(item, parsed, target, settings.distillationOverPenalty, recipe)
        val after = codec.parse(item)?.quality ?: before
        debugQuality("distillation-finalize", parsed.recipeId, before, after, "targetRuns=$target,actualRuns=${parsed.distillCount}")
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
        val result = codec.buildSampleFilterItem()
        val recipe = ShapedRecipe(filterRecipeKey, result)
        recipe.shape("PSP", "SCS", "PSP")
        recipe.setIngredient('P', Material.PAPER)
        recipe.setIngredient('S', Material.STRING)
        recipe.setIngredient('C', Material.CHARCOAL)
        Bukkit.addRecipe(recipe)
    }

    private fun saveState() {
        val yml = YamlConfiguration()
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
            yml.set("$base.currentBrewingItemName", state.currentBrewingItemName)
            yml.set("$base.lastCalculatedQuality", state.lastCalculatedQuality)
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

        agingStates.forEach { (key, state) ->
            val base = "aging.${key.toSerialized()}"
            yml.set("$base.size", state.size.name)
            yml.set("$base.barrelWoodType", state.barrelWoodType)
            saveInventory(yml, "$base.inventory", state.inventory)
            state.insertedAtEpochMillis.forEach { (slot, epoch) ->
                yml.set("$base.inserted.$slot", epoch)
            }
        }

        stateFile.parentFile?.mkdirs()
        yml.save(stateFile)
    }

    private fun loadState() {
        fermentationStates.clear()
        distillationStates.clear()
        agingStates.clear()

        if (!stateFile.exists()) return
        val yml = YamlConfiguration.loadConfiguration(stateFile)

        val fSection = yml.getConfigurationSection("fermentation")
        fSection?.getKeys(false)?.forEach { rawKey ->
            val key = BreweryLocationKey.parse(rawKey) ?: return@forEach
            val holder = FermentationHolder(key)
            val inv = Bukkit.createInventory(holder, 45, "発酵")
            holder.backingInventory = inv
            loadInventory(yml, "fermentation.$rawKey.inventory", inv)
            val state = FermentationState(key, inv)
            state.running = yml.getBoolean("fermentation.$rawKey.running", false)
            state.elapsedSeconds = yml.getLong("fermentation.$rawKey.elapsedSeconds", 0L)
            state.fuelRemainingSeconds = yml.getInt("fermentation.$rawKey.fuelRemainingSeconds", 0)
            state.producedBottleCount = yml.getInt("fermentation.$rawKey.producedBottleCount", 0)
            state.recipeId = yml.getString("fermentation.$rawKey.recipeId", "unknown") ?: "unknown"
            state.mismatchPenaltyStepCount = yml.getInt("fermentation.$rawKey.mismatchPenaltyStepCount", 0)
            state.baseQuality = yml.getDouble("fermentation.$rawKey.baseQuality", 40.0)
            state.currentBrewingItemName = yml.getString("fermentation.$rawKey.currentBrewingItemName", "なし") ?: "なし"
            state.lastCalculatedQuality = yml.getDouble("fermentation.$rawKey.lastCalculatedQuality", state.baseQuality)
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
            val inv = Bukkit.createInventory(holder, 45, "蒸留")
            holder.backingInventory = inv
            loadInventory(yml, "distillation.$rawKey.inventory", inv)
            val state = DistillationState(key, inv)
            state.running = yml.getBoolean("distillation.$rawKey.running", false)
            state.elapsedSecondsInCurrentStep = yml.getInt("distillation.$rawKey.elapsedSecondsInCurrentStep", 0)
            state.sessionDistillationRuns = yml.getInt("distillation.$rawKey.sessionDistillationRuns", 0)
            state.lastRequiredSeconds = yml.getInt("distillation.$rawKey.lastRequiredSeconds", 45)
            distillationStates[key] = state
            refreshDistillationDecor(state)
        }

        val aSection = yml.getConfigurationSection("aging")
        aSection?.getKeys(false)?.forEach { rawKey ->
            val key = BreweryLocationKey.parse(rawKey) ?: return@forEach
            val size = runCatching {
                BarrelSize.valueOf(yml.getString("aging.$rawKey.size", "BIG")!!.uppercase())
            }.getOrDefault(BarrelSize.BIG)
            val invSize = if (size == BarrelSize.BIG) 45 else 27
            val holder = AgingHolder(key)
            val inv = Bukkit.createInventory(holder, invSize, if (size == BarrelSize.BIG) "熟成（大樽）" else "熟成（小樽）")
            holder.backingInventory = inv
            loadInventory(yml, "aging.$rawKey.inventory", inv)
            val state = AgingState(key, size, inv)
            state.barrelWoodType = yml.getString("aging.$rawKey.barrelWoodType", "any") ?: "any"
            val insertedSection = yml.getConfigurationSection("aging.$rawKey.inserted")
            insertedSection?.getKeys(false)?.forEach { slotRaw ->
                val slot = slotRaw.toIntOrNull() ?: return@forEach
                state.insertedAtEpochMillis[slot] = yml.getLong("aging.$rawKey.inserted.$slotRaw")
            }
            agingStates[key] = state
            refreshAgingDecor(state)
        }
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

    private data class FermentationState(
        val locationKey: BreweryLocationKey,
        val inventory: Inventory,
        var running: Boolean = false,
        var elapsedSeconds: Long = 0,
        var fuelRemainingSeconds: Int = 0,
        var producedBottleCount: Int = 0,
        var recipeId: String = "unknown",
        var mismatchPenaltyStepCount: Int = 0,
        var baseQuality: Double = 40.0,
        var currentFirePower: FirePower? = null,
        var inputIngredientCounts: Map<Material, Int> = emptyMap(),
        var currentBrewingItemName: String = "なし",
        var lastCalculatedQuality: Double = 40.0
    )

    private data class DistillationState(
        val locationKey: BreweryLocationKey,
        val inventory: Inventory,
        var running: Boolean = false,
        var elapsedSecondsInCurrentStep: Int = 0,
        var sessionDistillationRuns: Int = 0,
        var lastRequiredSeconds: Int = 45
    )

    private data class AgingState(
        val locationKey: BreweryLocationKey,
        val size: BarrelSize,
        val inventory: Inventory,
        val insertedAtEpochMillis: MutableMap<Int, Long> = mutableMapOf(),
        var barrelWoodType: String = "any"
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
