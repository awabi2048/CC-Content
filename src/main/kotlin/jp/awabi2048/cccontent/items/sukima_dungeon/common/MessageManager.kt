package jp.awabi2048.cccontent.items.sukima_dungeon.common

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * SukimaDungeon メッセージ管理クラス
 * 元の SukimaDungeon プラグインの MessageManager を再現
 */
class MessageManager(private val langManager: LangManager) {
    private val miniMessage = MiniMessage.miniMessage()
    
    companion object {
        const val PREFIX = "sukima_dungeon.prefix"
        const val COMPASS_ACTIVATE = "sukima_dungeon.compass.activate"
        const val COMPASS_DEACTIVATE = "sukima_dungeon.compass.deactivate"
        const val COMPASS_DETECTED = "sukima_dungeon.compass.detected"
        const val COMPASS_COOLDOWN = "sukima_dungeon.compass.cooldown"
        const val COMPASS_NO_SPROUTS = "sukima_dungeon.compass.no_sprouts"
        const val TALISMAN_USE = "sukima_dungeon.talisman.use"
        const val TALISMAN_ESCAPE = "sukima_dungeon.talisman.escape"
        const val TALISMAN_CANCELLED = "sukima_dungeon.talisman.cancelled"
        const val SPROUT_HARVEST = "sukima_dungeon.sprout.harvest"
        const val ERROR_NOT_IN_DUNGEON = "sukima_dungeon.error.not_in_dungeon"
        const val ERROR_NO_PERMISSION = "sukima_dungeon.error.no_permission"
        const val ERROR_GENERIC = "sukima_dungeon.error.generic"
    }
    
    /**
     * プリフィックス付きメッセージを送信
     */
    fun send(sender: CommandSender, key: String, vararg args: Any?) {
        val prefix = langManager.get(PREFIX)
        val message = langManager.get(key, *args)
        
        val fullMessage = if (prefix.isNotEmpty() && !prefix.contains("[Missing")) {
            "$prefix $message"
        } else {
            message
        }
        
        sender.sendMessage(component(fullMessage))
    }
    
    /**
     * プレイヤーにアクションバーを表示
     */
    fun sendActionBar(player: Player, key: String, vararg args: Any?) {
        val message = langManager.get(key, *args)
        player.sendActionBar(component(message))
    }
    
    /**
     * プレイヤーにタイトルを表示
     */
    fun sendTitle(player: Player, titleKey: String, subtitleKey: String, vararg args: Any?) {
        val title = langManager.get(titleKey, *args)
        val subtitle = langManager.get(subtitleKey, *args)
        
        player.showTitle(
            Title.title(
                component(title),
                component(subtitle),
                Title.Times.times(
                    java.time.Duration.ofMillis(500),
                    java.time.Duration.ofMillis(2000),
                    java.time.Duration.ofMillis(500)
                )
            )
        )
    }
    
    /**
     * 文字列をMiniMessageコンポーネントに変換
     */
    fun component(text: String): Component {
        return miniMessage.deserialize(text)
    }
    
    /**
     * 生のメッセージを送信（キーなし）
     */
    fun sendRaw(sender: CommandSender, message: String) {
        sender.sendMessage(component(message))
    }
    
    /**
     * エラーメッセージを送信
     */
    fun sendError(sender: CommandSender, key: String, vararg args: Any?) {
        send(sender, key, *args)
    }
    
    /**
     * 成功メッセージを送信
     */
    fun sendSuccess(sender: CommandSender, key: String, vararg args: Any?) {
        send(sender, key, *args)
    }
    
    /**
     * 情報メッセージを送信
     */
    fun sendInfo(sender: CommandSender, key: String, vararg args: Any?) {
        send(sender, key, *args)
    }
    
    /**
     * コンパス起動メッセージ
     */
    fun sendCompassActivate(player: Player, tier: Int, duration: Int) {
        sendActionBar(player, COMPASS_ACTIVATE, tier, duration)
    }
    
    /**
     * コンパス停止メッセージ
     */
    fun sendCompassDeactivate(player: Player) {
        sendActionBar(player, COMPASS_DEACTIVATE)
    }
    
    /**
     * コンパス検知メッセージ
     */
    fun sendCompassDetected(player: Player, distance: Double, direction: String) {
        sendActionBar(player, COMPASS_DETECTED, distance, direction)
    }
    
    /**
     * コンパスクールダウンメッセージ
     */
    fun sendCompassCooldown(player: Player, cooldown: Int) {
        sendActionBar(player, COMPASS_COOLDOWN, cooldown)
    }
    
    /**
     * タリスマン使用メッセージ
     */
    fun sendTalismanUse(player: Player) {
        send(player, TALISMAN_USE)
    }
    
    /**
     * タリスマンエスケープメッセージ
     */
    fun sendTalismanEscape(player: Player) {
        sendTitle(player, TALISMAN_ESCAPE, "")
    }
}