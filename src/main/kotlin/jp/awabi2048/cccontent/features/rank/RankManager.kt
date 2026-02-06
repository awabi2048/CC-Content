package jp.awabi2048.cccontent.features.rank

import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import jp.awabi2048.cccontent.features.rank.tutorial.PlayerTutorialRank
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.PlayerProfession
import java.util.UUID

/**
 * ランクシステムの統合管理インターフェース
 * チュートリアルランクと職業管理を一元的に管理
 */
interface RankManager {
    
    /**
     * プレイヤーのチュートリアルランク情報を取得
     */
    fun getPlayerTutorial(playerUuid: UUID): PlayerTutorialRank
    
    /**
     * プレイヤーのチュートリアルランクを取得
     */
    fun getTutorialRank(playerUuid: UUID): TutorialRank
    
    /**
     * プレイヤーがATTAINERに達しているか確認
     */
    fun isAttainer(playerUuid: UUID): Boolean
    
    /**
     * チュートリアルランクに経験値を追加
     */
    fun addTutorialExp(playerUuid: UUID, amount: Long): Boolean
    
    /**
     * チュートリアルランクを直接設定（デバッグ用）
     */
    fun setTutorialRank(playerUuid: UUID, rank: TutorialRank)
    
    /**
     * プレイヤーの職業情報を取得
     */
    fun getPlayerProfession(playerUuid: UUID): PlayerProfession?
    
    /**
     * プレイヤーが職業を選択しているか確認
     */
    fun hasProfession(playerUuid: UUID): Boolean
    
    /**
     * プレイヤーに職業を選択させる
     */
    fun selectProfession(playerUuid: UUID, profession: Profession): Boolean
    
    /**
     * プレイヤーの職業を変更
     */
    fun changeProfession(playerUuid: UUID, profession: Profession): Boolean
    
    /**
     * 職業経験値を追加
     */
    fun addProfessionExp(playerUuid: UUID, amount: Long): Boolean
    
    /**
     * スキルを習得
     */
    fun acquireSkill(playerUuid: UUID, skillId: String): Boolean
    
    /**
     * 習得可能なスキル一覧を取得
     */
    fun getAvailableSkills(playerUuid: UUID): List<String>
    
    /**
     * 習得済みスキルを取得
     */
    fun getAcquiredSkills(playerUuid: UUID): Set<String>
    
    /**
     * 現在の職業経験値を取得
     */
    fun getCurrentProfessionExp(playerUuid: UUID): Long
    
    /**
     * 職業をリセット（デバッグ用）
     */
    fun resetProfession(playerUuid: UUID): Boolean
    
    /**
     * データを保存
     */
    fun saveData()
    
    /**
     * データを読み込む
     */
    fun loadData()
}
