package jp.awabi2048.cccontent.features.sukima_dungeon.listeners

import jp.awabi2048.cccontent.features.sukima_dungeon.DungeonManager
import org.bukkit.attribute.Attribute
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.entity.LivingEntity

class GravityListener : Listener {

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        val world = player.world
        val fromWorld = event.from

        // Handle entering dungeon
        val theme = DungeonManager.getTheme(world)
        if (theme != null) {
            val gravityAttribute = player.getAttribute(Attribute.GRAVITY)
            if (gravityAttribute != null) {
                gravityAttribute.baseValue = 0.08 * theme.gravity
            }
        }

        // Handle leaving dungeon
        // If the player came from a dungeon, we need to reset their gravity.
        // Even if they went to another dungeon, the block above sets the new gravity.
        // But if they went to a non-dungeon world, we must reset it.
        if (DungeonManager.getTheme(world) == null) {
             val gravityAttribute = player.getAttribute(Attribute.GRAVITY)
             if (gravityAttribute != null) {
                 gravityAttribute.baseValue = 0.08
             }
        }
    }

    @EventHandler
    fun onEntitySpawn(event: EntitySpawnEvent) {
        val entity = event.entity
        if (entity is LivingEntity) {
            val world = entity.world
            val theme = DungeonManager.getTheme(world)
            if (theme != null) {
                val gravityAttribute = entity.getAttribute(Attribute.GRAVITY)
                if (gravityAttribute != null) {
                    gravityAttribute.baseValue = 0.08 * theme.gravity
                }
            }
        }
    }
}
