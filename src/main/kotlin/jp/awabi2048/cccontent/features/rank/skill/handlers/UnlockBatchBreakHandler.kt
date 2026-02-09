package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

class UnlockBatchBreakHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "collect.unlock_batch_break"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean {
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        val profile = skillEffect.getStringParam("profile", "3x3")
        return when (profile) {
            "3x3" -> 1.0
            "vertical" -> 2.0
            "chain" -> 3.0
            else -> 1.0
        }
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId in listOf("lumberjack", "miner")
    }

    override fun isEnabled(): Boolean {
        return false
    }
}
