package jp.awabi2048.cccontent.features.rank.profession

import jp.awabi2048.cccontent.features.rank.skill.SkillSwitchMode
import java.util.UUID

/**
 * プレイヤーの職業情報と進行状況
 */
data class PlayerProfession(
    val playerUuid: UUID,

    var profession: Profession,

    var acquiredSkills: MutableSet<String> = mutableSetOf(),

    var currentExp: Long = 0L,

    var lastUpdated: Long = System.currentTimeMillis(),

    var bossBarEnabled: Boolean = true,

    var prestigeSkills: MutableSet<String> = mutableSetOf(),

    /**
     * 現在アクティブな能動スキルID
     */
    var activeSkillId: String? = null,

    /**
     * 能動スキルの切替様式
     */
    var skillSwitchMode: SkillSwitchMode = SkillSwitchMode.MENU_ONLY,

    /**
     * スキルごとの発動ON/OFF状態（skillId -> enabled）
     * 未設定のスキルはON扱い
     */
    var skillActivationStates: MutableMap<String, Boolean> = mutableMapOf()
) {
    fun getCurrentLevel(skillTree: SkillTree): Int {
        return skillTree.calculateLevelByExp(currentExp)
    }

    fun getPrestigeLevel(skillTree: SkillTree): Int {
        return skillTree.calculatePrestigeLevelByExp(currentExp)
    }

    fun isMaxLevel(skillTree: SkillTree): Boolean {
        return getCurrentLevel(skillTree) >= skillTree.getMaxLevel()
    }

    fun canPrestige(skillTree: SkillTree): Boolean {
        return isMaxLevel(skillTree) && getPrestigeLevel(skillTree) >= 1
    }

    fun addExperience(amount: Long) {
        currentExp += amount
        lastUpdated = System.currentTimeMillis()
    }

    fun acquireSkill(skillId: String): Boolean {
        acquiredSkills.add(skillId)
        lastUpdated = System.currentTimeMillis()
        return true
    }

    fun acquirePrestigeSkill(skillId: String): Boolean {
        prestigeSkills.add(skillId)
        lastUpdated = System.currentTimeMillis()
        return true
    }

    fun hasSkillUnlocked(skillId: String): Boolean {
        return acquiredSkills.contains(skillId)
    }

    fun hasPrestigeSkillUnlocked(skillId: String): Boolean {
        return prestigeSkills.contains(skillId)
    }

    fun canUnlockPrestigeSkill(skillTree: SkillTree, skillId: String): Boolean {
        val skill = skillTree.getSkill(skillId) ?: return false
        val prestigeLevel = getPrestigeLevel(skillTree)
        return hasSkillUnlocked(skillId) &&
               prestigeLevel >= skill.requiredLevel &&
               !hasPrestigeSkillUnlocked(skillId)
    }

    fun getNextSkillOptions(skillTree: SkillTree): List<String> {
        return skillTree.getAvailableSkills(acquiredSkills, getCurrentLevel(skillTree))
    }

    fun getNextPrestigeSkillOptions(skillTree: SkillTree): List<String> {
        if (!isMaxLevel(skillTree)) return emptyList()
        val prestigeLevel = getPrestigeLevel(skillTree)
        return skillTree.getAllSkills().values
            .filter { skill ->
                hasSkillUnlocked(skill.skillId) &&
                prestigeLevel >= skill.requiredLevel &&
                !hasPrestigeSkillUnlocked(skill.skillId)
            }
            .map { it.skillId }
    }

    fun getCurrentSkillLevel(): Int {
        return acquiredSkills.size
    }

    fun resetForPrestige() {
        acquiredSkills.clear()
        currentExp = 0L
        lastUpdated = System.currentTimeMillis()
    }

    /**
     * スキルの発動状態を取得（未設定はON扱い）
     */
    fun isSkillActivationEnabled(skillId: String): Boolean {
        return skillActivationStates[skillId] ?: true
    }

    /**
     * スキルの発動状態を設定
     */
    fun setSkillActivationEnabled(skillId: String, enabled: Boolean) {
        skillActivationStates[skillId] = enabled
        lastUpdated = System.currentTimeMillis()
    }

    /**
     * スキルの発動状態をトグル（反転）して新しい状態を返す
     */
    fun toggleSkillActivation(skillId: String): Boolean {
        val currentState = isSkillActivationEnabled(skillId)
        val newState = !currentState
        setSkillActivationEnabled(skillId, newState)
        return newState
    }
}
