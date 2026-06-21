package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class FireproofLeggingsItem : CustomItem {
    override val feature: String = "misc"
    override val id: String = "fireproof_leggings"
    override val displayName: String = "§e耐火レギンス"
    override val lore: List<String> = listOf(
        "§7耐熱性と遮熱性にすぐれたレギンス",
        "§6爆発物を扱う道具§7に使えるかも"
    )
    override val itemModel: NamespacedKey = NamespacedKey.minecraft("chainmail_leggings")

    private val itemKey = NamespacedKey("cccontent", "fireproof_leggings")

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        meta.displayName(Component.text(name))
        meta.lore(CustomItemI18n.lore(player, "custom_items.$feature.$id.lore", lore))
        meta.setItemModel(itemModel)
        meta.persistentDataContainer.set(itemKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(itemKey, PersistentDataType.BYTE)
    }

    companion object {
        fun isWearing(player: Player): Boolean {
            val leggings = player.inventory.leggings
            return leggings?.let { FireproofLeggingsItem().matches(it) } ?: false
        }
    }
}
