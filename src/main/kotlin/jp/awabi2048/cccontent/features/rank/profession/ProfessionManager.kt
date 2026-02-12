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
     * プレイヤーの現在職業レベルを取得
     */
    fun getCurrentLevel(playerUuid: UUID): Int

    /**
     * デバッグ用: 職業レベルを直接設定
     */
    fun setLevel(playerUuid: UUID, level: Int): Boolean
    
    /**
     * 職業をリセット（デバッグ用）
     * @param playerUuid プレイヤーのUUID
     * @return 成功した場合true
     */
    fun resetProfession(playerUuid: UUID): Boolean

    fun isBossBarEnabled(playerUuid: UUID): Boolean

    fun setBossBarEnabled(playerUuid: UUID, enabled: Boolean)

    /**
     * プレイヤーの現在のプレステージレベルを取得
     * @param playerUuid プレイヤーのUUID
     * @return プレステージレベル（0=未プレステージ）
     */
    fun getPrestigeLevel(playerUuid: UUID): Int

    /**
     * プレイヤーがプレステージ可能かチェック
     * @param playerUuid プレイヤーのUUID
     * @return プレステージ可能な場合true
     */
    fun canPrestige(playerUuid: UUID): Boolean

    /**
     * プレイヤーがプレステージスキルを習得
     * @param playerUuid プレイヤーのUUID
     * @param skillId 習得するスキルID
     * @return 成功した場合true
     */
    fun acquirePrestigeSkill(playerUuid: UUID, skillId: String): Boolean

    /**
     * プレイヤーが習得可能なプレステージスキルの一覧を取得
     * @param playerUuid プレイヤーのUUID
     * @return 習得可能なプレステージスキルIDのリスト
     */
    fun getAvailablePrestigeSkills(playerUuid: UUID): List<String>

    /**
     * プレイヤーが習得済みのプレステージスキルを取得
     * @param playerUuid プレイヤーのUUID
     * @return 習得済みプレステージスキルID集合
     */
    fun getAcquiredPrestigeSkills(playerUuid: UUID): Set<String>

    /**
     * プレイヤーが指定スキルのプレステージ版を習得済みかチェック
     * @param playerUuid プレイヤーのUUID
     * @param skillId スキルID
     * @return 習得済みの場合true
     */
    fun hasPrestigeSkill(playerUuid: UUID, skillId: String): Boolean

    /**
     * プレステージを実行
     * @param playerUuid プレイヤーのUUID
     * @return 成功した場合true
     */
    fun executePrestige(playerUuid: UUID): Boolean
}
