@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.arena

import jp.awabi2048.cccontent.gui.ManagedMenuPresenter

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import jp.awabi2048.cccontent.economy.ContentEconomyBridge
import jp.awabi2048.cccontent.gui.GuiMenuItems
import jp.awabi2048.cccontent.gui.VirtualInventoryEscrowService
import jp.awabi2048.cccontent.items.CustomItemI18n
import jp.awabi2048.cccontent.items.arena.ArenaMobTokenItem
import jp.awabi2048.cccontent.util.ItemMetaCompat
import jp.awabi2048.cccontent.util.cancelWithDebug
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

private object ArenaTokenExchangeLayout {
    const val MENU_SIZE = 45
    const val BULK_EXCHANGE_SLOT = 38
    const val EXCHANGE_SLOT = 40
    const val INFO_SLOT = 42
    const val CONFIRM_PREVIEW_SLOT = 22
    const val CONFIRM_OK_SLOT = 20
    const val CONFIRM_CANCEL_SLOT = 24
    val INPUT_SLOTS: Set<Int> = (10..16).toSet() + (19..25).toSet() + (28..34).toSet()
    val TITLE: String get() = ArenaI18n.text(null, "arena.ui.token_exchange.title")
    val CONFIRM_TITLE: String get() = ArenaI18n.text(null, "arena.ui.token_exchange.confirm_title")
}

private enum class TokenExchangeMode(val id: String, val material: Material) {
    INPUT_SLOTS("input_slots", Material.BUNDLE),
    ALL_INVENTORY("all_inventory", Material.CHEST)
}

private class ArenaTokenExchangeHolder(val ownerId: UUID) : InventoryHolder {
    lateinit var backingInventory: Inventory
    val escrowBySlot = mutableMapOf<Int, String>()
    var transitionToConfirm = false
    override fun getInventory(): Inventory = backingInventory
}

private class ArenaTokenExchangeConfirmHolder(
    val ownerId: UUID,
    val mode: TokenExchangeMode,
    val quote: TokenExchangeQuote,
    val sourceHolder: ArenaTokenExchangeHolder
) : InventoryHolder {
    lateinit var backingInventory: Inventory
    var handled = false
    override fun getInventory(): Inventory = backingInventory
}

private data class TokenExchangeLine(
    val categoryId: String,
    val amount: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal
)

private data class TokenExchangeQuote(
    val lines: List<TokenExchangeLine>,
    val total: BigDecimal,
    val stacks: List<ItemStack>
) {
    val signature: Map<String, Int> = lines.associate { it.categoryId to it.amount }
}

private data class RemovedInputItem(val slot: Int, val item: ItemStack)

