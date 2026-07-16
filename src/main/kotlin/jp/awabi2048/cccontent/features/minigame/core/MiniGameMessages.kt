package jp.awabi2048.cccontent.features.minigame.core

import com.awabi2048.ccsystem.CCSystem
import org.bukkit.entity.Player

object MiniGameMessages {
    fun text(player: Player?, key: String, vararg values: Pair<String, Any?>): String {
        val placeholders = values.associate { it.first to (it.second ?: "null") }
        return CCSystem.getAPI().getI18nString(player, "minigame.$key", placeholders).replace('&', '§')
    }
}
