package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.rank.skill.ActiveSkillIdentifier
import jp.awabi2048.cccontent.features.rank.skill.ActiveSkillManager
import jp.awabi2048.cccontent.features.rank.skill.ActiveTriggerType
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectRegistry
import jp.awabi2048.cccontent.features.rank.skill.ToolType
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class ActiveSkillTriggerListener : Listener {

    @EventHandler
    fun onShiftRightClick(event: PlayerInteractEvent) {
        val hand = event.hand
        if (hand != null && hand != EquipmentSlot.HAND) {
            return
        }

        if (!event.player.isSneaking) {
            return
        }

        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) {
            return
        }

        val player = event.player
        val mainHandMaterial = player.inventory.itemInMainHand.type

        val playerProfession = CCContent.rankManager.getPlayerProfession(player.uniqueId) ?: return
        val activeSkills = ActiveSkillIdentifier.getPlayerActiveSkills(playerProfession)
        if (activeSkills.isEmpty()) {
            return
        }

        val activeSkillId = ActiveSkillManager.getCurrentActiveSkillId(player) ?: run {
            val fallbackSkillId = activeSkills.first()
            playerProfession.activeSkillId = fallbackSkillId
            CCContent.rankManager.savePlayerProfession(player.uniqueId)
            fallbackSkillId
        }

        if (!isAllowedProfessionTool(mainHandMaterial, playerProfession.profession)) {
            return
        }

        val skillTree = SkillTreeRegistry.getSkillTree(playerProfession.profession) ?: return
        val skillEffect = skillTree.getSkill(activeSkillId)?.effect ?: return
        if (!isAllowedBySkillTargetTools(mainHandMaterial, skillEffect)) {
            return
        }

        val handler = SkillEffectRegistry.getHandler(skillEffect.type) ?: return
        if (!handler.isActiveSkill()) {
            return
        }

        if (handler.getTriggerType() != ActiveTriggerType.MANUAL_SHIFT_RIGHT_CLICK) {
            return
        }

        val applied = SkillEffectEngine.applyEffect(
            player,
            playerProfession.profession,
            activeSkillId,
            skillEffect,
            event
        )
        if (applied) {
            event.isCancelled = true
        }
    }

    private fun isAllowedProfessionTool(mainHandMaterial: Material, profession: Profession): Boolean {
        val requiredToolType = when (profession) {
            Profession.LUMBERJACK -> ToolType.AXE
            Profession.MINER -> ToolType.PICKAXE
            Profession.FARMER -> ToolType.HOE
            Profession.SWORDSMAN -> ToolType.SWORD
            Profession.WARRIOR -> ToolType.AXE
            else -> null
        } ?: return false

        return ToolType.fromMaterial(mainHandMaterial) == requiredToolType
    }

    private fun isAllowedBySkillTargetTools(mainHandMaterial: Material, skillEffect: SkillEffect): Boolean {
        val targetTools = skillEffect.getStringListParam("targetTools")
        if (targetTools.isEmpty()) {
            return true
        }

        return targetTools.any { matchesToolKeyword(mainHandMaterial, it) }
    }

    private fun matchesToolKeyword(mainHandMaterial: Material, rawKeyword: String): Boolean {
        val keyword = rawKeyword.uppercase()
        val name = mainHandMaterial.name
        return when (keyword) {
            "AXE" -> name.endsWith("_AXE")
            "PICKAXE" -> name.endsWith("_PICKAXE")
            "HOE" -> name.endsWith("_HOE")
            "SHOVEL" -> name.endsWith("_SHOVEL")
            "SWORD" -> name.endsWith("_SWORD")
            "SHEARS" -> name == "SHEARS"
            else -> name == keyword
        }
    }
}
