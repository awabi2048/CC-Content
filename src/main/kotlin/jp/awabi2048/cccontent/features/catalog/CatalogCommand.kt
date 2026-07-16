package jp.awabi2048.cccontent.features.catalog

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuIconData
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

data class CatalogItem(val id: String, val material: Material)

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
    private val isAvailable: (CatalogType) -> Boolean
) : CommandExecutor, Listener {
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
        player.openInventory(inventory)
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
            inventory.setItem(layout.itemSlots[index], fishingItem(player, definition, entries[definition.id]))
        }
        val elementService = CCSystem.getAPI().getGuiElementService()
        if (page > 0) inventory.setItem(layout.previousPageSlot, navigationItem(elementService, Material.ARROW, text(player, "fishing.fishdex.previous")))
        if (page + 1 < totalPages) inventory.setItem(layout.nextPageSlot, navigationItem(elementService, Material.ARROW, text(player, "fishing.fishdex.next")))
        inventory.setItem(layout.actionSlot, navigationItem(elementService, Material.BARRIER, text(player, "catalog.close"), GuiElementRole.CANCEL))
        player.openInventory(inventory)
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
            add(GuiMenuIconData(text(player, "fishing.fishdex.total"), visibleEntry.obtainedCount, "§f"))
            add(GuiMenuIconData(text(player, "fishing.fishdex.maximum"), "${visibleEntry.maximumWeight ?: 0}g", "§f"))
            add(GuiMenuIconData(text(player, "fishing.fishdex.minimum"), "${visibleEntry.minimumWeight ?: 0}g", "§f"))
            addAll(FishQuality.entries.map { quality ->
                GuiMenuIconData(text(player, "fishing.fishdex.quality.${quality.id}"), visibleEntry.qualityCounts[quality.id] ?: 0L, "§f")
            })
        } else listOf(GuiMenuIconData(text(player, "fishing.fishdex.total"), 0L, "§f"))
        return service.menuIcon(GuiMenuIconSpec(
            material = if (discovered) definition.material else Material.GRAY_DYE,
            name = GuiNameSpec.Text(text(player, "fishing.catalog.item.${definition.id}"), GuiNameStyle.DEFAULT),
            role = GuiElementRole.CONTENT,
            amount = 1,
            description = listOf(text(player, "fishing.fishdex.description.${definition.id}")),
            data = data,
            options = emptyList(),
            warnings = if (discovered) emptyList() else listOf(text(player, "fishing.fishdex.not_caught")),
            dangers = emptyList(),
            actions = emptyList(),
            glint = null
        ))
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = sender as? Player ?: return true
        val type = when (label.lowercase()) {
            "fishdex" -> CatalogType.FISHING
            "cookdex" -> CatalogType.COOKING
            "brewdex" -> CatalogType.BREWERY
            else -> args.firstOrNull()?.lowercase()?.let { value -> CatalogType.entries.firstOrNull { it.id == value } } ?: CatalogType.FISHING
        }
        val pageArg = if (label.equals("catalog", true)) args.getOrNull(1) else args.firstOrNull()
        open(player, type, pageArg?.toIntOrNull()?.coerceAtLeast(1)?.minus(1) ?: 0)
        return true
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? CatalogHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (holder.owner != player.uniqueId) return
        if (holder.type == CatalogType.FISHING) {
            val layout = CCSystem.getAPI().getGuiLayoutService().sevenColumnList(21)
            when (event.rawSlot) {
                layout.previousPageSlot -> if (holder.page > 0) open(player, holder.type, holder.page - 1)
                layout.nextPageSlot -> if (holder.page + 1 < holder.totalPages) open(player, holder.type, holder.page + 1)
                layout.actionSlot -> player.closeInventory()
            }
        } else {
            val layout = CCSystem.getAPI().getGuiLayoutService().pagedList54()
            if (event.rawSlot == layout.previousPageSlot && holder.page > 0) open(player, holder.type, holder.page - 1)
            if (event.rawSlot == layout.nextPageSlot && holder.page + 1 < holder.totalPages) open(player, holder.type, holder.page + 1)
            if (event.rawSlot == layout.backSlot) player.closeInventory()
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder !is CatalogHolder) return
        event.isCancelled = true
    }

    private fun text(player: Player, key: String, vararg placeholders: Pair<String, Any?>): String =
        CCSystem.getAPI().getI18nString(player, key, placeholders.associate { it.first to (it.second ?: "") }).replace('&', '§')
}

private class CatalogHolder(val owner: UUID, val type: CatalogType, val page: Int, val totalPages: Int) : InventoryHolder {
    override fun getInventory(): Inventory = backingInventory
    lateinit var backingInventory: Inventory
}
