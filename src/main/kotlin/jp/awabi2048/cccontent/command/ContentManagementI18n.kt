package jp.awabi2048.cccontent.command

import com.awabi2048.ccsystem.CCSystem
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object ContentManagementI18n {
    fun text(sender: CommandSender?, key: String, vararg placeholders: Pair<String, Any?>): String {
        val player = sender as? Player
        val values = placeholders.associate { (name, value) -> name to (value ?: "null") }
        return CCSystem.getAPI()
            .getI18nString(player, "content_management.$key", values)
            .replace('&', '§')
    }
}
