package jp.awabi2048.cccontent.features.sukima_dungeon

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.items.CustomItemManager as GlobalCustomItemManager
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.inventory.ItemFlag

enum class DungeonTier(
    val internalName: String,
    val tier: Int,
    val successRate: Double,
    val availableSizes: List<String>
) {
    BROKEN("BROKEN", 1, 0.5, listOf("small")),
    WORN("WORN", 2, 0.7, listOf("small", "medium")),
    FADED("FADED", 3, 0.9, listOf("small", "medium", "large")),
    NEW("NEW", 4, 1.0, listOf("medium", "large", "huge"));

    companion object {
        fun fromName(name: String?): DungeonTier? = values().find { it.internalName == name }
        fun fromTier(tier: Int): DungeonTier? = values().find { it.tier == tier }
    }
}

enum class CustomItem(val id: String) {
    COMPASS_TIER_1("compass_tier_1"),
    COMPASS_TIER_2("compass_tier_2"),
    COMPASS_TIER_3("compass_tier_3"),
    MARKER_TOOL("marker_tool"),
    BOOKMARK_BROKEN("bookmark_broken"),
    BOOKMARK_WORN("bookmark_worn"),
    BOOKMARK_FADED("bookmark_faded"),
    BOOKMARK_NEW("bookmark_new"),
    TALISMAN("talisman"),
    WORLD_SPROUT("world_sprout");

    companion object {
        fun fromId(id: String?): CustomItem? = values().find { it.id.equals(id, true) }
    }
}

object CustomItemManager {
    private val TIER_KEY = NamespacedKey("sukimadungeon", "tier")
    private val ITEM_TYPE_KEY = NamespacedKey("sukimadungeon", "item_type")
    private val DUNGEON_ITEM_KEY = NamespacedKey("sukimadungeon", "is_dungeon_item")
    private val COMPASS_RADIUS_KEY = NamespacedKey("sukimadungeon", "compass_radius")
    private val COMPASS_TIME_KEY = NamespacedKey("sukimadungeon", "compass_time")
    private val COMPASS_COOLDOWN_KEY = NamespacedKey("sukimadungeon", "compass_cooldown")

    fun markAsDungeonItem(item: ItemStack) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(DUNGEON_ITEM_KEY, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
    }

    fun isDungeonItem(item: ItemStack?): Boolean {
        if (item == null) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(DUNGEON_ITEM_KEY, PersistentDataType.BYTE)
    }

    fun getCustomItem(type: CustomItem, player: Player?): ItemStack {
        return when (type) {
            CustomItem.COMPASS_TIER_1 -> getCompassItem(player, 1)
            CustomItem.COMPASS_TIER_2 -> getCompassItem(player, 2)
            CustomItem.COMPASS_TIER_3 -> getCompassItem(player, 3)
            CustomItem.MARKER_TOOL -> {
                CCContent.instance.getMarkerManager().getMarkerTool(player)
            }
            CustomItem.BOOKMARK_BROKEN -> getBookmarkItem(player, DungeonTier.BROKEN)
            CustomItem.BOOKMARK_WORN -> getBookmarkItem(player, DungeonTier.WORN)
            CustomItem.BOOKMARK_FADED -> getBookmarkItem(player, DungeonTier.FADED)
            CustomItem.BOOKMARK_NEW -> getBookmarkItem(player, DungeonTier.NEW)
            CustomItem.TALISMAN -> getTalismanItem(player)
            CustomItem.WORLD_SPROUT -> getWorldSproutItem()
        }
    }

    fun getBookmarkItem(player: Player?, tier: DungeonTier = DungeonTier.BROKEN): ItemStack {
        val itemId = when (tier) {
            DungeonTier.BROKEN -> "sukima_dungeon.bookmark_broken"
            DungeonTier.WORN -> "sukima_dungeon.bookmark_worn"
            DungeonTier.FADED -> "sukima_dungeon.bookmark_faded"
            DungeonTier.NEW -> "sukima_dungeon.bookmark_new"
        }
        return GlobalCustomItemManager.createItemForPlayer(itemId, player)
            ?: ItemStack(Material.POISONOUS_POTATO)
    }

    fun getTalismanItem(player: Player?): ItemStack {
        return GlobalCustomItemManager.createItemForPlayer("sukima_dungeon.talisman", player)
            ?: ItemStack(Material.POISONOUS_POTATO)
    }

    fun isBookmarkItem(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.get(ITEM_TYPE_KEY, PersistentDataType.STRING) == "bookmark" ||
               meta.persistentDataContainer.has(TIER_KEY, PersistentDataType.STRING)
    }

    fun isTalismanItem(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        val type = meta.persistentDataContainer.get(ITEM_TYPE_KEY, PersistentDataType.STRING)
        return type == "talisman"
    }

    fun getTier(item: ItemStack): DungeonTier? {
        val meta = item.itemMeta ?: return null
        val tierName = meta.persistentDataContainer.get(TIER_KEY, PersistentDataType.STRING)
        return DungeonTier.fromName(tierName)
    }

