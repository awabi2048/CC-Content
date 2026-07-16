package jp.awabi2048.cccontent.items

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import jp.awabi2048.cccontent.util.ContentLocaleResolver
import net.kyori.adventure.text.Component
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

    fun lore(player: Player?, key: String, fallback: List<String>): List<Component> {
        return CCSystem.getAPI().getLoreService().render(
            GuiLoreSpec.Rich(
                list(player, key, fallback).map { GuiLoreLine.Text(it) },
                GuiLoreFrame.NONE
            )
        )
    }

    fun resolveLocale(player: Player?): String {
        return ContentLocaleResolver.resolve(player)
    }
}
