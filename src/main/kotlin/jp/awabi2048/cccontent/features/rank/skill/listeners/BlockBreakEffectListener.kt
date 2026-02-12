package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.features.rank.job.BlockPositionCodec
import jp.awabi2048.cccontent.features.rank.job.IgnoreBlockStore
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.handlers.BlastMineHandler
import jp.awabi2048.cccontent.features.rank.skill.handlers.BreakSpeedBoostHandler
import jp.awabi2048.cccontent.features.rank.skill.handlers.UnlockBatchBreakHandler
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
    private val ignoreBlockStore: IgnoreBlockStore,
    private val blastMineHandler: BlastMineHandler? = null
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
        val profession = compiledEffects.profession

        val effectType = "collect.break_speed_boost"
        val blockTypeName = event.block.type.name

        // 対象ブロックに有効な全エントリを取得
        val allEntries = compiledEffects.byType[effectType]
        if (allEntries.isNullOrEmpty()) return

        // 優先度順に合成されたエフェクトを取得
        val combinedEffect = SkillEffectEngine.combineEffects(allEntries, blockTypeName) ?: return

        // 最深スキルを特定（合成後も適用対象情報が必要）
        val deepestEntry = allEntries.firstOrNull() ?: return

        SkillEffectEngine.applyEffect(event.player, profession, deepestEntry.skillId, combinedEffect, event)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val playerUuid = event.player.uniqueId

        if (!isPlayerPlacedBlock(event.block) && !UnlockBatchBreakHandler.isInternalBreakInProgress(playerUuid) && !BlastMineHandler.isInternalBreakInProgress(playerUuid)) {
            val compiledEffects = SkillEffectEngine.getCachedEffects(playerUuid)
            val debugMode = UnlockBatchBreakHandler.BatchBreakMode.fromTool(event.player.inventory.itemInMainHand.type)
            var debugApplied = false

            val blastMineDebugEffect = BlastMineHandler.getDebugEffect(playerUuid)
            if (blastMineDebugEffect != null) {
                val profession = compiledEffects?.profession ?: jp.awabi2048.cccontent.features.rank.profession.Profession.MINER
                debugApplied = SkillEffectEngine.applyEffect(event.player, profession, "debug.blast_mine", blastMineDebugEffect, event)
            }

            if (!debugApplied && debugMode != null) {
                val debugEffect = UnlockBatchBreakHandler.getDebugEffect(playerUuid, debugMode)
                if (debugEffect != null) {
                    val profession = compiledEffects?.profession ?: when (debugMode) {
                        UnlockBatchBreakHandler.BatchBreakMode.CUT_ALL -> jp.awabi2048.cccontent.features.rank.profession.Profession.LUMBERJACK
                        UnlockBatchBreakHandler.BatchBreakMode.MINE_ALL -> jp.awabi2048.cccontent.features.rank.profession.Profession.MINER
                    }
                    debugApplied = SkillEffectEngine.applyEffect(event.player, profession, "debug.${debugMode.modeKey}", debugEffect, event)
                }
            }

            if (!debugApplied && compiledEffects != null) {
                val blastMineEntry = SkillEffectEngine.getCachedEffectForBlock(
                    playerUuid,
                    BlastMineHandler.EFFECT_TYPE,
                    event.block.type.name
                )
                if (blastMineEntry != null) {
                    debugApplied = SkillEffectEngine.applyEffect(event.player, compiledEffects.profession, blastMineEntry.skillId, blastMineEntry.effect, event)
                }

                if (!debugApplied) {
                    val entry = SkillEffectEngine.getCachedEffectForBlock(
                        playerUuid,
                        UnlockBatchBreakHandler.EFFECT_TYPE,
                        event.block.type.name
                    )

                    if (entry != null) {
                        SkillEffectEngine.applyEffect(event.player, compiledEffects.profession, entry.skillId, entry.effect, event)
                    }
                }
            }
        }

        BreakSpeedBoostHandler.clearBoost(playerUuid)
        durabilityEligibleUntil.remove(playerUuid)
    }

    @EventHandler
    fun onBlockDropItem(event: BlockDropItemEvent) {
        if (isPlayerPlacedBlock(event.block)) {
            return
        }

        blastMineHandler?.applyDropModification(event.player, event)

        val compiledEffects = SkillEffectEngine.getCachedEffects(event.player.uniqueId) ?: return

        val hasSilkTouch = event.player.inventory.itemInMainHand.containsEnchantment(Enchantment.SILK_TOUCH)
        val profession = compiledEffects.profession
        val blockType = event.block.type.name

        val replaceLootEntry = SkillEffectEngine.getCachedEffectForBlock(event.player.uniqueId, "collect.replace_loot_table", blockType)
        if (replaceLootEntry != null) {
            SkillEffectEngine.applyEffect(event.player, profession, replaceLootEntry.skillId, replaceLootEntry.effect, event)
        }

        if (!hasSilkTouch) {
            // 複数の drop_bonus スキルを加算対応
            val dropBonusEntries = compiledEffects.byType["collect.drop_bonus"] ?: emptyList()
            if (dropBonusEntries.isNotEmpty()) {
                val combinedEffect = SkillEffectEngine.combineEffects(dropBonusEntries)
                if (combinedEffect != null) {
                    // 代表スキル ID は最深のものを使用
                    val representativeSkillId = dropBonusEntries.firstOrNull()?.skillId ?: "unknown"
                    SkillEffectEngine.applyEffect(event.player, profession, representativeSkillId, combinedEffect, event)
                }
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
        val playerUuid = event.player.uniqueId
        BreakSpeedBoostHandler.clearBoost(playerUuid)
        durabilityEligibleUntil.remove(playerUuid)
        UnlockBatchBreakHandler.stopForPlayer(playerUuid)
        BlastMineHandler.stopForPlayer(playerUuid)
    }
}
