package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.Random

/**
 * ルート管理クラス
 * ダンジョン内のモブドロップ・宝箱アイテムを管理
 */
class LootManager(private val plugin: JavaPlugin) {
    
    /**
     * ドロップテーブル
     */
    data class LootTable(
        val items: List<LootItem>
    )
    
    /**
     * ドロップアイテム定義
     */
    data class LootItem(
        val material: Material,
        val minAmount: Int = 1,
        val maxAmount: Int = 1,
        val dropChance: Double = 1.0  // 0.0～1.0
    )
    
    private val random = Random()
    
    // ドロップテーブル（難易度 -> アイテムリスト）
    private val lootTables: MutableMap<String, LootTable> = mutableMapOf()
    
    /**
     * ドロップテーブルを初期化
     */
    init {
        initializeDefaultLootTables()
    }
    
    /**
     * デフォルトドロップテーブルを初期化
     */
    private fun initializeDefaultLootTables() {
        // 通常モブドロップ
        lootTables["common"] = LootTable(listOf(
            LootItem(Material.DIAMOND, 1, 2, 0.3),
            LootItem(Material.EMERALD, 2, 4, 0.5),
            LootItem(Material.GOLD_INGOT, 1, 3, 0.4),
            LootItem(Material.IRON_INGOT, 2, 5, 0.6)
        ))
        
        // ボスドロップ
        lootTables["boss"] = LootTable(listOf(
            LootItem(Material.DIAMOND_BLOCK, 1, 2, 0.8),
            LootItem(Material.NETHERITE_INGOT, 1, 1, 0.3),
            LootItem(Material.ENCHANTED_GOLDEN_APPLE, 1, 1, 0.2),
            LootItem(Material.DIAMOND, 3, 5, 0.9)
        ))
        
        // 宝箱ドロップ
        lootTables["treasure"] = LootTable(listOf(
            LootItem(Material.DIAMOND, 5, 10, 0.9),
            LootItem(Material.EMERALD_BLOCK, 1, 2, 0.5),
            LootItem(Material.GOLD_BLOCK, 2, 4, 0.6),
            LootItem(Material.ENCHANTED_GOLDEN_APPLE, 2, 3, 0.4)
        ))
    }
    
    /**
     * ドロップを実行
     * @param location ドロップ位置
     * @param tableKey ドロップテーブルキー
     */
    fun dropLoot(location: Location, tableKey: String = "common") {
        val lootTable = lootTables[tableKey] ?: lootTables["common"] ?: return
        
        val world = location.world ?: return
        
        for (lootItem in lootTable.items) {
            // ドロップ確率をチェック
            if (random.nextDouble() > lootItem.dropChance) {
                continue
            }
            
            // アイテム数をランダム決定
            val amount = if (lootItem.minAmount == lootItem.maxAmount) {
                lootItem.minAmount
            } else {
                random.nextInt(lootItem.maxAmount - lootItem.minAmount + 1) + lootItem.minAmount
            }
            
            // アイテムをドロップ
            val itemStack = ItemStack(lootItem.material, amount)
            world.dropItem(location, itemStack)
        }
        
        plugin.logger.info("[SukimaDungeon] ルートをドロップしました: $tableKey")
    }
    
    /**
     * ドロップテーブルを登録
     * @param key テーブルキー
     * @param lootTable ドロップテーブル
     */
    fun registerLootTable(key: String, lootTable: LootTable) {
        lootTables[key] = lootTable
        plugin.logger.info("[SukimaDungeon] ドロップテーブルを登録しました: $key")
    }
    
    /**
     * ドロップテーブルを取得
     * @param key テーブルキー
     * @return ドロップテーブル、見つからない場合は null
     */
    fun getLootTable(key: String): LootTable? {
        return lootTables[key]
    }
    
    /**
     * 全ドロップテーブルを取得
     * @return ドロップテーブルのマップ
     */
    fun getAllLootTables(): Map<String, LootTable> {
        return lootTables.toMap()
    }
    
    /**
     * ランダムなアイテムを取得
     * @param tableKey テーブルキー
     * @return アイテム、見つからない場合は null
     */
    fun getRandomLoot(tableKey: String = "common"): ItemStack? {
        val lootTable = lootTables[tableKey] ?: return null
        
        if (lootTable.items.isEmpty()) return null
        
        val lootItem = lootTable.items[random.nextInt(lootTable.items.size)]
        val amount = if (lootItem.minAmount == lootItem.maxAmount) {
            lootItem.minAmount
        } else {
            random.nextInt(lootItem.maxAmount - lootItem.minAmount + 1) + lootItem.minAmount
        }
        
        return ItemStack(lootItem.material, amount)
    }
    
    /**
     * クリーンアップ
     */
    fun cleanup() {
        lootTables.clear()
        initializeDefaultLootTables()
    }
}
