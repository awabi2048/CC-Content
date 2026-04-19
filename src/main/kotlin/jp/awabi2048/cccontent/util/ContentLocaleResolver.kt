package jp.awabi2048.cccontent.util

import com.awabi2048.ccsystem.CCSystem
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object ContentLocaleResolver {
    fun resolve(player: Player?): String {
        if (player == null) {
            return CCSystem.getAPI().getSupportedLanguages().firstOrNull() ?: "ja_jp"
        }

        val ccSystemPlugin = Bukkit.getPluginManager().getPlugin("CC-System")
        if (ccSystemPlugin == null || !ccSystemPlugin.isEnabled) {
            throw IllegalStateException("CC-System が有効化されていないため locale を解決できません")
        }

        return CCSystem.getAPI().getPlayerLanguage(player)
    }
}
