package jp.awabi2048.cccontent.features.arena

import org.bukkit.Color
import org.bukkit.Location
import java.util.UUID

enum class ArenaActionMarkerType {
    DOOR_TOGGLE,
    BARRIER_ACTIVATE
}

enum class ArenaActionMarkerState(val defaultColor: Color) {
    PRE_ACTIVATED(Color.fromRGB(255, 64, 64)),
    READY(Color.fromRGB(255, 216, 64)),
    RUNNING(Color.fromRGB(96, 255, 128))
}

data class ArenaActionMarker(
    val id: UUID,
    val type: ArenaActionMarkerType,
    val center: Location,
    val holdTicksRequired: Int,
    val wave: Int? = null,
    val stateColors: Map<ArenaActionMarkerState, Color> = ArenaActionMarkerState.entries.associateWith { it.defaultColor },
    var state: ArenaActionMarkerState = ArenaActionMarkerState.PRE_ACTIVATED,
    var colorTransitionFrom: Color = ArenaActionMarkerState.PRE_ACTIVATED.defaultColor,
    var colorTransitionTo: Color = ArenaActionMarkerState.PRE_ACTIVATED.defaultColor,
    var colorTransitionStartTick: Long = 0L
) {
    init {
        val initialColor = colorFor(state)
        colorTransitionFrom = initialColor
        colorTransitionTo = initialColor
    }

    fun colorFor(state: ArenaActionMarkerState): Color {
        return stateColors[state] ?: state.defaultColor
    }
}

data class ArenaActionMarkerHoldState(
    var markerId: UUID? = null,
    var heldTicks: Int = 0
)
