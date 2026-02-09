package jp.awabi2048.cccontent.features.rank.skill

import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.SkillTree
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry
import org.bukkit.entity.Player
import java.util.UUID

data class CompiledEffects(
    val byType: Map<String, SkillEffectEntry>,
    val playerUuid: UUID,
    val profession: Profession,
    val timestamp: Long = System.currentTimeMillis()
)

data class SkillEffectEntry(
    val skillId: String,
    val effect: SkillEffect,
    val depth: Int,
    val strength: Double
)

object SkillEffectEngine {
    private val effectCache: MutableMap<UUID, CompiledEffects> = mutableMapOf()

    fun rebuildCache(playerUuid: UUID, acquiredSkills: Set<String>, profession: Profession) {
        val skillTree = SkillTreeRegistry.getSkillTree(profession) ?: return
        val byType = mutableMapOf<String, SkillEffectEntry>()

        for (skillId in acquiredSkills) {
            val skill = skillTree.getSkill(skillId) ?: continue
            val effect = skill.effect ?: continue

            val handler = SkillEffectRegistry.getHandler(effect.type) ?: continue
            if (!handler.isEnabled() || !handler.supportsProfession(profession.id)) continue

            val depth = SkillDepthCalculator.calculateDepth(skillId, skillTree)
            val strength = handler.calculateStrength(effect)

            val existing = byType[effect.type]
            if (existing == null || depth > existing.depth) {
                byType[effect.type] = SkillEffectEntry(skillId, effect, depth, strength)
            }
        }

        effectCache[playerUuid] = CompiledEffects(byType.toMap(), playerUuid, profession)
    }

    fun getCachedEffects(playerUuid: UUID): CompiledEffects? {
        return effectCache[playerUuid]
    }

    fun getCachedEffect(playerUuid: UUID, effectType: String): SkillEffectEntry? {
        return effectCache[playerUuid]?.byType?.get(effectType)
    }

    fun clearCache(playerUuid: UUID) {
        effectCache.remove(playerUuid)
    }

    fun clearAllCache() {
        effectCache.clear()
    }

    fun hasCachedEffect(playerUuid: UUID, effectType: String): Boolean {
        return effectCache[playerUuid]?.byType?.containsKey(effectType) == true
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
