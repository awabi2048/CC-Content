package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import org.bukkit.potion.PotionEffectType

class SwordsmanUnderdogBuffHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "swordsman.underdog_buff"

        data class ParsedBuff(val type: PotionEffectType, val amplifier: Int)

        fun parseBuffNotation(raw: String): ParsedBuff? {
            val normalized = raw.trim().uppercase()
            val separatorIndex = normalized.lastIndexOf('_')
            if (separatorIndex <= 0 || separatorIndex >= normalized.lastIndex) {
                return null
            }

            val typeName = normalized.substring(0, separatorIndex)
            val level = normalized.substring(separatorIndex + 1).toIntOrNull() ?: return null
            if (level <= 0) {
                return null
            }

            val effectType = PotionEffectType.getByName(typeName) ?: return null
            return ParsedBuff(effectType, level - 1)
        }
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean {
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        val ratio = skillEffect.getDoubleParam("ratio", 1.0)
        val radius = skillEffect.getIntParam("radius", 1)
        return ratio + radius
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "swordsman"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val radius = skillEffect.getIntParam("radius", -1)
        if (radius <= 0) {
            return false
        }

        val ratio = skillEffect.getDoubleParam("ratio", -1.0)
        if (ratio < 0.0) {
            return false
        }

        val buff = skillEffect.getStringParam("buff", "")
        return parseBuffNotation(buff) != null
    }
}
