package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlot
import java.util.UUID

class BreakSpeedBoostHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "collect.break_speed_boost"
        const val DEFAULT_MAX_MULTIPLIER = 3.0
        const val ATTRIBUTE_NAME = "rank_skill_break_speed_boost"

        private val ATTRIBUTE_UUID = UUID.fromString("4d2f0120-f0c4-4b2b-9a2d-0f9c4b0c5d6e")
        private val activeBoosts = mutableMapOf<UUID, ActiveBoost>()

        fun clearBoost(playerUuid: UUID) {
            val boost = activeBoosts.remove(playerUuid) ?: return
            val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(jp.awabi2048.cccontent.CCContent::class.java)
            val player = plugin.server.getPlayer(playerUuid) ?: return

            val blockBreakingSpeed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED) ?: return
            blockBreakingSpeed.removeModifier(boost.modifier)
        }

        fun clearAllBoosts() {
            val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(jp.awabi2048.cccontent.CCContent::class.java)
            for ((playerUuid, _) in activeBoosts.toMap()) {
                clearBoost(playerUuid)
            }
            activeBoosts.clear()
        }

        fun addActiveBoost(playerUuid: UUID, boost: ActiveBoost) {
            activeBoosts[playerUuid] = boost
        }
    }

    data class ActiveBoost(
        val modifier: AttributeModifier,
        val targetBlocks: List<String>,
        val targetTools: List<String>
    )

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.RUNTIME

    override fun applyEffect(context: EffectContext): Boolean {
        val event = context.getEvent<org.bukkit.event.block.BlockDamageEvent>() ?: return false
        val player = event.player
        val maxMultiplier = context.skillEffect.getDoubleParam("maxMultiplier", DEFAULT_MAX_MULTIPLIER)
        val multiplier = context.skillEffect.getDoubleParam("multiplier", 1.0).coerceAtMost(maxMultiplier)

        val targetBlocks = context.skillEffect.getStringListParam("targetBlocks")
        val targetTools = context.skillEffect.getStringListParam("targetTools")

        if (targetBlocks.isNotEmpty()) {
            val blockType = event.block.type.name
            if (blockType !in targetBlocks) {
                return false
            }
        }

        if (targetTools.isNotEmpty()) {
            val tool = player.inventory.itemInMainHand
            val toolType = tool.type.name
            if (!targetTools.any { toolType.contains(it) }) {
                return false
            }
        }

        BreakSpeedBoostHandler.Companion.clearBoost(player.uniqueId)

        val blockBreakingSpeed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED) ?: return false
        val amount = (multiplier - 1.0) * blockBreakingSpeed.baseValue

        val modifier = AttributeModifier(
            BreakSpeedBoostHandler.Companion.ATTRIBUTE_UUID,
            BreakSpeedBoostHandler.Companion.ATTRIBUTE_NAME,
            amount,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1
        )

        blockBreakingSpeed.addModifier(modifier)

        BreakSpeedBoostHandler.Companion.addActiveBoost(player.uniqueId, ActiveBoost(modifier, targetBlocks, targetTools))

        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return skillEffect.getDoubleParam("multiplier", 1.0)
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId in listOf("lumberjack", "miner")
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val multiplier = skillEffect.getDoubleParam("multiplier", 0.0)
        return multiplier > 0.0
    }
}
