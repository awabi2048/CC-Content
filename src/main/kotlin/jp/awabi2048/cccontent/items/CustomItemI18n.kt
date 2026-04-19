package jp.awabi2048.cccontent.items

import com.awabi2048.ccsystem.CCSystem
import jp.awabi2048.cccontent.util.ContentLocaleResolver
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object CustomItemI18n {
    private const val SOURCE_ID = "CC-Content:custom_items"

    fun initialize(plugin: JavaPlugin) {
    }

    fun text(player: Player?, key: String, fallback: String): String {
        return CCSystem.getAPI().getI18nString(SOURCE_ID, player, key).replace('&', '§')
    }

    fun list(player: Player?, key: String, fallback: List<String>): List<String> {
        return CCSystem.getAPI().getI18nStringList(SOURCE_ID, player, key).map { it.replace('&', '§') }
    }

    fun resolveLocale(player: Player?): String {
        return ContentLocaleResolver.resolve(player)
    }
}
