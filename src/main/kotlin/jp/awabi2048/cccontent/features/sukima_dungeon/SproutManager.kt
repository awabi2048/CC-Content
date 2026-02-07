package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

object SproutManager {
    private const val MARKER_TAG = "world_sprout_marker"

    fun populate(plugin: JavaPlugin, world: World, targetCount: Int): Int {
        // Find all "SPROUT" markers
        val markers = world.entities.filter { 
            (it is org.bukkit.entity.Marker && it.scoreboardTags.contains("sd.marker.sprout")) ||
            (it is ArmorStand && it.customName == "SPROUT")
        }
        
        if (markers.isEmpty()) {
            plugin.logger.warning("No SPROUT markers found in dungeon generation.")
            return 0
        }

        val selected = markers.shuffled().take(targetCount)
        
        for (marker in selected) {
            val loc = marker.location
            // Center the block location
            val blockLoc = loc.clone().toBlockLocation()
            if (blockLoc.block.type.isAir) {
                blockLoc.block.setType(Material.MANGROVE_PROPAGULE, false)
            } else {
                plugin.logger.info("Sprout location at ${blockLoc.toVector()} is not air (${blockLoc.block.type}), skipping placement.")
                continue
            }
            
            // Spawn tracker entity in the center of the block for particles and detection
            val trackerLoc = blockLoc.clone().add(0.5, 0.0, 0.5)
            val tracker = world.spawn(trackerLoc, ArmorStand::class.java)
            tracker.isVisible = false
            tracker.isMarker = true
            tracker.setGravity(false)
            tracker.isSmall = true
            tracker.addScoreboardTag(MARKER_TAG)
        }
        
        // Remove all SPROUT markers (both used and unused) to clean up

        
        plugin.logger.info("Populated ${selected.size} World Sprouts (Target: $targetCount, Candidates: ${markers.size})")
        return selected.size
    }

    fun isSproutBlock(block: Block): Boolean {
        if (block.type != Material.MANGROVE_PROPAGULE) return false
        // Check if there is a tracker nearby - slightly wider range to be safe
        val center = block.location.add(0.5, 0.5, 0.5)
        return block.world.getNearbyEntities(center, 0.7, 0.7, 0.7)
            .any { it.scoreboardTags.contains(MARKER_TAG) }
    }

    fun breakSprout(plugin: JavaPlugin, player: Player, block: Block) {
        val world = block.world
        val center = block.location.add(0.5, 0.5, 0.5)
        
        // Remove tracker and marker(item display)
        val trackers = world.getNearbyEntities(center, 0.7, 0.7, 0.7)
            .filter { it.scoreboardTags.contains(MARKER_TAG) }
        
        for (tracker in trackers) {
            val uuid = tracker.uniqueId
            // Also notify listeners to remove client-side markers (item displays)
            // CompassListener uses these UUIDs
            org.bukkit.Bukkit.getPluginManager().callEvent(jp.awabi2048.cccontent.features.sukima_dungeon.events.SproutBreakEvent(uuid))
            tracker.remove()
        }
            
        // Break block naturally? Or just set to air?
        // Requirement: "Block is destroyed, give item to player"
        block.type = Material.AIR
        
        // Play sound
        player.playSound(player.location, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 1.0f)
        
        // Give item
        val item = CustomItemManager.getWorldSproutItem()
        CustomItemManager.markAsDungeonItem(item)
        if (player.inventory.firstEmpty() != -1) {
            player.inventory.addItem(item)
        } else {
            world.dropItemNaturally(player.location, item)
        }
        
        // Update Session
        val session = DungeonSessionManager.getSession(player)
        if (session != null) {
            session.collectedSprouts++
            
            val remaining = session.totalSprouts - session.collectedSprouts
            // Logic for all players in the same dungeon?
            // Currently sessions are per player.
            // But the dungeon is shared if multi-player?
            // "Parties" aren't explicitly implemented yet in SessionManager, but multiple players can be in same world.
            // We should count sprouts remaining in the WORLD, or use the session's total vs collected.
            // If strictly single player dungeon per world:
            
            // Check world remaining sprouts
            val remainingInWorld = countRemainingSprouts(world)
            
            // Sync session if needed, or just rely on world count.
            // The requirement says: "Verify remaining count in current dungeon".
            
            if (remainingInWorld == 0) {
                // CLEAR!
                handleDungeonClear(plugin, player)
            } else {
                // Update scoreboard for all players in this world
                updateScoreboards(world)
            }
        }
    }
    
    fun countRemainingSprouts(world: World): Int {
        return world.entities.filter { it.scoreboardTags.contains(MARKER_TAG) }.size
    }
    
    private fun updateScoreboards(world: World) {
        // Find all players in this world that have a session
        val players = world.players
        for (p in players) {
            val s = DungeonSessionManager.getSession(p)
            if (s != null) {
                // Update collected count based on total - remaining
                s.collectedSprouts = s.totalSprouts - countRemainingSprouts(world)
                ScoreboardManager.updateScoreboard(p)
            }
        }
    }

    private fun handleDungeonClear(plugin: JavaPlugin, player: Player) {
        val world = player.world
        world.players.forEach { p ->
            val session = DungeonSessionManager.getSession(p) ?: return@forEach
            
            // Start collapse
            session.isCollapsing = true
            session.collapseRemainingMillis = session.durationSeconds * 1000L
            
            p.playSound(p.location, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
            
            // Send warning message
            val warnMsg = MessageManager.getMessage(p, "oage_collapse_warn")
            MessageManager.sendOageMessage(p, " 「$warnMsg」")
            
            // Scoreboard will automatically update with timer in next tick
            p.sendMessage("§e§l[システム] §fすべてのワールドの芽を回収しました！ダンジョンが崩壊を始めます。帰還ポータルから脱出してください。")
        }
    }

    fun startParticleTask(plugin: JavaPlugin) {
        object : BukkitRunnable() {
            override fun run() {
                // Iterate all dungeon worlds
                for (world in org.bukkit.Bukkit.getWorlds()) {
                    if (!world.name.startsWith("dungeon_")) continue
                    
                    val sprouts = world.entities.filter { it.scoreboardTags.contains(MARKER_TAG) }
                    
                    for (sprout in sprouts) {
                        // Check if player is nearby (e.g. 16 blocks)
                        val nearbyPlayers = sprout.getNearbyEntities(16.0, 16.0, 16.0).filterIsInstance<Player>()
                        if (nearbyPlayers.isEmpty()) continue
                        
                        // Spawn particles
                        // DustTransition: Magenta to White
                        val options = Particle.DustTransition(
                            org.bukkit.Color.fromRGB(255, 0, 255),
                            org.bukkit.Color.WHITE,
                            1.0f
                        )
                        
                        // Radius 2, 50 count
                        // world.spawnParticle(Particle.DUST_COLOR_TRANSITION, sprout.location, 50, 2.0, 2.0, 2.0, options)
                        // Requirement: "Radius 2 blocks... 50 particles"
                        // Automatically spread if offset is used.
                        world.spawnParticle(
                            Particle.DUST_COLOR_TRANSITION, 
                            sprout.location, 
                            50, 
                            2.0, 2.0, 2.0, // x, y, z offsets (radius approximation)
                            0.0, // extra speed
                            options
                        )
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L)
    }
}
