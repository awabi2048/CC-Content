package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

enum class TransparentFrameKind(
    val fullId: String,
    val material: Material
) {
    NORMAL("misc.invisible_item_frame", Material.ITEM_FRAME),
    GLOW("misc.invisible_glow_item_frame", Material.GLOW_ITEM_FRAME)
}

object TransparentItemFrameKeys {
    val NORMAL_ITEM_KEY = NamespacedKey("cccontent", "invisible_item_frame")
    val GLOW_ITEM_KEY = NamespacedKey("cccontent", "invisible_glow_item_frame")
}

class TransparentItemFrameItem : CustomItem {
    override val feature = "misc"
    override val id = "invisible_item_frame"
    override val displayName = "§f透明な額縁"
    override val lore = listOf(
        "§7設置すると透明になります。",
        "§7手に持って§eスニーク§7中は周囲の透明額縁が見えるようになります。"
    )

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.ITEM_FRAME, amount)
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)
        meta.displayName(Component.text(name))
        meta.lore(localizedLore.map { Component.text(it) })
        meta.persistentDataContainer.set(TransparentItemFrameKeys.NORMAL_ITEM_KEY, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.ITEM_FRAME) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(TransparentItemFrameKeys.NORMAL_ITEM_KEY, PersistentDataType.BYTE)
    }
}

class TransparentGlowItemFrameItem : CustomItem {
    override val feature = "misc"
    override val id = "invisible_glow_item_frame"
    override val displayName = "§b透明な光る額縁"
    override val lore = listOf(
        "§7設置すると透明になります。",
        "§7手に持って§eスニーク§7中は周囲の透明額縁が見えるようになります。"
    )

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.GLOW_ITEM_FRAME, amount)
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)
        meta.displayName(Component.text(name))
        meta.lore(localizedLore.map { Component.text(it) })
        meta.persistentDataContainer.set(TransparentItemFrameKeys.GLOW_ITEM_KEY, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.GLOW_ITEM_FRAME) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(TransparentItemFrameKeys.GLOW_ITEM_KEY, PersistentDataType.BYTE)
    }
}
