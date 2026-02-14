package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.ActiveSkillManager
import jp.awabi2048.cccontent.features.rank.skill.ActiveTriggerType
import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class WindGustHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "collect.wind_gust_mock"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun isActiveSkill(): Boolean = true

    override fun getTriggerType(): ActiveTriggerType = ActiveTriggerType.MANUAL_SHIFT_RIGHT_CLICK

    override fun applyEffect(context: EffectContext): Boolean {
        val player = context.player
        if (!ActiveSkillManager.isActiveSkillById(player.uniqueId, context.skillId)) {
            return false
        }

        player.sendMessage(
            Component.text("突風を発動しました（モック）")
                .color(NamedTextColor.AQUA)
        )
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double = 1.0

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "lumberjack"
    }
}
