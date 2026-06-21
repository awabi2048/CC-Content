package jp.awabi2048.cccontent.items.marker

import jp.awabi2048.cccontent.localization.LanguageKeyRequirement
import jp.awabi2048.cccontent.localization.LanguageKeyRequirementProvider
import java.nio.file.Path

/** Finite keys assembled by AdminMarkerToolService from marker mode identifiers. */
object MarkerToolLanguageKeyRequirements : LanguageKeyRequirementProvider {
    private val arenaModes = setOf(
        "mob", "checkpoint", "connection_in", "connection_out", "door_block",
        "barrier_core", "barrier_point", "pedestal", "join_area", "lobby",
        "lobby_main", "lobby_tutorial_start", "lobby_tutorial_step", "lift"
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
