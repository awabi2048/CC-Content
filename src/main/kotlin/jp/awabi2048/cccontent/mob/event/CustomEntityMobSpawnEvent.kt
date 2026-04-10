package jp.awabi2048.cccontent.mob.event

import jp.awabi2048.cccontent.mob.EntityMobSpawnOptions
import jp.awabi2048.cccontent.mob.MobDefinition
import org.bukkit.entity.Entity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class CustomEntityMobSpawnEvent(
    val entity: Entity,
    val definition: MobDefinition,
    val options: EntityMobSpawnOptions
) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
