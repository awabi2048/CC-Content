package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

/**
 * 戦士スキルモジュール：照準強化（ホーミング）
 *
 * パラメータ:
 *   homing_strength: Double - 1tickあたりの最大旋回角（度）
 *   duration: Int - ホーミング持続時間（tick）
 */
class WarriorAimingHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "warrior.aiming"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean {
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        val homingStrength = skillEffect.getDoubleParam("homing_strength", 0.0).coerceAtLeast(0.0)
        val duration = skillEffect.getIntParam("duration", 0).coerceAtLeast(0)
        return homingStrength * duration.toDouble()
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "warrior"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val homingStrength = skillEffect.getDoubleParam("homing_strength", 0.0)
        val duration = skillEffect.getIntParam("duration", 0)
        return homingStrength > 0.0 && duration > 0
    }
}
