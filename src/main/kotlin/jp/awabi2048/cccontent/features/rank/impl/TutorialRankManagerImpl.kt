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
        // 経験値ベースシステムから移行したため、このメソッドは廃止予定
        // タスクベースのランクアップに対応させるため、実装なし
        return false
    }
    
    override fun isAttainer(playerUuid: UUID): Boolean {
        return getRank(playerUuid) == TutorialRank.ATTAINER
    }
    
    override fun setRank(playerUuid: UUID, rank: TutorialRank) {
        val tutorial = getPlayerTutorial(playerUuid)
        val oldRank = tutorial.currentRank
        tutorial.currentRank = rank
        tutorial.lastUpdated = System.currentTimeMillis()
        storage.saveTutorialRank(tutorial)
        
        // ランクが変更された場合、イベントを発火
        if (oldRank != rank) {
            val player = Bukkit.getPlayer(playerUuid)
            if (player != null) {
                val event = TutorialRankUpEvent(player, oldRank, rank)
                Bukkit.getPluginManager().callEvent(event)
            }
        }
    }
    
    /**
     * タスク完了によるランクアップを実行
     */
    override fun rankUpByTask(playerUuid: UUID): Boolean {
        val tutorial = getPlayerTutorial(playerUuid)
        val oldRank = tutorial.currentRank
        
        if (tutorial.rankUp()) {
            storage.saveTutorialRank(tutorial)
            
            val player = Bukkit.getPlayer(playerUuid)
            if (player != null) {
                val event = TutorialRankUpEvent(player, oldRank, tutorial.currentRank)
                Bukkit.getPluginManager().callEvent(event)
            }
            return true
        }
        return false
    }
    
    fun saveData() {
        tutorialCache.values.forEach { storage.saveTutorialRank(it) }
    }
    
    fun loadData() {
        tutorialCache.clear()
    }
}
