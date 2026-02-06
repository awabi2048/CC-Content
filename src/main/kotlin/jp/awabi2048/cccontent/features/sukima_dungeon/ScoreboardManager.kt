package jp.awabi2048.cccontent.features.sukima_dungeon

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Scoreboard
import java.util.*

/**
 * スコアボード管理クラス
 * ダンジョン進捗をスコアボードで表示
 */
class ScoreboardManager {
    
    private val scoreboards: MutableMap<UUID, Scoreboard> = mutableMapOf()
    private val objectives: MutableMap<UUID, String> = mutableMapOf()
    
    /**
     * プレイヤー用のスコアボードを作成
     * @param player プレイヤー
     * @param session ダンジョンセッション
     */
    fun createScoreboard(player: Player, session: DungeonSession) {
        val scoreboardManager = Bukkit.getScoreboardManager() ?: return
        val scoreboard = scoreboardManager.newScoreboard
        
        // オブジェクティブを作成
        val objectiveName = "sukima_dungeon_${player.uniqueId}"
        val objective = scoreboard.registerNewObjective(
            objectiveName,
            "dummy",
            Component.text("§e§lSukima Dungeon")
        )
        objective.setDisplaySlot(DisplaySlot.SIDEBAR)
        
        // スコアボード内容を設定
        updateScoreboard(player, session, scoreboard, objective)
        
        // プレイヤーにスコアボードを設定
        player.scoreboard = scoreboard
        
        scoreboards[player.uniqueId] = scoreboard
        objectives[player.uniqueId] = objectiveName
    }
    
    /**
     * スコアボードを更新
     * @param player プレイヤー
     * @param session ダンジョンセッション
     */
    fun updateScoreboard(player: Player, session: DungeonSession) {
        val scoreboard = scoreboards[player.uniqueId] ?: return
        val objectiveName = objectives[player.uniqueId] ?: return
        val objective = scoreboard.getObjective(objectiveName) ?: return
        
        updateScoreboard(player, session, scoreboard, objective)
    }
    
    /**
     * スコアボード内容を更新（内部用）
     */
    private fun updateScoreboard(
        player: Player,
        session: DungeonSession,
        scoreboard: Scoreboard,
        objective: org.bukkit.scoreboard.Objective
    ) {
        // 既存のスコアをクリア
        for (entry in scoreboard.entries) {
            scoreboard.resetScores(entry)
        }
        
        var score = 10
        
        // タイトル
        objective.getScore("§7────────────────").score = score--
        
        // テーマ
        objective.getScore("§eテーマ: §f${session.themeName}").score = score--
        
        // ティア
        objective.getScore("§eティア: §f${session.tier.name}").score = score--
        
        // 空行
        objective.getScore("").score = score--
        
        // 進捗
        objective.getScore("§e進捗").score = score--
        val progress = session.getProgress()
        objective.getScore("  §f$progress").score = score--
        
        // 空行
        objective.getScore(" ").score = score--
        
        // 時間情報
        objective.getScore("§e時間").score = score--
        objective.getScore("  経過: §f${session.getElapsedFormatted()}").score = score--
        objective.getScore("  残り: §f${session.getRemainingFormatted()}").score = score--
        
        // 空行
        objective.getScore("  ").score = score--
        
        // 状態
        val state = if (session.isCollapsing) "§c崩壊中" else "§a進行中"
        objective.getScore("§e状態: $state").score = score--
        
        // フッター
        objective.getScore("§7────────────────").score = score--
    }
    
    /**
     * プレイヤーのスコアボードを削除
     * @param player プレイヤー
     */
    fun removeScoreboard(player: Player) {
        val scoreboard = scoreboards[player.uniqueId]
        val objectiveName = objectives[player.uniqueId]
        
        if (scoreboard != null && objectiveName != null) {
            val objective = scoreboard.getObjective(objectiveName)
            objective?.unregister()
        }
        
        scoreboards.remove(player.uniqueId)
        objectives.remove(player.uniqueId)
        
        // プレイヤーをメインスコアボードに戻す
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    }
    
    /**
     * 全スコアボードをクリア
     */
    fun clearAll() {
        for ((playerId, scoreboard) in scoreboards) {
            val objectiveName = objectives[playerId]
            if (objectiveName != null) {
                val objective = scoreboard.getObjective(objectiveName)
                objective?.unregister()
            }
        }
        
        scoreboards.clear()
        objectives.clear()
    }
}
