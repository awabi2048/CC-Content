package jp.awabi2048.cccontent.features.rank.listener

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * アイテム所持判定を行うヘルパークラス
 * リスナーではなく、utility クラスとしても使用可能
 */
object TutorialInventoryHelper {
    
    /**
     * プレイヤーがインベントリに保持している指定Material のアイテム総数を取得
     * メインスロット、装備スロット、オフハンドをすべてチェック
     * @param player プレイヤー
     * @param material Material名（大文字）
     * @return 所持数
     */
    fun countItemInInventory(player: Player, material: String): Int {
        var total = 0
        
        // メインインベントリ
        total += player.inventory.contents
            .filterNotNull()
            .filter { it.type.name == material }
            .sumOf { it.amount }
        
        // 装備スロット
        total += player.inventory.armorContents
            .filterNotNull()
            .filter { it.type.name == material }
            .sumOf { it.amount }
        
        // オフハンド
        val offHandItem = player.inventory.itemInOffHand
        if (offHandItem.type.name == material) {
            total += offHandItem.amount
        }
        
        return total
    }
    
    /**
     * プレイヤーが指定Material の必要数を保持しているか判定
     * @param player プレイヤー
     * @param material Material名（大文字）
     * @param requiredAmount 必要数
     * @return 保持していればtrue
     */
    fun hasEnoughItem(player: Player, material: String, requiredAmount: Int): Boolean {
        return countItemInInventory(player, material) >= requiredAmount
    }
}
