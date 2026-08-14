package jp.awabi2048.cccontent.features.sukima_dungeon

import com.awabi2048.ccsystem.CCSystem
import org.bukkit.plugin.java.JavaPlugin

object LangManager {
    fun load(plugin: JavaPlugin) {
    }

    fun getMessage(lang: String, key: String, params: Map<String, String> = emptyMap()): String {
        return CCSystem.getAPI().getLocalized(lang, jp.awabi2048.cccontent.util.ContentLocalizationKeys.text("sukima_dungeon.$key", "sukima_dungeon."), params).replace('&', '§')
    }

    fun getList(lang: String, key: String): List<String> {
        return CCSystem.getAPI().getLocalized(lang, jp.awabi2048.cccontent.util.ContentLocalizationKeys.textList("sukima_dungeon.$key", "sukima_dungeon.")).map { it.replace('&', '§') }
    }

    fun getTierName(lang: String, tier: String): String {
        return CCSystem.getAPI().getLocalized(lang, jp.awabi2048.cccontent.util.ContentLocalizationKeys.text("sukima_dungeon.tiers.$tier", "sukima_dungeon.")).replace('&', '§')
    }
}
