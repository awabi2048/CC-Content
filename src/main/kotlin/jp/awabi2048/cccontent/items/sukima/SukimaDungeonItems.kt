package jp.awabi2048.cccontent.items.sukima

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.sukima_dungeon.CustomItemManager as SukimaCustomItemManager
import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonTier
import jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager
import jp.awabi2048.cccontent.features.sukima_dungeon.SukimaConfigHelper
import jp.awabi2048.cccontent.features.sukima_dungeon.gui.DungeonEntranceGui
import jp.awabi2048.cccontent.features.sukima_dungeon.gui.TalismanConfirmGui
import jp.awabi2048.cccontent.features.sukima_dungeon.isSukimaDungeonWorld
import jp.awabi2048.cccontent.items.CustomItem
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

private val COMPASS_TIER_KEY = NamespacedKey("sukimadungeon", "compass_tier")

class SukimaCompassTier1Item : CustomItem {
    override val feature: String = "sukima_dungeon"
    override val id: String = "compass_tier_1"
    override val displayName: String = "§bワールドの芽コンパス [Tier 1]"
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        return SukimaCustomItemManager.getCompassItem(player, 1).apply {
            this.amount = amount
            val meta = this.itemMeta
            if (meta != null) {
                meta.persistentDataContainer.set(COMPASS_TIER_KEY, PersistentDataType.INTEGER, 1)
                this.itemMeta = meta
            }
        }
    }

    override fun matches(item: ItemStack): Boolean {
        if (!SukimaCustomItemManager.isCompassItem(item)) return false
        val tier = item.itemMeta?.persistentDataContainer?.get(COMPASS_TIER_KEY, PersistentDataType.INTEGER)
        if (tier != null) return tier == 1
        return item.itemMeta?.displayName?.contains("Tier 1") == true
    }
}

class SukimaCompassTier2Item : CustomItem {
    override val feature: String = "sukima_dungeon"
    override val id: String = "compass_tier_2"
    override val displayName: String = "§bワールドの芽コンパス [Tier 2]"
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        return SukimaCustomItemManager.getCompassItem(player, 2).apply {
            this.amount = amount
            val meta = this.itemMeta
            if (meta != null) {
                meta.persistentDataContainer.set(COMPASS_TIER_KEY, PersistentDataType.INTEGER, 2)
                this.itemMeta = meta
            }
        }
    }

    override fun matches(item: ItemStack): Boolean {
        if (!SukimaCustomItemManager.isCompassItem(item)) return false
        val tier = item.itemMeta?.persistentDataContainer?.get(COMPASS_TIER_KEY, PersistentDataType.INTEGER)
        if (tier != null) return tier == 2
        return item.itemMeta?.displayName?.contains("Tier 2") == true
    }
}

class SukimaCompassTier3Item : CustomItem {
    override val feature: String = "sukima_dungeon"
    override val id: String = "compass_tier_3"
    override val displayName: String = "§bワールドの芽コンパス [Tier 3]"
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        return SukimaCustomItemManager.getCompassItem(player, 3).apply {
            this.amount = amount
            val meta = this.itemMeta
            if (meta != null) {
                meta.persistentDataContainer.set(COMPASS_TIER_KEY, PersistentDataType.INTEGER, 3)
                this.itemMeta = meta
            }
        }
    }

    override fun matches(item: ItemStack): Boolean {
        if (!SukimaCustomItemManager.isCompassItem(item)) return false
        val tier = item.itemMeta?.persistentDataContainer?.get(COMPASS_TIER_KEY, PersistentDataType.INTEGER)
        if (tier != null) return tier == 3
        return item.itemMeta?.displayName?.contains("Tier 3") == true
    }
}

