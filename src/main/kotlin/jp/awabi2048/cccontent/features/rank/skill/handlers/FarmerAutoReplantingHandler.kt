package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler
import org.bukkit.event.block.BlockDropItemEvent

class FarmerAutoReplantingHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "farmer.auto_replanting"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.RUNTIME

    override fun applyEffect(context: EffectContext): Boolean {
        val event = context.getEvent<BlockDropItemEvent>() ?: return false
        val originalType = event.blockState.type
        val originalData = event.blockState.blockData

        if (!FarmerCropSupport.isReplantSupportedCrop(originalType)) {
            return false
        }

        return FarmerCropSupport.tryConsumeAndReplant(
            player = event.player,
            targetBlock = event.block,
            originalType = originalType,
            originalData = originalData
        )
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        return 1.0
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId == "farmer"
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        return true
    }
}
