package jp.awabi2048.cccontent.features.rank.profession

import jp.awabi2048.cccontent.features.rank.skill.SkillEffect

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

    /** このスキルを習得するのに必要な職業レベル */
    val requiredLevel: Int,

    /** このスキルのアイコン素材（Material名） */
    val icon: String? = null,

    /** このスキルから分岐する子スキルIDのリスト */
    val children: List<String> = emptyList(),

    /** このスキルの親スキルIDのリスト（children設定から導出） */
    val prerequisites: List<String> = emptyList(),

    /** このスキルの効果定義 */
    val effect: SkillEffect? = null,

    /** 分岐が排他的かどうか（true=片方のみ選択可能、false=両方選択可能） */
    val exclusiveBranch: Boolean = true,

    /** 発動切替が可能かどうか（true=Shift右クリックでON/OFF切替可能、false=常時ON） */
    val activationToggleable: Boolean = true
) {
    /**
     * このスキルが取得可能かチェック
     * @param acquiredSkills プレイヤーが習得済みのスキルID集合
     * @param currentLevel プレイヤーの現在の職業レベル
     * @return 取得可能な場合true
     */
    fun canAcquire(currentLevel: Int): Boolean {
        return currentLevel >= requiredLevel
    }
}
