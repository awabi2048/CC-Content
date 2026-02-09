package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

class DurabilitySaveChanceHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "collect.durability_save_chance"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.RUNTIME

    override fun applyEffect(context: EffectContext): Boolean {
        val event = context.getEvent<org.bukkit.event.player.PlayerItemDamageEvent>() ?: return false

        val saveChance = context.skillEffect.getDoubleParam("saveChance", 0.0)
        if (saveChance <= 0.0) {
            return false
        }

        val targetTools = context.skillEffect.getStringListParam("targetTools")
        if (targetTools.isNotEmpty()) {
            val toolType = event.item.type.name
            if (!targetTools.any { toolType.contains(it) }) {
                return false
            }
        }

        if (kotlin.random.Random.nextDouble() < saveChance) {
            event.damage = 0
            return true
        }

        return false
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return skillEffect.getDoubleParam("saveChance", 0.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId in listOf("lumberjack", "miner")
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val saveChance = skillEffect.getDoubleParam("saveChance", 0.0)
        return saveChance > 0.0 && saveChance <= 1.0
    }
}
