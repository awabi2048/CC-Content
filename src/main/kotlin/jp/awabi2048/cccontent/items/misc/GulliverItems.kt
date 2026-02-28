package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.Consumable
import io.papermc.paper.datacomponent.item.Tool
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import net.kyori.adventure.text.Component

object GulliverItems {
    val BIG_LIGHT_KEY = NamespacedKey("gulliver_light", "big_light")
    val SMALL_LIGHT_KEY = NamespacedKey("gulliver_light", "small_light")
}

class BigLight : CustomItem {
    override val feature = "misc"
    override val id = "big_light"
    override val displayName = "§dビッグライト"
    override val keepConsumableComponent = true
    override val lore = listOf(
        "§e§n右クリックを長押し§7して、体を大きくすることができます！",
        "§e§nShift + 右クリック§7で元の大きさに戻ることができます。"
    )
    
    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount)
        val meta = item.itemMeta ?: return item

        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)

        meta.displayName(Component.text(name))
        meta.lore(localizedLore.map { Component.text(it) })

        // Set item model to spyglass
        meta.setItemModel(NamespacedKey.minecraft("spyglass"))

        // Mark as Big Light
        meta.persistentDataContainer.set(GulliverItems.BIG_LIGHT_KEY, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        
        // Remove tool and attribute modifiers
        item.setData(DataComponentTypes.TOOL, Tool.tool().build())
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build())
        
        // Make it consumable to allow "holding right click"
        val consumable = Consumable.consumable()
            .consumeSeconds(1000000f)
            .animation(ItemUseAnimation.NONE)
            .hasConsumeParticles(false)
            .build()
        item.setData(DataComponentTypes.CONSUMABLE, consumable)

        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(GulliverItems.BIG_LIGHT_KEY, PersistentDataType.BYTE)
    }
}

class SmallLight : CustomItem {
    override val feature = "misc"
    override val id = "small_light"
    override val displayName = "§dスモールライト"
    override val keepConsumableComponent = true
    override val lore = listOf(
        "§e§n右クリックを長押し§7して、体を縮ませることができます！",
        "§e§nShift + 右クリック§7で元の大きさに戻ることができます。"
    )
    
    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount)
        val meta = item.itemMeta ?: return item

        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)

        meta.displayName(Component.text(name))
        meta.lore(localizedLore.map { Component.text(it) })

        // Set item model to spyglass
        meta.setItemModel(NamespacedKey.minecraft("spyglass"))

        // Mark as Small Light
        meta.persistentDataContainer.set(GulliverItems.SMALL_LIGHT_KEY, PersistentDataType.BYTE, 1)

        item.itemMeta = meta
        
        // Remove tool and attribute modifiers
        item.setData(DataComponentTypes.TOOL, Tool.tool().build())
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build())
        
        // Make it consumable
        val consumable = Consumable.consumable()
            .consumeSeconds(1000000f)
            .animation(ItemUseAnimation.NONE)
            .hasConsumeParticles(false)
            .build()
        item.setData(DataComponentTypes.CONSUMABLE, consumable)
        
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(GulliverItems.SMALL_LIGHT_KEY, PersistentDataType.BYTE)
    }
}
