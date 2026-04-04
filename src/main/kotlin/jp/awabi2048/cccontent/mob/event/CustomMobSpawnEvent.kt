package jp.awabi2048.cccontent.mob.event

import jp.awabi2048.cccontent.mob.MobDefinition
import jp.awabi2048.cccontent.mob.MobSpawnOptions
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class CustomMobSpawnEvent(
    val entity: LivingEntity,
    val definition: MobDefinition,
    val options: MobSpawnOptions
) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
