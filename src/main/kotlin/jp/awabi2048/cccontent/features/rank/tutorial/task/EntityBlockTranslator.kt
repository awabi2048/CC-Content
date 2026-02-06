package jp.awabi2048.cccontent.features.rank.tutorial.task

import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
import org.bukkit.entity.Player
import net.kyori.adventure.text.Component

/**
 * モブやブロックの名前をプレイヤーの言語に翻訳するヘルパークラス
 * 
 * 優先順位：
 * 1. Minecraft公式翻訳キー（entity.minecraft.*, block.minecraft.*, item.minecraft.*）
 *    - Component.translatable() で返す（クライアント側で自動翻訳）
 * 2. キャメルケース形式へのフォールバック
 */
class EntityBlockTranslator(
    private val messageProvider: MessageProvider
) {
    
    /**
     * モブ名を翻訳キーで返す
     * Component.translatable() でラップするとクライアント側で自動翻訳される
     * @param entityType モブのタイプ名（大文字）例: "ZOMBIE", "SKELETON"
     * @return 翻訳キー（形式: "entity.minecraft.zombie"）
     */
    fun translateEntity(entityType: String): String {
        val key = entityType.lowercase()
        // Minecraft公式翻訳キーを返す
        return "entity.minecraft.$key"
    }
    
    /**
     * ブロック名を翻訳キーで返す
     * Component.translatable() でラップするとクライアント側で自動翻訳される
     * @param blockType ブロックのタイプ名（大文字か小文字）例: "DIAMOND_ORE", "diamond_ore"
     * @return 翻訳キー（形式: "block.minecraft.diamond_ore"）
     */
    fun translateBlock(blockType: String): String {
        val key = blockType.lowercase()
        // Minecraft公式翻訳キーを返す
        return "block.minecraft.$key"
    }
    
    /**
     * アイテム名を翻訳キーで返す
     * Component.translatable() でラップするとクライアント側で自動翻訳される
     * @param itemName アイテムの名前（大文字か小文字）例: "OBSIDIAN", "obsidian"
     * @return 翻訳キー（形式: "item.minecraft.obsidian"）
     */
    fun translateItem(itemName: String): String {
        val key = itemName.lowercase()
        // Minecraft公式翻訳キーを返す
        return "item.minecraft.$key"
    }
    
    /**
     * ボス名を翻訳キーで返す
     * Component.translatable() でラップするとクライアント側で自動翻訳される
     * @param bossType ボスのタイプ名（大文字か小文字）例: "ENDER_DRAGON", "ender_dragon"
     * @return 翻訳キー（形式: "entity.minecraft.ender_dragon"）
     */
    fun translateBoss(bossType: String): String {
        val key = bossType.lowercase()
        // ボスもエンティティなので、entity.minecraft.* を返す
        return "entity.minecraft.$key"
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
