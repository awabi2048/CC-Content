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
    private val messageProvider: MessageProvider
) : CommandExecutor, TabCompleter {
    
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        // 権限確認
        if (!sender.hasPermission("cc.admin")) {
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
        sender.sendMessage("§a経験値: ${tutorial.currentExp}/${tutorial.currentRank.requiredExp}")
        
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
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String> {
        return when {
            args.size == 1 -> listOf("add-exp", "rankup", "profession", "add-prof-exp", "skill", "skill-available", "reset", "info")
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
