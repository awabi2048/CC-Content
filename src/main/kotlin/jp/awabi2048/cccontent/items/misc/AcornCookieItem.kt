package jp.awabi2048.cccontent.items.misc

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.Consumable
import io.papermc.paper.datacomponent.item.FoodProperties
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation
import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class AcornCookieItem : CustomItem {
    override val feature: String = "misc"
    override val id: String = "acorn_cookie"
    override val displayName: String = "§eどんぐりクッキー"
    override val lore: List<String> = listOf(
        "§7香ばしい匂いに食欲をそそられる",
        "§8§oおあげちゃん「わたしが焼きました！」"
    )
    override val itemModel: NamespacedKey = NamespacedKey.minecraft("cookie")
    override val keepConsumableComponent: Boolean = true

    private val itemKey = NamespacedKey("cccontent", "acorn_cookie")

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)
        meta.displayName(Component.text(name))
        meta.lore(localizedLore.map { Component.text(it) })
        meta.setItemModel(itemModel)
        meta.persistentDataContainer.set(itemKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta

        item.setData(
            DataComponentTypes.FOOD,
            FoodProperties.food()
                .nutrition(4)
                .saturation(4.0f)
                .build()
        )
        item.setData(
            DataComponentTypes.CONSUMABLE,
            Consumable.consumable()
                .consumeSeconds(1.6f)
                .animation(ItemUseAnimation.EAT)
                .hasConsumeParticles(true)
                .build()
        )

        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(itemKey, PersistentDataType.BYTE)
    }
}