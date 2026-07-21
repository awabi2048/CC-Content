package jp.awabi2048.cccontent.features.resourcecollection

import com.awabi2048.ccsystem.api.time.Season
import org.bukkit.Material
import java.util.ArrayDeque
import java.util.SplittableRandom
import java.util.UUID

enum class GatheringVegetationGroup {
    GRASS,
    FERN,
    AQUATIC_GRASS,
    FLOWER;

    companion object {
        fun from(material: Material): GatheringVegetationGroup? = when (material) {
            Material.SHORT_GRASS, Material.TALL_GRASS -> GRASS
            Material.FERN, Material.LARGE_FERN -> FERN
            Material.SEAGRASS, Material.TALL_SEAGRASS -> AQUATIC_GRASS
            Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM,
            Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP,
            Material.WHITE_TULIP, Material.PINK_TULIP, Material.OXEYE_DAISY,
            Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY -> FLOWER
            else -> null
        }
    }
}

data class GatheringPlantPosition(val x: Int, val y: Int, val z: Int)

data class GatheringPatchCandidate(
    val position: GatheringPlantPosition,
    val group: GatheringVegetationGroup
)

data class GatheringPatch(
    val id: String,
    val cellX: Int,
    val cellY: Int,
    val cellZ: Int,
    val group: GatheringVegetationGroup,
    val anchor: GatheringPlantPosition,
    val plants: List<GatheringPlantPosition>
)

object GatheringPatchModel {
    private const val MAX_PATCH_SIZE = 16
    private val anchorOrder = compareBy<GatheringPlantPosition>({ it.y }, { it.x }, { it.z })
    private val horizontalDirections = listOf(
        0 to -1, 1 to -1, 1 to 0, 1 to 1,
        0 to 1, -1 to 1, -1 to 0, -1 to -1
    )
    private val verticalOffsets = intArrayOf(0, 1, -1)

    @JvmStatic
    fun build(worldId: UUID, candidates: Collection<GatheringPatchCandidate>): List<GatheringPatch> {
        val unique = candidates.distinctBy { it.position to it.group }
        return unique
            .groupBy { candidate ->
                CellGroup(
                    Math.floorDiv(candidate.position.x, 4),
                    Math.floorDiv(candidate.position.y, 3),
                    Math.floorDiv(candidate.position.z, 4),
                    candidate.group
                )
            }
            .toSortedMap(compareBy<CellGroup>({ it.cellY }, { it.cellX }, { it.cellZ }, { it.group.ordinal }))
            .flatMap { (cell, members) -> buildCellPatches(worldId, cell, members) }
    }

    @JvmStatic
    fun stableSeed(
        worldSeed: Long,
        worldId: UUID,
        anchor: GatheringPlantPosition,
        season: Season
    ): Long = worldSeed xor
        worldId.mostSignificantBits xor
        worldId.leastSignificantBits xor
        (anchor.x.toLong() shl 32) xor
        (anchor.z.toLong() shl 1) xor
        anchor.y.toLong() xor
        (season.ordinal.toLong() * 0x9E3779B97F4A7C15UL.toLong())

    @JvmStatic
    fun weightedIndex(weights: List<Int>, seed: Long): Int? {
        require(weights.all { it >= 0 }) { "weights must not be negative" }
        val total = weights.sum()
        if (total <= 0) return null
        var cursor = SplittableRandom(seed).nextInt(total)
        weights.forEachIndexed { index, weight ->
            cursor -= weight
            if (cursor < 0) return index
        }
        return null
    }

    private fun buildCellPatches(
        worldId: UUID,
        cell: CellGroup,
        members: List<GatheringPatchCandidate>
    ): List<GatheringPatch> {
        val remaining = members.associateByTo(mutableMapOf(), GatheringPatchCandidate::position)
        val patches = mutableListOf<GatheringPatch>()
        while (remaining.isNotEmpty()) {
            val anchor = remaining.keys.minWith(anchorOrder)
            val component = collectComponent(anchor, remaining.keys)
            component.forEach(remaining::remove)
            val selected = breadthFirst(anchor, component).take(MAX_PATCH_SIZE)
            patches += GatheringPatch(
                id = "${worldId}:${cell.cellX}:${cell.cellY}:${cell.cellZ}:${cell.group}:${anchor.x}:${anchor.y}:${anchor.z}",
                cellX = cell.cellX,
                cellY = cell.cellY,
                cellZ = cell.cellZ,
                group = cell.group,
                anchor = anchor,
                plants = selected
            )
        }
        return patches
    }

    private fun collectComponent(
        start: GatheringPlantPosition,
        candidates: Set<GatheringPlantPosition>
    ): Set<GatheringPlantPosition> = breadthFirst(start, candidates).toSet()

    private fun breadthFirst(
        start: GatheringPlantPosition,
        candidates: Set<GatheringPlantPosition>
    ): List<GatheringPlantPosition> {
        val queue = ArrayDeque<GatheringPlantPosition>()
        val visited = linkedSetOf<GatheringPlantPosition>()
        queue += start
        visited += start
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for ((dx, dz) in horizontalDirections) for (dy in verticalOffsets) {
                val next = GatheringPlantPosition(current.x + dx, current.y + dy, current.z + dz)
                if (next in candidates && visited.add(next)) queue += next
            }
        }
        return visited.toList()
    }

    private data class CellGroup(
        val cellX: Int,
        val cellY: Int,
        val cellZ: Int,
        val group: GatheringVegetationGroup
    )
}
