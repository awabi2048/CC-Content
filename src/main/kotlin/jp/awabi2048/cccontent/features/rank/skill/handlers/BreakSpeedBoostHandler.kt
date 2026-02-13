package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import org.bukkit.attribute.Attribute
import java.util.UUID

class BreakSpeedBoostHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "collect.break_speed_boost"

        private val boostedPlayers = mutableSetOf<UUID>()

        fun clearBoost(playerUuid: UUID) {
            if (!boostedPlayers.remove(playerUuid)) return
            val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(jp.awabi2048.cccontent.CCContent::class.java)
            val player = plugin.server.getPlayer(playerUuid) ?: return
            val miningEfficiency = player.getAttribute(Attribute.MINING_EFFICIENCY) ?: return
            // base値をデフォルト値に戻す
            miningEfficiency.baseValue = miningEfficiency.defaultValue

        }

        fun clearAllBoosts() {
            val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(jp.awabi2048.cccontent.CCContent::class.java)
            for (playerUuid in boostedPlayers.toSet()) {
                val player = plugin.server.getPlayer(playerUuid) ?: continue
                val miningEfficiency = player.getAttribute(Attribute.MINING_EFFICIENCY) ?: continue
                miningEfficiency.baseValue = miningEfficiency.defaultValue
            }
            boostedPlayers.clear()
        }

        /**
         * バニラとスキルの効率レベル差分をbase値に加算する
         */
        fun applySpeedBoost(player: org.bukkit.entity.Player, vanillaEfficiencyLevel: Int, skillEfficiencyLevel: Double) {
            val miningEfficiency = player.getAttribute(Attribute.MINING_EFFICIENCY) ?: run {
    
                return
            }

            // base値をデフォルト値にリセット
            miningEfficiency.baseValue = miningEfficiency.defaultValue

            // バニラの効率レベルによる速度値: n^2 + 1
            val vanillaSpeed = (vanillaEfficiencyLevel.toDouble() * vanillaEfficiencyLevel.toDouble()) + 1.0
            // スキル込みの合計効率レベルによる速度値: (n+m)^2 + 1
            val totalEfficiency = vanillaEfficiencyLevel + skillEfficiencyLevel.toInt()
            val totalSpeed = (totalEfficiency.toDouble() * totalEfficiency.toDouble()) + 1.0
            // 差分をbase値に加算
            val diff = totalSpeed - vanillaSpeed
            miningEfficiency.baseValue = miningEfficiency.defaultValue + diff

            boostedPlayers.add(player.uniqueId)


        }
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.CACHED

    override fun applyEffect(context: EffectContext): Boolean {
        // このハンドラーはListener経由で適用されるため、ここでは処理しない
        return false
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return skillEffect.getDoubleParam("efficiency_level", 0.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId in listOf("lumberjack", "miner")
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val efficiencyLevel = skillEffect.getDoubleParam("efficiency_level", 0.0)
        return efficiencyLevel >= 0.0
    }
}