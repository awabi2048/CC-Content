package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import io.papermc.paper.scoreboard.numbers.NumberFormat

object ScoreboardManager {
    
    fun setupScoreboard(player: Player) {
        val manager = Bukkit.getScoreboardManager() ?: return
        val scoreboard = manager.newScoreboard
        val objective = scoreboard.registerNewObjective("dungeon", "dummy", MessageManager.getMessage(player, "scoreboard_title"))
        
        objective.displaySlot = DisplaySlot.SIDEBAR
        objective.numberFormat(NumberFormat.blank())
        
        // Initial setup
        updateScoreboard(player, scoreboard)
        
        player.scoreboard = scoreboard
    }
    
    fun updateScoreboard(player: Player, scoreboard: org.bukkit.scoreboard.Scoreboard? = null) {
        val session = DungeonSessionManager.getSession(player) ?: return
        val sb = scoreboard ?: player.scoreboard
        val objective = sb.getObjective("dungeon") ?: return
        
        // Clear old entries
        sb.entries.forEach { sb.resetScores(it) }
        
        val bar = MessageManager.getMessage(player, "common_bar")
        val sproutMsg = MessageManager.getMessage(player, "scoreboard_sprouts", mapOf(
            "count" to session.collectedSprouts.toString(),
            "count_remaining" to session.totalSprouts.toString()
        ))
        val timeMsg = MessageManager.getMessage(player, "scoreboard_remaining", mapOf(
            "time_remaining" to session.getFormattedRemainingTime()
        ))
        
        // Use scores to control order (higher score = top)
        objective.getScore(bar + "§1").score = 5 // Top bar
        objective.getScore(sproutMsg).score = 4
        
        if (session.isCollapsing) {
            objective.getScore(timeMsg).score = 3
        }
        
        objective.getScore(bar + "§2").score = 1 // Bottom bar
    }
    
    fun removeScoreboard(player: Player) {
        val manager = Bukkit.getScoreboardManager() ?: return
        player.scoreboard = manager.mainScoreboard
    }
}
