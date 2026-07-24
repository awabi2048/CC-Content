package jp.awabi2048.cccontent.features.rank.impl

import jp.awabi2048.cccontent.features.rank.RankStorage
import jp.awabi2048.cccontent.features.rank.tutorial.PlayerTutorialRank
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import jp.awabi2048.cccontent.features.rank.profession.PlayerProfession
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.BossBarDisplayMode
import jp.awabi2048.cccontent.features.rank.profession.profile.FishingInformationMode
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionCycleStatistics
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionFeatureToggles
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionPrestigeRecord
import jp.awabi2048.cccontent.features.rank.skill.SkillSwitchMode
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

/**
 * YAML形式でランクデータを保存するストレージの実装
 * playerdata/<uuid>.yml内の"rank"セクションにランク情報を格納
 */
class YamlRankStorage(
    private val dataDirectory: File
) : RankStorage {

    companion object {
        private const val PROFESSION_SCHEMA_VERSION = 3
        private const val SCHEMA_MARKER_FILE = "rank-profession-schema-v3.applied"
    }

    private val logger = Logger.getLogger("CC-Content")
    
    private val playerdataDirectory = File(dataDirectory, "playerdata").apply { mkdirs() }
    
    override fun init() {
        playerdataDirectory.mkdirs()
        discardLegacyProfessionDataOnce()
    }

    private fun discardLegacyProfessionDataOnce() {
        val marker = File(dataDirectory, SCHEMA_MARKER_FILE)
        if (marker.exists()) return

        val timestamp = Instant.now().toEpochMilli()
        val backupDirectory = File(dataDirectory, "discarded/rank-profession-$timestamp")
        var discardedPlayers = 0

        for (playerFile in playerdataDirectory.listFiles { file -> file.isFile && file.extension == "yml" }.orEmpty()) {
            val config = YamlConfiguration.loadConfiguration(playerFile)
            if (!config.isConfigurationSection("rank.profession")) continue

            backupDirectory.mkdirs()
            Files.copy(
                playerFile.toPath(),
                File(backupDirectory, playerFile.name).toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            config.set("rank.profession", null)
            config.save(playerFile)
            discardedPlayers++
        }

        val oldProfessionDirectory = File(dataDirectory, "profession")
        if (oldProfessionDirectory.exists()) {
            backupDirectory.mkdirs()
            Files.move(
                oldProfessionDirectory.toPath(),
                File(backupDirectory, "profession").toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }

        marker.parentFile?.mkdirs()
        Files.writeString(
            marker.toPath(),
            "schemaVersion=$PROFESSION_SCHEMA_VERSION\nappliedAt=$timestamp\ndiscardedPlayers=$discardedPlayers\n"
        )
        logger.info("[YamlRankStorage] 旧職業データを $discardedPlayers 件退避し、新スキーマを適用しました")
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
        tutorialSection.set("taskProgress.myWorldCreated", taskProgress.myWorldCreated)
        tutorialSection.set("taskProgress.activeOverworldTime", taskProgress.activeOverworldTime)
        tutorialSection.set("taskProgress.diamondOresMined", taskProgress.diamondOresMined)
        tutorialSection.set("taskProgress.netherPortalIgnited", taskProgress.netherPortalIgnited)
        tutorialSection.set("taskProgress.activeNetherResourceTime", taskProgress.activeNetherResourceTime)
        tutorialSection.set("taskProgress.enderEyesCrafted", taskProgress.enderEyesCrafted)
        tutorialSection.set("taskProgress.endPortalOpened", taskProgress.endPortalOpened)
        
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
            
            val rawRankName = tutorialSection.getString("currentRank", "NEWBIE") ?: "NEWBIE"
            val rank = TutorialRank.entries.firstOrNull { it.name.equals(rawRankName, ignoreCase = true) }
                ?: run {
                    throw IllegalStateException(
                        "[YamlRankStorage] Unknown currentRank '$rawRankName' for player $playerUuid"
                    )
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
                mutableMapOf(),
                tutorialSection.getBoolean("taskProgress.myWorldCreated", false),
                tutorialSection.getLong("taskProgress.activeOverworldTime", 0L),
                tutorialSection.getInt("taskProgress.diamondOresMined", 0),
                tutorialSection.getBoolean("taskProgress.netherPortalIgnited", false),
                tutorialSection.getLong("taskProgress.activeNetherResourceTime", 0L),
                tutorialSection.getInt("taskProgress.enderEyesCrafted", 0),
                tutorialSection.getBoolean("taskProgress.endPortalOpened", false)
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
        professionSection.set("schemaVersion", PROFESSION_SCHEMA_VERSION)
        professionSection.set("profession", profession.profession.id)
        professionSection.set("currentExp", profession.currentExp)
        professionSection.set("lastUpdated", profession.lastUpdated)
        professionSection.set("bossBarDisplayMode", profession.bossBarDisplayMode.id)
        professionSection.set("levelUpNotificationEnabled", profession.levelUpNotificationEnabled)

        professionSection.set("acquiredSkills", profession.acquiredSkills.toList())
        professionSection.set("prestigeSkills", profession.prestigeSkills.toList())
        professionSection.set("activeSkillId", profession.activeSkillId)
        professionSection.set("skillSwitchMode", profession.skillSwitchMode.id)
        professionSection.set("skillActivationStates", null)
        profession.skillActivationStates.forEach { (skillId, enabled) ->
            professionSection.set("skillActivationStates.$skillId", enabled)
        }

        if (profession.profession.usesTypedAbilityAdapter) {
            professionSection.set("featureToggles.batchProcessingEnabled", profession.featureToggles.batchProcessingEnabled)
            professionSection.set("featureToggles.leafCleanupEnabled", profession.featureToggles.leafCleanupEnabled)
            professionSection.set("featureToggles.automaticReplantEnabled", profession.featureToggles.automaticReplantEnabled)
            professionSection.set("featureToggles.areaTillingEnabled", profession.featureToggles.areaTillingEnabled)
            professionSection.set("featureToggles.areaHarvestEnabled", profession.featureToggles.areaHarvestEnabled)
            professionSection.set("featureToggles.fishingInformationMode", profession.featureToggles.fishingInformationMode.name)
            professionSection.set("cycleStatistics.validActions", profession.cycleStatistics.validActions)
            professionSection.set("cycleStatistics.specialistActions", profession.cycleStatistics.specialistActions)
            professionSection.set("cycleStatistics.highQualityActions", profession.cycleStatistics.highQualityActions)
            professionSection.set("cycleStatistics.firstDiscoveries", profession.cycleStatistics.firstDiscoveries)
        } else {
            clearTypedProfessionFields(professionSection)
        }
        savePrestigeRecords(config, profession.prestigeRecords)

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

            if (professionSection.getInt("schemaVersion", -1) != PROFESSION_SCHEMA_VERSION) {
                throw IllegalStateException("Unsupported profession schema for player $playerUuid")
            }

            val professionId = professionSection.getString("profession") ?: return null
            val profession = Profession.fromId(professionId) ?: return null

            val currentExp = professionSection.getLong("currentExp", 0L)
            val lastUpdated = professionSection.getLong("lastUpdated", System.currentTimeMillis())
            val bossBarDisplayModeId = professionSection.getString("bossBarDisplayMode")
            val bossBarDisplayMode = BossBarDisplayMode.fromId(bossBarDisplayModeId ?: BossBarDisplayMode.SHORT.id)
                ?: throw IllegalStateException("Unknown bossBarDisplayMode '$bossBarDisplayModeId' for player $playerUuid")
            val levelUpNotificationEnabled = professionSection.getBoolean("levelUpNotificationEnabled", true)
            val effectiveBossBarEnabled = bossBarDisplayMode.visible

            val acquiredSkills = professionSection.getStringList("acquiredSkills").toMutableSet()
            val prestigeSkills = professionSection.getStringList("prestigeSkills").toMutableSet()
            val activeSkillId = professionSection.getString("activeSkillId")
            val skillSwitchModeId = professionSection.getString("skillSwitchMode") ?: SkillSwitchMode.MENU_ONLY.id
            val skillSwitchMode = SkillSwitchMode.fromId(skillSwitchModeId)
                ?: throw IllegalStateException("Unknown skillSwitchMode '$skillSwitchModeId' for player $playerUuid")
            val skillActivationStates = loadSkillActivationStates(professionSection)

            val featureToggles = if (profession.usesTypedAbilityAdapter) {
                loadFeatureToggles(professionSection, profession)
            } else {
                ProfessionFeatureToggles.defaultsFor(profession)
            }
            val cycleStatistics = ProfessionCycleStatistics(
                validActions = professionSection.getLong("cycleStatistics.validActions", 0L),
                specialistActions = professionSection.getLong("cycleStatistics.specialistActions", 0L),
                highQualityActions = professionSection.getLong("cycleStatistics.highQualityActions", 0L),
                firstDiscoveries = professionSection.getLong("cycleStatistics.firstDiscoveries", 0L)
            )
            val prestigeRecords = loadPrestigeRecords(config)

            PlayerProfession(
                playerUuid,
                profession,
                acquiredSkills,
                currentExp,
                lastUpdated,
                effectiveBossBarEnabled,
                bossBarDisplayMode,
                levelUpNotificationEnabled,
                prestigeSkills,
                activeSkillId,
                skillSwitchMode,
                skillActivationStates,
                featureToggles,
                cycleStatistics,
                prestigeRecords
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

    private fun clearLegacyProfessionFields(section: org.bukkit.configuration.ConfigurationSection) {
        listOf(
            "acquiredSkills",
            "prestigeSkills",
            "activeSkillId",
            "skillSwitchMode",
            "skillActivationStates",
            "bossBarEnabled"
        ).forEach { section.set(it, null) }
    }

    private fun clearTypedProfessionFields(section: org.bukkit.configuration.ConfigurationSection) {
        listOf(
            "specializationId",
            "featureToggles",
            "cycleStatistics",
            "bossBarEnabled"
        ).forEach { section.set(it, null) }
    }

    private fun loadSkillActivationStates(
        section: org.bukkit.configuration.ConfigurationSection
    ): MutableMap<String, Boolean> = mutableMapOf<String, Boolean>().also { states ->
        section.getConfigurationSection("skillActivationStates")?.getKeys(false)?.forEach { skillId ->
            states[skillId] = section.getBoolean("skillActivationStates.$skillId", true)
        }
    }

    private fun loadFeatureToggles(
        section: org.bukkit.configuration.ConfigurationSection,
        profession: Profession
    ): ProfessionFeatureToggles {
        val defaults = ProfessionFeatureToggles.defaultsFor(profession)
        val informationModeName = section.getString(
            "featureToggles.fishingInformationMode",
            defaults.fishingInformationMode.name
        ) ?: defaults.fishingInformationMode.name
        val informationMode = FishingInformationMode.entries.firstOrNull { it.name == informationModeName }
            ?: throw IllegalStateException("Unknown fishingInformationMode '$informationModeName'")
        return ProfessionFeatureToggles(
            batchProcessingEnabled = section.getBoolean("featureToggles.batchProcessingEnabled", defaults.batchProcessingEnabled),
            leafCleanupEnabled = section.getBoolean("featureToggles.leafCleanupEnabled", defaults.leafCleanupEnabled),
            automaticReplantEnabled = section.getBoolean("featureToggles.automaticReplantEnabled", defaults.automaticReplantEnabled),
            areaTillingEnabled = section.getBoolean("featureToggles.areaTillingEnabled", defaults.areaTillingEnabled),
            areaHarvestEnabled = section.getBoolean("featureToggles.areaHarvestEnabled", defaults.areaHarvestEnabled),
            fishingInformationMode = informationMode
        )
    }

    private fun savePrestigeRecords(config: YamlConfiguration, records: List<ProfessionPrestigeRecord>) {
        if (records.isEmpty()) return
        config.set("rank.professionPrestige", null)
        records.forEachIndexed { index, record ->
            val path = "rank.professionPrestige.$index"
            config.set("$path.professionId", record.professionId)
            config.set("$path.specializationId", record.specializationId)
            config.set("$path.completedAtEpochMillis", record.completedAtEpochMillis)
            config.set("$path.cycleNumber", record.cycleNumber)
            config.set("$path.representativeStatistic", record.representativeStatistic)
        }
    }

    private fun loadPrestigeRecords(
        section: org.bukkit.configuration.ConfigurationSection
    ): MutableList<ProfessionPrestigeRecord> = section
        .getConfigurationSection("rank.professionPrestige")
        ?.getKeys(false)
        ?.sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
        ?.map { index ->
            val path = "rank.professionPrestige.$index"
            ProfessionPrestigeRecord(
                professionId = requireNotNull(section.getString("$path.professionId")),
                specializationId = section.getString("$path.specializationId"),
                completedAtEpochMillis = section.getLong("$path.completedAtEpochMillis"),
                cycleNumber = section.getInt("$path.cycleNumber"),
                representativeStatistic = section.getLong("$path.representativeStatistic")
            )
        }
        ?.toMutableList()
        ?: mutableListOf()
}
