package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

/**
 * 剣士スキルモジュール：スウィープ攻撃ダメージ強化
 *
 * スウィープ攻撃時に Sweeping Edge の仮想レベルを加算して強化する。
 * 複数スキルが有効な場合は加算で計算する。
 *
 * パラメータ:
 *   sweeping_edge: Double - Sweeping Edge の追加レベル（小数可）
 */
class SweepAttackDamageBoostHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "swordsman.sweep_damage_boost"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean {
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return skillEffect.getDoubleParam("sweeping_edge", 0.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "swordsman"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val sweepingEdge = skillEffect.getDoubleParam("sweeping_edge", 0.0)
        return sweepingEdge > 0
    }
}
