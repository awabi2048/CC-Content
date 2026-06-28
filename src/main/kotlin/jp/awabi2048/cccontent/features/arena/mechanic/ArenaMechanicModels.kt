package jp.awabi2048.cccontent.features.arena.mechanic

import org.bukkit.Location

const val ARENA_MECHANIC_MARKER_PREFIX = "arena.marker.mechanic."

data class ArenaMechanicMarker(
    val tag: String,
    val location: Location
) {
    val mechanicPath: String = tag.removePrefix(ARENA_MECHANIC_MARKER_PREFIX)

    fun clone(): ArenaMechanicMarker {
        return copy(location = location.clone())
    }
}
