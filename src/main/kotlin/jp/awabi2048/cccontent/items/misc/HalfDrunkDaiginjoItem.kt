package jp.awabi2048.cccontent.items.misc

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.PotionContents
import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class HalfDrunkDaiginjoItem : CustomItem {
    override val feature: String = "misc"
    override val id: String = "half_drunk_daiginjo"
    override val displayName: String = "§6飲みかけの大吟醸"
    override val lore: List<String> = listOf("§7誰かがつまみ食いしたみたい")
    override val itemModel: NamespacedKey = NamespacedKey.minecraft("potion")

    private val itemKey = NamespacedKey("cccontent", "half_drunk_daiginjo")

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        meta.displayName(Component.text(name))
        meta.lore(CustomItemI18n.lore(player, "custom_items.$feature.$id.lore", lore))
        meta.setItemModel(itemModel)
        meta.persistentDataContainer.set(itemKey, PersistentDataType.BYTE, 1)
        
        val potionContents = PotionContents.potionContents()
            .potion(org.bukkit.potion.PotionType.SLOW_FALLING)
            .build()
        item.setData(DataComponentTypes.POTION_CONTENTS, potionContents)
        
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(itemKey, PersistentDataType.BYTE)
    }
}
