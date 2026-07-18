package jp.awabi2048.cccontent.features.brewery.barrel

import jp.awabi2048.cccontent.features.brewery.model.BarrelSize
import jp.awabi2048.cccontent.features.brewery.model.BreweryLocationKey
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.Stairs
import org.bukkit.block.data.type.WallSign
import java.util.UUID

data class BarrelMatchFailure(
    val location: BreweryLocationKey,
    val expected: String,
    val actual: String
)

sealed interface BarrelMatchResult {
    data class Matched(val barrel: BreweryBarrel) : BarrelMatchResult
    data class Failed(val failure: BarrelMatchFailure) : BarrelMatchResult
}

class BreweryBarrelMatcher {
    fun match(signBlock: Block): BarrelMatchResult {
        val wallSign = signBlock.blockData as? WallSign
            ?: return failed(signBlock, "wall sign", signBlock.type.name)
        val outward = wallSign.facing
        val attached = signBlock.getRelative(outward.oppositeFace)
        val woodType = BreweryBarrelStructure.woodType(signBlock.type)
            ?: return failed(signBlock, "supported wood sign", signBlock.type.name)

        var firstFailure: BarrelMatchFailure? = null
        for (size in listOf(BarrelSize.SMALL, BarrelSize.BIG)) {
            val width = if (size == BarrelSize.SMALL) 2 else 4
            val anchorHeight = if (size == BarrelSize.SMALL) 0 else 1
            for (signWidth in 0 until width) {
                val origin = offset(attached, outward, -signWidth, -anchorHeight, 0)
                when (val result = validate(size, origin, signBlock, outward, woodType, UUID.randomUUID())) {
                    is BarrelMatchResult.Matched -> return result
                    is BarrelMatchResult.Failed -> if (firstFailure == null) firstFailure = result.failure
                }
            }
        }
        return BarrelMatchResult.Failed(
            firstFailure ?: BarrelMatchFailure(
                BreweryLocationKey.fromBlock(attached),
                "small or large barrel",
                attached.type.name
            )
        )
    }

    fun validate(barrel: BreweryBarrel): BarrelMatchResult {
        val origin = barrel.origin.toLocation()?.block
            ?: return BarrelMatchResult.Failed(
                BarrelMatchFailure(barrel.origin, "loaded world", "missing world")
            )
        val sign = barrel.sign.toLocation()?.block
            ?: return BarrelMatchResult.Failed(
                BarrelMatchFailure(barrel.sign, "wall sign", "missing world")
            )
        return validate(barrel.size, origin, sign, barrel.outward, barrel.woodType, barrel.id)
    }

    private fun validate(
        size: BarrelSize,
        origin: Block,
        sign: Block,
        outward: BlockFace,
        woodType: String,
        id: UUID
    ): BarrelMatchResult {
        val members = linkedSetOf<BreweryLocationKey>()
        for (cell in BreweryBarrelStructure.cells(size)) {
            val block = offset(origin, outward, cell.width, cell.height, cell.depth)
            val expectedMaterial = BreweryBarrelStructure.material(woodType, cell.role)
            if (block.type != expectedMaterial) {
                return BarrelMatchResult.Failed(
                    BarrelMatchFailure(
                        BreweryLocationKey.fromBlock(block),
                        expectedMaterial?.name ?: cell.role.name,
                        block.type.name
                    )
                )
            }
            if (block.blockData is Stairs) {
                val expectedFacing = when (cell.role) {
                    BarrelBlockRole.STAIRS_OUTWARD -> outward
                    BarrelBlockRole.STAIRS_INWARD -> outward.oppositeFace
                    else -> null
                }
                if (expectedFacing != null && (block.blockData as Stairs).facing != expectedFacing) {
                    return BarrelMatchResult.Failed(
                        BarrelMatchFailure(
                            BreweryLocationKey.fromBlock(block),
                            "stairs facing ${expectedFacing.name}",
                            "stairs facing ${(block.blockData as Stairs).facing.name}"
                        )
                    )
                }
            }
            members += BreweryLocationKey.fromBlock(block)
        }
        members += BreweryLocationKey.fromBlock(sign)
        return BarrelMatchResult.Matched(
            BreweryBarrel(
                id = id,
                size = size,
                origin = BreweryLocationKey.fromBlock(origin),
                sign = BreweryLocationKey.fromBlock(sign),
                outward = outward,
                woodType = woodType,
                members = members
            )
        )
    }

    private fun offset(
        origin: Block,
        outward: BlockFace,
        width: Int,
        height: Int,
        depth: Int
    ): Block {
        val right = rightOf(outward)
        return origin
            .getRelative(right, width)
            .getRelative(BlockFace.UP, height)
            .getRelative(outward.oppositeFace, depth)
    }

    private fun rightOf(face: BlockFace): BlockFace = when (face) {
        BlockFace.NORTH -> BlockFace.EAST
        BlockFace.EAST -> BlockFace.SOUTH
        BlockFace.SOUTH -> BlockFace.WEST
        BlockFace.WEST -> BlockFace.NORTH
        else -> error("Barrel sign must face horizontally: $face")
    }

    private fun failed(block: Block, expected: String, actual: String): BarrelMatchResult.Failed =
        BarrelMatchResult.Failed(
            BarrelMatchFailure(BreweryLocationKey.fromBlock(block), expected, actual)
        )
}
