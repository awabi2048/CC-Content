package jp.awabi2048.cccontent.items.sukima_dungeon

import jp.awabi2048.cccontent.items.CustomItem
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.NamespacedKey
import org.bukkit.Bukkit
import org.bukkit.persistence.PersistentDataType
import net.kyori.adventure.text.Component

class SproutItem : CustomItem {
    override val feature = "sukima_dungeon"
    override val id = "sprout"
    override val displayName = "§6スプラウト"
    override val lore = listOf("§7ダンジョン内に生える不思議な植物")
    
    override fun createItem(amount: Int): ItemStack {
         val item = ItemStack(Material.SEAGRASS, amount)
         val meta = item.itemMeta ?: return item
         meta.displayName(Component.text(displayName))
         meta.lore(lore.map { Component.text(it) })
         meta.setCustomModelData(3001)
         meta.persistentDataContainer.set(
             NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "sukima_item"),
             PersistentDataType.STRING, "sprout"
         )
         item.itemMeta = meta
         return item
     }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.SEAGRASS) return false
        val meta = item.itemMeta ?: return false
        return (meta.hasCustomModelData() && meta.customModelData == 3001)
    }
}

class CompassItem : CustomItem {
    override val feature = "sukima_dungeon"
    override val id = "compass"
    override val displayName = "§6ダンジョンコンパス"
    override val lore = listOf("§7ダンジョンの方向を示す不思議なコンパス")
    
    override fun createItem(amount: Int): ItemStack {
         val item = ItemStack(Material.COMPASS, amount)
         val meta = item.itemMeta ?: return item
         meta.displayName(Component.text(displayName))
         meta.lore(lore.map { Component.text(it) })
         meta.setCustomModelData(3002)
         meta.persistentDataContainer.set(
             NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "sukima_item"),
             PersistentDataType.STRING, "compass"
         )
         item.itemMeta = meta
         return item
     }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.COMPASS) return false
        val meta = item.itemMeta ?: return false
        return (meta.hasCustomModelData() && meta.customModelData == 3002)
    }
}

class TalismanItem : CustomItem {
    override val feature = "sukima_dungeon"
    override val id = "talisman"
    override val displayName = "§6おあげちゃんの御札"
    override val lore = listOf("§7おあげちゃんの力がこもった不思議な札")
    
    override fun createItem(amount: Int): ItemStack {
         val item = ItemStack(Material.PAINTING, amount)
         val meta = item.itemMeta ?: return item
         meta.displayName(Component.text(displayName))
         meta.lore(lore.map { Component.text(it) })
         meta.setCustomModelData(3003)
         meta.persistentDataContainer.set(
             NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "sukima_item"),
             PersistentDataType.STRING, "talisman"
         )
         item.itemMeta = meta
         return item
     }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.PAINTING) return false
        val meta = item.itemMeta ?: return false
        return (meta.hasCustomModelData() && meta.customModelData == 3003)
    }
}
