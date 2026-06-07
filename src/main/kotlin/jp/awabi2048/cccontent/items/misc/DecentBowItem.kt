package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.persistence.PersistentDataType
import java.util.Random

class DecentBowItem : CustomItem {
    override val feature: String = "misc"
    override val id: String = "decent_bow"
    override val displayName: String = "§bなかなかな弓"
    override val lore: List<String> = listOf(
        "§7おあげちゃんが譲り受けた弓",
        "§7古びてはいるが、高いポテンシャルを持っている"
    )

    private val itemKey = NamespacedKey("cccontent", "decent_bow")
    private val random = Random()
    private val maxDurability = 384

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.BOW, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)
        meta.displayName(Component.text(name))
        meta.lore(localizedLore.map { Component.text(it) })
        meta.persistentDataContainer.set(itemKey, PersistentDataType.BYTE, 1)

        meta.addEnchant(Enchantment.INFINITY, 1, true)
        meta.addEnchant(Enchantment.MENDING, 1, true)

        if (meta is Damageable) {
            val remainingDurability = random.nextInt(1, 21)
            val damage = maxDurability - remainingDurability
            meta.damage = damage
        }

        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.BOW) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(itemKey, PersistentDataType.BYTE)
    }
}