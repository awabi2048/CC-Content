package jp.awabi2048.cccontent.util

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.sukima_dungeon.SukimaConfigHelper
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object OageMessageSender {
    fun getPrefix(plugin: JavaPlugin = CCContent.instance): String {
        val config = SukimaConfigHelper.getConfig(plugin)
        return config.getString("oage_message_prefix", "§e【おあげちゃん】§r") ?: "§e【おあげちゃん】§r"
    }

    fun getSound(plugin: JavaPlugin = CCContent.instance): Sound {
        val config = SukimaConfigHelper.getConfig(plugin)
        val soundStr = config.getString("oage_message_sound", "entity.villager.ambient")
        return try {
            if (soundStr != null) Sound.valueOf(soundStr.uppercase().replace('.', '_')) else Sound.ENTITY_VILLAGER_AMBIENT
        } catch (_: Exception) {
            Sound.ENTITY_VILLAGER_AMBIENT
        }
    }

    fun send(player: Player, message: String, plugin: JavaPlugin = CCContent.instance) {
        player.sendMessage(getPrefix(plugin) + message)
        player.playSound(player.location, getSound(plugin), 1f, 1f)
    }
}
