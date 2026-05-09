@file:Suppress("DEPRECATION", "USELESS_ELVIS")

package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import io.papermc.paper.scoreboard.numbers.NumberFormat

object ScoreboardManager {
    private const val OBJECTIVE_NAME = "dungeon"
    private const val TOP_BAR_TEAM = "sd_bar_top"
    private const val BOTTOM_BAR_TEAM = "sd_bar_bottom"
    private const val TOP_BAR_ENTRY = "§0"
    private const val BOTTOM_BAR_ENTRY = "§1"
    
    fun setupScoreboard(player: Player) {
        val manager = Bukkit.getScoreboardManager() ?: return
        val scoreboard = manager.newScoreboard
        val objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, "dummy", MessageManager.getMessage(player, "scoreboard_title"))
        
        objective.displaySlot = DisplaySlot.SIDEBAR
        objective.numberFormat(NumberFormat.blank())
        
        // Initial setup
        updateScoreboard(player, scoreboard)
        
        player.scoreboard = scoreboard
    }
    
    fun updateScoreboard(player: Player, scoreboard: org.bukkit.scoreboard.Scoreboard? = null) {
        val session = DungeonSessionManager.getSession(player) ?: return
        val sb = scoreboard ?: player.scoreboard
        val objective = sb.getObjective(OBJECTIVE_NAME) ?: return
        
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

        applySeparator(sb, TOP_BAR_TEAM, TOP_BAR_ENTRY, bar)
        applySeparator(sb, BOTTOM_BAR_TEAM, BOTTOM_BAR_ENTRY, bar)
        
        // Use scores to control order (higher score = top)
        objective.getScore(TOP_BAR_ENTRY).score = 5
        objective.getScore(sproutMsg).score = 4
        
        if (session.isCollapsing) {
            objective.getScore(timeMsg).score = 3
        }
        
        objective.getScore(BOTTOM_BAR_ENTRY).score = 1
    }
    
    fun removeScoreboard(player: Player) {
        val manager = Bukkit.getScoreboardManager() ?: return
        player.scoreboard = manager.mainScoreboard
    }

    private fun applySeparator(
        scoreboard: org.bukkit.scoreboard.Scoreboard,
        teamName: String,
        entry: String,
        separator: String
    ) {
        val team = scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName)
        if (!team.hasEntry(entry)) {
            team.addEntry(entry)
        }
        team.prefix(net.kyori.adventure.text.Component.text(separator))
        team.suffix(net.kyori.adventure.text.Component.empty())
    }
}
