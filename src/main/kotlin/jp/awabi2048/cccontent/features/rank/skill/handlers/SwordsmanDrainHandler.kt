package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

class SwordsmanDrainHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "swordsman.drain"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean {
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        val ratio = skillEffect.getDoubleParam("ratio", 0.0).coerceAtLeast(0.0)
        val maxHeal = skillEffect.getDoubleParam("max_heal", 0.0).coerceAtLeast(0.0)
        val chance = skillEffect.getDoubleParam("chance", 0.0).coerceIn(0.0, 1.0)
        return ratio * chance + maxHeal
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "swordsman"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val ratio = skillEffect.getDoubleParam("ratio", -1.0)
        if (ratio <= 0.0) {
            return false
        }

        val maxHeal = skillEffect.getDoubleParam("max_heal", -1.0)
        if (maxHeal < 0.0) {
            return false
        }

        val chance = skillEffect.getDoubleParam("chance", -1.0)
        return chance in 0.0..1.0
    }
}