class SukimaMarkerToolItem : CustomItem {
    override val feature: String = "sukima_dungeon"
    override val id: String = "marker_tool"
    override val displayName: String = "§dマーカー設置ツール"
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val target = player ?: CCContent.instance.server.onlinePlayers.firstOrNull()
        val item = if (target != null) {
            CCContent.instance.getMarkerManager().getMarkerTool(target)
        } else {
            ItemStack(Material.BLAZE_ROD)
        }
        item.amount = amount
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        return runCatching {
            CCContent.instance.getMarkerManager().isMarkerTool(item)
        }.getOrDefault(false)
    }
}

class SukimaBookmarkBrokenItem : CustomItem {
    override val feature: String = "sukima_dungeon"
    override val id: String = "bookmark_broken"
    override val displayName: String = "§dぼろぼろのふしぎなしおり"
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        return SukimaCustomItemManager.getBookmarkItem(player, DungeonTier.BROKEN).apply { this.amount = amount }
    }

    override fun matches(item: ItemStack): Boolean {
        return SukimaCustomItemManager.isBookmarkItem(item) && SukimaCustomItemManager.getTier(item) == DungeonTier.BROKEN
    }

    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val blacklist = SukimaConfigHelper.getConfig(CCContent.instance).getStringList("bookmark_blacklist_worlds")
        if (blacklist.contains(player.world.name) || isSukimaDungeonWorld(player.world)) {
            player.sendMessage(MessageManager.getMessage(player, "bookmark_cannot_use_here"))
            event.isCancelled = true
            return
        }

        DungeonEntranceGui(CCContent.instance.getStructureLoader(), DungeonTier.BROKEN).open(player)
        player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f)
        event.isCancelled = true
    }
}

class SukimaBookmarkWornItem : CustomItem {
    override val feature: String = "sukima_dungeon"
    override val id: String = "bookmark_worn"
    override val displayName: String = "§d擦り切れたふしぎなしおり"
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        return SukimaCustomItemManager.getBookmarkItem(player, DungeonTier.WORN).apply { this.amount = amount }
    }

    override fun matches(item: ItemStack): Boolean {
        return SukimaCustomItemManager.isBookmarkItem(item) && SukimaCustomItemManager.getTier(item) == DungeonTier.WORN
    }

    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val blacklist = SukimaConfigHelper.getConfig(CCContent.instance).getStringList("bookmark_blacklist_worlds")
        if (blacklist.contains(player.world.name) || isSukimaDungeonWorld(player.world)) {
            player.sendMessage(MessageManager.getMessage(player, "bookmark_cannot_use_here"))
            event.isCancelled = true
            return
        }

        DungeonEntranceGui(CCContent.instance.getStructureLoader(), DungeonTier.WORN).open(player)
        player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f)
        event.isCancelled = true
    }
}

class SukimaBookmarkFadedItem : CustomItem {
    override val feature: String = "sukima_dungeon"
    override val id: String = "bookmark_faded"
    override val displayName: String = "§d色褪せたふしぎなしおり"
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        return SukimaCustomItemManager.getBookmarkItem(player, DungeonTier.FADED).apply { this.amount = amount }
    }

    override fun matches(item: ItemStack): Boolean {
        return SukimaCustomItemManager.isBookmarkItem(item) && SukimaCustomItemManager.getTier(item) == DungeonTier.FADED
    }

    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val blacklist = SukimaConfigHelper.getConfig(CCContent.instance).getStringList("bookmark_blacklist_worlds")
        if (blacklist.contains(player.world.name) || isSukimaDungeonWorld(player.world)) {
            player.sendMessage(MessageManager.getMessage(player, "bookmark_cannot_use_here"))
            event.isCancelled = true
            return
        }

        DungeonEntranceGui(CCContent.instance.getStructureLoader(), DungeonTier.FADED).open(player)
        player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f)
        event.isCancelled = true
    }
}

