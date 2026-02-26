package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

/**
 * 戦士スキルモジュール：3WAY射撃
 *
 * パラメータ:
 *   arrow_consumption: Int - 1射撃あたりの総消費本数
 *   side_damage_multiplier: Double - 左右の矢のダメージ倍率（中央矢基準）
 */
class WarriorThreeWayHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "warrior.3way"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean {
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return skillEffect.getDoubleParam("side_damage_multiplier", 1.0).coerceAtLeast(0.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "warrior"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val arrowConsumption = skillEffect.getIntParam("arrow_consumption", -1)
        val sideDamageMultiplier = skillEffect.getDoubleParam("side_damage_multiplier", 1.0)
        return arrowConsumption >= 1 && sideDamageMultiplier >= 0.0
    }
}
