package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

/**
 * 戦士スキルモジュール：クイックチャージ
 *
 * パラメータ:
 *   speed_multiplier: Double - 速度換算時のチャージ倍率（> 0.0）
 */
class WarriorQuickChargeHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "warrior.quick_charge"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean {
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return skillEffect.getDoubleParam("speed_multiplier", 0.0).coerceAtLeast(0.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "warrior"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val speedMultiplier = skillEffect.getDoubleParam("speed_multiplier", 0.0)
        return speedMultiplier > 0.0
    }
}
