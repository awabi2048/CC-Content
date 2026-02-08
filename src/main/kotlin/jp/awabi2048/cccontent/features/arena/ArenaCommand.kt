package jp.awabi2048.cccontent.features.arena

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ArenaCommand(private val arenaManager: ArenaManager) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("cc-content.arena.admin")) {
            sender.sendMessage("§c権限がありません")
            return true
        }

        if (args.isEmpty()) {
            showUsage(sender)
            return true
        }

        return when (args[0].lowercase()) {
            "start" -> handleStart(sender, args)
            "stop" -> handleStop(sender, args)
            "theme" -> handleTheme(sender, args)
            else -> {
                showUsage(sender)
                true
            }
        }
    }

    private fun handleStart(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 3) {
            sender.sendMessage("§c使用法: /arenaa start <player|@s> <waves> [theme]")
            return true
        }

        val target = when (args[1]) {
            "@s" -> sender as? Player
            else -> Bukkit.getPlayer(args[1])
        }
        if (target == null) {
            sender.sendMessage("§c対象プレイヤーが見つかりません")
            return true
        }

        val waves = args[2].toIntOrNull()
        if (waves == null || waves <= 0) {
            sender.sendMessage("§cwaves は1以上の整数で指定してください")
            return true
        }

        val theme = args.getOrNull(3)
        when (val result = arenaManager.startSession(target, waves, theme)) {
            is ArenaStartResult.Success -> {
                sender.sendMessage("§a${target.name} のアリーナを開始しました (theme=${result.themeId}, waves=${result.waves})")
            }
            is ArenaStartResult.Error -> {
                sender.sendMessage("§c開始失敗: ${result.message}")
            }
        }
        return true
    }

    private fun handleStop(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage("§c使用法: /arenaa stop <player>")
            return true
        }
        val target = Bukkit.getPlayer(args[1])
        if (target == null) {
            sender.sendMessage("§c対象プレイヤーが見つかりません")
            return true
        }
        val stopped = arenaManager.stopSession(target, "§c管理コマンドによりアリーナを停止しました")
        if (stopped) {
            sender.sendMessage("§a${target.name} のアリーナを停止しました")
        } else {
            sender.sendMessage("§e${target.name} はアリーナセッションに参加していません")
        }
        return true
    }

    private fun handleTheme(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2 || !args[1].equals("list", ignoreCase = true)) {
            sender.sendMessage("§c使用法: /arenaa theme list")
            return true
        }

        val ids = arenaManager.getThemeIds().sorted()
        if (ids.isEmpty()) {
            sender.sendMessage("§e利用可能なテーマがありません")
            return true
        }

        sender.sendMessage("§6[Arena] 利用可能テーマ: ${ids.joinToString(", ")}")
        return true
    }

    private fun showUsage(sender: CommandSender) {
        sender.sendMessage("§6=== Arena 管理コマンド ===")
        sender.sendMessage("§f/arenaa start <player|@s> <waves> [theme]")
        sender.sendMessage("§f/arenaa stop <player>")
        sender.sendMessage("§f/arenaa theme list")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!sender.hasPermission("cc-content.arena.admin")) return emptyList()

        return when (args.size) {
            1 -> listOf("start", "stop", "theme").filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> when (args[0].lowercase()) {
                "start" -> listOf("@s") + Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                "stop" -> arenaManager.getActiveSessionPlayerNames().filter { it.startsWith(args[1], ignoreCase = true) }
                "theme" -> listOf("list").filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "start" -> listOf("1", "3", "5", "10").filter { it.startsWith(args[2], ignoreCase = true) }
                else -> emptyList()
            }
            4 -> when (args[0].lowercase()) {
                "start" -> arenaManager.getThemeIds().filter { it.startsWith(args[3], ignoreCase = true) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
