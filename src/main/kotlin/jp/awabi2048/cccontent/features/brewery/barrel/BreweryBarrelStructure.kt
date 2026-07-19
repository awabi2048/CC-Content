package jp.awabi2048.cccontent.features.brewery.barrel

import jp.awabi2048.cccontent.features.brewery.model.BarrelSize
import jp.awabi2048.cccontent.features.brewery.model.BreweryLocationKey
import org.bukkit.Material
import org.bukkit.block.BlockFace
import java.util.UUID

enum class BarrelBlockRole {
    PLANKS,
    STAIRS_OUTWARD,
    STAIRS_INWARD,
    FENCE
}

data class BarrelStructureCell(
    val width: Int,
    val height: Int,
    val depth: Int,
    val role: BarrelBlockRole
)

data class BreweryBarrel(
    val id: UUID,
    val size: BarrelSize,
    val origin: BreweryLocationKey,
    val sign: BreweryLocationKey,
    val outward: BlockFace,
    val woodType: String,
    val members: Set<BreweryLocationKey>
)

object BreweryBarrelStructure {
    fun cells(size: BarrelSize): List<BarrelStructureCell> = when (size) {
        BarrelSize.SMALL -> buildList {
            for (width in 0..1) {
                for (depth in 0..1) {
                    add(BarrelStructureCell(width, 0, depth, BarrelBlockRole.PLANKS))
                    add(
                        BarrelStructureCell(
                            width,
                            1,
                            depth,
                            if (depth == 0) BarrelBlockRole.STAIRS_OUTWARD else BarrelBlockRole.STAIRS_INWARD
                        )
                    )
                }
            }
        }
        BarrelSize.BIG -> buildList {
            for (height in 0..1) {
                for (width in 0..3) {
                    for (depth in 0..2) {
                        add(BarrelStructureCell(width, height, depth, BarrelBlockRole.PLANKS))
                    }
                }
            }
            for (width in 0..3) {
                add(BarrelStructureCell(width, 2, 0, BarrelBlockRole.STAIRS_OUTWARD))
                add(BarrelStructureCell(width, 2, 1, BarrelBlockRole.PLANKS))
                add(BarrelStructureCell(width, 2, 2, BarrelBlockRole.STAIRS_INWARD))
            }
            for (width in listOf(0, 3)) {
                for (depth in listOf(0, 2)) {
                    add(BarrelStructureCell(width, -1, depth, BarrelBlockRole.FENCE))
                }
            }
        }
    }

    fun material(woodType: String, role: BarrelBlockRole): Material? {
        val prefix = woodType.uppercase()
        val suffix = when (role) {
            BarrelBlockRole.PLANKS -> "PLANKS"
            BarrelBlockRole.STAIRS_OUTWARD,
            BarrelBlockRole.STAIRS_INWARD -> "STAIRS"
            BarrelBlockRole.FENCE -> "FENCE"
        }
        return Material.matchMaterial("${prefix}_$suffix")
    }

    fun woodType(material: Material): String? {
        val name = material.name.lowercase()
        return SUPPORTED_WOODS.firstOrNull { wood ->
            name == "${wood}_planks" ||
                name == "${wood}_stairs" ||
                name == "${wood}_fence" ||
                name == "${wood}_wall_sign" ||
                name == "${wood}_sign"
        }
    }

    val SUPPORTED_WOODS: Set<String> = setOf(
        "oak",
        "spruce",
        "birch",
        "jungle",
        "acacia",
        "dark_oak",
        "mangrove",
        "cherry",
        "pale_oak",
        "crimson",
        "warped"
    )
}
