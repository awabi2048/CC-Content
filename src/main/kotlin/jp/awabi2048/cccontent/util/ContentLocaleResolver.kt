package jp.awabi2048.cccontent.util

import com.awabi2048.ccsystem.CCSystem
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object ContentLocaleResolver {
    private const val DEFAULT_LOCALE = "ja_jp"

    fun resolve(player: Player?): String {
        if (player == null) {
            return DEFAULT_LOCALE
        }

        val ccSystemPlugin = Bukkit.getPluginManager().getPlugin("CC-System")
        if (ccSystemPlugin != null && ccSystemPlugin.isEnabled) {
            return CCSystem.getAPI().getPlayerLanguage(player)
        }

        val raw = player.locale.lowercase().replace('-', '_')
        return when {
            raw == "ja_jp" -> "ja_jp"
            raw == "en_us" -> "en_us"
            raw.startsWith("ja") -> "ja_jp"
            raw.startsWith("en") -> "en_us"
            else -> DEFAULT_LOCALE
        }
    }
}
