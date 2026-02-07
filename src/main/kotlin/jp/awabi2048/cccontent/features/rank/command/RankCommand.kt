package jp.awabi2048.cccontent.features.rank.command

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.listener.TutorialInventoryHelper
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import jp.awabi2048.cccontent.features.rank.tutorial.task.TaskProgress
import jp.awabi2048.cccontent.features.rank.tutorial.task.TaskRequirement
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
import org.bukkit.Bukkit
import org.bukkit.Material
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
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.format.TextDecoration

/**
 * ランクシステムのデバッグコマンド実装
 */
class RankCommand(
    private val rankManager: RankManager,
    private val messageProvider: MessageProvider,
    private val taskLoader: jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskLoader? = null,
    private val taskChecker: jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskChecker? = null,
    private val translator: jp.awabi2048.cccontent.features.rank.tutorial.task.EntityBlockTranslator? = null
) : CommandExecutor, TabCompleter, Listener {
    
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
            "rankup" -> handleRankUp(sender, args)
            "profession" -> handleProfession(sender, args)
            "add-prof-exp" -> handleAddProfExp(sender, args)
            "skill" -> handleSkill(sender, args)
            "skill-available" -> handleSkillAvailable(sender, args)
            "reset" -> handleReset(sender, args)
            "info" -> handleInfo(sender, args)
            "task-info" -> handleTaskInfo(sender, args)
            "task-reset" -> handleTaskReset(sender, args)
            "complete-task" -> handleCompleteTask(sender, args)
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
        
        val rankChanged = rankManager.addTutorialExp(player.uniqueId, amount)
        val rank = rankManager.getTutorialRank(player.uniqueId)
        
        if (rankChanged) {
            sender.sendMessage("§a${player.name} がランクアップしました: $rank")
            player.sendMessage("§aランクアップ！§6${rank.name}§aになりました")
        } else {
            sender.sendMessage("§a${player.name} に ${amount}EXP を追加しました")
        }
        
        return true
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
    
    private fun handleAddProfExp(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 3) {
            sender.sendMessage("§c使用法: /rank add-prof-exp <player> <amount>")
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
    
    private fun handleSkill(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 3) {
            sender.sendMessage("§c使用法: /rank skill <player> <skillId>")
            return false
        }
        
        val player = Bukkit.getPlayer(args[1])
        if (player == null) {
            sender.sendMessage("§cプレイヤーが見つかりません: ${args[1]}")
            return false
        }
        
        val skillId = args[2]
        val success = rankManager.acquireSkill(player.uniqueId, skillId)
        
        if (success) {
            sender.sendMessage("§a${player.name} がスキル $skillId を習得しました")
        } else {
            sender.sendMessage("§cスキル習得に失敗しました")
        }
        
        return success
    }
    
    private fun handleSkillAvailable(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage("§c使用法: /rank skill-available <player>")
            return false
        }
        
        val player = Bukkit.getPlayer(args[1])
        if (player == null) {
            sender.sendMessage("§cプレイヤーが見つかりません: ${args[1]}")
            return false
        }
        
        val available = rankManager.getAvailableSkills(player.uniqueId)
        if (available.isEmpty()) {
            sender.sendMessage("§c習得可能なスキルはありません")
        } else {
            sender.sendMessage("§a習得可能なスキル: ${available.joinToString(", ")}")
        }
        
        return true
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
        
        val success = rankManager.resetProfession(player.uniqueId)
        if (success) {
            sender.sendMessage("§a${player.name} の職業をリセットしました")
        } else {
            sender.sendMessage("§c${player.name} は職業を選択していません")
        }
        
        return success
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
        
        sender.sendMessage("§6=== ${targetPlayer.name} のランク情報 ===")
        sender.sendMessage("§aチュートリアルランク: ${tutorial.currentRank.name}")
        sender.sendMessage("§aランクレベル: ${tutorial.currentRank.level}")
        
        val profession = rankManager.getPlayerProfession(uuid)
        if (profession != null) {
            sender.sendMessage("§a職業: ${profession.profession.id}")
            sender.sendMessage("§a習得スキル: ${profession.acquiredSkills.joinToString(", ")}")
            sender.sendMessage("§a職業経験値: ${profession.currentExp}")
        } else {
            sender.sendMessage("§c職業: 未選択")
        }
        
        return true
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
        
        if (taskLoader == null || taskChecker == null) {
            sender.sendMessage(messageProvider.getMessage("rank.tutorial_task_info.system_not_initialized"))
            return false
        }
        
        val uuid = targetPlayer.uniqueId
        val tutorial = rankManager.getPlayerTutorial(uuid)
        val progress = tutorial.taskProgress
        val requirement = taskLoader.getRequirement(tutorial.currentRank.name)
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
                details += buildTranslatedTargetLine(nameComponent, done, minOf(current, required), required)
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
                details += buildTranslatedTargetLine(nameComponent, done, minOf(current, required), required)
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
                details += buildTranslatedTargetLine(nameComponent, done, minOf(current, required), required)
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
                details += buildTranslatedTargetLine(nameComponent, done, minOf(current, required), required)
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

    private fun buildTranslatedTargetLine(nameComponent: Component, done: Boolean, current: Int, required: Int): Component {
        val prefix = toComponent(
            messageProvider.getMessage(
                "rank.tutorial_task_info.line.target_prefix",
                "status" to statusIcon(done)
            )
        )
        val suffix = toComponent(
            messageProvider.getMessage(
                "rank.tutorial_task_info.line.target_suffix",
                "current" to current,
                "required" to required
            )
        )
        return prefix.append(nameComponent).append(suffix)
    }

    private fun toComponent(text: String): Component = LEGACY_SERIALIZER.deserialize(text)

    private fun withoutItalic(component: Component): Component =
        component.decoration(TextDecoration.ITALIC, false)

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

    private class TaskInfoGuiHolder : InventoryHolder {
        lateinit var backingInventory: Inventory

        override fun getInventory(): Inventory = backingInventory
    }

    companion object {
        private val LEGACY_SERIALIZER: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()
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
            "${hours}時間${mins}分"
        } else {
            "${minutes}分"
        }
    }
    
    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage("§6=== ランクシステムコマンド ===")
        sender.sendMessage("§a/rank add-exp <player> <amount> §7- チュートリアル経験値を追加")
        sender.sendMessage("§a/rank rankup <player> §7- 次のランクにランクアップ")
        sender.sendMessage("§a/rank profession <player> <id> §7- 職業を選択/変更")
        sender.sendMessage("§a/rank add-prof-exp <player> <amount> §7- 職業経験値を追加")
        sender.sendMessage("§a/rank skill <player> <skillId> §7- スキルを習得")
        sender.sendMessage("§a/rank skill-available <player> §7- 習得可能なスキル一覧")
        sender.sendMessage("§a/rank reset <player> §7- 職業をリセット")
        sender.sendMessage("§a/rank info [player] §7- ランク情報表示")
        sender.sendMessage("§a/rank task-info <player> §7- タスク進捗を表示")
        sender.sendMessage("§a/rank task-reset <player> §7- タスク進捗をリセット")
        sender.sendMessage("§a/rank complete-task <player> §7- タスクをすべて完了扱いに")
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String> {
        return when {
            args.size == 1 -> listOf("add-exp", "rankup", "profession", "add-prof-exp", "skill", "skill-available", "reset", "info", "task-info", "task-reset", "complete-task")
                .filter { it.startsWith(args[0].lowercase()) }
            args.size == 2 -> Bukkit.getOnlinePlayers().map { it.name }
                .filter { it.startsWith(args[1], ignoreCase = true) }
            args.size == 3 && args[0].equals("profession", ignoreCase = true) -> 
                listOf("lumberjack", "brewer", "miner")
                    .filter { it.startsWith(args[2].lowercase()) }
            else -> emptyList()
        }
    }
}
