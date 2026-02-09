package jp.awabi2048.cccontent.features.rank.profession

/**
 * スキルツリーの各ノードを表現
 */
data class SkillNode(
    /** スキルID（例：skill0, skill1など） */
    val skillId: String,
    
    /** スキル名の翻訳キー */
    val nameKey: String,
    
    /** スキル説明文の翻訳キー */
    val descriptionKey: String,
    
    /** このスキルを習得するのに必要な経験値 */
    val requiredExp: Long,

    /** このスキルのアイコン素材（Material名） */
    val icon: String? = null,
    
    /**
     * このスキルの前提条件となるスキルIDのリスト
     * 親子関係はこの prerequisites のみから導出する
     */
    val prerequisites: List<String> = emptyList()
) {
    /**
     * このスキルが取得可能かチェック
     * @param acquiredSkills プレイヤーが習得済みのスキルID集合
     * @param currentExp プレイヤーの現在の経験値
     * @return 取得可能な場合true
     */
    fun canAcquire(acquiredSkills: Set<String>, currentExp: Long): Boolean {
        // 経験値が足りているかチェック
        if (currentExp < requiredExp) return false
        
        // すべての前提条件を満たしているかチェック
        return prerequisites.all { it in acquiredSkills }
    }
}
