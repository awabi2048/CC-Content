package jp.awabi2048.cccontent.features.rank.job

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.action.ContentAction
import com.awabi2048.ccsystem.api.action.ContentActionType
import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionExperience
import jp.awabi2048.cccontent.features.resourcecollection.ResourceCollectionKind
import jp.awabi2048.cccontent.features.resourcecollection.ResourceMaterialPolicy
import jp.awabi2048.cccontent.features.resourcecollection.SpecialistCollectionService
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import java.util.UUID

/**
 * 再設計された採集職の通常作業をコード定義で記録する。
 * バニラの破壊、ドロップ、経験値オーブ、耐久処理には介入しない。
 */
class ProfessionMinerExpListener(
    private val rankManager: RankManager
) : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        if (SpecialistCollectionService.isInternalBreak(player.uniqueId, event.block)) return
        val profession = rankManager.getPlayerProfession(player.uniqueId)?.profession ?: return
        val kind = ResourceMaterialPolicy.classify(event.block.type, event.block.blockData) ?: return
        if (kind.profession != profession) return
        val actionType = when (kind) {
            ResourceCollectionKind.MINERAL -> ContentActionType.MINERAL_EXTRACTED
            ResourceCollectionKind.FOREST -> ContentActionType.TREE_PROCESSED
            ResourceCollectionKind.CROP -> if (event.block.blockData is org.bukkit.block.data.Ageable) {
                ContentActionType.CROP_HARVESTED
            } else {
                ContentActionType.PLANT_GATHERED
            }
        }

        if (!CCSystem.getAPI().getNaturalOriginRegistry().isNatural(event.block)) return
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

}
