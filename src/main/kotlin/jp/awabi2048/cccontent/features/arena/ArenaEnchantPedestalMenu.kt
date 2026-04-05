package jp.awabi2048.cccontent.features.arena

import jp.awabi2048.cccontent.items.arena.ArenaOverEnchanterCatalystData
import jp.awabi2048.cccontent.items.arena.ArenaOverEnchanterMode
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
    const val CATALYST_SLOT = 19
    const val TOOL_SLOT = 22
    const val EXP_SLOT = 25
    const val EXECUTE_SLOT = 40
    const val LEFT_PLACEHOLDER_SLOT = 37
    const val RIGHT_PLACEHOLDER_SLOT = 43
    val TITLE: String
        get() = ArenaI18n.text(null, "arena.ui.pedestal.title", "§8不思議な祭壇")

    val CATALYST_STEPS: List<List<Int>> = listOf(
        listOf(18),
        listOf(20),
        listOf(12, 21, 30),
        listOf(13)
    )

    val EXP_STEPS: List<List<Int>> = listOf(
        listOf(26),
        listOf(24),
        listOf(14, 23, 32),
        listOf(31)
    )

    val PANEL_SLOTS: Set<Int> = (CATALYST_STEPS.flatten() + EXP_STEPS.flatten()).toSet()
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
    WHITE,
    PURPLE,
    GREEN
}

private data class PedestalEvaluation(
    val catalystReady: Boolean,
    val processable: Boolean,
    val expReady: Boolean,
    val executable: Boolean,
    val requiredLevel: Int?,
    val missingLevel: Int?,
    val mode: ArenaOverEnchanterMode?
)

private data class ViewerRuntime(
    var panelAnimationTask: BukkitTask? = null,
    var forgeAnimationTask: BukkitTask? = null,
    var routeGlintTask: BukkitTask? = null,
    var isForging: Boolean = false,
    var routeGlintEnabled: Boolean = false,
    var lastExecutable: Boolean = false
)

