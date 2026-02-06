package jp.awabi2048.cccontent.features.rank.profession

/**
 * 職業のスキルツリーを定義するインターフェース
 */
interface SkillTree {
    
    /**
     * 職業のIDを取得
     */
    fun getProfessionId(): String
    
    /**
     * 指定されたスキルノードを取得
     */
    fun getSkill(skillId: String): SkillNode?
    
    /**
     * すべてのスキルを取得
     */
    fun getAllSkills(): Map<String, SkillNode>
    
    /**
     * 開始スキル（ツリーの最初）のIDを取得
     */
    fun getStartSkillId(): String
    
    /**
     * プレイヤーが取得可能なスキルの一覧を取得
     * @param acquiredSkills プレイヤーが習得済みのスキルID集合
     * @param currentExp プレイヤーの現在の経験値
     * @return 取得可能なスキルIDのリスト
     */
    fun getAvailableSkills(acquiredSkills: Set<String>, currentExp: Long): List<String>
    
    /**
     * 指定されたスキルが取得可能かチェック
     */
    fun canAcquire(skillId: String, acquiredSkills: Set<String>, currentExp: Long): Boolean {
        val skill = getSkill(skillId) ?: return false
        return skill.canAcquire(acquiredSkills, currentExp)
    }
    
    /**
     * 指定されたスキル取得に必要な経験値を取得
     */
    fun getRequiredExp(skillId: String): Long? {
        return getSkill(skillId)?.requiredExp
    }
}
