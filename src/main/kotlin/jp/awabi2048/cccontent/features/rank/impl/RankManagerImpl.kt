package jp.awabi2048.cccontent.features.rank.impl

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import jp.awabi2048.cccontent.features.rank.tutorial.PlayerTutorialRank
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.PlayerProfession
import jp.awabi2048.cccontent.features.rank.profession.ProfessionBossBarManager
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class RankManagerImpl(
    private val storage: RankStorage,
    private var messageProvider: jp.awabi2048.cccontent.features.rank.localization.MessageProvider? = null
) : RankManager {
    
    private val tutorialManager = TutorialRankManagerImpl(storage)
    private var professionManager: ProfessionManagerImpl? = null
    private var bossBarManager: ProfessionBossBarManager? = null
    
    fun setMessageProvider(provider: jp.awabi2048.cccontent.features.rank.localization.MessageProvider) {
        this.messageProvider = provider
        this.professionManager = ProfessionManagerImpl(storage, provider)
    }
    
    fun initBossBarManager(plugin: JavaPlugin) {
        val pm = professionManager ?: return
        val mp = messageProvider ?: return
        this.bossBarManager = ProfessionBossBarManager(plugin, pm, mp)
    }
    
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
        return tutorialManager.isAttainer(playerUuid) && !getProfessionManager().hasProfession(playerUuid)
    }
    
    override fun getPlayerRank(playerUuid: UUID): String {
        if (!tutorialManager.isAttainer(playerUuid)) {
            return tutorialManager.getRank(playerUuid).name
        }
        val prof = getProfessionManager().getPlayerProfession(playerUuid)
        return if (prof != null) prof.profession.id else "ATTAINER"
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
        val result = getProfessionManager().addExperience(playerUuid, amount)
        if (result) {
            val player = Bukkit.getPlayer(playerUuid)
            if (player != null) {
                bossBarManager?.showExpGain(player, amount)
            }
        }
        return result
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

    override fun getCurrentProfessionLevel(playerUuid: UUID): Int {
        return getProfessionManager().getCurrentLevel(playerUuid)
    }

    override fun setProfessionLevel(playerUuid: UUID, level: Int): Boolean {
        return getProfessionManager().setLevel(playerUuid, level)
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

    override fun isProfessionBossBarEnabled(playerUuid: UUID): Boolean {
        return getProfessionManager().isBossBarEnabled(playerUuid)
    }

    override fun setProfessionBossBarEnabled(playerUuid: UUID, enabled: Boolean) {
        getProfessionManager().setBossBarEnabled(playerUuid, enabled)
    }

    override fun hideProfessionBossBar(playerUuid: UUID) {
        bossBarManager?.hideBossBar(playerUuid)
    }

    override fun hideAllProfessionBossBars() {
        bossBarManager?.hideAll()
    }

    override fun getPrestigeLevel(playerUuid: UUID): Int {
        return getProfessionManager().getPrestigeLevel(playerUuid)
    }

    override fun canPrestige(playerUuid: UUID): Boolean {
        return getProfessionManager().canPrestige(playerUuid)
    }

    override fun acquirePrestigeSkill(playerUuid: UUID, skillId: String): Boolean {
        return getProfessionManager().acquirePrestigeSkill(playerUuid, skillId)
    }

    override fun executePrestige(playerUuid: UUID): Boolean {
        val result = getProfessionManager().executePrestige(playerUuid)
        if (result) {
            tutorialManager.setRank(playerUuid, jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank.ATTAINER)
        }
        return result
    }

    override fun isMaxProfessionLevel(playerUuid: UUID): Boolean {
        val playerProf = getProfessionManager().getPlayerProfession(playerUuid) ?: return false
        val skillTree = jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry.getSkillTree(playerProf.profession) ?: return false
        return playerProf.isMaxLevel(skillTree)
    }

    override fun savePlayerProfession(playerUuid: UUID) {
        val playerProf = getProfessionManager().getPlayerProfession(playerUuid) ?: return
        storage.saveProfession(playerProf)
    }
}
