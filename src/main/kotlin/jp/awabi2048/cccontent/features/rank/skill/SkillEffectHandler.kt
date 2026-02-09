package jp.awabi2048.cccontent.features.rank.skill

import jp.awabi2048.cccontent.features.rank.profession.SkillTree

interface SkillEffectHandler {
    fun getEffectType(): String

    fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    fun applyEffect(context: EffectContext): Boolean

    fun calculateStrength(skillEffect: SkillEffect): Double

    fun getSkillDepth(skillId: String, skillTree: SkillTree): Int {
        return SkillDepthCalculator.calculateDepth(skillId, skillTree)
    }

    fun supportsProfession(professionId: String): Boolean = true

    fun validateParams(skillEffect: SkillEffect): Boolean = true

    fun isEnabled(): Boolean = true
}
