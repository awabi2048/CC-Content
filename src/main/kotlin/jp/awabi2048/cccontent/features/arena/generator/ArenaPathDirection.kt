package jp.awabi2048.cccontent.features.arena.generator

enum class ArenaPathDirection(val dx: Int, val dz: Int, val token: String) {
    NORTH(0, -1, "north"),
    EAST(1, 0, "east"),
    SOUTH(0, 1, "south"),
    WEST(-1, 0, "west");

    fun left(): ArenaPathDirection = entries[(ordinal + 3) % 4]

    fun right(): ArenaPathDirection = entries[(ordinal + 1) % 4]

    fun opposite(): ArenaPathDirection = entries[(ordinal + 2) % 4]

    fun rotateClockwise(quarter: Int): ArenaPathDirection = entries[(ordinal + quarter.mod(4)) % 4]

    fun isAdjacent(other: ArenaPathDirection): Boolean {
        val distance = (ordinal - other.ordinal).mod(4)
        return distance == 1 || distance == 3
    }

    companion object {
        fun fromToken(token: String): ArenaPathDirection? {
            val normalized = token.trim().lowercase()
            return entries.firstOrNull { it.token == normalized }
        }
    }
}
