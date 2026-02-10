package jp.awabi2048.cccontent.features.rank.profession.skilltree

import jp.awabi2048.cccontent.features.rank.profession.SkillNode
import jp.awabi2048.cccontent.features.rank.profession.SkillTree
import jp.awabi2048.cccontent.features.rank.skill.EvaluationMode
import jp.awabi2048.cccontent.features.rank.skill.SkillEffect
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import kotlin.math.roundToLong

class ConfigBasedSkillTree(
    private val professionId: String,
    configFile: File
) : SkillTree {

    private val skills: MutableMap<String, SkillNode> = mutableMapOf()
    private val childrenByParent: MutableMap<String, List<String>> = mutableMapOf()
    private val parentsByChild: MutableMap<String, List<String>> = mutableMapOf()

    private var startSkillId: String = "skill0"
    private var levelInitialExp: Long = 100L
    private var levelBase: Double = 1.2
    private var maxLevel: Int = 100
    private var requiredTotalExpByLevel: List<Long> = listOf(0L)

    init {
        loadFromConfig(configFile)
    }

    private fun loadFromConfig(configFile: File) {
        try {
            val config = YamlConfiguration.loadConfiguration(configFile)
            val skillsSection = config.getConfigurationSection("skills")
                ?: throw IllegalArgumentException("$professionId: skills セクションが見つかりません")

            val levelSection = skillsSection.getConfigurationSection("settings.level")
                ?: throw IllegalArgumentException("$professionId: skills.settings.level セクションが見つかりません")

            levelInitialExp = levelSection.getLong("initialExp", 100L).coerceAtLeast(1L)
            levelBase = levelSection.getDouble("base", 1.2).coerceAtLeast(1.0)
            maxLevel = levelSection.getInt("maxLevel", 100).coerceAtLeast(1)

            skills.clear()
            skillsSection.getKeys(false)
                .filter { it != "settings" }
                .forEach { skillId ->
                    val skillSection = skillsSection.getConfigurationSection(skillId) ?: return@forEach

                    val effect = parseEffect(skillSection)

                    val skill = SkillNode(
                        skillId = skillId,
                        nameKey = skillSection.getString("nameKey", "skill.$professionId.$skillId.name") ?: "",
                        descriptionKey = skillSection.getString("descriptionKey", "skill.$professionId.$skillId.description") ?: "",
                        requiredLevel = skillSection.getInt("requiredLevel", 1).coerceAtLeast(1),
                        icon = skillSection.getString("icon"),
                        children = skillSection.getStringList("children").distinct(),
                        effect = effect,
                        exclusiveBranch = skillSection.getBoolean("exclusiveBranch", true)
                    )

                    skills[skillId] = skill
                }

            if (skills.isEmpty()) {
                throw IllegalArgumentException("$professionId: スキル定義が空です")
            }

            rebuildGraphIndexes()
            validateSkillGraph()
            rebuildSkillNodesWithParents()

            startSkillId = skills.entries
                .firstOrNull { (skillId, _) -> (parentsByChild[skillId] ?: emptyList()).isEmpty() }
                ?.key
                ?: throw IllegalArgumentException("$professionId: 開始スキルが見つかりません")

            buildLevelThresholds()
        } catch (e: Exception) {
            throw IllegalStateException("スキルツリー読み込みに失敗しました: $professionId", e)
        }
    }

    private fun parseEffect(skillSection: org.bukkit.configuration.ConfigurationSection): SkillEffect? {
        val effectSection = skillSection.getConfigurationSection("effect") ?: return null

        val type = effectSection.getString("type") ?: return null
        val evaluationModeStr = effectSection.getString("evaluationMode", "CACHED") ?: "CACHED"
        val evaluationMode = try {
            EvaluationMode.valueOf(evaluationModeStr.uppercase())
        } catch (e: IllegalArgumentException) {
            EvaluationMode.CACHED
        }

        val paramsSection = effectSection.getConfigurationSection("params")
        val params = mutableMapOf<String, Any>()

        if (paramsSection != null) {
            paramsSection.getKeys(false).forEach { key ->
                val value = paramsSection.get(key)
                if (value != null) {
                    params[key] = value
                }
            }
        }

        return SkillEffect(type, params, evaluationMode)
    }

    private fun rebuildGraphIndexes() {
        childrenByParent.clear()
        parentsByChild.clear()

        skills.values.forEach { skill ->
            childrenByParent[skill.skillId] = skill.children.sorted()
            parentsByChild.putIfAbsent(skill.skillId, emptyList())
        }

        val mutableParents = skills.keys.associateWith { mutableListOf<String>() }.toMutableMap()
        skills.values.forEach { skill ->
            skill.children.forEach { childId ->
                val parents = mutableParents[childId] ?: return@forEach
                parents += skill.skillId
            }
        }

        mutableParents.forEach { (skillId, parents) ->
            parentsByChild[skillId] = parents.sorted()
        }
    }

    private fun rebuildSkillNodesWithParents() {
        val rebuilt = skills.mapValues { (_, skill) ->
            skill.copy(prerequisites = parentsByChild[skill.skillId] ?: emptyList())
        }
        skills.clear()
        skills.putAll(rebuilt)
    }

    private fun validateSkillGraph() {
        skills.values.forEach { skill ->
            if (skill.children.size > 2) {
                throw IllegalArgumentException("$professionId/${skill.skillId}: 子スキルは最大2つまでです")
            }
            skill.children.forEach { childId ->
                if (!skills.containsKey(childId)) {
                    throw IllegalArgumentException("$professionId/${skill.skillId}: 存在しない子スキル '$childId'")
                }
            }
        }

        parentsByChild.forEach { (skillId, parents) ->
            if (parents.size > 2) {
                throw IllegalArgumentException("$professionId/$skillId: 親スキルは最大2つまでです")
            }
        }

        val rootCount = skills.keys.count { (parentsByChild[it] ?: emptyList()).isEmpty() }
        if (rootCount == 0) {
            throw IllegalArgumentException("$professionId: 開始スキル候補がありません")
        }
    }

    private fun buildLevelThresholds() {
        val thresholds = MutableList(maxLevel + 1) { 0L }
        var cumulative = 0L
        for (level in 2..maxLevel) {
            val required = getExpToNextLevel(level - 1)
            cumulative = saturatingAdd(cumulative, required)
            thresholds[level] = cumulative
        }
        requiredTotalExpByLevel = thresholds
    }

    private fun saturatingAdd(a: Long, b: Long): Long {
        if (Long.MAX_VALUE - a < b) {
            return Long.MAX_VALUE
        }
        return a + b
    }

    override fun getProfessionId(): String = professionId

    override fun getSkill(skillId: String): SkillNode? = skills[skillId]

    override fun getAllSkills(): Map<String, SkillNode> = skills.toMap()

    override fun getStartSkillId(): String = startSkillId

    override fun getChildren(skillId: String): List<String> {
        return childrenByParent[skillId] ?: emptyList()
    }

    override fun getParents(skillId: String): List<String> {
        return parentsByChild[skillId] ?: emptyList()
    }

    override fun getAvailableSkills(acquiredSkills: Set<String>, currentLevel: Int): List<String> {
        return skills.keys
            .sorted()
            .filter { canAcquire(it, acquiredSkills, currentLevel) }
    }

    override fun getLevelInitialExp(): Long = levelInitialExp

    override fun getLevelBase(): Double = levelBase

    override fun getMaxLevel(): Int = maxLevel

    override fun getRequiredTotalExpForLevel(level: Int): Long {
        val safeLevel = level.coerceIn(1, maxLevel)
        return requiredTotalExpByLevel[safeLevel]
    }

    override fun calculateLevelByExp(totalExp: Long): Int {
        val exp = totalExp.coerceAtLeast(0L)
        var low = 1
        var high = maxLevel
        var answer = 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val threshold = requiredTotalExpByLevel[mid]
            if (exp >= threshold) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return answer
    }

    override fun getExpToNextLevel(level: Int): Long {
        val safeLevel = level.coerceAtLeast(1)
        val raw = levelInitialExp * Math.pow(levelBase, (safeLevel - 1).toDouble())
        return raw.roundToLong().coerceAtLeast(1L)
    }
}
