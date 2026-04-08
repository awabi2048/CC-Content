package jp.awabi2048.cccontent.features.arena

import jp.awabi2048.cccontent.items.arena.ArenaOverEnchanterCatalystData
import jp.awabi2048.cccontent.items.arena.ArenaOverEnchanterMode
import jp.awabi2048.cccontent.features.arena.mission.ArenaMissionService
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.SoundCategory
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.Locale
import java.util.UUID

private object ArenaEnchantPedestalLayout {
    const val MENU_SIZE = 45
    const val TOOL_SLOT = 22
    const val EXP_SLOT = 25
    const val EXECUTE_SLOT = 40
    const val PROGRESS_SLOT = 37
    const val INFO_SLOT = 43
    val CATALYST_SLOTS: List<Int> = listOf(10, 19, 28)
    const val REINFORCE_SLOT = 19
    val CENTER_WHITE_SLOTS: Set<Int> = setOf(12, 13, 14, 18, 20, 21, 23, 24, 26, 30, 31, 32)
    val ACTIVE_BACKGROUND_SLOTS: Set<Int> = (9..35).toSet() - setOf(TOOL_SLOT)
    val TITLE: String
        get() = ArenaI18n.text(null, "arena.ui.pedestal.title", "§8不思議な祭壇")

    val REVEAL_STEPS: List<List<Int>> = listOf(
        listOf(13, 31),
        listOf(12, 14, 21, 23, 30, 32),
        listOf(11, 15, 20, 24, 29, 33),
        listOf(10, 16, 18, 19, 25, 26, 28, 34)
    )
    val COLLAPSE_STEPS: List<List<Int>> = REVEAL_STEPS.reversed()
}

private class ArenaEnchantPedestalHolder(
    val ownerId: UUID
) : InventoryHolder {
    var backingInventory: Inventory? = null

    override fun getInventory(): Inventory {
        return backingInventory ?: Bukkit.createInventory(this, ArenaEnchantPedestalLayout.MENU_SIZE, ArenaEnchantPedestalLayout.TITLE)
    }
}

private enum class PanelColor {
    PURPLE,
    BLUE,
    RED,
    GRAY
}

private enum class PedestalUiState {
    NO_TOOL,
    TOOL_NO_OVER,
    TOOL_HAS_OVER
}

private data class PreparedCatalyst(
    val slot: Int,
    val item: ItemStack,
    val catalyst: ArenaOverEnchanterCatalystData.Catalyst,
    val requiredLevel: Int
)

private data class PedestalEvaluation(
    val catalystReady: Boolean,
    val processable: Boolean,
    val expReady: Boolean,
    val executable: Boolean,
    val requiredLevel: Int?,
    val missingLevel: Int?,
    val uiState: PedestalUiState,
    val unlockedSlotCount: Int,
    val usedSlotCount: Int,
    val preparedCatalysts: List<PreparedCatalyst>
)

private data class SlotUnlockProgress(
    val successCount: Int,
    val unlockedSlotCount: Int,
    val currentThreshold: Int,
    val nextThreshold: Int?
)

private data class OverEnchantState(
    val entries: Map<String, Int>
)

private data class ViewerRuntime(
    var panelAnimationTask: BukkitTask? = null,
    var forgeAnimationTask: BukkitTask? = null,
    var isForging: Boolean = false,
    var lastExecutable: Boolean = false
)

