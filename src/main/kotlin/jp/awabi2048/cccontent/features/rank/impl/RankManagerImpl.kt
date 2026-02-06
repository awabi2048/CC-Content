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
    private val storage: RankStorage,
    private var messageProvider: jp.awabi2048.cccontent.features.rank.localization.MessageProvider? = null
) : RankManager {
    
    private val tutorialManager = TutorialRankManagerImpl(storage)
    private var professionManager: ProfessionManagerImpl? = null
    
    /**
     * MessageProviderを設定して、ProfessionManagerを初期化
     */
    fun setMessageProvider(provider: jp.awabi2048.cccontent.features.rank.localization.MessageProvider) {
        this.messageProvider = provider
        this.professionManager = ProfessionManagerImpl(storage, provider)
    }
    
    /**
     * ProfessionManagerを取得（初期化済みか確認）
     */
    private fun getProfessionManager(): ProfessionManagerImpl {
        return professionManager ?: throw IllegalStateException("ProfessionManager is not initialized. Call setMessageProvider first.")
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
        return getProfessionManager().getPlayerProfession(playerUuid)
    }
    
    override fun hasProfession(playerUuid: UUID): Boolean {
        return getProfessionManager().hasProfession(playerUuid)
    }
    
    override fun selectProfession(playerUuid: UUID, profession: Profession): Boolean {
        return getProfessionManager().selectProfession(playerUuid, profession)
    }
    
    override fun changeProfession(playerUuid: UUID, profession: Profession): Boolean {
        return getProfessionManager().changeProfession(playerUuid, profession)
    }
    
    override fun addProfessionExp(playerUuid: UUID, amount: Long): Boolean {
        return getProfessionManager().addExperience(playerUuid, amount)
    }
    
    override fun acquireSkill(playerUuid: UUID, skillId: String): Boolean {
        return getProfessionManager().acquireSkill(playerUuid, skillId)
    }
    
    override fun getAvailableSkills(playerUuid: UUID): List<String> {
        return getProfessionManager().getAvailableSkills(playerUuid)
    }
    
    override fun getAcquiredSkills(playerUuid: UUID): Set<String> {
        return getProfessionManager().getAcquiredSkills(playerUuid)
    }
    
    override fun getCurrentProfessionExp(playerUuid: UUID): Long {
        return getProfessionManager().getCurrentExp(playerUuid)
    }
    
    override fun resetProfession(playerUuid: UUID): Boolean {
        return getProfessionManager().resetProfession(playerUuid)
    }
    
    override fun saveData() {
        tutorialManager.saveData()
        professionManager?.saveData()
    }
    
    override fun loadData() {
        tutorialManager.loadData()
        professionManager?.loadData()
    }
    
    override fun rankUpByTask(playerUuid: UUID): Boolean {
        return tutorialManager.rankUpByTask(playerUuid)
    }
}
