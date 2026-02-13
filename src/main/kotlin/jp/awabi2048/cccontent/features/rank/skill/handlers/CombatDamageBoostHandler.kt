package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import java.util.UUID

/**
 * 戦闘系職業共通スキルモジュール：職業ツールによる攻撃ダメージ強化
 *
 * 職業ツール（剣/斧など）を手に持って攻撃した際に、攻撃ダメージを割合で強化する。
 * 複数スキルが有効な場合は乗算で計算する。
 *
 * パラメータ:
 *   multiplier: Double - ダメージ倍率（例: 1.2 = 20%増）
 *   targetWeapons: List<String> - 対象ウェポンのマテリアル名部分一致リスト（例: ["SWORD", "AXE"]）
 */
class CombatDamageBoostHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "combat.profession_damage_boost"
        const val ATTRIBUTE_NAME = "rank_skill_combat_damage_boost"

        private val ATTRIBUTE_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")

        /** アクティブなモディファイア管理（playerUuid -> modifier） */
        private val activeModifiers = mutableMapOf<UUID, AttributeModifier>()

        /**
         * プレイヤーの攻撃力モディファイアを適用する。
         * 既存のものがあれば上書きする。
         */
        fun applyModifier(playerUuid: UUID, totalMultiplier: Double) {
            val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(
                jp.awabi2048.cccontent.CCContent::class.java
            )
            val player = plugin.server.getPlayer(playerUuid) ?: return
            val attr = player.getAttribute(Attribute.ATTACK_DAMAGE) ?: return

            // 既存のモディファイアを除去
            removeModifier(playerUuid)

            if (totalMultiplier <= 1.0) return

            val modifier = AttributeModifier(
                ATTRIBUTE_UUID,
                ATTRIBUTE_NAME,
                totalMultiplier - 1.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1
            )
            attr.addModifier(modifier)
            activeModifiers[playerUuid] = modifier
        }

        /** プレイヤーのモディファイアを除去する */
        fun removeModifier(playerUuid: UUID) {
            val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(
                jp.awabi2048.cccontent.CCContent::class.java
            )
            val player = plugin.server.getPlayer(playerUuid) ?: run {
                activeModifiers.remove(playerUuid)
                return
            }
            val attr = player.getAttribute(Attribute.ATTACK_DAMAGE) ?: return
            val existing = activeModifiers.remove(playerUuid) ?: return
            attr.removeModifier(existing)
        }

        /** 全プレイヤーのモディファイアを除去する */
        fun removeAllModifiers() {
            for (uuid in activeModifiers.keys.toList()) {
                removeModifier(uuid)
            }
        }
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    /**
     * このハンドラーは CombatEffectListener から直接呼ばれないが、
     * インターフェース上の実装として残す。
     * 実際の適用は applyModifier() を直接使う。
     */
    override fun applyEffect(context: EffectContext): Boolean {
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return skillEffect.getDoubleParam("multiplier", 1.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId in listOf("swordsman", "warrior")
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val multiplier = skillEffect.getDoubleParam("multiplier", 0.0)
        return multiplier > 0.0
    }
}
