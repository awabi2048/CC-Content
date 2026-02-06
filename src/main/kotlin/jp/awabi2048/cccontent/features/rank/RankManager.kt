package jp.awabi2048.cccontent.features.rank

import java.util.UUID

/**
 * ランクシステムの管理インターフェース
 * プレイヤーのランク情報の保存・読み込み・更新を行う
 */
interface RankManager {
    
    /**
     * プレイヤーのランクデータを取得
     * 存在しない場合は新規作成
     * @param playerUuid プレイヤーのUUID
     * @return プレイヤーのランクデータ
     */
    fun getPlayerRankData(playerUuid: UUID): PlayerRankData
    
    /**
     * プレイヤーの特定のランクを取得
     * @param playerUuid プレイヤーのUUID
     * @param rankType ランクのタイプ
     * @return プレイヤーのランク情報
     */
    fun getPlayerRank(playerUuid: UUID, rankType: RankType): PlayerRank
    
    /**
     * プレイヤーのランクにスコアを追加
     * @param playerUuid プレイヤーのUUID
     * @param rankType ランクのタイプ
     * @param score 追加するスコア
     * @return ティアが変更されたかどうか
     */
    fun addScore(playerUuid: UUID, rankType: RankType, score: Long): Boolean
    
    /**
     * プレイヤーのランクをリセット
     * @param playerUuid プレイヤーのUUID
     * @param rankType ランクのタイプ
     */
    fun resetRank(playerUuid: UUID, rankType: RankType)
    
    /**
     * プレイヤーの全ランクデータを削除
     * @param playerUuid プレイヤーのUUID
     */
    fun deletePlayerData(playerUuid: UUID)
    
    /**
     * 変更をストレージに保存
     */
    fun saveData()
    
    /**
     * ストレージからデータを読み込む
     */
    fun loadData()
    
    /**
     * 指定されたRankTypeの全プレイヤーランキングを取得
     * @param rankType ランクのタイプ
     * @param limit ランキング上位数
     * @return スコアで降順ソートされた(UUID, スコア, ティア)のリスト
     */
    fun getRanking(rankType: RankType, limit: Int = 10): List<Triple<UUID, Long, RankTier>>
}
