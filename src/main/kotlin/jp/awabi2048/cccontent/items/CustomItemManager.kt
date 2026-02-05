package jp.awabi2048.cccontent.items

import org.bukkit.inventory.ItemStack
import java.util.logging.Logger

/**
 * カスタムアイテムを一元管理するオブジェクト
 * 全てのカスタムアイテムはここに登録され、ID検索やアイテム生成に使用される
 */
object CustomItemManager {
    private val items = mutableMapOf<String, CustomItem>()
    private lateinit var logger: Logger
    
    fun setLogger(logger: Logger) {
        this.logger = logger
    }
    
    /**
     * カスタムアイテムを登録
     * @param item カスタムアイテムインスタンス
     */
    fun register(item: CustomItem) {
        items[item.fullId] = item
        if (::logger.isInitialized) {
            logger.info("Registered custom item: ${item.fullId}")
        }
    }
    
    /**
     * アイテムIDでアイテムを取得
     * @param fullId feature.idの形式
     * @return CustomItemインスタンス、見つからない場合はnull
     */
    fun getItem(fullId: String): CustomItem? = items[fullId]
    
    /**
     * アイテムを生成
     * @param fullId feature.idの形式
     * @param amount 数量（デフォルト1）
     * @return 生成されたItemStack、見つからない場合はnull
     */
    fun createItem(fullId: String, amount: Int = 1): ItemStack? {
        return items[fullId]?.createItem(amount)
    }
    
    /**
     * ItemStackからカスタムアイテムを識別
     * @param item 判定するItemStack
     * @return 一致したCustomItem、見つからない場合はnull
     */
    fun identify(item: ItemStack): CustomItem? {
        return items.values.find { it.matches(item) }
    }
    
    /**
     * フィーチャーごとのアイテムリスト取得
     * @param feature フィーチャー名
     * @return 該当フィーチャーのCustomItemリスト
     */
    fun getItemsByFeature(feature: String): List<CustomItem> {
        return items.values.filter { it.feature == feature }
    }
    
    /**
     * 全アイテムID取得
     * @return 全アイテムID
     */
    fun getAllItemIds(): Set<String> = items.keys
    
    /**
     * フィーチャー別の全アイテムID取得
     * @param feature フィーチャー名
     * @return 該当フィーチャーのアイテムID一覧
     */
    fun getItemIdsByFeature(feature: String): List<String> {
        return items.values.filter { it.feature == feature }.map { it.fullId }
    }
    
    /**
     * 登録されているアイテム数を取得
     * @return アイテム数
     */
    fun getItemCount(): Int = items.size
    
    /**
     * フィーチャー別のアイテム数を取得
     * @param feature フィーチャー名
     * @return 該当フィーチャーのアイテム数
     */
    fun getItemCountByFeature(feature: String): Int {
        return items.values.count { it.feature == feature }
    }
}
