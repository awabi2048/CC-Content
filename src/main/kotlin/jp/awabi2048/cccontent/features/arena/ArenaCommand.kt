package jp.awabi2048.cccontent.features.arena

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import jp.awabi2048.cccontent.features.arena.quest.ArenaQuestService

class ArenaCommand(
    private val arenaManagerProvider: () -> ArenaManager? = { null },
    private val questService: ArenaQuestService? = null,
    private val sessionInfoMenu: ArenaSessionInfoMenu? = null,
    private val pedestalMenu: ArenaEnchantPedestalMenu? = null,
    private val featureEnabledProvider: () -> Boolean = { true },
    private val featureFailureReasonProvider: () -> String? = { null }
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("cc-content.arena.admin")) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.no_permission", "&c権限がありません"))
            return true
        }

        if (!featureEnabledProvider()) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable", "§cArena feature は初期化に失敗したため利用できません"))
            featureFailureReasonProvider()?.let { sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable_reason", "§7理由: {reason}", "reason" to it)) }
            return true
        }

        if (args.isEmpty()) {
            showUsage(sender)
            return true
        }

        return when (args[0].lowercase()) {
            "menu" -> handleMenu(sender)
            "start" -> handleStart(sender, args)
            "stop" -> handleStop(sender, args)
            "theme" -> handleTheme(sender, args)
            "broadcast" -> handleBroadcast(sender)
            "pedestal" -> handlePedestal(sender)
            else -> {
                showUsage(sender)
                true
            }
        }
    }

    private fun handleMenu(sender: CommandSender): Boolean {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.player_only", "§cこのコマンドはプレイヤーのみ実行できます"))
            return true
        }

        if (!featureEnabledProvider()) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable", "§cArena feature は初期化に失敗したため利用できません"))
            featureFailureReasonProvider()?.let { sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable_reason", "§7理由: {reason}", "reason" to it)) }
            return true
        }

        val manager = arenaManagerProvider()
        if (manager == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable", "§cArena feature は初期化に失敗したため利用できません"))
            featureFailureReasonProvider()?.let { sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable_reason", "§7理由: {reason}", "reason" to it)) }
            return true
        }

        val service = questService
        if (service == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.menu_open_failed", "§cアリーナメニューを開けませんでした"))
            return true
        }

        service.openMenu(player)
        return true
    }

    private fun handleStart(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 4) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.usage.start",
                    "&c使用法: /arenaa start <player|@s|@near> <star_count> <theme>"
                )
            )
            return true
        }

        val manager = arenaManagerProvider()
        if (manager == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable", "§cArena feature は初期化に失敗したため利用できません"))
            featureFailureReasonProvider()?.let { sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable_reason", "§7理由: {reason}", "reason" to it)) }
            return true
        }

        val (target, initialParticipants) = when (args[1].lowercase()) {
            "@s" -> {
                val self = sender as? Player
                if (self == null) {
                    sender.sendMessage("§c@s はプレイヤーのみ使用できます")
                    return true
                }
                self to emptyList()
            }
            "@near" -> {
                val self = sender as? Player
                if (self == null) {
                    sender.sendMessage("§c@near はプレイヤーのみ使用できます")
                    return true
                }
                val nearbyPlayers = (self.getNearbyEntities(16.0, 16.0, 16.0)
                    .mapNotNull { it as? Player } + self)
                    .distinctBy { it.uniqueId }
                self to nearbyPlayers
            }
            else -> {
                val player = Bukkit.getPlayer(args[1])
                if (player == null) {
                    sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.target_not_found", "&c対象プレイヤーが見つかりません"))
                    return true
                }
                player to emptyList()
            }
        }

        val starCount = args[2].toIntOrNull()
        if (starCount == null || starCount < 1 || starCount > 4) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.usage.start", "&c使用法: /arenaa start <player|@s|@near> <star_count(1-4)> <theme>"))
            return true
        }
        val difficultyId = "star_$starCount"
        val theme = args[3]

        val startedAt = System.nanoTime()
        when (val result = manager.startSession(
            target,
            difficultyId,
            theme,
            initialParticipants = initialParticipants
        )) {
            is ArenaStartResult.Success -> {
                sender.sendMessage(
                    ArenaI18n.text(
                        sender,
                        "arena.messages.command.start_success",
                        "&a{player} のアリーナを開始しました (difficulty={difficulty}, theme={theme}, waves={waves})",
                        "player" to target.name,
                        "mob_type" to result.themeId,
                        "difficulty" to result.difficultyDisplay,
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
        sender.sendMessage("§7処理時間: ${((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)} ms")
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
        val manager = arenaManagerProvider()
        if (manager == null) {
            sender.sendMessage("§cArena feature は初期化に失敗したため利用できません")
            featureFailureReasonProvider()?.let { sender.sendMessage("§7理由: $it") }
            return true
        }

        val stopped = manager.stopSession(
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

        val manager = arenaManagerProvider()
        if (manager == null) {
            sender.sendMessage("§cArena feature は初期化に失敗したため利用できません")
            featureFailureReasonProvider()?.let { sender.sendMessage("§7理由: $it") }
            return true
        }

        val ids = manager.getThemeIds().sorted()
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

    private fun handleBroadcast(sender: CommandSender): Boolean {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.player_only", "§cこのコマンドはプレイヤーのみ実行できます"))
            return true
        }

        val menu = sessionInfoMenu
        if (menu == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable", "§cArena feature は初期化に失敗したため利用できません"))
            return true
        }

        menu.openMenu(player)
        return true
    }

    private fun handlePedestal(sender: CommandSender): Boolean {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.player_only", "§cこのコマンドはプレイヤーのみ実行できます"))
            return true
        }

        val menu = pedestalMenu
        if (menu == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable", "§cArena feature は初期化に失敗したため利用できません"))
            return true
        }

        menu.openMenu(player)
        return true
    }

    private fun showUsage(sender: CommandSender) {
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.header", "&6=== Arena 管理コマンド ==="))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.menu", "&f/arenaa menu"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.start", "&f/arenaa start <player|@s|@near> <star_count> <theme>"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.stop", "&f/arenaa stop <player>"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.theme", "&f/arenaa theme list"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.broadcast", "&f/arenaa broadcast"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.pedestal", "&f/arenaa pedestal"))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!sender.hasPermission("cc-content.arena.admin")) return emptyList()
        if (!featureEnabledProvider()) return emptyList()

        return when (args.size) {
            1 -> listOf("menu", "start", "stop", "theme", "broadcast", "pedestal").filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> when (args[0].lowercase()) {
                "menu" -> emptyList()
                "start" -> listOf("@s", "@near") + Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                "stop" -> arenaManagerProvider()?.getActiveSessionPlayerNames()?.filter { it.startsWith(args[1], ignoreCase = true) } ?: emptyList()
                "theme" -> listOf("list").filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "start" -> listOf("1", "2", "3", "4").filter { it.startsWith(args[2], ignoreCase = true) }
                else -> emptyList()
            }
            4 -> when (args[0].lowercase()) {
                "start" -> arenaManagerProvider()?.getThemeIds()?.filter { it.startsWith(args[3], ignoreCase = true) } ?: emptyList()
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
