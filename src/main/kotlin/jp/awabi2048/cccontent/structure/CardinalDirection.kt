package jp.awabi2048.cccontent.structure

/**
 * 4方位を表す列挙子。Minecraft/WorldEdit の座標系に基づく:
 * - NORTH: -Z 方向 (yaw=180)
 * - EAST:  +X 方向 (yaw=270)
 * - SOUTH: +Z 方向 (yaw=0)
 * - WEST:  -X 方向 (yaw=90)
 *
 * ordinal は時計回り (NORTH -> EAST -> SOUTH -> WEST) の順で、
 * WorldEdit の rotateY(ordinal * 90.0) と対応する。
 */
enum class CardinalDirection(val dx: Int, val dz: Int, val token: String) {
    NORTH(0, -1, "north"),
    EAST(1, 0, "east"),
    SOUTH(0, 1, "south"),
    WEST(-1, 0, "west");

    fun left(): CardinalDirection = entries[(ordinal + 3) % 4]

    fun right(): CardinalDirection = entries[(ordinal + 1) % 4]

    fun opposite(): CardinalDirection = entries[(ordinal + 2) % 4]

    fun rotateClockwise(quarter: Int): CardinalDirection = entries[(ordinal + quarter.mod(4)) % 4]

    fun isAdjacent(other: CardinalDirection): Boolean {
        val distance = (ordinal - other.ordinal).mod(4)
        return distance == 1 || distance == 3
    }

    companion object {
        fun fromToken(token: String): CardinalDirection? {
            val normalized = token.trim().lowercase()
            return entries.firstOrNull { it.token == normalized }
        }

        /**
         * プレイヤーの yaw (Bukkit: SOUTH=0, WEST=90, NORTH=180, EAST=270) を
         * 4方位のいずれかに丸める。
         */
        fun fromPlayerYaw(yawDegrees: Float): CardinalDirection {
            val normalized = ((yawDegrees.toDouble() % 360.0) + 360.0) % 360.0
            return when {
                normalized < 45.0 || normalized >= 315.0 -> SOUTH
                normalized < 135.0 -> WEST
                normalized < 225.0 -> NORTH
                else -> EAST
            }
        }
    }
}
