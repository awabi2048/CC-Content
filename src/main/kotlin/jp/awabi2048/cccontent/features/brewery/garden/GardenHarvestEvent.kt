package jp.awabi2048.cccontent.features.brewery.garden

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class GardenHarvestEvent(val player: Player, val location: Location, val plantId: String, val fruitItemId: String) : Event() {
    override fun getHandlers(): HandlerList = handlers
    companion object { private val handlers = HandlerList(); @JvmStatic fun getHandlerList(): HandlerList = handlers }
}
