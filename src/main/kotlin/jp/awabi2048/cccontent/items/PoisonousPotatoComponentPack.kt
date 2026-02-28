package jp.awabi2048.cccontent.items

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.Consumable
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers
import io.papermc.paper.datacomponent.item.Tool
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation
import org.bukkit.inventory.ItemStack

object PoisonousPotatoComponentPack {
    fun applyShared(item: ItemStack) {
        item.setData(DataComponentTypes.TOOL, Tool.tool().build())
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build())
    }

    fun applyNonConsumable(item: ItemStack) {
        applyShared(item)
        item.unsetData(DataComponentTypes.CONSUMABLE)
    }

    fun applyHoldConsumable(
        item: ItemStack,
        consumeSeconds: Float = 1_000_000f,
        animation: ItemUseAnimation = ItemUseAnimation.NONE,
        hasParticles: Boolean = false
    ) {
        applyShared(item)
        val consumable = Consumable.consumable()
            .consumeSeconds(consumeSeconds)
            .animation(animation)
            .hasConsumeParticles(hasParticles)
            .build()
        item.setData(DataComponentTypes.CONSUMABLE, consumable)
    }
}
