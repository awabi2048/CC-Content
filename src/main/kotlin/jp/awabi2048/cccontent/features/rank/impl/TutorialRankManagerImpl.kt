package jp.awabi2048.cccontent.features.rank.impl

import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import jp.awabi2048.cccontent.features.rank.tutorial.PlayerTutorialRank
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRankManager
import jp.awabi2048.cccontent.features.rank.event.TutorialRankUpEvent
import org.bukkit.Bukkit
import java.util.UUID

/**
 * チュートリアルランク管理の実装
 */
class TutorialRankManagerImpl(
    private val storage: RankStorage
) : TutorialRankManager {
    
    /** メモリ上にキャッシュされたチュートリアルランク */
    private val tutorialCache: MutableMap<UUID, PlayerTutorialRank> = mutableMapOf()
    
    init {
        storage.init()
    }
    
    override fun getPlayerTutorial(playerUuid: UUID): PlayerTutorialRank {
        return tutorialCache.getOrPut(playerUuid) {
            storage.loadTutorialRank(playerUuid) ?: PlayerTutorialRank(playerUuid)
        }
    }
    
    override fun getRank(playerUuid: UUID): TutorialRank {
        return getPlayerTutorial(playerUuid).currentRank
    }
    
    override fun addExperience(playerUuid: UUID, amount: Long): Boolean {
        val tutorial = getPlayerTutorial(playerUuid)
        val oldRank = tutorial.currentRank
        
        val rankChanged = tutorial.addExperience(amount)
        storage.saveTutorialRank(tutorial)
        
        // プレイヤーがオンラインの場合、ランクアップイベントを発火
        if (rankChanged) {
            val player = Bukkit.getPlayer(playerUuid)
            if (player != null) {
                val event = TutorialRankUpEvent(player, oldRank, tutorial.currentRank, tutorial.currentExp)
                Bukkit.getPluginManager().callEvent(event)
            }
        }
        
        return rankChanged
    }
    
    override fun isAttainer(playerUuid: UUID): Boolean {
        return getRank(playerUuid) == TutorialRank.ATTAINER
    }
    
    override fun setRank(playerUuid: UUID, rank: TutorialRank) {
        val tutorial = getPlayerTutorial(playerUuid)
        tutorial.currentRank = rank
        tutorial.currentExp = 0L
        tutorial.lastUpdated = System.currentTimeMillis()
        storage.saveTutorialRank(tutorial)
    }
    
    fun saveData() {
        tutorialCache.values.forEach { storage.saveTutorialRank(it) }
    }
    
    fun loadData() {
        tutorialCache.clear()
    }
}
