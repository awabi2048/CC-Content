package jp.awabi2048.cccontent.items.sukima_dungeon

import jp.awabi2048.cccontent.items.CustomItem
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemFlag
import org.bukkit.Bukkit
import org.bukkit.persistence.PersistentDataType
import net.kyori.adventure.text.Component
import org.bukkit.inventory.meta.ItemMeta

/**
 * ワールドの芽（World Sprout）アイテム
 */
class SproutItem : CustomItem {
    override val feature = "sukima_dungeon"
    override val id = "sprout"
    override val displayName = "§dワールドの芽"
    override val lore = listOf(
        "§7§m――――――――――――――――――――――――――――――",
        "§7スキマダンジョンで刈り取ってきた、",
        "§7ワールドの芽。",
        "§7おあげちゃんに渡すと、アイテムと",
        "§7交換してもらえる。",
        "§7§m――――――――――――――――――――――――――――――"
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, 1)
        val meta = item.itemMeta ?: return item
        
        // ItemModel を設定（POISONOUS_POTATO のモデルを world_sprout に変更）
        meta.setItemModel(NamespacedKey("sukimadungeon", "world_sprout"))
        
        // スタックサイズを1に固定
        meta.setMaxStackSize(1)
        
        // 表示名
        meta.displayName(Component.text(displayName))
        
        // 説明文
        meta.lore(lore.map { Component.text(it) })
        
        // エンチャントグリント表示
        meta.setEnchantmentGlintOverride(true)
        
        // アイテムフラグ設定（属性、エンチャント非表示）
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
        
        // PersistentDataContainer で識別
        meta.persistentDataContainer.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "sukima_item"),
            PersistentDataType.STRING,
            "world_sprout"
        )
        
        item.itemMeta = meta
        
        // コンポーネント設定：食べられないように設定
        item.unsetData(DataComponentTypes.CONSUMABLE)
        
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        
        val pdc = meta.persistentDataContainer
        val itemType = pdc.get(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "sukima_item"),
            PersistentDataType.STRING
        )
        
        return itemType == "world_sprout"
    }
}

/**
 * ダンジョンコンパス Tier 1
 * 探知時間: 30秒, クールタイム: 60秒, 効果: 飢餓I
 */
class CompassTier1Item : CustomItem {
    override val feature = "sukima_dungeon"
    override val id = "compass_tier1"
    override val displayName = "§bワールドの芽コンパス [Tier 1]"
    override val lore = listOf(
        "§7§m――――――――――――――――――――――――――――――",
        "§7スキマダンジョン内で§bShiftを長押し",
        "§7することで、近くにある",
        "§aワールドの芽§7の場所を知ることができます！",
        "",
        "§7探知半径: §b48 block",
        "§7最大展開: §b30 秒",
        "§7クールタイム: §b60 秒",
        "§7§m――――――――――――――――――――――――――――――"
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, 1)
        val meta = item.itemMeta ?: return item
        
        meta.setItemModel(NamespacedKey("sukimadungeon", "compass_tier1"))
        meta.setMaxStackSize(1)
        meta.displayName(Component.text(displayName))
        meta.lore(lore.map { Component.text(it) })
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
        
        // NOTE: コンポーネント設定（!consumable）はリソースパック側で定義が必要
        // Paper 1.21.8 では ItemMeta 経由での設定がまだ未実装
        
        // PDC に compass のパラメータを保存
        val pdc = meta.persistentDataContainer
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "sukima_item"),
            PersistentDataType.STRING,
            "compass"
        )
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_tier"),
            PersistentDataType.INTEGER,
            1
        )
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_radius"),
            PersistentDataType.DOUBLE,
            48.0
        )
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_time"),
            PersistentDataType.INTEGER,
            30
        )
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_cooldown"),
            PersistentDataType.INTEGER,
            60
        )
        
        item.itemMeta = meta
        
        // コンポーネント設定：食べられないように設定
        item.unsetData(DataComponentTypes.CONSUMABLE)
        
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        
        val pdc = meta.persistentDataContainer
        val itemType = pdc.get(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "sukima_item"),
            PersistentDataType.STRING
        ) ?: return false
        
        val tier = pdc.get(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_tier"),
            PersistentDataType.INTEGER
        ) ?: return false
        
        return itemType == "compass" && tier == 1
    }
}

/**
 * ダンジョンコンパス Tier 2
 * 探知時間: 20秒, クールタイム: 40秒, 効果: 飢餓I + 盲目I
 */
class CompassTier2Item : CustomItem {
    override val feature = "sukima_dungeon"
    override val id = "compass_tier2"
    override val displayName = "§bワールドの芽コンパス [Tier 2]"
    override val lore = listOf(
        "§7§m――――――――――――――――――――――――――――――",
        "§7スキマダンジョン内で§bShiftを長押し",
        "§7することで、近くにある",
        "§aワールドの芽§7の場所を知ることができます！",
        "",
        "§7探知半径: §b48 block",
        "§7最大展開: §b20 秒",
        "§7クールタイム: §b40 秒",
        "§7§m――――――――――――――――――――――――――――――"
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, 1)
        val meta = item.itemMeta ?: return item
        
