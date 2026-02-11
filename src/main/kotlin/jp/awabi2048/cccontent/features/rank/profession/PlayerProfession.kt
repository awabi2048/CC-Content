package jp.awabi2048.cccontent.features.rank.profession

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
    
    var bossBarEnabled: Boolean = true
) {
    fun getCurrentLevel(skillTree: SkillTree): Int {
        return skillTree.calculateLevelByExp(currentExp)
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
    
    fun getNextSkillOptions(skillTree: SkillTree): List<String> {
        return skillTree.getAvailableSkills(acquiredSkills, getCurrentLevel(skillTree))
    }
    
    fun getCurrentSkillLevel(): Int {
        return acquiredSkills.size
    }
}
