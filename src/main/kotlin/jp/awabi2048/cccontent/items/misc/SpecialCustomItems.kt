package jp.awabi2048.cccontent.items.misc

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.Consumable
import io.papermc.paper.datacomponent.item.FoodProperties
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation
import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import jp.awabi2048.cccontent.items.CustomItemManager
import net.kyori.adventure.text.Component
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.entity.ThrownExpBottle
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object SpecialCustomItemKeys {
    val SPARKLING_STONE = NamespacedKey("cccontent", "sparkling_stone")
    val SWEET_BERRY = NamespacedKey("cccontent", "delicious_sweet_berry")
    val PICKAXE = NamespacedKey("cccontent", "old_pickaxe")
    val BOOTS = NamespacedKey("cccontent", "worn_boots")
    val EXPERIENCE_BOTTLE = NamespacedKey("cccontent", "large_experience_bottle")
}

private fun applyLocalizedMeta(
    meta: ItemMeta,
    player: Player?,
    feature: String,
    id: String,
    displayName: String,
    lore: List<String>
) {
    val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
    val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)
    meta.displayName(Component.text(name))
    meta.lore(localizedLore.map { Component.text(it) })
}

class SparklingStoneItem : CustomItem {
    override val feature: String = "misc"
    override val id: String = "sparkling_stone"
    override val displayName: String = "§bキラキラの石"
    override val lore: List<String> = listOf(
        "§7おあげちゃんが拾ったきれいな石",
        "§7ダイヤモンドに負けず劣らずの輝き"
    )
    override val itemModel: NamespacedKey = NamespacedKey.minecraft("resin_clump")

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        applyLocalizedMeta(meta, player, feature, id, displayName, lore)
        meta.setItemModel(itemModel)
        meta.persistentDataContainer.set(SpecialCustomItemKeys.SPARKLING_STONE, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(SpecialCustomItemKeys.SPARKLING_STONE, PersistentDataType.BYTE)
    }
}

class ExquisiteSweetBerryItem : CustomItem {
    override val feature: String = "misc"
    override val id: String = "delicious_sweet_berry"
    override val displayName: String = "§d絶品スイートベリー"
    override val lore: List<String> = listOf(
        "§7おあげちゃんがおやつにとっておいたスイートベリー",
        "§7とっても甘くておいしい"
    )
    override val keepConsumableComponent: Boolean = true
    override val itemModel: NamespacedKey = NamespacedKey.minecraft("sweet_berries")

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        applyLocalizedMeta(meta, player, feature, id, displayName, lore)
        meta.setItemModel(itemModel)
        item.itemMeta = meta

        item.setData(
            DataComponentTypes.FOOD,
            FoodProperties.food()
                .nutrition(6)
                .saturation(6.0f)
                .build()
        )
        item.setData(
            DataComponentTypes.CONSUMABLE,
            Consumable.consumable()
                .consumeSeconds(5.0f)
                .animation(ItemUseAnimation.EAT)
                .hasConsumeParticles(false)
                .addEffect(
                    ConsumeEffect.applyStatusEffects(
                        listOf(PotionEffect(PotionEffectType.REGENERATION, 100, 0, false, false, false)),
                        1.0f
                    )
                )
                .build()
        )

        item.itemMeta = meta
        meta.persistentDataContainer.set(SpecialCustomItemKeys.SWEET_BERRY, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(SpecialCustomItemKeys.SWEET_BERRY, PersistentDataType.BYTE)
    }
}

class OldPickaxeItem : CustomItem {
    override val feature: String = "misc"
    override val id: String = "old_pickaxe"
    override val displayName: String = "§7古びたつるはし"
    override val lore: List<String> = listOf(
        "§7おあげちゃんが庭で掘り出した、文字通りの掘り出しもの",
        "§d§oガイアブレーカー §7という銘が刻まれている",
        "§7ずいぶん古びていて、今にも折れてしまいそうだ"
    )

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.STONE_PICKAXE, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        applyLocalizedMeta(meta, player, feature, id, displayName, lore)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        meta.addAttributeModifier(
            Attribute.BLOCK_BREAK_SPEED,
            AttributeModifier(
                SpecialCustomItemKeys.PICKAXE,
                0.05,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.OFFHAND
            )
        )
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.STONE_PICKAXE) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(SpecialCustomItemKeys.PICKAXE, PersistentDataType.BYTE)
    }
}

class WornBootsItem : CustomItem {
    override val feature: String = "misc"
    override val id: String = "worn_boots"
    override val displayName: String = "§7くたくたになったブーツ"
    override val lore: List<String> = listOf(
        "§7おあげちゃんが庭で掘り出した、文字通りの掘り出しもの",
        "§2§o7 League Boots (7 リーグブーツ) §7という銘が刻まれている",
        "§7ずいぶん古びていて、とても履けそうにない"
    )

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.CHAINMAIL_BOOTS, amount.coerceAtLeast(1))
        val meta = item.itemMeta ?: return item
        applyLocalizedMeta(meta, player, feature, id, displayName, lore)
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        meta.addAttributeModifier(
            Attribute.MOVEMENT_SPEED,
            AttributeModifier(
                SpecialCustomItemKeys.BOOTS,
                0.005,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.FEET
            )
        )
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.CHAINMAIL_BOOTS) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(SpecialCustomItemKeys.BOOTS, PersistentDataType.BYTE)
    }
}

class LargeExperienceBottleItem : CustomItem {
    override val feature: String = "misc"
    override val id: String = "large_experience_bottle"
    override val displayName: String = "§a大きなエンチャント瓶"
    override val lore: List<String> = listOf(
        "§7通常のエンチャント瓶よりも多くの力を秘めた、高濃度のエンチャント瓶",
        "§7右クリックで使用可能"
    )
    override val itemModel: NamespacedKey = NamespacedKey.minecraft("experience_bottle")

    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, amount.coerceIn(1, 16))
        val meta = item.itemMeta ?: return item
        applyLocalizedMeta(meta, player, feature, id, displayName, lore)
        meta.setItemModel(itemModel)
        meta.setMaxStackSize(16)
        meta.persistentDataContainer.set(SpecialCustomItemKeys.EXPERIENCE_BOTTLE, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(SpecialCustomItemKeys.EXPERIENCE_BOTTLE, PersistentDataType.BYTE)
    }

    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        if (CustomItemManager.identify(item) != this) return

        event.isCancelled = true
        val bottle = player.launchProjectile(ThrownExpBottle::class.java)
        bottle.velocity = player.eyeLocation.direction.clone().normalize().multiply(0.7).add(Vector(0.0, 0.1, 0.0))
        bottle.persistentDataContainer.set(SpecialCustomItemKeys.EXPERIENCE_BOTTLE, PersistentDataType.BYTE, 1)
        if (player.gameMode == GameMode.CREATIVE) return

        when (event.hand) {
            EquipmentSlot.HAND -> consumeOne(player.inventory.itemInMainHand).also { player.inventory.setItemInMainHand(it) }
            EquipmentSlot.OFF_HAND -> consumeOne(player.inventory.itemInOffHand).also { player.inventory.setItemInOffHand(it) }
            else -> Unit
        }
    }

    private fun consumeOne(item: ItemStack): ItemStack {
        if (item.amount <= 1) return ItemStack(Material.AIR)
        item.amount -= 1
        return item
    }
}
