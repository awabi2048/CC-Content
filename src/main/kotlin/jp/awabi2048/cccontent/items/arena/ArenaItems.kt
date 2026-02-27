package jp.awabi2048.cccontent.items.arena

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.items.CustomItemI18n
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.NamespacedKey
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import net.kyori.adventure.text.Component

class ArenaTicketItem : CustomItem {
    override val feature = "arena"
    override val id = "ticket"
    override val displayName = "§6アリーナチケット"
    override val lore = listOf("§7アリーナへの参加チケット")
    
    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.PAPER, amount)
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)
        meta.displayName(Component.text(name))
        meta.lore(localizedLore.map { Component.text(it) })
        meta.setCustomModelData(4001)
        meta.persistentDataContainer.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "arena_item"),
            PersistentDataType.STRING, "ticket"
        )
        item.itemMeta = meta
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.PAPER) return false
        val meta = item.itemMeta ?: return false
        return (meta.hasCustomModelData() && meta.customModelData == 4001)
    }
}

class ArenaMedalItem : CustomItem {
    override val feature = "arena"
    override val id = "medal"
    override val displayName = "§6アリーナメダル"
    override val lore = listOf("§7アリーナでの戦いの証")
    
    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.SUNFLOWER, amount)
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)
        meta.displayName(Component.text(name))
        meta.lore(localizedLore.map { Component.text(it) })
        meta.setCustomModelData(4002)
        meta.persistentDataContainer.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "arena_item"),
            PersistentDataType.STRING, "medal"
        )
        item.itemMeta = meta
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.SUNFLOWER) return false
        val meta = item.itemMeta ?: return false
        return (meta.hasCustomModelData() && meta.customModelData == 4002)
    }
}

class ArenaPrizeItem : CustomItem {
    override val feature = "arena"
    override val id = "prize"
    override val displayName = "§6アリーナ報酬箱"
    override val lore = listOf("§7アリーナでの勝利報酬")
    
    override fun createItem(amount: Int): ItemStack = createItemForPlayer(null, amount)

    override fun createItemForPlayer(player: Player?, amount: Int): ItemStack {
        val item = ItemStack(Material.CHEST, amount)
        val meta = item.itemMeta ?: return item
        val name = CustomItemI18n.text(player, "custom_items.$feature.$id.name", displayName)
        val localizedLore = CustomItemI18n.list(player, "custom_items.$feature.$id.lore", lore)
        meta.displayName(Component.text(name))
        meta.lore(localizedLore.map { Component.text(it) })
        meta.setCustomModelData(4003)
        meta.persistentDataContainer.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "arena_item"),
            PersistentDataType.STRING, "prize"
        )
        item.itemMeta = meta
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.CHEST) return false
        val meta = item.itemMeta ?: return false
        return (meta.hasCustomModelData() && meta.customModelData == 4003)
    }
}
