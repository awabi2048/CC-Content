package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

class UnlockItemTokenHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "general.unlock_item_token"
        private val unlockedItems: MutableSet<String> = mutableSetOf()
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean {
        val items = context.skillEffect.getStringListParam("items")
        for (item in items) {
            unlockedItems.add("${context.playerUuid}_$item")
        }
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        val items = skillEffect.getStringListParam("items").size
        return items.toDouble()
    }

    override fun supportsProfession(professionId: String): Boolean {
        return true
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        return skillEffect.params.containsKey("items")
    }

    fun isItemUnlocked(playerUuid: java.util.UUID, item: String): Boolean {
        return unlockedItems.contains("${playerUuid}_$item")
    }

    fun clearUnlockedItems() {
        unlockedItems.clear()
    }
}
