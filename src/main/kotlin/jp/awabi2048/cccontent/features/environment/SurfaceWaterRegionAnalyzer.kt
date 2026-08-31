package jp.awabi2048.cccontent.features.environment

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace

/**
 * 海水採取に使える、地表の連続水域を判定します。
 *
 * ワールド全体を走査すると採取一回で重い処理になるため、必要数に達した時点で
 * 打ち切る上限付き幅優先探索にしています。判定は同じY座標の水平面だけを対象にします。
 */
class SurfaceWaterRegionAnalyzer(
    private val environmentResolver: CollectionEnvironmentResolver,
    private val minimumBlocks: Int = 100
) {
    init {
        require(minimumBlocks > 0)
    }

    fun hasMinimumSurfaceWater(block: Block): Boolean {
        if (block.type != Material.WATER || block.getRelative(BlockFace.UP).type != Material.AIR) return false
        if (!environmentResolver.isOceanFamily(environmentResolver.at(block))) return false

        val origin = WaterPosition(block.x, block.y, block.z)
        val queue = ArrayDeque<WaterPosition>()
        val visited = mutableSetOf<WaterPosition>()
        var waterCount = 0
        queue += origin
        while (queue.isNotEmpty() && waterCount < minimumBlocks) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            val currentBlock = block.world.getBlockAt(current.x, current.y, current.z)
            if (currentBlock.type != Material.WATER ||
                currentBlock.getRelative(BlockFace.UP).type != Material.AIR ||
                !environmentResolver.isOceanFamily(environmentResolver.at(currentBlock))) {
                continue
            }
            waterCount++
            for (neighbor in HORIZONTAL_NEIGHBORS) {
                val next = WaterPosition(
                    current.x + neighbor.modX,
                    current.y,
                    current.z + neighbor.modZ
                )
                if (next !in visited) queue += next
            }
        }
        return waterCount >= minimumBlocks
    }

    private data class WaterPosition(val x: Int, val y: Int, val z: Int)

    private companion object {
        val HORIZONTAL_NEIGHBORS = listOf(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)
    }
}
