package jp.awabi2048.cccontent.features.rank

import java.util.UUID

/**
 * プレイヤーのランク情報
 * 特定のRankTypeに関連した個別のランク情報を保持
 */
data class PlayerRank(
    /** プレイヤーのUUID */
    val playerUuid: UUID,
    
    /** ランクのタイプ（ARENA、SUKIMA_DUNGEON、CUSTOM_ITEM） */
    val rankType: RankType,
    
    /** 現在のスコア */
    var score: Long = 0L,
    
    /** 現在のティア */
    var tier: RankTier = RankTier.BRONZE_I,
    
    /** 前回の更新日時（UnixTimeMillis） */
    var lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * スコアを追加
     * @param amount 追加するスコア量
     * @return ティアが変更されたかどうか
     */
    fun addScore(amount: Long): Boolean {
        val oldTier = tier
        score += amount
        tier = RankTier.getTierByScore(score)
        lastUpdated = System.currentTimeMillis()
        return oldTier != tier
    }
    
    /**
     * スコアをリセット
     */
    fun resetScore() {
        score = 0L
        tier = RankTier.BRONZE_I
        lastUpdated = System.currentTimeMillis()
    }
    
    /**
     * 現在のティアの進捗度（0.0 ~ 1.0）を取得
     * @return 進捗度
     */
    fun getTierProgress(): Double {
        if (tier == RankTier.MASTER) return 1.0
        
        val currentTierThreshold = tier.nextThreshold
        val nextTier = RankTier.values().getOrNull(tier.ordinal + 1)
        val nextTierThreshold = nextTier?.nextThreshold ?: Long.MAX_VALUE
        
        val currentProgress = score - currentTierThreshold
        val totalProgress = nextTierThreshold - currentTierThreshold
        
        return if (totalProgress <= 0) 0.0 else (currentProgress.toDouble() / totalProgress.toDouble()).coerceIn(0.0, 1.0)
    }
    
    /**
     * 次のティアまでに必要なスコア
     * @return 必要なスコア量
     */
    fun getScoreToNextTier(): Long {
        if (tier == RankTier.MASTER) return 0L
        
        val nextTier = RankTier.values().getOrNull(tier.ordinal + 1)
        val nextTierThreshold = nextTier?.nextThreshold ?: Long.MAX_VALUE
        
        return (nextTierThreshold - score).coerceAtLeast(0)
    }
}
