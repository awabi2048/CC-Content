package jp.awabi2048.cccontent.features.sukima_dungeon.listeners

import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonManager
import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonSessionManager
import jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.Random

class TrapListener(private val plugin: JavaPlugin) : Listener {
    private val random = Random()

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val session = DungeonSessionManager.getSession(player) ?: return
        val startLocation = session.startLocation ?: return
        
        val world = player.world
        val theme = DungeonManager.getTheme(world) ?: return
        val voidYLimit = theme.voidYLimit ?: return

        // Check if player fell below Y limit
        if (player.location.y < voidYLimit) {
            // Add blindness effect for 2 seconds
            player.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 40, 1))
            
            // Play sound
            player.playSound(player.location, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
            player.playSound(player.location, org.bukkit.Sound.BLOCK_LAVA_EXTINGUISH, 0.5f, 1.5f)

            // Trigger abyss teleport: Try to teleport to an ITEM marker first
            val itemMarkers = world.entities.filterIsInstance<org.bukkit.entity.Marker>()
                .filter { it.scoreboardTags.contains("sd.marker.item") }
            
            if (itemMarkers.isNotEmpty()) {
                val targetMarker = itemMarkers[random.nextInt(itemMarkers.size)]
                player.teleport(targetMarker.location)
                player.sendMessage(MessageManager.getMessage(player, "trap_triggered"))
            } else {
                // Fallback: Teleport to random player in dungeon, or random location if solo
                val playersInDungeon = world.players.filter { it.uniqueId != player.uniqueId && it.location.y >= voidYLimit }
                
                if (playersInDungeon.isNotEmpty()) {
                    val targetPlayer = playersInDungeon[random.nextInt(playersInDungeon.size)]
                    player.teleport(targetPlayer.location)
                    player.sendMessage(MessageManager.getMessage(player, "trap_triggered_to_player", mapOf("player" to targetPlayer.name)))
                } else {
                    val targetGridX = random.nextInt(session.gridWidth)
                    val targetGridZ = random.nextInt(session.gridLength)
                    
                    val targetX = startLocation.x + (targetGridX * session.tileSize) + (session.tileSize / 2.0)
                    val targetZ = startLocation.z + (targetGridZ * session.tileSize) + (session.tileSize / 2.0)
                    val targetY = startLocation.y + 1.0
                    
                    val targetLocation = Location(world, targetX, targetY, targetZ, player.location.yaw, player.location.pitch)
                    
                    player.teleport(targetLocation)
                    player.sendMessage(MessageManager.getMessage(player, "trap_triggered"))
                }
            }
        }
    }
}
