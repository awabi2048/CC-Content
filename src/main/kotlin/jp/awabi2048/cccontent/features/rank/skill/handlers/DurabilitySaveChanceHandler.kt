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

        // バニラの耐久エンチャントレベルを取得（整数）
        val vanillaLevel = event.item.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING).coerceAtLeast(0)

        // スキルによる耐久レベルを取得（小数）
        val skillLevel = context.skillEffect.getDoubleParam("unbreaking_level", 0.0)

        // 合計レベルを計算
        val totalLevel = vanillaLevel + skillLevel

        if (totalLevel <= 0.0) {
            return false
        }

        // N/(N+1) の確率でダメージをキャンセル
        val chanceToSkipDamage = totalLevel / (totalLevel + 1.0)
        
        if (kotlin.random.Random.nextDouble() < chanceToSkipDamage) {
            event.damage = 0
            return true
        }

        return false
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return skillEffect.getDoubleParam("unbreaking_level", 0.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId in listOf("lumberjack", "miner")
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val unbreakingLevel = skillEffect.getDoubleParam("unbreaking_level", 0.0)
        return unbreakingLevel >= 0.0
    }
}
