package jp.awabi2048.cccontent.features.rank.skill

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.rank.RankReleasePolicy
import jp.awabi2048.cccontent.features.rank.profession.PlayerProfession
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * 能動スキルの管理・切り替えを行うマネージャー
 */
object ActiveSkillManager {

    private val forceAlwaysOnSkillIds: Set<String> = setOf(
        "wind_gust"
    )

    fun isSkillForceAlwaysOn(skillId: String): Boolean {
        return skillId in forceAlwaysOnSkillIds
    }

    /**
     * プレイヤーの選択スキルを次にローテーションする（Fキー対象）
     * effect.typeごとに最優先1件のみを対象とする
     * @return 新しく選択されたスキルID（nullの場合は対象スキルを所持していない）
     */
    fun rotateActiveSkill(player: Player): String? {
        if (!RankReleasePolicy.canUseSkills(player)) return null
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return null

        // Fキー対象スキルを取得（effect.typeごとに最優先1件のみ）
        val toggleableSkills = ActiveSkillIdentifier.getToggleableSkillsForFKey(profession)
        if (toggleableSkills.isEmpty()) {
            return null
        }

        // 現在選択中のスキルを探す
        val currentActiveId = profession.activeSkillId
        val nextSkillId = if (currentActiveId == null) {
            // 未設定の場合は最初のスキルを選択
            toggleableSkills.first()
        } else {
            // 現在のスキルの次を選択（循環）
            val currentIndex = toggleableSkills.indexOf(currentActiveId)
            if (currentIndex == -1) {
                // 現在のスキルが取得リストにない場合（取得解除されたなど）は最初に戻る
                toggleableSkills.first()
            } else {
                val nextIndex = (currentIndex + 1) % toggleableSkills.size
                toggleableSkills[nextIndex]
            }
        }

        // 選択スキルを更新
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
        if (!RankReleasePolicy.canUseSkills(player)) return SkillSwitchMode.MENU_ONLY
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
        if (!RankReleasePolicy.canUseSkills(player)) return false
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return false

        return profession.activeSkillId == skillId
    }

    /**
     * プレイヤーが現在アクティブなスキルを持っているか
     */
    fun hasActiveSkill(player: Player): Boolean {
        if (!RankReleasePolicy.canUseSkills(player)) return false
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return false

        return profession.activeSkillId != null
    }

    /**
     * 現在アクティブなスキルIDを取得
     */
    fun getCurrentActiveSkillId(player: Player): String? {
        if (!RankReleasePolicy.canUseSkills(player)) return null
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return null

        return profession.activeSkillId
    }

    /**
     * 指定されたスキルIDが現在選択中かどうか（playerUuid版）
     */
    fun isActiveSkillById(playerUuid: UUID, skillId: String): Boolean {
        if (!RankReleasePolicy.canUseSkills(playerUuid)) return false
        val profession = CCContent.rankManager.getPlayerProfession(playerUuid)
            ?: return false

        val currentActive = profession.activeSkillId
        if (currentActive != null) {
            return currentActive == skillId
        }

        val toggleableSkills = ActiveSkillIdentifier.getToggleableSkillsForFKey(profession)
        if (toggleableSkills.isEmpty()) {
            return false
        }

        val autoSelected = toggleableSkills.first()
        profession.activeSkillId = autoSelected
        CCContent.rankManager.savePlayerProfession(playerUuid)

        return autoSelected == skillId
    }

    /**
     * 現在選択中スキルの発動ON/OFFを切り替える
     * @return トグル結果（true=ON, false=OFF, null=切替不可または対象なし）
     */
    fun toggleCurrentSkillActivation(player: Player): Boolean? {
        if (!RankReleasePolicy.canUseSkills(player)) return null
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return null

        val currentSkillId = profession.activeSkillId ?: return null
        if (!isSkillToggleable(player, currentSkillId)) {
            return null
        }

        // トグル実行
        val newState = profession.toggleSkillActivation(currentSkillId)
        CCContent.rankManager.savePlayerProfession(player.uniqueId)

        // キャッシュを再構築
        SkillEffectEngine.rebuildCache(
            player.uniqueId,
            profession.acquiredSkills,
            profession.profession,
            profession.prestigeSkills,
            profession.skillActivationStates
        )

        // プレイヤーに通知
        notifyActivationToggle(player, currentSkillId, newState, profession)

        return newState
    }

    /**
     * 指定スキルが切替可能かどうか判定
     */
    fun isSkillToggleable(player: Player, skillId: String): Boolean {
        if (!RankReleasePolicy.canUseSkills(player)) return false
        if (isSkillForceAlwaysOn(skillId)) {
            return false
        }

        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return false

        val skillTree = SkillTreeRegistry.getSkillTree(profession.profession) ?: return false
        val skillNode = skillTree.getSkill(skillId) ?: return false

        // activationToggleable が false なら切替不可
        if (!skillNode.activationToggleable) {
            return false
        }

        // 手動発動スキルは常時ON（切替不可）
        val effect = skillNode.effect ?: return true
        val handler = SkillEffectRegistry.getHandler(effect.type) ?: return true
        return handler.getTriggerType() != ActiveTriggerType.MANUAL_SHIFT_RIGHT_CLICK
    }