class SukimaBookmarkNewItem : CustomItem {
    override val feature: String = "sukima_dungeon"
    override val id: String = "bookmark_new"
    override val displayName: String = "§d新品のふしぎなしおり"
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        return SukimaCustomItemManager.getBookmarkItem(player, DungeonTier.NEW).apply { this.amount = amount }
    }

    override fun matches(item: ItemStack): Boolean {
        return SukimaCustomItemManager.isBookmarkItem(item) && SukimaCustomItemManager.getTier(item) == DungeonTier.NEW
    }

    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        val blacklist = SukimaConfigHelper.getConfig(CCContent.instance).getStringList("bookmark_blacklist_worlds")
        if (blacklist.contains(player.world.name) || isSukimaDungeonWorld(player.world)) {
            player.sendMessage(MessageManager.getMessage(player, "bookmark_cannot_use_here"))
            event.isCancelled = true
            return
        }

        DungeonEntranceGui(CCContent.instance.getStructureLoader(), DungeonTier.NEW).open(player)
        player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f)
        event.isCancelled = true
    }
}

class SukimaTalismanItem : CustomItem {
    override val feature: String = "sukima_dungeon"
    override val id: String = "talisman"
    override val displayName: String = "§6おあげちゃんのお札"
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        return SukimaCustomItemManager.getTalismanItem(player).apply { this.amount = amount }
    }

    override fun matches(item: ItemStack): Boolean = SukimaCustomItemManager.isTalismanItem(item)

    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        if (isSukimaDungeonWorld(player.world)) {
            TalismanConfirmGui().open(player)
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
        } else {
            player.sendMessage(MessageManager.getMessage(player, "talisman_cannot_use_here"))
        }
        event.isCancelled = true
    }

    override fun onInventoryClick(player: Player, event: InventoryClickEvent) {
        if (event.inventory.holder is TalismanConfirmGui) return

        val currentItem = event.currentItem
        val cursorItem = event.cursor
        val action = event.action

        val isTalismanInSlot = SukimaCustomItemManager.isTalismanItem(currentItem)
        val isTalismanOnCursor = SukimaCustomItemManager.isTalismanItem(cursorItem)
        val isTalismanInHotbar = event.click == ClickType.NUMBER_KEY &&
            SukimaCustomItemManager.isTalismanItem(player.inventory.getItem(event.hotbarButton))

        if (!isTalismanInSlot && !isTalismanOnCursor && !isTalismanInHotbar) return

        if (
            action == InventoryAction.DROP_ALL_CURSOR ||
            action == InventoryAction.DROP_ALL_SLOT ||
            action == InventoryAction.DROP_ONE_CURSOR ||
            action == InventoryAction.DROP_ONE_SLOT ||
            event.click == ClickType.WINDOW_BORDER_LEFT ||
            event.click == ClickType.WINDOW_BORDER_RIGHT
        ) {
            event.isCancelled = true
            return
        }

        val clickedInventory = event.clickedInventory ?: return
        val topInventory = event.view.topInventory
        if (
            action == InventoryAction.MOVE_TO_OTHER_INVENTORY &&
            clickedInventory == event.view.bottomInventory &&
            topInventory.type != InventoryType.CRAFTING &&
            topInventory.type != InventoryType.PLAYER
        ) {
            event.isCancelled = true
            return
        }

        if (
            clickedInventory == topInventory &&
            topInventory.type != InventoryType.CRAFTING &&
            topInventory.type != InventoryType.PLAYER
        ) {
            event.isCancelled = true
            return
        }

        if (
            event.click == ClickType.NUMBER_KEY &&
            clickedInventory == topInventory &&
            topInventory.type != InventoryType.CRAFTING &&
            topInventory.type != InventoryType.PLAYER
        ) {
            event.isCancelled = true
        }
    }
}

class SukimaWorldSproutItem : CustomItem {
    override val feature: String = "sukima_dungeon"
    override val id: String = "world_sprout"
    override val displayName: String = "§dワールドの芽"
    override val lore: List<String> = emptyList()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        return SukimaCustomItemManager.getWorldSproutItem().apply { this.amount = amount }
    }

    override fun matches(item: ItemStack): Boolean = SukimaCustomItemManager.isWorldSproutItem(item)
}
