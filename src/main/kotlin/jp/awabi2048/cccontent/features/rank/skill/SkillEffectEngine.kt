package jp.awabi2048.cccontent.features.rank.skill

import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.SkillTree
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry
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

    fun rebuildCache(playerUuid: UUID, acquiredSkills: Set<String>, profession: Profession) {
        val skillTree = SkillTreeRegistry.getSkillTree(profession) ?: return
        val mutableByType = mutableMapOf<String, MutableList<SkillEffectEntry>>()

        for (skillId in acquiredSkills) {
            val skill = skillTree.getSkill(skillId) ?: continue
            val effect = skill.effect ?: continue

            val handler = SkillEffectRegistry.getHandler(effect.type) ?: continue
            if (!handler.isEnabled() || !handler.supportsProfession(profession.id)) continue

            val depth = SkillDepthCalculator.calculateDepth(skillId, skillTree)
            val strength = handler.calculateStrength(effect)

            mutableByType
                .getOrPut(effect.type) { mutableListOf() }
                .add(SkillEffectEntry(skillId, effect, depth, strength))
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
}
