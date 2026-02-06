package jp.awabi2048.cccontent.features.rank.impl

import com.google.gson.Gson
import com.google.gson.JsonObject
import jp.awabi2048.cccontent.features.rank.*
import java.io.File
import java.util.UUID

/**
 * JSON形式でランクデータを保存するストレージの実装
 */
class JsonRankStorage(
    private val dataDirectory: File
) : RankStorage {
    
    private val gson = Gson()
    private val rankDirectory = File(dataDirectory, "ranks").apply { mkdirs() }
    
    override fun init() {
        rankDirectory.mkdirs()
    }
    
    override fun cleanup() {
        // クリーンアップ処理が必要に応じて追加
    }
    
    override fun savePlayerRank(rankData: PlayerRankData) {
        val file = File(rankDirectory, "${rankData.playerUuid}.json")
        val json = serializePlayerRankData(rankData)
        file.writeText(gson.toJson(json))
    }
    
    override fun loadPlayerRank(playerUuid: UUID): PlayerRankData? {
        val file = File(rankDirectory, "$playerUuid.json")
        if (!file.exists()) return null
        
        return try {
            val json = gson.fromJson(file.readText(), JsonObject::class.java)
            deserializePlayerRankData(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    override fun deletePlayerRank(playerUuid: UUID) {
        val file = File(rankDirectory, "$playerUuid.json")
        file.delete()
    }
    
    override fun getAllPlayerRanks(rankType: RankType): List<PlayerRankData> {
        return rankDirectory.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val json = gson.fromJson(file.readText(), JsonObject::class.java)
                    deserializePlayerRankData(json)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } ?: emptyList()
    }
    
    private fun serializePlayerRankData(rankData: PlayerRankData): JsonObject {
        val json = JsonObject()
        json.addProperty("playerUuid", rankData.playerUuid.toString())
        
        val ranksJson = JsonObject()
        rankData.ranks.forEach { (rankType, playerRank) ->
            val rankJson = JsonObject()
            rankJson.addProperty("score", playerRank.score)
            rankJson.addProperty("tier", playerRank.tier.name)
            rankJson.addProperty("lastUpdated", playerRank.lastUpdated)
            ranksJson.add(rankType.name, rankJson)
        }
        json.add("ranks", ranksJson)
        
        return json
    }
    
    private fun deserializePlayerRankData(json: JsonObject): PlayerRankData {
        val playerUuid = UUID.fromString(json.get("playerUuid").asString)
        val rankData = PlayerRankData(playerUuid)
        
        val ranksJson = json.getAsJsonObject("ranks")
        RankType.values().forEach { rankType ->
            val rankJson = ranksJson.getAsJsonObject(rankType.name)
            if (rankJson != null) {
                val score = rankJson.get("score").asLong
                val tier = RankTier.valueOf(rankJson.get("tier").asString)
                val lastUpdated = rankJson.get("lastUpdated").asLong
                
                rankData.ranks[rankType] = PlayerRank(
                    playerUuid,
                    rankType,
                    score,
                    tier,
                    lastUpdated
                )
            }
        }
        
        return rankData
    }
}
