package jp.awabi2048.cccontent.features.sukima_dungeon.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

class SproutBreakEvent(val trackerUuid: UUID) : Event() {
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }

    override fun getHandlers(): HandlerList = HANDLERS
}
