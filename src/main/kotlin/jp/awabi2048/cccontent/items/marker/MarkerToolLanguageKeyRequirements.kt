package jp.awabi2048.cccontent.items.marker

import jp.awabi2048.cccontent.localization.LanguageKeyRequirement
import jp.awabi2048.cccontent.localization.LanguageKeyRequirementProvider
import java.nio.file.Path

/** Finite keys assembled by AdminMarkerToolService from marker mode identifiers. */
object MarkerToolLanguageKeyRequirements : LanguageKeyRequirementProvider {
    private val arenaModes = setOf(
        "mob", "checkpoint", "connection_in", "connection_out", "door_block",
        "barrier_core", "barrier_point", "pedestal", "join_area", "lobby",
        "lobby_main", "lobby_tutorial_start", "lobby_tutorial_step", "lift",
        "nether_track_path_left", "nether_track_path_right", "nether_cart_reference_left", "nether_cart_reference_right", "nether_magma_vent",
        "nether_engine_start", "nether_core_center_left", "nether_core_center_right", "nether_core_activate",
        "ocean_geyser", "ocean_whirlpool",
        "natura_stalactite", "natura_mist"
    )

    override fun requiredKeys(resourcesRoot: Path): List<LanguageKeyRequirement> =
        arenaModes.sorted().map { mode ->
            LanguageKeyRequirement(
                sourceId = "CC-System",
                key = "arena.marker.modes.$mode",
                reason = "arena marker tool defines dynamic mode id=$mode"
            )
        }
}
