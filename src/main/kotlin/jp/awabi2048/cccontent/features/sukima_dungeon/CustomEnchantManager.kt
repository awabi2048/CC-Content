package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.persistence.PersistentDataType

/**
 * カスタムエンチャント管理クラス
 * ダンジョン固有のカスタムエンチャントを管理
 */
class CustomEnchantManager(private val plugin: JavaPlugin) {
    
    /**
     * カスタムエンチャント定義
     */
    enum class CustomEnchant(val displayName: String, val maxLevel: Int) {
        CURSE_BREAK("破ろう呪い", 3),        // ダメージ軽減
        DRAIN_POWER("力吸収", 2),            // 敵からエネルギー吸収
        ECHO_STRIKE("反響撃", 2),            // 連続攻撃
        SWIFT_STEP("軽足", 3),               // 移動速度上昇
        FATE_WEAVER("運操り", 1),            // ドロップ確率上昇
        VOID_TOUCH("虚ろな手", 1),           // ホログラム効果
        TEMPORAL_EDGE("時間刻み", 2),        // クールダウン短縮
        SANCTUARY("聖域", 1)                 // 回復エリア
    }
    
    /**
     * カスタムエンチャント効果定義
     */
    data class EnchantEffect(
        val enchant: CustomEnchant,
        val level: Int,
        val effectFunction: (level: Int) -> String  // 効果説明を返す
    )
    
    /**
     * アイテムにカスタムエンチャントを追加
     * @param item アイテム
     * @param enchant カスタムエンチャント
     * @param level レベル（1～maxLevel）
     * @return 成功したか
     */
    fun addCustomEnchant(item: ItemStack, enchant: CustomEnchant, level: Int): Boolean {
        if (level < 1 || level > enchant.maxLevel) {
            plugin.logger.warning("[SukimaDungeon] カスタムエンチャントレベルが無効です: $level")
            return false
        }
        
        val meta = item.itemMeta ?: return false
        val pdc = meta.persistentDataContainer
        
        // PDCにカスタムエンチャント情報を保存
        val key = NamespacedKey(plugin, "custom_enchant_${enchant.name}")
        pdc.set(key, PersistentDataType.INTEGER, level)
        
        item.itemMeta = meta
        
        plugin.logger.info("[SukimaDungeon] カスタムエンチャント ${enchant.displayName} Lv.$level をアイテムに追加しました")
        return true
    }
    
    /**
     * アイテムからカスタムエンチャントレベルを取得
     * @param item アイテム
     * @param enchant カスタムエンチャント
     * @return エンチャントレベル、見つからない場合は 0
     */
    fun getCustomEnchantLevel(item: ItemStack, enchant: CustomEnchant): Int {
        val meta = item.itemMeta ?: return 0
        val pdc = meta.persistentDataContainer
        
        val key = NamespacedKey(plugin, "custom_enchant_${enchant.name}")
        return pdc.getOrDefault(key, PersistentDataType.INTEGER, 0)
    }
    
    /**
     * アイテムが特定のカスタムエンチャントを持っているか確認
     * @param item アイテム
     * @param enchant カスタムエンチャント
     * @return 持っているか
     */
    fun hasCustomEnchant(item: ItemStack, enchant: CustomEnchant): Boolean {
        return getCustomEnchantLevel(item, enchant) > 0
    }
    
    /**
     * アイテムの全カスタムエンチャントを取得
     * @param item アイテム
     * @return カスタムエンチャント -> レベル のマップ
     */
    fun getAllCustomEnchants(item: ItemStack): Map<CustomEnchant, Int> {
        val meta = item.itemMeta ?: return emptyMap()
        val pdc = meta.persistentDataContainer
        
        val enchants = mutableMapOf<CustomEnchant, Int>()
        
        for (enchant in CustomEnchant.values()) {
            val level = getCustomEnchantLevel(item, enchant)
            if (level > 0) {
                enchants[enchant] = level
            }
        }
        
        return enchants
    }
    
    /**
     * アイテムからカスタムエンチャントを削除
     * @param item アイテム
     * @param enchant カスタムエンチャント
     * @return 成功したか
     */
    fun removeCustomEnchant(item: ItemStack, enchant: CustomEnchant): Boolean {
        val meta = item.itemMeta ?: return false
        val pdc = meta.persistentDataContainer
        
        val key = NamespacedKey(plugin, "custom_enchant_${enchant.name}")
        pdc.remove(key)
        
        item.itemMeta = meta
        return true
    }
    
    /**
     * カスタムエンチャントの説明を取得
     * @param enchant カスタムエンチャント
     * @param level レベル
     * @return 説明テキスト
     */
    fun getEnchantDescription(enchant: CustomEnchant, level: Int): String {
        return when (enchant) {
            CustomEnchant.CURSE_BREAK -> "受けるダメージを ${10 * level}% 軽減します"
            CustomEnchant.DRAIN_POWER -> "敵を倒した時、体力を ${5 * level} 回復します"
            CustomEnchant.ECHO_STRIKE -> "${level + 1} 連続で追加ダメージを与えます"
            CustomEnchant.SWIFT_STEP -> "移動速度を ${10 * level}% 上昇させます"
            CustomEnchant.FATE_WEAVER -> "ドロップ確率を ${20}% 上昇させます"
            CustomEnchant.VOID_TOUCH -> "攻撃に虚ろな輝きが纏わります"
            CustomEnchant.TEMPORAL_EDGE -> "スキルのクールダウンを ${10 * level}% 短縮します"
            CustomEnchant.SANCTUARY -> "周囲のプレイヤーを ${5 * level}% 回復します"
        }
    }
    
    /**
     * カスタムエンチャント一覧を取得
     * @return カスタムエンチャント情報のリスト
     */
    fun getAllEnchants(): List<String> {
        return CustomEnchant.values().map { enchant ->
            "${enchant.displayName} (Lv.1～${enchant.maxLevel})"
        }
    }
}
