package jp.awabi2048.cccontent.features.rank.skill

import jp.awabi2048.cccontent.features.rank.prestige.PrestigeToken
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.SkillTree
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

data class CompiledEffects(
    val byType: Map<String, List<SkillEffectEntry>>,
    val targetedByType: Map<String, TargetedEffectCache>,
    val playerUuid: UUID,
    val profession: Profession,
    val timestamp: Long = System.currentTimeMillis()
)

data class TargetedEffectCache(
    val byBlockType: Map<String, SkillEffectEntry>,
    val fallback: SkillEffectEntry?
)

data class SkillEffectEntry(
    val skillId: String,
    val effect: SkillEffect,
    val depth: Int,
    val strength: Double
)

object SkillEffectEngine {
    private val effectCache: MutableMap<UUID, CompiledEffects> = mutableMapOf()
    private val blockTargetedEffectTypes = setOf(
        "collect.break_speed_boost",
        "collect.drop_bonus",
        "collect.replace_loot_table",
        "collect.unlock_batch_break"
    )

    fun rebuildCache(playerUuid: UUID, acquiredSkills: Set<String>, profession: Profession, prestigeSkills: Set<String> = emptySet()) {
        val skillTree = SkillTreeRegistry.getSkillTree(profession) ?: return
        val mutableByType = mutableMapOf<String, MutableList<SkillEffectEntry>>()

        // 通常スキルを追加
        for (skillId in acquiredSkills) {
            val skill = skillTree.getSkill(skillId) ?: continue
            val effect = skill.effect
            if (effect == null) {
                org.bukkit.Bukkit.getLogger().info("[SkillEffectEngine] Skill $skillId has no effect")
                continue
            }

            val handler = SkillEffectRegistry.getHandler(effect.type)
            if (handler == null) {
                org.bukkit.Bukkit.getLogger().info("[SkillEffectEngine] No handler for effect type: ${effect.type}")
                continue
            }
            if (!handler.isEnabled() || !handler.supportsProfession(profession.id)) {
                org.bukkit.Bukkit.getLogger().info("[SkillEffectEngine] Handler disabled or not supported: ${effect.type}")
                continue
            }

            val depth = SkillDepthCalculator.calculateDepth(skillId, skillTree)
            val strength = handler.calculateStrength(effect)

            org.bukkit.Bukkit.getLogger().info("[SkillEffectEngine] Added skill $skillId with effect ${effect.type}, depth=$depth, strength=$strength")

            mutableByType
                .getOrPut(effect.type) { mutableListOf() }
                .add(SkillEffectEntry(skillId, effect, depth, strength))
        }

        // プレステージスキルを追加（思念アイテムを持っていれば有効）
        val player = Bukkit.getPlayer(playerUuid)
        val hasToken = player != null && PrestigeToken.hasTokenInHotbarOrOffhand(player, profession)
        if (hasToken) {
            for (skillId in prestigeSkills) {
                val skill = skillTree.getSkill(skillId) ?: continue
                val effect = skill.effect ?: continue

                val handler = SkillEffectRegistry.getHandler(effect.type) ?: continue
                if (!handler.isEnabled() || !handler.supportsProfession(profession.id)) continue

                val depth = SkillDepthCalculator.calculateDepth(skillId, skillTree)
                // プレステージスキルは通常スキルより強い効果を持つ（depth + 10で区別）
                val strength = handler.calculateStrength(effect) * 1.5

                mutableByType
                    .getOrPut(effect.type) { mutableListOf() }
                    .add(SkillEffectEntry(skillId, effect, depth + 10, strength))
            }
        }

        val sortedByType = mutableByType
            .mapValues { (_, entries) ->
                entries.sortedWith(
                    compareByDescending<SkillEffectEntry> { it.depth }
                        .thenByDescending { it.strength }
                        .thenBy { it.skillId }
                )
            }

        val targetedByType = mutableMapOf<String, TargetedEffectCache>()
        for ((effectType, entries) in sortedByType) {
            if (effectType !in blockTargetedEffectTypes) {
                continue
            }
            targetedByType[effectType] = buildTargetedCache(entries)
        }

        effectCache[playerUuid] = CompiledEffects(sortedByType, targetedByType.toMap(), playerUuid, profession)
    }