class ArenaTokenExchangeMenu(private val plugin: JavaPlugin) : Listener {
    private companion object {
        const val CONFIG_PATH = "config/arena/token_exchange.yml"
        const val FEATURE_ID = "arena_token_exchange"
        const val VIEW_ID = "token_exchange_input"
        val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.DOWN)
        val REQUIRED_RATE_CATEGORY_IDS = setOf(
            "skeleton", "zombie", "drowned", "spider", "husk", "bogged", "guardian",
            "wither_skeleton", "blaze", "slime", "silverfish", "spirit", "magma_cube",
            "witch", "iron_golem", "elder_guardian", "frog", "enderman", "shulker",
            "endermite", "bat", "creeper", "boomerang"
        )
    }

    private val hintKey = NamespacedKey(plugin, "arena_token_exchange_hint")
    private val auditLogger = ArenaAuditLogger(plugin)
    private val escrow = VirtualInventoryEscrowService(plugin)
    private val activeMenuPlayers = mutableSetOf<UUID>()
    private var rates: Map<String, BigDecimal> = emptyMap()
    private var maintenanceTask: BukkitTask? = null
    private var maintenanceTick = 0

    fun initialize() {
        escrow.initialize()
        reload()
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { maintainHints() }, 10L, 10L)
    }

    fun shutdown() {
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.openInventory.topInventory.holder is ArenaTokenExchangeHolder ||
                player.openInventory.topInventory.holder is ArenaTokenExchangeConfirmHolder
            ) {
                ManagedMenuPresenter.close(player)
            }
        }
        maintenanceTask?.cancel()
        maintenanceTask = null
        Bukkit.getOnlinePlayers().forEach { player -> removeTemporaryHints(player, "plugin_shutdown", true) }
        activeMenuPlayers.clear()
    }

    fun reload() {
        val file = File(plugin.dataFolder, CONFIG_PATH)
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource(CONFIG_PATH, false)
        }
        val config = YamlConfiguration.loadConfiguration(file)
        val section = config.getConfigurationSection("rates")
            ?: throw IllegalStateException("$CONFIG_PATH: rates がありません")
        val loadedRates = section.getKeys(false).associateWith { categoryId ->
            val value = section.getDouble(categoryId, Double.NaN)
            require(value.isFinite() && value > 0.0) { "$CONFIG_PATH: rates.$categoryId が不正です" }
            truncate(value)
        }
        val missingCategoryIds = REQUIRED_RATE_CATEGORY_IDS - loadedRates.keys
        require(missingCategoryIds.isEmpty()) {
            "$CONFIG_PATH: rates is missing required categories: ${missingCategoryIds.sorted().joinToString()}"
        }
        rates = loadedRates
    }

    fun openMenu(player: Player) {
        if (player.openInventory.topInventory.holder is ArenaTokenExchangeHolder ||
            player.openInventory.topInventory.holder is ArenaTokenExchangeConfirmHolder
        ) {
            ManagedMenuPresenter.close(player)
        }
        recoverPendingItems(player, "menu_open")
        val holder = ArenaTokenExchangeHolder(player.uniqueId)
        val inventory = Bukkit.createInventory(holder, ArenaTokenExchangeLayout.MENU_SIZE, ArenaTokenExchangeLayout.TITLE)
        holder.backingInventory = inventory
        activeMenuPlayers += player.uniqueId
        syncTemporaryHints(player)
        render(player, holder)
        ManagedMenuPresenter.open(
            player,
            inventory,
            menuId = "arena_token_exchange",
            policy = ManagedMenuPresenter.inputPolicy(ArenaTokenExchangeLayout.INPUT_SLOTS),
        )
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        recoverPendingItems(event.player, "player_join")
        removeTemporaryHints(event.player, "player_join_cleanup", true)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        activeMenuPlayers -= event.player.uniqueId
        removeTemporaryHints(event.player, "player_quit", false)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        when (val holder = event.view.topInventory.holder) {
            is ArenaTokenExchangeHolder -> handleMenuClick(event, holder)
            is ArenaTokenExchangeConfirmHolder -> handleConfirmClick(event, holder)
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        when (event.view.topInventory.holder) {
            is ArenaTokenExchangeHolder -> {
                if (event.rawSlots.any { it < event.view.topInventory.size }) {
                    event.isCancelled = true
                }
            }
            is ArenaTokenExchangeConfirmHolder -> event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        when (val holder = event.view.topInventory.holder) {
            is ArenaTokenExchangeHolder -> {
                if (holder.transitionToConfirm) return
                closeExchangeFlow(player, holder, "menu_closed")
            }
            is ArenaTokenExchangeConfirmHolder -> {
                if (holder.handled) return
                holder.sourceHolder.transitionToConfirm = false
                auditLogger.logTokenExchangeConfirmation(
                    player.uniqueId, player.name, "token_exchange_confirm_cancelled",
                    holder.mode.id, holder.quote.total.toDouble(), holder.quote.stacks, "confirm_closed"
                )
                closeExchangeFlow(player, holder.sourceHolder, "confirm_closed")
            }
        }
    }

    private fun handleMenuClick(event: InventoryClickEvent, holder: ArenaTokenExchangeHolder) {
        val player = event.whoClicked as? Player ?: return
        if (player.uniqueId != holder.ownerId) {
            event.cancelWithDebug("ArenaTokenExchangeMenu.handleMenuClick: wrong_owner")
            return
        }
        val top = holder.backingInventory
        if (blockedClick(event.click)) {
            event.cancelWithDebug("ArenaTokenExchangeMenu.handleMenuClick: blocked_click")
            return
        }
        val clickedTop = event.clickedInventory == top
        if (!clickedTop) {
            if (event.isShiftClick) {
                event.cancelWithDebug("ArenaTokenExchangeMenu.handleMenuClick: shift_click_store")
                insertFromPlayerSlot(player, holder, event.slot, "shift_click")
            }
            return
        }
        event.cancelWithDebug("ArenaTokenExchangeMenu.handleMenuClick: menu_click")
        when (event.rawSlot) {
            ArenaTokenExchangeLayout.EXCHANGE_SLOT -> openConfirmation(player, holder, TokenExchangeMode.INPUT_SLOTS)
            ArenaTokenExchangeLayout.BULK_EXCHANGE_SLOT -> {
                playInputSound(player)
                openConfirmation(player, holder, TokenExchangeMode.ALL_INVENTORY)
            }
            ArenaTokenExchangeLayout.INFO_SLOT -> Unit
            in ArenaTokenExchangeLayout.INPUT_SLOTS -> {
                if (event.isShiftClick || event.cursor.type.isAir) {
                    returnInputSlot(player, holder, event.rawSlot, "manual_remove")
                } else {
                    insertFromCursor(player, holder, event.rawSlot, event.cursor, event.isRightClick)
                }
            }
        }
    }

    private fun handleConfirmClick(event: InventoryClickEvent, holder: ArenaTokenExchangeConfirmHolder) {
        val player = event.whoClicked as? Player ?: return
        event.cancelWithDebug("ArenaTokenExchangeMenu.handleConfirmClick: confirm_click")
        if (player.uniqueId != holder.ownerId || event.clickedInventory != holder.backingInventory) return
        when (event.rawSlot) {
            ArenaTokenExchangeLayout.CONFIRM_OK_SLOT -> {
                playInputSound(player)
                holder.handled = true
                executeConfirmedExchange(player, holder)
            }
            ArenaTokenExchangeLayout.CONFIRM_CANCEL_SLOT -> {
                playInputSound(player)
                holder.handled = true
                holder.sourceHolder.transitionToConfirm = false
                auditLogger.logTokenExchangeConfirmation(
                    player.uniqueId, player.name, "token_exchange_confirm_cancelled",
                    holder.mode.id, holder.quote.total.toDouble(), holder.quote.stacks, "cancel_button"
                )
                activeMenuPlayers += player.uniqueId
                syncTemporaryHints(player)
                render(player, holder.sourceHolder)
                ManagedMenuPresenter.open(
                    player,
                    holder.sourceHolder.backingInventory,
                    menuId = "arena_token_exchange",
                    policy = ManagedMenuPresenter.inputPolicy(ArenaTokenExchangeLayout.INPUT_SLOTS),
                )
            }
        }
    }

    private fun insertFromCursor(player: Player, holder: ArenaTokenExchangeHolder, slot: Int, cursor: ItemStack, one: Boolean) {
        if (!isExchangeable(cursor)) return
        val current = holder.backingInventory.getItem(slot)?.takeUnless { it.type.isAir }
        val cleanCursor = withoutTemporaryHint(cursor.clone())
        val movedAmount = if (one) 1 else cleanCursor.amount
        val moved = cleanCursor.clone().apply { amount = movedAmount }
        if (current != null) {
            if (!current.isSimilar(moved) || current.amount + moved.amount > current.maxStackSize) return
            val merged = current.clone().apply { amount += moved.amount }
            val escrowId = holder.escrowBySlot[slot] ?: return
            escrow.updateItem(escrowId, merged)
            holder.backingInventory.setItem(slot, merged)
            auditLogger.logTokenExchangeInputChange(player.uniqueId, player.name, "insert", slot, moved, "cursor_merge", escrowId)
        } else {
            val record = escrow.reserve(FEATURE_ID, player.uniqueId, player.name, VIEW_ID, slot, "cursor", null, moved)
            holder.escrowBySlot[slot] = record.id
            holder.backingInventory.setItem(slot, moved)
            auditLogger.logTokenExchangeInputChange(player.uniqueId, player.name, "insert", slot, moved, "cursor_place", record.id)
        }
        cursor.amount -= movedAmount
        player.setItemOnCursor(if (cursor.amount <= 0) null else cursor)
        playInputSound(player)
        render(player, holder)
    }

    private fun insertFromPlayerSlot(player: Player, holder: ArenaTokenExchangeHolder, playerSlot: Int, reason: String) {
        val raw = player.inventory.getItem(playerSlot)?.takeUnless { it.type.isAir } ?: return
        if (!isExchangeable(raw)) return
        val slot = ArenaTokenExchangeLayout.INPUT_SLOTS.firstOrNull { holder.backingInventory.getItem(it).isNullOrAir() } ?: return
        val moved = withoutTemporaryHint(raw.clone())
        val record = escrow.reserve(FEATURE_ID, player.uniqueId, player.name, VIEW_ID, slot, "player_inventory", playerSlot, moved)
        holder.escrowBySlot[slot] = record.id
        holder.backingInventory.setItem(slot, moved)
        player.inventory.setItem(playerSlot, null)
        auditLogger.logTokenExchangeInputChange(player.uniqueId, player.name, "insert", slot, moved, reason, record.id)
        playInputSound(player)
        syncTemporaryHints(player)
        render(player, holder)
    }

    private fun returnInputSlot(player: Player, holder: ArenaTokenExchangeHolder, slot: Int, reason: String) {
        val item = holder.backingInventory.getItem(slot)?.takeUnless { it.type.isAir } ?: return
        val clean = withoutTemporaryHint(item.clone())
        val leftovers = player.inventory.addItem(clean)
        val leftover = leftovers.values.firstOrNull()
        val escrowId = holder.escrowBySlot[slot]
        if (leftover == null) {
            escrowId?.let { escrow.resolve(it) }
            holder.escrowBySlot.remove(slot)
            auditLogger.logTokenExchangeInputChange(player.uniqueId, player.name, "return", slot, clean, reason, escrowId)
        } else {
            escrowId?.let { escrow.updateItem(it, leftover) }
            val returnedAmount = clean.amount - leftover.amount
            if (returnedAmount > 0) {
                auditLogger.logTokenExchangeInputChange(
                    player.uniqueId, player.name, "return_partial", slot,
                    clean.clone().apply { amount = returnedAmount }, reason, escrowId
                )
            }
        }
        holder.backingInventory.setItem(slot, null)
        syncTemporaryHints(player)
        render(player, holder)
    }

    private fun returnAllInputs(player: Player, holder: ArenaTokenExchangeHolder, reason: String) {
        ArenaTokenExchangeLayout.INPUT_SLOTS.forEach { slot -> returnInputSlot(player, holder, slot, reason) }
    }

    private fun recoverPendingItems(player: Player, reason: String) {
        escrow.pendingFor(player.uniqueId, FEATURE_ID).forEach { record ->
            val clean = withoutTemporaryHint(record.item.clone())
            val leftovers = player.inventory.addItem(clean)
            val leftover = leftovers.values.firstOrNull()
            if (leftover == null) {
                escrow.resolve(record.id)
                auditLogger.logTokenExchangeInputChange(
                    player.uniqueId, player.name, "return", record.virtualSlot, clean, reason, record.id
                )
            } else {
                escrow.updateItem(record.id, leftover)
                val returnedAmount = clean.amount - leftover.amount
                if (returnedAmount > 0) {
                    auditLogger.logTokenExchangeInputChange(
                        player.uniqueId, player.name, "return_partial", record.virtualSlot,
                        clean.clone().apply { amount = returnedAmount }, reason, record.id
                    )
                }
            }
        }
    }

    private fun openConfirmation(player: Player, sourceHolder: ArenaTokenExchangeHolder, mode: TokenExchangeMode) {
        val quote = quote(player, sourceHolder, mode) ?: return
        if (quote.lines.isEmpty()) return
        sourceHolder.transitionToConfirm = true
        activeMenuPlayers -= player.uniqueId
        removeTemporaryHints(player, "confirm_opened", false)
        val holder = ArenaTokenExchangeConfirmHolder(player.uniqueId, mode, quote, sourceHolder)
        val inventory = Bukkit.createInventory(holder, ArenaTokenExchangeLayout.MENU_SIZE, ArenaTokenExchangeLayout.CONFIRM_TITLE)
        holder.backingInventory = inventory
        renderConfirmation(player, holder)
        auditLogger.logTokenExchangeConfirmation(
            player.uniqueId, player.name, "token_exchange_confirm_opened",
            mode.id, quote.total.toDouble(), quote.stacks
        )
        ManagedMenuPresenter.open(player, inventory, menuId = "arena_token_exchange_confirm")
    }

    private fun executeConfirmedExchange(player: Player, holder: ArenaTokenExchangeConfirmHolder) {
        val sourceHolder = holder.sourceHolder
        val current = quote(player, sourceHolder, holder.mode)
        if (current == null || current.signature != holder.quote.signature) {
            auditLogger.logTokenExchangeConfirmation(
                player.uniqueId, player.name, "token_exchange_failed",
                holder.mode.id, holder.quote.total.toDouble(), holder.quote.stacks, "items_changed_after_confirmation"
            )
            holder.sourceHolder.transitionToConfirm = false
            activeMenuPlayers += player.uniqueId
            syncTemporaryHints(player)
            render(player, sourceHolder)
            ManagedMenuPresenter.open(
                player,
                sourceHolder.backingInventory,
                menuId = "arena_token_exchange",
                policy = ManagedMenuPresenter.inputPolicy(ArenaTokenExchangeLayout.INPUT_SLOTS),
            )
            return
        }
        val removedFromInput = removeInputItemsForExchange(sourceHolder)
        val inputSignature = linesForStacks(removedFromInput.map { it.item }).associate { it.categoryId to it.amount }
        val removedFromPlayer = if (holder.mode == TokenExchangeMode.ALL_INVENTORY) {
            removePlayerItemsForExchange(player, current.signature.mapValues { (categoryId, amount) ->
                amount - inputSignature.getOrDefault(categoryId, 0)
            })
        } else {
            emptyList()
        }
        val consumed = removedFromInput.map { it.item } + removedFromPlayer
        if (holder.mode == TokenExchangeMode.ALL_INVENTORY && linesForStacks(consumed).associate { it.categoryId to it.amount } != current.signature) {
            restoreConsumed(player, sourceHolder, removedFromInput, removedFromPlayer)
            auditLogger.logTokenExchangeConfirmation(
                player.uniqueId, player.name, "token_exchange_failed",
                holder.mode.id, current.total.toDouble(), current.stacks, "consume_mismatch"
            )
            closeExchangeFlow(player, sourceHolder, "consume_mismatch")
            ManagedMenuPresenter.close(player)
            return
        }
        if (!ContentEconomyBridge.deposit(plugin, player, current.total.toDouble())) {
            restoreConsumed(player, sourceHolder, removedFromInput, removedFromPlayer)
            auditLogger.logTokenExchangeConfirmation(
                player.uniqueId, player.name, "token_exchange_failed",
                holder.mode.id, current.total.toDouble(), current.stacks, "economy_deposit_failed"
            )
            player.sendMessage(ArenaI18n.text(player, "arena.messages.token_exchange.failed"))
            sourceHolder.transitionToConfirm = false
            activeMenuPlayers += player.uniqueId
            syncTemporaryHints(player)
            render(player, sourceHolder)
            ManagedMenuPresenter.open(
                player,
                sourceHolder.backingInventory,
                menuId = "arena_token_exchange",
                policy = ManagedMenuPresenter.inputPolicy(ArenaTokenExchangeLayout.INPUT_SLOTS),
            )
            return
        }
        sourceHolder.escrowBySlot.values.toList().forEach { escrow.resolve(it) }
        sourceHolder.escrowBySlot.clear()
        auditLogger.logTokenExchangeConfirmation(
            player.uniqueId, player.name, "token_exchange_completed",
            holder.mode.id, current.total.toDouble(), consumed
        )
        sourceHolder.transitionToConfirm = false
        activeMenuPlayers -= player.uniqueId
        removeTemporaryHints(player, "exchange_completed", false)
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f)
        ManagedMenuPresenter.close(player)
        player.sendMessage(ArenaI18n.text(player, "arena.messages.token_exchange.completed", "amount" to formatAcorn(current.total)))
    }

    private fun removeInputItemsForExchange(holder: ArenaTokenExchangeHolder): List<RemovedInputItem> {
        return ArenaTokenExchangeLayout.INPUT_SLOTS.mapNotNull { slot ->
            holder.backingInventory.getItem(slot)?.takeUnless { it.type.isAir }?.clone()?.let {
                holder.backingInventory.setItem(slot, null)
                RemovedInputItem(slot, it)
            }
        }
    }

    private fun removePlayerItemsForExchange(player: Player, required: Map<String, Int>): List<ItemStack> {
        val remaining = required.toMutableMap()
        val removed = mutableListOf<ItemStack>()
        for (slot in player.inventory.storageContents.indices) {
            val stack = player.inventory.getItem(slot)?.takeUnless { it.type.isAir } ?: continue
            val categoryId = ArenaMobTokenItem.readCategoryId(stack) ?: continue
            val wanted = remaining.getOrDefault(categoryId, 0)
            if (wanted <= 0) continue
            val clean = withoutTemporaryHint(stack.clone())
            val taking = minOf(clean.amount, wanted)
            removed += clean.clone().apply { amount = taking }
            if (taking == stack.amount) {
                player.inventory.setItem(slot, null)
            } else {
                stack.amount -= taking
                player.inventory.setItem(slot, stack)
            }
            remaining[categoryId] = wanted - taking
        }
        return removed
    }

    private fun restoreConsumed(
        player: Player,
        holder: ArenaTokenExchangeHolder,
        inputItems: List<RemovedInputItem>,
        playerItems: List<ItemStack>
    ) {
        inputItems.forEach { removed ->
            holder.backingInventory.setItem(removed.slot, removed.item)
        }
        playerItems.forEach { item ->
            val leftovers = player.inventory.addItem(item)
            leftovers.values.forEach { leftover -> player.world.dropItemNaturally(player.location, leftover) }
        }
    }

    private fun closeExchangeFlow(player: Player, holder: ArenaTokenExchangeHolder, reason: String) {
        holder.transitionToConfirm = false
        activeMenuPlayers -= player.uniqueId
        returnAllInputs(player, holder, reason)
        removeTemporaryHints(player, reason, false)
    }

    private fun quote(player: Player, holder: ArenaTokenExchangeHolder, mode: TokenExchangeMode): TokenExchangeQuote? {
        val stacks = inputStacks(holder.backingInventory).toMutableList()
        if (mode == TokenExchangeMode.ALL_INVENTORY) {
            player.inventory.storageContents.mapNotNullTo(stacks) { stack ->
                stack?.takeUnless { it.type.isAir || !isExchangeable(it) }?.let { withoutTemporaryHint(it.clone()) }
            }
        }
        val lines = linesForStacks(stacks)
        val total = lines.fold(ZERO) { sum, line -> truncate(sum + line.subtotal) }
        return TokenExchangeQuote(lines, total, stacks)
    }

    private fun inputStacks(inventory: Inventory): List<ItemStack> {
        return ArenaTokenExchangeLayout.INPUT_SLOTS.mapNotNull { slot ->
            inventory.getItem(slot)?.takeUnless { it.type.isAir }?.let { withoutTemporaryHint(it.clone()) }
        }
    }

    private fun linesForStacks(stacks: List<ItemStack>): List<TokenExchangeLine> {
        val counts = linkedMapOf<String, Int>()
        stacks.forEach { stack ->
            val categoryId = ArenaMobTokenItem.readCategoryId(stack) ?: return@forEach
            if (!rates.containsKey(categoryId)) return@forEach
            counts[categoryId] = counts.getOrDefault(categoryId, 0) + stack.amount
        }
        return counts.map { (categoryId, amount) ->
            val price = rates.getValue(categoryId)
            TokenExchangeLine(categoryId, amount, price, truncate(price * amount.toBigDecimal()))
        }
    }

    private fun isExchangeable(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return false
        val categoryId = ArenaMobTokenItem.readCategoryId(item) ?: return false
        return rates.containsKey(categoryId)
    }

    private fun render(player: Player, holder: ArenaTokenExchangeHolder) {
        val inventory = holder.backingInventory
        val framePane = ArenaMenuItems.backgroundPane(Material.BLACK_STAINED_GLASS_PANE)
        val middlePane = ArenaMenuItems.backgroundPane(Material.GRAY_STAINED_GLASS_PANE)
        for (slot in 0 until inventory.size) {
            if (slot !in ArenaTokenExchangeLayout.INPUT_SLOTS &&
                slot !in setOf(ArenaTokenExchangeLayout.BULK_EXCHANGE_SLOT, ArenaTokenExchangeLayout.EXCHANGE_SLOT, ArenaTokenExchangeLayout.INFO_SLOT)
            ) {
                inventory.setItem(slot, if (slot / 9 in 1..3) middlePane else framePane)
            }
        }
        inventory.setItem(
            ArenaTokenExchangeLayout.EXCHANGE_SLOT,
            buildActionButton(player, TokenExchangeMode.INPUT_SLOTS, linesForStacks(inputStacks(inventory)))
        )
        inventory.setItem(
            ArenaTokenExchangeLayout.BULK_EXCHANGE_SLOT,
            buildActionButton(player, TokenExchangeMode.ALL_INVENTORY, quote(player, holder, TokenExchangeMode.ALL_INVENTORY)!!.lines)
        )
        inventory.setItem(
            ArenaTokenExchangeLayout.INFO_SLOT,
            GuiMenuItems.icon(Material.BOOK, ArenaI18n.text(player, "arena.ui.token_exchange.info_name"), listOf(
                GuiLoreLine.Text(ArenaI18n.text(player, "arena.ui.token_exchange.info_lore"))
            ))
        )
    }

    private fun renderConfirmation(player: Player, holder: ArenaTokenExchangeConfirmHolder) {
        GuiMenuItems.fillFramed(holder.backingInventory)
        holder.backingInventory.setItem(
            ArenaTokenExchangeLayout.CONFIRM_PREVIEW_SLOT,
            buildActionButton(player, holder.mode, holder.quote.lines)
        )
        holder.backingInventory.setItem(
            ArenaTokenExchangeLayout.CONFIRM_OK_SLOT,
            GuiMenuItems.icon(Material.LIME_CONCRETE, ArenaI18n.text(player, "arena.ui.token_exchange.confirm_ok"))
        )
        holder.backingInventory.setItem(
            ArenaTokenExchangeLayout.CONFIRM_CANCEL_SLOT,
            GuiMenuItems.icon(Material.RED_CONCRETE, ArenaI18n.text(player, "arena.ui.token_exchange.confirm_cancel"))
        )
    }

    private fun buildActionButton(player: Player, mode: TokenExchangeMode, lines: List<TokenExchangeLine>): ItemStack {
        val item = ItemStack(mode.material)
        val meta = item.itemMeta ?: return item
        if (mode == TokenExchangeMode.INPUT_SLOTS && lines.isEmpty()) {
            item.itemMeta = meta
            return ArenaMenuItems.hideTooltip(item)
        }
        meta.setDisplayName(
            ArenaI18n.text(
                player,
                if (mode == TokenExchangeMode.INPUT_SLOTS) "arena.ui.token_exchange.exchange_name"
                else "arena.ui.token_exchange.bulk_exchange_name"
            )
        )
        if (lines.isNotEmpty()) {
            meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Rich(buildExchangeLore(player, lines), GuiLoreFrame.BOTH)))
        }
        ItemMetaCompat.hideAdditionalTooltip(meta)
        item.itemMeta = meta
        return item
    }

    private fun buildExchangeLore(player: Player, lines: List<TokenExchangeLine>): List<GuiLoreLine> {
        return buildList {
            add(GuiLoreLine.Text(ArenaI18n.text(player, "arena.ui.token_exchange.details_heading")))
            lines.take(4).forEach { line ->
                val name = CustomItemI18n.text(player, "custom_items.arena.mob_token.token_names.${line.categoryId}", line.categoryId)
                    .replace(Regex("§[0-9a-fk-or]", RegexOption.IGNORE_CASE), "")
                add(GuiLoreLine.SubData(name, ArenaI18n.text(
                    player,
                    "arena.ui.token_exchange.details_line",
                    "unit_price" to formatAcorn(line.unitPrice),
                    "amount" to line.amount,
                    "subtotal" to formatAcorn(line.subtotal)
                )))
            }
            if (lines.size > 4) {
                val remaining = lines.drop(4).fold(ZERO) { sum, line -> truncate(sum + line.subtotal) }
                add(GuiLoreLine.Data(ArenaI18n.text(player, "arena.ui.token_exchange.details_other"), formatAcorn(remaining), "§f"))
            }
        }
    }

    private fun syncTemporaryHints(player: Player) {
        player.inventory.storageContents.forEach { stack ->
            if (stack == null || stack.type.isAir) return@forEach
            removeTemporaryHint(stack)
            val categoryId = ArenaMobTokenItem.readCategoryId(stack) ?: return@forEach
            val price = rates[categoryId] ?: return@forEach
            val meta = stack.itemMeta ?: return@forEach
            val lore = meta.lore?.toMutableList() ?: mutableListOf()
            lore += "§a交換可能です §7( §a交換額 §7: ${formatAcorn(price)} §7)"
            meta.lore = lore
            meta.persistentDataContainer.set(hintKey, PersistentDataType.BYTE, 1)
            stack.itemMeta = meta
        }
    }

    private fun removeTemporaryHints(player: Player, reason: String, auditCleanup: Boolean) {
        player.inventory.storageContents.forEach { stack ->
            if (stack == null || stack.type.isAir) return@forEach
            if (removeTemporaryHint(stack) && auditCleanup) {
                auditLogger.logTokenExchangeCleanup(player.uniqueId, player.name, stack, reason)
            }
        }
        player.itemOnCursor.takeUnless { it.type.isAir }?.let { cursor ->
            if (removeTemporaryHint(cursor) && auditCleanup) {
                auditLogger.logTokenExchangeCleanup(player.uniqueId, player.name, cursor, reason)
            }
            player.setItemOnCursor(cursor)
        }
    }

    private fun removeTemporaryHint(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        if (!meta.persistentDataContainer.has(hintKey, PersistentDataType.BYTE)) return false
        val lore = meta.lore?.toMutableList() ?: mutableListOf()
        val index = lore.indexOfLast { it.startsWith("§a交換可能です ") }
        if (index >= 0) lore.removeAt(index)
        meta.lore = if (lore.isEmpty()) null else lore
        meta.persistentDataContainer.remove(hintKey)
        item.itemMeta = meta
        return true
    }

    private fun withoutTemporaryHint(item: ItemStack): ItemStack {
        removeTemporaryHint(item)
        return item
    }

    private fun maintainHints() {
        maintenanceTick++
        Bukkit.getOnlinePlayers().forEach { player ->
            if (activeMenuPlayers.contains(player.uniqueId)) {
                syncTemporaryHints(player)
            } else if (maintenanceTick % 20 == 0) {
                removeTemporaryHints(player, "periodic_cleanup", true)
            }
        }
    }

    private fun playInputSound(player: Player) {
        CCSystem.getAPI().getMenuSoundService().onMenuClick(player, "arena_token_exchange")
    }

    private fun blockedClick(click: ClickType): Boolean = click in setOf(
        ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND, ClickType.DOUBLE_CLICK, ClickType.DROP,
        ClickType.CONTROL_DROP, ClickType.CREATIVE, ClickType.MIDDLE
    )

    private fun truncate(value: Double): BigDecimal = BigDecimal.valueOf(value).setScale(2, RoundingMode.DOWN)
    private fun truncate(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.DOWN)
    private fun formatAcorn(value: BigDecimal): String = "§6🐿 §e${value.setScale(2, RoundingMode.DOWN).toPlainString()}"
    private fun ItemStack?.isNullOrAir(): Boolean = this == null || type.isAir
}
