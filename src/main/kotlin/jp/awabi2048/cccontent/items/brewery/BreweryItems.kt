package jp.awabi2048.cccontent.items.brewery

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class BrewerySampleFilterItem(private val plugin: JavaPlugin) : CustomItem {
    override val feature: String = "brewery"
    override val id: String = "sample_filter"
    override val displayName: String = "§e蒸留フィルター（試作）"
    override val lore: List<String> = listOf(
        "§7蒸留時間 -15%",
        "§71蒸留ごとに耐久を1消費"
    )
    override val itemModel: NamespacedKey = NamespacedKey.minecraft("shears")

    private val key = NamespacedKey(plugin, "brewery_filter_sample")

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount)
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        meta.displayName(Component.text(name))
        meta.lore(CustomItemI18n.lore(player, "custom_items.$feature.$id.lore", lore))
        meta.persistentDataContainer.set(key, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(key, PersistentDataType.BYTE)
    }
}

class BreweryMockClockItem(private val plugin: JavaPlugin) : CustomItem {
    override val feature: String = "brewery"
    override val id: String = "mock_clock"
    override val displayName: String = "§bふしぎな時計（モック）"
    override val lore: List<String> = listOf("§7熟成GUIの時計スロット用（効果なし）")
    override val itemModel: NamespacedKey = NamespacedKey.minecraft("clock")

    private val key = NamespacedKey(plugin, "brewery_mock_clock")

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount)
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        meta.displayName(Component.text(name))
        meta.lore(CustomItemI18n.lore(player, "custom_items.$feature.$id.lore", lore))
        meta.persistentDataContainer.set(key, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(key, PersistentDataType.BYTE)
    }
}

class BreweryMockYeastItem(private val plugin: JavaPlugin) : CustomItem {
    override val feature: String = "brewery"
    override val id: String = "mock_yeast"
    override val displayName: String = "§a培養済み酵母（モック）"
    override val lore: List<String> = listOf("§7発酵GUIの酵母スロット用（効果なし）")
    override val itemModel: NamespacedKey = NamespacedKey.minecraft("honey_bottle")

    private val key = NamespacedKey(plugin, "brewery_mock_yeast")

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount)
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        meta.displayName(Component.text(name))
        meta.lore(CustomItemI18n.lore(player, "custom_items.$feature.$id.lore", lore))
        meta.persistentDataContainer.set(key, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(key, PersistentDataType.BYTE)
    }
}
