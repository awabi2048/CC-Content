package jp.awabi2048.cccontent.items.misc

import io.papermc.paper.datacomponent.DataComponentTypes
import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class ExpGeneratorItem : CustomItem {
    override val feature: String = "misc"
    override val id: String = "exp_generator"
    override val displayName: String = "§bEXPジェネレータ"
    override val lore: List<String> = listOf(
        "§7不思議な力で §aEXP §7を動力に変換する機構",
        "§7仕組みはよくわからない"
    )
    override val itemModel: NamespacedKey = NamespacedKey.minecraft("ender_eye")

    private val itemKey = NamespacedKey("cccontent", "exp_generator")

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        meta.displayName(Component.text(name))
        meta.lore(CustomItemI18n.lore(player, "custom_items.$feature.$id.lore", lore))
        meta.setItemModel(itemModel)
        meta.persistentDataContainer.set(itemKey, PersistentDataType.BYTE, 1)
        
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(itemKey, PersistentDataType.BYTE)
    }
}
