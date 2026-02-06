package jp.awabi2048.cccontent.features.rank

import java.util.UUID

/**
 * ランクデータのストレージインターフェース
 * 永続化されたランクデータの読み書きを行う
 */
interface RankStorage {
    
    /**
     * プレイヤーのランクデータを保存
     * @param rankData 保存するランクデータ
     */
    fun savePlayerRank(rankData: PlayerRankData)
    
    /**
     * プレイヤーのランクデータを読み込む
     * @param playerUuid プレイヤーのUUID
     * @return ランクデータ、存在しない場合はnull
     */
    fun loadPlayerRank(playerUuid: UUID): PlayerRankData?
    
    /**
     * プレイヤーのランクデータを削除
     * @param playerUuid プレイヤーのUUID
     */
    fun deletePlayerRank(playerUuid: UUID)
    
    /**
     * 指定されたRankTypeの全プレイヤーランキングを取得
     * @param rankType ランクのタイプ
     * @return すべてのPlayerRankDataのリスト
     */
    fun getAllPlayerRanks(rankType: RankType): List<PlayerRankData>
    
    /**
     * ストレージの初期化
     */
    fun init()
    
    /**
     * ストレージのクリーンアップ
     */
    fun cleanup()
}