    private fun buildTargetedCache(entries: List<SkillEffectEntry>): TargetedEffectCache {
        val byBlockType = mutableMapOf<String, SkillEffectEntry>()
        var fallback: SkillEffectEntry? = null

        for (entry in entries) {
            val targetBlocks = entry.effect.getStringListParam("targetBlocks")
            if (targetBlocks.isEmpty()) {
                if (fallback == null) {
                    fallback = entry
                }
                continue
            }

            for (blockType in targetBlocks) {
                if (!byBlockType.containsKey(blockType)) {
                    byBlockType[blockType] = entry
                }
            }
        }

        return TargetedEffectCache(byBlockType.toMap(), fallback)
    }

    fun getCachedEffects(playerUuid: UUID): CompiledEffects? {
        return effectCache[playerUuid]
    }

    fun getCachedEffect(playerUuid: UUID, effectType: String): SkillEffectEntry? {
        return effectCache[playerUuid]?.byType?.get(effectType)?.firstOrNull()
    }

    fun getCachedEffectForBlock(playerUuid: UUID, effectType: String, blockType: String): SkillEffectEntry? {
        val compiled = effectCache[playerUuid] ?: return null
        val targeted = compiled.targetedByType[effectType]
        if (targeted != null) {
            return targeted.byBlockType[blockType] ?: targeted.fallback
        }
        return compiled.byType[effectType]?.firstOrNull()
    }

    fun clearCache(playerUuid: UUID) {
        effectCache.remove(playerUuid)
    }

    fun clearAllCache() {
        effectCache.clear()
    }

    fun hasCachedEffect(playerUuid: UUID, effectType: String): Boolean {
        return effectCache[playerUuid]?.byType?.get(effectType)?.isNotEmpty() == true
    }

    fun applyEffect(player: Player, profession: Profession, skillId: String, effect: SkillEffect, event: Any?): Boolean {
        val handler = SkillEffectRegistry.getHandler(effect.type) ?: return false

        if (!handler.isEnabled()) {
            return false
        }

        if (!handler.supportsProfession(profession.id)) {
            return false
        }

        if (!handler.validateParams(effect)) {
            return false
        }

        val context = EffectContext(
            player = player,
            playerUuid = player.uniqueId,
            profession = profession,
            skillId = skillId,
            skillEffect = effect,
            rawEvent = event as? org.bukkit.event.Event
        )

        return handler.applyEffect(context)
    }

    fun getCachedEffectValue(playerUuid: UUID, effectType: String, paramKey: String, defaultValue: Double = 0.0): Double {
        val entry = getCachedEffect(playerUuid, effectType) ?: return defaultValue
        return entry.effect.getDoubleParam(paramKey, defaultValue)
    }

    fun getCachedEffectValueAsInt(playerUuid: UUID, effectType: String, paramKey: String, defaultValue: Int = 0): Int {
        val entry = getCachedEffect(playerUuid, effectType) ?: return defaultValue
        return entry.effect.getIntParam(paramKey, defaultValue)
    }

    fun getCachedEffectValueAsString(playerUuid: UUID, effectType: String, paramKey: String, defaultValue: String = ""): String {
        val entry = getCachedEffect(playerUuid, effectType) ?: return defaultValue
        return entry.effect.getStringParam(paramKey, defaultValue)
    }

    fun getCachedEffectValueAsStringList(playerUuid: UUID, effectType: String, paramKey: String): List<String> {
        val entry = getCachedEffect(playerUuid, effectType) ?: return emptyList()
        return entry.effect.getStringListParam(paramKey)
    }

    fun getCachedEffectValueAsBoolean(playerUuid: UUID, effectType: String, paramKey: String, defaultValue: Boolean = false): Boolean {
        val entry = getCachedEffect(playerUuid, effectType) ?: return defaultValue
        return entry.effect.getBooleanParam(paramKey, defaultValue)
    }

    fun getCacheSize(): Int {
        return effectCache.size
    }

