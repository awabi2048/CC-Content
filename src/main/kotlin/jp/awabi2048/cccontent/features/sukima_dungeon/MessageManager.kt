package jp.awabi2048.cccontent.features.sukima_dungeon

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.util.ContentLocaleResolver
import jp.awabi2048.cccontent.util.OageMessageSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object MessageManager {
    fun load(plugin: JavaPlugin) {
        LangManager.load(plugin)
        PlayerDataManager.init(plugin)
    }

    fun getMessage(player: Player?, key: String, params: Map<String, String> = emptyMap()): String {
        val lang = ContentLocaleResolver.resolve(player)
        return LangManager.getMessage(lang, key, params)
    }

    fun getMessage(lang: String, key: String, params: Map<String, String> = emptyMap()): String {
        return LangManager.getMessage(lang, key, params)
    }

    fun getList(player: Player?, key: String): List<String> {
        val lang = ContentLocaleResolver.resolve(player)
        return LangManager.getList(lang, key)
    }

    fun getTierName(player: Player?, tier: String): String {
        val lang = ContentLocaleResolver.resolve(player)
        return LangManager.getTierName(lang, tier)
    }
    
    // Legacy support or for console
    fun getMessage(key: String, params: Map<String, String> = emptyMap()): String {
        return getMessage(null as Player?, key, params)
    }

    fun getOagePrefix(): String {
        return OageMessageSender.getPrefix(CCContent.instance)
    }

    fun sendOageMessage(player: Player, message: String) {
        OageMessageSender.send(player, message, CCContent.instance)
    }

    /**
     * おあげちゃんの案内メッセージを遅延させて送信する
     * 最初の送信まで5秒(100ticks)待機し、以降は前行の文字数*2ticks待機する。
     */
    fun sendDelayedMessages(player: Player, messages: List<String>) {
        if (messages.isEmpty()) return

        val plugin = CCContent.instance
        var cumulativeDelay = 100L // 最初の遅延: 5秒
        val prefix = getOagePrefix()
        val sound = OageMessageSender.getSound(plugin)

        messages.forEach { msg ->
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                player.sendMessage(prefix + msg)
                player.playSound(player.location, sound, 1f, 1f)
            }, cumulativeDelay)
            
            // 次のメッセージまでの遅延を追加 (文字数 * 2 tick)
            // この文字列は待機時間の計算には含まない（prefixを除いたmsgの長さを使用）。
            cumulativeDelay += (msg.length * 2).toLong()
        }
    }
}
