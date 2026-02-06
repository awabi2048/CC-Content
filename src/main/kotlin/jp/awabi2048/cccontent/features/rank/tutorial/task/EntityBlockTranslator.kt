package jp.awabi2048.cccontent.features.rank.tutorial.task

import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
import org.bukkit.entity.Player

/**
 * モブやブロックの名前をプレイヤーの言語に翻訳するヘルパークラス
 */
class EntityBlockTranslator(
    private val messageProvider: MessageProvider
) {
    
    /**
     * モブ名を翻訳
     * @param entityType モブのタイプ名（大文字）例: "ZOMBIE", "SKELETON"
     * @param player プレイヤー（言語設定用）
     * @return 翻訳されたモブ名
     */
    fun translateEntity(entityType: String, player: Player? = null): String {
        // 小文字のキーに変換
        val key = entityType.lowercase()
        
        // 翻訳ファイルから取得を試みる
        val message = messageProvider.getMessage("entity.$key")
        
        // "Missing:" で始まる場合は翻訳がない、キャメルケースに変換
        return if (message.startsWith("§c[Missing:")) {
            formatName(key)
        } else {
            message
        }
    }
    
    /**
     * ブロック名を翻訳
     * @param blockType ブロックのタイプ名（大文字）例: "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE"
     * @param player プレイヤー（言語設定用）
     * @return 翻訳されたブロック名
     */
    fun translateBlock(blockType: String, player: Player? = null): String {
        // 小文字のキーに変換
        val key = blockType.lowercase()
        
        // 翻訳ファイルから取得を試みる
        val message = messageProvider.getMessage("block.$key")
        
        // "Missing:" で始まる場合は翻訳がない、キャメルケースに変換
        return if (message.startsWith("§c[Missing:")) {
            formatName(key)
        } else {
            message
        }
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
