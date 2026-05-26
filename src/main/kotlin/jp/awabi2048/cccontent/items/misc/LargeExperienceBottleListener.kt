package jp.awabi2048.cccontent.items.misc

import org.bukkit.entity.ExperienceOrb
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.ExpBottleEvent
import org.bukkit.persistence.PersistentDataType

class LargeExperienceBottleListener : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onExpBottle(event: ExpBottleEvent) {
        val bottle = event.entity
        val meta = bottle.persistentDataContainer
        if (!meta.has(SpecialCustomItemKeys.EXPERIENCE_BOTTLE, PersistentDataType.BYTE)) return

        event.isCancelled = true
        bottle.world.spawn(bottle.location, ExperienceOrb::class.java) { orb -> orb.experience = 1500 }
    }
}
