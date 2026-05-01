package jp.awabi2048.cccontent.features.arena

import jp.awabi2048.cccontent.items.arena.ArenaEnchantShardData
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
    val LEFT_PATH_STEPS: List<List<Int>> = listOf(
        listOf(18),
        listOf(20),
        listOf(12, 21, 30),
        listOf(13)
    )
    val RIGHT_PATH_STEPS: List<List<Int>> = listOf(
        listOf(26),
        listOf(24, 25),
        listOf(14, 23, 32),
        listOf(31)
    )
    val GLINT_PATH_STEPS: List<List<Int>> = listOf(
        listOf(18, 26),
        listOf(20, 24, 25),
        listOf(12, 14, 21, 23, 30, 32),
        listOf(13, 31)
    )
    val FORGE_STEPS: List<List<Int>> = listOf(
        listOf(18, 26),
        listOf(20, 24),
        listOf(12, 14, 21, 23, 30, 32),
        listOf(13, 31)
    )
    val LEFT_PATH_SLOTS: Set<Int> = LEFT_PATH_STEPS.flatten().toSet()
    val RIGHT_PATH_SLOTS: Set<Int> = RIGHT_PATH_STEPS.flatten().toSet()
    val GLINT_PATH_SLOTS: Set<Int> = GLINT_PATH_STEPS.flatten().toSet()
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

private enum class PanelAnimationKind {
    FULL,
    LEFT,
    RIGHT,
    LEFT_RIGHT,
    GLINT
}

private enum class PedestalUiState {
    NO_TOOL,
    TOOL_NO_OVER,
    TOOL_HAS_OVER
}

private data class PreparedCatalyst(
    val slot: Int,
    val item: ItemStack,
    val catalyst: ArenaEnchantShardData.Shard,
    val requiredLevel: Int
)

