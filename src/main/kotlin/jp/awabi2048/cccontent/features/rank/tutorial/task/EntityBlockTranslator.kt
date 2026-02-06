package jp.awabi2048.cccontent.features.rank.tutorial.task

import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
import org.bukkit.entity.EntityType
import org.bukkit.Material
import net.kyori.adventure.text.Component

/**
 * モブやブロックの名前をプレイヤーの言語に翻訳するヘルパークラス
 * 優先順位：
 * 1. Minecraft公式翻訳キー（entity.minecraft.*, block.minecraft.*, item.minecraft.*）
 * 2. カスタム翻訳ファイル（lang/ja_JP.yml）
 * 3. キャメルケース形式へのフォールバック
 */
class EntityBlockTranslator(
    private val messageProvider: MessageProvider
) {
    
    /**
     * モブ名を翻訳
     * Minecraft公式翻訳を優先的に使用
     * @param entityType モブのタイプ名（大文字）例: "ZOMBIE", "SKELETON"
     * @return 翻訳されたモブ名
     */
    fun translateEntity(entityType: String): String {
        val key = entityType.lowercase()
        
        // 1. カスタム翻訳を確認（Minecraftの公式翻訳がない場合のオーバーライド）
        val customKey = "custom.entity.$key"
        val customMessage = messageProvider.getMessage(customKey)
        if (!customMessage.startsWith("§c[Missing:")) {
            return customMessage
        }
        
        // 2. Minecraft公式翻訳キーを文字列で返す
        // （実際の翻訳はプレイヤーに送信する時にComponent形式で行う）
        val minecraftKey = "entity.minecraft.$key"
        
        // 3. キャメルケース形式にフォールバック
        return formatName(key)
    }
    
    /**
     * ブロック名を翻訳
     * Minecraft公式翻訳を優先的に使用
     * @param blockType ブロックのタイプ名（大文字か小文字）例: "DIAMOND_ORE", "diamond_ore"
     * @return 翻訳されたブロック名
     */
    fun translateBlock(blockType: String): String {
        val key = blockType.lowercase()
        
        // 1. カスタム翻訳を確認
        val customKey = "custom.block.$key"
        val customMessage = messageProvider.getMessage(customKey)
        if (!customMessage.startsWith("§c[Missing:")) {
            return customMessage
        }
        
        // 2. Minecraft公式翻訳キーを文字列で返す
        val minecraftKey = "block.minecraft.$key"
        
        // 3. キャメルケース形式にフォールバック
        return formatName(key)
    }
    
    /**
     * アイテム名を翻訳
     * Minecraft公式翻訳を優先的に使用
     * @param itemName アイテムの名前（大文字か小文字）例: "OBSIDIAN", "obsidian"
     * @return 翻訳されたアイテム名
     */
    fun translateItem(itemName: String): String {
        val key = itemName.lowercase()
        
        // 1. カスタム翻訳を確認
        val customKey = "custom.item.$key"
        val customMessage = messageProvider.getMessage(customKey)
        if (!customMessage.startsWith("§c[Missing:")) {
            return customMessage
        }
        
        // 2. Minecraft公式翻訳キーを文字列で返す
        val minecraftKey = "item.minecraft.$key"
        
        // 3. キャメルケース形式にフォールバック
        return formatName(key)
    }
    
    /**
     * ボス名を翻訳
     * Minecraft公式翻訳を優先的に使用（ボスはminecraft:entityに含まれる）
     * @param bossType ボスのタイプ名（大文字か小文字）例: "ENDER_DRAGON", "ender_dragon"
     * @return 翻訳されたボス名
     */
    fun translateBoss(bossType: String): String {
        val key = bossType.lowercase()
        
        // 1. カスタム翻訳を確認
        val customKey = "custom.boss.$key"
        val customMessage = messageProvider.getMessage(customKey)
        if (!customMessage.startsWith("§c[Missing:")) {
            return customMessage
        }
        
        // 2. Minecraft公式翻訳キーを文字列で返す（ボスもエンティティ）
        val minecraftKey = "entity.minecraft.$key"
        
        // 3. キャメルケース形式にフォールバック
        return formatName(key)
    }
    
    /**
     * キャメルケース形式に変換（翻訳がない場合のフォールバック）
     * 例: "diamond_ore" → "Diamond Ore"
     */
    private fun formatName(name: String): String {
        return name
            .split("_")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}
