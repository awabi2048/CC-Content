package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import jp.awabi2048.cccontent.features.rank.skill.listeners.BlockBreakEffectListener
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block

class ReplantHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "lumberjack.replant"

        private val LOG_TO_SAPLING = mapOf(
            "OAK_LOG" to "OAK_SAPLING",
            "BIRCH_LOG" to "BIRCH_SAPLING",
            "SPRUCE_LOG" to "SPRUCE_SAPLING",
            "JUNGLE_LOG" to "JUNGLE_SAPLING",
            "ACACIA_LOG" to "ACACIA_SAPLING",
            "DARK_OAK_LOG" to "DARK_OAK_SAPLING",
            "CHERRY_LOG" to "CHERRY_SAPLING",
            "CRIMSON_STEM" to "CRIMSON_FUNGUS",
            "WARPED_STEM" to "WARPED_FUNGUS"
        )

        private val PLANTABLE_BLOCKS = setOf(
            Material.DIRT,
            Material.GRASS_BLOCK,
            Material.PODZOL,
            Material.COARSE_DIRT,
            Material.FARMLAND,
            Material.ROOTED_DIRT,
            Material.MOSS_BLOCK,
            Material.CRIMSON_NYLIUM,
            Material.WARPED_NYLIUM,
            Material.SOUL_SOIL
        )

        private val processedBlocks: MutableMap<java.util.UUID, Long> = mutableMapOf()

        fun markAsProcessed(playerUuid: java.util.UUID) {
            processedBlocks[playerUuid] = System.currentTimeMillis()
        }

        fun isProcessed(playerUuid: java.util.UUID): Boolean {
            val lastTime = processedBlocks[playerUuid] ?: return false
            if (System.currentTimeMillis() - lastTime > 5000) {
                processedBlocks.remove(playerUuid)
                return false
            }
            return true
        }

        fun clearProcessed(playerUuid: java.util.UUID) {
            processedBlocks.remove(playerUuid)
        }
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.RUNTIME

    override fun applyEffect(context: EffectContext): Boolean {
        val event = context.getEvent<org.bukkit.event.block.BlockDropItemEvent>() ?: return false

        val player = event.player
        val playerUuid = player.uniqueId

        val cachedBlockType = BlockBreakEffectListener.getCachedBlockTypeForPlayer(playerUuid) ?: return false

        val saplingType = LOG_TO_SAPLING[cachedBlockType] ?: return false

        if (UnlockBatchBreakHandler.isInternalBreakInProgress(playerUuid)) {
            if (isProcessed(playerUuid)) {
                return false
            }

            val lowestBlock = UnlockBatchBreakHandler.getLowestBreakPosition(playerUuid) ?: return false
            if (!isValidPlantPosition(lowestBlock)) {
                return false
            }

            val blockBelow = lowestBlock.getRelative(0, -1, 0)
            if (blockBelow.type !in PLANTABLE_BLOCKS) {
                return false
            }

            val saplingMaterial = Material.matchMaterial(saplingType) ?: return false
            if (!consumeOneSapling(player, saplingMaterial)) {
                return false
            }

            lowestBlock.type = saplingMaterial
            markAsProcessed(playerUuid)

            player.world.playSound(lowestBlock.location, Sound.BLOCK_ROOTED_DIRT_PLACE, 1.0f, 1.0f)

            return true
        }

        val block = event.block
        val blockType = block.type.name

        if (blockType != "AIR") {
            return false
        }

        val blockBelow = block.getRelative(0, -1, 0)
        if (blockBelow.type !in PLANTABLE_BLOCKS) {
            return false
        }

        val saplingMaterial = Material.matchMaterial(saplingType) ?: return false
        if (!consumeOneSapling(player, saplingMaterial)) {
            return false
        }

        block.type = saplingMaterial

        player.world.playSound(block.location, Sound.BLOCK_ROOTED_DIRT_PLACE, 1.0f, 1.0f)

        return true
    }

    private fun isValidPlantPosition(block: Block): Boolean {
        return block.type.isAir
    }

    private fun consumeOneSapling(player: org.bukkit.entity.Player, saplingMaterial: Material): Boolean {
        val inventory = player.inventory
        val slotIndex = inventory.contents.indexOfFirst { stack ->
            stack != null && stack.type == saplingMaterial && stack.amount > 0
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

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return 1.0
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "lumberjack"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        return true
    }
}
