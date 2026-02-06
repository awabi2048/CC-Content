package jp.awabi2048.cccontent.features.rank.tutorial.task

import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
import org.bukkit.entity.Player
import net.kyori.adventure.text.Component

/**
 * モブやブロックの名前をプレイヤーの言語に翻訳するヘルパークラス
 * ローカルな日本語翻訳ファイルを使用
 * 
 * 注：Minecraft公式のTextComponentを使用する場合、プレイヤーに送信する際に自動翻訳されます。
 * ここでは、サーバーメッセージ用に日本語翻訳ファイルを使用します。
 */
class EntityBlockTranslator(
    private val messageProvider: MessageProvider
) {
    
    /**
     * モブ名を翻訳（サーバーメッセージ用）
     * @param entityType モブのタイプ名（大文字）例: "ZOMBIE", "SKELETON"
     * @return 翻訳されたモブ名
     */
    fun translateEntity(entityType: String): String {
        val key = entityType.lowercase()
        
        // ローカライズファイルから翻訳を取得
        val customMessage = messageProvider.getMessage("entity.$key")
        
        // 翻訳が見つかった場合はそれを使用
        if (!customMessage.startsWith("§c[Missing:")) {
            return customMessage
        }
        
        // 翻訳がない場合は、キャメルケース形式にフォールバック
        return formatName(key)
    }
    
    /**
     * ブロック名を翻訳（サーバーメッセージ用）
     * @param blockType ブロックのタイプ名（大文字か小文字）例: "DIAMOND_ORE", "diamond_ore"
     * @return 翻訳されたブロック名
     */
    fun translateBlock(blockType: String): String {
        val key = blockType.lowercase()
        
        // ローカライズファイルから翻訳を取得
        val customMessage = messageProvider.getMessage("block.$key")
        
        // 翻訳が見つかった場合はそれを使用
        if (!customMessage.startsWith("§c[Missing:")) {
            return customMessage
        }
        
        // 翻訳がない場合は、キャメルケース形式にフォールバック
        return formatName(key)
    }
    
    /**
     * アイテム名を翻訳（サーバーメッセージ用）
     * @param itemName アイテムの名前（大文字か小文字）例: "OBSIDIAN", "obsidian"
     * @return 翻訳されたアイテム名
     */
    fun translateItem(itemName: String): String {
        val key = itemName.lowercase()
        val translationKey = "item.$key"
        
        // ローカライズファイルから翻訳を取得
        val customMessage = messageProvider.getMessage(translationKey)
        
        // 翻訳が見つかった場合はそれを使用
        if (!customMessage.startsWith("§c[Missing:")) {
            return customMessage
        }
        
        // デバッグ：翻訳がない場合はログを出力
        // println("DEBUG translateItem: key=$translationKey, result=$customMessage")
        
        // 翻訳がない場合は、キャメルケース形式にフォールバック
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