    fun getAllCachedEffects(): Map<UUID, CompiledEffects> {
        return effectCache.toMap()
    }

    /**
     * 複数スキルエフェクトを優先度順に合成する。
     * 
     * 基本ルール：
     * - 最も深いスキル（depth が大きい）から順に適用
     * - CombineRule に従って合成
     * 
     * @param entries 対象エフェクトエントリ（depth降順でソート済み想定）
     * @param blockType ブロックタイプ（対象フィルタリング用）
     * @return 合成済みの SkillEffect
     */
    fun combineEffects(entries: List<SkillEffectEntry>, blockType: String = ""): SkillEffect? {
        if (entries.isEmpty()) return null

        // 最初のエントリ（最も深い）を取得
        val baseEntry = entries.firstOrNull() ?: return null
        val baseEffect = baseEntry.effect
        val combineRule = baseEffect.combineRule

        // REPLACE の場合は最深スキルのみを使用
        if (combineRule == CombineRule.REPLACE) {
            return baseEffect
        }

        // 対象ブロック型の場合、フィルタリング
        val applicableEntries = if (blockType.isNotEmpty()) {
            entries.filter { entry ->
                val targetBlocks = entry.effect.getStringListParam("targetBlocks")
                targetBlocks.isEmpty() || blockType in targetBlocks
            }
        } else {
            entries
        }

        if (applicableEntries.isEmpty()) return null
        if (applicableEntries.size == 1) return applicableEntries[0].effect

        // 複数エフェクトを合成
        return when (combineRule) {
            CombineRule.ADD -> combineByAdd(applicableEntries, baseEffect)
            CombineRule.MULTIPLY -> combineByMultiply(applicableEntries, baseEffect)
            CombineRule.REPLACE -> applicableEntries[0].effect
            else -> applicableEntries[0].effect
        }
    }

    /**
     * 加算方式で複数エフェクトを合成
     */
    private fun combineByAdd(entries: List<SkillEffectEntry>, baseEffect: SkillEffect): SkillEffect {
        // 数値パラメータを抽出して合算
        val combinedParams = baseEffect.params.toMutableMap()

        // 数値型パラメータを特定して合算（最初の0以外の値で判定）
        val numericKeys = mutableSetOf<String>()
        for (entry in entries) {
            for ((key, value) in entry.effect.params) {
                if (value is Number && key !in numericKeys && key !in listOf("targetBlocks", "targetWeapons", "targetTools")) {
                    numericKeys.add(key)
                }
            }
        }

        var totalValue = 0.0
        for (key in numericKeys) {
            totalValue = 0.0
            for (entry in entries) {
                val value = entry.effect.getDoubleParam(key, 0.0)
                totalValue += value
            }
            combinedParams[key] = totalValue
        }

        return SkillEffect(
            type = baseEffect.type,
            params = combinedParams,
            evaluationMode = baseEffect.evaluationMode,
            combineRule = baseEffect.combineRule
        )
    }

    /**
     * 乗算方式で複数エフェクトを合成
     */
    private fun combineByMultiply(entries: List<SkillEffectEntry>, baseEffect: SkillEffect): SkillEffect {
        // 数値パラメータを抽出して乗算
        val combinedParams = baseEffect.params.toMutableMap()

        // 数値型パラメータを特定して乗算（最初の0以外の値で判定）
        val numericKeys = mutableSetOf<String>()
        for (entry in entries) {
            for ((key, value) in entry.effect.params) {
                if (value is Number && key !in numericKeys && key !in listOf("targetBlocks", "targetWeapons", "targetTools")) {
                    numericKeys.add(key)
                }
            }
        }

        for (key in numericKeys) {
            var totalValue = 1.0
            for (entry in entries) {
                val value = entry.effect.getDoubleParam(key, 1.0)
                totalValue *= value
            }
            combinedParams[key] = totalValue
        }

        return SkillEffect(
            type = baseEffect.type,
            params = combinedParams,
            evaluationMode = baseEffect.evaluationMode,
            combineRule = baseEffect.combineRule
        )
    }
}