class ArenaEnchantPedestalMenu(
    private val plugin: JavaPlugin,
    private val coreConfigProvider: () -> FileConfiguration
) : Listener {
    private companion object {
        val PROTECTION_IDS: Set<String> = setOf("protection", "fire_protection", "blast_protection", "projectile_protection")
        val DAMAGE_IDS: Set<String> = setOf("sharpness", "smite", "bane_of_arthropods")
    }

    private val runtimes = mutableMapOf<UUID, ViewerRuntime>()
    private val routeModeKey = NamespacedKey(plugin, "over_enchanter_route_mode")
    private val overStackingUsedKey = NamespacedKey(plugin, "over_enchanter_over_stacking_used")
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

        if (rawSlot !in setOf(ArenaEnchantPedestalLayout.CATALYST_SLOT, ArenaEnchantPedestalLayout.TOOL_SLOT)) {
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
                processInputStateChange(player, top, rawSlot, wasEmpty, nowEmpty)
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
            processInputStateChange(player, top, rawSlot, wasEmpty, nowEmpty)
        })
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.view.topInventory.holder as? ArenaEnchantPedestalHolder ?: return
        val player = event.player as? Player ?: return
        val runtime = runtimes.remove(holder.ownerId)
        runtime?.panelAnimationTask?.cancel()
        runtime?.forgeAnimationTask?.cancel()
        runtime?.routeGlintTask?.cancel()
        runtime?.isForging = false

        playSound(player, "minecraft:block.ender_chest.close", 0.5f)
        playSound(player, "minecraft:entity.allay.item_thrown", 0.5f)

        val top = event.inventory
        returnItemToPlayer(player, top, ArenaEnchantPedestalLayout.CATALYST_SLOT)
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
        if (clickedTop) {
            val rawSlot = event.rawSlot
            if (rawSlot !in setOf(ArenaEnchantPedestalLayout.CATALYST_SLOT, ArenaEnchantPedestalLayout.TOOL_SLOT)) {
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
                processInputStateChange(player, top, rawSlot, wasEmpty, nowEmpty)
            })
            return
        }

        val moving = event.currentItem?.takeUnless { it.type.isAir } ?: return
        val targetSlot = resolveInputSlotForItem(moving)
        if (targetSlot == null || !isInputSlotAvailableForInsert(top, targetSlot)) {
            event.isCancelled = true
            return
        }

        event.isCancelled = true
        val source = event.clickedInventory ?: return
        source.setItem(event.slot, null)
        top.setItem(targetSlot, moving.clone())
        playSound(player, "minecraft:block.enchantment_table.use", 1.7f)
        playSound(player, "minecraft:block.end_portal_frame.fill", 0.75f)
        animatePanelTransition(player, top)
    }

    private fun isTopSlotOperationAllowed(event: InventoryClickEvent, topSlot: Int): Boolean {
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
            return true
        }
        val placeItem = cursor?.takeUnless { it.type.isAir } ?: return false
        return when (topSlot) {
            ArenaEnchantPedestalLayout.CATALYST_SLOT -> isCatalystItem(placeItem)
            ArenaEnchantPedestalLayout.TOOL_SLOT -> isToolCandidateItem(placeItem)
            else -> false
        }
    }

    private fun resolveInputSlotForItem(item: ItemStack): Int? {
        return when {
            isCatalystItem(item) -> ArenaEnchantPedestalLayout.CATALYST_SLOT
            isToolCandidateItem(item) -> ArenaEnchantPedestalLayout.TOOL_SLOT
            else -> null
        }
    }

    private fun processInputStateChange(
        player: Player,
        inventory: Inventory,
        rawSlot: Int,
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
        } else {
            playSound(player, "minecraft:block.enchantment_table.use", 1.7f)
            playSound(player, "minecraft:block.end_portal_frame.fill", 0.75f)
        }
        animatePanelTransition(player, inventory)
    }

    private fun handleExecuteClick(player: Player, inventory: Inventory, runtime: ViewerRuntime) {
        val evaluation = evaluate(player, inventory)
        if (!evaluation.executable || evaluation.requiredLevel == null || evaluation.mode == null) {
            renderStatic(player, inventory)
            return
        }

        runtime.panelAnimationTask?.cancel()
        runtime.routeGlintTask?.cancel()
        runtime.routeGlintTask = null
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

            if (stepIndex < 4) {
                ArenaEnchantPedestalLayout.CATALYST_STEPS[stepIndex].forEach { slot ->
                    inventory.setItem(slot, buildForgeProgressItem(player))
                }
                ArenaEnchantPedestalLayout.EXP_STEPS[stepIndex].forEach { slot ->
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
        val catalystItem = getInputItem(inventory, ArenaEnchantPedestalLayout.CATALYST_SLOT) ?: return
        val toolItem = getInputItem(inventory, ArenaEnchantPedestalLayout.TOOL_SLOT) ?: return
        val catalyst = ArenaOverEnchanterCatalystData.read(catalystItem) ?: return
        val targetEnchant = resolveEnchantment(catalyst.targetEnchantmentId) ?: return

        val eval = evaluate(player, inventory)
        val requiredLevel = eval.requiredLevel ?: return
        if (!eval.executable) {
            return
        }
        if (player.level < requiredLevel) {
            return
        }

        val resultingLevel = when (catalyst.mode) {
            ArenaOverEnchanterMode.LIMIT_BREAKING -> {
                val overLevel = catalyst.overLevel ?: return
                targetEnchant.maxLevel + overLevel
            }

            ArenaOverEnchanterMode.OVER_STACKING,
            ArenaOverEnchanterMode.EXOTIC_ATTACH -> targetEnchant.maxLevel
        }

        toolItem.addUnsafeEnchantment(targetEnchant, resultingLevel)
        val modeValue = when (catalyst.mode) {
            ArenaOverEnchanterMode.LIMIT_BREAKING -> "limit_breaking"
            ArenaOverEnchanterMode.OVER_STACKING,
            ArenaOverEnchanterMode.EXOTIC_ATTACH -> "over_path"
        }
        setRouteMode(toolItem, modeValue)
        if (catalyst.mode == ArenaOverEnchanterMode.OVER_STACKING || catalyst.mode == ArenaOverEnchanterMode.EXOTIC_ATTACH) {
            setOverStackingUsed(toolItem, true)
        }

        player.level = (player.level - requiredLevel).coerceAtLeast(0)

        if (catalystItem.amount > 1) {
            catalystItem.amount -= 1
        } else {
            inventory.setItem(ArenaEnchantPedestalLayout.CATALYST_SLOT, null)
        }
        inventory.setItem(ArenaEnchantPedestalLayout.TOOL_SLOT, toolItem)
    }

    private fun animatePanelTransition(player: Player, inventory: Inventory) {
        val holder = inventory.holder as? ArenaEnchantPedestalHolder ?: return
        val runtime = runtimes.getOrPut(holder.ownerId) { ViewerRuntime() }
        runtime.panelAnimationTask?.cancel()

        val evaluation = evaluate(player, inventory)
        val targetLeft = if (evaluation.catalystReady) PanelColor.PURPLE else PanelColor.WHITE
        val targetRight = if (evaluation.expReady) PanelColor.GREEN else PanelColor.WHITE

        var stepIndex = 0
        runtime.panelAnimationTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline || player.openInventory.topInventory != inventory) {
                runtime.panelAnimationTask?.cancel()
                runtime.panelAnimationTask = null
                return@Runnable
            }

            if (stepIndex < 4) {
                ArenaEnchantPedestalLayout.CATALYST_STEPS[stepIndex].forEach { slot ->
                    inventory.setItem(slot, buildPanelItem(player, targetLeft, glint = false))
                }
                ArenaEnchantPedestalLayout.EXP_STEPS[stepIndex].forEach { slot ->
                    inventory.setItem(slot, buildPanelItem(player, targetRight, glint = false))
                }
                stepIndex += 1
                return@Runnable
            }

            runtime.panelAnimationTask?.cancel()
            runtime.panelAnimationTask = null
            renderStatic(player, inventory)
        }, 0L, 3L)
    }

    private fun renderStatic(player: Player, inventory: Inventory) {
        val headerFooter = buildSimplePane(player, Material.BLACK_STAINED_GLASS_PANE)
        val neutral = buildSimplePane(player, Material.GRAY_STAINED_GLASS_PANE)

        for (slot in 0..8) {
            if (slot != ArenaEnchantPedestalLayout.CATALYST_SLOT && slot != ArenaEnchantPedestalLayout.TOOL_SLOT && slot != ArenaEnchantPedestalLayout.EXP_SLOT && slot != ArenaEnchantPedestalLayout.EXECUTE_SLOT) {
                inventory.setItem(slot, headerFooter)
            }
        }
        for (slot in 36..44) {
            if (slot != ArenaEnchantPedestalLayout.CATALYST_SLOT && slot != ArenaEnchantPedestalLayout.TOOL_SLOT && slot != ArenaEnchantPedestalLayout.EXP_SLOT && slot != ArenaEnchantPedestalLayout.EXECUTE_SLOT) {
                inventory.setItem(slot, headerFooter)
            }
        }

        for (slot in 9..35) {
            if (slot !in ArenaEnchantPedestalLayout.PANEL_SLOTS && slot != ArenaEnchantPedestalLayout.CATALYST_SLOT && slot != ArenaEnchantPedestalLayout.TOOL_SLOT && slot != ArenaEnchantPedestalLayout.EXP_SLOT && slot != ArenaEnchantPedestalLayout.EXECUTE_SLOT) {
                inventory.setItem(slot, neutral)
            }
        }

        val evaluation = evaluate(player, inventory)
        val leftColor = if (evaluation.catalystReady) PanelColor.PURPLE else PanelColor.WHITE
        val rightColor = if (evaluation.expReady) PanelColor.GREEN else PanelColor.WHITE

        val holder = inventory.holder as? ArenaEnchantPedestalHolder
        val runtime = holder?.let { runtimes.getOrPut(it.ownerId) { ViewerRuntime() } }
        val routeGlint = runtime?.routeGlintEnabled == true && evaluation.executable
        if (runtime != null) {
            val wasExecutable = runtime.lastExecutable
            if (!wasExecutable && evaluation.executable) {
                playSound(player, "minecraft:block.trial_spawner.ominous_activate", 0.75f)
                playSound(player, "minecraft:block.beacon.power_select", 0.75f)
                startReadyRouteGlintAnimation(player, inventory, runtime, leftColor, rightColor)
            }
            if (wasExecutable && !evaluation.executable) {
                runtime.routeGlintTask?.cancel()
                runtime.routeGlintTask = null
                runtime.routeGlintEnabled = false
                playSound(player, "minecraft:block.beacon.deactivate", 0.8f)
                playSound(player, "minecraft:block.ender_chest.close", 0.8f)
            }
            runtime.lastExecutable = evaluation.executable
        }

        ArenaEnchantPedestalLayout.CATALYST_STEPS.flatten().forEach { slot ->
            inventory.setItem(slot, buildPanelItem(player, leftColor, glint = routeGlint))
        }
        ArenaEnchantPedestalLayout.EXP_STEPS.flatten().forEach { slot ->
            inventory.setItem(slot, buildPanelItem(player, rightColor, glint = routeGlint))
        }

        if (getInputItem(inventory, ArenaEnchantPedestalLayout.CATALYST_SLOT).isNullOrAir()) {
            inventory.setItem(ArenaEnchantPedestalLayout.CATALYST_SLOT, buildInputSlotPlaceholder(player))
        }
        if (getInputItem(inventory, ArenaEnchantPedestalLayout.TOOL_SLOT).isNullOrAir()) {
            inventory.setItem(ArenaEnchantPedestalLayout.TOOL_SLOT, buildInputSlotPlaceholder(player))
        }

        inventory.setItem(ArenaEnchantPedestalLayout.EXP_SLOT, buildExpInfoItem(player, evaluation))
        inventory.setItem(ArenaEnchantPedestalLayout.EXECUTE_SLOT, buildExecuteItem(player, evaluation.executable))
    }

    private fun buildExpInfoItem(player: Player, evaluation: PedestalEvaluation): ItemStack {
        val item = ItemStack(Material.BOOKSHELF)
        val meta = item.itemMeta ?: return item
        if (!evaluation.processable) {
            meta.setDisplayName("§c力を感じられません")
            meta.lore = null
        } else if (evaluation.missingLevel != null && evaluation.missingLevel > 0) {
            meta.setDisplayName("§c智力が足りません")
            meta.lore = listOf("§8あと §a${evaluation.missingLevel} レベル §8必要です")
        } else {
            val consume = evaluation.requiredLevel ?: 0
            meta.setDisplayName("§4智力は十分です")
            meta.lore = listOf("§8合成によって §a$consume レベル §8を消費します")
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        return item
    }

    private fun buildExecuteItem(player: Player, executable: Boolean): ItemStack {
        val item = ItemStack(Material.ENCHANTING_TABLE)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(if (executable) "§d力を解放する" else "§d§kaaaaaa")
        meta.lore = null
        meta.setEnchantmentGlintOverride(executable)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
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

    private fun buildSimplePane(player: Player, material: Material): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(ArenaI18n.text(player, "arena.ui.pedestal.blank", " "))
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        applyExplicitTooltipHide(meta)
        item.itemMeta = meta
        return item
    }

    private fun buildSimpleItem(player: Player, material: Material, nameKey: String, fallback: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(ArenaI18n.text(player, nameKey, fallback))
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        applyExplicitTooltipHide(meta)
        item.itemMeta = meta
        return item
    }

    private fun buildPanelItem(player: Player, color: PanelColor, glint: Boolean): ItemStack {
        val material = when (color) {
            PanelColor.WHITE -> Material.WHITE_STAINED_GLASS_PANE
            PanelColor.PURPLE -> Material.PURPLE_STAINED_GLASS_PANE
            PanelColor.GREEN -> Material.LIME_STAINED_GLASS_PANE
        }
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(ArenaI18n.text(player, "arena.ui.pedestal.blank", " "))
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
        val catalystItem = getInputItem(inventory, ArenaEnchantPedestalLayout.CATALYST_SLOT)
        val toolItem = getInputItem(inventory, ArenaEnchantPedestalLayout.TOOL_SLOT)
        val catalyst = catalystItem?.let { ArenaOverEnchanterCatalystData.read(it) }
        if (catalyst == null) {
            return PedestalEvaluation(
                catalystReady = false,
                processable = false,
                expReady = false,
                executable = false,
                requiredLevel = null,
                missingLevel = null,
                mode = null
            )
        }

        val requiredLevel = resolveRequiredLevel(catalyst)
        val hasRequiredLevel = requiredLevel != null
        val tool = toolItem?.takeUnless { it.type.isAir }?.clone()

        val catalystReady = hasRequiredLevel
        if (tool == null || !hasRequiredLevel || requiredLevel == null) {
            return PedestalEvaluation(
                catalystReady = catalystReady,
                processable = false,
                expReady = false,
                executable = false,
                requiredLevel = requiredLevel,
                missingLevel = null,
                mode = catalyst.mode
            )
        }

        val toolValid = validateToolByMode(tool, catalyst)
        val routeValid = validateRouteCompatibility(tool, catalyst.mode)
        val processable = catalystReady && toolValid && routeValid
        val expReady = processable && player.level >= requiredLevel
        val missingLevel = if (processable && player.level < requiredLevel) requiredLevel - player.level else null
        return PedestalEvaluation(
            catalystReady = catalystReady,
            processable = processable,
            expReady = expReady,
            executable = expReady,
            requiredLevel = requiredLevel,
            missingLevel = missingLevel,
            mode = catalyst.mode
        )
    }

    private fun startReadyRouteGlintAnimation(
        player: Player,
        inventory: Inventory,
        runtime: ViewerRuntime,
        leftColor: PanelColor,
        rightColor: PanelColor
    ) {
        runtime.routeGlintTask?.cancel()
        runtime.routeGlintEnabled = false
        var stepIndex = 0
        runtime.routeGlintTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline || player.openInventory.topInventory != inventory) {
                runtime.routeGlintTask?.cancel()
                runtime.routeGlintTask = null
                runtime.routeGlintEnabled = false
                return@Runnable
            }

            if (runtime.isForging || !evaluate(player, inventory).executable) {
                runtime.routeGlintTask?.cancel()
                runtime.routeGlintTask = null
                runtime.routeGlintEnabled = false
                return@Runnable
            }

            if (stepIndex < ArenaEnchantPedestalLayout.CATALYST_STEPS.size) {
                ArenaEnchantPedestalLayout.CATALYST_STEPS[stepIndex].forEach { slot ->
                    inventory.setItem(slot, buildPanelItem(player, leftColor, glint = true))
                }
                ArenaEnchantPedestalLayout.EXP_STEPS[stepIndex].forEach { slot ->
                    inventory.setItem(slot, buildPanelItem(player, rightColor, glint = true))
                }
                stepIndex += 1
                return@Runnable
            }

            runtime.routeGlintTask?.cancel()
            runtime.routeGlintTask = null
            runtime.routeGlintEnabled = true
        }, 0L, 1L)
    }

    private fun validateRouteCompatibility(tool: ItemStack, mode: ArenaOverEnchanterMode): Boolean {
        val route = getRouteMode(tool)
        val overUsed = isOverStackingUsed(tool)
        return when (mode) {
            ArenaOverEnchanterMode.LIMIT_BREAKING -> route != "over_path" && !overUsed
            ArenaOverEnchanterMode.OVER_STACKING,
            ArenaOverEnchanterMode.EXOTIC_ATTACH -> route != "limit_breaking" && !overUsed
        }
    }

    private fun validateToolByMode(tool: ItemStack, catalyst: ArenaOverEnchanterCatalystData.Catalyst): Boolean {
        val targetEnchant = resolveEnchantment(catalyst.targetEnchantmentId) ?: return false
        val currentTargetLevel = tool.getEnchantmentLevel(targetEnchant)

        return when (catalyst.mode) {
            ArenaOverEnchanterMode.LIMIT_BREAKING -> {
                val overLevel = catalyst.overLevel ?: return false
                if (!targetEnchant.canEnchantItem(tool)) {
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

    private fun getRouteMode(item: ItemStack): String? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(routeModeKey, PersistentDataType.STRING)
    }

    private fun setRouteMode(item: ItemStack, mode: String) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(routeModeKey, PersistentDataType.STRING, mode)
        item.itemMeta = meta
    }

    private fun isOverStackingUsed(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.get(overStackingUsedKey, PersistentDataType.BYTE)?.toInt() == 1
    }

    private fun setOverStackingUsed(item: ItemStack, used: Boolean) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(overStackingUsedKey, PersistentDataType.BYTE, if (used) 1 else 0)
        item.itemMeta = meta
    }

    private fun returnItemToPlayer(player: Player, inventory: Inventory, slot: Int) {
        val item = getInputItem(inventory, slot) ?: return
        inventory.setItem(slot, null)
        val leftovers = player.inventory.addItem(item)
        leftovers.values.forEach { leftover ->
            player.world.dropItemNaturally(player.location, leftover)
        }
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
        val cursor = event.cursor?.clone()?.takeUnless { it.type.isAir } ?: return
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
