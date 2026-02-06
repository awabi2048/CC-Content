package jp.awabi2048.cccontent.features.rank

import java.util.UUID

/**
 * プレイヤーの全ランク情報を管理
 * 1プレイヤーが持つ複数のRankTypeのランク情報を統合管理
 */
data class PlayerRankData(
    /** プレイヤーのUUID */
    val playerUuid: UUID,
    
    /** 各ランクタイプごとのランク情報 */
    val ranks: MutableMap<RankType, PlayerRank> = mutableMapOf()
) {
    init {
        // 初期化時に全RankTypeのランク情報を作成
        RankType.values().forEach { rankType ->
            ranks[rankType] = PlayerRank(playerUuid, rankType)
        }
    }
    
    /**
     * 指定されたRankTypeのランク情報を取得
     * @param rankType ランクのタイプ
     * @return ランク情報
     */
    fun getRank(rankType: RankType): PlayerRank {
        return ranks[rankType] ?: PlayerRank(playerUuid, rankType).also { ranks[rankType] = it }
    }
    
    /**
     * 全ランクの総スコアを取得
     * @return 総スコア
     */
    fun getTotalScore(): Long {
        return ranks.values.sumOf { it.score }
    }
    
    /**
     * 最高のティアを取得
     * @return 最高のティア
     */
    fun getHighestTier(): RankTier {
        return ranks.values.maxByOrNull { it.tier.level }?.tier ?: RankTier.BRONZE_I
    }
}
