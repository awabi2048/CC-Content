package jp.awabi2048.cccontent.structure

import kotlin.math.floor

data class StructurePoint2D(val x: Double, val z: Double)

data class StructureBounds2D(
    val minX: Int,
    val minZ: Int,
    val maxX: Int,
    val maxZ: Int
) {
    val width: Int get() = maxX - minX + 1
    val depth: Int get() = maxZ - minZ + 1
}

/**
 * WorldEdit の scale(-1, 1, 1) -> rotateY(quarter * 90) と同じ変換モデル。
 */
data class StructureTransform(
    val rotationQuarter: Int = 0,
    val mirrorX: Boolean = false
) {
    val normalizedQuarter: Int = rotationQuarter.mod(4)

    fun applyRawPoint(x: Double, z: Double): StructurePoint2D {
        val rotated = when (normalizedQuarter) {
            1 -> StructurePoint2D(z, -x)
            2 -> StructurePoint2D(-x, -z)
            3 -> StructurePoint2D(-z, x)
            else -> StructurePoint2D(x, z)
        }
        return if (mirrorX) StructurePoint2D(-rotated.x, rotated.z) else rotated
    }

    fun bounds(width: Int, depth: Int): StructureBounds2D {
        require(width > 0 && depth > 0) { "Structure dimensions must be positive: ${width}x$depth" }
        val corners = listOf(
            applyRawPoint(0.0, 0.0),
            applyRawPoint((width - 1).toDouble(), 0.0),
            applyRawPoint(0.0, (depth - 1).toDouble()),
            applyRawPoint((width - 1).toDouble(), (depth - 1).toDouble())
        )
        return StructureBounds2D(
            minX = floor(corners.minOf { it.x }).toInt(),
            minZ = floor(corners.minOf { it.z }).toInt(),
            maxX = floor(corners.maxOf { it.x }).toInt(),
            maxZ = floor(corners.maxOf { it.z }).toInt()
        )
    }

    fun applyLocalPoint(x: Double, z: Double, width: Int, depth: Int): StructurePoint2D {
        val raw = applyRawPoint(x, z)
        val bounds = bounds(width, depth)
        return StructurePoint2D(raw.x - bounds.minX, raw.z - bounds.minZ)
    }

    fun applyDirection(direction: CardinalDirection): CardinalDirection {
        val rotated = direction.rotateClockwise(-normalizedQuarter)
        return if (mirrorX) {
            when (rotated) {
                CardinalDirection.EAST -> CardinalDirection.WEST
                CardinalDirection.WEST -> CardinalDirection.EAST
                else -> rotated
            }
        } else {
            rotated
        }
    }

    fun inverseDirection(direction: CardinalDirection): CardinalDirection {
        return CardinalDirection.entries.first { applyDirection(it) == direction }
    }

    companion object {
        fun rotationBetween(from: CardinalDirection, to: CardinalDirection): Int {
            return (0..3).first { StructureTransform(it).applyDirection(from) == to }
        }

        fun rotationMatching(
            canonicalDirections: Set<CardinalDirection>,
            actualDirections: Set<CardinalDirection>
        ): Int? {
            return (0..3).firstOrNull { quarter ->
                canonicalDirections.mapTo(linkedSetOf()) {
                    StructureTransform(quarter).applyDirection(it)
                } == actualDirections
            }
        }
    }
}
