package jp.awabi2048.cccontent.items

import com.awabi2048.ccsystem.CCSystem
import jp.awabi2048.cccontent.util.ContentLocaleResolver
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object CustomItemI18n {
    fun initialize(plugin: JavaPlugin) {
    }

    fun text(player: Player?, key: String, fallback: String): String {
        return CCSystem.getAPI().getI18nString(player, key).replace('&', '§')
    }

    fun list(player: Player?, key: String, fallback: List<String>): List<String> {
        return CCSystem.getAPI().getI18nStringList(player, key).map { it.replace('&', '§') }
    }

    fun resolveLocale(player: Player?): String {
        return ContentLocaleResolver.resolve(player)
    }
}
