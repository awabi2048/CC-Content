package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

/**
 * 戦士スキルモジュール：狙撃
 *
 * パラメータ:
 *   max_charge_time: Double - 追加チャージの最大時間（tick）
 *   damage_multiplier: Double - ダメージ倍率係数
 *   range_multiplier: Double - 射程倍率（1.0以上）
 */
class WarriorSnipeHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "warrior.snipe"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean {
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        val damage = skillEffect.getDoubleParam("damage_multiplier", 0.0)
        val range = skillEffect.getDoubleParam("range_multiplier", 1.0)
        return damage + (range - 1.0).coerceAtLeast(0.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "warrior"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val maxChargeTime = skillEffect.getDoubleParam("max_charge_time", 0.0)
        val damageMultiplier = skillEffect.getDoubleParam("damage_multiplier", 0.0)
        val rangeMultiplier = skillEffect.getDoubleParam("range_multiplier", 1.0)

        return maxChargeTime > 0.0 && damageMultiplier >= 0.0 && rangeMultiplier >= 1.0
    }
}
