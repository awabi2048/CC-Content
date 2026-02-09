package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.features.rank.job.BlockPositionCodec
import jp.awabi2048.cccontent.features.rank.job.IgnoreBlockStore
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

class BlockBreakEffectListener(
    private val ignoreBlockStore: IgnoreBlockStore
) : Listener {
    companion object {
        private const val DURABILITY_EFFECT_WINDOW_MILLIS = 1500L
    }

    private val durabilityEligibleUntil: MutableMap<java.util.UUID, Long> = mutableMapOf()

    private fun isPlayerPlacedBlock(block: org.bukkit.block.Block): Boolean {
        val packedPosition = BlockPositionCodec.pack(block.x, block.y, block.z)
        return ignoreBlockStore.contains(block.world.uid, packedPosition)
    }

    @EventHandler
    fun onBlockDamage(event: BlockDamageEvent) {
        if (isPlayerPlacedBlock(event.block)) {
            BreakSpeedBoostHandler.clearBoost(event.player.uniqueId)
            durabilityEligibleUntil.remove(event.player.uniqueId)
            return
        }

        durabilityEligibleUntil[event.player.uniqueId] = System.currentTimeMillis() + DURABILITY_EFFECT_WINDOW_MILLIS

        val compiledEffects = SkillEffectEngine.getCachedEffects(event.player.uniqueId) ?: return

        val effectType = "collect.break_speed_boost"
        val entry = SkillEffectEngine.getCachedEffectForBlock(event.player.uniqueId, effectType, event.block.type.name) ?: return

        val skillEffect = entry.effect
        val profession = compiledEffects.profession

        SkillEffectEngine.applyEffect(event.player, profession, entry.skillId, skillEffect, event)
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        BreakSpeedBoostHandler.clearBoost(event.player.uniqueId)
        durabilityEligibleUntil.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onBlockDropItem(event: BlockDropItemEvent) {
        if (isPlayerPlacedBlock(event.block)) {
            return
        }

        val compiledEffects = SkillEffectEngine.getCachedEffects(event.player.uniqueId) ?: return

        val hasSilkTouch = event.player.inventory.itemInMainHand.containsEnchantment(Enchantment.SILK_TOUCH)
        val profession = compiledEffects.profession
        val blockType = event.block.type.name

        val replaceLootEntry = SkillEffectEngine.getCachedEffectForBlock(event.player.uniqueId, "collect.replace_loot_table", blockType)
        if (replaceLootEntry != null) {
            SkillEffectEngine.applyEffect(event.player, profession, replaceLootEntry.skillId, replaceLootEntry.effect, event)
        }

        if (!hasSilkTouch) {
            val dropBonusEntry = SkillEffectEngine.getCachedEffectForBlock(event.player.uniqueId, "collect.drop_bonus", blockType)
            if (dropBonusEntry != null) {
                SkillEffectEngine.applyEffect(event.player, profession, dropBonusEntry.skillId, dropBonusEntry.effect, event)
            }
        }
    }

    @EventHandler
    fun onPlayerItemDamage(event: PlayerItemDamageEvent) {
        val expiresAt = durabilityEligibleUntil[event.player.uniqueId] ?: return
        if (System.currentTimeMillis() > expiresAt) {
            durabilityEligibleUntil.remove(event.player.uniqueId)
            return
        }

        val compiledEffects = SkillEffectEngine.getCachedEffects(event.player.uniqueId) ?: return

        val effectType = "collect.durability_save_chance"
        val entry = SkillEffectEngine.getCachedEffect(event.player.uniqueId, effectType) ?: return

        val skillEffect = entry.effect
        val profession = compiledEffects.profession
        SkillEffectEngine.applyEffect(event.player, profession, entry.skillId, skillEffect, event)
    }

    @EventHandler
    fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        BreakSpeedBoostHandler.clearBoost(event.player.uniqueId)
        durabilityEligibleUntil.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        BreakSpeedBoostHandler.clearBoost(event.player.uniqueId)
        durabilityEligibleUntil.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        BreakSpeedBoostHandler.clearBoost(event.player.uniqueId)
        durabilityEligibleUntil.remove(event.player.uniqueId)
    }
}
