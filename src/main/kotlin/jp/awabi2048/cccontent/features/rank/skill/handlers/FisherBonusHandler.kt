package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

class FisherBonusHandler private constructor(
    private val effectType: String,
    private val paramKey: String,
    private val validValue: (Double) -> Boolean,
    private val strength: (Double) -> Double
) : SkillEffectHandler {
    override fun getEffectType(): String = effectType

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean = true

    override fun calculateStrength(skillEffect: SkillEffect): Double =
        strength(skillEffect.getDoubleParam(paramKey, 0.0))

    override fun supportsProfession(professionId: String): Boolean = professionId == "fisher"

    override fun validateParams(skillEffect: SkillEffect): Boolean =
        validValue(skillEffect.getDoubleParam(paramKey, Double.NaN))

    companion object {
        const val HOOK_WINDOW_EFFECT = "fisher.hook_window_bonus"
        const val STABILITY_EFFECT = "fisher.stability_bonus"
        const val DURATION_EFFECT = "fisher.duration_multiplier"

        fun all(): List<FisherBonusHandler> = listOf(
            FisherBonusHandler(HOOK_WINDOW_EFFECT, "multiplier", { it.isFinite() && it >= 1.0 }, { it - 1.0 }),
            FisherBonusHandler(STABILITY_EFFECT, "bonus", { it.isFinite() && it in 0.0..0.9 }, { it }),
            FisherBonusHandler(DURATION_EFFECT, "multiplier", { it.isFinite() && it in 0.1..1.0 }, { 1.0 - it })
        )
    }
}
