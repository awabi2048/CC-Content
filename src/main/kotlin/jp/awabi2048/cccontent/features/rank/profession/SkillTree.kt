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
     * @param currentLevel プレイヤーの現在の職業レベル
     * @return 取得可能なスキルIDのリスト
     */
    fun getAvailableSkills(acquiredSkills: Set<String>, currentLevel: Int): List<String>

    /**
     * レベル1→2に必要な初期経験値
     */
    fun getLevelInitialExp(): Long

    /**
     * レベルアップ必要経験値の指数係数
     */
    fun getLevelBase(): Double

    /**
     * 職業レベルの上限
     */
    fun getMaxLevel(): Int

    /**
     * 職業概要アイコンを取得（設定がなければnull）
     */
    fun getOverviewIcon(): String? = null

    /**
     * 指定レベルでのレベルアップに必要な経験値を取得
     */
    fun getRequiredExpForLevel(level: Int): Long {
        return getExpToNextLevel(level)
    }

    /**
     * 指定スキルから派生する子スキル一覧を取得
     */
    fun getChildren(skillId: String): List<String> {
        return getSkill(skillId)?.children ?: emptyList()
    }

    /**
     * 指定スキルの親スキル一覧を取得
     */
    fun getParents(skillId: String): List<String> {
        return getSkill(skillId)?.prerequisites ?: emptyList()
    }
    
    /**
     * 指定されたスキルが取得可能かチェック
     */
    fun canAcquire(skillId: String, acquiredSkills: Set<String>, currentLevel: Int): Boolean {
        val skill = getSkill(skillId) ?: return false
        if (skill.skillId in acquiredSkills) {
            return false
        }
        if (!skill.canAcquire(currentLevel)) {
            return false
        }

        val parents = getParents(skillId)
        if (parents.isNotEmpty() && parents.none { it in acquiredSkills }) {
            return false
        }

        parents.filter { it in acquiredSkills }.forEach { parentId ->
            val parentSkill = getSkill(parentId) ?: return@forEach
            if (!parentSkill.exclusiveBranch) return@forEach
            val siblings = getChildren(parentId)
            if (siblings.size >= 2) {
                val chosen = siblings.firstOrNull { it in acquiredSkills }
                if (chosen != null && chosen != skillId) {
                    return false
                }
            }
        }

        return true
    }
    
    /**
     * 指定されたスキル取得に必要な職業レベルを取得
     */
    fun getRequiredLevel(skillId: String): Int? {
        return getSkill(skillId)?.requiredLevel
    }

    /**
     * 指定レベルに到達する累積経験値を取得
     */
    fun getRequiredTotalExpForLevel(level: Int): Long

    /**
     * 累積経験値から現在レベルを算出
     */
    fun calculateLevelByExp(totalExp: Long): Int

    /**
     * 指定レベルから次レベルに必要な経験値を算出
     */
    fun getExpToNextLevel(level: Int): Long {
        val safeLevel = level.coerceAtLeast(1)
        val raw = getLevelInitialExp() * Math.pow(getLevelBase(), (safeLevel - 1).toDouble())
        return Math.round(raw).coerceAtLeast(1L)
    }
}
