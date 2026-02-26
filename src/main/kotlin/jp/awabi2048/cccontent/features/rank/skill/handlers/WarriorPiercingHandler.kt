package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

/**
 * 戦士スキルモジュール：貫通射撃
 *
 * パラメータ:
 *   max_pierce_count: Int - 貫通できる最大エンティティ数
 */
class WarriorPiercingHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "warrior.piercing"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean {
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return skillEffect.getIntParam("max_pierce_count", 0).toDouble().coerceAtLeast(0.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "warrior"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val maxPierceCount = skillEffect.getIntParam("max_pierce_count", -1)
        return maxPierceCount >= 1
    }
}
