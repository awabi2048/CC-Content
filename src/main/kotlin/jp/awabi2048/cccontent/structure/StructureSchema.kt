package jp.awabi2048.cccontent.structure

data class MarkerRequirement(
    val tag: String,
    val entityType: String = StructureSchemas.MARKER_ENTITY_TYPE,
    val minimum: Int = 1,
    val maximum: Int? = null
)

data class ArenaStructureSchema(
    val keyword: String,
    val inSide: CardinalDirection?,
    val outSides: Set<CardinalDirection>
) {
    val connectionSides: Set<CardinalDirection>
        get() = setOfNotNull(inSide) + outSides
}

data class SukimaStructureSchema(
    val keyword: String,
    val canonicalOpenings: Set<CardinalDirection>,
    val markerRequirements: List<MarkerRequirement>
)

object StructureSchemas {
    const val MARKER_ENTITY_TYPE = "minecraft:marker"
    const val ARENA_CONNECTION_IN_TAG = "arena.marker.connection.in"
    const val ARENA_CONNECTION_OUT_TAG = "arena.marker.connection.out"

    val ARENA_CONNECTION_TAGS: Set<String> = setOf(ARENA_CONNECTION_IN_TAG, ARENA_CONNECTION_OUT_TAG)

    private val arenaSchemas = listOf(
        ArenaStructureSchema(
            keyword = "entrance",
            inSide = null,
            outSides = setOf(CardinalDirection.NORTH)
        ),
        ArenaStructureSchema(
            keyword = "straight",
            inSide = CardinalDirection.NORTH,
            outSides = setOf(CardinalDirection.SOUTH)
        ),
        ArenaStructureSchema(
            keyword = "corner",
            inSide = CardinalDirection.NORTH,
            outSides = setOf(CardinalDirection.EAST)
        ),
        ArenaStructureSchema(
            keyword = "corridor",
            inSide = CardinalDirection.NORTH,
            outSides = setOf(CardinalDirection.SOUTH)
        ),
        ArenaStructureSchema(
            keyword = "goal",
            inSide = CardinalDirection.NORTH,
            outSides = emptySet()
        ),
        ArenaStructureSchema(
            keyword = "tjunction_room",
            inSide = CardinalDirection.NORTH,
            outSides = setOf(CardinalDirection.SOUTH, CardinalDirection.EAST)
        ),
        ArenaStructureSchema(
            keyword = "pedestal_room",
            inSide = CardinalDirection.NORTH,
            outSides = emptySet()
        ),
        ArenaStructureSchema(
            keyword = "lift",
            inSide = CardinalDirection.NORTH,
            outSides = setOf(CardinalDirection.SOUTH)
        )
    ).associateBy { it.keyword }

    private val sukimaSchemas = listOf(
        SukimaStructureSchema("straight", setOf(CardinalDirection.NORTH, CardinalDirection.SOUTH), mobRequirement()),
        SukimaStructureSchema("corner", setOf(CardinalDirection.NORTH, CardinalDirection.EAST), mobRequirement()),
        SukimaStructureSchema(
            "t_shape",
            setOf(CardinalDirection.NORTH, CardinalDirection.EAST, CardinalDirection.WEST),
            mobRequirement()
        ),
        SukimaStructureSchema("cross", CardinalDirection.entries.toSet(), mobRequirement()),
        SukimaStructureSchema("deadend", setOf(CardinalDirection.NORTH), mobRequirement()),
        SukimaStructureSchema("trap", setOf(CardinalDirection.NORTH, CardinalDirection.SOUTH), mobRequirement()),
        SukimaStructureSchema(
            "entrance",
            setOf(CardinalDirection.NORTH),
            listOf(MarkerRequirement("sd.marker.spawn"))
        ),
        SukimaStructureSchema("miniboss", setOf(CardinalDirection.NORTH, CardinalDirection.SOUTH), mobRequirement()),
        SukimaStructureSchema("rest", setOf(CardinalDirection.NORTH, CardinalDirection.SOUTH), mobRequirement())
    ).associateBy { it.keyword }

    fun arena(keyword: String): ArenaStructureSchema? = arenaSchemas[keyword]

    fun arenaRequiresConnectionMarkers(fileNameWithoutExtension: String): Boolean {
        val parts = fileNameWithoutExtension.lowercase().split('.')
        if (parts.isEmpty() || arena(parts.first()) == null) return false
        if (parts.first() == "goal") {
            return parts.getOrNull(1) == "barrier_restart"
        }
        return parts.none { OPEN_FRAME_PATTERN.matches(it) }
    }

    fun sukima(keyword: String): SukimaStructureSchema? = sukimaSchemas[keyword]

    private fun mobRequirement() = listOf(MarkerRequirement("sd.marker.mob"))

    private val OPEN_FRAME_PATTERN = Regex("open_[1-9]\\d*")
}
