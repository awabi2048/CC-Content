package jp.awabi2048.cccontent.features.rank.impl

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import jp.awabi2048.cccontent.features.rank.tutorial.PlayerTutorialRank
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.PlayerProfession
import java.util.UUID

/**
 * ランクシステムの統合実装
 */
class RankManagerImpl(
    storage: RankStorage,
    private var messageProvider: jp.awabi2048.cccontent.features.rank.localization.MessageProvider? = null
) : RankManager {
    
    private val tutorialManager = TutorialRankManagerImpl(storage)
    private val professionManager: ProfessionManagerImpl by lazy {
        ProfessionManagerImpl(storage, messageProvider!!)
    }
    
    /**
     * MessageProviderを設定
     */
    fun setMessageProvider(provider: jp.awabi2048.cccontent.features.rank.localization.MessageProvider) {
        this.messageProvider = provider
    }
    
    override fun getPlayerTutorial(playerUuid: UUID): PlayerTutorialRank {
        return tutorialManager.getPlayerTutorial(playerUuid)
    }
    
    override fun getTutorialRank(playerUuid: UUID): TutorialRank {
        return tutorialManager.getRank(playerUuid)
    }
    
    override fun isAttainer(playerUuid: UUID): Boolean {
        return tutorialManager.isAttainer(playerUuid)
    }
    
    override fun addTutorialExp(playerUuid: UUID, amount: Long): Boolean {
        return tutorialManager.addExperience(playerUuid, amount)
    }
    
    override fun setTutorialRank(playerUuid: UUID, rank: TutorialRank) {
        tutorialManager.setRank(playerUuid, rank)
    }
    
    override fun getPlayerProfession(playerUuid: UUID): PlayerProfession? {
        return professionManager.getPlayerProfession(playerUuid)
    }
    
    override fun hasProfession(playerUuid: UUID): Boolean {
        return professionManager.hasProfession(playerUuid)
    }
    
    override fun selectProfession(playerUuid: UUID, profession: Profession): Boolean {
        return professionManager.selectProfession(playerUuid, profession)
    }
    
    override fun changeProfession(playerUuid: UUID, profession: Profession): Boolean {
        return professionManager.changeProfession(playerUuid, profession)
    }
    
    override fun addProfessionExp(playerUuid: UUID, amount: Long): Boolean {
        return professionManager.addExperience(playerUuid, amount)
    }
    
    override fun acquireSkill(playerUuid: UUID, skillId: String): Boolean {
        return professionManager.acquireSkill(playerUuid, skillId)
    }
    
    override fun getAvailableSkills(playerUuid: UUID): List<String> {
        return professionManager.getAvailableSkills(playerUuid)
    }
    
    override fun getAcquiredSkills(playerUuid: UUID): Set<String> {
        return professionManager.getAcquiredSkills(playerUuid)
    }
    
    override fun getCurrentProfessionExp(playerUuid: UUID): Long {
        return professionManager.getCurrentExp(playerUuid)
    }
    
    override fun resetProfession(playerUuid: UUID): Boolean {
        return professionManager.resetProfession(playerUuid)
    }
    
    override fun saveData() {
        tutorialManager.saveData()
        professionManager.saveData()
    }
    
    override fun loadData() {
        tutorialManager.loadData()
        professionManager.loadData()
    }
}
