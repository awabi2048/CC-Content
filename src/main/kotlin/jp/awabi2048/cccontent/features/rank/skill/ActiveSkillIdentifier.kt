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
}
