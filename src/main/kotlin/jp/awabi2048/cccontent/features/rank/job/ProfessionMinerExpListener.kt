package jp.awabi2048.cccontent.features.rank.job

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.action.ContentAction
import com.awabi2048.ccsystem.api.action.ContentActionType
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionExperience
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.Ageable
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import java.util.UUID

/**
 * 再設計された採集職の通常作業をコード定義で記録する。
 * バニラの破壊、ドロップ、経験値オーブ、耐久処理には介入しない。
 */
class ProfessionMinerExpListener(
    private val rankManager: RankManager,
    private val ignoreBlockStore: IgnoreBlockStore
) : Listener {
    companion object {
        private val MINER_MATERIALS = setOf(
            Material.COAL_ORE,
            Material.DEEPSLATE_COAL_ORE,
            Material.COPPER_ORE,
            Material.DEEPSLATE_COPPER_ORE,
            Material.IRON_ORE,
            Material.DEEPSLATE_IRON_ORE,
            Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE,
            Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE,
            Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_QUARTZ_ORE,
            Material.NETHER_GOLD_ORE,
            Material.ANCIENT_DEBRIS
        )

        private val LUMBERJACK_MATERIALS = setOf(
            Material.OAK_LOG,
            Material.BIRCH_LOG,
            Material.SPRUCE_LOG,
            Material.JUNGLE_LOG,
            Material.ACACIA_LOG,
            Material.DARK_OAK_LOG,
            Material.MANGROVE_LOG,
            Material.CHERRY_LOG,
            Material.PALE_OAK_LOG,
            Material.CRIMSON_STEM,
            Material.WARPED_STEM
        )

        private val FARMER_AGEABLE_MATERIALS = setOf(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.COCOA,
            Material.NETHER_WART,
            Material.SWEET_BERRY_BUSH
        )

        private val FARMER_NATURAL_MATERIALS = setOf(
            Material.MELON,
            Material.PUMPKIN,
            Material.SUGAR_CANE,
            Material.CACTUS,
            Material.BAMBOO,
            Material.KELP,
            Material.KELP_PLANT
        )
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.blockPlaced
        ignoreBlockStore.add(block.world.uid, BlockPositionCodec.pack(block.x, block.y, block.z))
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val profession = rankManager.getPlayerProfession(player.uniqueId)?.profession ?: return
        val actionType = when (profession) {
            Profession.MINER -> if (event.block.type in MINER_MATERIALS) ContentActionType.MINERAL_EXTRACTED else null
            Profession.LUMBERJACK -> if (event.block.type in LUMBERJACK_MATERIALS) ContentActionType.TREE_PROCESSED else null
            Profession.FARMER -> farmerActionType(event.block)
            else -> null
        } ?: return

        if (isPlayerPlaced(event.block)) return
        rankManager.addProfessionExp(player.uniqueId, ProfessionExperience.NORMAL_ACTION)
        rankManager.recordProfessionCycleAction(player.uniqueId)
        CCSystem.getAPI().getContentActionDispatcher().publish(
            ContentAction(
                actionId = UUID.randomUUID(),
                schemaVersion = 1,
                occurredAt = CCSystem.getAPI().getSharedClockService().now().toInstant(),
                playerId = player.uniqueId,
                actionType = actionType,
                amount = 1L,
                worldKey = event.block.world.key,
                metadata = mapOf("material" to event.block.type.key.toString())
            )
        )
    }

    private fun farmerActionType(block: Block): ContentActionType? = when {
        block.type in FARMER_AGEABLE_MATERIALS && isFullyGrownAgeable(block) -> ContentActionType.CROP_HARVESTED
        block.type in FARMER_NATURAL_MATERIALS -> ContentActionType.PLANT_GATHERED
        else -> null
    }

    private fun isPlayerPlaced(block: Block): Boolean = ignoreBlockStore.contains(
        block.world.uid,
        BlockPositionCodec.pack(block.x, block.y, block.z)
    )

    private fun isFullyGrownAgeable(block: Block): Boolean {
        val ageable = block.blockData as? Ageable ?: return false
        return ageable.age >= ageable.maximumAge
    }
}