private data class PedestalEvaluation(
    val catalystReady: Boolean,
    val processable: Boolean,
    val expReady: Boolean,
    val executable: Boolean,
    val powerInsufficient: Boolean = false,
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

private data class InputSnapshot(
    val hasTool: Boolean,
    val hasAnyInput: Boolean,
    val shardCount: Int,
    val expReady: Boolean,
    val executable: Boolean
)

private data class PathAnimationRequest(
    val kind: PanelAnimationKind,
    val reveal: Boolean,
    val startProgress: Int? = null
)

private data class ViewerRuntime(
    var panelAnimationTask: BukkitTask? = null,
    var forgeAnimationTask: BukkitTask? = null,
    var isForging: Boolean = false,
    var lastExecutable: Boolean = false,
    var isPanelAnimating: Boolean = false,
    var activeAnimationKind: PanelAnimationKind? = null,
    var activeAnimationProgress: Int = 0,
    var activeAnimationDirection: Int = 1,
    var leftRightStartLeft: Int = 0,
    var leftRightStartRight: Int = 0,
    var leftPathLevel: Int = 0,
    var rightPathLevel: Int = 0,
    var glintPathLevel: Int = 0,
    var frozenRemovalDisplayState: Map<Int, ItemStack>? = null,
    var returnCatalystsBeforeFullCollapse: Boolean = false,
    val pendingPathAnimations: MutableList<PathAnimationRequest> = mutableListOf()
)

class ArenaEnchantPedestalMenu(
    private val plugin: JavaPlugin,
    private val coreConfigProvider: () -> FileConfiguration,
    private val missionServiceProvider: () -> ArenaMissionService?,
    private val arenaManagerProvider: () -> ArenaManager? = { null }
) : Listener {
    private companion object {
        val PROTECTION_IDS: Set<String> = setOf("protection", "fire_protection", "blast_protection", "projectile_protection")
        val DAMAGE_IDS: Set<String> = setOf("sharpness", "smite", "bane_of_arthropods")
        val DEFAULT_SLOT_UNLOCKS: Map<Int, Int> = mapOf(1 to 0, 2 to 30, 3 to 100)
        const val FULL_ANIMATION_PERIOD_TICKS: Long = 2L
        const val PATH_ANIMATION_PERIOD_TICKS: Long = 2L
        const val GLINT_ANIMATION_PERIOD_TICKS: Long = 1L
        const val FORGE_ANIMATION_PERIOD_TICKS: Long = 8L
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
    private val auditLogger = ArenaAuditLogger(plugin)

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
        val top = event.view.topInventory
        val clickedTop = event.clickedInventory == top
        if (runtime.isPanelAnimating && !isAllowedDuringPanelAnimation(event, top, clickedTop)) {
            event.isCancelled = true
            return
        }

        if (isBlockedClickType(event)) {
            event.isCancelled = true
            return
        }

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

        captureRemovalDisplayStateIfNeeded(event, player, top, runtime, rawSlot)

        if (handleReverseOnToolRemovalDuringAnimation(event, player, top, runtime, rawSlot)) {
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
            val before = captureSnapshot(player, top)
            event.isCancelled = true
            placeFromCursorIntoInputSlot(event, top, rawSlot)
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val after = captureSnapshot(player, top)
                processInputStateChange(player, top, before, after)
            })
            return
        }

        if (!isTopSlotOperationAllowed(event, rawSlot)) {
            event.isCancelled = true
            return
        }

        val before = captureSnapshot(player, top)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val after = captureSnapshot(player, top)
            processInputStateChange(player, top, before, after)
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
        runtime?.frozenRemovalDisplayState = null
        runtime?.returnCatalystsBeforeFullCollapse = false

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
        val holder = top.holder as? ArenaEnchantPedestalHolder
        val runtime = holder?.let { runtimes.getOrPut(it.ownerId) { ViewerRuntime() } }
        if (clickedTop) {
            val rawSlot = event.rawSlot
            if (rawSlot != ArenaEnchantPedestalLayout.TOOL_SLOT && rawSlot !in ArenaEnchantPedestalLayout.CATALYST_SLOTS) {
                event.isCancelled = true
                return
            }
            if (runtime != null) {
                captureRemovalDisplayStateIfNeeded(event, player, top, runtime, rawSlot)
            }
            if (runtime != null && handleReverseOnToolRemovalDuringAnimation(event, player, top, runtime, rawSlot)) {
                return
            }
            if (isInputPlaceholder(top.getItem(rawSlot))) {
                event.isCancelled = true
                return
            }

            val before = captureSnapshot(player, top)
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val after = captureSnapshot(player, top)
                processInputStateChange(player, top, before, after)
            })
            return
        }

        if (runtime?.isPanelAnimating == true) {
            event.isCancelled = true
            return
        }

        val moving = event.currentItem?.takeUnless { it.type.isAir } ?: return
        val targetSlot = resolveInputSlotForItem(moving, top, evaluation)
        if (targetSlot == null || !isInputSlotAvailableForInsert(top, targetSlot)) {
            event.isCancelled = true
            return
        }

        val before = captureSnapshot(player, top)
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
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val after = captureSnapshot(player, top)
            processInputStateChange(player, top, before, after)
        })
    }

    private fun isTopSlotOperationAllowed(event: InventoryClickEvent, topSlot: Int): Boolean {
        val player = event.whoClicked as? Player ?: return false
        val top = event.view.topInventory
        val holder = top.holder as? ArenaEnchantPedestalHolder
        val runtime = holder?.let { runtimes.getOrPut(it.ownerId) { ViewerRuntime() } }
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
        if (runtime?.isPanelAnimating == true) {
            return false
        }
        val isActiveCatalystSlot = topSlot in activeCatalystSlots
        val placeItem = cursor.takeUnless { it.type.isAir } ?: return false
        return when (topSlot) {
            ArenaEnchantPedestalLayout.TOOL_SLOT -> isToolCandidateItem(placeItem)
            in ArenaEnchantPedestalLayout.CATALYST_SLOTS -> isActiveCatalystSlot && canInsertCatalystIntoSlot(placeItem, top, topSlot)
            else -> false
        }
    }

    private fun handleReverseOnToolRemovalDuringAnimation(
        event: InventoryClickEvent,
        player: Player,
        top: Inventory,
        runtime: ViewerRuntime,
        rawSlot: Int
    ): Boolean {
        if (!runtime.isPanelAnimating) {
            return false
        }
        if (rawSlot != ArenaEnchantPedestalLayout.TOOL_SLOT) {
            return false
        }
        if (isInputPlaceholder(top.getItem(rawSlot))) {
            return false
        }
        val placing = when (event.action) {
            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_ONE,
            InventoryAction.PLACE_SOME,
            InventoryAction.SWAP_WITH_CURSOR -> true

            else -> false
        }
        if (placing) {
            event.isCancelled = true
            return true
        }

        event.isCancelled = false
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (getInputItem(top, ArenaEnchantPedestalLayout.TOOL_SLOT) != null) {
                return@Runnable
            }
            startToolRemovalSequence(player, top, runtime)
        })
        return true
    }

    private fun captureRemovalDisplayStateIfNeeded(
        event: InventoryClickEvent,
        player: Player,
        top: Inventory,
        runtime: ViewerRuntime,
        rawSlot: Int
    ) {
        if (rawSlot != ArenaEnchantPedestalLayout.TOOL_SLOT) {
            return
        }
        val item = top.getItem(rawSlot) ?: return
        if (item.type.isAir || isInputPlaceholder(item)) {
            return
        }
        val placing = when (event.action) {
            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_ONE,
            InventoryAction.PLACE_SOME,
            InventoryAction.SWAP_WITH_CURSOR -> true

            else -> false
        }
        if (placing) {
            return
        }
        runtime.frozenRemovalDisplayState = buildFrozenRemovalDisplayState(player, top, runtime)
    }

    private fun isAllowedDuringPanelAnimation(event: InventoryClickEvent, top: Inventory, clickedTop: Boolean): Boolean {
        if (!clickedTop) {
            return true
        }
        val rawSlot = event.rawSlot
        if (rawSlot != ArenaEnchantPedestalLayout.TOOL_SLOT) {
            return false
        }
        if (isInputPlaceholder(top.getItem(rawSlot))) {
            return false
        }
        val placing = when (event.action) {
            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_ONE,
            InventoryAction.PLACE_SOME,
            InventoryAction.SWAP_WITH_CURSOR -> true

            else -> false
        }
        return !placing
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
        before: InputSnapshot,
        after: InputSnapshot
    ) {
        val holder = inventory.holder as? ArenaEnchantPedestalHolder ?: return
        val runtime = runtimes.getOrPut(holder.ownerId) { ViewerRuntime() }

        if (before.hasTool && !after.hasTool) {
            playSound(player, "minecraft:block.enchantment_table.use", 0.85f)
            playSound(player, "minecraft:block.end_portal_frame.fill", 0.5f)
            startToolRemovalSequence(player, inventory, runtime)
            return
        }

        if (!before.hasAnyInput && after.hasAnyInput) {
            playSound(player, "minecraft:block.enchantment_table.use", 1.7f)
            playSound(player, "minecraft:block.end_portal_frame.fill", 0.75f)
            startAnimation(player, inventory, runtime, PanelAnimationKind.FULL, reveal = true)
            return
        }
        if (before.hasAnyInput && !after.hasAnyInput) {
            playSound(player, "minecraft:block.enchantment_table.use", 0.85f)
            playSound(player, "minecraft:block.end_portal_frame.fill", 0.5f)
            startAnimation(player, inventory, runtime, PanelAnimationKind.FULL, reveal = false)
            return
        }

        if (runtime.isPanelAnimating) {
            renderStatic(player, inventory)
            return
        }

        val queued = mutableListOf<PathAnimationRequest>()
        val leftReveal = before.shardCount == 0 && after.shardCount > 0
        val leftCollapse = before.shardCount > 0 && after.shardCount == 0
        val rightReveal = !before.expReady && after.expReady
        val rightCollapse = before.expReady && !after.expReady
        val glintReveal = !before.executable && after.executable
        val glintCollapse = before.executable && !after.executable

        if (glintCollapse && runtime.glintPathLevel > 0) {
            playSound(player, "minecraft:block.beacon.deactivate", 0.8f)
            playSound(player, "minecraft:block.ender_chest.close", 0.8f)
            queued += PathAnimationRequest(
                kind = PanelAnimationKind.GLINT,
                reveal = false,
                startProgress = runtime.glintPathLevel
            )
        }

        when {
            leftReveal && rightReveal -> {
                queued += PathAnimationRequest(PanelAnimationKind.LEFT_RIGHT, reveal = true)
            }

            leftCollapse && rightCollapse -> {
                val start = maxOf(runtime.leftPathLevel, runtime.rightPathLevel)
                if (start > 0) {
                    queued += PathAnimationRequest(PanelAnimationKind.LEFT_RIGHT, reveal = false, startProgress = start)
                }
            }

            leftReveal -> {
                queued += PathAnimationRequest(PanelAnimationKind.LEFT, reveal = true)
            }

            leftCollapse -> {
                if (runtime.leftPathLevel > 0) {
                    queued += PathAnimationRequest(
                        PanelAnimationKind.LEFT,
                        reveal = false,
                        startProgress = runtime.leftPathLevel
                    )
                }
            }

            rightReveal -> {
                queued += PathAnimationRequest(PanelAnimationKind.RIGHT, reveal = true)
            }

            rightCollapse -> {
                if (runtime.rightPathLevel > 0) {
                    queued += PathAnimationRequest(
                        PanelAnimationKind.RIGHT,
                        reveal = false,
                        startProgress = runtime.rightPathLevel
                    )
                }
            }
        }
        if (glintReveal) {
            playSound(player, "minecraft:block.trial_spawner.ominous_activate", 0.75f)
            playSound(player, "minecraft:block.beacon.power_select", 0.75f)
            queued += PathAnimationRequest(PanelAnimationKind.GLINT, reveal = true)
        }

        if (queued.isEmpty()) {
            renderStatic(player, inventory)
            return
        }

        runtime.pendingPathAnimations.clear()
        runtime.pendingPathAnimations.addAll(queued)
        val first = runtime.pendingPathAnimations.removeAt(0)
        startAnimation(player, inventory, runtime, first.kind, first.reveal, first.startProgress)
    }

    private fun startToolRemovalSequence(player: Player, inventory: Inventory, runtime: ViewerRuntime) {
        val activeKind = runtime.activeAnimationKind
        val activeProgress = runtime.activeAnimationProgress
        runtime.panelAnimationTask?.cancel()
        runtime.panelAnimationTask = null
        runtime.isPanelAnimating = false
        runtime.activeAnimationKind = null
        runtime.activeAnimationProgress = 0
        runtime.activeAnimationDirection = 1
        runtime.pendingPathAnimations.clear()
        runtime.returnCatalystsBeforeFullCollapse = true

        if (runtime.glintPathLevel > 0) {
            playSound(player, "minecraft:block.beacon.deactivate", 0.8f)
            playSound(player, "minecraft:block.ender_chest.close", 0.8f)
        }

        if (activeKind == PanelAnimationKind.FULL) {
            runtime.pendingPathAnimations += PathAnimationRequest(
                kind = PanelAnimationKind.FULL,
                reveal = false,
                startProgress = activeProgress.coerceIn(0, ArenaEnchantPedestalLayout.REVEAL_STEPS.size)
            )
            val first = runtime.pendingPathAnimations.removeAt(0)
            startAnimation(player, inventory, runtime, first.kind, first.reveal, first.startProgress)
            return
        }

        if (runtime.glintPathLevel > 0) {
            runtime.pendingPathAnimations += PathAnimationRequest(
                kind = PanelAnimationKind.GLINT,
                reveal = false,
                startProgress = runtime.glintPathLevel
            )
        }
        if (runtime.leftPathLevel > 0 && runtime.rightPathLevel > 0) {
            runtime.pendingPathAnimations += PathAnimationRequest(
                kind = PanelAnimationKind.LEFT_RIGHT,
                reveal = false,
                startProgress = maxOf(runtime.leftPathLevel, runtime.rightPathLevel)
            )
        } else if (runtime.rightPathLevel > 0) {
            runtime.pendingPathAnimations += PathAnimationRequest(
                kind = PanelAnimationKind.RIGHT,
                reveal = false,
                startProgress = runtime.rightPathLevel
            )
        } else if (runtime.leftPathLevel > 0) {
            runtime.pendingPathAnimations += PathAnimationRequest(
                kind = PanelAnimationKind.LEFT,
                reveal = false,
                startProgress = runtime.leftPathLevel
            )
        }

        runtime.pendingPathAnimations += PathAnimationRequest(kind = PanelAnimationKind.FULL, reveal = false)
        if (runtime.pendingPathAnimations.first().kind == PanelAnimationKind.FULL && runtime.returnCatalystsBeforeFullCollapse) {
            returnAllCatalystsToPlayer(player, inventory)
            runtime.returnCatalystsBeforeFullCollapse = false
        }
        val first = runtime.pendingPathAnimations.removeAt(0)
        startAnimation(player, inventory, runtime, first.kind, first.reveal, first.startProgress)
    }

    private fun handleExecuteClick(player: Player, inventory: Inventory, runtime: ViewerRuntime) {
        val evaluation = evaluate(player, inventory)
        if (!evaluation.executable || evaluation.requiredLevel == null || evaluation.preparedCatalysts.isEmpty()) {
            renderStatic(player, inventory)
            return
        }

        // TODO: executeForge() の成否を返す形にして、失敗時は成功SE/演出を分岐させる。
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

            if (stepIndex < ArenaEnchantPedestalLayout.FORGE_STEPS.size) {
                ArenaEnchantPedestalLayout.FORGE_STEPS[stepIndex].forEach { slot ->
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
        }, 0L, FORGE_ANIMATION_PERIOD_TICKS)
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

        val baseItem = toolItem.clone()
        val consumedCatalysts = prepared.sortedBy { it.slot }.map { it.item.clone() }
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
        auditLogger.logPedestalTransform(player.uniqueId, player.name, baseItem, workingTool.clone(), consumedCatalysts)
        inventory.setItem(ArenaEnchantPedestalLayout.TOOL_SLOT, workingTool)
    }

    private fun startAnimation(
        player: Player,
        inventory: Inventory,
        runtime: ViewerRuntime,
        kind: PanelAnimationKind,
        reveal: Boolean,
        startProgress: Int? = null
    ) {
        if (kind == PanelAnimationKind.LEFT_RIGHT) {
            runtime.leftRightStartLeft = if (reveal) ArenaEnchantPedestalLayout.LEFT_PATH_STEPS.size else runtime.leftPathLevel
            runtime.leftRightStartRight = if (reveal) ArenaEnchantPedestalLayout.RIGHT_PATH_STEPS.size else runtime.rightPathLevel
        }
        val max = resolveAnimationMax(kind, runtime)
        val periodTicks = resolveAnimationPeriodTicks(kind)
        runtime.panelAnimationTask?.cancel()
        runtime.isPanelAnimating = true
        runtime.activeAnimationKind = kind
        runtime.activeAnimationDirection = if (reveal) 1 else -1
        runtime.activeAnimationProgress = startProgress ?: if (reveal) 0 else max

        runtime.panelAnimationTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline || player.openInventory.topInventory != inventory) {
                runtime.panelAnimationTask?.cancel()
                runtime.panelAnimationTask = null
                runtime.isPanelAnimating = false
                runtime.activeAnimationKind = null
                return@Runnable
            }

            val evaluation = evaluate(player, inventory)
            val displayState = buildDisplayState(player, inventory, evaluation, runtime)

            val nextProgress = (runtime.activeAnimationProgress + runtime.activeAnimationDirection).coerceIn(0, max)
            runtime.activeAnimationProgress = nextProgress
            applyAnimationView(player, inventory, runtime, displayState, kind, nextProgress)

            val complete = nextProgress == if (runtime.activeAnimationDirection > 0) max else 0
            if (!complete) {
                return@Runnable
            }

            when (kind) {
                PanelAnimationKind.LEFT -> runtime.leftPathLevel = nextProgress
                PanelAnimationKind.RIGHT -> runtime.rightPathLevel = nextProgress
                PanelAnimationKind.LEFT_RIGHT -> {
                    runtime.leftPathLevel = minOf(nextProgress, runtime.leftRightStartLeft)
                    runtime.rightPathLevel = minOf(nextProgress, runtime.leftRightStartRight)
                }
                PanelAnimationKind.GLINT -> runtime.glintPathLevel = nextProgress
                PanelAnimationKind.FULL -> Unit
            }

            if (kind == PanelAnimationKind.FULL && evaluation.uiState == PedestalUiState.NO_TOOL) {
                resetPathState(runtime)
                runtime.returnCatalystsBeforeFullCollapse = false
                runtime.frozenRemovalDisplayState = null
            }

            runtime.panelAnimationTask?.cancel()
            runtime.panelAnimationTask = null
            runtime.isPanelAnimating = false
            runtime.activeAnimationKind = null
            runtime.activeAnimationProgress = 0
            runtime.activeAnimationDirection = 1
            applyDisplayState(inventory, buildDisplayState(player, inventory, evaluate(player, inventory), runtime))
            runNextPendingPathAnimation(player, inventory, runtime)
        }, 0L, periodTicks)
    }

    private fun resolveAnimationPeriodTicks(kind: PanelAnimationKind): Long {
        return when (kind) {
            PanelAnimationKind.FULL -> FULL_ANIMATION_PERIOD_TICKS
            PanelAnimationKind.LEFT,
            PanelAnimationKind.RIGHT,
            PanelAnimationKind.LEFT_RIGHT -> PATH_ANIMATION_PERIOD_TICKS
            PanelAnimationKind.GLINT -> GLINT_ANIMATION_PERIOD_TICKS
        }
    }

    private fun resolveAnimationMax(kind: PanelAnimationKind, runtime: ViewerRuntime): Int {
        return when (kind) {
            PanelAnimationKind.FULL -> ArenaEnchantPedestalLayout.REVEAL_STEPS.size
            PanelAnimationKind.LEFT -> ArenaEnchantPedestalLayout.LEFT_PATH_STEPS.size
            PanelAnimationKind.RIGHT -> ArenaEnchantPedestalLayout.RIGHT_PATH_STEPS.size
            PanelAnimationKind.LEFT_RIGHT -> maxOf(runtime.leftRightStartLeft, runtime.leftRightStartRight)
            PanelAnimationKind.GLINT -> ArenaEnchantPedestalLayout.GLINT_PATH_STEPS.size
        }
    }

    private fun runNextPendingPathAnimation(player: Player, inventory: Inventory, runtime: ViewerRuntime) {
        if (runtime.pendingPathAnimations.isEmpty()) {
            return
        }
        if (runtime.returnCatalystsBeforeFullCollapse && runtime.pendingPathAnimations.first().kind == PanelAnimationKind.FULL) {
            returnAllCatalystsToPlayer(player, inventory)
            runtime.returnCatalystsBeforeFullCollapse = false
        }
        val next = runtime.pendingPathAnimations.removeAt(0)
        startAnimation(player, inventory, runtime, next.kind, next.reveal, next.startProgress)
    }

    private fun animationSteps(kind: PanelAnimationKind): List<List<Int>> {
        return when (kind) {
            PanelAnimationKind.FULL -> ArenaEnchantPedestalLayout.REVEAL_STEPS
            PanelAnimationKind.LEFT -> ArenaEnchantPedestalLayout.LEFT_PATH_STEPS
            PanelAnimationKind.RIGHT -> ArenaEnchantPedestalLayout.RIGHT_PATH_STEPS
            PanelAnimationKind.LEFT_RIGHT -> ArenaEnchantPedestalLayout.LEFT_PATH_STEPS
            PanelAnimationKind.GLINT -> ArenaEnchantPedestalLayout.GLINT_PATH_STEPS
        }
    }

    private fun applyAnimationView(
        player: Player,
        inventory: Inventory,
        runtime: ViewerRuntime,
        displayState: Map<Int, ItemStack>,
        kind: PanelAnimationKind,
        progress: Int
    ) {
        val protectedSlots = resolveProtectedInputSlots(inventory)
        when (kind) {
            PanelAnimationKind.FULL -> {
                val allSlots = ArenaEnchantPedestalLayout.REVEAL_STEPS.flatten().toSet()
                val hiddenPane = buildHiddenAnimationPane(player)
                val steps = ArenaEnchantPedestalLayout.REVEAL_STEPS
                allSlots.forEach { slot ->
                    if (slot in protectedSlots) return@forEach
                    inventory.setItem(slot, hiddenPane)
                }
                for (i in 0 until progress.coerceIn(0, steps.size)) {
                    steps[i].forEach { slot ->
                        if (slot in protectedSlots) return@forEach
                        displayState[slot]?.let { inventory.setItem(slot, it) }
                    }
                }
            }

            PanelAnimationKind.LEFT,
            PanelAnimationKind.RIGHT -> {
                applyDisplayState(inventory, displayState)
                val pathColor = if (kind == PanelAnimationKind.LEFT) Material.PURPLE_STAINED_GLASS_PANE else Material.LIME_STAINED_GLASS_PANE
                val steps = animationSteps(kind)
                val selected = steps.take(progress.coerceIn(0, steps.size)).flatten().toSet()
                selected.forEach { slot ->
                    if (slot in protectedSlots) return@forEach
                    inventory.setItem(slot, buildPathPane(player, pathColor, glint = false))
                }
                if (kind == PanelAnimationKind.LEFT) {
                    runtime.leftPathLevel = progress.coerceIn(0, steps.size)
                } else {
                    runtime.rightPathLevel = progress.coerceIn(0, steps.size)
                }
            }

            PanelAnimationKind.LEFT_RIGHT -> {
                applyDisplayState(inventory, displayState)
                val leftCount = minOf(progress.coerceAtLeast(0), runtime.leftRightStartLeft)
                val rightCount = minOf(progress.coerceAtLeast(0), runtime.leftRightStartRight)
                val leftSlots = ArenaEnchantPedestalLayout.LEFT_PATH_STEPS
                    .take(leftCount.coerceIn(0, ArenaEnchantPedestalLayout.LEFT_PATH_STEPS.size))
                    .flatten()
                    .toSet()
                val rightSlots = ArenaEnchantPedestalLayout.RIGHT_PATH_STEPS
                    .take(rightCount.coerceIn(0, ArenaEnchantPedestalLayout.RIGHT_PATH_STEPS.size))
                    .flatten()
                    .toSet()

                leftSlots.forEach { slot ->
                    if (slot in protectedSlots) return@forEach
                    inventory.setItem(slot, buildPathPane(player, Material.PURPLE_STAINED_GLASS_PANE, glint = false))
                }
                rightSlots.forEach { slot ->
                    if (slot in protectedSlots) return@forEach
                    inventory.setItem(slot, buildPathPane(player, Material.LIME_STAINED_GLASS_PANE, glint = false))
                }
                runtime.leftPathLevel = leftCount.coerceIn(0, ArenaEnchantPedestalLayout.LEFT_PATH_STEPS.size)
                runtime.rightPathLevel = rightCount.coerceIn(0, ArenaEnchantPedestalLayout.RIGHT_PATH_STEPS.size)
            }

            PanelAnimationKind.GLINT -> {
                applyDisplayState(inventory, displayState)
                val glintSteps = ArenaEnchantPedestalLayout.GLINT_PATH_STEPS
                val count = progress.coerceIn(0, glintSteps.size)
                val glintSlots = glintSteps.take(count).flatten().toSet()
                glintSlots.forEach { slot ->
                    if (slot in protectedSlots) return@forEach
                    val material = when {
                        slot in ArenaEnchantPedestalLayout.RIGHT_PATH_SLOTS -> Material.LIME_STAINED_GLASS_PANE
                        slot in ArenaEnchantPedestalLayout.LEFT_PATH_SLOTS -> Material.PURPLE_STAINED_GLASS_PANE
                        else -> Material.WHITE_STAINED_GLASS_PANE
                    }
                    inventory.setItem(slot, buildPathPane(player, material, glint = true))
                }
                runtime.glintPathLevel = count
            }
        }
    }

    private fun renderStatic(player: Player, inventory: Inventory) {
        val evaluation = evaluate(player, inventory)
        val holder = inventory.holder as? ArenaEnchantPedestalHolder
        val runtime = holder?.let { runtimes.getOrPut(it.ownerId) { ViewerRuntime() } }
        if (runtime != null) {
            // TODO: 実機で挙動が固まったら、frozenRemovalDisplayState を含む状態機械を整理する。
            if (evaluation.uiState == PedestalUiState.NO_TOOL && !runtime.isPanelAnimating) {
                resetPathState(runtime)
            }
            runtime.lastExecutable = evaluation.executable
        }
        val displayState = buildDisplayState(player, inventory, evaluation, runtime)
        applyDisplayState(inventory, displayState)
    }

    private fun buildDisplayState(
        player: Player,
        inventory: Inventory,
        evaluation: PedestalEvaluation,
        runtime: ViewerRuntime? = null
    ): Map<Int, ItemStack> {
        val display = runtime?.frozenRemovalDisplayState
            ?.mapValues { (_, item) -> item.clone() }
            ?.toMutableMap()
            ?: buildBaseDisplayState(player, inventory, evaluation)

        val usePathOverlay = runtime?.frozenRemovalDisplayState != null || evaluation.uiState != PedestalUiState.NO_TOOL
        val leftCount = if (usePathOverlay) runtime?.leftPathLevel ?: 0 else 0
        val rightCount = if (usePathOverlay) runtime?.rightPathLevel ?: 0 else 0
        val glintCount = if (usePathOverlay) runtime?.glintPathLevel ?: 0 else 0

        ArenaEnchantPedestalLayout.LEFT_PATH_STEPS.take(leftCount.coerceIn(0, ArenaEnchantPedestalLayout.LEFT_PATH_STEPS.size)).flatten().forEach { slot ->
            if (getInputItem(inventory, slot) == null) {
                display[slot] = buildPathPane(player, Material.PURPLE_STAINED_GLASS_PANE, glint = false)
            }
        }
        ArenaEnchantPedestalLayout.RIGHT_PATH_STEPS.take(rightCount.coerceIn(0, ArenaEnchantPedestalLayout.RIGHT_PATH_STEPS.size)).flatten().forEach { slot ->
            if (getInputItem(inventory, slot) == null) {
                display[slot] = buildPathPane(player, Material.LIME_STAINED_GLASS_PANE, glint = false)
            }
        }
        ArenaEnchantPedestalLayout.GLINT_PATH_STEPS.take(glintCount.coerceIn(0, ArenaEnchantPedestalLayout.GLINT_PATH_STEPS.size)).flatten().forEach { slot ->
            if (getInputItem(inventory, slot) == null) {
                val material = when {
                    slot in ArenaEnchantPedestalLayout.RIGHT_PATH_SLOTS -> Material.LIME_STAINED_GLASS_PANE
                    slot in ArenaEnchantPedestalLayout.LEFT_PATH_SLOTS -> Material.PURPLE_STAINED_GLASS_PANE
                    else -> Material.WHITE_STAINED_GLASS_PANE
                }
                display[slot] = buildPathPane(player, material, glint = true)
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

    private fun buildBaseDisplayState(
        player: Player,
        inventory: Inventory,
        evaluation: PedestalEvaluation
    ): MutableMap<Int, ItemStack> {
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

    private fun buildFrozenRemovalDisplayState(player: Player, inventory: Inventory, runtime: ViewerRuntime): Map<Int, ItemStack> {
        val frozenRuntime = runtime.copy(
            panelAnimationTask = null,
            forgeAnimationTask = null,
            isPanelAnimating = false,
            activeAnimationKind = null,
            activeAnimationProgress = 0,
            activeAnimationDirection = 1,
            glintPathLevel = 0,
            frozenRemovalDisplayState = null,
            returnCatalystsBeforeFullCollapse = false,
            pendingPathAnimations = mutableListOf()
        )
        return buildDisplayState(player, inventory, evaluate(player, inventory), frozenRuntime)
            .mapValues { (_, item) -> item.clone() }
    }

    private fun applyDisplayState(inventory: Inventory, displayState: Map<Int, ItemStack>) {
        displayState.forEach { (slot, item) ->
            inventory.setItem(slot, item)
        }
    }

    private fun resetPathState(runtime: ViewerRuntime) {
        runtime.leftRightStartLeft = 0
        runtime.leftRightStartRight = 0
        runtime.leftPathLevel = 0
        runtime.rightPathLevel = 0
        runtime.glintPathLevel = 0
        runtime.frozenRemovalDisplayState = null
    }

    private fun captureSnapshot(player: Player, inventory: Inventory): InputSnapshot {
        val tool = getInputItem(inventory, ArenaEnchantPedestalLayout.TOOL_SLOT)
        val shardCount = ArenaEnchantPedestalLayout.CATALYST_SLOTS.count { slot -> getInputItem(inventory, slot) != null }
        val evaluation = evaluate(player, inventory)
        return InputSnapshot(
            hasTool = tool != null,
            hasAnyInput = tool != null || shardCount > 0,
            shardCount = shardCount,
            expReady = evaluation.expReady,
            executable = evaluation.executable
        )
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
        if (evaluation.powerInsufficient) {
            meta.setDisplayName(ArenaI18n.text(player, "arena.ui.pedestal.execute.insufficient_power", "§c祭壇の力が足りません"))
            meta.lore = null
        } else if (!evaluation.processable) {
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
        if (evaluation.powerInsufficient) {
            meta.setDisplayName("§5オーバーエンチャント")
            meta.lore = listOf(ArenaI18n.text(player, "arena.ui.pedestal.execute.insufficient_power", "§c祭壇の力が足りません"))
        } else if (!evaluation.processable) {
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

    private fun buildPathPane(player: Player, material: Material, glint: Boolean): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(ArenaI18n.text(player, "arena.ui.pedestal.blank", " "))
        meta.persistentDataContainer.set(inputPlaceholderKey, PersistentDataType.BYTE, 1)
        meta.setEnchantmentGlintOverride(glint)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
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
            val catalyst = ArenaEnchantShardData.read(item)
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

            if (!validateArenaPower(player, catalyst)) {
                return PedestalEvaluation(
                    catalystReady = true,
                    processable = false,
                    expReady = false,
                    executable = false,
                    powerInsufficient = true,
                    requiredLevel = null,
                    missingLevel = null,
                    uiState = uiState,
                    unlockedSlotCount = unlockProgress.unlockedSlotCount,
                    usedSlotCount = usedSlotCount,
                    preparedCatalysts = emptyList()
                )
            }

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
        catalyst: ArenaEnchantShardData.Shard,
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
        catalyst: ArenaEnchantShardData.Shard,
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

    private fun validateArenaPower(player: Player, catalyst: ArenaEnchantShardData.Shard): Boolean {
        val currentStar = currentArenaStar(player) ?: return true
        return resolveShardLevel(catalyst) <= currentStar
    }

    private fun currentArenaStar(player: Player): Int? {
        val session = arenaManagerProvider()?.getSession(player) ?: return null
        return session.difficultyStar.coerceAtLeast(1)
    }

    private fun resolveShardLevel(catalyst: ArenaEnchantShardData.Shard): Int {
        return when (catalyst.mode) {
            ArenaOverEnchanterMode.LIMIT_BREAKING -> catalyst.overLevel ?: 1
            ArenaOverEnchanterMode.OVER_STACKING,
            ArenaOverEnchanterMode.EXOTIC_ATTACH -> 1
        }
    }

    private fun simulateApply(
        tool: ItemStack,
        state: OverEnchantState,
        catalyst: ArenaEnchantShardData.Shard
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

    private fun resolveRequiredLevel(catalyst: ArenaEnchantShardData.Shard): Int? {
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
        return ArenaEnchantShardData.read(item) != null
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

    private fun resolveAppliedOverLevel(catalyst: ArenaEnchantShardData.Shard): Int {
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

    private fun returnAllCatalystsToPlayer(player: Player, inventory: Inventory) {
        ArenaEnchantPedestalLayout.CATALYST_SLOTS.forEach { slot ->
            returnItemToPlayer(player, inventory, slot)
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
        val incoming = ArenaEnchantShardData.read(item) ?: return false
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
            val existingCatalyst = ArenaEnchantShardData.read(existing) ?: return@filter true
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
