package jp.awabi2048.cccontent.features.rank.skill

import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.rank.profession.PlayerProfession
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * 能動スキルの管理・切り替えを行うマネージャー
 */
object ActiveSkillManager {

    /**
     * プレイヤーの能動スキルを次にローテーションする
     * @return 新しくアクティブになったスキルID（nullの場合は能動スキルを所持していない）
     */
    fun rotateActiveSkill(player: Player): String? {
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return null

        val activeSkills = ActiveSkillIdentifier.getPlayerActiveSkills(player)
        if (activeSkills.isEmpty()) {
            return null
        }

        // 現在アクティブなスキルを探す
        val currentActiveId = profession.activeSkillId
        val nextSkillId = if (currentActiveId == null) {
            // 未設定の場合は最初のスキルを選択
            activeSkills.first()
        } else {
            // 現在のスキルの次を選択（循環）
            val currentIndex = activeSkills.indexOf(currentActiveId)
            if (currentIndex == -1) {
                // 現在のスキルが取得リストにない場合（取得解除されたなど）は最初に戻る
                activeSkills.first()
            } else {
                val nextIndex = (currentIndex + 1) % activeSkills.size
                activeSkills[nextIndex]
            }
        }

        // アクティブスキルを更新
        profession.activeSkillId = nextSkillId
        CCContent.rankManager.savePlayerProfession(player.uniqueId)

        // プレイヤーに通知
        notifySkillSwitch(player, nextSkillId, profession)

        return nextSkillId
    }

    /**
     * 切替様式を次にローテーションする
     * @return 新しい切替様式
     */
    fun rotateSwitchMode(player: Player): SkillSwitchMode {
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return SkillSwitchMode.MENU_ONLY

        val currentMode = profession.skillSwitchMode
        val modes = SkillSwitchMode.values()
        val currentIndex = modes.indexOf(currentMode)
        val nextIndex = (currentIndex + 1) % modes.size
        val nextMode = modes[nextIndex]

        profession.skillSwitchMode = nextMode
        CCContent.rankManager.savePlayerProfession(player.uniqueId)

        // プレイヤーに通知
        notifyModeSwitch(player, nextMode)

        return nextMode
    }

    /**
     * 指定されたスキルIDが現在アクティブかどうかチェック
     */
    fun isSkillActive(player: Player, skillId: String): Boolean {
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return false

        return profession.activeSkillId == skillId
    }

    /**
     * プレイヤーが現在アクティブなスキルを持っているか
     */
    fun hasActiveSkill(player: Player): Boolean {
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return false

        return profession.activeSkillId != null
    }

    /**
     * 現在アクティブなスキルIDを取得
     */
    fun getCurrentActiveSkillId(player: Player): String? {
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return null

        return profession.activeSkillId
    }

    /**
     * 指定されたスキルIDが現在アクティブかどうか（playerUuid版）
     */
    fun isActiveSkillById(playerUuid: UUID, skillId: String): Boolean {
        val profession = CCContent.rankManager.getPlayerProfession(playerUuid)
            ?: return false

        val currentActive = profession.activeSkillId
        if (currentActive != null) {
            return currentActive == skillId
        }

        val activeSkills = ActiveSkillIdentifier.getPlayerActiveSkills(profession)
        if (activeSkills.isEmpty()) {
            return false
        }

        val autoSelected = activeSkills.first()
        profession.activeSkillId = autoSelected
        CCContent.rankManager.savePlayerProfession(playerUuid)

        return autoSelected == skillId
    }

    /**
     * スキル切替通知
     */
    private fun notifySkillSwitch(player: Player, skillId: String, profession: PlayerProfession) {
        val skillTree = SkillTreeRegistry.getSkillTree(profession.profession)
        val skillNode = skillTree?.getSkill(skillId)
        val skillName = skillNode?.let {
            CCContent.languageManager.getMessage(it.nameKey)
        } ?: skillId

        player.sendMessage(
            Component.text("アクティブスキルを変更しました: ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(skillName).color(NamedTextColor.YELLOW))
        )
    }

    /**
     * 切替様式変更通知
     */
    private fun notifyModeSwitch(player: Player, mode: SkillSwitchMode) {
        player.sendMessage(
            Component.text("スキル切替様式を変更しました: ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(mode.displayName).color(NamedTextColor.YELLOW))
        )
    }

    /**
     * モード切替ボタン用アイテムを作成
     */
    fun createModeSwitchButton(player: Player): ItemStack {
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
        val activeSkills = profession?.let { ActiveSkillIdentifier.getPlayerActiveSkills(it) } ?: emptyList()
        val currentActiveId = profession?.activeSkillId
        val currentMode = profession?.skillSwitchMode ?: SkillSwitchMode.MENU_ONLY

        val button = ItemStack(Material.MAGENTA_GLAZED_TERRACOTTA)
        val meta = button.itemMeta

        // 名前
        meta.displayName(
            Component.text("モード切替")
                .color(NamedTextColor.LIGHT_PURPLE)
        )

        // Lore
        val lore = mutableListOf<Component>()

        // アクティブスキル表示
        if (activeSkills.isEmpty()) {
            lore.add(Component.text("取得している能動スキルがありません")
                .color(NamedTextColor.GRAY))
        } else {
            val skillTree = profession?.profession?.let {
                SkillTreeRegistry.getSkillTree(it)
            }

            lore.add(Component.text("アクティブスキル:")
                .color(NamedTextColor.GRAY))

            for (skillId in activeSkills) {
                val skillNode = skillTree?.getSkill(skillId)
                val skillName = skillNode?.let {
                    CCContent.languageManager.getMessage(it.nameKey)
                } ?: skillId

                val isActive = skillId == currentActiveId
                val prefix = if (isActive) "▶ " else "  "
                val color = if (isActive) NamedTextColor.GREEN else NamedTextColor.DARK_GRAY

                lore.add(Component.text("$prefix$skillName").color(color))
            }

            lore.add(Component.empty())
            lore.add(Component.text("切替様式: ${currentMode.displayName}")
                .color(NamedTextColor.YELLOW))
        }

        lore.add(Component.empty())
        lore.add(Component.text("左クリック: スキル切替")
            .color(NamedTextColor.AQUA))
        lore.add(Component.text("右クリック: 様式変更")
            .color(NamedTextColor.AQUA))

        meta.lore(lore)
        button.itemMeta = meta

        return button
    }
}
