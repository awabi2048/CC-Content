package jp.awabi2048.cccontent.items.misc

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.PoisonousPotatoComponentPack
import jp.awabi2048.cccontent.util.ItemMetaCompat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
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
        val modelMaterial = if (variant.itemMaterial.isItem) variant.itemMaterial else Material.NAME_TAG
        val item = ItemStack(Material.POISONOUS_POTATO, amount)
        PoisonousPotatoComponentPack.applyNonConsumable(item)
        val meta = item.itemMeta ?: return item
        meta.displayName(Component.text(variant.itemDisplayName))
        meta.lore(variant.itemLore.map {
            Component.text(it.replace(Regex("§[0-9a-fk-or]", RegexOption.IGNORE_CASE), ""), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        })
        meta.setItemModel(NamespacedKey.minecraft(modelMaterial.key.key))
        meta.setMaxStackSize(1)
        variant.itemCustomModelData?.let { ItemMetaCompat.setLegacyCustomModelData(meta, it) }
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
