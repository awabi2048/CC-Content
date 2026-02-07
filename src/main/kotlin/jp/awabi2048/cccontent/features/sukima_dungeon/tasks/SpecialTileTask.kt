package jp.awabi2048.cccontent.features.sukima_dungeon.tasks

import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonSession
import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonSessionManager
import jp.awabi2048.cccontent.features.sukima_dungeon.MessageManager
import jp.awabi2048.cccontent.features.sukima_dungeon.mobs.MobManager
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object SpecialTileTask {
    fun tick(plugin: JavaPlugin, mobManager: MobManager) {
        for (session in DungeonSessionManager.getAllSessions()) {
            val player = session.player ?: continue
            if (!player.isOnline) continue
            if (session.dungeonWorldName != player.world.name) continue

            val loc = player.location
            val startLoc = session.startLocation ?: continue
            val tileSize = session.tileSize

            val relX = (loc.x - startLoc.x).toInt()
            val relZ = (loc.z - startLoc.z).toInt()
            
            // Adjust for grid coordinates
            val gridX = if (relX >= 0) relX / tileSize else (relX - tileSize + 1) / tileSize
            val gridZ = if (relZ >= 0) relZ / tileSize else (relZ - tileSize + 1) / tileSize
            
            val cell = gridX to gridZ

            // Rest Tile Logic
            if (session.restCells.contains(cell)) {
                if (player.location.block.type == Material.WATER || player.eyeLocation.block.type == Material.WATER) {
                    player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 40, 1))
                    
                    // Show message once if not already sent in this tile visit? 
                    // For simplicity, let's just apply the effect. 
                    // If we want a message, we'd need another state.
                }
            }

            // Miniboss Tile Logic
            if (session.minibossMarkers.containsKey(cell) && !session.minibossTriggered.contains(cell)) {
                val spawnLoc = session.minibossMarkers[cell]!!
                session.minibossTriggered.add(cell)
                
                mobManager.spawnMob(spawnLoc, session.themeName, startLoc)
                
                player.sendMessage(MessageManager.getMessage(player, "oage_miniboss_spawn"))
                player.playSound(player.location, Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 0.5f)
            }

            // Distance-based Mob Spawn/Despawn
            val config = jp.awabi2048.cccontent.features.sukima_dungeon.SukimaConfigHelper.getConfig(plugin)
            val spawnRange = config.getDouble("mob_spawn.spawn_range", 24.0)
            val despawnRange = config.getDouble("mob_spawn.despawn_range", 32.0)
            val spawnRangeSq = spawnRange * spawnRange
            val despawnRangeSq = despawnRange * despawnRange
            
            val world = player.world
            val participants = if (session.isMultiplayer) world.players else listOf(player)

            for (point in session.mobSpawnPoints) {
                val currentUUID = session.spawnedMobs[point]
                val entity = currentUUID?.let { org.bukkit.Bukkit.getEntity(it) } as? org.bukkit.entity.LivingEntity
                
                if (entity != null && entity.isValid && !entity.isDead) {
                    // Check for despawn
                    // Use Mob's current location for despawn check
                    val anyPlayerNear = participants.any { 
                        it.world == entity.world && it.location.distanceSquared(entity.location) < despawnRangeSq 
                    }
                    if (!anyPlayerNear) {
                        entity.remove()
                        session.spawnedMobs.remove(point)
                    }
                } else {
                    // Check for spawn
                    if (currentUUID != null) session.spawnedMobs.remove(point) // Cleanup dead/invalid
                    
                    // Use original spawn point for spawn check
                    val anyPlayerNear = participants.any { 
                        it.world == point.world && it.location.distanceSquared(point) < spawnRangeSq 
                    }
                    if (anyPlayerNear) {
                        val newMob = mobManager.spawnMob(point, session.themeName, startLoc)
                        if (newMob != null) {
                            session.spawnedMobs[point] = newMob.uniqueId
                        }
                    }
                }
            }
        }
    }
}
