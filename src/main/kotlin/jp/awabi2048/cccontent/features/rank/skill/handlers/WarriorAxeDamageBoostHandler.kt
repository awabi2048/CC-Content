package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

/**
 * 戦士スキルモジュール：斧攻撃ダメージ強化
 *
 * パラメータ:
 *   sharpness: Double - Sharpnessの追加レベル（小数可）
 */
class WarriorAxeDamageBoostHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "warrior.axe_damage_boost"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean {
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return skillEffect.getDoubleParam("sharpness", 0.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "warrior"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val sharpness = skillEffect.getDoubleParam("sharpness", 0.0)
        return sharpness > 0.0
    }
}
