package jp.awabi2048.cccontent.features.arena

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import jp.awabi2048.cccontent.util.ContentLocalizationKeys
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object ArenaI18n {
    fun initialize(plugin: JavaPlugin) {
    }

    fun clearCache() {
    }

    /** テーマ等のレジストリIDは、Arenaドメイン内の生成済みキーへだけ解決します。 */
    fun text(sender: CommandSender?, key: String, vararg placeholders: Pair<String, Any?>): String =
        text(sender as? Player, ContentLocalizationKeys.text(key, "arena."), *placeholders)

    fun text(player: Player?, key: String, vararg placeholders: Pair<String, Any?>): String =
        text(player, ContentLocalizationKeys.text(key, "arena."), *placeholders)

    fun stringList(player: Player?, key: String, vararg placeholders: Pair<String, Any?>): List<String> =
        stringList(player, ContentLocalizationKeys.textList(key, "arena."), *placeholders)

    private fun placeholdersMap(vararg placeholders: Pair<String, Any?>): Map<String, Any> {
        return placeholders.associate { (key, value) -> key to (value ?: "null") }
    }
    fun text(sender: CommandSender?, key: LocalizationKey<String>, vararg placeholders: Pair<String, Any?>): String =
        text(sender as? Player, key, *placeholders)

    fun text(player: Player?, key: LocalizationKey<String>, vararg placeholders: Pair<String, Any?>): String =
        CCSystem.getAPI().getLocalized(player, key, placeholdersMap(*placeholders)).replace('&', '§')

    fun stringList(
        player: Player?,
        key: LocalizationKey<List<String>>,
        vararg placeholders: Pair<String, Any?>,
    ): List<String> = CCSystem.getAPI().getLocalized(player, key, placeholdersMap(*placeholders)).map {
        it.replace('&', '§')
    }
}
