package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

/**
 * 戦士スキルモジュール：矢節約
 *
 * パラメータ:
 *   chance: Double - 矢を消費しない発動確率（0.0..1.0）
 */
class WarriorArrowSavingHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "warrior.arrow_saving"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean {
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return skillEffect.getDoubleParam("chance", 0.0).coerceIn(0.0, 1.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "warrior"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val chance = skillEffect.getDoubleParam("chance", -1.0)
        return chance in 0.0..1.0
    }
}
