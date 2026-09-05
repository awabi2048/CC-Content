package jp.awabi2048.cccontent.items.crops

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.generated.ContentCropsKeys
import jp.awabi2048.cccontent.items.CustomItem
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * 収穫した大豆。支柱から右クリックで回収される。
 * 外観は暫定でバニラの小麦モデルを流用。
 */
class SoybeanItem : CustomItem {
    override val feature: String = "crops"
    override val id: String = "soybean"
    override val displayName: String = "§a大豆"
    override val itemModel: NamespacedKey = NamespacedKey.minecraft("wheat")

    private val itemKey = NamespacedKey("cccontent", "crops_soybean")

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text(CCSystem.getAPI().getLocalized(player, ContentCropsKeys.CROPS_SOYBEAN_NAME).replace('&', '§')))
        meta.lore(
            CCSystem.getAPI().getLocalized(player, ContentCropsKeys.CROPS_SOYBEAN_LORE)
                .map { Component.text(it.replace('&', '§')) }
        )
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
}
