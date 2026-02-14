package jp.awabi2048.cccontent.features.rank.impl

import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.tutorial.PlayerTutorialRank
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import jp.awabi2048.cccontent.features.rank.profession.PlayerProfession
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.skill.SkillSwitchMode
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
        migrateOldProfessionData()
    }
    
    /**
     * 旧形式の職業データを移行
     * profession/<profession>/<uuid>.yml から playerdata/<uuid>.yml へ
     */
    private fun migrateOldProfessionData() {
        val professionDir = File(dataDirectory, "profession")
        if (!professionDir.exists()) return
        
        val migratedCount = mutableListOf<String>()
        
        for (professionFile in professionDir.listFiles() ?: emptyArray()) {
            if (!professionFile.isDirectory) continue
            
            val professionId = professionFile.name
            for (playerFile in professionDir.listFiles() ?: emptyArray()) {
                if (playerFile.extension != "yml") continue
                
                val uuidString = playerFile.nameWithoutExtension
                val uuid = try {
                    UUID.fromString(uuidString)
                } catch (e: IllegalArgumentException) {
                    continue
                }
                
                val targetFile = File(playerdataDirectory, "$uuidString.yml")
                if (targetFile.exists()) continue
                
                try {
                    val oldConfig = YamlConfiguration.loadConfiguration(playerFile)
                    val newConfig = if (targetFile.exists()) YamlConfiguration.loadConfiguration(targetFile) else YamlConfiguration()
                    
                    val professionSection = newConfig.createSection("rank.profession")
                    professionSection.set("profession", professionId)
                    professionSection.set("acquiredSkills", oldConfig.getStringList("acquiredSkills"))
                    professionSection.set("prestigeSkills", oldConfig.getStringList("prestigeSkills"))
                    professionSection.set("currentExp", oldConfig.getLong("currentExp", 0L))
                    professionSection.set("lastUpdated", oldConfig.getLong("lastUpdated", System.currentTimeMillis()))
                    professionSection.set("bossBarEnabled", oldConfig.getBoolean("bossBarEnabled", true))
                    
                    newConfig.save(targetFile)
                    playerFile.delete()
                    migratedCount += "$uuidString"
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (professionFile.listFiles()?.isEmpty() == true) {
                professionFile.delete()
            }
        }
        
        if (migratedCount.isNotEmpty()) {
            java.util.logging.Logger.getLogger("CC-Content").info("[YamlRankStorage] 旧職業データを ${migratedCount.size} 件移行しました")
        }
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

        val professionSection = config.getConfigurationSection("rank.profession")
            ?: config.createSection("rank.profession")
        professionSection.set("profession", profession.profession.id)
        professionSection.set("acquiredSkills", profession.acquiredSkills.toList())
        professionSection.set("prestigeSkills", profession.prestigeSkills.toList())
        professionSection.set("currentExp", profession.currentExp)
        professionSection.set("lastUpdated", profession.lastUpdated)
        professionSection.set("bossBarEnabled", profession.bossBarEnabled)
        professionSection.set("activeSkillId", profession.activeSkillId)
        professionSection.set("skillSwitchMode", profession.skillSwitchMode.id)

        // スキル発動状態を保存
        professionSection.set("skillActivationStates", null) // 古いデータをクリア
        profession.skillActivationStates.forEach { (skillId, enabled) ->
            professionSection.set("skillActivationStates.$skillId", enabled)
        }

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
            val prestigeSkills = professionSection.getStringList("prestigeSkills").toMutableSet()
            val currentExp = professionSection.getLong("currentExp", 0L)
            val lastUpdated = professionSection.getLong("lastUpdated", System.currentTimeMillis())
            val bossBarEnabled = professionSection.getBoolean("bossBarEnabled", true)

            // 後方互換性：既存データにフィールドがない場合はデフォルト値を使用
            val activeSkillId = professionSection.getString("activeSkillId")
            val skillSwitchModeId = professionSection.getString("skillSwitchMode") ?: SkillSwitchMode.MENU_ONLY.id
            val skillSwitchMode = SkillSwitchMode.fromId(skillSwitchModeId) ?: SkillSwitchMode.MENU_ONLY

            // スキル発動状態を読み込み（未設定時は空Map）
            val skillActivationStates = mutableMapOf<String, Boolean>()
            professionSection.getConfigurationSection("skillActivationStates")?.getKeys(false)?.forEach { skillId ->
                skillActivationStates[skillId] = professionSection.getBoolean("skillActivationStates.$skillId", true)
            }

            PlayerProfession(
                playerUuid,
                profession,
                acquiredSkills,
                currentExp,
                lastUpdated,
                bossBarEnabled,
                prestigeSkills,
                activeSkillId,
                skillSwitchMode,
                skillActivationStates
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
