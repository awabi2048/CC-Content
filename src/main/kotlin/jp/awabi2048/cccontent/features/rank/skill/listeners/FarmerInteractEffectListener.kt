package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.skill.CompiledEffects
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.handlers.FarmerAreaTillingHandler
import jp.awabi2048.cccontent.features.rank.skill.handlers.FarmerCropSupport
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class FarmerInteractEffectListener : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onHoeRightClick(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) {
            return
        }

        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            return
        }

        val player = event.player
        if (!FarmerCropSupport.isHoe(player.inventory.itemInMainHand.type)) {
            return
        }

        val clickedBlock = event.clickedBlock ?: return
        val compiledEffects = getOrRebuildCompiledEffects(player) ?: return
        if (compiledEffects.profession != Profession.FARMER) {
            return
        }

        val entry = SkillEffectEngine.getCachedEffectForBlock(
            player.uniqueId,
            FarmerAreaTillingHandler.EFFECT_TYPE,
            clickedBlock.type.name
        ) ?: return

        SkillEffectEngine.applyEffect(player, compiledEffects.profession, entry.skillId, entry.effect, event)
    }

    private fun getOrRebuildCompiledEffects(player: org.bukkit.entity.Player): CompiledEffects? {
        val playerUuid = player.uniqueId
        SkillEffectEngine.getCachedEffects(playerUuid)?.let { return it }

        val playerProf = CCContent.rankManager.getPlayerProfession(playerUuid) ?: return null
        SkillEffectEngine.rebuildCache(playerUuid, playerProf.acquiredSkills, playerProf.profession, playerProf.prestigeSkills)
        return SkillEffectEngine.getCachedEffects(playerUuid)
    }
}
