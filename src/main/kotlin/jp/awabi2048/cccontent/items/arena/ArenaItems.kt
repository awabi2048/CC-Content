package jp.awabi2048.cccontent.items.arena

import jp.awabi2048.cccontent.items.CustomItem
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.NamespacedKey
import org.bukkit.Bukkit
import org.bukkit.persistence.PersistentDataType
import net.kyori.adventure.text.Component

class SoulBottleItem : CustomItem {
    override val feature = "arena"
    override val id = "soul_bottle"
    override val displayName = "§6魂の瓶"
    override val lore = listOf("§7モブを倒すことでチャージされる")
    
    override fun createItem(amount: Int): ItemStack {
         val item = ItemStack(Material.GLASS_BOTTLE, amount)
         val meta = item.itemMeta ?: return item
         meta.displayName(Component.text(displayName))
         meta.lore(lore.map { Component.text(it) })
         meta.setCustomModelData(2001)
         meta.persistentDataContainer.set(
             NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "arena_item"),
             PersistentDataType.STRING, "soul_bottle"
         )
         item.itemMeta = meta
         return item
     }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.GLASS_BOTTLE) return false
        val meta = item.itemMeta ?: return false
        return (meta.hasCustomModelData() && meta.customModelData == 2001)
    }
}

class BoosterItem : CustomItem {
    override val feature = "arena"
    override val id = "booster"
    override val displayName = "§6ブースター"
    override val lore = listOf("§7ステータスを一時的に上昇させる")
    
    override fun createItem(amount: Int): ItemStack {
         val item = ItemStack(Material.ENDER_EYE, amount)
         val meta = item.itemMeta ?: return item
         meta.displayName(Component.text(displayName))
         meta.lore(lore.map { Component.text(it) })
         meta.setCustomModelData(2002)
         meta.persistentDataContainer.set(
             NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "arena_item"),
             PersistentDataType.STRING, "booster"
         )
         item.itemMeta = meta
         return item
     }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.ENDER_EYE) return false
        val meta = item.itemMeta ?: return false
        return (meta.hasCustomModelData() && meta.customModelData == 2002)
    }
}

class MobDropSackItem : CustomItem {
    override val feature = "arena"
    override val id = "mob_drop_sack"
    override val displayName = "§6モブドロップサック"
    override val lore = listOf("§7モブのドロップを集めるアイテム")
    
    override fun createItem(amount: Int): ItemStack {
         val item = ItemStack(Material.BUNDLE, amount)
         val meta = item.itemMeta ?: return item
         meta.displayName(Component.text(displayName))
         meta.lore(lore.map { Component.text(it) })
         meta.setCustomModelData(2003)
         meta.persistentDataContainer.set(
             NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "arena_item"),
             PersistentDataType.STRING, "mob_drop_sack"
         )
         item.itemMeta = meta
         return item
     }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.BUNDLE) return false
        val meta = item.itemMeta ?: return false
        return (meta.hasCustomModelData() && meta.customModelData == 2003)
    }
}

class HunterTalismanItem : CustomItem {
    override val feature = "arena"
    override val id = "hunter_talisman"
    override val displayName = "§6ハンタータリスマン"
    override val lore = listOf("§7ハンター職の能力を強化するタリスマン")
    
    override fun createItem(amount: Int): ItemStack {
         val item = ItemStack(Material.AMETHYST_SHARD, amount)
         val meta = item.itemMeta ?: return item
         meta.displayName(Component.text(displayName))
         meta.lore(lore.map { Component.text(it) })
         meta.setCustomModelData(2004)
         meta.persistentDataContainer.set(
             NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "arena_item"),
             PersistentDataType.STRING, "hunter_talisman"
         )
         item.itemMeta = meta
         return item
     }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.AMETHYST_SHARD) return false
        val meta = item.itemMeta ?: return false
        return (meta.hasCustomModelData() && meta.customModelData == 2004)
    }
}

class GolemTalismanItem : CustomItem {
    override val feature = "arena"
    override val id = "golem_talisman"
    override val displayName = "§6ゴーレムタリスマン"
    override val lore = listOf("§7ゴーレム職の能力を強化するタリスマン")
    
    override fun createItem(amount: Int): ItemStack {
         val item = ItemStack(Material.IRON_INGOT, amount)
         val meta = item.itemMeta ?: return item
         meta.displayName(Component.text(displayName))
         meta.lore(lore.map { Component.text(it) })
         meta.setCustomModelData(2005)
         meta.persistentDataContainer.set(
             NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "arena_item"),
             PersistentDataType.STRING, "golem_talisman"
         )
         item.itemMeta = meta
         return item
     }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.IRON_INGOT) return false
        val meta = item.itemMeta ?: return false
        return (meta.hasCustomModelData() && meta.customModelData == 2005)
    }
}
