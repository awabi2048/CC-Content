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
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.no_permission", "&c権限がありません"))
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
        if (args.size < 4) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.usage.start",
                    "&c使用法: /arenaa start <player|@s> <mob_type> <difficulty> [theme]"
                )
            )
            return true
        }

        val target = when (args[1]) {
            "@s" -> sender as? Player
            else -> Bukkit.getPlayer(args[1])
        }
        if (target == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.target_not_found", "&c対象プレイヤーが見つかりません"))
            return true
        }

        val mobTypeId = args[2]
        val difficultyId = args[3]
        val theme = args.getOrNull(4)
        when (val result = arenaManager.startSession(target, mobTypeId, difficultyId, theme)) {
            is ArenaStartResult.Success -> {
                sender.sendMessage(
                    ArenaI18n.text(
                        sender,
                        "arena.messages.command.start_success",
                        "&a{player} のアリーナを開始しました (mob_type={mob_type}, difficulty={difficulty}, theme={theme}, waves={waves})",
                        "player" to target.name,
                        "mob_type" to result.mobTypeId,
                        "difficulty" to result.difficultyId,
                        "theme" to result.themeId,
                        "waves" to result.waves
                    )
                )
            }
            is ArenaStartResult.Error -> {
                sender.sendMessage(
                    ArenaI18n.text(
                        sender,
                        result.messageKey,
                        result.fallback,
                        *result.placeholders
                    )
                )
            }
        }
        return true
    }

    private fun handleStop(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.usage.stop", "&c使用法: /arenaa stop <player>"))
            return true
        }
        val target = Bukkit.getPlayer(args[1])
        if (target == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.target_not_found", "&c対象プレイヤーが見つかりません"))
            return true
        }
        val stopped = arenaManager.stopSession(
            target,
            ArenaI18n.text(target, "arena.messages.session.stopped_by_admin", "&c管理コマンドによりアリーナを停止しました")
        )
        if (stopped) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.stop_success",
                    "&a{player} のアリーナを停止しました",
                    "player" to target.name
                )
            )
        } else {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.stop_not_in_session",
                    "&e{player} はアリーナセッションに参加していません",
                    "player" to target.name
                )
            )
        }
        return true
    }

    private fun handleTheme(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2 || !args[1].equals("list", ignoreCase = true)) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.usage.theme", "&c使用法: /arenaa theme list"))
            return true
        }

        val ids = arenaManager.getThemeIds().sorted()
        if (ids.isEmpty()) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.theme_none", "&e利用可能なテーマがありません"))
            return true
        }

        sender.sendMessage(
            ArenaI18n.text(
                sender,
                "arena.messages.command.theme_list",
                "&6[Arena] 利用可能テーマ: {themes}",
                "themes" to ids.joinToString(", ")
            )
        )
        return true
    }

    private fun showUsage(sender: CommandSender) {
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.header", "&6=== Arena 管理コマンド ==="))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.start", "&f/arenaa start <player|@s> <mob_type> <difficulty> [theme]"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.stop", "&f/arenaa stop <player>"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.theme", "&f/arenaa theme list"))
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
                "start" -> arenaManager.getMobTypeIds().filter { it.startsWith(args[2], ignoreCase = true) }
                else -> emptyList()
            }
            4 -> when (args[0].lowercase()) {
                "start" -> arenaManager.getDifficultyIds().filter { it.startsWith(args[3], ignoreCase = true) }
                else -> emptyList()
            }
            5 -> when (args[0].lowercase()) {
                "start" -> arenaManager.getThemeIds().filter { it.startsWith(args[4], ignoreCase = true) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
