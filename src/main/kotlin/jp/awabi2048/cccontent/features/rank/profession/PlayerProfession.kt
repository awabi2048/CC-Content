package jp.awabi2048.cccontent.features.rank.profession

import java.util.UUID

/**
 * プレイヤーの職業情報と進行状況
 */
data class PlayerProfession(
    /** プレイヤーのUUID */
    val playerUuid: UUID,
    
    /** プレイヤーの職業 */
    var profession: Profession,
    
    /** プレイヤーが習得済みのスキルID集合 */
    var acquiredSkills: MutableSet<String> = mutableSetOf(),

    /** 現在の職業レベル */
    var currentLevel: Int = 1,
    
    /** 職業経験値（累積） */
    var currentExp: Long = 0L,
    
    /** 最後の更新日時 */
    var lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * 経験値を追加
     * @param amount 追加する経験値
     */
    fun addExperience(amount: Long) {
        currentExp += amount
        lastUpdated = System.currentTimeMillis()
    }
    
    /**
     * スキルを習得
     * @param skillId 習得するスキルID
     * @return 成功した場合true
     */
    fun acquireSkill(skillId: String): Boolean {
        acquiredSkills.add(skillId)
        lastUpdated = System.currentTimeMillis()
        return true
    }
    
    /**
     * 次のスキル選択肢を取得（複数の選択肢がある場合）
     * @param skillTree スキルツリー
     * @return 次に選択可能なスキルIDのリスト
     */
    fun getNextSkillOptions(skillTree: SkillTree): List<String> {
        return skillTree.getAvailableSkills(acquiredSkills, currentLevel)
    }
    
    /**
     * 現在のスキルレベルを取得（習得済みスキル数）
     */
    fun getCurrentSkillLevel(): Int {
        return acquiredSkills.size
    }
}
