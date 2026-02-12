package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent

/**
 * 戦闘系職業共通スキルモジュール：スウィープ攻撃ダメージ強化
 *
 * スウィープ攻撃（DamageCause.ENTITY_SWEEP_ATTACK）時に
 * ダメージを割合で強化する。複数スキルが有効な場合は乗算で計算する。
 *
 * パラメータ:
 *   sweep_multiplier: Double - スウィープ攻撃ダメージ倍率（例: 1.3 = 30%増）
 */
class SweepAttackDamageBoostHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "combat.sweep_damage_boost"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.RUNTIME

    /**
     * スウィープ攻撃イベント時にダメージを倍率分だけ増加させる。
     * イベントのダメージを直接書き換えるため、Attribute Modifier は使用しない。
     */
    override fun applyEffect(context: EffectContext): Boolean {
        val event = context.getEvent<EntityDamageByEntityEvent>() ?: return false

        if (event.cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return false

        val multiplier = context.skillEffect.getDoubleParam("sweep_multiplier", 1.0)
        if (multiplier <= 1.0) return false

        event.damage = event.damage * multiplier
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return skillEffect.getDoubleParam("sweep_multiplier", 1.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId in listOf("swordsman", "warrior")
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val multiplier = skillEffect.getDoubleParam("sweep_multiplier", 0.0)
        return multiplier > 0.0
    }
}