    /**
     * スキルの発動状態を取得（ON/OFF）
     * 切替不可スキルは常にtrue
     */
    fun isSkillActivationEnabled(player: Player, skillId: String): Boolean {
        if (!RankReleasePolicy.canUseSkills(player)) return false
        val profession = CCContent.rankManager.getPlayerProfession(player.uniqueId)
            ?: return true

        // 切替不可スキルは常にON
        if (!isSkillToggleable(player, skillId)) {
            return true
        }

        return profession.isSkillActivationEnabled(skillId)
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

        // ON/OFF状態表示（切替不可スキルは非表示）
        val isToggleable = isSkillToggleable(player, skillId)
        val stateSuffix = if (isToggleable) {
            val isEnabled = profession.isSkillActivationEnabled(skillId)
            val stateText = if (isEnabled) "ON" else "OFF"
            val stateColor = if (isEnabled) NamedTextColor.GREEN else NamedTextColor.RED
            Component.text(" [").color(NamedTextColor.GRAY)
                .append(Component.text(stateText).color(stateColor))
                .append(Component.text("]").color(NamedTextColor.GRAY))
        } else {
            Component.empty()
        }

        player.sendActionBar(
            Component.text("選択中: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(skillName).color(NamedTextColor.YELLOW))
                .append(stateSuffix)
        )
    }

    /**
     * 発動ON/OFF切替通知
     */
    private fun notifyActivationToggle(player: Player, skillId: String, enabled: Boolean, profession: PlayerProfession) {
        val skillTree = SkillTreeRegistry.getSkillTree(profession.profession)
        val skillNode = skillTree?.getSkill(skillId)
        val skillName = skillNode?.let {
            CCContent.languageManager.getMessage(it.nameKey)
        } ?: skillId

        val stateText = if (enabled) "ON" else "OFF"
        val stateColor = if (enabled) NamedTextColor.GREEN else NamedTextColor.RED

        player.sendActionBar(
            Component.text(skillName)
                .color(NamedTextColor.YELLOW)
                .append(Component.text(": ").color(NamedTextColor.GRAY))
                .append(Component.text(stateText).color(stateColor))
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
        val toggleableSkills = profession?.let { ActiveSkillIdentifier.getToggleableSkillsForFKey(it) } ?: emptyList()
        val currentActiveId = profession?.activeSkillId
        val currentMode = profession?.skillSwitchMode ?: SkillSwitchMode.MENU_ONLY

        val button = ItemStack(Material.MAGENTA_GLAZED_TERRACOTTA)
        val meta = button.itemMeta

        // 名前
        meta.displayName(
            Component.text(CCContent.languageManager.getMessage("active_skill.selector.display"))
                .color(NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false)
        )

        // Lore
        val lore = mutableListOf<GuiLoreLine>()

        // 切替対象スキル表示
        if (toggleableSkills.isEmpty()) {
            lore.add(GuiLoreLine.Text(CCContent.languageManager.getMessage("active_skill.selector.no_targets")))
        } else {
            val skillTree = profession?.profession?.let {
                SkillTreeRegistry.getSkillTree(it)
            }

            lore.add(GuiLoreLine.Text(CCContent.languageManager.getMessage("active_skill.selector.targets")))

            for (skillId in toggleableSkills) {
                val skillNode = skillTree?.getSkill(skillId)
                val skillName = skillNode?.let {
                    CCContent.languageManager.getMessage(it.nameKey)
                } ?: skillId

                val isSelected = skillId == currentActiveId
                val isEnabled = profession?.isSkillActivationEnabled(skillId) ?: true
                val isToggleable = profession != null && isSkillToggleable(player, skillId)

                val color = when {
                    isSelected && isEnabled -> "§a"
                    isSelected && !isEnabled -> "§c"
                    else -> "§8"
                }

                val label = if (isToggleable) {
                    "$skillName (${CCContent.languageManager.getMessage(if (isEnabled) "active_skill.selector.enabled" else "active_skill.selector.disabled")})"
                } else skillName
                lore.add(GuiLoreLine.Option(label, isSelected, color, "§8"))
            }

            lore.add(GuiLoreLine.Spacer)
            lore.add(GuiLoreLine.Data(CCContent.languageManager.getMessage("active_skill.selector.mode"), currentMode.displayName, "§e"))
        }

        lore.add(GuiLoreLine.Spacer)
        lore.add(GuiLoreLine.Action(CCContent.languageManager.getMessage("active_skill.selector.left_click"), CCContent.languageManager.getMessage("active_skill.selector.select_action")))
        lore.add(GuiLoreLine.Action(CCContent.languageManager.getMessage("active_skill.selector.right_click"), CCContent.languageManager.getMessage("active_skill.selector.mode_action")))

        meta.lore(CCSystem.getAPI().getLoreService().render(GuiLoreSpec.Rich(lore, GuiLoreFrame.NONE)))
        button.itemMeta = meta

        return button
    }
}
