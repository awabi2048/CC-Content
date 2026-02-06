package jp.awabi2048.cccontent.features.rank.tutorial

import java.util.UUID

/**
 * チュートリアルランク管理インターフェース
 */
interface TutorialRankManager {
    
    /**
     * プレイヤーのチュートリアルランク情報を取得
     * @param playerUuid プレイヤーのUUID
     * @return チュートリアルランク情報
     */
    fun getPlayerTutorial(playerUuid: UUID): PlayerTutorialRank
    
    /**
     * プレイヤーの現在のランクを取得
     * @param playerUuid プレイヤーのUUID
     * @return 現在のランク
     */
    fun getRank(playerUuid: UUID): TutorialRank
    
    /**
     * プレイヤーが経験値を獲得（ランクアップ判定を含む）
     * @param playerUuid プレイヤーのUUID
     * @param amount 追加する経験値
     * @return ランクアップした場合true
     */
    fun addExperience(playerUuid: UUID, amount: Long): Boolean
    
    /**
     * プレイヤーがATTAINERランクに達しているか確認
     * @param playerUuid プレイヤーのUUID
     * @return ATTAINER以上の場合true
     */
    fun isAttainer(playerUuid: UUID): Boolean
    
    /**
     * チュートリアルランクを直接設定（デバッグ用）
     * @param playerUuid プレイヤーのUUID
     * @param rank 設定するランク
     */
    fun setRank(playerUuid: UUID, rank: TutorialRank)
}