class ArenaEnchantPedestalMenu(
    private val plugin: JavaPlugin,
    private val coreConfigProvider: () -> FileConfiguration,
    private val missionServiceProvider: () -> ArenaMissionService?
) : Listener {
    private companion object {
        val PROTECTION_IDS: Set<String> = setOf("protection", "fire_protection", "blast_protection", "projectile_protection")
        val DAMAGE_IDS: Set<String> = setOf("sharpness", "smite", "bane_of_arthropods")
        val DEFAULT_SLOT_UNLOCKS: Map<Int, Int> = mapOf(1 to 0, 2 to 30, 3 to 100)
        val ENCHANTMENT_SYMBOLS: Map<String, String> = mapOf(
            "sharpness" to "⚔",
            "smite" to "☠",
            "bane_of_arthropods" to "✢",
            "protection" to "⛨",
            "fire_protection" to "♨",
            "blast_protection" to "✹",
            "projectile_protection" to "➶",
            "multishot" to "✣",
            "piercing" to "❖",
            "mending" to "✚",
            "infinity" to "∞",
            "breach" to "⛏"
        )
    }

    private val runtimes = mutableMapOf<UUID, ViewerRuntime>()
    private val overEnchantEntriesKey = NamespacedKey(plugin, "over_enchanter_entries")
    private val overEnchantLoreManagedKey = NamespacedKey(plugin, "over_enchanter_lore_managed")
    private val inputPlaceholderKey = NamespacedKey(plugin, "pedestal_input_placeholder")

    fun openMenu(player: Player) {
        val holder = ArenaEnchantPedestalHolder(player.uniqueId)
        val inventory = Bukkit.createInventory(holder, ArenaEnchantPedestalLayout.MENU_SIZE, ArenaEnchantPedestalLayout.TITLE)
        holder.backingInventory = inventory
        runtimes[player.uniqueId] = ViewerRuntime()
        renderStatic(player, inventory)
        player.openInventory(inventory)
        playSound(player, "minecraft:block.ender_chest.open", 0.5f)
        playSound(player, "minecraft:entity.illusioner.mirror_move", 0.75f)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        event.view.topInventory.holder as? ArenaEnchantPedestalHolder ?: return
        event.isCancelled = true
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? ArenaEnchantPedestalHolder ?: return
        val player = event.whoClicked as? Player ?: return
        if (player.uniqueId != holder.ownerId) {
            event.isCancelled = true
            return
        }

        val runtime = runtimes.getOrPut(holder.ownerId) { ViewerRuntime() }
        if (runtime.isForging) {
            event.isCancelled = true
            return
        }

        if (isBlockedClickType(event)) {
            event.isCancelled = true
            return
        }

        val top = event.view.topInventory
        val clickedTop = event.clickedInventory == top

        if (event.isShiftClick) {
            handleShiftClick(event, player, top, clickedTop)
            return
        }

        if (!clickedTop) {
            return
        }

        val rawSlot = event.rawSlot
        if (rawSlot == ArenaEnchantPedestalLayout.EXECUTE_SLOT) {
            event.isCancelled = true
            handleExecuteClick(player, top, runtime)
            return
        }

        if (rawSlot == ArenaEnchantPedestalLayout.PROGRESS_SLOT || rawSlot == ArenaEnchantPedestalLayout.INFO_SLOT || rawSlot == ArenaEnchantPedestalLayout.EXP_SLOT) {
            event.isCancelled = true
            return
        }

        if (rawSlot != ArenaEnchantPedestalLayout.TOOL_SLOT && rawSlot !in ArenaEnchantPedestalLayout.CATALYST_SLOTS) {
            event.isCancelled = true
            return
        }

        if (isBlockedInventoryAction(event.action)) {
            event.isCancelled = true
            return
        }

        if (event.click != ClickType.LEFT && event.click != ClickType.RIGHT) {
            event.isCancelled = true
            return
        }

        if (isInputPlaceholder(top.getItem(rawSlot))) {
            if (event.cursor.isNullOrAir()) {
                event.isCancelled = true
                return
            }
            if (!isTopSlotOperationAllowed(event, rawSlot)) {
                event.isCancelled = true
                return
            }
            event.isCancelled = true
            placeFromCursorIntoInputSlot(event, top, rawSlot)
            val wasEmpty = true
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val nowEmpty = getInputItem(top, rawSlot).isNullOrAir()
                processInputStateChange(player, top, wasEmpty, nowEmpty)
            })
            return
        }

        if (!isTopSlotOperationAllowed(event, rawSlot)) {
            event.isCancelled = true
            return
        }

        val wasEmpty = getInputItem(top, rawSlot).isNullOrAir()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val nowEmpty = getInputItem(top, rawSlot).isNullOrAir()
            processInputStateChange(player, top, wasEmpty, nowEmpty)
        })
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.view.topInventory.holder as? ArenaEnchantPedestalHolder ?: return
        val player = event.player as? Player ?: return
        val runtime = runtimes.remove(holder.ownerId)
        runtime?.panelAnimationTask?.cancel()
        runtime?.forgeAnimationTask?.cancel()
        runtime?.isForging = false

        playSound(player, "minecraft:block.ender_chest.close", 0.5f)
        playSound(player, "minecraft:entity.allay.item_thrown", 0.5f)

        val top = event.inventory
        ArenaEnchantPedestalLayout.CATALYST_SLOTS.forEach { slot ->
            returnItemToPlayer(player, top, slot)
        }
        returnItemToPlayer(player, top, ArenaEnchantPedestalLayout.TOOL_SLOT)
    }

    private fun isBlockedClickType(event: InventoryClickEvent): Boolean {
        return when (event.click) {
            ClickType.NUMBER_KEY,
            ClickType.DOUBLE_CLICK,
            ClickType.SWAP_OFFHAND,
            ClickType.DROP,
            ClickType.CONTROL_DROP,
            ClickType.CREATIVE,
            ClickType.MIDDLE -> true

            else -> false
        }
    }

    private fun isBlockedInventoryAction(action: InventoryAction): Boolean {
        return when (action) {
            InventoryAction.HOTBAR_MOVE_AND_READD,
            InventoryAction.HOTBAR_SWAP,
            InventoryAction.COLLECT_TO_CURSOR,
            InventoryAction.CLONE_STACK -> true

            else -> false
        }
    }

    private fun handleShiftClick(
        event: InventoryClickEvent,
        player: Player,
        top: Inventory,
        clickedTop: Boolean
    ) {
        val evaluation = evaluate(player, top)
        if (clickedTop) {
            val rawSlot = event.rawSlot
            if (rawSlot != ArenaEnchantPedestalLayout.TOOL_SLOT && rawSlot !in ArenaEnchantPedestalLayout.CATALYST_SLOTS) {
                event.isCancelled = true
                return
            }
            if (isInputPlaceholder(top.getItem(rawSlot))) {
                event.isCancelled = true
                return
            }

            val wasEmpty = getInputItem(top, rawSlot).isNullOrAir()
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val nowEmpty = getInputItem(top, rawSlot).isNullOrAir()
                processInputStateChange(player, top, wasEmpty, nowEmpty)
            })
            return
        }

        val moving = event.currentItem?.takeUnless { it.type.isAir } ?: return
        val targetSlot = resolveInputSlotForItem(moving, top, evaluation)
        if (targetSlot == null || !isInputSlotAvailableForInsert(top, targetSlot)) {
            event.isCancelled = true
            return
        }

        event.isCancelled = true
        val source = event.clickedInventory ?: return
        val placed = moving.clone().apply { amount = 1 }
        top.setItem(targetSlot, placed)
        if (moving.amount <= 1) {
            source.setItem(event.slot, null)
        } else {
            moving.amount -= 1
            source.setItem(event.slot, moving)
        }
        playSound(player, "minecraft:block.enchantment_table.use", 1.7f)
        playSound(player, "minecraft:block.end_portal_frame.fill", 0.75f)
        animatePanelTransition(player, top)
    }

    private fun isTopSlotOperationAllowed(event: InventoryClickEvent, topSlot: Int): Boolean {
        val player = event.whoClicked as? Player ?: return false
        val top = event.view.topInventory
        val evaluation = evaluate(player, top)
        val activeCatalystSlots = resolveActiveCatalystSlots(evaluation)
        val isToolSlot = topSlot == ArenaEnchantPedestalLayout.TOOL_SLOT
        val isAnyCatalystSlot = topSlot in ArenaEnchantPedestalLayout.CATALYST_SLOTS
        if (!isToolSlot && !isAnyCatalystSlot) {
            return false
        }

        val cursor = event.cursor
        val action = event.action
        val placing = when (action) {
            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_ONE,
            InventoryAction.PLACE_SOME,
            InventoryAction.SWAP_WITH_CURSOR -> true

            else -> false
        }
        if (!placing) {
            return getInputItem(top, topSlot) != null
        }
        val isActiveCatalystSlot = topSlot in activeCatalystSlots
        val placeItem = cursor.takeUnless { it.type.isAir } ?: return false
        return when (topSlot) {
            ArenaEnchantPedestalLayout.TOOL_SLOT -> isToolCandidateItem(placeItem)
            in ArenaEnchantPedestalLayout.CATALYST_SLOTS -> isActiveCatalystSlot && canInsertCatalystIntoSlot(placeItem, top, topSlot)
            else -> false
        }
    }

    private fun resolveInputSlotForItem(item: ItemStack, inventory: Inventory, evaluation: PedestalEvaluation): Int? {
        return when {
            isCatalystItem(item) -> {
                val activeCatalystSlots = resolveActiveCatalystSlots(evaluation)
                activeCatalystSlots.firstOrNull { slot ->
                    isInputSlotAvailableForInsert(inventory, slot) && canInsertCatalystIntoSlot(item, inventory, slot)
                }
            }
            isToolCandidateItem(item) -> ArenaEnchantPedestalLayout.TOOL_SLOT
            else -> null
        }
    }

    private fun processInputStateChange(
        player: Player,
        inventory: Inventory,
        wasEmpty: Boolean,
        nowEmpty: Boolean
    ) {
        if (wasEmpty == nowEmpty) {
            renderStatic(player, inventory)
            return
        }
        if (nowEmpty) {
            playSound(player, "minecraft:block.enchantment_table.use", 0.85f)
            playSound(player, "minecraft:block.end_portal_frame.fill", 0.5f)
            animatePanelTransition(player, inventory, reversed = true)
        } else {
            playSound(player, "minecraft:block.enchantment_table.use", 1.7f)
            playSound(player, "minecraft:block.end_portal_frame.fill", 0.75f)
            animatePanelTransition(player, inventory, reversed = false)
        }
    }

    private fun handleExecuteClick(player: Player, inventory: Inventory, runtime: ViewerRuntime) {
        val evaluation = evaluate(player, inventory)
        if (!evaluation.executable || evaluation.requiredLevel == null || evaluation.preparedCatalysts.isEmpty()) {
            renderStatic(player, inventory)
            return
        }

        runtime.panelAnimationTask?.cancel()
        runtime.isForging = true
        playSound(player, "minecraft:block.end_portal_frame.fill", 2.0f)
        playSound(player, "minecraft:item.trident.thunder", 1.5f)

        var stepIndex = 0

        runtime.forgeAnimationTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline || player.openInventory.topInventory != inventory) {
                runtime.forgeAnimationTask?.cancel()
                runtime.forgeAnimationTask = null
                runtime.isForging = false
                return@Runnable
            }

            if (stepIndex < ArenaEnchantPedestalLayout.REVEAL_STEPS.size) {
                ArenaEnchantPedestalLayout.REVEAL_STEPS[stepIndex].forEach { slot ->
                    inventory.setItem(slot, buildForgeProgressItem(player))
                }
                stepIndex += 1
                return@Runnable
            }

            runtime.forgeAnimationTask?.cancel()
            runtime.forgeAnimationTask = null
            runCatching {
                executeForge(player, inventory)
            }
            playSound(player, "minecraft:entity.lightning_bolt.thunder", 2.0f)
            playSound(player, "minecraft:entity.breeze.hurt", 0.5f)
            runtime.isForging = false
            renderStatic(player, inventory)
        }, 0L, 10L)
    }

    private fun executeForge(player: Player, inventory: Inventory) {
        val toolItem = getInputItem(inventory, ArenaEnchantPedestalLayout.TOOL_SLOT) ?: return

        val eval = evaluate(player, inventory)
        val requiredLevel = eval.requiredLevel ?: return
        val prepared = eval.preparedCatalysts
        if (!eval.executable || prepared.isEmpty()) {
            return
        }
        if (player.level < requiredLevel) {
            return
        }

        val workingTool = toolItem.clone()
        var state = getOverEnchantState(workingTool)
        prepared.sortedBy { it.slot }.forEach { preparedCatalyst ->
            val catalyst = preparedCatalyst.catalyst
            val targetEnchant = resolveEnchantment(catalyst.targetEnchantmentId) ?: return@forEach
            val resultingLevel = when (catalyst.mode) {
                ArenaOverEnchanterMode.LIMIT_BREAKING -> {
                    val overLevel = catalyst.overLevel ?: return@forEach
                    targetEnchant.maxLevel + overLevel
                }

                ArenaOverEnchanterMode.OVER_STACKING,
                ArenaOverEnchanterMode.EXOTIC_ATTACH -> targetEnchant.maxLevel
            }
            workingTool.addUnsafeEnchantment(targetEnchant, resultingLevel)
            val appliedOverLevel = resolveAppliedOverLevel(catalyst)
            state = applyOverEnchantEntry(state, catalyst.targetEnchantmentId, appliedOverLevel)
            consumeOneCatalyst(inventory, preparedCatalyst.slot)
        }

        setOverEnchantState(workingTool, state)
        applyOverEnchantLore(workingTool, state)

        player.level = (player.level - requiredLevel).coerceAtLeast(0)
        missionServiceProvider()?.recordOverEnchantSuccess(player.uniqueId, prepared.size)
        inventory.setItem(ArenaEnchantPedestalLayout.TOOL_SLOT, workingTool)
    }

    private fun animatePanelTransition(player: Player, inventory: Inventory, reversed: Boolean = false) {
        val holder = inventory.holder as? ArenaEnchantPedestalHolder ?: return
        val runtime = runtimes.getOrPut(holder.ownerId) { ViewerRuntime() }
        runtime.panelAnimationTask?.cancel()
        val evaluation = evaluate(player, inventory)
        val displayState = buildDisplayState(player, inventory, evaluation)
        val allSlots = ArenaEnchantPedestalLayout.REVEAL_STEPS.flatten().toSet()
        val hiddenPane = buildHiddenAnimationPane(player)
        val protectedSlots = resolveProtectedInputSlots(inventory)

        if (!reversed) {
            for (slot in allSlots) {
                if (slot in protectedSlots) continue
                inventory.setItem(slot, hiddenPane)
            }
            val steps = ArenaEnchantPedestalLayout.REVEAL_STEPS
            var stepIndex = 0
            runtime.panelAnimationTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                if (!player.isOnline || player.openInventory.topInventory != inventory) {
                    runtime.panelAnimationTask?.cancel()
                    runtime.panelAnimationTask = null
                    return@Runnable
                }
                if (stepIndex < steps.size) {
                    steps[stepIndex].forEach { slot ->
                        if (slot in protectedSlots) return@forEach
                        displayState[slot]?.let { inventory.setItem(slot, it) }
                    }
                    stepIndex += 1
                    return@Runnable
                }
                runtime.panelAnimationTask?.cancel()
                runtime.panelAnimationTask = null
                applyDisplayState(inventory, displayState)
            }, 0L, 3L)
        } else {
            val steps = ArenaEnchantPedestalLayout.COLLAPSE_STEPS
            var stepIndex = 0
            runtime.panelAnimationTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                if (!player.isOnline || player.openInventory.topInventory != inventory) {
                    runtime.panelAnimationTask?.cancel()
                    runtime.panelAnimationTask = null
                    return@Runnable
                }
                if (stepIndex < steps.size) {
                    steps[stepIndex].forEach { slot ->
                        if (slot in protectedSlots) return@forEach
                        inventory.setItem(slot, hiddenPane)
                    }
                    stepIndex += 1
                    return@Runnable
                }
                runtime.panelAnimationTask?.cancel()
                runtime.panelAnimationTask = null
                applyDisplayState(inventory, displayState)
            }, 0L, 3L)
        }
    }

    private fun renderStatic(player: Player, inventory: Inventory) {
        val evaluation = evaluate(player, inventory)
        val holder = inventory.holder as? ArenaEnchantPedestalHolder
        val runtime = holder?.let { runtimes.getOrPut(it.ownerId) { ViewerRuntime() } }
        if (runtime != null) {
            val wasExecutable = runtime.lastExecutable
            if (!wasExecutable && evaluation.executable) {
                playSound(player, "minecraft:block.trial_spawner.ominous_activate", 0.75f)
                playSound(player, "minecraft:block.beacon.power_select", 0.75f)
            }
            if (wasExecutable && !evaluation.executable) {
                playSound(player, "minecraft:block.beacon.deactivate", 0.8f)
                playSound(player, "minecraft:block.ender_chest.close", 0.8f)
            }
            runtime.lastExecutable = evaluation.executable
        }
        val displayState = buildDisplayState(player, inventory, evaluation)
        applyDisplayState(inventory, displayState)
    }

    private fun buildDisplayState(
        player: Player,
        inventory: Inventory,
        evaluation: PedestalEvaluation
    ): Map<Int, ItemStack> {
        val framePane = buildSimplePane(player, Material.BLACK_STAINED_GLASS_PANE)
        val neutralPane = buildSimplePane(player, Material.GRAY_STAINED_GLASS_PANE)
        val reservedSlots = setOf(
            ArenaEnchantPedestalLayout.TOOL_SLOT,
            ArenaEnchantPedestalLayout.EXP_SLOT,
            ArenaEnchantPedestalLayout.EXECUTE_SLOT,
            ArenaEnchantPedestalLayout.PROGRESS_SLOT,
            ArenaEnchantPedestalLayout.INFO_SLOT
        ) + ArenaEnchantPedestalLayout.CATALYST_SLOTS
        val display = mutableMapOf<Int, ItemStack>()

        for (slot in 0 until ArenaEnchantPedestalLayout.MENU_SIZE) {
            if (slot in reservedSlots) {
                continue
            }
            display[slot] = when {
                slot in 0..8 || slot in 36..44 -> framePane
                slot in ArenaEnchantPedestalLayout.CENTER_WHITE_SLOTS && evaluation.uiState != PedestalUiState.NO_TOOL -> buildSimplePane(player, Material.WHITE_STAINED_GLASS_PANE)
                slot in ArenaEnchantPedestalLayout.ACTIVE_BACKGROUND_SLOTS -> neutralPane
                else -> neutralPane
            }
        }

        if (getInputItem(inventory, ArenaEnchantPedestalLayout.TOOL_SLOT).isNullOrAir()) {
            display[ArenaEnchantPedestalLayout.TOOL_SLOT] = buildInputSlotPlaceholder(player)
        }

        ArenaEnchantPedestalLayout.CATALYST_SLOTS.forEach { slot ->
            if (getInputItem(inventory, slot).isNullOrAir()) {
                display[slot] = buildCatalystPlaceholderForSlot(player, evaluation, slot)
            }
        }

        display[ArenaEnchantPedestalLayout.EXP_SLOT] = if (evaluation.uiState == PedestalUiState.NO_TOOL) {
            buildSimplePane(player, Material.GRAY_STAINED_GLASS_PANE)
        } else {
            buildExpInfoItem(player, evaluation)
        }
        val progress = resolveSlotUnlockProgress(player)
        display[ArenaEnchantPedestalLayout.PROGRESS_SLOT] = buildProgressGaugeItem(player, progress)
        display[ArenaEnchantPedestalLayout.INFO_SLOT] = buildInfoItem(player)
        display[ArenaEnchantPedestalLayout.EXECUTE_SLOT] = buildExecuteItem(player, evaluation)
        return display
    }

    private fun applyDisplayState(inventory: Inventory, displayState: Map<Int, ItemStack>) {
        displayState.forEach { (slot, item) ->
            inventory.setItem(slot, item)
        }
    }

    private fun resolveProtectedInputSlots(inventory: Inventory): Set<Int> {
        val protected = mutableSetOf<Int>()
        if (getInputItem(inventory, ArenaEnchantPedestalLayout.TOOL_SLOT) != null) {
            protected += ArenaEnchantPedestalLayout.TOOL_SLOT
        }
        ArenaEnchantPedestalLayout.CATALYST_SLOTS.forEach { slot ->
            if (getInputItem(inventory, slot) != null) {
                protected += slot
            }
        }
        return protected
    }

    private fun buildExpInfoItem(player: Player, evaluation: PedestalEvaluation): ItemStack {
        val item = ItemStack(Material.BOOKSHELF)
        val meta = item.itemMeta ?: return item
        if (!evaluation.processable) {
            meta.setDisplayName("§c力を感じられません")
            meta.lore = null
        } else if (evaluation.missingLevel != null && evaluation.missingLevel > 0) {
            meta.setDisplayName("§c智力が足りません")
            meta.lore = listOf(
                ArenaI18n.text(
                    player,
                    "arena.ui.pedestal.execute.missing_level",
                    "§8あと §a{value} レベル §8必要です",
                    "value" to evaluation.missingLevel
                )
            )
        } else {
            val consume = evaluation.requiredLevel ?: 0
            meta.setDisplayName("§a智力は十分です")
            meta.lore = listOf(
                ArenaI18n.text(
                    player,
                    "arena.ui.pedestal.execute.consume_level",
                    "§8合成によって §a{value} レベル §8を消費します",
                    "value" to consume
                )
            )
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return item
    }

    private fun buildProgressGaugeItem(player: Player, progress: SlotUnlockProgress): ItemStack {
        val item = ItemStack(Material.END_CRYSTAL)
        val meta = item.itemMeta ?: return item
        val gaugeLine = buildGaugeLine(progress)
        meta.setDisplayName(ArenaI18n.text(player, "arena.ui.pedestal.blank", " "))
        meta.lore = listOf(gaugeLine)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return item
    }

    private fun buildInfoItem(player: Player): ItemStack {
        val item = ItemStack(Material.MAP)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(ArenaI18n.text(player, "arena.ui.pedestal.blank", " "))
        meta.lore = ArenaI18n.stringList(
            player,
            "arena.ui.pedestal.info.dummy",
            listOf(
                "§7ダミーテキスト1",
                "§7ダミーテキスト2",
                "§7ダミーテキスト3"
            )
        )
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return item
    }

    private fun buildExecuteItem(player: Player, evaluation: PedestalEvaluation): ItemStack {
        val item = ItemStack(Material.ENCHANTING_TABLE)
        val meta = item.itemMeta ?: return item
        val executable = evaluation.executable
        if (!evaluation.processable) {
            meta.setDisplayName("§5オーバーエンチャント")
            meta.lore = listOf(
                ArenaI18n.text(
                    player,
                    "arena.ui.pedestal.execute.waiting_lore",
                    "§7条件を満たすと実行できます"
                )
            )
        } else if (evaluation.missingLevel != null && evaluation.missingLevel > 0) {
            meta.setDisplayName("§5オーバーエンチャント")
            meta.lore = listOf(
                ArenaI18n.text(
                    player,
                    "arena.ui.pedestal.execute.missing_level",
                    "§8あと §a{value} レベル §8必要です",
                    "value" to evaluation.missingLevel
                )
            )
        } else {
            meta.setDisplayName("§d力を解放する")
            val consume = evaluation.requiredLevel ?: 0
            meta.lore = listOf(
                ArenaI18n.text(
                    player,
                    "arena.ui.pedestal.execute.consume_level",
                    "§8合成によって §a{value} レベル §8を消費します",
                    "value" to consume
                )
            )
        }
        meta.setEnchantmentGlintOverride(executable)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
        item.itemMeta = meta
        return item
    }

    private fun buildCatalystPlaceholderForSlot(player: Player, evaluation: PedestalEvaluation, slot: Int): ItemStack {
        val index = ArenaEnchantPedestalLayout.CATALYST_SLOTS.indexOf(slot)
        return when (evaluation.uiState) {
            PedestalUiState.NO_TOOL -> buildCatalystSlotPlaceholder(player, PanelColor.GRAY)
            PedestalUiState.TOOL_NO_OVER -> {
                if (index >= 0 && index < evaluation.unlockedSlotCount) {
                    buildCatalystSlotPlaceholder(player, PanelColor.PURPLE)
                } else {
                    buildCatalystSlotPlaceholder(player, PanelColor.RED)
                }
            }
            PedestalUiState.TOOL_HAS_OVER -> {
                if (slot == ArenaEnchantPedestalLayout.REINFORCE_SLOT) {
                    buildCatalystSlotPlaceholder(player, PanelColor.BLUE)
                } else {
                    buildCatalystSlotPlaceholder(player, PanelColor.GRAY)
                }
            }
        }
    }

    private fun buildCatalystSlotPlaceholder(player: Player, color: PanelColor): ItemStack {
        val material = when (color) {
            PanelColor.PURPLE -> Material.PURPLE_STAINED_GLASS_PANE
            PanelColor.BLUE -> Material.LIGHT_BLUE_STAINED_GLASS_PANE
            PanelColor.RED -> Material.RED_STAINED_GLASS_PANE
            else -> Material.GRAY_STAINED_GLASS_PANE
        }
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(ArenaI18n.text(player, "arena.ui.pedestal.blank", " "))
        meta.lore = null
        meta.persistentDataContainer.set(inputPlaceholderKey, PersistentDataType.BYTE, 1)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        applyExplicitTooltipHide(meta)
        item.itemMeta = meta
        return item
    }

    private fun buildInputSlotPlaceholder(player: Player): ItemStack {
        val item = ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(ArenaI18n.text(player, "arena.ui.pedestal.blank", " "))
        meta.persistentDataContainer.set(inputPlaceholderKey, PersistentDataType.BYTE, 1)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        applyExplicitTooltipHide(meta)
        item.itemMeta = meta
        return item
    }

    private fun buildHiddenAnimationPane(player: Player): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(ArenaI18n.text(player, "arena.ui.pedestal.blank", " "))
        meta.persistentDataContainer.set(inputPlaceholderKey, PersistentDataType.BYTE, 1)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        applyExplicitTooltipHide(meta)
        item.itemMeta = meta
        return item
    }

    private fun buildSimplePane(player: Player, material: Material): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(ArenaI18n.text(player, "arena.ui.pedestal.blank", " "))
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        applyExplicitTooltipHide(meta)
        item.itemMeta = meta
        return item
    }

    private fun buildForgeProgressItem(player: Player): ItemStack {
        val item = ItemStack(Material.YELLOW_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(ArenaI18n.text(player, "arena.ui.pedestal.blank", " "))
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        applyExplicitTooltipHide(meta)
        item.itemMeta = meta
        return item
    }

    private fun evaluate(player: Player, inventory: Inventory): PedestalEvaluation {
        val unlockProgress = resolveSlotUnlockProgress(player)
        val toolItem = getInputItem(inventory, ArenaEnchantPedestalLayout.TOOL_SLOT)
        val state = toolItem?.let { getOverEnchantState(it) } ?: OverEnchantState(entries = emptyMap())
        val uiState = when {
            toolItem == null -> PedestalUiState.NO_TOOL
            state.entries.isEmpty() -> PedestalUiState.TOOL_NO_OVER
            else -> PedestalUiState.TOOL_HAS_OVER
        }
        val activeCatalystSlots = resolveActiveCatalystSlots(uiState, unlockProgress.unlockedSlotCount)
        val prepared = mutableListOf<PreparedCatalyst>()
        val usedSlotCount = usedOverEnchantSlotCount(state)
        if (toolItem == null) {
            return PedestalEvaluation(
                catalystReady = false,
                processable = false,
                expReady = false,
                executable = false,
                requiredLevel = null,
                missingLevel = null,
                uiState = uiState,
                unlockedSlotCount = unlockProgress.unlockedSlotCount,
                usedSlotCount = usedSlotCount,
                preparedCatalysts = emptyList()
            )
        }

        val candidates = activeCatalystSlots
            .mapNotNull { slot -> getInputItem(inventory, slot)?.let { slot to it } }
            .sortedBy { it.first }
        if (candidates.isEmpty()) {
            return PedestalEvaluation(
                catalystReady = false,
                processable = false,
                expReady = false,
                executable = false,
                requiredLevel = null,
                missingLevel = null,
                uiState = uiState,
                unlockedSlotCount = unlockProgress.unlockedSlotCount,
                usedSlotCount = usedSlotCount,
                preparedCatalysts = emptyList()
            )
        }

        val seenIds = mutableSetOf<String>()
        var workingTool = toolItem.clone()
        var workingState = state
        var totalRequired = 0
        for ((slot, item) in candidates) {
            val catalyst = ArenaOverEnchanterCatalystData.read(item)
                ?: return PedestalEvaluation(
                    catalystReady = true,
                    processable = false,
                    expReady = false,
                    executable = false,
                    requiredLevel = null,
                    missingLevel = null,
                    uiState = uiState,
                    unlockedSlotCount = unlockProgress.unlockedSlotCount,
                    usedSlotCount = usedSlotCount,
                    preparedCatalysts = emptyList()
                )
            val normalizedId = catalyst.targetEnchantmentId.trim().lowercase(Locale.ROOT)
            if (!seenIds.add(normalizedId)) {
                return PedestalEvaluation(
                    catalystReady = true,
                    processable = false,
                    expReady = false,
                    executable = false,
                    requiredLevel = null,
                    missingLevel = null,
                    uiState = uiState,
                    unlockedSlotCount = unlockProgress.unlockedSlotCount,
                    usedSlotCount = usedSlotCount,
                    preparedCatalysts = emptyList()
                )
            }
            val required = resolveRequiredLevel(catalyst)
                ?: return PedestalEvaluation(
                    catalystReady = true,
                    processable = false,
                    expReady = false,
                    executable = false,
                    requiredLevel = null,
                    missingLevel = null,
                    uiState = uiState,
                    unlockedSlotCount = unlockProgress.unlockedSlotCount,
                    usedSlotCount = usedSlotCount,
                    preparedCatalysts = emptyList()
                )

            if (!validateRouteCompatibility(workingState, catalyst, uiState, unlockProgress.unlockedSlotCount)) {
                return PedestalEvaluation(
                    catalystReady = true,
                    processable = false,
                    expReady = false,
                    executable = false,
                    requiredLevel = null,
                    missingLevel = null,
                    uiState = uiState,
                    unlockedSlotCount = unlockProgress.unlockedSlotCount,
                    usedSlotCount = usedSlotCount,
                    preparedCatalysts = emptyList()
                )
            }
            if (!validateToolByMode(workingTool, workingState, catalyst, uiState)) {
                return PedestalEvaluation(
                    catalystReady = true,
                    processable = false,
                    expReady = false,
                    executable = false,
                    requiredLevel = null,
                    missingLevel = null,
                    uiState = uiState,
                    unlockedSlotCount = unlockProgress.unlockedSlotCount,
                    usedSlotCount = usedSlotCount,
                    preparedCatalysts = emptyList()
                )
            }

            prepared += PreparedCatalyst(slot = slot, item = item, catalyst = catalyst, requiredLevel = required)
            totalRequired += required

            val simulated = simulateApply(workingTool, workingState, catalyst)
            workingTool = simulated.first
            workingState = simulated.second
        }

        val expReady = player.level >= totalRequired
        val missingLevel = if (expReady) null else (totalRequired - player.level)
        return PedestalEvaluation(
            catalystReady = true,
            processable = true,
            expReady = expReady,
            executable = expReady,
            requiredLevel = totalRequired,
            missingLevel = missingLevel,
            uiState = uiState,
            unlockedSlotCount = unlockProgress.unlockedSlotCount,
            usedSlotCount = usedSlotCount,
            preparedCatalysts = prepared
        )
    }

    private fun validateRouteCompatibility(
        state: OverEnchantState,
        catalyst: ArenaOverEnchanterCatalystData.Catalyst,
        uiState: PedestalUiState,
        unlockedSlotCount: Int
    ): Boolean {
        val targetId = catalyst.targetEnchantmentId.trim().lowercase(Locale.ROOT)
        return when (uiState) {
            PedestalUiState.NO_TOOL -> false
            PedestalUiState.TOOL_NO_OVER -> {
                val targetAlreadyExists = state.entries.containsKey(targetId)
                targetAlreadyExists || state.entries.size < unlockedSlotCount
            }

            PedestalUiState.TOOL_HAS_OVER -> state.entries.containsKey(targetId)
        }
    }

    private fun validateToolByMode(
        tool: ItemStack,
        state: OverEnchantState,
        catalyst: ArenaOverEnchanterCatalystData.Catalyst,
        uiState: PedestalUiState
    ): Boolean {
        val targetEnchant = resolveEnchantment(catalyst.targetEnchantmentId) ?: return false
        val currentTargetLevel = tool.getEnchantmentLevel(targetEnchant)
        val targetId = catalyst.targetEnchantmentId.trim().lowercase(Locale.ROOT)
        val currentOverLevel = state.entries[targetId] ?: 0

        return when (catalyst.mode) {
            ArenaOverEnchanterMode.LIMIT_BREAKING -> {
                val overLevel = catalyst.overLevel ?: return false
                if (!targetEnchant.canEnchantItem(tool)) {
                    return false
                }
                if (uiState == PedestalUiState.TOOL_HAS_OVER && !state.entries.containsKey(targetId)) {
                    return false
                }
                val expectedCurrentOver = overLevel - 1
                if (expectedCurrentOver > 0 && currentOverLevel != expectedCurrentOver) {
                    return false
                }
                if (expectedCurrentOver <= 0 && currentOverLevel > 0) {
                    return false
                }
                val requiredCurrent = targetEnchant.maxLevel + overLevel - 1
                currentTargetLevel == requiredCurrent
            }

            ArenaOverEnchanterMode.OVER_STACKING -> {
                if (currentTargetLevel > 0) {
                    return false
                }
                validateOverStackingBase(tool, catalyst.targetEnchantmentId)
            }

            ArenaOverEnchanterMode.EXOTIC_ATTACH -> {
                if (currentTargetLevel > 0) {
                    return false
                }
                validateExoticAttach(tool, catalyst.targetEnchantmentId)
            }
        }
    }

    private fun simulateApply(
        tool: ItemStack,
        state: OverEnchantState,
        catalyst: ArenaOverEnchanterCatalystData.Catalyst
    ): Pair<ItemStack, OverEnchantState> {
        val targetEnchant = resolveEnchantment(catalyst.targetEnchantmentId) ?: return tool to state
        val resultingLevel = when (catalyst.mode) {
            ArenaOverEnchanterMode.LIMIT_BREAKING -> {
                val overLevel = catalyst.overLevel ?: return tool to state
                targetEnchant.maxLevel + overLevel
            }

            ArenaOverEnchanterMode.OVER_STACKING,
            ArenaOverEnchanterMode.EXOTIC_ATTACH -> targetEnchant.maxLevel
        }
        val cloned = tool.clone()
        cloned.addUnsafeEnchantment(targetEnchant, resultingLevel)
        val appliedOverLevel = resolveAppliedOverLevel(catalyst)
        val nextState = applyOverEnchantEntry(state, catalyst.targetEnchantmentId, appliedOverLevel)
        return cloned to nextState
    }

    private fun validateOverStackingBase(tool: ItemStack, targetEnchantmentId: String): Boolean {
        return when (targetEnchantmentId) {
            "infinity" -> isBow(tool.type) && hasEnchantment(tool, "mending")
            "mending" -> isBow(tool.type) && hasEnchantment(tool, "infinity")
            "multishot" -> isCrossbow(tool.type) && hasEnchantment(tool, "piercing")
            "piercing" -> isCrossbow(tool.type) && hasEnchantment(tool, "multishot")
            "sharpness", "smite", "bane_of_arthropods" -> {
                isSwordOrAxe(tool.type) && DAMAGE_IDS.any { it != targetEnchantmentId && hasEnchantment(tool, it) }
            }

            "protection", "fire_protection", "blast_protection", "projectile_protection" -> {
                isArmor(tool.type) && PROTECTION_IDS.any { it != targetEnchantmentId && hasEnchantment(tool, it) }
            }

            else -> false
        }
    }

    private fun validateExoticAttach(tool: ItemStack, targetEnchantmentId: String): Boolean {
        return when (targetEnchantmentId) {
            "infinity", "sharpness" -> isCrossbow(tool.type)
            "breach" -> isSwordOrAxe(tool.type)
            else -> false
        }
    }

    private fun resolveRequiredLevel(catalyst: ArenaOverEnchanterCatalystData.Catalyst): Int? {
        val path = when (catalyst.mode) {
            ArenaOverEnchanterMode.LIMIT_BREAKING -> {
                val level = catalyst.overLevel ?: return null
                "arena.over_enchanter.limit_breaking.${catalyst.targetEnchantmentId}.$level"
            }

            ArenaOverEnchanterMode.OVER_STACKING -> "arena.over_enchanter.over_stacking.${catalyst.targetEnchantmentId}"
            ArenaOverEnchanterMode.EXOTIC_ATTACH -> "arena.over_enchanter.exotic_attach.${catalyst.targetEnchantmentId}"
        }
        val value = coreConfigProvider().getInt(path, -1)
        return value.takeIf { it > 0 }
    }

    private fun resolveEnchantment(id: String): Enchantment? {
        val normalized = id.trim().lowercase(Locale.ROOT)
        return Enchantment.getByKey(NamespacedKey.minecraft(normalized))
    }

    private fun hasEnchantment(item: ItemStack, enchantmentId: String): Boolean {
        val enchantment = resolveEnchantment(enchantmentId) ?: return false
        return item.getEnchantmentLevel(enchantment) > 0
    }

    private fun isCatalystItem(item: ItemStack): Boolean {
        return ArenaOverEnchanterCatalystData.read(item) != null
    }

    private fun isToolCandidateItem(item: ItemStack): Boolean {
        val material = item.type
        return isBow(material) || isCrossbow(material) || isSwordOrAxe(material) || isArmor(material)
    }

    private fun isBow(material: Material): Boolean = material == Material.BOW

    private fun isCrossbow(material: Material): Boolean = material == Material.CROSSBOW

    private fun isSwordOrAxe(material: Material): Boolean {
        return material.name.endsWith("_SWORD") || material.name.endsWith("_AXE")
    }

    private fun isArmor(material: Material): Boolean {
        val name = material.name
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
    }

    private fun resolveAppliedOverLevel(catalyst: ArenaOverEnchanterCatalystData.Catalyst): Int {
        return when (catalyst.mode) {
            ArenaOverEnchanterMode.LIMIT_BREAKING -> catalyst.overLevel ?: 0
            ArenaOverEnchanterMode.OVER_STACKING,
            ArenaOverEnchanterMode.EXOTIC_ATTACH -> 1
        }
    }

    private fun applyOverEnchantEntry(
        state: OverEnchantState,
        enchantmentId: String,
        overLevel: Int
    ): OverEnchantState {
        val normalizedId = enchantmentId.trim().lowercase(Locale.ROOT)
        val nextEntries = state.entries.toMutableMap()
        nextEntries[normalizedId] = overLevel.coerceAtLeast(1)
        return OverEnchantState(entries = nextEntries)
    }

    private fun getOverEnchantState(item: ItemStack): OverEnchantState {
        val meta = item.itemMeta ?: return OverEnchantState(entries = emptyMap())
        val pdc = meta.persistentDataContainer
        val rawEntries = pdc.get(overEnchantEntriesKey, PersistentDataType.STRING).orEmpty()
        val entries = parseOverEnchantEntries(rawEntries)
        return OverEnchantState(entries = entries)
    }

    private fun setOverEnchantState(item: ItemStack, state: OverEnchantState) {
        val meta = item.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        val serialized = serializeOverEnchantEntries(state.entries)
        if (serialized.isBlank()) {
            pdc.remove(overEnchantEntriesKey)
        } else {
            pdc.set(overEnchantEntriesKey, PersistentDataType.STRING, serialized)
        }
        item.itemMeta = meta
    }

    private fun parseOverEnchantEntries(raw: String): Map<String, Int> {
        if (raw.isBlank()) {
            return emptyMap()
        }
        return buildMap {
            raw.split('|').forEach { token ->
                val parts = token.split(':', limit = 2)
                if (parts.size != 2) {
                    return@forEach
                }
                val id = parts[0].trim().lowercase(Locale.ROOT)
                val level = parts[1].trim().toIntOrNull() ?: return@forEach
                if (id.isBlank() || level <= 0) {
                    return@forEach
                }
                put(id, level)
            }
        }
    }

    private fun serializeOverEnchantEntries(entries: Map<String, Int>): String {
        if (entries.isEmpty()) {
            return ""
        }
        return entries.entries
            .sortedBy { it.key }
            .joinToString("|") { "${it.key}:${it.value.coerceAtLeast(1)}" }
    }

    private fun usedOverEnchantSlotCount(state: OverEnchantState): Int {
        return state.entries.size.coerceAtLeast(0)
    }

    private fun orderedOverEnchantEntries(state: OverEnchantState): List<Pair<String, Int>> {
        return state.entries.entries
            .sortedBy { it.key }
            .map { it.key to it.value }
    }

    private fun buildOverEnchantToken(enchantmentId: String, overLevel: Int): String {
        val symbol = ENCHANTMENT_SYMBOLS[enchantmentId.trim().lowercase(Locale.ROOT)] ?: "?"
        val color = when (overLevel) {
            1 -> "§6"
            2 -> "§d"
            3 -> "§b"
            else -> "§f"
        }
        return "$color〚$symbol〛"
    }

    private fun applyOverEnchantLore(item: ItemStack, state: OverEnchantState) {
        val meta = item.itemMeta ?: return
        val ordered = orderedOverEnchantEntries(state)
        val line = ordered.joinToString(" ") { (id, level) ->
            buildOverEnchantToken(id, level)
        }

        val existing = meta.lore?.toMutableList() ?: mutableListOf()
        val managed = meta.persistentDataContainer.get(overEnchantLoreManagedKey, PersistentDataType.BYTE)?.toInt() == 1

        if (line.isBlank()) {
            if (managed && existing.isNotEmpty()) {
                existing.removeAt(0)
            }
            if (existing.isEmpty()) {
                meta.lore = null
            } else {
                meta.lore = existing
            }
            meta.persistentDataContainer.remove(overEnchantLoreManagedKey)
            item.itemMeta = meta
            return
        }

        if (managed && existing.isNotEmpty()) {
            existing[0] = line
        } else {
            existing.add(0, line)
        }
        meta.lore = existing
        meta.persistentDataContainer.set(overEnchantLoreManagedKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
    }

    private fun resolveSlotUnlockProgress(player: Player): SlotUnlockProgress {
        val successCount = missionServiceProvider()?.getOverEnchantSuccessCount(player.uniqueId) ?: 0
        val thresholds = resolveSlotUnlockThresholds()
        val unlockedSlotCount = thresholds.entries
            .filter { successCount >= it.value }
            .maxOfOrNull { it.key }
            ?.coerceIn(1, 3)
            ?: 1
        val currentThreshold = thresholds[unlockedSlotCount] ?: 0
        val nextThreshold = thresholds[unlockedSlotCount + 1]
        return SlotUnlockProgress(
            successCount = successCount.coerceAtLeast(0),
            unlockedSlotCount = unlockedSlotCount,
            currentThreshold = currentThreshold,
            nextThreshold = nextThreshold
        )
    }

    private fun resolveSlotUnlockThresholds(): Map<Int, Int> {
        val section = coreConfigProvider().getConfigurationSection("arena.over_enchanter.slot_unlocks")
        if (section == null) {
            return DEFAULT_SLOT_UNLOCKS
        }
        val parsed = mutableMapOf<Int, Int>()
        section.getKeys(false).forEach { rawKey ->
            val slot = rawKey.toIntOrNull() ?: return@forEach
            if (slot !in 1..3) {
                return@forEach
            }
            val threshold = section.getInt(rawKey, -1)
            if (threshold < 0) {
                return@forEach
            }
            parsed[slot] = threshold
        }
        if (parsed[1] == null) {
            parsed[1] = 0
        }
        if (parsed[2] == null) {
            parsed[2] = DEFAULT_SLOT_UNLOCKS[2] ?: 30
        }
        if (parsed[3] == null) {
            parsed[3] = DEFAULT_SLOT_UNLOCKS[3] ?: 100
        }
        return parsed.toSortedMap()
    }

    private fun buildGaugeLine(progress: SlotUnlockProgress): String {
        val totalBars = 30
        val next = progress.nextThreshold
        if (next == null) {
            return "§a" + "❚".repeat(totalBars)
        }
        val span = (next - progress.currentThreshold).coerceAtLeast(1)
        val currentInSpan = (progress.successCount - progress.currentThreshold).coerceIn(0, span)
        val fillRatio = currentInSpan.toDouble() / span.toDouble()
        val filled = (fillRatio * totalBars).toInt().coerceIn(0, totalBars)
        val empty = (totalBars - filled).coerceAtLeast(0)
        return "§a" + "❚".repeat(filled) + "§7" + "❚".repeat(empty)
    }

    private fun returnItemToPlayer(player: Player, inventory: Inventory, slot: Int) {
        val item = getInputItem(inventory, slot) ?: return
        inventory.setItem(slot, null)
        val leftovers = player.inventory.addItem(item)
        leftovers.values.forEach { leftover ->
            player.world.dropItemNaturally(player.location, leftover)
        }
    }

    private fun consumeOneCatalyst(inventory: Inventory, slot: Int) {
        val item = getInputItem(inventory, slot) ?: return
        if (item.amount > 1) {
            item.amount -= 1
        } else {
            inventory.setItem(slot, null)
        }
    }

    private fun resolveActiveCatalystSlots(evaluation: PedestalEvaluation): List<Int> {
        return resolveActiveCatalystSlots(evaluation.uiState, evaluation.unlockedSlotCount)
    }

    private fun resolveActiveCatalystSlots(uiState: PedestalUiState, unlockedSlotCount: Int): List<Int> {
        return when (uiState) {
            PedestalUiState.NO_TOOL -> emptyList()
            PedestalUiState.TOOL_NO_OVER -> ArenaEnchantPedestalLayout.CATALYST_SLOTS.take(unlockedSlotCount.coerceIn(1, 3))
            PedestalUiState.TOOL_HAS_OVER -> listOf(ArenaEnchantPedestalLayout.REINFORCE_SLOT)
        }
    }

    private fun canInsertCatalystIntoSlot(item: ItemStack, inventory: Inventory, slot: Int): Boolean {
        val incoming = ArenaOverEnchanterCatalystData.read(item) ?: return false
        val incomingId = incoming.targetEnchantmentId.trim().lowercase(Locale.ROOT)
        val player = inventory.viewers.firstOrNull() as? Player
        val evaluation = player?.let { evaluate(it, inventory) }
        if (evaluation != null && slot !in resolveActiveCatalystSlots(evaluation)) {
            return false
        }
        if (evaluation?.uiState == PedestalUiState.TOOL_HAS_OVER) {
            val tool = getInputItem(inventory, ArenaEnchantPedestalLayout.TOOL_SLOT) ?: return false
            val state = getOverEnchantState(tool)
            if (!state.entries.containsKey(incomingId)) {
                return false
            }
        }

        val activeSlots = (evaluation?.let { resolveActiveCatalystSlots(it) } ?: ArenaEnchantPedestalLayout.CATALYST_SLOTS).filter { s ->
            if (s == slot) {
                return@filter true
            }
            val existing = getInputItem(inventory, s) ?: return@filter true
            val existingCatalyst = ArenaOverEnchanterCatalystData.read(existing) ?: return@filter true
            existingCatalyst.targetEnchantmentId.trim().lowercase(Locale.ROOT) != incomingId
        }
        return activeSlots.size == (evaluation?.let { resolveActiveCatalystSlots(it).size } ?: ArenaEnchantPedestalLayout.CATALYST_SLOTS.size)
    }

    private fun isInputSlotAvailableForInsert(inventory: Inventory, slot: Int): Boolean {
        val existing = inventory.getItem(slot)
        return existing.isNullOrAir() || isInputPlaceholder(existing)
    }

    private fun getInputItem(inventory: Inventory, slot: Int): ItemStack? {
        val raw = inventory.getItem(slot) ?: return null
        if (raw.type.isAir || isInputPlaceholder(raw)) {
            return null
        }
        return raw
    }

    private fun isInputPlaceholder(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) {
            return false
        }
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.get(inputPlaceholderKey, PersistentDataType.BYTE)?.toInt() == 1
    }

    private fun placeFromCursorIntoInputSlot(event: InventoryClickEvent, inventory: Inventory, slot: Int) {
        val cursor = event.cursor.clone().takeUnless { it.type.isAir } ?: return
        val insertAmount = 1
        val placed = cursor.clone().apply { amount = insertAmount }
        inventory.setItem(slot, placed)

        val remaining = cursor.amount - insertAmount
        if (remaining <= 0) {
            event.whoClicked.setItemOnCursor(null)
        } else {
            cursor.amount = remaining
            event.whoClicked.setItemOnCursor(cursor)
        }
    }

    private fun ItemStack?.isNullOrAir(): Boolean {
        return this == null || this.type.isAir
    }

    private fun playSound(player: Player, key: String, pitch: Float) {
        player.playSound(player.location, key, SoundCategory.PLAYERS, 1.0f, pitch)
    }

    private fun applyExplicitTooltipHide(meta: ItemMeta) {
        runCatching {
            meta.setHideTooltip(true)
        }
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        runCatching {
            val method = meta.javaClass.methods.firstOrNull {
                it.name == "setHideTooltip" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType
            } ?: return
            method.invoke(meta, true)
        }
    }
}
