package jp.awabi2048.cccontent.features.arena.mechanic

import net.kyori.adventure.bossbar.BossBar
import org.bukkit.Location

const val ARENA_MECHANIC_MARKER_PREFIX = "arena.marker.mechanic."

data class ArenaMechanicMarker(
    val tag: String,
    val location: Location,
    val facingYaw: Float? = null
) {
    val mechanicPath: String = tag.removePrefix(ARENA_MECHANIC_MARKER_PREFIX)

    fun clone(): ArenaMechanicMarker {
        return copy(location = location.clone())
    }
}

data class ArenaMechanicBarrierGateResult(
    val allowed: Boolean,
    val reason: String? = null
) {
    companion object {
        fun allowed(): ArenaMechanicBarrierGateResult = ArenaMechanicBarrierGateResult(true)
        fun blocked(reason: String): ArenaMechanicBarrierGateResult = ArenaMechanicBarrierGateResult(false, reason)
    }
}

data class ArenaMechanicObjectiveProgress(
    val title: String,
    val progress: Float,
    val color: BossBar.Color
)
