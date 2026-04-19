package jp.awabi2048.cccontent.features.arena

import com.awabi2048.ccsystem.CCSystem
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object ArenaI18n {
    private const val SOURCE_ID = "CC-Content:arena"

    fun initialize(plugin: JavaPlugin) {
    }

    fun clearCache() {
    }

    fun text(sender: CommandSender?, key: String, fallback: String, vararg placeholders: Pair<String, Any?>): String {
        return text(sender as? Player, key, fallback, *placeholders)
    }

    fun text(player: Player?, key: String, fallback: String, vararg placeholders: Pair<String, Any?>): String {
        return CCSystem.getAPI().getI18nString(SOURCE_ID, player, key, placeholdersMap(*placeholders)).replace('&', '§')
    }

    fun stringList(player: Player?, key: String, fallback: List<String>, vararg placeholders: Pair<String, Any?>): List<String> {
        return CCSystem.getAPI().getI18nStringList(SOURCE_ID, player, key, placeholdersMap(*placeholders)).map { it.replace('&', '§') }
    }

    private fun placeholdersMap(vararg placeholders: Pair<String, Any?>): Map<String, Any> {
        return placeholders.associate { (key, value) -> key to (value ?: "null") }
    }
}
