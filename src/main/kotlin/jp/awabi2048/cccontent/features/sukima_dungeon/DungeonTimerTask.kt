package jp.awabi2048.cccontent.features.sukima_dungeon

import jp.awabi2048.cccontent.items.sukima_dungeon.common.MessageManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

/**
 * ダンジョンタイマータスク
 * 20tick毎（1秒）にセッション情報を更新し、時間切れをチェック
 */
class DungeonTimerTask(
    private val plugin: JavaPlugin,
    private val dungeonSessionManager: DungeonSessionManager,
    private val messageManager: MessageManager
) : Runnable {
    
    private var taskId: Int = -1
    
    /**
     * タスクを開始
     * @return タスクID
     */
    fun start(): Int {
        taskId = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this,
            0L,
            20L // 20tick = 1秒
        ).taskId
        plugin.logger.info("[SukimaDungeon] ダンジョンタイマータスクが起動しました (Task ID: $taskId)")
        return taskId
    }
    
    /**
     * タスクを停止
     */
    fun stop() {
        if (taskId >= 0) {
            Bukkit.getScheduler().cancelTask(taskId)
            plugin.logger.info("[SukimaDungeon] ダンジョンタイマータスクを停止しました")
        }
    }
    
    /**
     * メインループ処理
     */
    override fun run() {
        try {
            // 全セッションの経過時間を更新
            dungeonSessionManager.updateAllSessions()
            
            // セッションをチェックして、時間切れのプレイヤーを処理
            checkTimeouts()
        } catch (e: Exception) {
            plugin.logger.warning("[SukimaDungeon] タイマータスク中にエラーが発生しました")
            e.printStackTrace()
        }
    }
    
    /**
     * 時間切れセッションをチェック
     */
    private fun checkTimeouts() {
        val allSessions = dungeonSessionManager.getAllSessions().toList()
        
        for ((playerUuid, session) in allSessions) {
            val player = Bukkit.getPlayer(playerUuid) ?: continue
            
            // プレイヤーがオンラインかつダンジョン内にいるかチェック
            if (!player.isOnline) {
                // プレイヤーがオフラインの場合、セッションを終了
                dungeonSessionManager.endSession(playerUuid)
                continue
            }
            
            // 時間切れをチェック
            if (session.getRemainingTime() <= 0) {
                handleTimeout(player, session)
            } else {
                // 進捗状況をスコアボードで表示（1秒ごと）
                updateScoreboard(player, session)
            }
        }
    }
    
    /**
     * 時間切れ時の処理
     */
    private fun handleTimeout(player: Player, session: DungeonSession) {
        messageManager.send(player, "§c§lタイムアップ！")
        
        // テレポート演出
        if (session.escapeLocation != null) {
            player.teleport(session.escapeLocation)
            messageManager.sendActionBar(player, "§cダンジョンから脱出しました")
        }
        
        // セッション終了
        dungeonSessionManager.endSession(player.uniqueId)
    }
    
    /**
     * スコアボード更新（進捗表示）
     */
    private fun updateScoreboard(player: Player, session: DungeonSession) {
        val progress = session.getProgress()
        val remaining = session.getRemainingFormatted()
        val elapsed = session.getElapsedFormatted()
        
        // アクションバーに情報を表示（5秒ごと）
        if (session.getElapsed() % 100 == 0L) { // 100 ticks = 5秒
            messageManager.sendActionBar(
                player,
                "§e進捗: §f$progress §7| §e残り時間: §f$remaining"
            )
        }
    }
}
