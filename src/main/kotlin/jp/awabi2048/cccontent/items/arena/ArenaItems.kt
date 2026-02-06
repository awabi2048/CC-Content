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
        // ArenaItemManagerを使用して空の魂の瓶を作成
        val item = jp.awabi2048.cccontent.arena.item.ArenaItemManager.createEmptySoulBottle()
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        return jp.awabi2048.cccontent.arena.item.ArenaItemManager.isEmptySoulBottle(item)
    }
}

class BoosterItem : CustomItem {
    override val feature = "arena"
    override val id = "booster"
    override val displayName = "§6ブースター"
    override val lore = listOf("§7ステータスを一時的に上昇させる")
    
    override fun createItem(amount: Int): ItemStack {
        // ArenaItemManagerを使用してブースター（50%）を作成
        val item = jp.awabi2048.cccontent.arena.item.ArenaItemManager.getItemById("booster_50") ?: 
            throw IllegalStateException("ブースターアイテムが取得できません")
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        val id = jp.awabi2048.cccontent.arena.item.ArenaItemManager.getArenaItemId(item)
        return id in listOf("booster_50", "booster_100", "booster_150")
    }
}

class MobDropSackItem : CustomItem {
    override val feature = "arena"
    override val id = "mob_drop_sack"
    override val displayName = "§6モブドロップサック"
    override val lore = listOf("§7モブのドロップを集めるアイテム")
    
    override fun createItem(amount: Int): ItemStack {
        // ArenaItemManagerを使用してモブドロップサックを作成
        val item = jp.awabi2048.cccontent.arena.item.ArenaItemManager.getItemById("mob_drop_sack") ?: 
            throw IllegalStateException("モブドロップサックアイテムが取得できません")
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        return jp.awabi2048.cccontent.arena.item.ArenaItemManager.isMobDropSack(item)
    }
}

class HunterTalismanItem : CustomItem {
    override val feature = "arena"
    override val id = "hunter_talisman"
    override val displayName = "§6ハンタータリスマン"
    override val lore = listOf("§7ハンター職の能力を強化するタリスマン")
    
    override fun createItem(amount: Int): ItemStack {
        // ArenaItemManagerを使用してハンタータリスマンを作成
        val item = jp.awabi2048.cccontent.arena.item.ArenaItemManager.getItemById("talisman_hunter") ?: 
            throw IllegalStateException("ハンタータリスマンアイテムが取得できません")
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        return jp.awabi2048.cccontent.arena.item.ArenaItemManager.isTalisman(item) &&
               jp.awabi2048.cccontent.arena.item.ArenaItemManager.getArenaItemId(item) == "talisman_hunter"
    }
}

class GolemTalismanItem : CustomItem {
    override val feature = "arena"
    override val id = "golem_talisman"
    override val displayName = "§6ゴーレムタリスマン"
    override val lore = listOf("§7ゴーレム職の能力を強化するタリスマン")
    
    override fun createItem(amount: Int): ItemStack {
        // ArenaItemManagerを使用してゴーレムタリスマンを作成
        val item = jp.awabi2048.cccontent.arena.item.ArenaItemManager.getItemById("talisman_golem") ?: 
            throw IllegalStateException("ゴーレムタリスマンアイテムが取得できません")
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        return jp.awabi2048.cccontent.arena.item.ArenaItemManager.isTalisman(item) &&
               jp.awabi2048.cccontent.arena.item.ArenaItemManager.getArenaItemId(item) == "talisman_golem"
    }
}

class TicketNormalItem : CustomItem {
    override val feature = "arena"
    override val id = "ticket_normal"
    override val displayName = "§6アリーナチケット"
    override val lore = listOf("§7通常アリーナ用のチケット")
    
    override fun createItem(amount: Int): ItemStack {
        val item = jp.awabi2048.cccontent.arena.item.ArenaItemManager.getItemById("ticket_normal") ?: 
            throw IllegalStateException("アリーナチケットアイテムが取得できません")
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        val id = jp.awabi2048.cccontent.arena.item.ArenaItemManager.getArenaItemId(item)
        return id == "ticket_normal"
    }
}

class TicketBossItem : CustomItem {
    override val feature = "arena"
    override val id = "ticket_boss"
    override val displayName = "§6上級アリーナチケット"
    override val lore = listOf("§7上級アリーナ用のチケット")
    
    override fun createItem(amount: Int): ItemStack {
        val item = jp.awabi2048.cccontent.arena.item.ArenaItemManager.getItemById("ticket_boss") ?: 
            throw IllegalStateException("上級アリーナチケットアイテムが取得できません")
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        val id = jp.awabi2048.cccontent.arena.item.ArenaItemManager.getArenaItemId(item)
        return id == "ticket_boss"
    }
}

class TicketQuickItem : CustomItem {
    override val feature = "arena"
    override val id = "ticket_quick"
    override val displayName = "§6クイックアリーナチケット"
    override val lore = listOf("§7クイックアリーナ用のチケット")
    
    override fun createItem(amount: Int): ItemStack {
        val item = jp.awabi2048.cccontent.arena.item.ArenaItemManager.getItemById("ticket_quick") ?: 
            throw IllegalStateException("クイックアリーナチケットアイテムが取得できません")
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        val id = jp.awabi2048.cccontent.arena.item.ArenaItemManager.getArenaItemId(item)
        return id == "ticket_quick"
    }
}

class SoulFragmentItem : CustomItem {
    override val feature = "arena"
    override val id = "soul_fragment"
    override val displayName = "§6ソウルフラグメント"
    override val lore = listOf("§7アリーナ入場用の捧げ物")
    
    override fun createItem(amount: Int): ItemStack {
        val item = jp.awabi2048.cccontent.arena.item.ArenaItemManager.getItemById("soul_fragment") ?: 
            throw IllegalStateException("ソウルフラグメントアイテムが取得できません")
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        return jp.awabi2048.cccontent.arena.item.ArenaItemManager.isSoulFragment(item)
    }
}

class QuestTicketItem : CustomItem {
    override val feature = "arena"
    override val id = "quest_ticket"
    override val displayName = "§6クエストチケット"
    override val lore = listOf("§7クエスト報酬用のチケット")
    
    override fun createItem(amount: Int): ItemStack {
        val item = jp.awabi2048.cccontent.arena.item.ArenaItemManager.getItemById("quest_ticket") ?: 
            throw IllegalStateException("クエストチケットアイテムが取得できません")
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        val id = jp.awabi2048.cccontent.arena.item.ArenaItemManager.getArenaItemId(item)
        return id == "quest_ticket"
    }
}
