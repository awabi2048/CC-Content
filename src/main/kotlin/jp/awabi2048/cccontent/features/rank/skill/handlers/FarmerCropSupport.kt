package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.job.BlockPositionCodec
import jp.awabi2048.cccontent.features.rank.job.IgnoreBlockStore
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Directional
import org.bukkit.entity.Player

object FarmerCropSupport {
    val placedCheckMaterials: Set<Material> = setOf(
        Material.MELON,
        Material.PUMPKIN,
        Material.SUGAR_CANE,
        Material.CACTUS,
        Material.BAMBOO,
        Material.KELP,
        Material.KELP_PLANT
    )

    val harvestItems: Set<Material> = setOf(
        Material.WHEAT,
        Material.CARROT,
        Material.POTATO,
        Material.BEETROOT,
        Material.MELON_SLICE,
        Material.PUMPKIN,
        Material.COCOA_BEANS,
        Material.NETHER_WART,
        Material.SUGAR_CANE,
        Material.CACTUS,
        Material.BAMBOO,
        Material.KELP,
        Material.SWEET_BERRIES
    )

    private val replantSeedByCrop: Map<Material, Material> = mapOf(
        Material.WHEAT to Material.WHEAT_SEEDS,
        Material.CARROTS to Material.CARROT,
        Material.POTATOES to Material.POTATO,
        Material.BEETROOTS to Material.BEETROOT_SEEDS,
        Material.NETHER_WART to Material.NETHER_WART,
        Material.SWEET_BERRY_BUSH to Material.SWEET_BERRIES,
        Material.COCOA to Material.COCOA_BEANS
    )

    private val sweetBerrySoil: Set<Material> = setOf(
        Material.DIRT,
        Material.GRASS_BLOCK,
        Material.PODZOL,
        Material.COARSE_DIRT,
        Material.FARMLAND,
        Material.MOSS_BLOCK,
        Material.ROOTED_DIRT
    )

    private val farmlandConvertible: Set<Material> = setOf(
        Material.DIRT,
        Material.GRASS_BLOCK,
        Material.DIRT_PATH
    )

    private val autoPlantSeedsOnFarmland: List<Pair<Material, Material>> = listOf(
        Material.WHEAT_SEEDS to Material.WHEAT,
        Material.CARROT to Material.CARROTS,
        Material.POTATO to Material.POTATOES,
        Material.BEETROOT_SEEDS to Material.BEETROOTS,
        Material.MELON_SEEDS to Material.MELON_STEM,
        Material.PUMPKIN_SEEDS to Material.PUMPKIN_STEM
    )

    fun isHoe(material: Material): Boolean {
        return material.name.endsWith("_HOE")
    }

    fun isMatureAgeable(blockData: BlockData): Boolean {
        val ageable = blockData as? Ageable ?: return false
        return ageable.age >= ageable.maximumAge
    }

    fun isHarvestableCrop(type: Material, blockData: BlockData): Boolean {
        if (type in placedCheckMaterials) {
            return true
        }
        return isMatureAgeable(blockData)
    }

    fun isFarmerHarvestItem(itemType: Material): Boolean {
        return itemType in harvestItems
    }

    fun shouldGrantExp(block: Block, blockType: Material, originalData: BlockData, ignoreBlockStore: IgnoreBlockStore): Boolean {
        if (blockType in placedCheckMaterials) {
            val packedPosition = BlockPositionCodec.pack(block.x, block.y, block.z)
            return !ignoreBlockStore.contains(block.world.uid, packedPosition)
        }
        return isMatureAgeable(originalData)
    }

    fun isReplantSupportedCrop(type: Material): Boolean {
        return type in replantSeedByCrop.keys
    }

    fun tryConsumeAndReplant(player: Player, targetBlock: Block, originalType: Material, originalData: BlockData): Boolean {
        if (!isReplantSupportedCrop(originalType)) {
            return false
        }

        if (!targetBlock.type.isAir) {
            return false
        }

        val seedItem = replantSeedByCrop[originalType] ?: return false
        if (!canReplantOnSurface(targetBlock, originalType, originalData)) {
            return false
        }

        if (!consumeOneItem(player, seedItem)) {
            return false
        }

        targetBlock.blockData = buildReplantBlockData(originalType, originalData)
        return true
    }

    fun canApplyTillingTo(block: Block, clickedFace: BlockFace): Boolean {
        if (clickedFace == BlockFace.DOWN) {
            return false
        }

        val type = block.type
        val blockAbove = block.getRelative(BlockFace.UP)
        val hasAirAbove = blockAbove.type.isAir

        if (type in farmlandConvertible) {
            return hasAirAbove
        }

        if (type == Material.COARSE_DIRT || type == Material.ROOTED_DIRT) {
            return hasAirAbove
        }

        return false
    }

    fun applyTillingTo(block: Block, clickedFace: BlockFace): Boolean {
        if (!canApplyTillingTo(block, clickedFace)) {
            return false
        }

        return when (block.type) {
            Material.DIRT,
            Material.GRASS_BLOCK,
            Material.DIRT_PATH -> {
                block.type = Material.FARMLAND
                true
            }

            Material.COARSE_DIRT,
            Material.ROOTED_DIRT -> {
                block.type = Material.DIRT
                true
            }

            else -> false
        }
    }

    fun tryAutoPlantSeedOnFarmland(player: Player, tilledBlock: Block): Boolean {
        if (tilledBlock.type != Material.FARMLAND) {
            return false
        }

        val plantBlock = tilledBlock.getRelative(BlockFace.UP)
        if (!plantBlock.type.isAir) {
            return false
        }

        for ((seedItem, cropBlock) in autoPlantSeedsOnFarmland) {
            if (!consumeOneItem(player, seedItem)) {
                continue
            }

            plantBlock.type = cropBlock
            return true
        }

        return false
    }

    private fun canReplantOnSurface(targetBlock: Block, originalType: Material, originalData: BlockData): Boolean {
        if (originalType == Material.COCOA) {
            val directional = originalData as? Directional ?: return false
            val attachedBlock = targetBlock.getRelative(directional.facing.oppositeFace)
            val attachedType = attachedBlock.type
            return attachedType == Material.JUNGLE_LOG ||
                attachedType == Material.STRIPPED_JUNGLE_LOG ||
                attachedType == Material.JUNGLE_WOOD ||
                attachedType == Material.STRIPPED_JUNGLE_WOOD
        }

        val below = targetBlock.getRelative(BlockFace.DOWN).type
        return when (originalType) {
            Material.NETHER_WART -> below == Material.SOUL_SAND
            Material.SWEET_BERRY_BUSH -> below in sweetBerrySoil
            else -> below == Material.FARMLAND
        }
    }

    private fun buildReplantBlockData(originalType: Material, originalData: BlockData): BlockData {
        val cloned = originalData.clone()
        val ageable = cloned as? Ageable
        if (ageable != null) {
            ageable.age = 0
            return ageable
        }
        return originalType.createBlockData()
    }

    private fun consumeOneItem(player: Player, material: Material): Boolean {
        val inventory = player.inventory
        val slotIndex = inventory.contents.indexOfFirst { stack ->
            stack != null && stack.type == material && stack.amount > 0
        }
        if (slotIndex < 0) {
            return false
        }

        val stack = inventory.getItem(slotIndex) ?: return false
        stack.amount -= 1
        if (stack.amount <= 0) {
            inventory.setItem(slotIndex, null)
        } else {
            inventory.setItem(slotIndex, stack)
        }
        return true
    }
}
