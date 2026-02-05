package jp.awabi2048.cccontent.items.misc

import jp.awabi2048.cccontent.items.CustomItem
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import org.bukkit.Bukkit
import net.kyori.adventure.text.Component

class BigLight : CustomItem {
    override val feature = "misc"
    override val id = "big_light"
    override val displayName = "§6ビッグライト"
    override val lore = listOf("§7右クリック長押しで", "§7プレイヤーを大きくする")
    
    override fun createItem(amount: Int): ItemStack {
         val item = ItemStack(Material.WOODEN_PICKAXE, amount)
         val meta = item.itemMeta ?: return item
         meta.displayName(Component.text(displayName))
         meta.lore(lore.map { Component.text(it) })
         meta.setCustomModelData(1001)
         meta.persistentDataContainer.set(
             NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "gulliver_type"),
             PersistentDataType.STRING, "big_light"
         )
         item.itemMeta = meta
         return item
     }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.WOODEN_PICKAXE) return false
        return item.itemMeta?.let {
            (it.hasCustomModelData() && it.customModelData == 1001)
        } ?: false
    }
}

class SmallLight : CustomItem {
    override val feature = "misc"
    override val id = "small_light"
    override val displayName = "§6スモールライト"
    override val lore = listOf("§7右クリック長押しで", "§7プレイヤーを小さくする")
    
    override fun createItem(amount: Int): ItemStack {
         val item = ItemStack(Material.WOODEN_PICKAXE, amount)
         val meta = item.itemMeta ?: return item
         meta.displayName(Component.text(displayName))
         meta.lore(lore.map { Component.text(it) })
         meta.setCustomModelData(1002)
         meta.persistentDataContainer.set(
             NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "gulliver_type"),
             PersistentDataType.STRING, "small_light"
         )
         item.itemMeta = meta
         return item
     }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.WOODEN_PICKAXE) return false
        return item.itemMeta?.let {
            (it.hasCustomModelData() && it.customModelData == 1002)
        } ?: false
    }
}
