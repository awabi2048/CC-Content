package jp.awabi2048.cccontent.features.rank.command

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.listener.ProfessionSelector
import jp.awabi2048.cccontent.features.rank.listener.TutorialInventoryHelper
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import jp.awabi2048.cccontent.features.rank.tutorial.task.TaskProgress
import jp.awabi2048.cccontent.features.rank.tutorial.task.TaskRequirement
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.ProfessionType
import jp.awabi2048.cccontent.features.rank.profession.SkillNode
import jp.awabi2048.cccontent.features.rank.profession.SkillTreeRegistry
import jp.awabi2048.cccontent.features.rank.profession.BossBarDisplayMode
import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
import jp.awabi2048.cccontent.features.rank.gui.ConfirmationDialog
import jp.awabi2048.cccontent.features.rank.skill.ActiveSkillManager
import jp.awabi2048.cccontent.features.rank.skill.ActiveSkillIdentifier
import jp.awabi2048.cccontent.features.rank.skill.SkillEffectEngine
import jp.awabi2048.cccontent.features.rank.skill.handlers.AttackReachBoostHandler
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.format.TextDecoration

/**
 * ランクシステムのデバッグコマンド実装
 */
class RankCommand(
    private val rankManager: RankManager,
    private val messageProvider: MessageProvider,
    private var taskLoader: jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskLoader? = null,
    private var taskChecker: jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskChecker? = null,
    private val translator: jp.awabi2048.cccontent.features.rank.tutorial.task.EntityBlockTranslator? = null
) : CommandExecutor, TabCompleter, Listener, ProfessionSelector {

    fun setTutorialTaskSystem(
        loader: jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskLoader?,
        checker: jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskChecker?
    ) {
        taskLoader = loader
        taskChecker = checker
    }

    fun openRankMenu(viewer: Player): Boolean {
        val tutorialRank = rankManager.getTutorialRank(viewer.uniqueId)
        return if (tutorialRank == TutorialRank.ATTAINER) {
            openProfessionMainMenu(viewer)
        } else {
            openTutorialRankMenu(viewer)
        }
    }
    
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        // 権限確認
        if (!sender.hasPermission("cc-content.admin")) {
            sender.sendMessage("§c権限がありません")
            return false
        }
        
        if (args.isEmpty()) {
            sendUsage(sender)
            return false
        }
        
        return when (args[0].lowercase()) {
            "add-exp" -> handleAddExp(sender, args)
            "set-prof-level" -> handleSetProfLevel(sender, args)
            "set" -> handleSet(sender, args)
            "reset" -> handleReset(sender, args)
            else -> {
                sendUsage(sender)
                false
            }
        }
    }
    
    private fun handleAddExp(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 3) {
            sender.sendMessage("§c使用法: /rank add-exp <player> <amount>")
            return false
        }
        
        val player = Bukkit.getPlayer(args[1])
        if (player == null) {
            sender.sendMessage("§cプレイヤーが見つかりません: ${args[1]}")
            return false
        }
        
        val amount = args[2].toLongOrNull()
        if (amount == null || amount <= 0) {
            sender.sendMessage("§c経験値は正の整数で指定してください")
            return false
        }
        
        val success = rankManager.addProfessionExp(player.uniqueId, amount)

        if (success) {
            sender.sendMessage("§a${player.name} に ${amount} 職業EXP を追加しました")
        } else {
            sender.sendMessage("§c${player.name} は職業を選択していません")
        }
        
        return success
    }

    private fun handleSetProfLevel(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 3) {
            sender.sendMessage("§c使用法: /rank set-prof-level <player> <level>")
            return false
        }

        val player = Bukkit.getPlayer(args[1])
        if (player == null) {
            sender.sendMessage("§cプレイヤーが見つかりません: ${args[1]}")
            return false
        }

        val level = args[2].toIntOrNull()
        if (level == null || level <= 0) {
            sender.sendMessage("§cレベルは1以上の整数で指定してください")
            return false
        }

        val success = rankManager.setProfessionLevel(player.uniqueId, level)
        if (success) {
            val appliedLevel = rankManager.getCurrentProfessionLevel(player.uniqueId)
            sender.sendMessage("§a${player.name} の職業レベルを ${appliedLevel} に設定しました")
        } else {
            sender.sendMessage("§c${player.name} は職業を選択していません")
        }

        return success
    }

    private fun handleSet(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます")
            return false
        }

        if (args.size < 2) {
            sender.sendMessage("§c使用法: /rank set <ランク名>")
            return false
        }

        val targetName = args[1]
        val uuid = sender.uniqueId

        val tutorialRank = TutorialRank.entries.firstOrNull { it.name.equals(targetName, ignoreCase = true) }
        if (tutorialRank != null) {
            rankManager.resetProfession(uuid)
            rankManager.setTutorialRank(uuid, tutorialRank)

            val tutorial = rankManager.getPlayerTutorial(uuid)
            tutorial.taskProgress = TaskProgress(uuid, tutorialRank.name)
            tutorial.lastUpdated = System.currentTimeMillis()
            tutorial.lastPlayTime = System.currentTimeMillis()
            rankManager.saveData()
            refreshSkillEffectCache(sender)

            sender.sendMessage("§aランクを ${tutorialRank.name} に設定しました")
            return true
        }

        val profession = Profession.entries.firstOrNull {
            it.id.equals(targetName, ignoreCase = true) || it.name.equals(targetName, ignoreCase = true)
        }
        if (profession != null) {
            rankManager.setTutorialRank(uuid, TutorialRank.ATTAINER)
            val success = if (rankManager.hasProfession(uuid)) {
                val current = rankManager.getPlayerProfession(uuid)?.profession
                if (current == profession) {
                    true
                } else {
                    rankManager.changeProfession(uuid, profession)
                }
            } else {
                rankManager.selectProfession(uuid, profession)
            }

            if (!success) {
                sender.sendMessage("§c職業の設定に失敗しました")
                return false
            }

            refreshSkillEffectCache(sender)

            sender.sendMessage("§aランクを職業 ${profession.id} に設定しました")
            return true
        }

        sender.sendMessage("§c無効なランク名です: $targetName")
        sender.sendMessage(
            "§7使用可能: ${TutorialRank.entries.joinToString(", ") { it.name }} / ${Profession.entries.joinToString(", ") { it.id }}"
        )
        return false
    }

    private fun refreshSkillEffectCache(player: Player) {
        val uuid = player.uniqueId
        val playerProf = rankManager.getPlayerProfession(uuid)

        if (playerProf == null) {
            SkillEffectEngine.clearCache(uuid)
            AttackReachBoostHandler.removeModifier(uuid)
            return
        }

        SkillEffectEngine.rebuildCache(
            uuid,
            playerProf.acquiredSkills,
            playerProf.profession,
            playerProf.prestigeSkills,
            playerProf.skillActivationStates
        )
    }
    
    private fun handleRankUp(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage("§c使用法: /rank rankup <player>")
            return false
        }
        
        val player = Bukkit.getPlayer(args[1])
        if (player == null) {
            sender.sendMessage("§cプレイヤーが見つかりません: ${args[1]}")
            return false
        }
        
        val currentRank = rankManager.getTutorialRank(player.uniqueId)
        val nextRank = TutorialRank.values().getOrNull(currentRank.ordinal + 1)
        
        if (nextRank == null) {
            sender.sendMessage("§c${player.name} は既に最高ランクです")
            return false
        }
        
        rankManager.setTutorialRank(player.uniqueId, nextRank)
        sender.sendMessage("§a${player.name} を $nextRank にランクアップしました")
        player.sendMessage("§aランクアップ！§6${nextRank.name}§aになりました")
        
        return true
    }
    
    private fun handleProfession(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 3) {
            sender.sendMessage("§c使用法: /rank profession <player> <profession_id>")
            sender.sendMessage("§6職業ID: lumberjack, brewer, miner")
            return false
        }
        
        val player = Bukkit.getPlayer(args[1])
        if (player == null) {
            sender.sendMessage("§cプレイヤーが見つかりません: ${args[1]}")
            return false
        }
        
        val professionId = args[2]
        val profession = Profession.fromId(professionId)
        if (profession == null) {
            sender.sendMessage("§c無効な職業ID: $professionId")
            return false
        }
        
        val success = if (rankManager.hasProfession(player.uniqueId)) {
            rankManager.changeProfession(player.uniqueId, profession)
        } else {
            rankManager.selectProfession(player.uniqueId, profession)
        }
        
        if (success) {
            sender.sendMessage("§a${player.name} の職業を変更/選択しました: ${profession.id}")
        } else {
            sender.sendMessage("§c職業の変更/選択に失敗しました")
        }
        
        return success
    }
    
    private fun handleSkill(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size == 1) {
            if (sender !is Player) {
                sender.sendMessage(messageProvider.getMessage("rank.skill.player_only"))
                return false
            }
            return openSkillTreeGui(sender)
        }

        sender.sendMessage(messageProvider.getMessage("rank.skill.usage"))
        return false
    }
    
    private fun handleReset(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage("§c使用法: /rank reset <player>")
            return false
        }
        
        val player = Bukkit.getPlayer(args[1])
        if (player == null) {
            sender.sendMessage("§cプレイヤーが見つかりません: ${args[1]}")
            return false
        }
        
        val uuid = player.uniqueId
        rankManager.resetProfession(uuid)

        val resetRank = resolveLowestDefinedTutorialRank()
        val tutorial = rankManager.getPlayerTutorial(uuid)
        tutorial.currentRank = resetRank
        tutorial.taskProgress = TaskProgress(uuid, tutorial.currentRank.name)
        tutorial.lastPlayTime = System.currentTimeMillis()
        tutorial.lastUpdated = System.currentTimeMillis()
        rankManager.saveData()

        sender.sendMessage("§a${player.name} を ${resetRank.name} にリセットしました")
        return true
    }

    private fun handleBossbar(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます")
            return false
        }

        if (args.size < 2) {
            val currentState = if (rankManager.isProfessionBossBarEnabled(sender.uniqueId)) "有効" else "無効"
            sender.sendMessage("§a職業経験値ボスバー: §f$currentState")
            sender.sendMessage("§7使用法: /rank bossbar <on|off>")
            return true
        }

        val enabled = when (args[1].lowercase()) {
            "on", "true", "enable" -> true
            "off", "false", "disable" -> false
            else -> {
                sender.sendMessage("§c使用法: /rank bossbar <on|off>")
                return false
            }
        }

        rankManager.setProfessionBossBarEnabled(sender.uniqueId, enabled)
        val stateText = if (enabled) "有効" else "無効"
        sender.sendMessage("§a職業経験値ボスバーを${stateText}にしました")
        return true
    }
    
    private fun handleInfo(sender: CommandSender, args: Array<out String>): Boolean {
        val targetPlayer = if (args.size >= 2) {
            Bukkit.getPlayer(args[1])
        } else if (sender is Player) {
            sender
        } else {
            sender.sendMessage("§cプレイヤーを指定してください")
            return false
        }

        if (targetPlayer == null) {
            sender.sendMessage("§cプレイヤーが見つかりません")
            return false
        }

        val uuid = targetPlayer.uniqueId
        val tutorial = rankManager.getPlayerTutorial(uuid)
        val currentRank = rankManager.getPlayerRank(uuid)

        sender.sendMessage("§6=== ${targetPlayer.name} のランク情報 ===")
        sender.sendMessage("§a現在のランク: §e$currentRank")

        if (currentRank == "ATTAINER") {
            sender.sendMessage("§7職業を選択してください")
        } else if (rankManager.getPlayerProfession(uuid) != null) {
            val profession = rankManager.getPlayerProfession(uuid)
            val currentLevel = rankManager.getCurrentProfessionLevel(uuid)
            sender.sendMessage("§a職業レベル: $currentLevel")
            sender.sendMessage("§a習得スキル: ${profession?.acquiredSkills?.joinToString(", ") ?: "なし"}")
            sender.sendMessage("§a職業経験値: ${profession?.currentExp ?: 0}")
        } else {
            val nextRank = tutorial.getNextRank()
            if (nextRank != null) {
                sender.sendMessage("§7次のランク: §f${nextRank.name}")
            }
        }

        return true
    }

    /**
     * 職業メインメニューを開く
     */
    fun openProfessionMainMenu(viewer: Player): Boolean {
        val playerProfession = rankManager.getPlayerProfession(viewer.uniqueId)
        if (playerProfession == null) {
            // 職業未選択の場合は職業選択GUIを開く
            return openProfessionSelectionGui(viewer)
        }

        val holder = ProfessionMainMenuGuiHolder()
        val inventory = Bukkit.createInventory(
            holder,
            45,
            messageProvider.getMessage("gui.profession.selection.main_menu_title")
        )
        holder.backingInventory = inventory

        renderProfessionMainMenu(inventory, viewer, playerProfession)
        viewer.openInventory(inventory)
        viewer.playSound(viewer.location, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f)
        return true
    }

    private fun openTutorialRankMenu(viewer: Player): Boolean {
        val holder = TutorialRankMenuGuiHolder(TUTORIAL_MENU_PAGE_FIRST)
        val inventory = Bukkit.createInventory(holder, 45, "§8チュートリアルランク")
        holder.backingInventory = inventory

        renderTutorialRankMenu(inventory, viewer, holder.page)
        viewer.openInventory(inventory)
        viewer.playSound(viewer.location, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f)
        return true
    }

    private fun renderTutorialRankMenu(inventory: Inventory, viewer: Player, page: Int) {
        val headerFooterPane = createBackgroundItem(Material.BLACK_STAINED_GLASS_PANE)
        val basePane = createBackgroundItem(Material.GRAY_STAINED_GLASS_PANE)
        val acquiredRoutePane = createBackgroundItem(Material.LIME_STAINED_GLASS_PANE)
        val lockedRoutePane = createBackgroundItem(Material.ORANGE_STAINED_GLASS_PANE)

        for (slot in 0 until inventory.size) {
            inventory.setItem(slot, basePane)
        }
        for (slot in 0..8) {
            inventory.setItem(slot, headerFooterPane)
        }
        for (slot in 36..44) {
            inventory.setItem(slot, headerFooterPane)
        }
        val tutorial = rankManager.getPlayerTutorial(viewer.uniqueId)
        val currentRank = tutorial.currentRank
        val routeSlots = if (page == TUTORIAL_MENU_PAGE_SECOND) {
            TUTORIAL_MENU_ROUTE_SLOTS_PAGE2
        } else {
            TUTORIAL_MENU_ROUTE_SLOTS_PAGE1
        }
        val routeLeadingRank = if (page == TUTORIAL_MENU_PAGE_SECOND) {
            TUTORIAL_MENU_ROUTE_LEADING_RANK_PAGE2
        } else {
            TUTORIAL_MENU_ROUTE_LEADING_RANK_PAGE1
        }
        routeSlots.forEach { slot ->
            val leadingRank = routeLeadingRank[slot]
            val routeItem = if (leadingRank != null && leadingRank.level <= currentRank.level) {
                acquiredRoutePane
            } else {
                lockedRoutePane
            }
            inventory.setItem(slot, routeItem)
        }

        val entries = when (page) {
            TUTORIAL_MENU_PAGE_FIRST -> listOf(
                TutorialRank.NEWBIE to 19,
                TutorialRank.VISITOR to 22,
                TutorialRank.PIONEER to 25
            )
            TUTORIAL_MENU_PAGE_SECOND -> listOf(
                TutorialRank.ADVENTURER to 19,
                TutorialRank.ATTAINER to 22
            )
            else -> emptyList()
        }

        entries.forEach { (rank, slot) ->
            inventory.setItem(slot, createTutorialRankItem(viewer, tutorial, rank, currentRank))
        }

        inventory.setItem(TUTORIAL_MENU_PLAYER_SLOT, createTutorialPlayerInfoItem(viewer))
    }

    private fun createTutorialRankItem(
        viewer: Player,
        tutorial: jp.awabi2048.cccontent.features.rank.tutorial.PlayerTutorialRank,
        rank: TutorialRank,
        currentRank: TutorialRank
    ): ItemStack {
        val loader = taskLoader
        val requirementRank = TutorialRank.values().getOrNull(rank.ordinal - 1)
        val requirement = requirementRank?.let { reqRank -> loader?.getRequirement(reqRank.name) } ?: TaskRequirement()
        val mode = when {
            requirementRank == null -> TutorialRankTaskDisplayMode.NONE
            requirementRank.level < currentRank.level -> TutorialRankTaskDisplayMode.COMPLETED
            requirementRank == currentRank -> TutorialRankTaskDisplayMode.CURRENT
            else -> TutorialRankTaskDisplayMode.FUTURE
        }

        val description = messageProvider.getMessage("tutorial_rank.${rank.name.lowercase()}.description")

        val lore = mutableListOf<Component>()
        lore += toComponent(BAR)
        lore += toComponent("§f§l| §7$description")
        lore += toComponent(BAR)
        if (loader == null) {
            lore += toComponent("§f§l| §c${messageProvider.getMessage("tutorial_rank.task.system_not_initialized")}")
        } else {
            lore += buildTutorialRankTaskLore(viewer, tutorial.taskProgress, requirement, mode)
        }
        lore += toComponent(BAR)

        val icon = resolveTutorialRankIcon(loader?.getRankIcon(rank.name), rank)
        val name = messageProvider.getMessage("tutorial_rank.${rank.name.lowercase()}.name")
        return createGuiItem(icon, toComponent("§6§l$name"), lore)
    }

    private fun buildTutorialRankTaskLore(
        viewer: Player,
        progress: TaskProgress,
        requirement: TaskRequirement,
        mode: TutorialRankTaskDisplayMode
    ): List<Component> {
        if (mode == TutorialRankTaskDisplayMode.NONE) {
            return listOf(toComponent("§f§l| §a${messageProvider.getMessage("tutorial_rank.task.no_requirement")}"))
        }

        if (requirement.isEmpty()) {
            return listOf(toComponent("§f§l| §a${messageProvider.getMessage("tutorial_rank.task.no_requirement")}"))
        }

        val lore = mutableListOf<Component>()
        val statusLine = when (mode) {
            TutorialRankTaskDisplayMode.COMPLETED -> "§a§n${messageProvider.getMessage("tutorial_rank.task.status.completed")}"
            TutorialRankTaskDisplayMode.CURRENT -> null
            TutorialRankTaskDisplayMode.FUTURE -> "§c§n${messageProvider.getMessage("tutorial_rank.task.status.future")}"
            TutorialRankTaskDisplayMode.NONE -> null
        }
        statusLine?.let { lore += toComponent(it) }

        val defeatSuffix = messageProvider.getMessage("tutorial_rank.task.suffix.defeat")
        val collectSuffix = messageProvider.getMessage("tutorial_rank.task.suffix.collect")
        val playTimeLabel = messageProvider.getMessage("tutorial_rank.task.label.play_time")
        val expLabel = messageProvider.getMessage("tutorial_rank.task.label.exp")

        if (requirement.playTimeMin > 0) {
            val required = requirement.playTimeMin.toLong()
            val current = when (mode) {
                TutorialRankTaskDisplayMode.COMPLETED -> required
                TutorialRankTaskDisplayMode.CURRENT -> minOf(progress.playTime, required)
                TutorialRankTaskDisplayMode.FUTURE -> 0L
                TutorialRankTaskDisplayMode.NONE -> 0L
            }
            val done = current >= required
            val lineColor = if (done) "§a" else "§c"
            val numberColor = progressColorCode(current, required)
            lore += toComponent("${lineColor}§l| §7${playTimeLabel} (§r${numberColor}${formatPlayTime(current)}§7/${formatPlayTime(required)}§7)")
        }

        requirement.mobKills.forEach { (mobType, required) ->
            val current = when (mode) {
                TutorialRankTaskDisplayMode.COMPLETED -> required
                TutorialRankTaskDisplayMode.CURRENT -> minOf(progress.getMobKillCount(mobType), required)
                TutorialRankTaskDisplayMode.FUTURE -> 0
                TutorialRankTaskDisplayMode.NONE -> 0
            }
            val done = current >= required
            val targetName = resolveEntityNameComponent(mobType)
            lore += buildTutorialTargetLine(targetName, done, current, required, defeatSuffix)
        }

        requirement.bossKills.forEach { (bossType, required) ->
            val current = when (mode) {
                TutorialRankTaskDisplayMode.COMPLETED -> required
                TutorialRankTaskDisplayMode.CURRENT -> minOf(progress.getBossKillCount(bossType), required)
                TutorialRankTaskDisplayMode.FUTURE -> 0
                TutorialRankTaskDisplayMode.NONE -> 0
            }
            val done = current >= required
            val targetName = resolveBossNameComponent(bossType)
            lore += buildTutorialTargetLine(targetName, done, current, required, defeatSuffix)
        }

        requirement.blockMines.forEach { (blockType, required) ->
            val current = when (mode) {
                TutorialRankTaskDisplayMode.COMPLETED -> required
                TutorialRankTaskDisplayMode.CURRENT -> minOf(progress.getBlockMineCount(blockType), required)
                TutorialRankTaskDisplayMode.FUTURE -> 0
                TutorialRankTaskDisplayMode.NONE -> 0
            }
            val done = current >= required
            val targetName = resolveBlockNameComponent(blockType)
            lore += buildTutorialTargetLine(targetName, done, current, required, collectSuffix)
        }

        if (requirement.vanillaExp > 0) {
            val required = requirement.vanillaExp
            val current = when (mode) {
                TutorialRankTaskDisplayMode.COMPLETED -> required
                TutorialRankTaskDisplayMode.CURRENT -> minOf(progress.vanillaExp, required)
                TutorialRankTaskDisplayMode.FUTURE -> 0L
                TutorialRankTaskDisplayMode.NONE -> 0L
            }
            val done = current >= required
            val lineColor = if (done) "§a" else "§c"
            val numberColor = progressColorCode(current, required)
            lore += toComponent("${lineColor}§l| §7${expLabel} (§r${numberColor}${current}§7/${required}§7)")
        }

        requirement.itemsRequired.forEach { (material, required) ->
            val current = when (mode) {
                TutorialRankTaskDisplayMode.COMPLETED -> required
                TutorialRankTaskDisplayMode.CURRENT -> minOf(
                    TutorialInventoryHelper.countItemInInventory(viewer, material.uppercase()),
                    required
                )
                TutorialRankTaskDisplayMode.FUTURE -> 0
                TutorialRankTaskDisplayMode.NONE -> 0
            }
            val done = current >= required
            val targetName = resolveItemNameComponent(material)
            lore += buildTutorialTargetLine(targetName, done, current, required, collectSuffix)
        }

        return lore.take(TUTORIAL_RANK_TASK_LORE_LIMIT)
    }

    private fun buildTutorialTargetLine(
        nameComponent: Component,
        done: Boolean,
        current: Int,
        required: Int,
        suffix: String = ""
    ): Component {
        val contentColorCode = if (done) "§a" else "§c"
        val numberColor = progressColorCode(current.toLong(), required.toLong())
        return toComponent("${contentColorCode}§l| §r")
            .append(nameComponent.color(NamedTextColor.GRAY))
            .append(toComponent("§7${suffix} (§r${numberColor}${current}§7/${required}§7)"))
    }

    private fun resolveEntityNameComponent(entityType: String): Component {
        val translatorInstance = translator ?: return Component.text("???")
        return if (runCatching { org.bukkit.entity.EntityType.valueOf(entityType.uppercase()) }.isSuccess) {
            Component.translatable(translatorInstance.translateEntity(entityType))
        } else {
            Component.text("???")
        }
    }

    private fun resolveBossNameComponent(bossType: String): Component {
        val translatorInstance = translator ?: return Component.text("???")
        return if (runCatching { org.bukkit.entity.EntityType.valueOf(bossType.uppercase()) }.isSuccess) {
            Component.translatable(translatorInstance.translateBoss(bossType))
        } else {
            Component.text("???")
        }
    }

    private fun resolveBlockNameComponent(blockType: String): Component {
        val translatorInstance = translator ?: return Component.text("???")
        val material = Material.matchMaterial(blockType.uppercase())
        return if (material != null) {
            Component.translatable(translatorInstance.translateBlock(blockType))
        } else {
            Component.text("???")
        }
    }

    private fun resolveItemNameComponent(itemName: String): Component {
        val translatorInstance = translator ?: return Component.text("???")
        val material = Material.matchMaterial(itemName.uppercase())
        return if (material != null) {
            Component.translatable(translatorInstance.translateItem(itemName))
        } else {
            Component.text("???")
        }
    }

    private fun progressColorCode(current: Long, required: Long): String {
        if (required <= 0L) {
            return "§a"
        }
        val percent = ((current.toDouble() / required.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        return when {
            percent >= 100 -> "§a"
            percent >= 50 -> "§e"
            else -> "§c"
        }
    }

    private fun resolveTutorialRankIcon(iconName: String?, rank: TutorialRank): Material {
        val configured = iconName?.trim()?.uppercase()?.let { Material.matchMaterial(it) }
        if (configured != null) {
            return configured
        }
        return if (rank == TutorialRank.ATTAINER) Material.JACK_O_LANTERN else Material.CARVED_PUMPKIN
    }

    private fun createTutorialPlayerInfoItem(viewer: Player): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? org.bukkit.inventory.meta.SkullMeta
        if (meta != null) {
            meta.owningPlayer = viewer
            meta.displayName(withoutItalic(toComponent("§e${viewer.name}")))
            meta.lore(
                listOf(
                    withoutItalic(toComponent(BAR)),
                    withoutItalic(toComponent("§f§l| §7${messageProvider.getMessage("tutorial_rank.task.page_hint")}")),
                    withoutItalic(toComponent(BAR))
                )
            )
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            item.itemMeta = meta
        }
        return item
    }

    /**
     * 職業選択GUIを開く
     */
    override fun openProfessionSelectionGui(player: Player): Boolean {
        val holder = ProfessionSelectionGuiHolder()
        val inventory = Bukkit.createInventory(
            holder,
            54,
            messageProvider.getMessage("gui.profession.selection.title")
        )
        holder.backingInventory = inventory

        renderProfessionSelectionGui(inventory, player)
        player.openInventory(inventory)
        player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f)
        return true
    }

    private fun renderProfessionSelectionGui(inventory: Inventory, viewer: Player) {
        val headerFooterPane = createBackgroundItem(Material.BLACK_STAINED_GLASS_PANE)
        val basePane = createBackgroundItem(Material.GRAY_STAINED_GLASS_PANE)
        
        // ベース埋め
        for (slot in 0 until inventory.size) {
            inventory.setItem(slot, basePane)
        }
        
        // ヘッダーとフッター（黒）
        for (slot in 0..8) {
            inventory.setItem(slot, headerFooterPane)
        }
        for (slot in 45..53) {
            inventory.setItem(slot, headerFooterPane)
        }
        
        // プレイヤーアイコン（Row1, Column5 = slot 4）
        val playerHead = createPlayerHeadItem(viewer)
        inventory.setItem(4, playerHead)
        
        // ヒントアイコン（Row6, Column5 = slot 49）
        val hintLore = messageProvider.getMessageList("gui.profession.selection.hint.lore")
        val hintItem = createGuiItem(
            Material.BOOK,
            toComponent(messageProvider.getMessage("gui.profession.selection.hint.title")),
            hintLore.map { toComponent(it) }
        )
        inventory.setItem(49, hintItem)
        
        // 現在の職業を取得
        val currentProfession = rankManager.getPlayerProfession(viewer.uniqueId)?.profession
        
        // 職業をタイプ別に分類
        val professionsByType = Profession.values().groupBy { it.type }
        
        // Row3 (slots 20-24): GATHERING + CRAFTING
        val row3Professions = mutableListOf<Profession>()
        professionsByType[ProfessionType.GATHERING]?.let { row3Professions.addAll(it) }
        professionsByType[ProfessionType.CRAFTING]?.let { row3Professions.addAll(it) }
        
        val row3Slots = listOf(20, 21, 22, 23, 24)
        row3Professions.take(5).forEachIndexed { index, profession ->
            val slot = row3Slots.getOrNull(index) ?: return@forEachIndexed
            val isCurrent = profession == currentProfession
            val item = createProfessionItem(profession, isCurrent, currentProfession != null)
            inventory.setItem(slot, item)
        }
        
        // Row4 (slots 29-33): COMBAT + CREATIVE
        val row4Professions = mutableListOf<Profession>()
        professionsByType[ProfessionType.COMBAT]?.let { row4Professions.addAll(it) }
        professionsByType[ProfessionType.CREATIVE]?.let { row4Professions.addAll(it) }
        
        val row4Slots = listOf(29, 30, 31, 32, 33)
        row4Professions.take(5).forEachIndexed { index, profession ->
            val slot = row4Slots.getOrNull(index) ?: return@forEachIndexed
            val isCurrent = profession == currentProfession
            val item = createProfessionItem(profession, isCurrent, currentProfession != null)
            inventory.setItem(slot, item)
        }
    }
    
    private fun createPlayerHeadItem(player: Player): ItemStack {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as? org.bukkit.inventory.meta.SkullMeta
        meta?.let {
            it.owningPlayer = player
            it.displayName(toComponent("§a§l${player.name}"))
            val playerHeadLore = messageProvider.getMessageList("gui.profession.selection.player_head.lore")
            it.lore(playerHeadLore.map { lore -> toComponent(lore) })
            head.itemMeta = it
        }
        return head
    }
    
    private fun createProfessionItem(profession: Profession, isCurrent: Boolean, hasProfession: Boolean): ItemStack {
        val skillTree = SkillTreeRegistry.getSkillTree(profession)
        val startSkill = skillTree?.getStartSkillId()?.let { skillTree.getSkill(it) }
        val icon = startSkill?.icon?.let { Material.matchMaterial(it.uppercase()) } ?: Material.BOOK
        
        val professionName = messageProvider.getProfessionName(profession)
        val professionDesc = messageProvider.getProfessionDescription(profession)
        val typeName = when (profession.type) {
            ProfessionType.GATHERING -> messageProvider.getMessage("gui.profession.type.gathering")
            ProfessionType.CRAFTING -> messageProvider.getMessage("gui.profession.type.crafting")
            ProfessionType.COMBAT -> messageProvider.getMessage("gui.profession.type.combat")
            ProfessionType.CREATIVE -> messageProvider.getMessage("gui.profession.type.creative")
            ProfessionType.GENERAL -> messageProvider.getMessage("gui.profession.type.general")
        }
        
        return if (isCurrent) {
            // 現在の職業（選択済み）
            createGuiItem(
                icon,
                toComponent("${profession.displayColorCode}§l$professionName ${messageProvider.getMessage("gui.profession.selection.profession_item.selected")}"),
                listOf(
                    toComponent("§7$professionDesc"),
                    toComponent(""),
                    toComponent(messageProvider.getMessage("gui.profession.selection.profession_item.type_label", "type" to typeName)),
                    toComponent(""),
                    toComponent(messageProvider.getMessage("gui.profession.selection.profession_item.current_profession"))
                )
            )
        } else if (hasProfession) {
            // 他の職業（既に職業を持っている場合は選択不可）
            val alreadySelectedLore = messageProvider.getMessageList("gui.profession.selection.profession_item.already_selected_lore")
            val loreList = mutableListOf<Component>()
            loreList.add(toComponent("§7$professionDesc"))
            loreList.add(toComponent(""))
            loreList.add(toComponent(messageProvider.getMessage("gui.profession.selection.profession_item.type_label", "type" to typeName)))
            loreList.add(toComponent(""))
            alreadySelectedLore.forEach { lore ->
                loreList.add(toComponent(lore))
            }
            createGuiItem(
                Material.BARRIER,
                toComponent(messageProvider.getMessage("gui.profession.selection.profession_item.already_selected_title", "profession" to professionName)),
                loreList
            )
        } else {
            // 選択可能
            createGuiItem(
                icon,
                toComponent("${profession.displayColorCode}§l$professionName"),
                listOf(
                    toComponent("§7$professionDesc"),
                    toComponent(""),
                    toComponent(messageProvider.getMessage("gui.profession.selection.profession_item.type_label", "type" to typeName)),
                    toComponent(""),
                    toComponent(messageProvider.getMessage("gui.profession.selection.profession_item.click_to_select"))
                )
            )
        }
    }
    
    private fun showProfessionConfirmDialog(player: Player, profession: Profession) {
        val professionName = messageProvider.getProfessionName(profession)
        val professionDesc = messageProvider.getProfessionDescription(profession)
        
        ConfirmationDialog.show(
            player = player,
            title = withoutItalic(toComponent(messageProvider.getMessage("gui.profession.confirm_dialog.title"))),
            bodyMessage = withoutItalic(toComponent(messageProvider.getMessage("gui.profession.confirm_dialog.body", "profession" to professionName, "description" to professionDesc))),
            confirmText = withoutItalic(toComponent(messageProvider.getMessage("gui.profession.confirm_dialog.confirm_button"))),
            cancelText = withoutItalic(toComponent(messageProvider.getMessage("gui.profession.confirm_dialog.cancel_button"))),
            onConfirm = { target ->
                executeProfessionSelect(target, profession)
            }
        )
    }
    
    private fun executeProfessionSelect(player: Player, profession: Profession) {
        if (rankManager.hasProfession(player.uniqueId)) {
            player.sendMessage(messageProvider.getMessage("gui.profession.error.already_has_profession"))
            return
        }
        
        rankManager.selectProfession(player.uniqueId, profession)
        player.sendMessage(messageProvider.getMessage("message.profession_selected", "profession" to messageProvider.getProfessionName(profession)))
        player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
        
        // GUIを閉じる
        player.closeInventory()
    }

    private class ProfessionSelectionGuiHolder : InventoryHolder {
        lateinit var backingInventory: Inventory
        override fun getInventory(): Inventory = backingInventory
    }

    private fun renderProfessionMainMenu(
        inventory: Inventory,
        viewer: Player,
        playerProfession: jp.awabi2048.cccontent.features.rank.profession.PlayerProfession
    ) {
        val headerFooterPane = createBackgroundItem(Material.BLACK_STAINED_GLASS_PANE)
        val basePane = createBackgroundItem(Material.GRAY_STAINED_GLASS_PANE)

        for (slot in 0 until inventory.size) {
            inventory.setItem(slot, basePane)
        }
        for (slot in 0..8) {
            inventory.setItem(slot, headerFooterPane)
        }
        for (slot in 36..44) {
            inventory.setItem(slot, headerFooterPane)
        }

        val skillTreeLore = messageProvider.getMessageList("gui.skill.tree_button_lore")
        val skillTreeItem = createGuiItem(
            Material.OAK_SAPLING,
            toComponent(messageProvider.getMessage("gui.skill.tree_button")),
            listOf(toComponent(BAR)) + skillTreeLore.map { toComponent(it) } + listOf(toComponent(BAR))
        )
        inventory.setItem(MAIN_MENU_SKILL_TREE_SLOT, skillTreeItem)

        val currentLevel = rankManager.getCurrentProfessionLevel(viewer.uniqueId)
        val professionName = messageProvider.getProfessionName(playerProfession.profession)
        val professionDescription = messageProvider.getProfessionDescription(playerProfession.profession)
        
        // ボスバー側と同じ計算方法で経験値を表示
        val skillTree = SkillTreeRegistry.getSkillTree(playerProfession.profession)
        val currentExp = playerProfession.currentExp
        val requiredTotalExp = skillTree?.getRequiredTotalExpForLevel(currentLevel + 1) ?: 0L
        val previousLevelExp = skillTree?.getRequiredTotalExpForLevel(currentLevel) ?: 0L
        val currentLevelExp = currentExp - previousLevelExp
        val levelExp = if (currentLevel >= (skillTree?.getMaxLevel() ?: 50)) {
            currentLevelExp
        } else {
            requiredTotalExp - previousLevelExp
        }
        
        val professionIcon = getProfessionOverviewIcon(playerProfession.profession)
        
        val professionOverviewItem = createGuiItem(
            professionIcon,
            toComponent("§b【$professionName】"),
            listOf(
                toComponent(BAR),
                toComponent("§7$professionDescription"),
                toComponent(BAR),
                toComponent("§f§l| §7Lv. §e§l$currentLevel"),
                toComponent("§f§l| §7経験値 §a${String.format("%,d", currentLevelExp)}§7/${String.format("%,d", levelExp)}"),
                toComponent(BAR)
            )
        )
        inventory.setItem(MAIN_MENU_PROFESSION_OVERVIEW_SLOT, professionOverviewItem)

        val settingsLore = messageProvider.getMessageList("gui.skill.settings_button_lore")
        val settingsItem = createGuiItem(
            Material.COMPARATOR,
            toComponent(messageProvider.getMessage("gui.skill.settings_button")),
            listOf(toComponent(BAR)) + settingsLore.map { toComponent(it) } + listOf(toComponent(BAR))
        )
        inventory.setItem(MAIN_MENU_SETTINGS_SLOT, settingsItem)

        // プレイヤー情報アイコン (スロット40)
        val playerHead = ItemStack(Material.PLAYER_HEAD)
        val skullMeta = playerHead.itemMeta as? org.bukkit.inventory.meta.SkullMeta
        if (skullMeta != null) {
            skullMeta.owningPlayer = viewer
            skullMeta.displayName(withoutItalic(toComponent("§e${viewer.name}")))
            skullMeta.lore((listOf(toComponent(BAR)) + settingsLore.map { toComponent(it) } + listOf(toComponent(BAR))).map { withoutItalic(it) })
            skullMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            playerHead.itemMeta = skullMeta
        }
        inventory.setItem(MAIN_MENU_PLAYER_INFO_SLOT, playerHead)

        // 職業のヒントアイコン (スロット42) - モック実装
        val hintLoreMain = messageProvider.getMessageList("gui.skill.hint_button_lore")
        val hintItem = createGuiItem(
            Material.COMPASS,
            toComponent(messageProvider.getMessage("gui.skill.hint_button")),
            listOf(toComponent(BAR)) + hintLoreMain.map { toComponent(it) } + listOf(toComponent(BAR))
        )
        inventory.setItem(MAIN_MENU_HINT_SLOT, hintItem)

        // モード切替ボタン (スロット38)
        if (ActiveSkillIdentifier.hasAnyToggleableSkill(viewer)) {
            val modeSwitchItem = ActiveSkillManager.createModeSwitchButton(viewer)
            inventory.setItem(MAIN_MENU_MODE_SWITCH_SLOT, modeSwitchItem)
        } else {
            inventory.setItem(MAIN_MENU_MODE_SWITCH_SLOT, headerFooterPane)
        }
    }

    /**
     * 職業の概要アイコンのMaterialを取得
     */
    private fun getProfessionOverviewIcon(profession: Profession): Material {
        val skillTree = SkillTreeRegistry.getSkillTree(profession) ?: return Material.BARRIER
        
        // settings.overviewIconが設定されていればそれを使用、なければskill0のアイコンを使用
        val overviewIcon = skillTree.getOverviewIcon()
        if (overviewIcon != null) {
            val material = Material.matchMaterial(overviewIcon.uppercase())
            if (material != null) {
                return material
            }
        }
        
        // フォールバック: skill0のアイコンを使用
        val startSkill = skillTree.getSkill(skillTree.getStartSkillId())
        if (startSkill?.icon != null) {
            val material = Material.matchMaterial(startSkill.icon.uppercase())
            if (material != null) {
                return material
            }
        }
        
        return Material.BARRIER
    }

    /**
     * 次のレベルに必要な経験値を計算
     */
    private fun calculateRequiredExp(profession: Profession, currentLevel: Int): Long {
        val skillTree = SkillTreeRegistry.getSkillTree(profession) ?: return 100L
        return skillTree.getRequiredExpForLevel(currentLevel)
    }

    fun openSkillTreeDirect(viewer: Player): Boolean {
        return openSkillTreeGui(viewer)
    }

    private fun openSkillTreeGui(viewer: Player): Boolean {
        val playerProfession = rankManager.getPlayerProfession(viewer.uniqueId)
        if (playerProfession == null) {
            viewer.sendMessage(messageProvider.getMessage("rank.skill.gui.no_profession"))
            return false
        }

        val skillTree = SkillTreeRegistry.getSkillTree(playerProfession.profession)
        if (skillTree == null) {
            viewer.sendMessage(
                messageProvider.getMessage(
                    "rank.skill.gui.tree_not_found",
                    "profession" to messageProvider.getProfessionName(playerProfession.profession)
                )
            )
            return false
        }

        val selectedSkillId = skillTree.getStartSkillId()
        val selectedSkill = skillTree.getSkill(selectedSkillId)
        if (selectedSkill == null) {
            viewer.sendMessage(messageProvider.getMessage("rank.skill.gui.start_not_found"))
            return false
        }

        val currentLevel = rankManager.getCurrentProfessionLevel(viewer.uniqueId)
        val prestigeLevel = rankManager.getPrestigeLevel(viewer.uniqueId)
        val isMaxLevel = rankManager.isMaxProfessionLevel(viewer.uniqueId)
        val state = SkillTreeGuiState(
            profession = playerProfession.profession,
            selectedSkillId = selectedSkill.skillId,
            acquiredSkills = playerProfession.acquiredSkills.toSet(),
            currentLevel = currentLevel,
            currentExp = playerProfession.currentExp,
            prestigeSkills = playerProfession.prestigeSkills.toSet(),
            prestigeLevel = prestigeLevel,
            isMaxLevel = isMaxLevel
        )
        state.laneBySkillId[selectedSkill.skillId] = SkillTreeLane.CENTER
        val holder = SkillTreeGuiHolder(state)
        val inventory = Bukkit.createInventory(
            holder,
            45,
            messageProvider.getMessage(
                "rank.skill.gui.title",
                "profession" to messageProvider.getProfessionName(playerProfession.profession)
            )
        )
        holder.backingInventory = inventory

        renderSkillTreeGui(inventory, skillTree, state)
        viewer.openInventory(inventory)
        return true
    }

    private fun openProfessionSettingsMenu(viewer: Player): Boolean {
        val playerProfession = rankManager.getPlayerProfession(viewer.uniqueId) ?: return false
        val holder = ProfessionSettingsGuiHolder()
        val inventory = Bukkit.createInventory(
            holder,
            27,
            messageProvider.getMessage("gui.skill.settings_title")
        )
        holder.backingInventory = inventory
        renderProfessionSettingsMenu(inventory, playerProfession)
        viewer.openInventory(inventory)
        viewer.playSound(viewer.location, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f)
        return true
    }

    private fun renderProfessionSettingsMenu(
        inventory: Inventory,
        playerProfession: jp.awabi2048.cccontent.features.rank.profession.PlayerProfession
    ) {
        val headerFooterPane = createBackgroundItem(Material.BLACK_STAINED_GLASS_PANE)
        val basePane = createBackgroundItem(Material.GRAY_STAINED_GLASS_PANE)

        for (slot in 0 until inventory.size) {
            inventory.setItem(slot, basePane)
        }
        for (slot in 0..8) {
            inventory.setItem(slot, headerFooterPane)
        }
        for (slot in 18..26) {
            inventory.setItem(slot, headerFooterPane)
        }

        val bossBarMode = rankManager.getProfessionBossBarDisplayMode(playerProfession.playerUuid)
        val bossBarValueColor = when (bossBarMode) {
            BossBarDisplayMode.LONG -> "§a"
            BossBarDisplayMode.SHORT -> "§e"
            BossBarDisplayMode.HIDDEN -> "§c"
        }
        val bossBarItem = createGuiItem(
            Material.CLOCK,
            toComponent(messageProvider.getMessage("gui.skill.settings.bossbar.title")),
            listOf(
                toComponent(BAR),
                toComponent("§f§l| §7現在 $bossBarValueColor${bossBarMode.displayName}"),
                toComponent(messageProvider.getMessage("gui.skill.settings.bossbar.action")),
                toComponent(BAR)
            )
        )
        inventory.setItem(SETTINGS_MENU_BOSSBAR_SLOT, bossBarItem)

        val levelUpEnabled = rankManager.isLevelUpNotificationEnabled(playerProfession.playerUuid)
        val levelUpValue = if (levelUpEnabled) {
            "§a${messageProvider.getMessage("gui.skill.settings.level_up.enabled")}"
        } else {
            "§c${messageProvider.getMessage("gui.skill.settings.level_up.disabled")}"
        }
        val levelUpItem = createGuiItem(
            Material.BELL,
            toComponent(messageProvider.getMessage("gui.skill.settings.level_up.title")),
            listOf(
                toComponent(BAR),
                toComponent("§f§l| §7現在 $levelUpValue"),
                toComponent(messageProvider.getMessage("gui.skill.settings.level_up.action")),
                toComponent(BAR)
            )
        )
        inventory.setItem(SETTINGS_MENU_LEVELUP_NOTIFY_SLOT, levelUpItem)

        val backItem = createGuiItem(
            Material.REDSTONE,
            toComponent("§c戻る"),
            emptyList()
        )
        inventory.setItem(SETTINGS_MENU_BACK_SLOT, backItem)
    }

    private fun renderSkillTreeGui(inventory: Inventory, skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree, state: SkillTreeGuiState) {
        renderSkillTreeGuiLegacy(inventory, skillTree, state)
    }

    private fun renderSkillTreeGuiLegacy(inventory: Inventory, skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree, state: SkillTreeGuiState) {
        val selectedSkill = skillTree.getSkill(state.selectedSkillId) ?: return
        val childNodes = skillTree.getChildren(selectedSkill.skillId)
            .mapNotNull { skillTree.getSkill(it) }
            .take(2)
        val connectedAcquiredSkills = buildConnectedAcquiredSkills(skillTree, state.acquiredSkills)
        val selectedParentByChild = buildSelectedParentByChild(skillTree, connectedAcquiredSkills)
        val selectedParentSkillId = selectedParentByChild[selectedSkill.skillId] ?: getPreviousSkillId(selectedSkill)

        val layout = determineSkillTreeLayout(childNodes)
        val headerFooterPane = createBackgroundItem(Material.BLACK_STAINED_GLASS_PANE)
        val basePane = createBackgroundItem(Material.GRAY_STAINED_GLASS_PANE)
        val acquiredRoutePane = createBackgroundItem(Material.LIME_STAINED_GLASS_PANE)
        val lockedRoutePane = createBackgroundItem(Material.ORANGE_STAINED_GLASS_PANE)
        for (slot in 0 until inventory.size) {
            inventory.setItem(slot, basePane)
        }
        for (slot in 0..8) {
            inventory.setItem(slot, headerFooterPane)
        }
        for (slot in 36..44) {
            inventory.setItem(slot, headerFooterPane)
        }

        val routeToSelectedPane = if (selectedSkill.skillId in connectedAcquiredSkills) acquiredRoutePane else lockedRoutePane

        fun paintRoute(
            slots: List<Int>,
            pane: ItemStack,
            transitionSkillId: String? = null,
            transitionLane: SkillTreeLane = SkillTreeLane.CENTER,
            guideKey: String? = null,
            visible: Boolean = true
        ) {
            if (!visible) {
                return
            }
            val routeItem = if (transitionSkillId != null && guideKey != null) {
                createRouteGuideItem(pane, guideKey)
            } else {
                pane
            }
            slots.forEach { slot ->
                inventory.setItem(slot, routeItem)
                if (transitionSkillId != null) {
                    state.routeSlotToSkillId[slot] = RouteTransition(transitionSkillId, transitionLane)
                }
            }
        }

        state.slotToSkillId.clear()
        state.routeSlotToSkillId.clear()

        when (layout) {
            SkillTreeLayout.CHILD_ONE -> {
                state.currentSelectedLane = SkillTreeLane.CENTER
                state.laneBySkillId[selectedSkill.skillId] = SkillTreeLane.CENTER
                paintRoute(
                    CHILD_ONE_SELECTED_INCOMING_SLOTS,
                    routeToSelectedPane,
                    transitionSkillId = selectedParentSkillId,
                    transitionLane = SkillTreeLane.CENTER,
                    guideKey = "rank.skill.gui.route.prev",
                    visible = !isStartSkill(selectedSkill)
                )

                val child = childNodes.firstOrNull()
                if (child != null) {
                    val routeToChildPane = if (isAcquiredEdge(skillTree, selectedSkill.skillId, child.skillId, connectedAcquiredSkills, selectedParentByChild)) acquiredRoutePane else lockedRoutePane
                    val routeAfterChildPane = if (hasAcquiredOutgoingEdge(skillTree, child.skillId, connectedAcquiredSkills, selectedParentByChild)) acquiredRoutePane else lockedRoutePane
                    paintRoute(CHILD_ONE_TO_CHILD_SLOTS, routeToChildPane)
                    paintRoute(
                        CHILD_ONE_CHILD_OUTGOING_SLOTS,
                        routeAfterChildPane,
                        transitionSkillId = child.skillId,
                        transitionLane = SkillTreeLane.CENTER,
                        guideKey = "rank.skill.gui.route.next",
                        visible = !isEndSkill(skillTree, child)
                    )
                }

                setSkillNodeItem(inventory, SELECTED_SLOT_CHILD_ONE, selectedSkill, state, skillTree)
                child?.let { setSkillNodeItem(inventory, CHILD_SLOT_CHILD_ONE, it, state, skillTree) }
            }

            SkillTreeLayout.CHILD_TWO -> {
                state.currentSelectedLane = SkillTreeLane.CENTER
                state.laneBySkillId[selectedSkill.skillId] = SkillTreeLane.CENTER
                paintRoute(
                    CHILD_TWO_SELECTED_INCOMING_SLOTS,
                    routeToSelectedPane,
                    transitionSkillId = selectedParentSkillId,
                    transitionLane = SkillTreeLane.CENTER,
                    guideKey = "rank.skill.gui.route.prev",
                    visible = !isStartSkill(selectedSkill)
                )

                val routeFromSelectedToBranchPane = if (
                    hasAcquiredOutgoingEdge(skillTree, selectedSkill.skillId, connectedAcquiredSkills, selectedParentByChild)
                ) {
                    acquiredRoutePane
                } else {
                    lockedRoutePane
                }
                paintRoute(CHILD_TWO_SHARED_BRANCH_SLOTS, routeFromSelectedToBranchPane)

                val laneAssignedTop = childNodes.firstOrNull { state.laneBySkillId[it.skillId] == SkillTreeLane.TOP }
                val laneAssignedBottom = childNodes.firstOrNull { state.laneBySkillId[it.skillId] == SkillTreeLane.BOTTOM }
                val unassignedChildren = childNodes
                    .filter { it != laneAssignedTop && it != laneAssignedBottom }
                    .sortedBy { it.skillId }

                val topChild = laneAssignedTop ?: unassignedChildren.getOrNull(0)
                val bottomChild = laneAssignedBottom
                    ?: unassignedChildren.firstOrNull { it != topChild }
                    ?: unassignedChildren.getOrNull(1)

                if (topChild != null) {
                    state.laneBySkillId[topChild.skillId] = SkillTreeLane.TOP
                }
                if (bottomChild != null) {
                    state.laneBySkillId[bottomChild.skillId] = SkillTreeLane.BOTTOM
                }

                if (topChild != null) {
                    val routeToTopPane = if (isAcquiredEdge(skillTree, selectedSkill.skillId, topChild.skillId, connectedAcquiredSkills, selectedParentByChild)) acquiredRoutePane else lockedRoutePane
                    val routeAfterTopPane = if (hasAcquiredOutgoingEdge(skillTree, topChild.skillId, connectedAcquiredSkills, selectedParentByChild)) acquiredRoutePane else lockedRoutePane
                    paintRoute(CHILD_TWO_TO_TOP_CHILD_SLOTS, routeToTopPane)
                    paintRoute(
                        CHILD_TWO_TOP_CHILD_OUTGOING_SLOTS,
                        routeAfterTopPane,
                        transitionSkillId = topChild.skillId,
                        transitionLane = SkillTreeLane.TOP,
                        guideKey = "rank.skill.gui.route.next",
                        visible = !isEndSkill(skillTree, topChild)
                    )
                }

                if (bottomChild != null) {
                    val routeToBottomPane = if (isAcquiredEdge(skillTree, selectedSkill.skillId, bottomChild.skillId, connectedAcquiredSkills, selectedParentByChild)) acquiredRoutePane else lockedRoutePane
                    val routeAfterBottomPane = if (hasAcquiredOutgoingEdge(skillTree, bottomChild.skillId, connectedAcquiredSkills, selectedParentByChild)) acquiredRoutePane else lockedRoutePane
                    paintRoute(CHILD_TWO_TO_BOTTOM_CHILD_SLOTS, routeToBottomPane)
                    paintRoute(
                        CHILD_TWO_BOTTOM_CHILD_OUTGOING_SLOTS,
                        routeAfterBottomPane,
                        transitionSkillId = bottomChild.skillId,
                        transitionLane = SkillTreeLane.BOTTOM,
                        guideKey = "rank.skill.gui.route.next",
                        visible = !isEndSkill(skillTree, bottomChild)
                    )
                }

                setSkillNodeItem(inventory, SELECTED_SLOT_CHILD_TWO, selectedSkill, state, skillTree)
                topChild?.let { setSkillNodeItem(inventory, CHILD_TOP_SLOT_CHILD_TWO, it, state, skillTree) }
                bottomChild?.let { setSkillNodeItem(inventory, CHILD_BOTTOM_SLOT_CHILD_TWO, it, state, skillTree) }
            }

            SkillTreeLayout.MERGE -> {
                val rememberedLane = state.laneBySkillId[selectedSkill.skillId]
                val mergeSelectedLane = when (state.preferredLane) {
                    SkillTreeLane.TOP -> SkillTreeLane.TOP
                    SkillTreeLane.BOTTOM -> SkillTreeLane.BOTTOM
                    else -> when (rememberedLane) {
                        SkillTreeLane.TOP -> SkillTreeLane.TOP
                        SkillTreeLane.BOTTOM -> SkillTreeLane.BOTTOM
                        else -> SkillTreeLane.BOTTOM
                    }
                }
                state.currentSelectedLane = mergeSelectedLane
                state.laneBySkillId[selectedSkill.skillId] = mergeSelectedLane

                val mergeAlternateLane = if (mergeSelectedLane == SkillTreeLane.TOP) {
                    SkillTreeLane.BOTTOM
                } else {
                    SkillTreeLane.TOP
                }

                val selectedSlot = if (mergeSelectedLane == SkillTreeLane.TOP) MERGE_LEFT_TOP_SLOT else MERGE_LEFT_BOTTOM_SLOT
                val alternateSlot = if (mergeSelectedLane == SkillTreeLane.TOP) MERGE_LEFT_BOTTOM_SLOT else MERGE_LEFT_TOP_SLOT
                val selectedIncomingSlots = if (mergeSelectedLane == SkillTreeLane.TOP) {
                    MERGE_LEFT_TOP_INCOMING_SLOTS
                } else {
                    MERGE_LEFT_BOTTOM_INCOMING_SLOTS
                }
                val alternateIncomingSlots = if (mergeSelectedLane == SkillTreeLane.TOP) {
                    MERGE_LEFT_BOTTOM_INCOMING_SLOTS
                } else {
                    MERGE_LEFT_TOP_INCOMING_SLOTS
                }
                val selectedToChildSlots = if (mergeSelectedLane == SkillTreeLane.TOP) {
                    MERGE_TOP_TO_CHILD_SLOTS
                } else {
                    MERGE_BOTTOM_TO_CHILD_SLOTS
                }
                val alternateToChildSlots = if (mergeSelectedLane == SkillTreeLane.TOP) {
                    MERGE_BOTTOM_TO_CHILD_SLOTS
                } else {
                    MERGE_TOP_TO_CHILD_SLOTS
                }

                paintRoute(
                    selectedIncomingSlots,
                    routeToSelectedPane,
                    transitionSkillId = selectedParentSkillId,
                    transitionLane = mergeSelectedLane,
                    guideKey = "rank.skill.gui.route.prev",
                    visible = !isStartSkill(selectedSkill)
                )

                setSkillNodeItem(inventory, selectedSlot, selectedSkill, state, skillTree)
                val child = childNodes.firstOrNull()
                if (child != null) {
                    val selectedEdgeAcquired = isAcquiredEdge(skillTree, selectedSkill.skillId, child.skillId, connectedAcquiredSkills, selectedParentByChild)
                    val routeFromSelectedToChildPane = if (selectedEdgeAcquired) acquiredRoutePane else lockedRoutePane
                    val routeAfterChildPane = if (hasAcquiredOutgoingEdge(skillTree, child.skillId, connectedAcquiredSkills, selectedParentByChild)) acquiredRoutePane else lockedRoutePane
                    paintRoute(selectedToChildSlots, routeFromSelectedToChildPane)
                    paintRoute(
                        MERGE_CHILD_OUTGOING_SLOTS,
                        routeAfterChildPane,
                        transitionSkillId = child.skillId,
                        transitionLane = SkillTreeLane.CENTER,
                        guideKey = "rank.skill.gui.route.next",
                        visible = !isEndSkill(skillTree, child)
                    )

                    setSkillNodeItem(inventory, CHILD_SLOT_MERGE, child, state, skillTree)
                    val alternatePrerequisite = child.prerequisites.firstOrNull { it != selectedSkill.skillId }
                        ?.let { skillTree.getSkill(it) }
                    var alternateEdgeAcquired = false
                    if (alternatePrerequisite != null) {
                        state.laneBySkillId[alternatePrerequisite.skillId] = mergeAlternateLane
                        val alternateParentSkillId = selectedParentByChild[alternatePrerequisite.skillId] ?: getPreviousSkillId(alternatePrerequisite)
                        alternateEdgeAcquired = isAcquiredEdge(skillTree, alternatePrerequisite.skillId, child.skillId, connectedAcquiredSkills, selectedParentByChild)
                        val routeFromAlternateToChildPane = if (alternateEdgeAcquired) acquiredRoutePane else lockedRoutePane
                        val routeToAlternatePane = if (alternatePrerequisite.skillId in connectedAcquiredSkills) acquiredRoutePane else lockedRoutePane
                        paintRoute(
                            alternateIncomingSlots,
                            routeToAlternatePane,
                            transitionSkillId = alternateParentSkillId,
                            transitionLane = mergeAlternateLane,
                            guideKey = "rank.skill.gui.route.prev",
                            visible = !isStartSkill(alternatePrerequisite)
                        )
                        paintRoute(alternateToChildSlots, routeFromAlternateToChildPane)

                        setSkillNodeItem(inventory, alternateSlot, alternatePrerequisite, state, skillTree)
                    }

                    val sharedMergeRoutePane = if (selectedEdgeAcquired || alternateEdgeAcquired) acquiredRoutePane else lockedRoutePane
                    paintRoute(MERGE_SHARED_TO_CHILD_SLOTS, sharedMergeRoutePane)
                }
            }
        }

        // 戻るボタン（シンプルな形式）- 職業メインメニューに戻る
        val backButton = createGuiItem(
            Material.REDSTONE,
            toComponent("§c戻る"),
            emptyList()
        )
        inventory.setItem(NAV_LEFT_SLOT, backButton)
        // NAV_CENTER_SLOT, NAV_RIGHT_SLOTはモック削除のため背景のまま（既にheaderFooterPaneが設定済み）
    }

    private fun renderSkillTreeGuiViewport(
        inventory: Inventory,
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree,
        state: SkillTreeGuiState
    ) {
        val selectedSkill = skillTree.getSkill(state.selectedSkillId) ?: return
        val previousLaneMap = state.laneBySkillId.toMap()
        val renderModel = buildSkillTreeRenderModel(skillTree, previousLaneMap)
        val selectedPoint = renderModel.nodePositions[selectedSkill.skillId] ?: return
        val defaultViewportStartX = (selectedPoint.x - VIEWPORT_SELECTED_COLUMN).coerceAtLeast(0)
        val canvasMaxX = (
            renderModel.nodePositions.values.map { it.x } + renderModel.routeCells.keys.map { it.x }
        ).maxOrNull() ?: selectedPoint.x
        val maxViewportStartX = (canvasMaxX - 8).coerceAtLeast(0)
        val viewportStartX = (state.viewportStartX ?: defaultViewportStartX)
            .coerceIn(0, maxViewportStartX)
        state.viewportStartX = viewportStartX
        val viewportEndX = viewportStartX + 8

        val headerFooterPane = createBackgroundItem(Material.BLACK_STAINED_GLASS_PANE)
        val basePane = createBackgroundItem(Material.GRAY_STAINED_GLASS_PANE)
        val acquiredRoutePane = createBackgroundItem(Material.LIME_STAINED_GLASS_PANE)
        val lockedRoutePane = createBackgroundItem(Material.ORANGE_STAINED_GLASS_PANE)
        val connectedAcquiredSkills = buildConnectedAcquiredSkills(skillTree, state.acquiredSkills)

        for (slot in 0 until inventory.size) {
            inventory.setItem(slot, basePane)
        }
        for (slot in 0..8) {
            inventory.setItem(slot, headerFooterPane)
        }
        for (slot in 36..44) {
            inventory.setItem(slot, headerFooterPane)
        }

        state.slotToSkillId.clear()
        state.routeSlotToSkillId.clear()
        state.laneBySkillId.clear()
        renderModel.nodePositions.forEach { (skillId, point) ->
            state.laneBySkillId[skillId] = laneFromRow(point.y)
        }
        state.currentSelectedLane = state.laneBySkillId[selectedSkill.skillId] ?: SkillTreeLane.CENTER

        val routeSlotInfo = mutableMapOf<Int, RouteSlotRender>()
        renderModel.routeCells.forEach { (point, routeRefs) ->
            if (point.y !in 0..2 || point.x !in viewportStartX..viewportEndX) {
                return@forEach
            }

            val localX = point.x - viewportStartX
            val slot = toActiveSlot(localX, point.y)
            val slotInfo = routeSlotInfo.getOrPut(slot) { RouteSlotRender() }

            if (routeRefs.any { ref ->
                    ref.fromSkillId in connectedAcquiredSkills && ref.toSkillId in connectedAcquiredSkills
                }
            ) {
                slotInfo.acquired = true
            }

            val transition = resolveViewportBoundaryTransition(
                point = point,
                routeRefs = routeRefs,
                nodePositions = renderModel.nodePositions,
                viewportStartX = viewportStartX,
                viewportEndX = viewportEndX,
                preferredLane = state.preferredLane
            )
            if (transition != null && slotInfo.transition == null) {
                slotInfo.transition = transition.first
                slotInfo.guideKey = transition.second
            }
        }

        routeSlotInfo.forEach { (slot, info) ->
            val baseRouteItem = if (info.acquired) acquiredRoutePane else lockedRoutePane
            val displayItem = if (info.transition != null && info.guideKey != null) {
                createRouteGuideItem(baseRouteItem, info.guideKey!!)
            } else {
                baseRouteItem
            }
            inventory.setItem(slot, displayItem)
            if (info.transition != null) {
                state.routeSlotToSkillId[slot] = info.transition!!
            }
        }

        renderModel.nodePositions.forEach { (skillId, point) ->
            if (point.y !in 0..2 || point.x !in viewportStartX..viewportEndX) {
                return@forEach
            }

            val localX = point.x - viewportStartX
            val slot = toActiveSlot(localX, point.y)
            val skill = skillTree.getSkill(skillId) ?: return@forEach
            setSkillNodeItem(inventory, slot, skill, state, skillTree)
        }

        // 戻るボタン（シンプルな形式）- 職業メインメニューに戻る
        val backButton = createGuiItem(
            Material.REDSTONE,
            toComponent("§c戻る"),
            emptyList()
        )
        inventory.setItem(NAV_LEFT_SLOT, backButton)
        // NAV_CENTER_SLOT, NAV_RIGHT_SLOTはモック削除のため背景のまま（既にheaderFooterPaneが設定済み）
    }

    private fun buildSkillTreeRenderModel(
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree,
        rememberedLaneBySkill: Map<String, SkillTreeLane>
    ): SkillTreeRenderModel {
        val depthBySkill = computeSkillDepths(skillTree)
        val initialLaneBySkill = computeSkillLanes(skillTree, depthBySkill, rememberedLaneBySkill)
        val laneBySkill = resolveLaneCollisions(
            skillTree = skillTree,
            depthBySkill = depthBySkill,
            laneBySkill = initialLaneBySkill,
            rememberedLaneBySkill = rememberedLaneBySkill
        )

        val nodePositions = skillTree.getAllSkills().values.associate { skill ->
            val depth = depthBySkill[skill.skillId] ?: 0
            val lane = laneBySkill[skill.skillId] ?: SkillTreeLane.CENTER
            skill.skillId to VirtualPoint(x = depth * VIEWPORT_DEPTH_SPACING + VIEWPORT_SELECTED_COLUMN, y = rowFromLane(lane))
        }

        val routeCells = mutableMapOf<VirtualPoint, MutableList<RouteEdgeRef>>()
        skillTree.getAllSkills().values.forEach childLoop@{ child ->
            val childPosition = nodePositions[child.skillId] ?: return@childLoop
            child.prerequisites.forEach parentLoop@{ parentId ->
                val parentPosition = nodePositions[parentId] ?: return@parentLoop
                val path = buildRoutePath(parentPosition, childPosition)
                path.forEach { point ->
                    routeCells.getOrPut(point) { mutableListOf() }
                        .add(RouteEdgeRef(parentId, child.skillId))
                }
            }
        }

        return SkillTreeRenderModel(
            nodePositions = nodePositions,
            routeCells = routeCells.mapValues { it.value.toList() }
        )
    }

    private fun resolveLaneCollisions(
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree,
        depthBySkill: Map<String, Int>,
        laneBySkill: Map<String, SkillTreeLane>,
        rememberedLaneBySkill: Map<String, SkillTreeLane>
    ): Map<String, SkillTreeLane> {
        val resolved = laneBySkill.toMutableMap()

        val skillsByDepth = skillTree.getAllSkills().keys
            .groupBy { depthBySkill[it] ?: 0 }
            .toSortedMap()

        skillsByDepth.values.forEach { skillIds ->
            val occupied = mutableSetOf<SkillTreeLane>()
            val orderedSkillIds = skillIds.sortedWith(
                compareBy<String>({ rememberedLaneBySkill[it] == null }, { it })
            )

            orderedSkillIds.forEach { skillId ->
                val currentLane = resolved[skillId] ?: SkillTreeLane.CENTER
                if (currentLane !in occupied) {
                    occupied += currentLane
                    return@forEach
                }

                val nextLane = lanePreference(currentLane)
                    .firstOrNull { it !in occupied }
                    ?: currentLane
                resolved[skillId] = nextLane
                occupied += nextLane
            }
        }

        return resolved
    }

    private fun lanePreference(baseLane: SkillTreeLane): List<SkillTreeLane> {
        return when (baseLane) {
            SkillTreeLane.TOP -> listOf(SkillTreeLane.TOP, SkillTreeLane.CENTER, SkillTreeLane.BOTTOM)
            SkillTreeLane.CENTER -> listOf(SkillTreeLane.CENTER, SkillTreeLane.TOP, SkillTreeLane.BOTTOM)
            SkillTreeLane.BOTTOM -> listOf(SkillTreeLane.BOTTOM, SkillTreeLane.CENTER, SkillTreeLane.TOP)
        }
    }

    private fun computeSkillDepths(skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree): Map<String, Int> {
        val skills = skillTree.getAllSkills().keys.sorted()
        val depthBySkill = skills.associateWith { 0 }.toMutableMap()
        val indegree = skills.associateWith { skillTree.getSkill(it)?.prerequisites?.size ?: 0 }.toMutableMap()
        val queue = ArrayDeque(skills.filter { (indegree[it] ?: 0) == 0 })

        while (queue.isNotEmpty()) {
            val currentSkillId = queue.removeFirst()
            val currentDepth = depthBySkill[currentSkillId] ?: 0
            skillTree.getChildren(currentSkillId)
                .sorted()
                .forEach { childId ->
                    val nextDepth = (depthBySkill[childId] ?: 0).coerceAtLeast(currentDepth + 1)
                    depthBySkill[childId] = nextDepth

                    val nextInDegree = (indegree[childId] ?: 0) - 1
                    indegree[childId] = nextInDegree
                    if (nextInDegree == 0) {
                        queue.add(childId)
                    }
                }
        }

        return depthBySkill
    }

    private fun computeSkillLanes(
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree,
        depthBySkill: Map<String, Int>,
        rememberedLaneBySkill: Map<String, SkillTreeLane>
    ): Map<String, SkillTreeLane> {
        val laneBySkill = mutableMapOf<String, SkillTreeLane>()
        val skillsByDepth = skillTree.getAllSkills().values
            .sortedWith(compareBy<SkillNode> { depthBySkill[it.skillId] ?: 0 }.thenBy { it.skillId })

        skillsByDepth.forEach { skill ->
            val remembered = rememberedLaneBySkill[skill.skillId]
            if (remembered != null) {
                laneBySkill[skill.skillId] = remembered
                return@forEach
            }

            val lane = when (skill.prerequisites.size) {
                0 -> SkillTreeLane.CENTER
                1 -> laneBySkill[skill.prerequisites.first()] ?: SkillTreeLane.CENTER
                else -> {
                    val prerequisiteLanes = skill.prerequisites.map { laneBySkill[it] ?: SkillTreeLane.CENTER }.toSet()
                    when {
                        prerequisiteLanes.contains(SkillTreeLane.TOP) && prerequisiteLanes.contains(SkillTreeLane.BOTTOM) -> SkillTreeLane.CENTER
                        prerequisiteLanes.contains(SkillTreeLane.TOP) && prerequisiteLanes.contains(SkillTreeLane.CENTER) -> SkillTreeLane.TOP
                        prerequisiteLanes.contains(SkillTreeLane.BOTTOM) && prerequisiteLanes.contains(SkillTreeLane.CENTER) -> SkillTreeLane.BOTTOM
                        else -> prerequisiteLanes.firstOrNull() ?: SkillTreeLane.CENTER
                    }
                }
            }
            laneBySkill[skill.skillId] = lane
        }

        skillTree.getAllSkills().values
            .sortedWith(compareBy<SkillNode> { depthBySkill[it.skillId] ?: 0 }.thenBy { it.skillId })
            .forEach { parent ->
                val children = skillTree.getChildren(parent.skillId)
                    .mapNotNull { skillTree.getSkill(it) }
                    .filter { it.prerequisites.size == 1 }
                    .sortedBy { it.skillId }

                if (children.size != 2) {
                    return@forEach
                }

                val parentLane = laneBySkill[parent.skillId] ?: SkillTreeLane.CENTER
                val desiredLanes = when (parentLane) {
                    SkillTreeLane.TOP -> listOf(SkillTreeLane.TOP, SkillTreeLane.CENTER)
                    SkillTreeLane.CENTER -> listOf(SkillTreeLane.TOP, SkillTreeLane.BOTTOM)
                    SkillTreeLane.BOTTOM -> listOf(SkillTreeLane.CENTER, SkillTreeLane.BOTTOM)
                }

                val orderedChildren = children.sortedWith(
                    compareBy<SkillNode>({ rememberedLaneBySkill[it.skillId] == null }, { it.skillId })
                )

                orderedChildren.forEachIndexed { index, child ->
                    if (rememberedLaneBySkill[child.skillId] != null) {
                        return@forEachIndexed
                    }
                    laneBySkill[child.skillId] = desiredLanes.getOrElse(index) { SkillTreeLane.CENTER }
                }
            }

        return laneBySkill
    }

    private fun buildRoutePath(from: VirtualPoint, to: VirtualPoint): List<VirtualPoint> {
        if (to.x <= from.x) {
            return emptyList()
        }

        val path = mutableListOf<VirtualPoint>()
        val turnX = to.x - 1

        if (from.x + 1 <= turnX) {
            for (x in (from.x + 1)..turnX) {
                path += VirtualPoint(x, from.y)
            }
        }

        when {
            from.y < to.y -> {
                for (y in (from.y + 1)..to.y) {
                    path += VirtualPoint(turnX, y)
                }
            }

            from.y > to.y -> {
                for (y in (from.y - 1) downTo to.y) {
                    path += VirtualPoint(turnX, y)
                }
            }
        }

        return path.distinct()
    }

    private fun resolveViewportBoundaryTransition(
        point: VirtualPoint,
        routeRefs: List<RouteEdgeRef>,
        nodePositions: Map<String, VirtualPoint>,
        viewportStartX: Int,
        viewportEndX: Int,
        preferredLane: SkillTreeLane?
    ): Pair<RouteTransition, String>? {
        val candidates = mutableListOf<Pair<RouteTransition, String>>()

        routeRefs.forEach { ref ->
            val fromPoint = nodePositions[ref.fromSkillId] ?: return@forEach
            val toPoint = nodePositions[ref.toSkillId] ?: return@forEach

            if (point.x == viewportStartX && fromPoint.x < viewportStartX) {
                val lane = laneFromRow(fromPoint.y)
                candidates += RouteTransition(scrollDeltaX = -1, lane = lane) to "rank.skill.gui.route.prev"
            }

            if (point.x == viewportEndX && toPoint.x > viewportEndX) {
                val lane = laneFromRow(toPoint.y)
                candidates += RouteTransition(scrollDeltaX = 1, lane = lane) to "rank.skill.gui.route.next"
            }
        }

        if (candidates.isEmpty()) {
            return null
        }

        if (preferredLane != null) {
            candidates.firstOrNull { it.first.lane == preferredLane }?.let { return it }
        }

        return candidates.first()
    }

    private fun toActiveSlot(localX: Int, row: Int): Int = (row + 1) * 9 + localX

    private fun rowFromLane(lane: SkillTreeLane): Int = when (lane) {
        SkillTreeLane.TOP -> 0
        SkillTreeLane.CENTER -> 1
        SkillTreeLane.BOTTOM -> 2
    }

    private fun laneFromRow(row: Int): SkillTreeLane = when (row) {
        0 -> SkillTreeLane.TOP
        2 -> SkillTreeLane.BOTTOM
        else -> SkillTreeLane.CENTER
    }

    private fun determineSkillTreeLayout(children: List<SkillNode>): SkillTreeLayout {
        if (children.size == 2) {
            return SkillTreeLayout.CHILD_TWO
        }

        if (children.size == 1 && children.first().prerequisites.size == 2) {
            return SkillTreeLayout.MERGE
        }

        return SkillTreeLayout.CHILD_ONE
    }

    private fun getPreviousSkillId(skill: SkillNode): String? = skill.prerequisites.firstOrNull()

    private fun hasConnectedAcquiredDescendant(
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree,
        rootSkillId: String,
        connectedAcquiredSkills: Set<String>
    ): Boolean {
        val visited = mutableSetOf<String>()

        fun dfs(skillId: String): Boolean {
            if (!visited.add(skillId)) {
                return false
            }

            val children = skillTree.getChildren(skillId)
            for (childId in children) {
                if (childId in connectedAcquiredSkills) {
                    return true
                }
                if (dfs(childId)) {
                    return true
                }
            }
            return false
        }

        return dfs(rootSkillId)
    }

    private fun buildSelectedParentByChild(
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree,
        connectedAcquiredSkills: Set<String>
    ): Map<String, String> {
        val selected = mutableMapOf<String, String>()
        skillTree.getAllSkills().keys.sorted().forEach { childId ->
            if (childId !in connectedAcquiredSkills) {
                return@forEach
            }
            val parent = skillTree.getParents(childId)
                .filter { it in connectedAcquiredSkills }
                .sorted()
                .firstOrNull()
            if (parent != null) {
                selected[childId] = parent
            }
        }
        return selected
    }

    private fun isAcquiredEdge(
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree,
        fromSkillId: String,
        toSkillId: String,
        connectedAcquiredSkills: Set<String>,
        selectedParentByChild: Map<String, String>
    ): Boolean {
        if (fromSkillId !in connectedAcquiredSkills || toSkillId !in connectedAcquiredSkills) {
            return false
        }
        if (!skillTree.getChildren(fromSkillId).contains(toSkillId)) {
            return false
        }
        val selectedParent = selectedParentByChild[toSkillId]
        return selectedParent == null || selectedParent == fromSkillId
    }

    private fun hasAcquiredOutgoingEdge(
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree,
        fromSkillId: String,
        connectedAcquiredSkills: Set<String>,
        selectedParentByChild: Map<String, String>
    ): Boolean {
        return skillTree.getChildren(fromSkillId).any { childId ->
            isAcquiredEdge(skillTree, fromSkillId, childId, connectedAcquiredSkills, selectedParentByChild)
        }
    }

    private fun buildConnectedAcquiredSkills(
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree,
        acquiredSkills: Set<String>
    ): Set<String> {
        val connected = mutableSetOf<String>()
        val queue = ArrayDeque<String>()

        skillTree.getAllSkills().values
            .filter { skillTree.getParents(it.skillId).isEmpty() }
            .forEach { root ->
                if (root.skillId in acquiredSkills && connected.add(root.skillId)) {
                    queue.add(root.skillId)
                }
            }

        while (queue.isNotEmpty()) {
            val currentSkillId = queue.removeFirst()
            val children = skillTree.getChildren(currentSkillId)
            children.forEach { childId ->
                val child = skillTree.getSkill(childId) ?: return@forEach
                if (child.skillId !in acquiredSkills) {
                    return@forEach
                }
                val parents = skillTree.getParents(child.skillId)
                if (parents.isNotEmpty() && parents.none { it in connected }) {
                    return@forEach
                }
                if (connected.add(child.skillId)) {
                    queue.add(child.skillId)
                }
            }
        }

        return connected
    }

    private fun isStartSkill(skill: SkillNode): Boolean = skill.prerequisites.isEmpty()

    private fun isEndSkill(skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree, skill: SkillNode): Boolean {
        return skillTree.getChildren(skill.skillId).isEmpty()
    }

    private fun setSkillNodeItem(
        inventory: Inventory,
        slot: Int,
        skill: SkillNode,
        state: SkillTreeGuiState,
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree
    ) {
        inventory.setItem(slot, createSkillNodeItem(skill, state, skillTree))
        state.slotToSkillId[slot] = skill.skillId
    }

    private fun createSkillNodeItem(
        skill: SkillNode,
        state: SkillTreeGuiState,
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree
    ): ItemStack {
        val acquired = skill.skillId in state.acquiredSkills
        val available = !acquired && skillTree.canAcquire(skill.skillId, state.acquiredSkills, state.currentLevel)
        val prestigeUnlocked = skill.skillId in state.prestigeSkills && acquired
        val prestigeAvailable = state.isMaxLevel &&
                                skill.skillId !in state.prestigeSkills &&
                                acquired &&
                                state.prestigeLevel >= skill.requiredLevel

        val status = when {
            prestigeUnlocked -> messageProvider.getMessage("rank.skill.gui.status.prestige_unlocked")
            acquired -> messageProvider.getMessage("rank.skill.gui.status.acquired")
            available -> messageProvider.getMessage("rank.skill.gui.status.available")
            else -> messageProvider.getMessage("rank.skill.gui.status.locked")
        }

        val material = resolveSkillIconMaterial(skill.icon)
        val lore = mutableListOf(
            toComponent(messageProvider.getSkillDescription(state.profession, skill.skillId)),
            toComponent(messageProvider.getMessage("rank.skill.gui.lore.required_level", "level" to skill.requiredLevel)),
            toComponent(messageProvider.getMessage("rank.skill.gui.lore.current_level", "level" to state.currentLevel)),
            toComponent(messageProvider.getMessage("rank.skill.gui.lore.status", "status" to status))
        )

        // プレステージアンロック可能な場合、Loreに追加情報を表示
        if (prestigeAvailable) {
            lore.add(toComponent(""))
            lore.add(toComponent(messageProvider.getMessage("rank.skill.gui.lore.prestige_available")))
        }

        return createGuiItem(
            material,
            toComponent(messageProvider.getSkillName(state.profession, skill.skillId)),
            lore
        )
    }

    private fun createNavigationItem(material: Material, nameKey: String, loreKey: String): ItemStack {
        return createGuiItem(
            material,
            toComponent(messageProvider.getMessage(nameKey)),
            listOf(toComponent(messageProvider.getMessage(loreKey)))
        )
    }

    private fun createRouteGuideItem(baseItem: ItemStack, guideKey: String): ItemStack {
        val routeItem = baseItem.clone()
        val meta = routeItem.itemMeta ?: return routeItem
        meta.displayName(null)
        meta.lore(null)
        meta.itemName(withoutItalic(toComponent(messageProvider.getMessage(guideKey))))
        meta.setHideTooltip(false)
        meta.removeItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        routeItem.itemMeta = meta
        return routeItem
    }

    private fun resolveSkillIconMaterial(iconName: String?): Material {
        val normalized = iconName?.trim()?.uppercase() ?: return Material.BARRIER
        return Material.matchMaterial(normalized) ?: Material.BARRIER
    }
    
    /**
     * タスク進捗情報を表示
     */
    private fun handleTaskInfo(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage(messageProvider.getMessage("rank.tutorial_task_info.usage"))
            return false
        }

        val targetPlayer = Bukkit.getPlayer(args[1])
        if (targetPlayer == null) {
            sender.sendMessage(messageProvider.getMessage("rank.tutorial_task_info.player_not_found"))
            return false
        }
        
        val loader = taskLoader
        val checker = taskChecker
        if (loader == null || checker == null) {
            sender.sendMessage(messageProvider.getMessage("rank.tutorial_task_info.system_not_initialized"))
            return false
        }
        
        val uuid = targetPlayer.uniqueId
        val tutorial = rankManager.getPlayerTutorial(uuid)
        val progress = tutorial.taskProgress
        val requirement = loader.getRequirement(tutorial.currentRank.name)
        val viewer = if (sender is Player) sender else targetPlayer

        openTaskInfoGui(viewer, targetPlayer, tutorial.currentRank.name, progress, requirement)

        if (sender !is Player) {
            sender.sendMessage(
                messageProvider.getMessage(
                    "rank.tutorial_task_info.opened_for_player",
                    "player" to targetPlayer.name
                )
            )
        }
        return true
    }

    private fun openTaskInfoGui(
        viewer: Player,
        targetPlayer: Player,
        rankName: String,
        progress: TaskProgress,
        requirement: TaskRequirement
    ) {
        val holder = TaskInfoGuiHolder()
        val title = messageProvider.getMessage(
            "rank.tutorial_task_info.title",
            "player" to targetPlayer.name,
            "rank" to rankName
        )
        val inventory = Bukkit.createInventory(holder, 45, title)
        holder.backingInventory = inventory

        val headerFooter = createBackgroundItem(Material.BLACK_STAINED_GLASS_PANE)
        val middle = createBackgroundItem(Material.GRAY_STAINED_GLASS_PANE)

        for (slot in 0..8) {
            inventory.setItem(slot, headerFooter)
        }
        for (slot in 36..44) {
            inventory.setItem(slot, headerFooter)
        }
        for (slot in 9..35) {
            inventory.setItem(slot, middle)
        }

        val categories = buildTaskCategoryItems(targetPlayer, progress, requirement)
        val targetSlots = getCenteredSlots(categories.size)
        categories.zip(targetSlots).forEach { (item, slot) ->
            inventory.setItem(slot, item)
        }

        if (categories.isEmpty()) {
            val completeItem = createGuiItem(
                Material.LIME_STAINED_GLASS_PANE,
                toComponent(messageProvider.getMessage("rank.tutorial_task_info.complete_title")),
                listOf(toComponent(messageProvider.getMessage("rank.tutorial_task_info.complete_lore")))
            )
            inventory.setItem(22, completeItem)
        }

        viewer.openInventory(inventory)
    }

    private fun buildTaskCategoryItems(
        targetPlayer: Player,
        progress: TaskProgress,
        requirement: TaskRequirement
    ): List<ItemStack> {
        val items = mutableListOf<ItemStack>()

        if (requirement.playTimeMin > 0) {
            val current = minOf(progress.playTime, requirement.playTimeMin.toLong())
            val required = requirement.playTimeMin.toLong()
            val done = progress.playTime >= requirement.playTimeMin
            val lore = listOf(
                toComponent(
                    messageProvider.getMessage(
                        "rank.tutorial_task_info.line.progress_time",
                        "status" to statusIcon(done),
                        "current" to formatPlayTime(current),
                        "required" to formatPlayTime(required)
                    )
                ),
                toComponent(
                    messageProvider.getMessage(
                        "rank.tutorial_task_info.line.percent",
                        "percent" to percent(current, required)
                    )
                )
            )
            items += createGuiItem(
                Material.CLOCK,
                toComponent(messageProvider.getMessage("rank.tutorial_task_info.category.play_time")),
                lore
            )
        }

        if (requirement.mobKills.isNotEmpty() || requirement.bossKills.isNotEmpty()) {
            val details = mutableListOf<Component>()
            var allDone = true

            requirement.mobKills.forEach { (mobType, required) ->
                val current = progress.getMobKillCount(mobType)
                val done = current >= required
                if (!done) {
                    allDone = false
                }

                val nameComponent = if (translator != null) {
                    Component.translatable(translator.translateEntity(mobType))
                } else {
                    Component.text(mobType)
                }
                details += buildTranslatedTargetLine(
                    nameComponent,
                    done,
                    minOf(current, required),
                    required,
                    messageProvider.getMessage("tutorial_rank.task.suffix.defeat")
                )
            }
            requirement.bossKills.forEach { (bossType, required) ->
                val current = progress.getBossKillCount(bossType)
                val done = current >= required
                if (!done) {
                    allDone = false
                }

                val nameComponent = if (translator != null) {
                    Component.translatable(translator.translateBoss(bossType))
                } else {
                    Component.text(bossType)
                }
                details += buildTranslatedTargetLine(
                    nameComponent,
                    done,
                    minOf(current, required),
                    required,
                    messageProvider.getMessage("tutorial_rank.task.suffix.defeat")
                )
            }

            items += createGuiItem(
                Material.DIAMOND_SWORD,
                toComponent(
                    messageProvider.getMessage(
                        "rank.tutorial_task_info.category.combat",
                        "status" to statusIcon(allDone)
                    )
                ),
                details.take(7)
            )
        }

        if (requirement.blockMines.isNotEmpty()) {
            val details = mutableListOf<Component>()
            var allDone = true
            requirement.blockMines.forEach { (blockType, required) ->
                val current = progress.getBlockMineCount(blockType)
                val done = current >= required
                if (!done) {
                    allDone = false
                }

                val nameComponent = if (translator != null) {
                    Component.translatable(translator.translateBlock(blockType))
                } else {
                    Component.text(blockType)
                }
                details += buildTranslatedTargetLine(
                    nameComponent,
                    done,
                    minOf(current, required),
                    required,
                    messageProvider.getMessage("tutorial_rank.task.suffix.collect")
                )
            }

            items += createGuiItem(
                Material.IRON_PICKAXE,
                toComponent(
                    messageProvider.getMessage(
                        "rank.tutorial_task_info.category.mining",
                        "status" to statusIcon(allDone)
                    )
                ),
                details.take(7)
            )
        }

        if (requirement.vanillaExp > 0) {
            val current = minOf(progress.vanillaExp, requirement.vanillaExp)
            val required = requirement.vanillaExp
            val done = progress.vanillaExp >= required
            val lore = listOf(
                toComponent(
                    messageProvider.getMessage(
                        "rank.tutorial_task_info.line.progress_exp",
                        "status" to statusIcon(done),
                        "current" to current,
                        "required" to required
                    )
                ),
                toComponent(
                    messageProvider.getMessage(
                        "rank.tutorial_task_info.line.percent",
                        "percent" to percent(current, required)
                    )
                )
            )
            items += createGuiItem(
                Material.EXPERIENCE_BOTTLE,
                toComponent(messageProvider.getMessage("rank.tutorial_task_info.category.vanilla_exp")),
                lore
            )
        }

        if (requirement.itemsRequired.isNotEmpty()) {
            val details = mutableListOf<Component>()
            var allDone = true
            requirement.itemsRequired.forEach { (material, required) ->
                val current = TutorialInventoryHelper.countItemInInventory(targetPlayer, material.uppercase())
                val done = current >= required
                if (!done) {
                    allDone = false
                }

                val nameComponent = if (translator != null) {
                    Component.translatable(translator.translateItem(material))
                } else {
                    Component.text(material)
                }
                details += buildTranslatedTargetLine(
                    nameComponent,
                    done,
                    minOf(current, required),
                    required,
                    messageProvider.getMessage("tutorial_rank.task.suffix.collect")
                )
            }

            items += createGuiItem(
                Material.BUNDLE,
                toComponent(
                    messageProvider.getMessage(
                        "rank.tutorial_task_info.category.items",
                        "status" to statusIcon(allDone)
                    )
                ),
                details.take(7)
            )
        }

        return items
    }

    private fun getCenteredSlots(count: Int): List<Int> {
        if (count <= 0) {
            return emptyList()
        }

        val center = 22
        val slots = mutableListOf<Int>()
        var offset = 1

        if (count % 2 == 1) {
            slots += center
        }

        while (slots.size < count) {
            val left = center - offset
            if (left in 18..26) {
                slots += left
            }
            if (slots.size >= count) {
                break
            }

            val right = center + offset
            if (right in 18..26) {
                slots += right
            }
            offset++
        }

        return slots
    }

    private fun statusIcon(done: Boolean): String = if (done) "§a✓" else "§c✗"

    private fun percent(current: Long, required: Long): Int {
        if (required <= 0L) {
            return 100
        }
        return ((current.toDouble() / required.toDouble()) * 100).toInt().coerceIn(0, 100)
    }

    private fun createBackgroundItem(material: Material): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        if (meta != null) {
            meta.displayName(withoutItalic(Component.text(" ")))
            meta.lore(emptyList())
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            meta.setHideTooltip(true)
            item.itemMeta = meta
        }
        return item
    }

    private fun createGuiItem(material: Material, displayName: Component, lore: List<Component>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        if (meta != null) {
            meta.displayName(withoutItalic(displayName))
            meta.lore(lore.map { withoutItalic(it) })
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
            item.itemMeta = meta
        }
        return item
    }

    private fun buildTranslatedTargetLine(
        nameComponent: Component,
        done: Boolean,
        current: Int,
        required: Int,
        actionSuffix: String
    ): Component {
        val numberColor = progressColorCode(current.toLong(), required.toLong())
        return toComponent("${statusIcon(done)} ")
            .append(nameComponent.color(NamedTextColor.GRAY))
            .append(toComponent("§7${actionSuffix} (§r${numberColor}${current}§7/${required}§7)"))
    }

    private fun toComponent(text: String): Component = LEGACY_SERIALIZER.deserialize(text)

    private fun withoutItalic(component: Component): Component =
        component.decoration(TextDecoration.ITALIC, false)

    @EventHandler
    fun onSkillTreeGuiClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? SkillTreeGuiHolder ?: return
        event.isCancelled = true

        val clickedSlot = event.rawSlot
        if (clickedSlot !in 0 until event.view.topInventory.size) {
            return
        }

        val routeTransition = holder.state.routeSlotToSkillId[clickedSlot]
        if (routeTransition != null) {
            val player = event.whoClicked as? Player
            navigateSkillTreeToSkill(holder, routeTransition, player)
            return
        }

        val player = event.whoClicked as? Player

        when (clickedSlot) {
            NAV_LEFT_SLOT -> {
                if (player != null) {
                    onSkillTreeNavLeft(holder, player)
                }
            }
            NAV_CENTER_SLOT -> onSkillTreeNavCenter(holder)
            NAV_RIGHT_SLOT -> onSkillTreeNavRight(holder)
            else -> {
                val skillId = holder.state.slotToSkillId[clickedSlot] ?: return
                if (player == null) return
                onSkillTreeSkillNodeClicked(holder, skillId, player)
            }
        }
    }

    @EventHandler
    fun onSkillTreeGuiDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder !is SkillTreeGuiHolder) {
            return
        }
        event.isCancelled = true
    }

    private fun onSkillTreeNavLeft(holder: SkillTreeGuiHolder, viewer: Player) {
        holder.state.lastAction = "nav_left"
        // 職業メインメニューに戻る
        viewer.playSound(viewer.location, Sound.UI_BUTTON_CLICK, 0.8f, 1.0f)
        openProfessionMainMenu(viewer)
    }

    private fun onSkillTreeNavCenter(holder: SkillTreeGuiHolder) {
        // モック削除のため何もしない
        holder.state.lastAction = "nav_center"
    }

    private fun onSkillTreeNavRight(holder: SkillTreeGuiHolder) {
        // モック削除のため何もしない
        holder.state.lastAction = "nav_right"
    }

    private fun onSkillTreeSkillNodeClicked(holder: SkillTreeGuiHolder, skillId: String, viewer: Player) {
        holder.state.lastAction = "skill:$skillId"
        viewer.playSound(viewer.location, "minecraft:ui.button.click", 0.8f, 1.0f)

        val latestProfession = rankManager.getPlayerProfession(viewer.uniqueId)
        if (latestProfession == null || latestProfession.profession != holder.state.profession) {
            viewer.sendMessage(messageProvider.getMessage("rank.skill.gui.no_profession"))
            viewer.playSound(viewer.location, "minecraft:entity.villager.no", 1.0f, 1.0f)
            return
        }
        holder.state.acquiredSkills = latestProfession.acquiredSkills.toSet()
        holder.state.currentLevel = rankManager.getCurrentProfessionLevel(viewer.uniqueId)
        holder.state.currentExp = latestProfession.currentExp

        val skillTree = SkillTreeRegistry.getSkillTree(holder.state.profession) ?: return
        val skill = skillTree.getSkill(skillId) ?: return

        // プレステージスキルのアンロック可能チェック
        val prestigeAvailable = holder.state.isMaxLevel &&
                                skill.skillId !in holder.state.prestigeSkills &&
                                skill.skillId in holder.state.acquiredSkills &&
                                holder.state.prestigeLevel >= skill.requiredLevel

        if (skill.skillId in holder.state.acquiredSkills) {
            // プレステージスキルとしてアンロック可能な場合
            if (prestigeAvailable) {
                openPrestigeSkillUnlockDialog(viewer, holder, skillTree, skill)
                return
            }

            viewer.sendMessage(
                messageProvider.getMessage(
                    "rank.skill.gui.unlock.already",
                    "skill" to messageProvider.getSkillName(holder.state.profession, skill.skillId)
                )
            )
            viewer.playSound(viewer.location, "minecraft:entity.villager.no", 1.0f, 1.0f)
            return
        }

        val parents = skillTree.getParents(skill.skillId)
        val acquiredParents = parents.filter { it in holder.state.acquiredSkills }

        if (parents.isNotEmpty() && acquiredParents.isEmpty()) {
            val prerequisiteNames = parents.joinToString(" / ") {
                messageProvider.getSkillName(holder.state.profession, it)
            }
            viewer.sendMessage(
                messageProvider.getMessage(
                    "rank.skill.gui.unlock.missing_prerequisite",
                    "skills" to prerequisiteNames
                )
            )
            viewer.playSound(viewer.location, "minecraft:entity.villager.no", 1.0f, 1.0f)
            return
        }

        if (holder.state.currentLevel < skill.requiredLevel) {
            viewer.sendMessage(
                messageProvider.getMessage(
                    "rank.skill.gui.unlock.level_shortage",
                    "required" to skill.requiredLevel,
                    "current" to holder.state.currentLevel
                )
            )
            viewer.playSound(viewer.location, "minecraft:entity.villager.no", 1.0f, 1.0f)
            return
        }

        val blockedByBranch = acquiredParents.any { parentId ->
            val parentSkill = skillTree.getSkill(parentId)
            if (parentSkill?.exclusiveBranch != true) return@any false
            val siblings = skillTree.getChildren(parentId)
            if (siblings.size < 2) {
                return@any false
            }
            siblings.any { it in holder.state.acquiredSkills && it != skill.skillId }
        }
        if (blockedByBranch) {
            viewer.sendMessage(messageProvider.getMessage("rank.skill.gui.unlock.branch_locked"))
            viewer.playSound(viewer.location, "minecraft:entity.villager.no", 1.0f, 1.0f)
            return
        }

        val requiresChoiceConfirmation = acquiredParents.any { parentId ->
            val parentSkill = skillTree.getSkill(parentId)
            if (parentSkill?.exclusiveBranch != true) return@any false
            val siblings = skillTree.getChildren(parentId)
            siblings.size >= 2 && siblings.contains(skill.skillId) && siblings.none { it in holder.state.acquiredSkills }
        }
        if (requiresChoiceConfirmation) {
            openSkillUnlockDialog(viewer, holder, skillTree, skill)
            return
        }

        executeSkillUnlock(viewer, holder, skillTree, skill)
    }

    private fun executeSkillUnlock(
        viewer: Player,
        holder: SkillTreeGuiHolder,
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree,
        skill: SkillNode
    ) {
        val success = rankManager.acquireSkill(viewer.uniqueId, skill.skillId)
        if (!success) {
            viewer.sendMessage(messageProvider.getMessage("rank.skill.gui.unlock.failed"))
            viewer.playSound(viewer.location, "minecraft:entity.villager.no", 1.0f, 1.0f)
            return
        }

        val updatedProfession = rankManager.getPlayerProfession(viewer.uniqueId)
        if (updatedProfession != null) {
            holder.state.acquiredSkills = updatedProfession.acquiredSkills.toSet()
            holder.state.currentLevel = rankManager.getCurrentProfessionLevel(viewer.uniqueId)
            holder.state.currentExp = updatedProfession.currentExp
        }

        viewer.sendMessage(
            messageProvider.getMessage(
                "rank.skill.gui.unlock.success",
                "skill" to messageProvider.getSkillName(holder.state.profession, skill.skillId)
            )
        )
        renderSkillTreeGui(holder.backingInventory, skillTree, holder.state)
    }

    private fun openSkillUnlockDialog(
        viewer: Player,
        holder: SkillTreeGuiHolder,
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree,
        skill: SkillNode
    ) {
        val skillName = messageProvider.getSkillName(holder.state.profession, skill.skillId)
        val yesButton = io.papermc.paper.registry.data.dialog.ActionButton.builder(
            withoutItalic(toComponent(messageProvider.getMessage("rank.skill.gui.dialog.confirm")))
        )
            .width(150)
            .action(
                io.papermc.paper.registry.data.dialog.action.DialogAction.customClick(
                    io.papermc.paper.registry.data.dialog.action.DialogActionCallback { _, audience ->
                        val target = audience as? Player ?: return@DialogActionCallback
                        executeSkillUnlock(target, holder, skillTree, skill)
                    },
                    net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).build()
                )
            )
            .build()

        val noButton = io.papermc.paper.registry.data.dialog.ActionButton.builder(
            withoutItalic(toComponent(messageProvider.getMessage("rank.skill.gui.dialog.cancel")))
        )
            .width(150)
            .build()

        val dialog = io.papermc.paper.dialog.Dialog.create { factory ->
            factory.empty()
                .base(
                    io.papermc.paper.registry.data.dialog.DialogBase.builder(
                        withoutItalic(toComponent(messageProvider.getMessage("rank.skill.gui.dialog.title")))
                    )
                        .body(
                            listOf(
                                io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(
                                    withoutItalic(
                                        toComponent(
                                            messageProvider.getMessage(
                                                "rank.skill.gui.dialog.body",
                                                "skill" to skillName
                                            )
                                        )
                                    ),
                                    280
                                )
                            )
                        )
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction.CLOSE)
                        .build()
                )
                .type(io.papermc.paper.registry.data.dialog.type.DialogType.confirmation(yesButton, noButton))
        }

        viewer.showDialog(dialog)
    }

    private fun openPrestigeSkillUnlockDialog(
        viewer: Player,
        holder: SkillTreeGuiHolder,
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree,
        skill: SkillNode
    ) {
        val skillName = messageProvider.getSkillName(holder.state.profession, skill.skillId)
        val yesButton = io.papermc.paper.registry.data.dialog.ActionButton.builder(
            withoutItalic(toComponent(messageProvider.getMessage("rank.skill.gui.dialog.confirm")))
        )
            .width(150)
            .action(
                io.papermc.paper.registry.data.dialog.action.DialogAction.customClick(
                    io.papermc.paper.registry.data.dialog.action.DialogActionCallback { _, audience ->
                        val target = audience as? Player ?: return@DialogActionCallback
                        executePrestigeSkillUnlock(target, holder, skillTree, skill)
                    },
                    net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).build()
                )
            )
            .build()

        val noButton = io.papermc.paper.registry.data.dialog.ActionButton.builder(
            withoutItalic(toComponent(messageProvider.getMessage("rank.skill.gui.dialog.cancel")))
        )
            .width(150)
            .build()

        val dialog = io.papermc.paper.dialog.Dialog.create { factory ->
            factory.empty()
                .base(
                    io.papermc.paper.registry.data.dialog.DialogBase.builder(
                        withoutItalic(toComponent(messageProvider.getMessage("rank.skill.gui.prestige.dialog.title")))
                    )
                        .body(
                            listOf(
                                io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(
                                    withoutItalic(
                                        toComponent(
                                            messageProvider.getMessage(
                                                "rank.skill.gui.prestige.dialog.body",
                                                "skill" to skillName
                                            )
                                        )
                                    ),
                                    280
                                )
                            )
                        )
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction.CLOSE)
                        .build()
                )
                .type(io.papermc.paper.registry.data.dialog.type.DialogType.confirmation(yesButton, noButton))
        }

        viewer.showDialog(dialog)
    }

    private fun executePrestigeSkillUnlock(
        viewer: Player,
        holder: SkillTreeGuiHolder,
        skillTree: jp.awabi2048.cccontent.features.rank.profession.SkillTree,
        skill: SkillNode
    ) {
        val success = rankManager.acquirePrestigeSkill(viewer.uniqueId, skill.skillId)
        if (!success) {
            viewer.sendMessage(messageProvider.getMessage("rank.skill.gui.prestige.unlock.failed"))
            viewer.playSound(viewer.location, "minecraft:entity.villager.no", 1.0f, 1.0f)
            return
        }

        val updatedProfession = rankManager.getPlayerProfession(viewer.uniqueId)
        if (updatedProfession != null) {
            holder.state.prestigeSkills = updatedProfession.prestigeSkills.toSet()
        }

        viewer.sendMessage(
            messageProvider.getMessage(
                "rank.skill.gui.prestige.unlock.success",
                "skill" to messageProvider.getSkillName(holder.state.profession, skill.skillId)
            )
        )
        renderSkillTreeGui(holder.backingInventory, skillTree, holder.state)
    }

    private fun navigateSkillTreeToSkill(holder: SkillTreeGuiHolder, transition: RouteTransition, viewer: Player?) {
        val skillTree = SkillTreeRegistry.getSkillTree(holder.state.profession) ?: return
        transition.lane?.let { holder.state.preferredLane = it }

        if (transition.scrollDeltaX != 0) {
            val renderModel = buildSkillTreeRenderModel(skillTree, holder.state.laneBySkillId)
            val selectedPoint = renderModel.nodePositions[holder.state.selectedSkillId]
                ?: VirtualPoint(VIEWPORT_SELECTED_COLUMN, rowFromLane(SkillTreeLane.CENTER))
            val defaultViewportStartX = (selectedPoint.x - VIEWPORT_SELECTED_COLUMN).coerceAtLeast(0)
            val canvasMaxX = (
                renderModel.nodePositions.values.map { it.x } + renderModel.routeCells.keys.map { it.x }
            ).maxOrNull() ?: selectedPoint.x
            val maxViewportStartX = (canvasMaxX - 8).coerceAtLeast(0)
            val currentViewportStartX = holder.state.viewportStartX ?: defaultViewportStartX

            holder.state.viewportStartX = (currentViewportStartX + transition.scrollDeltaX)
                .coerceIn(0, maxViewportStartX)
            holder.state.lastAction = "route:scroll:${holder.state.viewportStartX}"
            renderSkillTreeGui(holder.backingInventory, skillTree, holder.state)
            if (viewer != null) {
                viewer.playSound(viewer.location, Sound.UI_BUTTON_CLICK, 0.8f, 1.2f)
            }
            return
        }

        val targetSkillId = transition.skillId ?: return
        if (skillTree.getSkill(targetSkillId) == null) {
            return
        }

        holder.state.selectedSkillId = targetSkillId
        holder.state.viewportStartX = null
        holder.state.lastAction = "route:$targetSkillId"
        renderSkillTreeGui(holder.backingInventory, skillTree, holder.state)
        if (viewer != null) {
            viewer.playSound(viewer.location, Sound.UI_BUTTON_CLICK, 0.8f, 1.2f)
        }
    }

    @EventHandler
    fun onProfessionMainMenuClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? ProfessionMainMenuGuiHolder ?: return
        event.isCancelled = true

        val clickedSlot = event.rawSlot
        if (clickedSlot !in 0 until event.view.topInventory.size) {
            return
        }

        val player = event.whoClicked as? Player ?: return

        when (clickedSlot) {
            MAIN_MENU_SKILL_TREE_SLOT -> {
                // スキルツリーを開く
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.8f, 2.0f)
                openSkillTreeGui(player)
            }
            MAIN_MENU_PROFESSION_OVERVIEW_SLOT -> {
                // Shift右クリックでプレステージ確認ダイアログを表示
                if (event.isShiftClick && event.isRightClick) {
                    openPrestigeConfirmDialogFirst(player)
                }
            }
            MAIN_MENU_MODE_SWITCH_SLOT -> {
                // 能動スキルがない場合は処理しない
                if (!ActiveSkillIdentifier.hasAnyToggleableSkill(player)) {
                    return
                }

                if (event.isLeftClick) {
                    // 左クリック: スキル切替
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.8f, 2.0f)
                    ActiveSkillManager.rotateActiveSkill(player)
                    // メニューを更新
                    val profession = rankManager.getPlayerProfession(player.uniqueId) ?: return
                    val holder = event.view.topInventory.holder as? ProfessionMainMenuGuiHolder ?: return
                    renderProfessionMainMenu(holder.backingInventory, player, profession)
                } else if (event.isRightClick) {
                    // 右クリック: 切替様式変更
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.8f, 1.5f)
                    ActiveSkillManager.rotateSwitchMode(player)
                    // メニューを更新
                    val profession = rankManager.getPlayerProfession(player.uniqueId) ?: return
                    val holder = event.view.topInventory.holder as? ProfessionMainMenuGuiHolder ?: return
                    renderProfessionMainMenu(holder.backingInventory, player, profession)
                }
            }
            MAIN_MENU_SETTINGS_SLOT -> {
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.8f, 2.0f)
                openProfessionSettingsMenu(player)
            }
            MAIN_MENU_PLAYER_INFO_SLOT, MAIN_MENU_HINT_SLOT -> {
            }
        }
    }

    @EventHandler
    fun onProfessionSettingsMenuClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? ProfessionSettingsGuiHolder ?: return
        event.isCancelled = true

        val clickedSlot = event.rawSlot
        if (clickedSlot !in 0 until event.view.topInventory.size) {
            return
        }

        val player = event.whoClicked as? Player ?: return
        val playerProfession = rankManager.getPlayerProfession(player.uniqueId) ?: return

        when (clickedSlot) {
            SETTINGS_MENU_BOSSBAR_SLOT -> {
                val currentMode = rankManager.getProfessionBossBarDisplayMode(player.uniqueId)
                val nextMode = currentMode.next()
                rankManager.setProfessionBossBarDisplayMode(player.uniqueId, nextMode)
                rankManager.savePlayerProfession(player.uniqueId)
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.8f, 2.0f)
                renderProfessionSettingsMenu(holder.backingInventory, playerProfession)
            }
            SETTINGS_MENU_LEVELUP_NOTIFY_SLOT -> {
                val current = rankManager.isLevelUpNotificationEnabled(player.uniqueId)
                rankManager.setLevelUpNotificationEnabled(player.uniqueId, !current)
                rankManager.savePlayerProfession(player.uniqueId)
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.8f, 2.0f)
                renderProfessionSettingsMenu(holder.backingInventory, playerProfession)
            }
            SETTINGS_MENU_BACK_SLOT -> {
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.8f, 1.0f)
                openProfessionMainMenu(player)
            }
        }
    }

    @EventHandler
    fun onProfessionSettingsMenuDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder !is ProfessionSettingsGuiHolder) {
            return
        }
        event.isCancelled = true
    }

    private fun openPrestigeConfirmDialogFirst(player: Player) {
        if (!rankManager.canPrestige(player.uniqueId)) {
            player.sendMessage(messageProvider.getMessage("rank.prestige.cannot"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        val profession = rankManager.getPlayerProfession(player.uniqueId)?.profession ?: return
        val professionName = messageProvider.getProfessionName(profession)
        val prestigeLevel = rankManager.getPrestigeLevel(player.uniqueId)

        val yesButton = io.papermc.paper.registry.data.dialog.ActionButton.builder(
            withoutItalic(toComponent(messageProvider.getMessage("rank.skill.gui.dialog.confirm")))
        )
            .width(150)
            .action(
                io.papermc.paper.registry.data.dialog.action.DialogAction.customClick(
                    io.papermc.paper.registry.data.dialog.action.DialogActionCallback { _, audience ->
                        val target = audience as? Player ?: return@DialogActionCallback
                        openPrestigeConfirmDialogSecond(target)
                    },
                    net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).build()
                )
            )
            .build()

        val noButton = io.papermc.paper.registry.data.dialog.ActionButton.builder(
            withoutItalic(toComponent(messageProvider.getMessage("rank.skill.gui.dialog.cancel")))
        )
            .width(150)
            .build()

        val dialog = io.papermc.paper.dialog.Dialog.create { factory ->
            factory.empty()
                .base(
                    io.papermc.paper.registry.data.dialog.DialogBase.builder(
                        withoutItalic(toComponent(messageProvider.getMessage("rank.prestige.dialog.first.title")))
                    )
                        .body(
                            listOf(
                                io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(
                                    withoutItalic(
                                        toComponent(
                                            messageProvider.getMessage(
                                                "rank.prestige.dialog.first.body",
                                                "profession" to professionName,
                                                "level" to prestigeLevel
                                            )
                                        )
                                    ),
                                    280
                                )
                            )
                        )
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction.CLOSE)
                        .build()
                )
                .type(io.papermc.paper.registry.data.dialog.type.DialogType.confirmation(yesButton, noButton))
        }

        player.showDialog(dialog)
    }

    private fun openPrestigeConfirmDialogSecond(player: Player) {
        val profession = rankManager.getPlayerProfession(player.uniqueId)?.profession ?: return
        val professionName = messageProvider.getProfessionName(profession)
        val prestigeLevel = rankManager.getPrestigeLevel(player.uniqueId)

        val yesButton = io.papermc.paper.registry.data.dialog.ActionButton.builder(
            withoutItalic(toComponent(messageProvider.getMessage("rank.skill.gui.dialog.confirm")))
        )
            .width(150)
            .action(
                io.papermc.paper.registry.data.dialog.action.DialogAction.customClick(
                    io.papermc.paper.registry.data.dialog.action.DialogActionCallback { _, audience ->
                        val target = audience as? Player ?: return@DialogActionCallback
                        executePrestige(target)
                    },
                    net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).build()
                )
            )
            .build()

        val noButton = io.papermc.paper.registry.data.dialog.ActionButton.builder(
            withoutItalic(toComponent(messageProvider.getMessage("rank.skill.gui.dialog.cancel")))
        )
            .width(150)
            .build()

        val dialog = io.papermc.paper.dialog.Dialog.create { factory ->
            factory.empty()
                .base(
                    io.papermc.paper.registry.data.dialog.DialogBase.builder(
                        withoutItalic(toComponent(messageProvider.getMessage("rank.prestige.dialog.second.title")))
                    )
                        .body(
                            listOf(
                                io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(
                                    withoutItalic(
                                        toComponent(
                                            messageProvider.getMessage(
                                                "rank.prestige.dialog.second.body",
                                                "profession" to professionName,
                                                "level" to prestigeLevel
                                            )
                                        )
                                    ),
                                    280
                                )
                            )
                        )
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction.CLOSE)
                        .build()
                )
                .type(io.papermc.paper.registry.data.dialog.type.DialogType.confirmation(yesButton, noButton))
        }

        player.showDialog(dialog)
    }

    private fun executePrestige(player: Player) {
        val success = rankManager.executePrestige(player.uniqueId)
        if (!success) {
            player.sendMessage(messageProvider.getMessage("rank.prestige.failed"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        player.closeInventory()
        openProfessionMainMenu(player)
    }

    @EventHandler
    fun onProfessionMainMenuDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder !is ProfessionMainMenuGuiHolder) {
            return
        }
        event.isCancelled = true
    }

    @EventHandler
    fun onProfessionSelectionGuiClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? ProfessionSelectionGuiHolder ?: return
        event.isCancelled = true

        val clickedSlot = event.rawSlot
        if (clickedSlot !in 0 until event.view.topInventory.size) {
            return
        }

        val player = event.whoClicked as? Player ?: return
        
        // 既に職業を持っている場合は何もしない
        if (rankManager.hasProfession(player.uniqueId)) {
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }
        
        // 職業スロットの定義
        val professionSlots = mapOf(
            // Row3: GATHERING + CRAFTING
            20 to Profession.LUMBERJACK,
            21 to Profession.MINER,
            22 to Profession.FARMER,
            23 to Profession.BREWER,
            24 to Profession.COOK,
            // Row4: COMBAT + CREATIVE
            29 to Profession.SWORDSMAN,
            30 to Profession.WARRIOR,
            31 to Profession.GARDENER,
            32 to Profession.CARPENTER
        )
        
        val selectedProfession = professionSlots[clickedSlot] ?: return
        
        // 確認ダイアログを表示
        showProfessionConfirmDialog(player, selectedProfession)
    }

    @EventHandler
    fun onProfessionSelectionGuiDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder !is ProfessionSelectionGuiHolder) {
            return
        }
        event.isCancelled = true
    }

    @EventHandler
    fun onTutorialRankMenuClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? TutorialRankMenuGuiHolder ?: return
        event.isCancelled = true

        val clickedSlot = event.rawSlot
        if (clickedSlot !in 0 until event.view.topInventory.size) {
            return
        }

        val player = event.whoClicked as? Player ?: return
        when (clickedSlot) {
            TUTORIAL_MENU_ROUTE_LEFT_SLOT -> {
                if (holder.page > TUTORIAL_MENU_PAGE_FIRST) {
                    holder.page -= 1
                    renderTutorialRankMenu(holder.backingInventory, player, holder.page)
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.8f, 1.2f)
                }
            }

            TUTORIAL_MENU_ROUTE_RIGHT_SLOT -> {
                if (holder.page < TUTORIAL_MENU_PAGE_SECOND) {
                    holder.page += 1
                    renderTutorialRankMenu(holder.backingInventory, player, holder.page)
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.8f, 1.2f)
                }
            }
        }
    }

    @EventHandler
    fun onTutorialRankMenuDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder !is TutorialRankMenuGuiHolder) {
            return
        }
        event.isCancelled = true
    }

    @EventHandler
    fun onTaskInfoGuiClick(event: InventoryClickEvent) {
        if (event.view.topInventory.holder !is TaskInfoGuiHolder) {
            return
        }
        event.isCancelled = true
    }

    @EventHandler
    fun onTaskInfoGuiDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder !is TaskInfoGuiHolder) {
            return
        }
        event.isCancelled = true
    }

    private class TutorialRankMenuGuiHolder(
        var page: Int
    ) : InventoryHolder {
        lateinit var backingInventory: Inventory

        override fun getInventory(): Inventory = backingInventory
    }

    private class TaskInfoGuiHolder : InventoryHolder {
        lateinit var backingInventory: Inventory

        override fun getInventory(): Inventory = backingInventory
    }

    private enum class TutorialRankTaskDisplayMode {
        COMPLETED,
        CURRENT,
        FUTURE,
        NONE
    }

    private data class SkillTreeGuiState(
        val profession: Profession,
        var selectedSkillId: String,
        var acquiredSkills: Set<String>,
        var currentLevel: Int,
        var currentExp: Long,
        val slotToSkillId: MutableMap<Int, String> = mutableMapOf(),
        val routeSlotToSkillId: MutableMap<Int, RouteTransition> = mutableMapOf(),
        val laneBySkillId: MutableMap<String, SkillTreeLane> = mutableMapOf(),
        var viewportStartX: Int? = null,
        var preferredLane: SkillTreeLane? = null,
        var currentSelectedLane: SkillTreeLane = SkillTreeLane.CENTER,
        var lastAction: String? = null,
        var prestigeSkills: Set<String> = emptySet(),
        var prestigeLevel: Int = 0,
        var isMaxLevel: Boolean = false
    )

    private data class RouteTransition(
        val skillId: String? = null,
        val lane: SkillTreeLane? = null,
        val scrollDeltaX: Int = 0
    )

    private data class VirtualPoint(
        val x: Int,
        val y: Int
    )

    private data class RouteEdgeRef(
        val fromSkillId: String,
        val toSkillId: String
    )

    private data class SkillTreeRenderModel(
        val nodePositions: Map<String, VirtualPoint>,
        val routeCells: Map<VirtualPoint, List<RouteEdgeRef>>
    )

    private data class RouteSlotRender(
        var acquired: Boolean = false,
        var transition: RouteTransition? = null,
        var guideKey: String? = null
    )

    private class SkillTreeGuiHolder(val state: SkillTreeGuiState) : InventoryHolder {
        lateinit var backingInventory: Inventory

        override fun getInventory(): Inventory = backingInventory
    }

    private class ProfessionMainMenuGuiHolder : InventoryHolder {
        lateinit var backingInventory: Inventory

        override fun getInventory(): Inventory = backingInventory
    }

    private class ProfessionSettingsGuiHolder : InventoryHolder {
        lateinit var backingInventory: Inventory

        override fun getInventory(): Inventory = backingInventory
    }

    private enum class SkillTreeLane {
        TOP,
        CENTER,
        BOTTOM
    }

    private enum class SkillTreeLayout {
        CHILD_ONE,
        CHILD_TWO,
        MERGE
    }

    companion object {
        private val LEGACY_SERIALIZER: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()
        private const val VIEWPORT_DEPTH_SPACING = 4
        private const val VIEWPORT_SELECTED_COLUMN = 2

        private const val NAV_LEFT_SLOT = 36
        private const val NAV_CENTER_SLOT = 40
        private const val NAV_RIGHT_SLOT = 44

        private const val SELECTED_SLOT_CHILD_ONE = 20
        private const val CHILD_SLOT_CHILD_ONE = 24

        private const val SELECTED_SLOT_CHILD_TWO = 20
        private const val CHILD_TOP_SLOT_CHILD_TWO = 15
        private const val CHILD_BOTTOM_SLOT_CHILD_TWO = 33

        private const val MERGE_LEFT_TOP_SLOT = 11
        private const val MERGE_LEFT_BOTTOM_SLOT = 29
        private const val CHILD_SLOT_MERGE = 24

        private val CHILD_ONE_SELECTED_INCOMING_SLOTS = listOf(18, 19)
        private val CHILD_ONE_TO_CHILD_SLOTS = listOf(21, 22, 23)
        private val CHILD_ONE_CHILD_OUTGOING_SLOTS = listOf(25, 26)

        private val CHILD_TWO_SELECTED_INCOMING_SLOTS = listOf(18, 19)
        private val CHILD_TWO_SHARED_BRANCH_SLOTS = listOf(21, 22)
        private val CHILD_TWO_TO_TOP_CHILD_SLOTS = listOf(13, 14)
        private val CHILD_TWO_TOP_CHILD_OUTGOING_SLOTS = listOf(16, 17)
        private val CHILD_TWO_TO_BOTTOM_CHILD_SLOTS = listOf(31, 32)
        private val CHILD_TWO_BOTTOM_CHILD_OUTGOING_SLOTS = listOf(34, 35)

        private val MERGE_LEFT_TOP_INCOMING_SLOTS = listOf(9, 10)
        private val MERGE_LEFT_BOTTOM_INCOMING_SLOTS = listOf(27, 28)
        private val MERGE_TOP_TO_CHILD_SLOTS = listOf(12, 13)
        private val MERGE_BOTTOM_TO_CHILD_SLOTS = listOf(30, 31)
        private val MERGE_SHARED_TO_CHILD_SLOTS = listOf(22, 23)
        private val MERGE_CHILD_OUTGOING_SLOTS = listOf(25, 26)

        // 職業メインメニュー スロット定数
        private const val MAIN_MENU_SKILL_TREE_SLOT = 20
        private const val MAIN_MENU_PROFESSION_OVERVIEW_SLOT = 22
        private const val MAIN_MENU_SETTINGS_SLOT = 24
        private const val MAIN_MENU_PLAYER_INFO_SLOT = 40
        private const val MAIN_MENU_MODE_SWITCH_SLOT = 38
        private const val MAIN_MENU_HINT_SLOT = 42

        private const val SETTINGS_MENU_BOSSBAR_SLOT = 10
        private const val SETTINGS_MENU_LEVELUP_NOTIFY_SLOT = 11
        private const val SETTINGS_MENU_BACK_SLOT = 18

        private const val TUTORIAL_MENU_PAGE_FIRST = 1
        private const val TUTORIAL_MENU_PAGE_SECOND = 2
        private const val TUTORIAL_MENU_ROUTE_LEFT_SLOT = 18
        private const val TUTORIAL_MENU_ROUTE_RIGHT_SLOT = 26
        private const val TUTORIAL_MENU_PLAYER_SLOT = 40
        private const val TUTORIAL_RANK_TASK_LORE_LIMIT = 10
        private val TUTORIAL_MENU_ROUTE_SLOTS_PAGE1 = listOf(20, 21, 23, 24, 26)
        private val TUTORIAL_MENU_ROUTE_SLOTS_PAGE2 = listOf(18, 20, 21, 23, 24, 25, 26)
        private val TUTORIAL_MENU_ROUTE_LEADING_RANK_PAGE1 = mapOf(
            20 to TutorialRank.NEWBIE,
            21 to TutorialRank.NEWBIE,
            23 to TutorialRank.VISITOR,
            24 to TutorialRank.VISITOR,
            26 to TutorialRank.PIONEER
        )
        private val TUTORIAL_MENU_ROUTE_LEADING_RANK_PAGE2 = mapOf(
            20 to TutorialRank.ADVENTURER,
            21 to TutorialRank.ADVENTURER,
            23 to TutorialRank.ATTAINER,
            24 to TutorialRank.ATTAINER,
            25 to TutorialRank.ATTAINER,
            26 to TutorialRank.ATTAINER
        )

        // 区切り線
        private const val BAR = "§7§m――――――――――――――――――――――――――――――"
    }
    
    /**
     * タスク進捗をリセット
     */
    private fun handleTaskReset(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage("§c使用法: /rank task-reset <player>")
            return false
        }
        
        val targetPlayer = Bukkit.getPlayer(args[1])
        if (targetPlayer == null) {
            sender.sendMessage("§cプレイヤーが見つかりません")
            return false
        }
        
        val uuid = targetPlayer.uniqueId
        val tutorial = rankManager.getPlayerTutorial(uuid)
        tutorial.taskProgress.reset()
        
        sender.sendMessage("§a${targetPlayer.name} のタスク進捗をリセットしました")
        return true
    }
    
    /**
     * 現在のランクのすべてのタスクを完了扱いに
     */
    private fun handleCompleteTask(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage("§c使用法: /rank complete-task <player>")
            return false
        }
        
        val targetPlayer = Bukkit.getPlayer(args[1])
        if (targetPlayer == null) {
            sender.sendMessage("§cプレイヤーが見つかりません")
            return false
        }
        
        val uuid = targetPlayer.uniqueId
        val tutorial = rankManager.getPlayerTutorial(uuid)
        
        // 非常に大きな値を設定してすべてのタスクを完了扱いに
        tutorial.taskProgress.playTime = Int.MAX_VALUE.toLong()
        tutorial.taskProgress.vanillaExp = Long.MAX_VALUE
        tutorial.taskProgress.mobKills.clear()
        tutorial.taskProgress.blockMines.clear()
        tutorial.taskProgress.bossKills.clear()
        tutorial.taskProgress.items.clear()
        
        sender.sendMessage("§a${targetPlayer.name} のタスクをすべて完了扱いにしました")
        
        // ランクアップを試みる
        if (rankManager.rankUpByTask(uuid)) {
            sender.sendMessage("§a${targetPlayer.name} がランクアップしました！")
        }
        
        return true
    }
    
    /**
     * プレイ時間（分）を時間と分に変換
     * @param minutes プレイ時間（分）
     * @return "X時間Y分" または "Y分" の形式
     */
    private fun formatPlayTime(minutes: Long): String {
        return if (minutes >= 60) {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins == 0L) {
                "${hours}時間"
            } else {
                "${hours}時間${mins}分"
            }
        } else {
            "${minutes}分"
        }
    }

    private fun resolveLowestDefinedTutorialRank(): TutorialRank {
        val lowestRankId = taskLoader?.getLowestDefinedRankId() ?: return TutorialRank.NEWBIE
        return TutorialRank.entries.firstOrNull { it.name.equals(lowestRankId, ignoreCase = true) }
            ?: TutorialRank.NEWBIE
    }
    
    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage("§6=== ランクシステムコマンド ===")
        sender.sendMessage("§a/rank add-exp <player> <amount> §7- 職業経験値を追加")
        sender.sendMessage("§a/rank set-prof-level <player> <level> §7- 職業レベルを設定")
        sender.sendMessage("§a/rank set <ランク名> §7- 自分のランクを設定")
        sender.sendMessage("§a/rank reset <player> §7- 最低ランクにリセット")
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String> {
        return when {
            args.size == 1 -> listOf("add-exp", "set-prof-level", "set", "reset")
                .filter { it.startsWith(args[0].lowercase()) }
            args.size == 2 && args[0].equals("set", ignoreCase = true) ->
                (TutorialRank.entries.map { it.name.lowercase() } + Profession.entries.map { it.id })
                    .filter { it.startsWith(args[1].lowercase()) }
            args.size == 2 -> Bukkit.getOnlinePlayers().map { it.name }
                .filter { it.startsWith(args[1], ignoreCase = true) }
            else -> emptyList()
        }
    }
}
