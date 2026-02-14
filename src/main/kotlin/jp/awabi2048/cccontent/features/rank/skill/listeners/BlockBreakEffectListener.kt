package jp.awabi2048.cccontent.features.rank.skill.listeners

import jp.awabi2048.cccontent.features.rank.job.BlockPositionCodec
import jp.awabi2048.cccontent.features.rank.job.IgnoreBlockStore
import jp.awabi2048.cccontent.features.rank.skill.CompiledEffects
import jp.awabi2048.cccontent.features.rank.skill.ActiveTriggerType
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectRegistry
import jp.awabi2048.cccontent.features.rank.skill.handlers.BlastMineHandler
import jp.awabi2048.cccontent.features.rank.skill.handlers.BreakSpeedBoostHandler
import jp.awabi2048.cccontent.features.rank.skill.handlers.UnlockBatchBreakHandler
import jp.awabi2048.cccontent.CCContent
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent

import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import java.util.UUID

class BlockBreakEffectListener(
    private val ignoreBlockStore: IgnoreBlockStore
) : Listener {
    companion object {

        // BlockBreakEventで保存したブロックタイプをBlockDropItemEventで参照するためのマップ
        private val blockTypeCache: MutableMap<java.util.UUID, String> = mutableMapOf()

        fun setBlockTypeForPlayer(playerUuid: UUID, blockType: String) {
            blockTypeCache[playerUuid] = blockType
        }

        fun getCachedBlockTypeForPlayer(playerUuid: UUID): String? {
            return blockTypeCache.remove(playerUuid)
        }
    }

    private fun isPlayerPlacedBlock(block: org.bukkit.block.Block): Boolean {
        val packedPosition = BlockPositionCodec.pack(block.x, block.y, block.z)
        return ignoreBlockStore.contains(block.world.uid, packedPosition)
    }

    private fun getOrRebuildCompiledEffects(player: org.bukkit.entity.Player): CompiledEffects? {
        val playerUuid = player.uniqueId
        SkillEffectEngine.getCachedEffects(playerUuid)?.let { return it }

        val playerProf = CCContent.rankManager.getPlayerProfession(playerUuid) ?: return null
        SkillEffectEngine.rebuildCache(playerUuid, playerProf.acquiredSkills, playerProf.profession, playerProf.prestigeSkills)
        return SkillEffectEngine.getCachedEffects(playerUuid)
    }



    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val playerUuid = event.player.uniqueId
        val isPlacedBlock = isPlayerPlacedBlock(event.block)
        
        // ブロックタイプをキャッシュ（BlockDropItemEventで参照用）
        BlockBreakEffectListener.setBlockTypeForPlayer(playerUuid, event.block.type.name)

        if (!UnlockBatchBreakHandler.isInternalBreakInProgress(playerUuid) && !BlastMineHandler.isInternalBreakInProgress(playerUuid)) {
            val compiledEffects = getOrRebuildCompiledEffects(event.player)
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
                if (blastMineEntry != null && canTriggerOnAutoBreak(blastMineEntry.effect.type)) {
                    debugApplied = SkillEffectEngine.applyEffect(event.player, compiledEffects.profession, blastMineEntry.skillId, blastMineEntry.effect, event)
                }

                if (!debugApplied && !isPlacedBlock) {
                    val entry = SkillEffectEngine.getCachedEffectForBlock(
                        playerUuid,
                        UnlockBatchBreakHandler.EFFECT_TYPE,
                        event.block.type.name
                    )
 
                    if (entry != null && canTriggerOnAutoBreak(entry.effect.type)) {
                        SkillEffectEngine.applyEffect(event.player, compiledEffects.profession, entry.skillId, entry.effect, event)
                    }
                }
            }
        }

    }

    private fun canTriggerOnAutoBreak(effectType: String): Boolean {
        val handler = SkillEffectRegistry.getHandler(effectType) ?: return false
        if (!handler.isActiveSkill()) {
            return true
        }

        return handler.getTriggerType() == ActiveTriggerType.AUTO_BREAK
    }

    @EventHandler
    fun onBlockDropItem(event: BlockDropItemEvent) {
        // キャッシュからブロックタイプを取得（BlockDropItemEventではブロックが既に削除されてAIRになっているため）
        val blockType = BlockBreakEffectListener.getCachedBlockTypeForPlayer(event.player.uniqueId) ?: event.block.type.name

        val compiledEffects = getOrRebuildCompiledEffects(event.player) ?: return

        val hasSilkTouch = event.player.inventory.itemInMainHand.containsEnchantment(Enchantment.SILK_TOUCH)
        val profession = compiledEffects.profession

        if (!hasSilkTouch) {
            val dropBonusEntries = compiledEffects.byType["collect.drop_bonus"] ?: emptyList()

            if (dropBonusEntries.isNotEmpty()) {
                val combinedEffect = SkillEffectEngine.combineEffects(dropBonusEntries, blockType)

                if (combinedEffect != null) {
                    val representativeSkillId = dropBonusEntries.firstOrNull()?.skillId ?: "unknown"
                    SkillEffectEngine.applyEffect(event.player, profession, representativeSkillId, combinedEffect, event)
                }
            }
        }

        val replaceLootEntry = SkillEffectEngine.getCachedEffectForBlock(event.player.uniqueId, "general.replace_loot_table", blockType)
        if (replaceLootEntry != null) {
            SkillEffectEngine.applyEffect(event.player, profession, replaceLootEntry.skillId, replaceLootEntry.effect, event)
        }

        val replantEntry = SkillEffectEngine.getCachedEffectForBlock(event.player.uniqueId, "lumberjack.replant", blockType)
        if (replantEntry != null) {
            BlockBreakEffectListener.setBlockTypeForPlayer(event.player.uniqueId, blockType)
            SkillEffectEngine.applyEffect(event.player, profession, replantEntry.skillId, replantEntry.effect, event)
        }
    }

    @EventHandler
    fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        val playerUuid = event.player.uniqueId
        val player = event.player
        

        
        // 古い速度ブーストを削除
        BreakSpeedBoostHandler.clearBoost(playerUuid)

        
        // 次のティックで新しいアイテム情報を取得して速度ブーストを適用
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(jp.awabi2048.cccontent.CCContent::class.java)
        org.bukkit.Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, {
            // プレイヤーがオンラインか確認
            val currentPlayer = org.bukkit.Bukkit.getPlayer(playerUuid) ?: return@scheduleSyncDelayedTask
            
            // 新しいメインハンドアイテムに対して速度ブーストを適用
            val compiledEffects = SkillEffectEngine.getCachedEffects(playerUuid)
            if (compiledEffects == null) {
                return@scheduleSyncDelayedTask
            }
            
            val profession = compiledEffects.profession
            val breakSpeedEntries = compiledEffects.byType["collect.break_speed_boost"] ?: emptyList()
            if (breakSpeedEntries.isEmpty()) {
                return@scheduleSyncDelayedTask
            }
            
            // 新しいメインハンドアイテムを取得
            val newMainHandItem = currentPlayer.inventory.itemInMainHand
            val toolType = newMainHandItem.type.name
            val vanillaEfficiencyLevel = newMainHandItem.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.EFFICIENCY)
            
            // 複数の速度ブーストエフェクトを合成
            val combinedEffect = SkillEffectEngine.combineEffects(breakSpeedEntries, "")
            if (combinedEffect == null) {
                return@scheduleSyncDelayedTask
            }
            
            // 合成したエフェクトの効率強化レベル情報を取得
            val skillEfficiencyLevel = combinedEffect?.getDoubleParam("efficiency_level", 0.0) ?: 0.0
            
            BreakSpeedBoostHandler.applySpeedBoost(currentPlayer, vanillaEfficiencyLevel, skillEfficiencyLevel)
        }, 1L) // 1ティック遅延
    }

    @EventHandler
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val playerUuid = event.player.uniqueId
        val player = event.player
        

        
        BreakSpeedBoostHandler.clearBoost(playerUuid)

        
        // 次のティックで新しいアイテム情報を取得して速度ブーストを再適用
        val plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(jp.awabi2048.cccontent.CCContent::class.java)
        org.bukkit.Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, {
            // プレイヤーがオンラインか確認
            val currentPlayer = org.bukkit.Bukkit.getPlayer(playerUuid) ?: return@scheduleSyncDelayedTask
            
            val compiledEffects = SkillEffectEngine.getCachedEffects(playerUuid)
            if (compiledEffects == null) {
                return@scheduleSyncDelayedTask
            }
            
            val profession = compiledEffects.profession
            val breakSpeedEntries = compiledEffects.byType["collect.break_speed_boost"] ?: emptyList()
            if (breakSpeedEntries.isEmpty()) {
                return@scheduleSyncDelayedTask
            }
            
            // 新しいメインハンドアイテムを取得
            val newMainHandItem = currentPlayer.inventory.itemInMainHand
            val toolType = newMainHandItem.type.name
            val vanillaEfficiencyLevel = newMainHandItem.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.EFFICIENCY)
            
            val combinedEffect = SkillEffectEngine.combineEffects(breakSpeedEntries, "")
            if (combinedEffect == null) {
                return@scheduleSyncDelayedTask
            }
            
            // 合成したエフェクトの効率強化レベル情報を取得
            val skillEfficiencyLevel = combinedEffect?.getDoubleParam("efficiency_level", 0.0) ?: 0.0
            
            BreakSpeedBoostHandler.applySpeedBoost(currentPlayer, vanillaEfficiencyLevel, skillEfficiencyLevel)
        }, 1L) // 1ティック遅延
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerUuid = event.player.uniqueId
        BreakSpeedBoostHandler.clearBoost(playerUuid)

        UnlockBatchBreakHandler.stopForPlayer(playerUuid)
        BlastMineHandler.stopForPlayer(playerUuid)
    }
}
