package jp.awabi2048.cccontent.features.rank

import jp.awabi2048.cccontent.features.rank.tutorial.PlayerTutorialRank
import jp.awabi2048.cccontent.features.rank.profession.PlayerProfession
import java.util.UUID

/**
 * ランクデータのストレージインターフェース
 * 永続化されたランクデータの読み書きを行う
 */
interface RankStorage {
    
    /**
     * プレイヤーのチュートリアルランクデータを保存
     * @param tutorialRank 保存するチュートリアルランク情報
     */
    fun saveTutorialRank(tutorialRank: PlayerTutorialRank)
    
    /**
     * プレイヤーのチュートリアルランクデータを読み込む
     * @param playerUuid プレイヤーのUUID
     * @return チュートリアルランク情報、存在しない場合はnull
     */
    fun loadTutorialRank(playerUuid: UUID): PlayerTutorialRank?
    
    /**
     * プレイヤーの職業データを保存
     * @param profession 保存する職業情報
     */
    fun saveProfession(profession: PlayerProfession)
    
    /**
     * プレイヤーの職業データを読み込む
     * @param playerUuid プレイヤーのUUID
     * @return 職業情報、存在しない場合はnull
     */
    fun loadProfession(playerUuid: UUID): PlayerProfession?
    
    /**
     * プレイヤーの職業データを削除
     * @param playerUuid プレイヤーのUUID
     */
    fun deleteProfession(playerUuid: UUID)
    
    /**
     * ストレージの初期化
     */
    fun init()
    
    /**
     * ストレージのクリーンアップ
     */
    fun cleanup()
}
