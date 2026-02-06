package jp.awabi2048.cccontent.features.rank.profession

import java.util.UUID

/**
 * 職業と職業進行を管理するインターフェース
 */
interface ProfessionManager {
    
    /**
     * プレイヤーの職業情報を取得
     * @param playerUuid プレイヤーのUUID
     * @return 職業情報、未選択の場合null
     */
    fun getPlayerProfession(playerUuid: UUID): PlayerProfession?
    
    /**
     * プレイヤーが職業を選択したか確認
     */
    fun hasProfession(playerUuid: UUID): Boolean
    
    /**
     * プレイヤーに職業を選択させる
     * @param playerUuid プレイヤーのUUID
     * @param profession 選択する職業
     * @return 成功した場合true
     */
    fun selectProfession(playerUuid: UUID, profession: Profession): Boolean
    
    /**
     * プレイヤーの職業を変更
     * @param playerUuid プレイヤーのUUID
     * @param profession 変更先の職業
     * @return 成功した場合true
     */
    fun changeProfession(playerUuid: UUID, profession: Profession): Boolean
    
    /**
     * プレイヤーに経験値を追加
     * @param playerUuid プレイヤーのUUID
     * @param amount 追加する経験値
     * @return 成功した場合true（職業未選択の場合false）
     */
    fun addExperience(playerUuid: UUID, amount: Long): Boolean
    
    /**
     * プレイヤーがスキルを習得
     * @param playerUuid プレイヤーのUUID
     * @param skillId 習得するスキルID
     * @return 成功した場合true
     */
    fun acquireSkill(playerUuid: UUID, skillId: String): Boolean
    
    /**
     * プレイヤーが習得可能なスキルの一覧を取得
     * @param playerUuid プレイヤーのUUID
     * @return 習得可能なスキルIDのリスト
     */
    fun getAvailableSkills(playerUuid: UUID): List<String>
    
    /**
     * プレイヤーが習得済みのスキルを取得
     * @param playerUuid プレイヤーのUUID
     * @return 習得済みスキルID集合
     */
    fun getAcquiredSkills(playerUuid: UUID): Set<String>
    
    /**
     * プレイヤーの現在の経験値を取得
     * @param playerUuid プレイヤーのUUID
     * @return 現在の経験値
     */
    fun getCurrentExp(playerUuid: UUID): Long
    
    /**
     * 職業をリセット（デバッグ用）
     * @param playerUuid プレイヤーのUUID
     * @return 成功した場合true
     */
    fun resetProfession(playerUuid: UUID): Boolean
}
