package jp.awabi2048.cccontent.features.rank.localization

import jp.awabi2048.cccontent.features.rank.profession.Profession

/**
 * MessageProviderの実装クラス
 */
class MessageProviderImpl(
    private val languageLoader: LanguageLoader
) : MessageProvider {
    
     override fun getMessage(key: String, vararg args: Any?): String {
         // 互換性のため、Any?を受け取る場合は単純にキーのみで取得する
         // 複数の引数がある場合は無視
         return languageLoader.getMessage(key)
     }
    
    override fun getProfessionName(profession: Profession): String {
        return getMessage("profession.${profession.id}.name")
    }
    
    override fun getProfessionDescription(profession: Profession): String {
        return getMessage("profession.${profession.id}.description")
    }
    
    override fun getSkillName(profession: Profession, skillId: String): String {
        return getMessage("skill.${profession.id}.$skillId.name")
    }
    
    override fun getSkillDescription(profession: Profession, skillId: String): String {
        return getMessage("skill.${profession.id}.$skillId.description")
    }
}
