package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

class UnlockSystemHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "craft.unlock_system"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean {
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return 1.0
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId in listOf("brewer", "cook")
    }

    override fun isEnabled(): Boolean {
        return false
    }
}
