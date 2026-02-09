package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.handlers.BreakSpeedBoostHandler
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent

class BlockBreakEffectListener : Listener {

    @EventHandler
    fun onBlockDamage(event: BlockDamageEvent) {
        val compiledEffects = SkillEffectEngine.getCachedEffects(event.player.uniqueId) ?: return

        val effectType = "collect.break_speed_boost"
        val entry = compiledEffects.byType[effectType] ?: return

        val skillEffect = entry.effect
        val profession = compiledEffects.profession

        SkillEffectEngine.applyEffect(event.player, profession, entry.skillId, skillEffect, event)
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        BreakSpeedBoostHandler.clearBoost(event.player.uniqueId)
    }

    @EventHandler
    fun onBlockDropItem(event: BlockDropItemEvent) {
        val compiledEffects = SkillEffectEngine.getCachedEffects(event.player.uniqueId) ?: return

        for ((effectType, entry) in compiledEffects.byType) {
            if (effectType == "collect.drop_bonus" || effectType == "collect.replace_loot_table") {
                val hasSilkTouch = event.player.inventory.itemInMainHand.containsEnchantment(Enchantment.SILK_TOUCH)

                if (effectType == "collect.drop_bonus" && hasSilkTouch) {
                    continue
                }

                val skillEffect = entry.effect
                val profession = compiledEffects.profession
                SkillEffectEngine.applyEffect(event.player, profession, entry.skillId, skillEffect, event)
            }
        }
    }

    @EventHandler
    fun onPlayerItemDamage(event: PlayerItemDamageEvent) {
        val compiledEffects = SkillEffectEngine.getCachedEffects(event.player.uniqueId) ?: return

        val effectType = "collect.durability_save_chance"
        val entry = compiledEffects.byType[effectType] ?: return

        val skillEffect = entry.effect
        val profession = compiledEffects.profession
        SkillEffectEngine.applyEffect(event.player, profession, entry.skillId, skillEffect, event)
    }

    @EventHandler
    fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        BreakSpeedBoostHandler.clearBoost(event.player.uniqueId)
    }

    @EventHandler
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        BreakSpeedBoostHandler.clearBoost(event.player.uniqueId)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        BreakSpeedBoostHandler.clearBoost(event.player.uniqueId)
    }
}
