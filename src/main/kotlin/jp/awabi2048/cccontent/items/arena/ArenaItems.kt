package jp.awabi2048.cccontent.items.arena

import jp.awabi2048.cccontent.items.CustomItem
import jp.awabi2048.cccontent.arena.item.ArenaItemManager
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.inventory.InventoryClickEvent

// ===== 既存アイテム =====

class SoulBottleItem : CustomItem {
    override val feature = "arena"
    override val id = "soul_bottle"
    override val displayName = "§d空の魔法の瓶"
    override val lore = listOf(
        "§7モブを倒して魂を集めよう。",
        "§7満タンになると§b魂入りの瓶§7に変化する。"
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ArenaItemManager.createEmptySoulBottle()
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        return ArenaItemManager.isEmptySoulBottle(item) || ArenaItemManager.isFilledSoulBottle(item)
    }
    
    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        // アリーナゲーム中のみ機能
        // フェーズ3で実装
    }
}

class BoosterItem : CustomItem {
    override val feature = "arena"
    override val id = "booster"
    override val displayName = "§d「アリーナ報酬ブースター」"
    override val lore = listOf(
        "§7報酬の倍率を上昇させるアイテム。",
        "§7アリーナクリア時に効果がある。"
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ArenaItemManager.getItemById("booster_50") 
            ?: ArenaItemManager.getItem(ArenaItemManager.ArenaItem.BOOSTER_50)
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        val id = ArenaItemManager.getArenaItemId(item)
        return id in listOf("booster_50", "booster_100", "booster_150")
    }
    
    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        // ブースター使用時の処理
        // フェーズ3で実装
    }
}

class MobDropSackItem : CustomItem {
    override val feature = "arena"
    override val id = "mob_drop_sack"
    override val displayName = "§bモブドロップの入れ物"
    override val lore = listOf(
        "§7モブを倒した時のドロップを",
        "§7自動的に集めるアイテム。"
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ArenaItemManager.getItemById("mob_drop_sack") 
            ?: ArenaItemManager.getItem(ArenaItemManager.ArenaItem.MOB_DROP_SACK)
        item.amount = 1  // スタックサイズ1
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        return ArenaItemManager.isMobDropSack(item)
    }
    
    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        // サック確認・空にする処理
        // フェーズ3で実装
    }
    
    override fun onInventoryClick(player: Player, event: InventoryClickEvent) {
        // インベントリでの操作制限など
        // フェーズ3で実装
    }
}

class HunterTalismanItem : CustomItem {
    override val feature = "arena"
    override val id = "hunter_talisman"
    override val displayName = "§b狩人のお守り"
    override val lore = listOf(
        "§7ハンター職の能力を強化する",
        "§7特別なお守り。"
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ArenaItemManager.getItemById("talisman_hunter") 
            ?: ArenaItemManager.getItem(ArenaItemManager.ArenaItem.TALISMAN_HUNTER)
        item.amount = 1  // スタックサイズ1
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        return ArenaItemManager.isTalisman(item) &&
               ArenaItemManager.getArenaItemId(item) == "talisman_hunter"
    }
    
    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        // ハンター職バフ効果
        // フェーズ3で実装
    }
}

class GolemTalismanItem : CustomItem {
    override val feature = "arena"
    override val id = "golem_talisman"
    override val displayName = "§bゴーレムのお守り"
    override val lore = listOf(
        "§7ゴーレム職の能力を強化する",
        "§7特別なお守り。"
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ArenaItemManager.getItemById("talisman_golem") 
            ?: ArenaItemManager.getItem(ArenaItemManager.ArenaItem.TALISMAN_GOLEM)
        item.amount = 1  // スタックサイズ1
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        return ArenaItemManager.isTalisman(item) &&
               ArenaItemManager.getArenaItemId(item) == "talisman_golem"
    }
    
    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        // ゴーレム職バフ効果
        // フェーズ3で実装
    }
}

// ===== 新規アイテム =====

class TicketNormalItem : CustomItem {
    override val feature = "arena"
    override val id = "ticket_normal"
    override val displayName = "§#00ff7fアリーナチケット"
    override val lore = listOf(
        "§8§m                    ",
        "§7通常難度のアリーナに挑戦できる。",
        "§8§m                    "
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ArenaItemManager.getItemById("ticket_normal") 
            ?: ArenaItemManager.getItem(ArenaItemManager.ArenaItem.TICKET_NORMAL)
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        return ArenaItemManager.getArenaItemId(item) == "ticket_normal"
    }
    
    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        // アリーナ入場処理
        // フェーズ3で実装
    }
}

class TicketBossItem : CustomItem {
    override val feature = "arena"
    override val id = "ticket_boss"
    override val displayName = "§#dc143c上級アリーナチケット"
    override val lore = listOf(
        "§8§m                    ",
        "§7上級難度のアリーナに挑戦できる。",
        "§8§m                    "
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ArenaItemManager.getItemById("ticket_boss") 
            ?: ArenaItemManager.getItem(ArenaItemManager.ArenaItem.TICKET_BOSS)
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        return ArenaItemManager.getArenaItemId(item) == "ticket_boss"
    }
    
    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        // アリーナ入場処理（上級）
        // フェーズ3で実装
    }
}

class TicketQuickItem : CustomItem {
    override val feature = "arena"
    override val id = "ticket_quick"
    override val displayName = "§dクイックアリーナチケット"
    override val lore = listOf(
        "§8§m                    ",
        "§7短時間でクリアできるアリーナに挑戦できる。",
        "§8§m                    "
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ArenaItemManager.getItemById("ticket_quick") 
            ?: ArenaItemManager.getItem(ArenaItemManager.ArenaItem.TICKET_QUICK)
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        return ArenaItemManager.getArenaItemId(item) == "ticket_quick"
    }
    
    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        // クイックアリーナ入場処理
        // フェーズ3で実装
    }
}

class SoulFragmentItem : CustomItem {
    override val feature = "arena"
    override val id = "soul_fragment"
    override val displayName = "§bソウルフラグメント"
    override val lore = listOf(
        "§8§m                    ",
        "§7アリーナへ入場するための捧げ物。",
        "§7強力な魂の欠片。",
        "§8§m                    "
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ArenaItemManager.getSoulFragment()
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        return ArenaItemManager.isSoulFragment(item)
    }
    
    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        // アリーナ入場時に消費
        // フェーズ3で実装
    }
}

class QuestTicketItem : CustomItem {
    override val feature = "arena"
    override val id = "quest_ticket"
    override val displayName = "§bアリーナクエスト手形"
    override val lore = listOf(
        "§8§m                    ",
        "§7クエスト達成の証。",
        "§7集めると良いことがあるかも...？",
        "§8§m                    "
    )
    
    override fun createItem(amount: Int): ItemStack {
        val item = ArenaItemManager.getItemById("quest_ticket") 
            ?: ArenaItemManager.getItem(ArenaItemManager.ArenaItem.QUEST_TICKET)
        item.amount = amount
        return item
    }
    
    override fun matches(item: ItemStack): Boolean {
        return ArenaItemManager.getArenaItemId(item) == "quest_ticket"
    }
    
    override fun onRightClick(player: Player, event: PlayerInteractEvent) {
        // クエスト報酬取得
        // フェーズ3で実装
    }
}
