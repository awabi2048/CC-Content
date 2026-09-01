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
 * 大豆の種。支柱に右クリックで作付けするためのカスタムアイテム。
 * 外観は暫定でバニラの小麦の種モデルを流用し、本来の見た目はリソースパック側で差し替え可能。
 */
class SoybeanSeedItem : CustomItem {
    override val feature: String = "crops"
    override val id: String = "soybean_seed"
    override val displayName: String = "§a大豆の種"
    override val itemModel: NamespacedKey = NamespacedKey.minecraft("wheat_seeds")

    private val itemKey = NamespacedKey("cccontent", "crops_soybean_seed")

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text(CCSystem.getAPI().getLocalized(player, ContentCropsKeys.CROPS_SOYBEAN_SEED_NAME).replace('&', '§')))
        meta.lore(
            CCSystem.getAPI().getLocalized(player, ContentCropsKeys.CROPS_SOYBEAN_SEED_LORE)
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
