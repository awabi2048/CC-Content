package jp.awabi2048.cccontent.command

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import jp.awabi2048.cccontent.util.ContentLocalizationKeys
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object ContentManagementI18n {
    /** 機能ID由来の有限キーは、接頭辞を固定した上で型と存在を即時検証します。 */
    fun text(sender: CommandSender?, key: String, vararg placeholders: Pair<String, Any?>): String =
        text(sender, ContentLocalizationKeys.text("content_management.$key", "content_management."), *placeholders)

    fun text(sender: CommandSender?, key: LocalizationKey<String>, vararg placeholders: Pair<String, Any?>): String {
        val player = sender as? Player
        val values = placeholders.associate { (name, value) -> name to (value ?: "null") }
        return CCSystem.getAPI()
            .getLocalized(player, key, values)
            .replace('&', '§')
    }
}
