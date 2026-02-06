package jp.awabi2048.cccontent.features.sukima_dungeon.gui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * GUI管理クラス
 * GUIの共通処理（インベントリ生成、アイテム配置等）を担当
 */
class GuiManager {
    
    /**
     * インベントリを作成
     * @param title インベントリのタイトル
     * @param rows 行数（1-6）
     * @return 作成されたインベントリ
     */
    fun createInventory(title: String, rows: Int): Inventory {
        require(rows in 1..6) { "行数は1～6の範囲である必要があります" }
        return org.bukkit.Bukkit.createInventory(null, rows * 9, title)
    }
    
    /**
     * アイテムを作成
     * @param material マテリアル
     * @param displayName 表示名
     * @param lore 説明文
     * @param amount 数量
     * @return 作成されたアイテム
     */
    fun createItem(
        material: Material,
        displayName: String,
        lore: List<String> = emptyList(),
        amount: Int = 1
    ): ItemStack {
        val item = ItemStack(material, amount)
        val meta = item.itemMeta
        if (meta != null) {
            // Adventure APIを使用（Paper 1.21.8 では必須）
            meta.displayName(Component.text(displayName))
            if (lore.isNotEmpty()) {
                meta.lore(lore.map { Component.text(it) })
            }
            item.itemMeta = meta
        }
        return item
    }
    
    /**
     * インベントリ内の特定のスロットにアイテムを配置
     * @param inventory インベントリ
     * @param slot スロット番号（0から始まる）
     * @param item アイテム
     */
    fun setItem(inventory: Inventory, slot: Int, item: ItemStack) {
        inventory.setItem(slot, item)
    }
    
    /**
     * 行と列からスロット番号を計算
     * @param row 行（0から始まる）
     * @param col 列（0から始まる）
     * @return スロット番号
     */
    fun getSlot(row: Int, col: Int): Int {
        return row * 9 + col
    }
    
    /**
     * ボーダーを描画（黒いステンドグラス）
     * @param inventory インベントリ
     */
    fun drawBorder(inventory: Inventory) {
        val borderItem = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val meta = borderItem.itemMeta
        if (meta != null) {
            meta.displayName(Component.text(" "))
            borderItem.itemMeta = meta
        }
        
        val maxSlot = inventory.size
        val cols = 9
        val rows = maxSlot / cols
        
        // 上下の枠線
        for (col in 0 until cols) {
            inventory.setItem(getSlot(0, col), borderItem)
            inventory.setItem(getSlot(rows - 1, col), borderItem)
        }
        
        // 左右の枠線
        for (row in 0 until rows) {
            inventory.setItem(getSlot(row, 0), borderItem)
            inventory.setItem(getSlot(row, cols - 1), borderItem)
        }
    }
    
    /**
     * プレイヤーに GUI を表示
     * @param player プレイヤー
     * @param inventory インベントリ
     */
    fun showGui(player: Player, inventory: Inventory) {
        player.openInventory(inventory)
    }
    
    /**
     * グレーアウト済みのアイテムを作成（クリック不可）
     * @param material マテリアル
     * @param displayName 表示名
     * @param lore 説明文
     * @return グレーアウトされたアイテム
     */
    fun createDisabledItem(
        material: Material,
        displayName: String,
        lore: List<String> = emptyList()
    ): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        if (meta != null) {
            meta.displayName(Component.text("§7$displayName"))
            if (lore.isNotEmpty()) {
                meta.lore(lore.map { Component.text("§7$it") })
            }
            item.itemMeta = meta
        }
        return item
    }
    
    /**
     * ページング用の次へボタンを作成
     * @return 次へボタン
     */
    fun createNextButton(): ItemStack {
        return createItem(
            Material.ARROW,
            "§a次へ",
            listOf("§7次のページに進みます")
        )
    }
    
    /**
     * ページング用の前へボタンを作成
     * @return 前へボタン
     */
    fun createPreviousButton(): ItemStack {
        return createItem(
            Material.ARROW,
            "§c前へ",
            listOf("§7前のページに戻ります")
        )
    }
    
    /**
     * キャンセルボタンを作成
     * @return キャンセルボタン
     */
    fun createCancelButton(): ItemStack {
        return createItem(
            Material.BARRIER,
            "§cキャンセル",
            listOf("§7このメニューを閉じます")
        )
    }
    
    /**
     * 確認ボタンを作成
     * @return 確認ボタン
     */
    fun createConfirmButton(): ItemStack {
        return createItem(
            Material.LIME_CONCRETE,
            "§a確認",
            listOf("§7このオプションで確定します")
        )
    }
}
