package jp.awabi2048.cccontent.items

import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

/**
 * カスタムアイテムの基本インターフェース
 * 全てのカスタムアイテムはこのインターフェースを実装する
 */
interface CustomItem {
    /** フィーチャー名（misc/arena/sukima_dungeon） */
    val feature: String
    
    /** アイテムID（big_lightなど） */
    val id: String
    
    /** 完全なID（feature.id） */
    val fullId: String
        get() = "$feature.$id"
    
    /** 表示名 */
    val displayName: String
    
    /** 説明文（複数行対応） */
    val lore: List<String>
        get() = emptyList()
    
    /**
     * アイテムを生成
     * @param amount 数量
     * @return 生成されたItemStack
     */
    fun createItem(amount: Int = 1): ItemStack
    
    /**
     * ItemStackがこのカスタムアイテムと一致するか判定
     * @param item 比較対象のItemStack
     * @return 一致する場合true
     */
    fun matches(item: ItemStack): Boolean
    
    /**
     * 右クリック時の処理（オプション）
     * @param player アクションを行ったプレイヤー
     * @param event PlayerInteractEvent
     */
    fun onRightClick(player: Player, event: PlayerInteractEvent) {}
    
    /**
     * 左クリック時の処理（オプション）
     * @param player アクションを行ったプレイヤー
     * @param event PlayerInteractEvent
     */
    fun onLeftClick(player: Player, event: PlayerInteractEvent) {}
    
    /**
     * インベントリクリック時の処理（オプション）
     * @param player アクションを行ったプレイヤー
     * @param event InventoryClickEvent
     */
    fun onInventoryClick(player: Player, event: InventoryClickEvent) {}
}
