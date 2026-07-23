package jp.awabi2048.cccontent.features.catalog

import jp.awabi2048.cccontent.gui.ManagedMenuPresenter

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuIconData
import com.awabi2048.ccsystem.api.gui.GuiMenuIconAction
import com.awabi2048.ccsystem.api.gui.GuiMenuIconSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import jp.awabi2048.cccontent.features.fishing.FishQuality
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.UUID

data class CatalogCondition(
    val labelKey: String,
    val localizedValues: List<String> = emptyList(),
    val rawValue: String? = null,
    val hintKey: String? = null
)

data class CatalogItem(
    val id: String,
    val material: Material,
    val conditions: List<CatalogCondition> = emptyList()
)

// 料理・醸造の本文36スロットを基準に、表示ページと要求ページを正規化する。
fun catalogTotalPages(itemCount: Int, itemsPerPage: Int = 36): Int {
    require(itemsPerPage > 0) { "itemsPerPage must be positive" }
    return maxOf(1, (itemCount.coerceAtLeast(0) + itemsPerPage - 1) / itemsPerPage)
}

fun catalogPageFor(requestedPage: Int, totalPages: Int): Int {
    require(totalPages > 0) { "totalPages must be positive" }
    return requestedPage.coerceIn(0, totalPages - 1)
}

