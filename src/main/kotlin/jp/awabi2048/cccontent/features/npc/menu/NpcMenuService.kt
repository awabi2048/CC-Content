package jp.awabi2048.cccontent.features.npc.menu

import com.awabi2048.ccsystem.CCSystem
import jp.awabi2048.cccontent.gui.GuiMenuItems
import jp.awabi2048.cccontent.gui.OwnedMenuHolder
import jp.awabi2048.cccontent.util.ContentLocaleResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class NpcMenuService(
    private val plugin: JavaPlugin
) : Listener {
    private companion object {
        const val CONFIG_PATH = "config/npc/menu.yml"
        const val MENU_ID = "oage_jinja_offering"
        const val TITLE = "§8おあげ神社 - 授与所"
        const val MENU_SIZE = 45
        const val SHOP_MENU_SIZE = 54
        const val TALK_MENU_SIZE = 54
        const val BACK_SLOT = 40
        const val SHOP_BACK_SLOT = 45
        const val TALK_BACK_SLOT = 49
        val MAIN_SLOTS = mapOf(
            19 to NpcMenuView.DAILY,
            21 to NpcMenuView.SHOP,
            23 to NpcMenuView.TALK,
            25 to NpcMenuView.REQUEST
        )
        val DAILY_ACTION_SLOTS = setOf(20, 24)
        val TALK_TOPIC_SLOTS = setOf(20, 21, 22, 23, 24, 29, 30, 31, 32, 33)
        val REQUEST_ACTION_SLOTS = setOf(20, 24)
        val SHOP_TAB_SLOTS = listOf(47, 48)
        val SHOP_CONTENT_SLOTS = listOf(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34)
    }

    private val configFile = File(plugin.dataFolder, CONFIG_PATH)
    private var configuredMenuIds: Set<String> = setOf(MENU_ID)

    fun initialize() {
        ensureConfig()
        reload()
    }

    fun reload() {
        ensureConfig()
        val config = YamlConfiguration.loadConfiguration(configFile)
        configuredMenuIds = config.getConfigurationSection("menus")
            ?.getKeys(false)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: setOf(MENU_ID)
    }

    fun getMenuIds(): Set<String> = configuredMenuIds

    fun open(menuId: String, player: Player): Boolean {
        if (menuId != MENU_ID || !configuredMenuIds.contains(menuId)) {
            player.sendMessage("§cNPCメニューが見つかりません: $menuId")
            return false
        }

        openView(player, NpcMenuView.MAIN)
        return true
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? NpcMenuHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (player.uniqueId != holder.ownerId) {
            return
        }

        val rawSlot = event.rawSlot
        if (rawSlot !in 0 until event.view.topInventory.size) {
            return
        }

        handleClick(player, holder, rawSlot)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? NpcMenuHolder ?: return
        if (event.rawSlots.any { it in 0 until event.view.topInventory.size } || holder.ownerId != (event.whoClicked as? Player)?.uniqueId) {
            event.isCancelled = true
        }
    }

    private fun handleClick(player: Player, holder: NpcMenuHolder, slot: Int) {
        when (holder.view) {
            NpcMenuView.MAIN -> {
                val next = MAIN_SLOTS[slot] ?: return
                playClick(player)
                openView(player, next)
            }

            NpcMenuView.DAILY -> {
                if (slot == BACK_SLOT) {
                    playClick(player)
                    openView(player, NpcMenuView.MAIN)
                } else if (slot in DAILY_ACTION_SLOTS) {
                    notifyPreparing(player)
                }
            }

            NpcMenuView.SHOP -> {
                if (slot == SHOP_BACK_SLOT) {
                    playClick(player)
                    openView(player, NpcMenuView.MAIN)
                    return
                }
                val tabIndex = SHOP_TAB_SLOTS.indexOf(slot)
                if (tabIndex >= 0) {
                    playClick(player)
                    openShop(player, ShopTab.entries[tabIndex.coerceAtMost(ShopTab.entries.lastIndex)])
                } else if (slot in SHOP_CONTENT_SLOTS) {
                    notifyPreparing(player)
                }
            }

            NpcMenuView.TALK -> {
                if (slot == TALK_BACK_SLOT) {
                    playClick(player)
                    openView(player, NpcMenuView.MAIN)
                } else if (slot in TALK_TOPIC_SLOTS) {
                    notifyPreparing(player)
                }
            }

            NpcMenuView.REQUEST -> {
                if (slot == BACK_SLOT) {
                    playClick(player)
                    openView(player, NpcMenuView.MAIN)
                } else if (slot in REQUEST_ACTION_SLOTS) {
                    notifyPreparing(player)
                }
            }
        }
    }

    private fun openView(player: Player, view: NpcMenuView) {
        if (view == NpcMenuView.SHOP) {
            openShop(player, ShopTab.OTHER)
            return
        }

        val holder = NpcMenuHolder(player.uniqueId, view)
        val inventory = Bukkit.createInventory(holder, menuSize(view), TITLE)
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
    }

    private fun openShop(player: Player, tab: ShopTab) {
        val holder = NpcMenuHolder(player.uniqueId, NpcMenuView.SHOP, tab)
        val inventory = Bukkit.createInventory(holder, SHOP_MENU_SIZE, TITLE)
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
    }

    private fun render(player: Player, holder: NpcMenuHolder, inventory: Inventory) {
        GuiMenuItems.fillFramed(inventory)
        when (holder.view) {
            NpcMenuView.MAIN -> renderMain(player, inventory)
            NpcMenuView.DAILY -> renderDaily(player, inventory)
            NpcMenuView.SHOP -> renderShop(player, holder.shopTab, inventory)
            NpcMenuView.TALK -> renderTalk(player, inventory)
            NpcMenuView.REQUEST -> renderRequest(player, inventory)
        }
    }

    private fun renderMain(player: Player, inventory: Inventory) {
        inventory.setItem(19, icon(player, "main.daily", Material.CHEST))
        inventory.setItem(21, icon(player, "main.shop", Material.CRAFTING_TABLE))
        inventory.setItem(23, icon(player, "main.talk", Material.WRITABLE_BOOK))
        inventory.setItem(25, icon(player, "main.request", Material.CLOCK))
    }

    private fun renderDaily(player: Player, inventory: Inventory) {
        inventory.setItem(4, icon(player, "daily.header", Material.WRITABLE_BOOK))
        inventory.setItem(20, icon(player, "daily.delivery", Material.DIAMOND_SWORD))
        inventory.setItem(24, icon(player, "daily.part_time", Material.BARREL))
        inventory.setItem(BACK_SLOT, backButton(player))
    }

    private fun renderShop(player: Player, tab: ShopTab, inventory: Inventory) {
        inventory.setItem(4, icon(player, "shop.header", Material.CHEST))
        inventory.setItem(SHOP_BACK_SLOT, backButton(player))
        renderShopTabs(player, tab, inventory)

        val contentMaterial = when (tab) {
            ShopTab.OTHER -> Material.EMERALD
            ShopTab.HEAD -> Material.PLAYER_HEAD
        }
        SHOP_CONTENT_SLOTS.forEachIndexed { index, slot ->
            inventory.setItem(slot, icon(player, "shop.placeholder", contentMaterial, "index" to (index + 1)))
        }
    }

    private fun renderShopTabs(player: Player, activeTab: ShopTab, inventory: Inventory) {
        ShopTab.entries.forEachIndexed { index, tab ->
            val key = if (tab == activeTab) "shop.tab.${tab.key}.active" else "shop.tab.${tab.key}"
            inventory.setItem(SHOP_TAB_SLOTS[index], icon(player, key, tab.material))
        }
    }

    private fun renderTalk(player: Player, inventory: Inventory) {
        inventory.setItem(4, icon(player, "talk.header", Material.WRITABLE_BOOK))
        inventory.setItem(20, icon(player, "talk.topic.arena", Material.DIAMOND_SWORD))
        listOf(21, 22, 23, 24, 29, 30, 31, 32, 33).forEach {
            inventory.setItem(it, icon(player, "talk.topic.locked", Material.GRAY_DYE))
        }
        inventory.setItem(TALK_BACK_SLOT, backButton(player))
    }

    private fun renderRequest(player: Player, inventory: Inventory) {
        inventory.setItem(4, icon(player, "request.header", Material.CLOCK))
        inventory.setItem(20, icon(player, "request.restore", Material.ENDER_EYE))
        inventory.setItem(24, icon(player, "request.ritual", Material.NETHER_STAR))
        inventory.setItem(BACK_SLOT, backButton(player))
    }

    private fun icon(player: Player, key: String, material: Material, vararg placeholders: Pair<String, Any?>) =
        GuiMenuItems.icon(
            material = material,
            name = text(player, "$key.name", *placeholders),
            lore = list(player, "$key.lore", *placeholders)
        )

    private fun backButton(player: Player) =
        GuiMenuItems.backButton(text(player, "back.name"), list(player, "back.lore"))

    private fun menuSize(view: NpcMenuView): Int {
        return when (view) {
            NpcMenuView.SHOP -> SHOP_MENU_SIZE
            NpcMenuView.TALK -> TALK_MENU_SIZE
            else -> MENU_SIZE
        }
    }

    private fun notifyPreparing(player: Player) {
        playClick(player)
        player.sendMessage(text(player, "messages.preparing"))
    }

    private fun playClick(player: Player) {
        player.playSound(player.location, "minecraft:ui.button.click", 0.7f, 1.6f)
    }

    private fun text(player: Player, key: String, vararg placeholders: Pair<String, Any?>): String {
        val locale = ContentLocaleResolver.resolve(player)
        return CCSystem.getAPI()
            .getI18nString("CC-Content:rank", locale, "gui.npc.oage_jinja_offering.$key", placeholdersMap(*placeholders))
            .replace('&', '§')
    }

    private fun list(player: Player, key: String, vararg placeholders: Pair<String, Any?>): List<String> {
        val locale = ContentLocaleResolver.resolve(player)
        return CCSystem.getAPI()
            .getI18nStringList("CC-Content:rank", locale, "gui.npc.oage_jinja_offering.$key", placeholdersMap(*placeholders))
            .map { it.replace('&', '§') }
    }

    private fun placeholdersMap(vararg placeholders: Pair<String, Any?>): Map<String, Any> {
        return placeholders.associate { (key, value) -> key to (value ?: "null") }
    }

    private fun ensureConfig() {
        if (!configFile.exists()) {
            configFile.parentFile?.mkdirs()
            plugin.saveResource(CONFIG_PATH, false)
        }
    }
}

private class NpcMenuHolder(
    ownerId: UUID,
    val view: NpcMenuView,
    val shopTab: ShopTab = ShopTab.OTHER
) : OwnedMenuHolder(ownerId)

private enum class NpcMenuView(val key: String) {
    MAIN("main"),
    DAILY("daily"),
    SHOP("shop"),
    TALK("talk"),
    REQUEST("request")
}

private enum class ShopTab(val key: String, val material: Material) {
    OTHER("other", Material.EMERALD),
    HEAD("head", Material.PLAYER_HEAD)
}
