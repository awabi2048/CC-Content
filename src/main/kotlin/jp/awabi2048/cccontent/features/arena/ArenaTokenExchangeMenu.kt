@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.arena

import jp.awabi2048.cccontent.economy.ContentEconomyBridge
import jp.awabi2048.cccontent.items.CustomItemI18n
import jp.awabi2048.cccontent.items.arena.ArenaMobTokenItem
import jp.awabi2048.cccontent.util.ItemMetaCompat
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
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
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

private object ArenaTokenExchangeLayout {
    const val MENU_SIZE = 45
    const val EXCHANGE_SLOT = 40
    val INPUT_SLOTS: Set<Int> = (10..16).toSet() + (19..25).toSet() + (28..34).toSet()
    val TITLE: String
        get() = ArenaI18n.text(null, "arena.ui.token_exchange.title")
}

private class ArenaTokenExchangeHolder(val ownerId: UUID) : InventoryHolder {
    var backingInventory: Inventory? = null

    override fun getInventory(): Inventory {
        return backingInventory ?: Bukkit.createInventory(this, ArenaTokenExchangeLayout.MENU_SIZE, ArenaTokenExchangeLayout.TITLE)
    }
}

private data class TokenExchangeLine(
    val categoryId: String,
    val amount: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal
)

class ArenaTokenExchangeMenu(private val plugin: JavaPlugin) : Listener {
    private companion object {
        const val CONFIG_PATH = "config/arena/token_exchange.yml"
        val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.DOWN)
        val REQUIRED_RATE_CATEGORY_IDS = setOf(
            "skeleton", "zombie", "drowned", "spider", "husk", "bogged", "guardian",
            "wither_skeleton", "blaze", "slime", "silverfish", "spirit", "magma_cube",
            "witch", "iron_golem", "elder_guardian", "frog", "enderman", "shulker",
            "endermite", "bat", "creeper", "boomerang"
        )
    }

    private var rates: Map<String, BigDecimal> = emptyMap()

    fun initialize() {
        reload()
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
        val holder = ArenaTokenExchangeHolder(player.uniqueId)
        val inventory = Bukkit.createInventory(holder, ArenaTokenExchangeLayout.MENU_SIZE, ArenaTokenExchangeLayout.TITLE)
        holder.backingInventory = inventory
        render(player, inventory)
        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val holder = top.holder as? ArenaTokenExchangeHolder ?: return
        val player = event.whoClicked as? Player ?: return
        if (player.uniqueId != holder.ownerId) {
            event.isCancelled = true
            return
        }
        if (event.click in setOf(ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND, ClickType.DOUBLE_CLICK, ClickType.DROP, ClickType.CONTROL_DROP, ClickType.CREATIVE, ClickType.MIDDLE)) {
            event.isCancelled = true
            return
        }

        val clickedTop = event.clickedInventory == top
        if (event.isShiftClick) {
            event.isCancelled = true
            if (!clickedTop) {
                moveTokenStackIntoInput(event, player, top)
            } else if (event.rawSlot in ArenaTokenExchangeLayout.INPUT_SLOTS) {
                moveInputStackToPlayer(player, top, event.rawSlot)
            }
            return
        }

        if (!clickedTop) {
            return
        }
        if (event.rawSlot == ArenaTokenExchangeLayout.EXCHANGE_SLOT) {
            event.isCancelled = true
            exchange(player, top)
            return
        }
        if (event.rawSlot !in ArenaTokenExchangeLayout.INPUT_SLOTS) {
            event.isCancelled = true
            return
        }

        if (event.action in setOf(InventoryAction.HOTBAR_SWAP, InventoryAction.HOTBAR_MOVE_AND_READD, InventoryAction.COLLECT_TO_CURSOR, InventoryAction.CLONE_STACK)) {
            event.isCancelled = true
            return
        }
        val inserting = event.action in setOf(
            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_ONE,
            InventoryAction.PLACE_SOME,
            InventoryAction.SWAP_WITH_CURSOR
        )
        if (inserting && !isExchangeable(event.cursor)) {
            event.isCancelled = true
            return
        }
        if (inserting) {
            playInputSound(player)
        }
        scheduleRender(player, top)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val top = event.view.topInventory
        val holder = top.holder as? ArenaTokenExchangeHolder ?: return
        val player = event.whoClicked as? Player ?: return
        if (player.uniqueId != holder.ownerId) {
            event.isCancelled = true
            return
        }
        val topSlots = event.rawSlots.filter { it < top.size }
        if (topSlots.isEmpty()) return
        if (topSlots.any { it !in ArenaTokenExchangeLayout.INPUT_SLOTS } || !isExchangeable(event.oldCursor)) {
            event.isCancelled = true
            return
        }
        playInputSound(player)
        scheduleRender(player, top)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.view.topInventory.holder as? ArenaTokenExchangeHolder ?: return
        val player = event.player as? Player ?: return
        if (player.uniqueId != holder.ownerId) return
        ArenaTokenExchangeLayout.INPUT_SLOTS.forEach { slot ->
            moveInputStackToPlayer(player, event.inventory, slot)
        }
    }

    private fun moveTokenStackIntoInput(event: InventoryClickEvent, player: Player, top: Inventory) {
        val moving = event.currentItem?.takeUnless { it.type.isAir } ?: return
        if (!isExchangeable(moving)) return
        val slot = ArenaTokenExchangeLayout.INPUT_SLOTS.firstOrNull { top.getItem(it).isNullOrAir() } ?: return
        top.setItem(slot, moving.clone())
        event.clickedInventory?.setItem(event.slot, null)
        playInputSound(player)
        render(player, top)
    }

    private fun moveInputStackToPlayer(player: Player, inventory: Inventory, slot: Int) {
        val stack = inventory.getItem(slot)?.takeUnless { it.type.isAir } ?: return
        inventory.setItem(slot, null)
        val leftovers = player.inventory.addItem(stack)
        leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
        render(player, inventory)
    }

    private fun exchange(player: Player, inventory: Inventory) {
        val lines = evaluate(inventory) ?: return
        if (lines.isEmpty()) return
        val total = lines.fold(ZERO) { sum, line -> truncate(sum + line.subtotal) }
        if (total <= ZERO || !ContentEconomyBridge.deposit(plugin, player, total.toDouble())) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.token_exchange.failed"))
            return
        }
        ArenaTokenExchangeLayout.INPUT_SLOTS.forEach { inventory.setItem(it, null) }
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f)
        player.closeInventory()
        player.sendMessage(ArenaI18n.text(player, "arena.messages.token_exchange.completed", "amount" to formatAcorn(total)))
    }

    private fun evaluate(inventory: Inventory): List<TokenExchangeLine>? {
        val counts = linkedMapOf<String, Int>()
        ArenaTokenExchangeLayout.INPUT_SLOTS.forEach { slot ->
            val stack = inventory.getItem(slot)?.takeUnless { it.type.isAir } ?: return@forEach
            val categoryId = ArenaMobTokenItem.readCategoryId(stack) ?: return null
            if (rates[categoryId] == null) return null
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

    private fun render(player: Player, inventory: Inventory) {
        val framePane = ArenaMenuItems.backgroundPane(Material.BLACK_STAINED_GLASS_PANE)
        val middlePane = ArenaMenuItems.backgroundPane(Material.GRAY_STAINED_GLASS_PANE)
        for (slot in 0 until inventory.size) {
            if (slot !in ArenaTokenExchangeLayout.INPUT_SLOTS && slot != ArenaTokenExchangeLayout.EXCHANGE_SLOT) {
                inventory.setItem(slot, if (slot / 9 in 1..3) middlePane else framePane)
            }
        }
        inventory.setItem(ArenaTokenExchangeLayout.EXCHANGE_SLOT, buildExchangeButton(player, evaluate(inventory).orEmpty()))
    }

    private fun buildExchangeButton(player: Player, lines: List<TokenExchangeLine>): ItemStack {
        val item = ItemStack(Material.BUNDLE)
        val meta = item.itemMeta ?: return item
        if (lines.isEmpty()) {
            meta.setDisplayName(ArenaI18n.text(player, "arena.ui.token_exchange.empty_name"))
            ItemMetaCompat.hideAdditionalTooltip(meta)
            item.itemMeta = meta
            return item
        }
        meta.setDisplayName(ArenaI18n.text(player, "arena.ui.token_exchange.exchange_name"))
        val separator = ArenaI18n.text(player, "arena.ui.separator")
        val lore = mutableListOf(separator, "§f❙ §7交換してもらうモブドロップ")
        lines.take(4).forEach { line ->
            val name = CustomItemI18n.text(player, "custom_items.arena.mob_token.token_names.${line.categoryId}", line.categoryId)
                .replace(Regex("§[0-9a-fk-or]", RegexOption.IGNORE_CASE), "")
            lore += "§8・§f$name ${formatAcorn(line.unitPrice)} §7× §b${line.amount} 個 §7= ${formatAcorn(line.subtotal)}"
        }
        if (lines.size > 4) {
            val remaining = lines.drop(4).fold(ZERO) { sum, line -> truncate(sum + line.subtotal) }
            lore += "§fその他 ${formatAcorn(remaining)}"
        }
        lore += separator
        meta.lore = lore
        ItemMetaCompat.hideAdditionalTooltip(meta)
        item.itemMeta = meta
        return item
    }

    private fun playInputSound(player: Player) {
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 2.0f)
    }

    private fun scheduleRender(player: Player, inventory: Inventory) {
        Bukkit.getScheduler().runTask(plugin, Runnable { render(player, inventory) })
    }

    private fun truncate(value: Double): BigDecimal = BigDecimal.valueOf(value).setScale(2, RoundingMode.DOWN)

    private fun truncate(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.DOWN)

    private fun formatAcorn(value: BigDecimal): String = "§6🐿 §e${value.setScale(2, RoundingMode.DOWN).toPlainString()}"

    private fun ItemStack?.isNullOrAir(): Boolean = this == null || type.isAir
}
