package jp.awabi2048.cccontent.features.rank.skill.handlers

import jp.awabi2048.cccontent.features.rank.skill.EffectContext
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectHandler

class ReplaceLootTableHandler : SkillEffectHandler {
    companion object {
        const val EFFECT_TYPE = "collect.replace_loot_table"
    }

    override fun getEffectType(): String = EFFECT_TYPE

    override fun getDefaultEvaluationMode(): EvaluationMode = EvaluationMode.RUNTIME

    override fun applyEffect(context: EffectContext): Boolean {
        val event = context.getEvent<org.bukkit.event.block.BlockDropItemEvent>() ?: return false

        val lootTableKey = context.skillEffect.getStringParam("lootTable", "") ?: return false
        if (lootTableKey.isEmpty()) {
            return false
        }

        val targetBlocks = context.skillEffect.getStringListParam("targetBlocks")
        if (targetBlocks.isNotEmpty()) {
            val blockType = event.block.type.name
            if (blockType !in targetBlocks) {
                return false
            }
        }

        val parts = lootTableKey.split(":")
        if (parts.size != 2) {
            return false
        }

        val namespace = parts[0]
        val path = parts[1]
        val key = org.bukkit.NamespacedKey.minecraft(namespace + "/" + path)

        val lootTable = org.bukkit.Bukkit.getLootTable(key) ?: return false

        event.items.clear()

        val context = org.bukkit.loot.LootContext.Builder(event.block.location)
            .killer(event.player)
            .build()

        val loot = lootTable.populateLoot(java.util.Random(), context)
        for (item in loot) {
            event.block.world.dropItemNaturally(event.block.location.add(0.5, 0.5, 0.5), item)
        }

        return true
    }

    override fun calculateStrength(skillEffect: SkillEffect): Double {
        val priority = skillEffect.getIntParam("priority", 0).toDouble()
        return priority
    }

    override fun supportsProfession(professionId: String): Boolean {
        return professionId in listOf("lumberjack", "miner")
    }

    override fun validateParams(skillEffect: SkillEffect): Boolean {
        return skillEffect.params.containsKey("lootTable")
    }
}
