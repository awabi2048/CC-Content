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
        val tutorialSection = config.getConfigurationSection("rank.tutorial")
            ?: config.createSection("rank.tutorial")
        tutorialSection.set("taskProgress.mobKills", null)
        tutorialSection.set("taskProgress.blockMines", null)
        tutorialSection.set("taskProgress.bossKills", null)
        tutorialSection.set("taskProgress.items", null)
        tutorialSection.set("currentRank", tutorialRank.currentRank.name)
        tutorialSection.set("lastUpdated", tutorialRank.lastUpdated)
        tutorialSection.set("lastPlayTime", tutorialRank.lastPlayTime)
        
        // タスク進捗を保存
        val taskProgress = tutorialRank.taskProgress
        tutorialSection.set("taskProgress.playTime", taskProgress.playTime)
        tutorialSection.set("taskProgress.vanillaExp", taskProgress.vanillaExp)
        
        // モブ討伐数
        taskProgress.mobKills.forEach { (mobType, count) ->
            tutorialSection.set("taskProgress.mobKills.$mobType", count)
        }
        
        // ブロック採掘数
        taskProgress.blockMines.forEach { (blockType, count) ->
            tutorialSection.set("taskProgress.blockMines.$blockType", count)
        }
        
        // ボス討伐数
        taskProgress.bossKills.forEach { (bossType, count) ->
            tutorialSection.set("taskProgress.bossKills.$bossType", count)
        }
        
        // アイテム所持数
        taskProgress.items.forEach { (material, count) ->
            tutorialSection.set("taskProgress.items.$material", count)
        }
        
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
            
            val rankName = tutorialSection.getString("currentRank", "NEWBIE") ?: "NEWBIE"
            val rank = try {
                TutorialRank.valueOf(rankName)
            } catch (e: IllegalArgumentException) {
                TutorialRank.NEWBIE
            }
            
            val lastUpdated = tutorialSection.getLong("lastUpdated", System.currentTimeMillis())
            val lastPlayTime = tutorialSection.getLong("lastPlayTime", System.currentTimeMillis())
            
            // タスク進捗を読み込み
            val taskProgress = jp.awabi2048.cccontent.features.rank.tutorial.task.TaskProgress(
                playerUuid,
                rank.name,
                tutorialSection.getLong("taskProgress.playTime", 0L),
                mutableMapOf(),
                mutableMapOf(),
                tutorialSection.getLong("taskProgress.vanillaExp", 0L),
                mutableMapOf(),
                mutableMapOf()
            )
            
            // モブ討伐数を読み込み
            tutorialSection.getConfigurationSection("taskProgress.mobKills")?.getKeys(false)?.forEach { mobType ->
                val count = tutorialSection.getInt("taskProgress.mobKills.$mobType", 0)
                taskProgress.mobKills[mobType] = count
            }
            
            // ブロック採掘数を読み込み
            tutorialSection.getConfigurationSection("taskProgress.blockMines")?.getKeys(false)?.forEach { blockType ->
                val count = tutorialSection.getInt("taskProgress.blockMines.$blockType", 0)
                taskProgress.blockMines[blockType] = count
            }
            
            // ボス討伐数を読み込み
            tutorialSection.getConfigurationSection("taskProgress.bossKills")?.getKeys(false)?.forEach { bossType ->
                val count = tutorialSection.getInt("taskProgress.bossKills.$bossType", 0)
                taskProgress.bossKills[bossType] = count
            }
            
            // アイテム所持数を読み込み
            tutorialSection.getConfigurationSection("taskProgress.items")?.getKeys(false)?.forEach { material ->
                val count = tutorialSection.getInt("taskProgress.items.$material", 0)
                taskProgress.items[material] = count
            }
            
            PlayerTutorialRank(
                playerUuid,
                rank,
                taskProgress,
                lastUpdated,
                lastPlayTime
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
        val professionSection = config.getConfigurationSection("rank.profession")
            ?: config.createSection("rank.profession")
        professionSection.set("profession", profession.profession.id)
        professionSection.set("acquiredSkills", profession.acquiredSkills.toList())
        professionSection.set("currentLevel", profession.currentLevel)
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
            val currentLevel = professionSection.getInt("currentLevel", 1)
            val currentExp = professionSection.getLong("currentExp", 0L)
            val lastUpdated = professionSection.getLong("lastUpdated", System.currentTimeMillis())
            
            PlayerProfession(
                playerUuid,
                profession,
                acquiredSkills,
                currentLevel,
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
