package jp.awabi2048.cccontent.features.npc.shop

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import jp.awabi2048.cccontent.economy.ContentEconomyBridge
import jp.awabi2048.cccontent.gui.GuiMenuItems
import jp.awabi2048.cccontent.gui.MenuEventGuards
import jp.awabi2048.cccontent.gui.OwnedMenuHolder
import jp.awabi2048.cccontent.gui.StandardMenuLayouts
import jp.awabi2048.cccontent.util.ContentLocaleResolver
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class OageShrineShopMenuService(
    private val plugin: JavaPlugin,
    private val state: OageShrineShopState,
    private val backToParent: (Player) -> Unit = { it.closeInventory() }
) : Listener {
    private var tabs: List<OageShrineShopTabDefinition> = emptyList()

    fun reload() {
        tabs = OageShrineShopConfig.load(plugin).tabs
    }

    fun open(player: Player) {
        openShop(player, tabs.firstOrNull()?.tabId ?: return)
    }

    fun openShop(player: Player, tabId: String) {
        val tab = tabs.firstOrNull { it.tabId == tabId } ?: tabs.firstOrNull() ?: return
        val holder = ShopHolder(player.uniqueId, tab)
        val inventory = Bukkit.createInventory(holder, SIZE, legacy(text(player, "title.shop")))
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
    }

    fun reopenShop(player: Player, tabId: String) {
        openShop(player, tabId)
    }

    fun openPurchaseConfirm(player: Player, resolved: OageShrineShopResolvedItem) {
        val holder = ConfirmHolder(player.uniqueId, resolved)
        val inventory = Bukkit.createInventory(holder, CONFIRM_SIZE, legacy(text(player, "confirm.title")))
        holder.backingInventory = inventory
        applyConfirmFrame(inventory)
        inventory.setItem(CONFIRM_PREVIEW_SLOT, resolved.previewItem)
        inventory.setItem(CONFIRM_CONFIRM_SLOT, confirmButton(player, resolved))
        inventory.setItem(CONFIRM_CANCEL_SLOT, cancelButton(player))
        player.openInventory(inventory)
    }

    fun confirmPurchase(player: Player, resolved: OageShrineShopResolvedItem) {
        if (ContentEconomyBridge.get(plugin) == null) {
            playError(player)
            player.sendMessage(text(player, "messages.no_economy"))
            return
        }

        if (state.canPurchase(player.uniqueId, resolved.tab, resolved.item) != PurchaseLimitState.ALLOWED) {
            playError(player)
            player.sendMessage(text(player, "messages.limit_reached"))
            return
        }

        if (!ContentEconomyBridge.has(plugin, player, resolved.item.price)) {
            playError(player)
            player.sendMessage(text(player, "messages.insufficient_funds"))
            return
        }

        if (!canAcceptReward(player, resolved.purchaseItem)) {
            playError(player)
            player.sendMessage(text(player, "messages.inventory_full"))
            return
        }

        if (!ContentEconomyBridge.withdraw(plugin, player, resolved.item.price)) {
            playError(player)
            player.sendMessage(text(player, "messages.no_economy"))
            return
        }

        val leftovers = player.inventory.addItem(resolved.purchaseItem.clone())
        if (leftovers.isNotEmpty()) {
            ContentEconomyBridge.deposit(plugin, player, resolved.item.price)
            playError(player)
            player.sendMessage(text(player, "messages.inventory_full"))
            return
        }

        state.recordPurchase(player.uniqueId, resolved.tab, resolved.item)
        playClick(player)
        player.closeInventory()
        player.sendMessage(text(player, "messages.purchase_complete", "name" to resolved.displayName, "price" to ContentEconomyBridge.formatPrice(resolved.item.price)))
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        when (val holder = event.view.topInventory.holder) {
            is ShopHolder -> {
                val player = MenuEventGuards.ownedTopClick(event, holder, "OageShrineShopMenuService.onInventoryClick: shop_click") ?: return

                when (event.rawSlot) {
                    SHOP_BACK_SLOT -> {
                        playClick(player)
                        backToParent(player)
                    }
                    in TAB_SLOTS -> {
                        val index = TAB_SLOTS.indexOf(event.rawSlot)
                        val target = tabs.getOrNull(index) ?: return
                        playClick(player)
                        openShop(player, target.tabId)
                    }
                    else -> {
                        val resolved = holder.items[event.rawSlot] ?: return
                        openPurchaseConfirm(player, resolved)
                    }
                }
            }
            is ConfirmHolder -> {
                val player = MenuEventGuards.ownedTopClick(event, holder, "OageShrineShopMenuService.onInventoryClick: confirm_click") ?: return
                when (event.rawSlot) {
                    CONFIRM_CONFIRM_SLOT -> confirmPurchase(player, holder.resolved)
                    CONFIRM_CANCEL_SLOT -> {
                        playClick(player)
                        openShop(player, holder.resolved.tab.tabId)
                    }
                }
            }
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        when (val holder = event.view.topInventory.holder) {
            is ShopHolder -> MenuEventGuards.cancelOwnedTopDrag(event, holder, "OageShrineShopMenuService.onInventoryDrag: shop_drag")
            is ConfirmHolder -> MenuEventGuards.cancelOwnedTopDrag(event, holder, "OageShrineShopMenuService.onInventoryDrag: confirm_drag")
        }
    }

    private fun render(player: Player, holder: ShopHolder, inventory: Inventory) {
        GuiMenuItems.fillFramed(inventory)
        inventory.setItem(4, icon(player, "main.shop", Material.CHEST))
        inventory.setItem(SHOP_BACK_SLOT, backButton(player))
        renderTabs(player, holder.tab, inventory)

        val resolvedItems = holder.tab.items.mapNotNull { resolveItem(player, holder.tab, it) }.take(CONTENT_SLOTS.size)
        holder.items = CONTENT_SLOTS.zip(resolvedItems).associate { (slot, item) -> slot to item }
        CONTENT_SLOTS.forEach { slot ->
            inventory.setItem(slot, holder.items[slot]?.previewItem ?: emptyShopItem())
        }
    }

    private fun renderTabs(player: Player, activeTab: OageShrineShopTabDefinition, inventory: Inventory) {
        TAB_SLOTS.forEachIndexed { index, slot ->
            val tab = tabs.getOrNull(index) ?: return@forEachIndexed
            val key = if (tab.tabId == activeTab.tabId) "shop.tab.${tab.tabId}.active" else "shop.tab.${tab.tabId}"
            inventory.setItem(slot, icon(player, key, Material.PLAYER_HEAD))
        }
    }

    private fun resolveItem(player: Player, tab: OageShrineShopTabDefinition, item: OageShrineShopItemDefinition): OageShrineShopResolvedItem? {
        val sourceItem = item.source.resolve(plugin, player) ?: return null
        val purchaseItem = sourceItem.clone()
        val purchaseMeta = purchaseItem.itemMeta ?: return null
        val displayName = displayNameFrom(sourceItem) ?: item.displayNameOverride ?: "§f${item.goodsId}"
        purchaseMeta.displayName(legacy(displayName))
        val fixedLore = list(player, "item.head_lore")
        purchaseMeta.lore(buildShopLore(fixedLore, item.price, tab, item))
        purchaseMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        purchaseItem.itemMeta = purchaseMeta

        val previewItem = purchaseItem.clone()
        val previewMeta = previewItem.itemMeta ?: return null
        previewMeta.lore(buildShopLore(fixedLore, item.price, tab, item))
        previewItem.itemMeta = previewMeta

        return OageShrineShopResolvedItem(tab, item, purchaseItem, previewItem, displayName, fixedLore)
    }

    private fun buildShopLore(headLore: List<String>, price: Double, tab: OageShrineShopTabDefinition, item: OageShrineShopItemDefinition): List<Component> {
        val costLine = "§f❙ §7初穂料 ${ContentEconomyBridge.formatAcorn(price)}"
        val limit = item.purchaseLimitDaily ?: tab.purchaseLimitDaily ?: item.purchaseLimitWeekly ?: tab.purchaseLimitWeekly
        val limitLine = limit?.let { "§f❙ §7交換可能個数 §b${it}個" }
        return CCSystem.getAPI().getLoreService().render(
            GuiLoreSpec.Blocks(listOf(
                GuiLoreBlock(headLore.map(GuiLoreLine::Raw)),
                GuiLoreBlock(buildList {
                    add(GuiLoreLine.Raw(costLine))
                    if (limitLine != null) add(GuiLoreLine.Raw(limitLine))
                })
            ))
        )
    }

    private fun confirmButton(player: Player, resolved: OageShrineShopResolvedItem): ItemStack {
        return GuiMenuItems.icon(
            Material.LIME_CONCRETE,
            text(player, "confirm.confirm_button.name"),
            list(player, "confirm.confirm_button.lore") + listOf(
                "§7${resolved.displayName}",
                "§7§f❙ §7初穂料 ${ContentEconomyBridge.formatAcorn(resolved.item.price)}"
            )
        )
    }

    private fun cancelButton(player: Player): ItemStack {
        return GuiMenuItems.icon(Material.RED_CONCRETE, text(player, "confirm.cancel_button.name"), list(player, "confirm.cancel_button.lore"))
    }

    private fun applyConfirmFrame(inventory: Inventory) {
        StandardMenuLayouts.applyStandardFrame(inventory)
    }

    private fun emptyShopItem(): ItemStack = GuiMenuItems.backgroundPane(Material.WHITE_STAINED_GLASS_PANE)

    private fun icon(player: Player, key: String, material: Material): ItemStack =
        GuiMenuItems.icon(material, text(player, "$key.name"), list(player, "$key.lore"))

    private fun backButton(player: Player): ItemStack =
        GuiMenuItems.backButton(text(player, "back.name"), list(player, "back.lore"))

    private fun text(player: Player, key: String, vararg placeholders: Pair<String, Any?>): String {
        val locale = ContentLocaleResolver.resolve(player)
        return CCSystem.getAPI().getI18nString(locale, "gui.npc.oage_shrine.$key", placeholdersMap(*placeholders)).replace('&', '§')
    }

    private fun list(player: Player, key: String, vararg placeholders: Pair<String, Any?>): List<String> {
        val locale = ContentLocaleResolver.resolve(player)
        return CCSystem.getAPI().getI18nStringList(locale, "gui.npc.oage_shrine.$key", placeholdersMap(*placeholders)).map { it.replace('&', '§') }
    }

    private fun placeholdersMap(vararg placeholders: Pair<String, Any?>): Map<String, Any> {
        return placeholders.associate { (key, value) -> key to (value ?: "null") }
    }

    private fun playClick(player: Player) {
        player.playSound(player.location, "minecraft:ui.button.click", 0.7f, 1.6f)
    }

    private fun playError(player: Player) {
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f)
    }

    private fun canAcceptReward(player: Player, item: ItemStack): Boolean {
        if (player.inventory.firstEmpty() >= 0) return true
        return player.inventory.storageContents.filterNotNull().any { existing -> existing.isSimilar(item) && existing.amount + item.amount <= existing.maxStackSize }
    }

    private fun legacy(text: String): Component = LegacyComponentSerializer.legacySection().deserialize(text).decoration(TextDecoration.ITALIC, false)

    private fun displayNameFrom(item: ItemStack): String? = item.itemMeta?.displayName()?.let { LegacyComponentSerializer.legacySection().serialize(it) }

    private class ShopHolder(
        ownerId: UUID,
        val tab: OageShrineShopTabDefinition
    ) : OwnedMenuHolder(ownerId) {
        var items: Map<Int, OageShrineShopResolvedItem> = emptyMap()
    }

    private class ConfirmHolder(
        ownerId: UUID,
        val resolved: OageShrineShopResolvedItem
    ) : OwnedMenuHolder(ownerId)

    companion object {
        const val SIZE = StandardMenuLayouts.SIZE_54
        const val CONFIRM_SIZE = StandardMenuLayouts.SIZE_45
        const val SHOP_BACK_SLOT = StandardMenuLayouts.FOOTER_LEFT_SLOT_54
        val CONFIRM_PREVIEW_SLOT = StandardMenuLayouts.CONFIRM_45.previewSlot
        val CONFIRM_CONFIRM_SLOT = StandardMenuLayouts.CONFIRM_45.confirmSlot
        val CONFIRM_CANCEL_SLOT = StandardMenuLayouts.CONFIRM_45.cancelSlot
        val TAB_SLOTS = listOf(47, 48)
        val CONTENT_SLOTS = (19..25).toList() + (28..34).toList()
    }
}
