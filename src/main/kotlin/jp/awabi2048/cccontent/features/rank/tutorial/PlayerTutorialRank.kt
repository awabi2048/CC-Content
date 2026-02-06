package jp.awabi2048.cccontent.features.rank.tutorial

import java.util.UUID

/**
 * プレイヤーのチュートリアルランク情報
 */
data class PlayerTutorialRank(
    /** プレイヤーのUUID */
    val playerUuid: UUID,
    
    /** 現在のランク */
    var currentRank: TutorialRank = TutorialRank.VISITOR,
    
    /** 現在の経験値（次のランクまで） */
    var currentExp: Long = 0L,
    
    /** 最後の更新日時 */
    var lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * 経験値を追加してランクアップ判定を実行
     * @param amount 追加する経験値
     * @return ランクアップした場合true
     */
    fun addExperience(amount: Long): Boolean {
        if (currentRank == TutorialRank.ATTAINER) {
            return false  // 最終ランクではランクアップしない
        }
        
        currentExp += amount
        lastUpdated = System.currentTimeMillis()
        
        // ランクアップ判定
        if (currentExp >= currentRank.requiredExp) {
            val nextRank = TutorialRank.values().getOrNull(currentRank.ordinal + 1)
            if (nextRank != null) {
                currentExp = 0L  // 経験値をリセット
                currentRank = nextRank
                return true
            }
        }
        return false
    }
    
    /**
     * 次のランクに必要な経験値を取得
     * @return 必要な経験値量
     */
    fun getRequiredExpForNextRank(): Long {
        return currentRank.requiredExp
    }
    
    /**
     * 次のランクまでの進捗度を取得（0.0～1.0）
     * @return 進捗度
     */
    fun getProgress(): Double {
        if (currentRank == TutorialRank.ATTAINER) {
            return 1.0
        }
        val required = currentRank.requiredExp
        return if (required <= 0) 1.0 else (currentExp.toDouble() / required.toDouble()).coerceIn(0.0, 1.0)
    }
}
