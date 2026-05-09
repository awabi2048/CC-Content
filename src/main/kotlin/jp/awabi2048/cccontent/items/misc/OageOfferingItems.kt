package jp.awabi2048.cccontent.items.misc

import com.destroystokyo.paper.profile.ProfileProperty
import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

object BoxedDaiginjoItem : CustomItem {
    private const val TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTYyMzRhZTdkNTU5MDNlYThiYzM0NDEzY2Q1MmRlZDNiMzdjOTJlZWU1YWU1MzNmYzUxMjZhNjU0NjFmMTFmIn19fQ=="

    override val feature: String = "misc"
    override val id: String = "boxed_daiginjo"
    override val displayName: String = "§f箱入り大吟醸"
    override val lore: List<String> = listOf(
        "§7ラベリングされた★5の大吟醸が入った箱",
        "§eおあげ神社に奉納できます"
    )
    override val canPlace: Boolean = false
    override val canStack: Boolean = false

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD, amount.coerceAtLeast(1))
        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.displayName(Component.text(CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)))
        meta.lore(CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore).map { Component.text(it) })
        meta.playerProfile = Bukkit.createProfile("boxed_daiginjo").also {
            it.setProperty(ProfileProperty("textures", TEXTURE))
        }
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        val meta = item.itemMeta as? SkullMeta ?: return false
        return meta.playerProfile?.properties?.any { it.name == "textures" && it.value == TEXTURE } == true
    }
}