    fun getWorldSproutItem(): ItemStack {
        return GlobalCustomItemManager.createItem("sukima_dungeon.world_sprout")
            ?: ItemStack(Material.POISONOUS_POTATO)
    }

    fun isWorldSproutItem(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        val type = meta.persistentDataContainer.get(ITEM_TYPE_KEY, PersistentDataType.STRING)
        return type == "world_sprout"
    }

    fun getCompassItem(player: Player? = null, tier: Int = 1): ItemStack {
        val itemId = "sukima_dungeon.compass_tier_${tier.coerceIn(1, 3)}"
        return GlobalCustomItemManager.createItemForPlayer(itemId, player)
            ?: ItemStack(Material.POISONOUS_POTATO)
    }

    fun isCompassItem(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        val type = meta.persistentDataContainer.get(ITEM_TYPE_KEY, PersistentDataType.STRING)
        return type == "compass"
    }

    fun applyBookmarkMetadata(item: ItemStack, player: Player?, tier: DungeonTier) {
        val meta = item.itemMeta ?: return

        val tierDisplayName = MessageManager.getTierName(player, tier.name)
        meta.setMaxStackSize(1)
        meta.setDisplayName(MessageManager.getMessage(player, "item_bookmark_name", mapOf("tier" to tierDisplayName)))
        meta.lore = MessageManager.getList(player, "item_bookmark_lore")
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        meta.persistentDataContainer.set(TIER_KEY, PersistentDataType.STRING, tier.internalName)
        meta.persistentDataContainer.set(ITEM_TYPE_KEY, PersistentDataType.STRING, "bookmark")
        item.itemMeta = meta
    }

    fun applyTalismanMetadata(item: ItemStack, player: Player?) {
        val meta = item.itemMeta ?: return

        meta.setMaxStackSize(1)
        meta.setDisplayName(MessageManager.getMessage(player, "item_talisman_name"))
        meta.lore = MessageManager.getList(player, "item_talisman_lore")
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        meta.persistentDataContainer.set(ITEM_TYPE_KEY, PersistentDataType.STRING, "talisman")
        item.itemMeta = meta
    }

    fun applyWorldSproutMetadata(item: ItemStack) {
        val meta = item.itemMeta ?: return
        val bar = MessageManager.getMessage(null, "common_bar")

        meta.setDisplayName("§dワールドの芽")
        meta.lore = listOf(
            bar,
            "§7スキマダンジョンで刈り取ってきた、ワールドの芽。",
            "§7おあげちゃんに渡すと、アイテムと交換してもらえる。",
            bar
        )
        meta.setEnchantmentGlintOverride(true)
        meta.persistentDataContainer.set(ITEM_TYPE_KEY, PersistentDataType.STRING, "world_sprout")
        item.itemMeta = meta
    }

    fun applyCompassMetadata(item: ItemStack, player: Player?, tier: Int) {
        val plugin = CCContent.instance
        val config = SukimaConfigHelper.getConfig(plugin)
        val meta = item.itemMeta ?: return
        val normalizedTier = tier.coerceIn(1, 3)
        val radius = config.getDouble("compass.radius", 48.0)
        val time = config.getInt("compass.time.tier$normalizedTier", when (normalizedTier) {
            1 -> 30
            2 -> 20
            else -> 10
        })
        val cooldown = config.getInt("compass.cooldown.tier$normalizedTier", when (normalizedTier) {
            1 -> 60
            2 -> 40
            else -> 20
        })

        val cooldownGroup = NamespacedKey("sukimadungeon", "compass")
        val cooldownComponent = meta.getUseCooldown()
        cooldownComponent.setCooldownSeconds(cooldown.toFloat())
        cooldownComponent.setCooldownGroup(cooldownGroup)
        meta.setUseCooldown(cooldownComponent)

        val bar = MessageManager.getMessage(player, "common_bar")
        meta.setMaxStackSize(1)
        meta.setDisplayName("§bワールドの芽コンパス [Tier $normalizedTier]")
        meta.lore = listOf(
            bar,
            "§7スキマダンジョン内で§bShiftを長押し§7ことで、近くにある",
            "§aワールドの芽§7の場所を知ることができます！",
            "",
            "§7探知半径: §b${radius.toInt()} block",
            "§7最大展開: §b${time} 秒",
            "§7クールタイム: §b${cooldown} 秒",
            bar
        )
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        meta.persistentDataContainer.set(ITEM_TYPE_KEY, PersistentDataType.STRING, "compass")
        meta.persistentDataContainer.set(COMPASS_RADIUS_KEY, PersistentDataType.DOUBLE, radius)
        meta.persistentDataContainer.set(COMPASS_TIME_KEY, PersistentDataType.INTEGER, time)
        meta.persistentDataContainer.set(COMPASS_COOLDOWN_KEY, PersistentDataType.INTEGER, cooldown)
        item.itemMeta = meta
    }
}
