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
import org.bukkit.Sound
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

        // 職業ツールを持っているかチェック
        if (!isAllowedProfessionTool(mainHandMaterial, playerProfession.profession)) {
            return
        }

        // Fキー対象スキルを取得
        val toggleableSkills = ActiveSkillIdentifier.getToggleableSkillsForFKey(playerProfession)
        if (toggleableSkills.isEmpty()) {
            return
        }

        // 現在選択中のスキルを取得
        val currentSkillId = ActiveSkillManager.getCurrentActiveSkillId(player) ?: run {
            val fallbackSkillId = toggleableSkills.first()
            playerProfession.activeSkillId = fallbackSkillId
            CCContent.rankManager.savePlayerProfession(player.uniqueId)
            fallbackSkillId
        }

        val skillTree = SkillTreeRegistry.getSkillTree(playerProfession.profession) ?: return
        val skillNode = skillTree.getSkill(currentSkillId) ?: return
        val skillEffect = skillNode.effect ?: return

        // 対象ツールチェック
        if (!isAllowedBySkillTargetTools(mainHandMaterial, skillEffect)) {
            return
        }

        val handler = SkillEffectRegistry.getHandler(skillEffect.type) ?: return

        // 手動発動スキル（MANUAL_SHIFT_RIGHT_CLICK）の場合は発動処理
        if (handler.isActiveSkill() && handler.getTriggerType() == ActiveTriggerType.MANUAL_SHIFT_RIGHT_CLICK) {
            val applied = SkillEffectEngine.applyEffect(
                player,
                playerProfession.profession,
                currentSkillId,
                skillEffect,
                event
            )
            if (applied) {
                event.isCancelled = true
            }
            return
        }

        // それ以外のスキルはON/OFF切替
        if (!ActiveSkillManager.isSkillToggleable(player, currentSkillId)) {
            return
        }

        val newState = ActiveSkillManager.toggleCurrentSkillActivation(player)
        if (newState != null) {
            event.isCancelled = true
            // 効果音を再生
            player.playSound(
                player.location,
                Sound.UI_BUTTON_CLICK,
                0.8f,
                if (newState) 1.2f else 0.8f
            )
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
