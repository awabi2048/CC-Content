package jp.awabi2048.cccontent.features.rank.profession.skilltree

import jp.awabi2048.cccontent.features.rank.profession.SkillNode
import jp.awabi2048.cccontent.features.rank.profession.SkillTree
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * YAML設定ファイルから動的にスキルツリーを読み込む実装
 */
class ConfigBasedSkillTree(
    private val professionId: String,
    configFile: File
) : SkillTree {

    private val skills: MutableMap<String, SkillNode> = mutableMapOf()
    private val childrenByParent: MutableMap<String, List<String>> = mutableMapOf()
    private var startSkillId: String = "skill0"
    
    init {
        loadFromConfig(configFile)
    }
    
    /**
     * YAML設定ファイルからスキルツリーを読み込む
     */
    private fun loadFromConfig(configFile: File) {
        try {
            val config = YamlConfiguration.loadConfiguration(configFile)
            val skillsSection = config.getConfigurationSection("skills") ?: return
            
            // 各スキルを読み込む
            skillsSection.getKeys(false).forEach { skillId ->
                val skillSection = skillsSection.getConfigurationSection(skillId) ?: return@forEach
                
                val skill = SkillNode(
                    skillId = skillId,
                    nameKey = skillSection.getString("nameKey", "skill.$professionId.$skillId.name") ?: "",
                    descriptionKey = skillSection.getString("descriptionKey", "skill.$professionId.$skillId.desc") ?: "",
                    requiredExp = skillSection.getLong("requiredExp", 0L),
                    icon = skillSection.getString("icon"),
                    prerequisites = skillSection.getStringList("prerequisites").toList()
                )

                skills[skillId] = skill
            }

            validateSkillGraph()
            buildChildrenMap()
            
            // 開始スキルを特定（前提条件がないスキル）
            startSkillId = skills.entries.firstOrNull { (_, skill) ->
                skill.prerequisites.isEmpty()
            }?.key ?: "skill0"
            
        } catch (e: Exception) {
            throw IllegalStateException("スキルツリー読み込みに失敗しました: $professionId", e)
        }
    }
    
    override fun getProfessionId(): String = professionId
    
    override fun getSkill(skillId: String): SkillNode? = skills[skillId]
    
    override fun getAllSkills(): Map<String, SkillNode> = skills.toMap()

    override fun getStartSkillId(): String = startSkillId

    override fun getChildren(skillId: String): List<String> {
        return childrenByParent[skillId] ?: emptyList()
    }

    override fun getAvailableSkills(acquiredSkills: Set<String>, currentExp: Long): List<String> {
        return skills.values
            .filter { it.canAcquire(acquiredSkills, currentExp) }
            .filter { skill ->
                // 前提条件を満たしていることを確認
                skill.prerequisites.all { it in acquiredSkills }
            }
            .map { it.skillId }
    }

    private fun validateSkillGraph() {
        skills.values.forEach { skill ->
            if (skill.prerequisites.size > 2) {
                throw IllegalArgumentException("$professionId/${skill.skillId}: prerequisites は最大2つまでです")
            }
            skill.prerequisites.forEach { prerequisite ->
                if (!skills.containsKey(prerequisite)) {
                    throw IllegalArgumentException("$professionId/${skill.skillId}: 存在しない前提スキル '$prerequisite'")
                }
            }
        }

        val childCountByParent = mutableMapOf<String, Int>()
        skills.values.forEach { skill ->
            skill.prerequisites.forEach { prerequisite ->
                val nextCount = (childCountByParent[prerequisite] ?: 0) + 1
                childCountByParent[prerequisite] = nextCount
                if (nextCount > 2) {
                    throw IllegalArgumentException("$professionId/$prerequisite: 子スキルは最大2つまでです")
                }
            }
        }
    }

    private fun buildChildrenMap() {
        childrenByParent.clear()
        skills.keys.forEach { childrenByParent[it] = emptyList() }

        val mutableMap = skills.keys.associateWith { mutableListOf<String>() }.toMutableMap()
        skills.values.forEach { skill ->
            skill.prerequisites.forEach { prerequisite ->
                mutableMap.getValue(prerequisite).add(skill.skillId)
            }
        }

        mutableMap.forEach { (skillId, children) ->
            childrenByParent[skillId] = children.sorted()
        }
    }
}
