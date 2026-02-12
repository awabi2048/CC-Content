package jp.awabi2048.cccontent.features.rank.localization

import jp.awabi2048.cccontent.features.rank.profession.Profession

/**
 * 翻訳メッセージを提供するインターフェース
 */
interface MessageProvider {
    /**
     * 翻訳キーに基づいてメッセージを取得
     * @param key 翻訳キー
     * @param args メッセージに埋め込む値
     * @return 翻訳されたメッセージ
     */
    fun getMessage(key: String, vararg args: Any?): String
    
    /**
     * 職業名を取得
     * @param profession 職業
     * @return 翻訳された職業名
     */
    fun getProfessionName(profession: Profession): String
    
    /**
     * 職業の説明文を取得
     * @param profession 職業
     * @return 翻訳された説明文
     */
    fun getProfessionDescription(profession: Profession): String
    
    /**
     * スキル名を取得
     * @param profession 職業
     * @param skillId スキルID
     * @return 翻訳されたスキル名
     */
    fun getSkillName(profession: Profession, skillId: String): String
    
    /**
     * スキルの説明文を取得
     * @param profession 職業
     * @param skillId スキルID
     * @return 翻訳された説明文
     */
    fun getSkillDescription(profession: Profession, skillId: String): String

    /**
     * リスト形式のメッセージを取得
     * @param key 翻訳キー
     * @return 翻訳されたメッセージリスト
     */
    fun getMessageList(key: String): List<String>
}
