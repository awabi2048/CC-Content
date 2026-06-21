package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.Random

class DecentArmorItem(
    private val slot: EquipmentSlot,
    private val material: Material,
    private val idSuffix: String,
    private val displayNameOverride: String,
    private val loreOverride: List<String>
) : CustomItem {
    override val feature: String = "misc"
    override val id: String = "decent_$idSuffix"
    override val displayName: String = displayNameOverride
    override val lore: List<String> = loreOverride

    private val itemKey = NamespacedKey("cccontent", "decent_$idSuffix")
    private val random = Random()

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(material, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        meta.displayName(Component.text(name))
        meta.lore(CustomItemI18n.lore(player, "custom_items.$feature.$id.lore", lore))
        meta.persistentDataContainer.set(itemKey, PersistentDataType.BYTE, 1)

        meta.addEnchant(Enchantment.PROTECTION, 3, true)
        
        val additionalEnchant = if (random.nextBoolean()) {
            Enchantment.FIRE_PROTECTION
        } else {
            Enchantment.PROJECTILE_PROTECTION
        }
        meta.addEnchant(additionalEnchant, 3, true)

        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != material) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(itemKey, PersistentDataType.BYTE)
    }

    companion object {
        fun createAll(): List<DecentArmorItem> = listOf(
            DecentArmorItem(
                EquipmentSlot.HEAD,
                Material.IRON_HELMET,
                "helmet",
                "§eそこそこなヘルメット",
                listOf("§f貰いもののエンチャント防具", "§7おあげちゃんには大きすぎるとのこと")
            ),
            DecentArmorItem(
                EquipmentSlot.CHEST,
                Material.IRON_CHESTPLATE,
                "chestplate",
                "§eそこそこなチェストプレート",
                listOf("§f貰いもののエンチャント防具", "§7おあげちゃんには大きすぎるとのこと")
            ),
            DecentArmorItem(
                EquipmentSlot.LEGS,
                Material.IRON_LEGGINGS,
                "leggings",
                "§eそこそこなレギンス",
                listOf("§f貰いもののエンチャント防具", "§7おあげちゃんには大きすぎるとのこと")
            ),
            DecentArmorItem(
                EquipmentSlot.FEET,
                Material.IRON_BOOTS,
                "boots",
                "§eそこそこなブーツ",
                listOf("§f貰いもののエンチャント防具", "§7おあげちゃんには大きすぎるとのこと")
            )
        )
    }
}
