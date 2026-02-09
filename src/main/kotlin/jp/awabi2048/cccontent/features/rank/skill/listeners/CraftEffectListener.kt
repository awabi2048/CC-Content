package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.FurnaceSmeltEvent

class CraftEffectListener : Listener {

    @EventHandler
    fun onCraftItem(event: CraftItemEvent) {
        val compiledEffects = SkillEffectEngine.getCachedEffects(event.whoClicked.uniqueId) ?: return
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return

        for ((effectType, entries) in compiledEffects.byType) {
            if (effectType.startsWith("craft.")) {
                val entry = entries.firstOrNull() ?: continue
                val skillEffect = entry.effect
                val profession = compiledEffects.profession

                if (!skillEffect.evaluationMode.name.equals("RUNTIME", ignoreCase = true)) {
                    continue
                }

                SkillEffectEngine.applyEffect(player, profession, entry.skillId, skillEffect, event)
            }
        }
    }

    @EventHandler
    fun onFurnaceSmelt(event: FurnaceSmeltEvent) {
        val compiledEffects = SkillEffectEngine.getCachedEffects(event.block.world.uid) ?: return

        for ((effectType, entries) in compiledEffects.byType) {
            if (effectType.startsWith("craft.")) {
                val entry = entries.firstOrNull() ?: continue
                val skillEffect = entry.effect
                val profession = compiledEffects.profession

                if (!skillEffect.evaluationMode.name.equals("RUNTIME", ignoreCase = true)) {
                    continue
                }
            }
        }
    }
}
