package jp.awabi2048.cccontent.features.rank.skill

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.rank.profession.PlayerProfession
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry
import org.bukkit.entity.Player

/**
 * 能動スキルの識別・管理を行うクラス
 */
object ActiveSkillIdentifier {

    /**
     * スキルエフェクトが能動スキルかどうか判定
     */
    fun isActiveSkillEffect(effectType: String): Boolean {
        val handler = SkillEffectRegistry.getHandler(effectType) ?: return false
        return handler.isActiveSkill()
    }

    /**
     * スキルがFキー切替対象かどうか判定
     * 能動スキルまたは効果を持つスキルが対象
     */
    fun isToggleableSkill(skillId: String, profession: Profession): Boolean {
        val skillTree = SkillTreeRegistry.getSkillTree(profession) ?: return false
        val skillNode = skillTree.getSkill(skillId) ?: return false
        val effect = skillNode.effect ?: return false

        // 能動スキルは対象
        if (isActiveSkillEffect(effect.type)) return true

        // 効果を持つスキルも対象
        return effect.type.isNotEmpty()
    }

    /**
     * プレイヤーが取得している能動スキルの一覧を取得
     * @return スキルIDのリスト
     */
    fun getPlayerActiveSkills(player: Player): List<String> {
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return emptyList()

        return getPlayerActiveSkills(profession)
    }

    /**
     * プレイヤーが取得している能動スキルの一覧を取得
     * @return スキルIDのリスト
     */
    fun getPlayerActiveSkills(profession: PlayerProfession): List<String> {
        val skillTree = SkillTreeRegistry.getSkillTree(profession.profession)
            ?: return emptyList()

        return profession.acquiredSkills.filter { skillId: String ->
            val skillNode = skillTree.getSkill(skillId)
            skillNode?.effect?.let { effect ->
                isActiveSkillEffect(effect.type)
            } ?: false
        }.toList()
    }

    /**
     * Fキー切替対象スキルを取得（effect.typeごとに最優先1件のみ）
     * 優先順: depth(深い方優先) > strength(高い方優先) > skillId(辞書順)
     * @return スキルIDのリスト
     */
    fun getToggleableSkillsForFKey(profession: PlayerProfession): List<String> {
        val skillTree = SkillTreeRegistry.getSkillTree(profession.profession)
            ?: return emptyList()

        // 効果を持つ習得済みスキルを抽出
        val skillsWithEffect = profession.acquiredSkills.mapNotNull { skillId ->
            val skillNode = skillTree.getSkill(skillId) ?: return@mapNotNull null
            val effect = skillNode.effect ?: return@mapNotNull null
            if (effect.type.isEmpty()) return@mapNotNull null

            val handler = SkillEffectRegistry.getHandler(effect.type) ?: return@mapNotNull null
            val depth = SkillDepthCalculator.calculateDepth(skillId, skillTree)
            val strength = handler.calculateStrength(effect)

            ToggleableSkillEntry(skillId, effect.type, depth, strength)
        }

        // effect.typeごとにグループ化し、最優先1件のみを抽出
        return skillsWithEffect
            .groupBy { it.effectType }
            .mapValues { (_, entries) ->
                entries.sortedWith(
                    compareByDescending<ToggleableSkillEntry> { it.depth }
                        .thenByDescending { it.strength }
                        .thenBy { it.skillId }
                ).first()
            }
            .values
            .sortedBy { it.skillId }
            .map { it.skillId }
    }

    /**
     * Fキー切替対象スキルが1つ以上あるか
     */
    fun hasAnyToggleableSkill(player: Player): Boolean {
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return false
        return getToggleableSkillsForFKey(profession).isNotEmpty()
    }

    /**
     * 指定されたスキルIDが能動スキルかどうか判定
     */
    fun isActiveSkill(skillId: String, profession: Profession): Boolean {
        val skillTree = SkillTreeRegistry.getSkillTree(profession)
            ?: return false

        val skillNode = skillTree.getSkill(skillId)
        return skillNode?.effect?.let { effect ->
            isActiveSkillEffect(effect.type)
        } ?: false
    }

    /**
     * プレイヤーが能動スキルを1つ以上取得しているか
     */
    fun hasAnyActiveSkill(player: Player): Boolean {
        return getPlayerActiveSkills(player).isNotEmpty()
    }

    /**
     * プレイヤーが能動スキルを1つ以上取得しているか
     */
    fun hasAnyActiveSkill(profession: PlayerProfession): Boolean {
        return getPlayerActiveSkills(profession).isNotEmpty()
    }

    /**
     * Fキー切替対象スキルの情報を保持するデータクラス
     */
    private data class ToggleableSkillEntry(
        val skillId: String,
        val effectType: String,
        val depth: Int,
        val strength: Double
    )
}
