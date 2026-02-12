package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import java.util.UUID

/**
 * 戦闘系職業共通スキルモジュール：攻撃リーチ拡張
 *
 * 職業ツールを手に持っている場合のみ minecraft:entity_interaction_range 属性を
 * Attribute Modifier で変更し、攻撃可能距離を拡張する。
 * ツールを持ち替えた際は除去し、適切なタイミングで再適用する。
 *
 * パラメータ:
 *   reach_blocks: Double - 追加リーチ量（ブロック数、例: 1.5）
 *   targetWeapons: List<String> - 対象ウェポンのマテリアル名部分一致リスト（例: ["SWORD", "AXE"]）
 */
class AttackReachBoostHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "combat.attack_reach_boost"
        const val ATTRIBUTE_NAME = "rank_skill_attack_reach_boost"

        private val ATTRIBUTE_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901")

        /** アクティブなモディファイア管理（playerUuid -> modifier） */
        private val activeModifiers = mutableMapOf<UUID, AttributeModifier>()

        /**
         * プレイヤーのリーチモディファイアを適用する。
         * @param playerUuid 対象プレイヤーUUID
         * @param extraReach 追加リーチ量（ブロック数）
         */
        fun applyModifier(playerUuid: UUID, extraReach: Double) {
            val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(
                jp.awabi2048.cccontent.CCContent::class.java
            )
            val player = plugin.server.getPlayer(playerUuid) ?: return
            val attr = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE) ?: return

            // 既存のモディファイアを除去
            removeModifier(playerUuid)

            if (extraReach <= 0.0) return

            val modifier = AttributeModifier(
                ATTRIBUTE_UUID,
                ATTRIBUTE_NAME,
                extraReach,
                AttributeModifier.Operation.ADD_NUMBER
            )
            attr.addModifier(modifier)
            activeModifiers[playerUuid] = modifier
        }

        /** プレイヤーのリーチモディファイアを除去する */
        fun removeModifier(playerUuid: UUID) {
            val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(
                jp.awabi2048.cccontent.CCContent::class.java
            )
            val player = plugin.server.getPlayer(playerUuid) ?: run {
                activeModifiers.remove(playerUuid)
                return
            }
            val attr = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE) ?: return
            val existing = activeModifiers.remove(playerUuid) ?: return
            attr.removeModifier(existing)
        }

        /** 全プレイヤーのリーチモディファイアを除去する */
        fun removeAllModifiers() {
            for (uuid in activeModifiers.keys.toList()) {
                removeModifier(uuid)
            }
        }

        /**
         * プレイヤーが職業ツールを手に持っているか確認し、リーチを再評価する。
         * @param playerUuid 対象プレイヤーUUID
         * @param targetWeapons 対象ウェポンの部分一致リスト
         * @param extraReach 適用するリーチ量
         */
        fun reevaluateReach(playerUuid: UUID, targetWeapons: List<String>, extraReach: Double) {
            val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(
                jp.awabi2048.cccontent.CCContent::class.java
            )
            val player = plugin.server.getPlayer(playerUuid) ?: return
            val heldItem = player.inventory.itemInMainHand.type.name

            val isHoldingTool = targetWeapons.isEmpty() || targetWeapons.any { heldItem.contains(it) }

            if (isHoldingTool) {
                applyModifier(playerUuid, extraReach)
            } else {
                removeModifier(playerUuid)
            }
        }
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.RUNTIME

    /**
     * このハンドラーは CombatEffectListener から直接呼ばれないが、
     * インターフェース上の実装として残す。
     * 実際の適用は reevaluateReach() / applyModifier() を直接使う。
     */
    override fun applyEffect(context: EffectContext): Boolean {
        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return skillEffect.getDoubleParam("reach_blocks", 0.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId in listOf("swordsman", "warrior")
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val reach = skillEffect.getDoubleParam("reach_blocks", -1.0)
        return reach > 0.0
    }
}
