package jp.awabi2048.cccontent.features.rank.impl

import jp.awabi2048.cccontent.features.rank.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/**
 * ランクシステムの標準実装
 */
class RankManagerImpl(
    private val storage: RankStorage
) : RankManager {
    
    /** メモリ上にキャッシュされたランクデータ */
    private val rankDataCache: MutableMap<UUID, PlayerRankData> = mutableMapOf()
    
    init {
        storage.init()
        storage.getAllPlayerRanks(RankType.ARENA).forEach { data ->
            rankDataCache[data.playerUuid] = data
        }
    }
    
    override fun getPlayerRankData(playerUuid: UUID): PlayerRankData {
        return rankDataCache.getOrPut(playerUuid) {
            storage.loadPlayerRank(playerUuid) ?: PlayerRankData(playerUuid)
        }
    }
    
    override fun getPlayerRank(playerUuid: UUID, rankType: RankType): PlayerRank {
        return getPlayerRankData(playerUuid).getRank(rankType)
    }
    
    override fun addScore(playerUuid: UUID, rankType: RankType, score: Long): Boolean {
        val rankData = getPlayerRankData(playerUuid)
        val playerRank = rankData.getRank(rankType)
        
        val oldTier = playerRank.tier
        val oldScore = playerRank.score
        val tierChanged = playerRank.addScore(score)
        
        // プレイヤーがオンラインの場合、イベントを発火
        val player = Bukkit.getPlayer(playerUuid)
        if (player != null) {
            // ScoreAddイベント
            val scoreEvent = PlayerScoreAddEvent(
                player,
                rankType,
                score,
                oldScore,
                playerRank.score
            )
            Bukkit.getPluginManager().callEvent(scoreEvent)
            
            // ティアが変更された場合、RankChangeイベント
            if (tierChanged) {
                val changeEvent = PlayerRankChangeEvent(
                    player,
                    rankType,
                    oldTier,
                    playerRank.tier,
                    playerRank.score
                )
                Bukkit.getPluginManager().callEvent(changeEvent)
                
                // ランクアップの場合、RankUpイベント
                if (oldTier.level < playerRank.tier.level) {
                    val upEvent = PlayerRankUpEvent(
                        player,
                        rankType,
                        oldTier,
                        playerRank.tier,
                        playerRank.score
                    )
                    Bukkit.getPluginManager().callEvent(upEvent)
                }
            }
        }
        
        return tierChanged
    }
    
    override fun resetRank(playerUuid: UUID, rankType: RankType) {
        val rankData = getPlayerRankData(playerUuid)
        rankData.getRank(rankType).resetScore()
    }
    
    override fun deletePlayerData(playerUuid: UUID) {
        rankDataCache.remove(playerUuid)
        storage.deletePlayerRank(playerUuid)
    }
    
    override fun saveData() {
        rankDataCache.values.forEach { storage.savePlayerRank(it) }
    }
    
    override fun loadData() {
        rankDataCache.clear()
        RankType.values().forEach { rankType ->
            storage.getAllPlayerRanks(rankType).forEach { data ->
                rankDataCache[data.playerUuid] = data
            }
        }
    }
    
    override fun getRanking(rankType: RankType, limit: Int): List<Triple<UUID, Long, RankTier>> {
        return rankDataCache.values
            .map { it.playerUuid to it.getRank(rankType) }
            .sortedByDescending { it.second.score }
            .take(limit)
            .map { (uuid, rank) -> Triple(uuid, rank.score, rank.tier) }
    }
}
