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
        return try {
            messageProvider.getMessage("entity.$key", null)
        } catch (e: Exception) {
            // 翻訳がない場合は、キャメルケースに変換して返す
            formatName(key)
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
        return try {
            messageProvider.getMessage("block.$key", null)
        } catch (e: Exception) {
            // 翻訳がない場合は、キャメルケースに変換して返す
            formatName(key)
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
