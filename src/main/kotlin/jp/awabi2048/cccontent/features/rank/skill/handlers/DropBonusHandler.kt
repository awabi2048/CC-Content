package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

class DropBonusHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "collect.drop_bonus"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.RUNTIME

    override fun applyEffect(context: EffectContext): Boolean {
        val event = context.getEvent<org.bukkit.event.block.BlockDropItemEvent>() ?: return false

        val amount = context.skillEffect.getIntParam("amount", 0)
        val chance = context.skillEffect.getDoubleParam("chance", 0.0)

        if (amount <= 0 && chance <= 0.0) {
            return false
        }

        val targetBlocks = context.skillEffect.getStringListParam("targetBlocks")
        val targetItems = context.skillEffect.getStringListParam("targetItems")

        if (targetBlocks.isNotEmpty()) {
            val blockType = event.block.type.name
            if (blockType !in targetBlocks) {
                return false
            }
        }

        val dropsToAdd = mutableListOf<org.bukkit.entity.Item>()

        for (drop in event.items) {
            val itemType = drop.itemStack.type.name

            if (targetItems.isNotEmpty() && itemType !in targetItems) {
                continue
            }

            if (amount > 0) {
                val newItem = drop.itemStack.clone()
                newItem.amount = amount
                dropsToAdd.add(event.block.world.dropItemNaturally(event.block.location.add(0.5, 0.5, 0.5), newItem))
            }

            if (chance > 0.0 && kotlin.random.Random.nextDouble() < chance) {
                val newItem = drop.itemStack.clone()
                newItem.amount = 1
                dropsToAdd.add(event.block.world.dropItemNaturally(event.block.location.add(0.5, 0.5, 0.5), newItem))
            }
        }

        return dropsToAdd.isNotEmpty()
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        val amount = skillEffect.getIntParam("amount", 0).toDouble()
        val chance = skillEffect.getDoubleParam("chance", 0.0)
        return amount + chance
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId in listOf("lumberjack", "miner")
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        val amount = skillEffect.getIntParam("amount", 0)
        val chance = skillEffect.getDoubleParam("chance", 0.0)
        return amount > 0 || chance > 0.0
    }
}
