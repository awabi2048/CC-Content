package jp.awabi2048.cccontent.features.arena.event

import jp.awabi2048.cccontent.features.arena.mission.ArenaDailyMissionEntry
import jp.awabi2048.cccontent.features.arena.mission.ArenaDailyMissionSet
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

class ArenaDailyMissionGeneratedEvent(
    val dateKey: String,
    val missionSet: ArenaDailyMissionSet
) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ArenaMissionStartRequestEvent(
    val player: Player,
    val dateKey: String,
    val mission: ArenaDailyMissionEntry
) : Event(), Cancellable {
    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ArenaSessionEndedEvent(
    val ownerPlayerId: UUID,
    val worldName: String,
    val themeId: String,
    val difficultyId: String,
    val starCount: Int,
    val waves: Int,
    val success: Boolean
) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