        meta.setItemModel(NamespacedKey("sukimadungeon", "compass_tier2"))
        meta.setMaxStackSize(1)
        meta.displayName(Component.text(displayName))
        meta.lore(lore.map { Component.text(it) })
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
        
        // NOTE: コンポーネント設定（!consumable）はリソースパック側で定義が必要
        // Paper 1.21.8 では ItemMeta 経由での設定がまだ未実装
        
        val pdc = meta.persistentDataContainer
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "sukima_item"),
            PersistentDataType.STRING,
            "compass"
        )
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_tier"),
            PersistentDataType.INTEGER,
            2
        )
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_radius"),
            PersistentDataType.DOUBLE,
            48.0
        )
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_time"),
            PersistentDataType.INTEGER,
            20
        )
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_cooldown"),
            PersistentDataType.INTEGER,
            40
        )
        
        item.itemMeta = meta
        
        // コンポーネント設定：食べられないように設定
        item.unsetData(DataComponentTypes.CONSUMABLE)
        
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        
        val pdc = meta.persistentDataContainer
        val itemType = pdc.get(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "sukima_item"),
            PersistentDataType.STRING
        ) ?: return false
        
        val tier = pdc.get(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_tier"),
            PersistentDataType.INTEGER
        ) ?: return false
        
        return itemType == "compass" && tier == 2
    }
}

/**
 * ダンジョンコンパス Tier 3
 * 探知時間: 10秒, クールタイム: 20秒, 効果: 飢餓I + 盲目I + 鈍化II
 */
class CompassTier3Item : CustomItem {
    override val feature = "sukima_dungeon"
    override val id = "compass_tier3"
    override val displayName = "§bワールドの芽コンパス [Tier 3]"
    override val lore = listOf(
        "§7§m――――――――――――――――――――――――――――――",
        "§7スキマダンジョン内で§bShiftを長押し",
        "§7することで、近くにある",
        "§aワールドの芽§7の場所を知ることができます！",
        "",
        "§7探知半径: §b48 block",
        "§7最大展開: §b10 秒",
        "§7クールタイム: §b20 秒",
        "§7§m――――――――――――――――――――――――――――――"
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, 1)
        val meta = item.itemMeta ?: return item
        
        meta.setItemModel(NamespacedKey("sukimadungeon", "compass_tier3"))
        meta.setMaxStackSize(1)
        meta.displayName(Component.text(displayName))
        meta.lore(lore.map { Component.text(it) })
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
        
        // NOTE: コンポーネント設定（!consumable）はリソースパック側で定義が必要
        // Paper 1.21.8 では ItemMeta 経由での設定がまだ未実装
        
        val pdc = meta.persistentDataContainer
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "sukima_item"),
            PersistentDataType.STRING,
            "compass"
        )
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_tier"),
            PersistentDataType.INTEGER,
            3
        )
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_radius"),
            PersistentDataType.DOUBLE,
            48.0
        )
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_time"),
            PersistentDataType.INTEGER,
            10
        )
        pdc.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_cooldown"),
            PersistentDataType.INTEGER,
            20
        )
        
        item.itemMeta = meta
        
        // コンポーネント設定：食べられないように設定
        item.unsetData(DataComponentTypes.CONSUMABLE)
        
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        
        val pdc = meta.persistentDataContainer
        val itemType = pdc.get(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "sukima_item"),
            PersistentDataType.STRING
        ) ?: return false
        
        val tier = pdc.get(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "compass_tier"),
            PersistentDataType.INTEGER
        ) ?: return false
        
        return itemType == "compass" && tier == 3
    }
}

/**
 * おあげちゃんのお札（Talisman）
 */
class TalismanItem : CustomItem {
    override val feature = "sukima_dungeon"
    override val id = "talisman"
    override val displayName = "§6おあげちゃんのお札"
    override val lore = listOf(
        "§7§m――――――――――――――――――――――――――――――",
        "§7スキマダンジョンに送り出したクラフターを守り、",
        "§7帰り道を作るために",
        "§7おあげちゃんが渡してくれたお札。",
        "§7ダンジョン内部で使うと、",
        "§b脱出§7することができる。",
        "§7§m――――――――――――――――――――――――――――――"
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, 1)
        val meta = item.itemMeta ?: return item
        
        meta.setItemModel(NamespacedKey("sukimadungeon", "talisman"))
        meta.setMaxStackSize(1)
        meta.displayName(Component.text(displayName))
        meta.lore(lore.map { Component.text(it) })
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
        
        meta.persistentDataContainer.set(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "sukima_item"),
            PersistentDataType.STRING,
            "talisman"
        )
        
        item.itemMeta = meta
        
        // コンポーネント設定：食べられないように設定
        item.unsetData(DataComponentTypes.CONSUMABLE)
        
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        val meta = item.itemMeta ?: return false
        
        val pdc = meta.persistentDataContainer
        val itemType = pdc.get(
            NamespacedKey(Bukkit.getPluginManager().getPlugin("CC-Content")!!, "sukima_item"),
            PersistentDataType.STRING
        )
        
        return itemType == "talisman"
    }
}
