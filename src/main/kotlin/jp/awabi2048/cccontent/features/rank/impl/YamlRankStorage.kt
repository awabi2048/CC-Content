package jp.awabi2048.cccontent.features.rank.impl

import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.tutorial.PlayerTutorialRank
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import jp.awabi2048.cccontent.features.rank.profession.PlayerProfession
import jp.awabi2048.cccontent.features.rank.profession.Profession
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
    
    override fun saveTutorialRank(tutorialRank: PlayerTutorialRank) {
        val file = File(playerdataDirectory, "${tutorialRank.playerUuid}.yml")
        val config = if (file.exists()) YamlConfiguration.loadConfiguration(file) else YamlConfiguration()
        
        // "rank.tutorial"セクションを作成または更新
        val tutorialSection = config.createSection("rank.tutorial")
        tutorialSection.set("currentRank", tutorialRank.currentRank.name)
        tutorialSection.set("currentExp", tutorialRank.currentExp)
        tutorialSection.set("lastUpdated", tutorialRank.lastUpdated)
        
        try {
            config.save(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun loadTutorialRank(playerUuid: UUID): PlayerTutorialRank? {
        val file = File(playerdataDirectory, "$playerUuid.yml")
        if (!file.exists()) return null
        
        return try {
            val config = YamlConfiguration.loadConfiguration(file)
            val tutorialSection = config.getConfigurationSection("rank.tutorial") ?: return null
            
            val rankName = tutorialSection.getString("currentRank", "VISITOR") ?: "VISITOR"
            val rank = try {
                TutorialRank.valueOf(rankName)
            } catch (e: IllegalArgumentException) {
                TutorialRank.VISITOR
            }
            
            val currentExp = tutorialSection.getLong("currentExp", 0L)
            val lastUpdated = tutorialSection.getLong("lastUpdated", System.currentTimeMillis())
            
            PlayerTutorialRank(
                playerUuid,
                rank,
                currentExp,
                lastUpdated
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    override fun saveProfession(profession: PlayerProfession) {
        val file = File(playerdataDirectory, "${profession.playerUuid}.yml")
        val config = if (file.exists()) YamlConfiguration.loadConfiguration(file) else YamlConfiguration()
        
        // "rank.profession"セクションを作成または更新
        val professionSection = config.createSection("rank.profession")
        professionSection.set("profession", profession.profession.id)
        professionSection.set("acquiredSkills", profession.acquiredSkills.toList())
        professionSection.set("currentExp", profession.currentExp)
        professionSection.set("lastUpdated", profession.lastUpdated)
        
        try {
            config.save(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun loadProfession(playerUuid: UUID): PlayerProfession? {
        val file = File(playerdataDirectory, "$playerUuid.yml")
        if (!file.exists()) return null
        
        return try {
            val config = YamlConfiguration.loadConfiguration(file)
            val professionSection = config.getConfigurationSection("rank.profession") ?: return null
            
            val professionId = professionSection.getString("profession") ?: return null
            val profession = Profession.fromId(professionId) ?: return null
            
            val acquiredSkills = professionSection.getStringList("acquiredSkills").toMutableSet()
            val currentExp = professionSection.getLong("currentExp", 0L)
            val lastUpdated = professionSection.getLong("lastUpdated", System.currentTimeMillis())
            
            PlayerProfession(
                playerUuid,
                profession,
                acquiredSkills,
                currentExp,
                lastUpdated
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    override fun deleteProfession(playerUuid: UUID) {
        val file = File(playerdataDirectory, "$playerUuid.yml")
        if (file.exists()) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                config.set("rank.profession", null)  // "rank.profession"セクションを削除
                config.save(file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
