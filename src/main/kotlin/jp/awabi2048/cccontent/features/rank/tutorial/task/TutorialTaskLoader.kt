package jp.awabi2048.cccontent.features.rank.tutorial.task

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * YAML ファイルから チュートリアルタスクの要件を読み込むローダー
 */
class TutorialTaskLoader {
    
    /** ロードされた要件キャッシュ */
    private val requirementCache: MutableMap<String, TaskRequirement> = mutableMapOf()
    
    /**
     * YAML ファイルからタスク要件を読み込む
     * @param file tutorial-tasks.yml ファイル
     */
    fun loadRequirements(file: File) {
        if (!file.exists()) {
            return
        }
        
        try {
            val config = YamlConfiguration.loadConfiguration(file)
            val tutorialRanksSection = config.getConfigurationSection("tutorial_ranks") ?: return
            
            for (rankId in tutorialRanksSection.getKeys(false)) {
                val rankSection = tutorialRanksSection.getConfigurationSection(rankId) ?: continue
                val requirementsSection = rankSection.getConfigurationSection("requirements") ?: continue
                
                val requirement = TaskRequirement(
                    playTimeMin = requirementsSection.getInt("play_time_min", 0),
                    mobKills = requirementsSection.getConfigurationSection("kill_mobs")?.let { section ->
                        section.getKeys(false).associateWith { section.getInt(it, 0) }
                    } ?: emptyMap(),
                    blockMines = requirementsSection.getConfigurationSection("mine_blocks")?.let { section ->
                        section.getKeys(false).associateWith { section.getInt(it, 0) }
                    } ?: emptyMap(),
                    vanillaExp = requirementsSection.getLong("vanilla_exp", 0L),
                    itemsRequired = requirementsSection.getConfigurationSection("items")?.let { section ->
                        section.getKeys(false).associateWith { section.getInt(it, 0) }
                    } ?: emptyMap(),
                    bossKills = requirementsSection.getConfigurationSection("kill_boss")?.let { section ->
                        section.getKeys(false).associateWith { section.getInt(it, 0) }
                    } ?: emptyMap()
                )
                
                requirementCache[rankId.uppercase()] = requirement
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 指定されたランクの要件を取得
     * @param rankId ランクID（大文字）
     * @return タスク要件、見つからない場合は空の要件
     */
    fun getRequirement(rankId: String): TaskRequirement {
        return requirementCache[rankId.uppercase()] ?: TaskRequirement()
    }
    
    /**
     * すべてのキャッシュをクリア
     */
    fun clearCache() {
        requirementCache.clear()
    }
}
