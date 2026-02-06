package jp.awabi2048.cccontent.features.rank.impl

import jp.awabi2048.cccontent.features.rank.*
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

/**
 * YAML形式でランクデータを保存するストレージの実装
 * playerdata/<uuid>.yml内の"rank"セクションにランク情報を格納
 */
class YamlRankStorage(
    private val dataDirectory: File
) : RankStorage {
    
    private val playerdataDirectory = File(dataDirectory, "playerdata").apply { mkdirs() }
    
    override fun init() {
        playerdataDirectory.mkdirs()
    }
    
    override fun cleanup() {
        // クリーンアップ処理が必要に応じて追加
    }
    
    override fun savePlayerRank(rankData: PlayerRankData) {
        val file = File(playerdataDirectory, "${rankData.playerUuid}.yml")
        val config = if (file.exists()) YamlConfiguration.loadConfiguration(file) else YamlConfiguration()
        
        // "rank"セクションを作成または更新
        val rankSection = config.createSection("rank")
        
        rankData.ranks.forEach { (rankType, playerRank) ->
            val rankTypeSection = rankSection.createSection(rankType.name)
            rankTypeSection.set("score", playerRank.score)
            rankTypeSection.set("tier", playerRank.tier.name)
            rankTypeSection.set("lastUpdated", playerRank.lastUpdated)
        }
        
        try {
            config.save(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun loadPlayerRank(playerUuid: UUID): PlayerRankData? {
        val file = File(playerdataDirectory, "$playerUuid.yml")
        if (!file.exists()) return null
        
        return try {
            val config = YamlConfiguration.loadConfiguration(file)
            val rankSection = config.getConfigurationSection("rank") ?: return null
            
            val rankData = PlayerRankData(playerUuid)
            
            RankType.values().forEach { rankType ->
                val rankTypeSection = rankSection.getConfigurationSection(rankType.name)
                if (rankTypeSection != null) {
                    val score = rankTypeSection.getLong("score")
                    val tierName = rankTypeSection.getString("tier", "BRONZE_I") ?: "BRONZE_I"
                    val lastUpdated = rankTypeSection.getLong("lastUpdated", System.currentTimeMillis())
                    
                    val tier = try {
                        RankTier.valueOf(tierName)
                    } catch (e: IllegalArgumentException) {
                        RankTier.BRONZE_I
                    }
                    
                    rankData.ranks[rankType] = PlayerRank(
                        playerUuid,
                        rankType,
                        score,
                        tier,
                        lastUpdated
                    )
                }
            }
            
            rankData
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    override fun deletePlayerRank(playerUuid: UUID) {
        val file = File(playerdataDirectory, "$playerUuid.yml")
        if (file.exists()) {
            val config = YamlConfiguration.loadConfiguration(file)
            config.set("rank", null)  // "rank"セクションを削除
            try {
                config.save(file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    override fun getAllPlayerRanks(rankType: RankType): List<PlayerRankData> {
        return playerdataDirectory.listFiles { file -> file.extension == "yml" }
            ?.mapNotNull { file ->
                try {
                    val uuid = UUID.fromString(file.nameWithoutExtension)
                    loadPlayerRank(uuid)
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
    }
}
