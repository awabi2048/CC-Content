package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItem
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class CustomHeadItem(
    private val plugin: JavaPlugin,
    val variant: CustomHeadVariant
) : CustomItem {
    override val feature: String = "misc"
    override val id: String = "custom_head.${variant.variantId}"
    override val displayName: String = variant.itemDisplayName
    override val lore: List<String> = variant.itemLore

    private val variantKey = NamespacedKey(plugin, "custom_head_variant")

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val material = if (variant.itemMaterial.isItem) variant.itemMaterial else Material.NAME_TAG
        val item = ItemStack(material, amount)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text(variant.itemDisplayName))
        meta.lore(variant.itemLore.map { Component.text(it) })
        variant.itemCustomModelData?.let { meta.setCustomModelData(it) }
        meta.persistentDataContainer.set(variantKey, PersistentDataType.STRING, variant.variantId)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.get(variantKey, PersistentDataType.STRING) == variant.variantId
    }

    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }
        event.isCancelled = true
        CustomHeadGuiListener.openSelectionGui(plugin, player, variant)
    }
}
