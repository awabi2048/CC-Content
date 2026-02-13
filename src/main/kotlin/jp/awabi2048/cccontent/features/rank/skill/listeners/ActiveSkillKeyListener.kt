package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.rank.skill.ActiveSkillIdentifier
import jp.awabi2048.cccontent.features.rank.skill.ActiveSkillManager
import jp.awabi2048.cccontent.features.rank.skill.SkillSwitchMode
import jp.awabi2048.cccontent.features.rank.skill.ToolType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerSwapHandItemsEvent

/**
 * Fキー（スワップハンド）での能動スキル切替リスナー
 */
class ActiveSkillKeyListener : Listener {

    @EventHandler
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return

        // 能動スキルが1つもない場合は何もしない
        if (!ActiveSkillIdentifier.hasAnyActiveSkill(player)) {
            return
        }

        // 切替様式をチェック
        when (profession.skillSwitchMode) {
            SkillSwitchMode.MENU_ONLY -> {
                // メニューからのみの場合はFキーでの切替を無視
                return
            }

            SkillSwitchMode.ANY_F_KEY -> {
                // 全てのFキー押下で切り替え
                event.isCancelled = true
                ActiveSkillManager.rotateActiveSkill(player)
            }

            SkillSwitchMode.TOOL_F_KEY -> {
                // 職業ツールを持っている場合のみ切り替え
                val mainHandItem = player.inventory.itemInMainHand
                val toolType = ToolType.fromMaterial(mainHandItem.type)

                if (toolType != null) {
                    event.isCancelled = true
                    ActiveSkillManager.rotateActiveSkill(player)
                }
            }
        }
    }
}