class CatalogCommand(
    private val store: CatalogStore,
    private val items: (CatalogType) -> List<CatalogItem>,
    private val isAvailable: (CatalogType) -> Boolean,
    private val fishingSearchTarget: (UUID) -> String? = { null },
    private val setFishingSearchTarget: (Player, String?) -> Unit = { _, _ -> },
    private val openFishingJournal: (Player) -> Unit = {}
) : CommandExecutor, Listener {
    fun openFromPublicRoute(player: Player, arguments: Map<String, String>): Boolean {
        val type = arguments["type"]
            ?.lowercase()
            ?.let { value -> CatalogType.entries.firstOrNull { it.id == value } }
            ?: CatalogType.FISHING
        val page = arguments["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        if (type == CatalogType.FISHING && !player.hasPermission("cc-content.admin")) {
            player.sendMessage(Component.text(text(player, "fishing.dictionary.item_required")))
            return false
        }
        open(player, type, page)
        return true
    }

    fun open(player: Player, type: CatalogType, requestedPage: Int = 0) {
        if (!isAvailable(type)) {
            val featureId = type.id
            player.sendMessage(Component.text(text(player, "content_management.feature_unavailable", "feature" to text(player, "content_management.feature.$featureId"))))
            return
        }
        val definitions = items(type)
        if (definitions.isEmpty()) {
            player.sendMessage(Component.text(text(player, "catalog.unavailable")))
            return
        }
        if (type == CatalogType.FISHING) {
            openFishing(player, definitions, requestedPage)
            return
        }
        val layoutService = CCSystem.getAPI().getGuiLayoutService()
        val layout = layoutService.pagedList54()
        val totalPages = catalogTotalPages(definitions.size, layout.itemSlots.size)
        val page = catalogPageFor(requestedPage, totalPages)
        val title = text(player, "${type.id}.catalog.title", "page" to (page + 1), "pages" to totalPages)
        val holder = CatalogHolder(player.uniqueId, type, page, totalPages)
        val inventory = Bukkit.createInventory(holder, 54, Component.text(title))
        holder.backingInventory = inventory
        layoutService.applyStandardFrame(inventory)
        val entries = store.entries(player.uniqueId, type)
        definitions.drop(page * layout.itemSlots.size).take(layout.itemSlots.size).forEachIndexed { index, definition ->
            val entry = entries[definition.id]
            inventory.setItem(layout.itemSlots[index], contentItem(player, type, definition, entry))
        }
        if (page > 0) inventory.setItem(layout.previousPageSlot, navigationItem(layoutServiceElement(), Material.ARROW, text(player, "catalog.previous")))
        if (page + 1 < totalPages) inventory.setItem(layout.nextPageSlot, navigationItem(layoutServiceElement(), Material.ARROW, text(player, "catalog.next")))
        inventory.setItem(layout.backSlot, navigationItem(layoutServiceElement(), Material.BARRIER, text(player, "catalog.close"), GuiElementRole.CANCEL))
        inventory.setItem(layout.infoSlot, pageInfoItem(layoutServiceElement(), player, page, totalPages))
        ManagedMenuPresenter.open(player, inventory)
    }

    private fun openFishing(player: Player, definitions: List<CatalogItem>, requestedPage: Int) {
        val layoutService = CCSystem.getAPI().getGuiLayoutService()
        val layout = layoutService.sevenColumnList(21)
        val totalPages = maxOf(1, (definitions.size + layout.itemsPerPage - 1) / layout.itemsPerPage)
        val page = requestedPage.coerceIn(0, totalPages - 1)
        val holder = CatalogHolder(player.uniqueId, CatalogType.FISHING, page, totalPages)
        val inventory = Bukkit.createInventory(holder, layout.size, Component.text(text(player, "fishing.fishdex.title", "page" to (page + 1), "pages" to totalPages)))
        holder.backingInventory = inventory
        layoutService.applyStandardFrame(inventory)
        val entries = store.entries(player.uniqueId, CatalogType.FISHING)
        definitions.drop(page * layout.itemsPerPage).take(layout.itemsPerPage).forEachIndexed { index, definition ->
            val slot = layout.itemSlots[index]
            holder.itemIdsBySlot[slot] = definition.id
            inventory.setItem(slot, fishingItem(player, definition, entries[definition.id]))
        }
        val elementService = CCSystem.getAPI().getGuiElementService()
        if (page > 0) inventory.setItem(layout.previousPageSlot, navigationItem(elementService, Material.ARROW, text(player, "fishing.fishdex.previous")))
        if (page + 1 < totalPages) inventory.setItem(layout.nextPageSlot, navigationItem(elementService, Material.ARROW, text(player, "fishing.fishdex.next")))
        inventory.setItem(layout.actionSlot, elementService.menuIcon(GuiMenuIconSpec(
                material = Material.WRITABLE_BOOK,
                name = GuiNameSpec.Text(text(player, "fishing.journal.tab"), GuiNameStyle.DEFAULT),
                role = GuiElementRole.NAVIGATION,
                amount = 1,
                description = emptyList(),
                data = emptyList(),
                options = emptyList(),
                warnings = emptyList(),
                dangers = emptyList(),
                actions = listOf(GuiMenuIconAction(
                    text(player, "fishing.journal.tab_operation"),
                    text(player, "fishing.journal.tab_action"),
                    text(player, "fishing.journal.tab_resolved"),
                    true
                )),
                glint = null
            )))
        ManagedMenuPresenter.open(player, inventory)
    }

    private fun layoutServiceElement() = CCSystem.getAPI().getGuiElementService()

    private fun contentItem(player: Player, type: CatalogType, definition: CatalogItem, entry: CatalogEntry?): ItemStack {
        val service = layoutServiceElement()
        val discovered = entry?.discovered == true
        val visibleEntry = entry ?: CatalogEntry(definition.id)
        val data = buildList {
            add(GuiMenuIconData(text(player, "catalog.data.obtained"), if (discovered) visibleEntry.obtainedCount else 0L, "§f"))
            if (type == CatalogType.BREWERY) {
                add(GuiMenuIconData(text(player, "catalog.data.drunk"), if (discovered) visibleEntry.drunkCount else 0L, "§f"))
                add(GuiMenuIconData(text(player, "catalog.data.quality"), if (discovered) visibleEntry.bestQuality else 0.0, "§f"))
            }
            add(GuiMenuIconData(text(player, "catalog.data.completion"), if (discovered) visibleEntry.bestCompletion else 0, "§f"))
        }
        val nameKey = when (type) {
            CatalogType.BREWERY -> "brewery.recipe.${definition.id}.name"
            CatalogType.COOKING -> "cooking.recipe.${definition.id}"
            CatalogType.FISHING -> "fishing.catalog.item.${definition.id}"
        }
        return service.menuIcon(GuiMenuIconSpec(
            material = if (discovered) definition.material else Material.GRAY_DYE,
            name = GuiNameSpec.Text(text(player, nameKey), GuiNameStyle.DEFAULT),
            role = GuiElementRole.CONTENT,
            amount = 1,
            description = emptyList(),
            data = data,
            options = emptyList(),
            warnings = if (discovered) emptyList() else listOf(text(player, "catalog.unknown")),
            dangers = emptyList(),
            actions = emptyList(),
            glint = null
        ))
    }

    private fun pageInfoItem(service: com.awabi2048.ccsystem.api.gui.GuiElementService, player: Player, page: Int, totalPages: Int): ItemStack =
        service.menuIcon(GuiMenuIconSpec(Material.PAPER, GuiNameSpec.Text(text(player, "catalog.page_info"), GuiNameStyle.DEFAULT), GuiElementRole.CONTENT, 1, emptyList(), listOf(GuiMenuIconData(text(player, "catalog.page_label"), "${page + 1}/$totalPages", "§f")), emptyList(), emptyList(), emptyList(), emptyList(), null))

    private fun navigationItem(service: com.awabi2048.ccsystem.api.gui.GuiElementService, material: Material, name: String, role: GuiElementRole = GuiElementRole.NAVIGATION): ItemStack =
        service.item(GuiItemSpec(material, GuiNameSpec.Text(name, GuiNameStyle.DEFAULT), com.awabi2048.ccsystem.api.gui.GuiLoreSpec.None, role, 1))

    private fun fishingItem(player: Player, definition: CatalogItem, entry: CatalogEntry?): ItemStack {
        val service = CCSystem.getAPI().getGuiElementService()
        val discovered = entry?.discovered == true
        val visibleEntry = entry ?: CatalogEntry(definition.id)
        val data = if (discovered) buildList {
            val qualityCounts = FishQuality.normalizeStoredCounts(visibleEntry.qualityCounts)
            add(GuiMenuIconData(text(player, "fishing.fishdex.total"), visibleEntry.obtainedCount, "§f"))
            val best = FishQuality.entries.lastOrNull { (qualityCounts[it] ?: 0L) > 0L } ?: FishQuality.COMMON
            add(GuiMenuIconData(text(player, "fishing.fishdex.best_quality"), best.stars, "§f"))
            val level = definition.conditions.firstOrNull { it.labelKey == "fishing.dictionary.condition.level" }?.rawValue ?: "1"
            add(GuiMenuIconData(text(player, "fishing.dictionary.condition.level"), level, "§f"))
        } else emptyList()
        val selected = fishingSearchTarget(player.uniqueId) == definition.id
        return service.menuIcon(GuiMenuIconSpec(
            material = if (discovered) definition.material else Material.GRAY_DYE,
            name = GuiNameSpec.Text(text(player, "fishing.catalog.item.${definition.id}"), GuiNameStyle.DEFAULT),
            role = GuiElementRole.CONTENT,
            amount = 1,
            description = if (discovered) listOf(text(player, "fishing.dictionary.description.${definition.id}")) else emptyList(),
            data = data,
            options = emptyList(),
            warnings = if (discovered) emptyList() else listOf(text(player, "fishing.dictionary.details_hidden")),
            dangers = emptyList(),
            actions = if (discovered) buildList {
                add(GuiMenuIconAction(
                    text(player, "fishing.dictionary.detail.operation"),
                    text(player, "fishing.dictionary.detail.action"),
                    text(player, "fishing.dictionary.detail.resolved"),
                    true
                ))
                add(if (selected) GuiMenuIconAction(
                    text(player, "fishing.dictionary.search.clear_operation"),
                    text(player, "fishing.dictionary.search.clear_action"),
                    text(player, "fishing.dictionary.search.clear_resolved"),
                    true
                ) else GuiMenuIconAction(
                    text(player, "fishing.dictionary.search.operation"),
                    text(player, "fishing.dictionary.search.action"),
                    text(player, "fishing.dictionary.search.resolved"),
                    true
                ))
            } else emptyList(),
            glint = selected
        ))
    }

    private fun openFishingDetail(player: Player, fishId: String, returnPage: Int) {
        val definition = items(CatalogType.FISHING).firstOrNull { it.id == fishId } ?: return
        val entry = store.entries(player.uniqueId, CatalogType.FISHING)[fishId] ?: return
        val layoutService = CCSystem.getAPI().getGuiLayoutService()
        val layout = layoutService.threeChoice45()
        val holder = FishingDetailHolder(player.uniqueId, returnPage)
        val inventory = Bukkit.createInventory(holder, layout.size, Component.text(text(player, "fishing.detail.title")))
        holder.backingInventory = inventory
        layoutService.applyStandardFrame(inventory)
        val service = CCSystem.getAPI().getGuiElementService()
        inventory.setItem(4, service.menuIcon(GuiMenuIconSpec(
            definition.material, GuiNameSpec.Text(text(player, "fishing.catalog.item.$fishId"), GuiNameStyle.PRIMARY),
            GuiElementRole.CONTENT, 1, listOf(text(player, "fishing.dictionary.description.$fishId")),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null
        )))
        val conditions = definition.conditions.map { condition ->
            GuiMenuIconData(text(player, condition.labelKey),
                condition.hintKey?.let { text(player, it) }
                    ?: condition.rawValue
                    ?: condition.localizedValues.joinToString("、") { text(player, it) }, "§f")
        }
        inventory.setItem(layout.leftSlot, service.menuIcon(GuiMenuIconSpec(
            Material.MAP, GuiNameSpec.Text(text(player, "fishing.detail.conditions"), GuiNameStyle.DEFAULT),
            GuiElementRole.CONTENT, 1, emptyList(), conditions, emptyList(), emptyList(), emptyList(), emptyList(), null
        )))
        val qualityCounts = FishQuality.normalizeStoredCounts(entry.qualityCounts)
        val records = buildList {
            add(GuiMenuIconData(text(player, "fishing.fishdex.total"), entry.obtainedCount, "§f"))
            add(GuiMenuIconData(text(player, "fishing.fishdex.maximum"), "${entry.maximumWeight ?: 0}g", "§f"))
            add(GuiMenuIconData(text(player, "fishing.fishdex.minimum"), "${entry.minimumWeight ?: 0}g", "§f"))
            add(GuiMenuIconData(text(player, "fishing.fishdex.quality_breakdown"), "", "§f"))
            FishQuality.entries.forEach { quality ->
                add(GuiMenuIconData("  ${quality.stars}", qualityCounts[quality] ?: 0L, "§f"))
            }
        }
        inventory.setItem(layout.centerSlot, service.menuIcon(GuiMenuIconSpec(
            Material.WRITABLE_BOOK, GuiNameSpec.Text(text(player, "fishing.detail.records"), GuiNameStyle.DEFAULT),
            GuiElementRole.CONTENT, 1, emptyList(), records, emptyList(), emptyList(), emptyList(), emptyList(), null
        )))
        inventory.setItem(layout.rightSlot, service.menuIcon(GuiMenuIconSpec(
            Material.CRAFTING_TABLE, GuiNameSpec.Text(text(player, "fishing.detail.uses"), GuiNameStyle.DEFAULT),
            GuiElementRole.CONTENT, 1, emptyList(), emptyList(), emptyList(),
            listOf(text(player, "fishing.detail.uses_none")), emptyList(), emptyList(), null
        )))
        inventory.setItem(layout.backSlot, navigationItem(service, Material.ARROW, text(player, "fishing.detail.back"), GuiElementRole.BACK))
        ManagedMenuPresenter.open(player, inventory)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = sender as? Player ?: return true
        val type = when (label.lowercase()) {
            "cookdex" -> CatalogType.COOKING
            "brewdex" -> CatalogType.BREWERY
            else -> args.firstOrNull()?.lowercase()?.let { value -> CatalogType.entries.firstOrNull { it.id == value } } ?: CatalogType.FISHING
        }
        val pageArg = if (label.equals("catalog", true)) args.getOrNull(1) else args.firstOrNull()
        val routeArguments = mapOf(
            "type" to type.id,
            "page" to (pageArg?.toIntOrNull()?.coerceAtLeast(1)?.minus(1) ?: 0).toString()
        )
        CCSystem.getAPI().getMenuCommandService().open(
            player,
            player,
            "cc-content:catalog",
            routeArguments
        )
        return true
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val detail = event.view.topInventory.holder as? FishingDetailHolder
        if (detail != null) {
            event.isCancelled = true
            val player = event.whoClicked as? Player ?: return
            if (detail.owner == player.uniqueId &&
                event.rawSlot == CCSystem.getAPI().getGuiLayoutService().threeChoice45().backSlot) {
                open(player, CatalogType.FISHING, detail.returnPage)
            }
            return
        }
        val holder = event.view.topInventory.holder as? CatalogHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (holder.owner != player.uniqueId) return
        if (holder.type == CatalogType.FISHING) {
            val layout = CCSystem.getAPI().getGuiLayoutService().sevenColumnList(21)
            when (event.rawSlot) {
                layout.previousPageSlot -> if (holder.page > 0) open(player, holder.type, holder.page - 1)
                layout.nextPageSlot -> if (holder.page + 1 < holder.totalPages) open(player, holder.type, holder.page + 1)
                layout.actionSlot -> openFishingJournal(player)
                else -> {
                    val fishId = holder.itemIdsBySlot[event.rawSlot] ?: return
                    if (store.entries(player.uniqueId, CatalogType.FISHING)[fishId]?.discovered != true) return
                    if (event.isRightClick) {
                        setFishingSearchTarget(player, if (fishingSearchTarget(player.uniqueId) == fishId) null else fishId)
                        open(player, holder.type, holder.page)
                    } else if (event.isLeftClick) {
                        player.playSound(player.location, org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.2f)
                        openFishingDetail(player, fishId, holder.page)
                    }
                }
            }
        } else {
            val layout = CCSystem.getAPI().getGuiLayoutService().pagedList54()
            if (event.rawSlot == layout.previousPageSlot && holder.page > 0) open(player, holder.type, holder.page - 1)
            if (event.rawSlot == layout.nextPageSlot && holder.page + 1 < holder.totalPages) open(player, holder.type, holder.page + 1)
            if (event.rawSlot == layout.backSlot) ManagedMenuPresenter.close(player)
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder !is CatalogHolder &&
            event.view.topInventory.holder !is FishingDetailHolder) return
        event.isCancelled = true
    }

    private fun text(player: Player, key: String, vararg placeholders: Pair<String, Any?>): String =
        CCSystem.getAPI().getI18nString(player, key, placeholders.associate { it.first to (it.second ?: "") }).replace('&', '§')
}

private class FishingDetailHolder(val owner: UUID, val returnPage: Int) : InventoryHolder {
    override fun getInventory(): Inventory = backingInventory
    lateinit var backingInventory: Inventory
}

private class CatalogHolder(val owner: UUID, val type: CatalogType, val page: Int, val totalPages: Int) : InventoryHolder {
    override fun getInventory(): Inventory = backingInventory
    lateinit var backingInventory: Inventory
    val itemIdsBySlot: MutableMap<Int, String> = mutableMapOf()
}
