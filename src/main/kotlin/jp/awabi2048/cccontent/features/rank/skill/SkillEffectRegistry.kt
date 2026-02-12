package jp.awabi2048.cccontent.features.rank.skill

import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry

object SkillEffectRegistry {
    private val handlers: MutableMap<String, SkillEffectHandler> = mutableMapOf()

    fun register(handler: SkillEffectHandler) {
        handlers[handler.getEffectType()] = handler
    }

    fun unregister(effectType: String) {
        handlers.remove(effectType)
    }

    fun getHandler(effectType: String): SkillEffectHandler? {
        return handlers[effectType]
    }

    fun getAllHandlers(): Map<String, SkillEffectHandler> {
        return handlers.toMap()
    }

    fun getHandlerCount(): Int {
        return handlers.size
    }

    fun getAllTypes(): Set<String> {
        return handlers.keys
    }

    fun clear() {
        handlers.clear()
    }

    fun getActiveHandlersForProfession(profession: Profession): List<SkillEffectHandler> {
        return handlers.values.filter { it.supportsProfession(profession.id) && it.isEnabled() }
    }

    fun validateEffectType(effectType: String): Boolean {
        return handlers.containsKey(effectType)
    }
}
