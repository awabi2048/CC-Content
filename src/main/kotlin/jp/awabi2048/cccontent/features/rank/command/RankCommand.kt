package jp.awabi2048.cccontent.features.rank.command

import jp.awabi2048.cccontent.features.rank.RankManager
import jp.awabi2048.cccontent.features.rank.tutorial.TutorialRank
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.localization.MessageProvider
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * ランクシステムのデバッグコマンド実装
 */
class RankCommand(
    private val rankManager: RankManager,
    private val messageProvider: MessageProvider,
    private val taskLoader: jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskLoader? = null,
    private val taskChecker: jp.awabi2048.cccontent.features.rank.tutorial.task.TutorialTaskChecker? = null,
    private val translator: jp.awabi2048.cccontent.features.rank.tutorial.task.EntityBlockTranslator? = null
) : CommandExecutor, TabCompleter {
    
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
            sender.sendMessage("§c使用法: /rank task-info <player>")
            return false
        }
        
        val targetPlayer = Bukkit.getPlayer(args[1])
        if (targetPlayer == null) {
            sender.sendMessage("§cプレイヤーが見つかりません")
            return false
        }
        
        if (taskLoader == null || taskChecker == null) {
            sender.sendMessage("§cタスクシステムが初期化されていません")
            return false
        }
        
        val uuid = targetPlayer.uniqueId
        val tutorial = rankManager.getPlayerTutorial(uuid)
        val progress = tutorial.taskProgress
        val requirement = taskLoader.getRequirement(tutorial.currentRank.name)
        
        sender.sendMessage("§6=== ${targetPlayer.name} のタスク進捗 ===")
        sender.sendMessage("§aランク: ${tutorial.currentRank.name}")
        
        // タスク要件が空（最終ランク）の場合
        if (requirement.isEmpty()) {
            sender.sendMessage("§a§lすべてのタスク完了！")
            return true
        }
        
        sender.sendMessage("§6--- タスク要件と進捗 ---")
        
        // プレイ時間
        if (requirement.playTimeMin > 0) {
            val progress_min = minOf(progress.playTime, requirement.playTimeMin.toLong())
            val percent = (progress_min.toDouble() / requirement.playTimeMin.toDouble() * 100).toInt()
            val status = if (progress.playTime >= requirement.playTimeMin) "§a✓" else "§c✗"
            val progressStr = formatPlayTime(progress.playTime)
            val requiredStr = formatPlayTime(requirement.playTimeMin.toLong())
            sender.sendMessage("$status プレイ時間: $progressStr / $requiredStr ($percent%)")
        }
        
        // モブ討伐
        requirement.mobKills.forEach { (mobType, required) ->
            val current = progress.getMobKillCount(mobType)
            val progress_count = minOf(current, required)
            val percent = (progress_count.toDouble() / required.toDouble() * 100).toInt()
            val status = if (current >= required) "§a✓" else "§c✗"
            // translator が存在する場合は翻訳、ない場合はそのまま表示
            val mobName = translator?.translateEntity(mobType) ?: mobType
            sender.sendMessage("$status $mobName 討伐: $current/$required ($percent%)")
        }
        
        // ブロック採掘
        requirement.blockMines.forEach { (blockType, required) ->
            val current = progress.getBlockMineCount(blockType)
            val progress_count = minOf(current, required)
            val percent = (progress_count.toDouble() / required.toDouble() * 100).toInt()
            val status = if (current >= required) "§a✓" else "§c✗"
            // translator が存在する場合は翻訳、ない場合はそのまま表示
            val blockName = translator?.translateBlock(blockType) ?: blockType
            sender.sendMessage("$status $blockName 採掘: $current/$required ($percent%)")
        }
        
        // バニラEXP
        if (requirement.vanillaExp > 0) {
            val progress_exp = minOf(progress.vanillaExp, requirement.vanillaExp)
            val percent = (progress_exp.toDouble() / requirement.vanillaExp.toDouble() * 100).toInt()
            val status = if (progress.vanillaExp >= requirement.vanillaExp) "§a✓" else "§c✗"
            sender.sendMessage("$status バニラEXP: $progress_exp/${requirement.vanillaExp} ($percent%)")
        }
        
        // アイテム
        requirement.itemsRequired.forEach { (material, required) ->
            val current = jp.awabi2048.cccontent.features.rank.listener.TutorialInventoryHelper.countItemInInventory(targetPlayer, material.uppercase())
            val progress_count = minOf(current, required)
            val percent = (progress_count.toDouble() / required.toDouble() * 100).toInt()
            val status = if (current >= required) "§a✓" else "§c✗"
            sender.sendMessage("$status $material 所持: $current/$required ($percent%)")
        }
        
        // ボス討伐
        requirement.bossKills.forEach { (bossType, required) ->
            val current = progress.getBossKillCount(bossType)
            val progress_count = minOf(current, required)
            val percent = (progress_count.toDouble() / required.toDouble() * 100).toInt()
            val status = if (current >= required) "§a✓" else "§c✗"
            sender.sendMessage("$status $bossType 討伐: $current/$required ($percent%)")
        }
        
        // 全体進捗
        val overallProgress = taskChecker.getProgress(progress, requirement, targetPlayer)
        val progressPercent = (overallProgress * 100).toInt()
        sender.sendMessage("§6--- 全体進捗: $progressPercent% ---")
        
        return true
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
