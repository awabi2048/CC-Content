package jp.awabi2048.cccontent.features.rank.profession

/**
 * 職業のスキルツリーを管理するレジストリ
 */
object SkillTreeRegistry {
    private val skillTrees: MutableMap<Profession, SkillTree> = mutableMapOf()
    
    /**
     * スキルツリーを登録
     */
    fun register(profession: Profession, skillTree: SkillTree) {
        skillTrees[profession] = skillTree
    }
    
    /**
     * 職業のスキルツリーを取得
     */
    fun getSkillTree(profession: Profession): SkillTree? {
        return skillTrees[profession]
    }
    
    /**
     * すべてのスキルツリーを取得
     */
    fun getAllSkillTrees(): Map<Profession, SkillTree> {
        return skillTrees.toMap()
    }
    
    /**
     * 登録済みのスキルツリー数を取得
     */
    fun getSkillTreeCount(): Int {
        return skillTrees.size
    }
}
