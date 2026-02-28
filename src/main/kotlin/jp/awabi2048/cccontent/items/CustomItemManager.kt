package jp.awabi2048.cccontent.items

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.logging.Logger

/**
 * カスタムアイテムを一元管理するオブジェクト
 * 全てのカスタムアイテムはここに登録され、ID検索やアイテム生成に使用される
 */
object CustomItemManager {
    private val items = mutableMapOf<String, CustomItem>()
    private val customItemIdKey = NamespacedKey("cccontent", "custom_item_id")
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
    }

    fun unregisterByPrefix(prefix: String): Int {
        val before = items.size
        val iterator = items.keys.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().startsWith(prefix)) {
                iterator.remove()
            }
        }
        return before - items.size
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
        return createItemForPlayer(fullId, null, amount)
    }

    fun createItemForPlayer(fullId: String, player: Player?, amount: Int = 1): ItemStack? {
        val customItem = items[fullId] ?: return null
        val created = customItem.createItemForPlayer(player, amount)
        applyCommonDataComponents(customItem, created)
        markCustomItemId(created, customItem.fullId)
        return created
    }
    
    /**
     * ItemStackからカスタムアイテムを識別
     * @param item 判定するItemStack
     * @return 一致したCustomItem、見つからない場合はnull
     */
    fun identify(item: ItemStack): CustomItem? {
        val meta = item.itemMeta
        if (meta != null) {
            val fullId = meta.persistentDataContainer.get(customItemIdKey, PersistentDataType.STRING)
            if (fullId != null) {
                items[fullId]?.let { return it }
            }
        }

        val matched = items.values.find { it.matches(item) } ?: return null
        markCustomItemId(item, matched.fullId)
        return matched
    }

    fun updateItemLocalization(item: ItemStack, player: Player?): Boolean {
        val customItem = identify(item) ?: return false
        customItem.updateLocalization(item, player)
        markCustomItemId(item, customItem.fullId)
        return true
    }

    private fun markCustomItemId(item: ItemStack, fullId: String) {
        val meta = item.itemMeta ?: return
        meta.persistentDataContainer.set(customItemIdKey, PersistentDataType.STRING, fullId)
        item.itemMeta = meta
    }

    private fun applyCommonDataComponents(customItem: CustomItem, item: ItemStack) {
        val meta = item.itemMeta ?: return
        val model = customItem.itemModel ?: meta.itemModel ?: NamespacedKey.minecraft(item.type.key.key)
        meta.setItemModel(model)
        item.itemMeta = meta

        if (item.type == Material.POISONOUS_POTATO && !customItem.keepConsumableComponent) {
            PoisonousPotatoComponentPack.applyNonConsumable(item)
        } else if (item.type == Material.POISONOUS_POTATO) {
            PoisonousPotatoComponentPack.applyShared(item)
        }
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
