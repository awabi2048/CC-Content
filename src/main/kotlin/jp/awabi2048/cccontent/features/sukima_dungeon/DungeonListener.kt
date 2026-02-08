package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent

class DungeonListener : Listener {

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val fromWorld = event.from
        val toWorld = event.player.world
        
        // Check if player left a dungeon world
        if (isSukimaDungeonWorld(fromWorld)) {
            DungeonSessionManager.endSession(event.player)
            ScoreboardManager.removeScoreboard(event.player)

            // おあげちゃんのリザルト報告
            val resultMsg = MessageManager.getMessage(event.player, "oage_result")
            MessageManager.sendDelayedMessages(event.player, listOf(resultMsg))
            
            if (fromWorld.players.isEmpty()) {
                DungeonManager.deleteDungeonWorld(fromWorld)
            }
        }

        if (isSukimaDungeonWorld(toWorld)) {
            BGMManager.play(event.player, "default")
        } else {
            BGMManager.stop(event.player)
        }

    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val world = event.player.world
        // We need to check if the world is empty *after* the player quits.
        // However, PlayerQuitEvent fires before the player is fully removed from the world list in some versions/implementations,
        // or immediately. To be safe, we check if the world has 0 players or just this 1 player.
        // Actually, checking if size <= 1 and contains the player is safer.
        
        if (isSukimaDungeonWorld(world)) {
            // Do NOT end session here, allow reconnecting
            // Scoreboard will be removed automatically on quit or handled on rejoin
            
            // Schedule a check for the next tick to ensure the player is gone
            org.bukkit.Bukkit.getScheduler().runTaskLater(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(DungeonListener::class.java), Runnable {
                if (world.players.isEmpty()) {
                    DungeonManager.deleteDungeonWorld(world)
                }
            }, 1L)
        }
    }
    @EventHandler
    fun onBlockBreak(event: org.bukkit.event.block.BlockBreakEvent) {
        val player = event.player
        val world = player.world
        
        if (isSukimaDungeonWorld(world)) {
            if (player.gameMode == org.bukkit.GameMode.SURVIVAL) {
                val blockType = event.block.type
                // Allow breaking World Sprouts and allowed utility blocks (torches, ladders, etc.)
                if (blockType != Material.MANGROVE_PROPAGULE && !ALLOWED_MATERIALS.contains(blockType)) {
                    event.isCancelled = true
                }
            }
        }
    }

    private val ALLOWED_MATERIALS = setOf(
        Material.TORCH,
        Material.WALL_TORCH,
        Material.SOUL_TORCH,
        Material.SOUL_WALL_TORCH,
        Material.LANTERN,
        Material.SOUL_LANTERN,
        Material.LADDER
    )

    @EventHandler
    fun onBlockPlace(event: org.bukkit.event.block.BlockPlaceEvent) {
        val player = event.player
        val world = player.world
        
        if (isSukimaDungeonWorld(world)) {
            if (player.gameMode == org.bukkit.GameMode.SURVIVAL) {
                if (!ALLOWED_MATERIALS.contains(event.block.type)) {
                    event.isCancelled = true
                }
            }
        }
    }

    @EventHandler
    fun onCommand(event: org.bukkit.event.player.PlayerCommandPreprocessEvent) {
        val player = event.player
        val world = player.world

        if (isSukimaDungeonWorld(world)) {
            if (player.isOp) return

            val message = event.message.lowercase().removePrefix("/")
            val command = message.split(" ")[0]

            val plugin = jp.awabi2048.cccontent.CCContent.instance
            val restrictedCommands = SukimaConfigHelper.getConfig(plugin).getStringList("restricted_commands")

            if (restrictedCommands.any { it.lowercase() == command }) {
                event.isCancelled = true
                player.sendMessage(MessageManager.getMessage(player, "command_restricted"))
            }
        }
    }
}
