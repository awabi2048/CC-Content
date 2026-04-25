package jp.awabi2048.cccontent.features.arena

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import jp.awabi2048.cccontent.features.arena.mission.ArenaLicenseTier
import jp.awabi2048.cccontent.features.arena.mission.ArenaMissionModifiers
import jp.awabi2048.cccontent.features.arena.mission.ArenaMissionService
import jp.awabi2048.cccontent.features.arena.mission.ArenaMissionType

class ArenaCommand(
    private val arenaManagerProvider: () -> ArenaManager? = { null },
    private val missionService: ArenaMissionService? = null,
    private val sessionInfoMenu: ArenaSessionInfoMenu? = null,
    private val pedestalMenu: ArenaEnchantPedestalMenu? = null,
    private val featureEnabledProvider: () -> Boolean = { true },
    private val featureFailureReasonProvider: () -> String? = { null }
) : CommandExecutor, TabCompleter {

    private enum class ArenaMenuType(
        val id: String,
        val permissionCheck: (Player) -> Boolean
    ) {
        MISSION("mission", { player -> ArenaPermissions.hasMissionMenuPermission(player) }),
        BROADCAST("broadcast", { player -> ArenaPermissions.hasBroadcastMenuPermission(player) }),
        PEDESTAL("pedestal", { player -> ArenaPermissions.hasPedestalMenuPermission(player) });

        companion object {
            fun fromId(id: String): ArenaMenuType? {
                return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
            }
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!ArenaPermissions.hasAdminAccess(sender)) {
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
            "menu" -> handleMenu(sender, args)
            "start" -> handleStart(sender, args)
            "stop" -> handleStop(sender, args)
            "license" -> handleLicense(sender, args)
            "theme" -> handleTheme(sender, args)
            "lobby" -> handleLobby(sender, args)
            "status" -> handleStatus(sender)
            "broadcast" -> handleBroadcast(sender)
            "pedestal" -> handlePedestal(sender)
            else -> {
                showUsage(sender)
                true
            }
        }
    }

    private fun handleMenu(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.usage.menu",
                    "&c使用法: /arenaa menu <mission|broadcast|pedestal> [player]"
                )
            )
            return true
        }

        val menuType = ArenaMenuType.fromId(args[1])
        if (menuType == null) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.usage.menu",
                    "&c使用法: /arenaa menu <mission|broadcast|pedestal> [player]"
                )
            )
            return true
        }

        val target = resolveTargetPlayer(sender, args.getOrNull(2)) ?: return true
        val forcedByOther = sender !is Player || sender.uniqueId != target.uniqueId

        if (!forcedByOther && !menuType.permissionCheck(target)) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.menu_permission_denied",
                    "&cこのメニューを開く権限がありません"
                )
            )
            return true
        }

        return openMenuByType(sender, target, menuType)
    }

    private fun handleLobby(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.usage.lobby",
                    "&c使用法: /arenaa lobby <player> [tutorial|main]"
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

        val target = Bukkit.getPlayer(args[1])
        if (target == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.target_not_found", "&c対象プレイヤーが見つかりません"))
            return true
        }
        if (!target.isOnline) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.target_not_found", "&c対象プレイヤーが見つかりません"))
            return true
        }

        val lobbyTypeRaw = args.getOrNull(2)?.lowercase()
        if (lobbyTypeRaw != null && lobbyTypeRaw != "tutorial" && lobbyTypeRaw != "main") {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.usage.lobby",
                    "&c使用法: /arenaa lobby <player> [tutorial|main]"
                )
            )
            return true
        }

        val moved = manager.sendPlayerToLobby(target, lobbyTypeRaw)
        if (!moved) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.lobby_failed",
                    "&cロビー移動に失敗しました。マーカー設定を確認してください"
                )
            )
            return true
        }

        val resolvedType = if (lobbyTypeRaw == "tutorial") "tutorial" else if (lobbyTypeRaw == "main") "main" else "auto"
        sender.sendMessage(
            ArenaI18n.text(
                sender,
                "arena.messages.command.lobby_success",
                "&a{player} をロビーへ移動しました ({type})",
                "player" to target.name,
                "type" to resolvedType
            )
        )
        return true
    }

    private fun handleStatus(sender: CommandSender): Boolean {
        val manager = arenaManagerProvider()
        if (manager == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable", "§cArena feature は初期化に失敗したため利用できません"))
            featureFailureReasonProvider()?.let { sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable_reason", "§7理由: {reason}", "reason" to it)) }
            return true
        }

        val report = manager.buildStatusReport()
        val mission = report.missionProgress
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.menu.status.title", "§6=== Arena Status ==="))
        sender.sendMessage("§7sessions: §f${report.activeSessionCount}§7 / §f${report.maxConcurrentSessions}")
        sender.sendMessage("§7arena worlds: §f${report.readyWorldCount} ready§7, §f${report.inUseWorldCount} in-use§7, §f${report.cleaningWorldCount} cleaning§7, §f${report.brokenWorldCount} broken")
        sender.sendMessage("§7world pool: §f${report.arenaWorldReady}§7 / §f${report.arenaWorldTotal} ready")
        sender.sendMessage("§7lift ready: ${if (report.liftReady) "§aYES" else "§cNO"}")
        sender.sendMessage("§7lobby markers: main=${report.mainLobbyCount}, tutorial_start=${report.tutorialStartCount}, tutorial_step=${report.tutorialStepCount}, return=${report.returnLobbyCount}, pedestal=${report.pedestalCount}")
        sender.sendMessage("§7lobby flow: main=${if (report.lobbyMainReady) "§aYES" else "§cNO"}, tutorial=${if (report.lobbyTutorialReady) "§aYES" else "§cNO"}")
        sender.sendMessage("§7lobby progress: visited=${report.lobbyProgressVisitedCount}, tutorial_completed=${report.lobbyProgressTutorialCompletedCount}")
        if (mission != null) {
            sender.sendMessage("§7missions: current=${if (mission.hasCurrentMissionSet) "§aYES" else "§cNO"}, active=${mission.activeMissionCount}, players=${mission.loadedPlayerRecords}, difficulty=${mission.difficultyCount}, generate_count=${mission.generateCount}, strong_enemy_types=${mission.strongEnemyMobTypeCount}, lobby_visited=${mission.lobbyProgressCount}, lobby_tutorial_done=${mission.lobbyTutorialCompletedCount}")
            sender.sendMessage("§7mission generated_at: ${mission.currentMissionGeneratedAtMillis ?: "none"}")
        }
        return true
    }

    private fun handleStart(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 5) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.usage.start",
                    "&c使用法: /arenaa start <player|@s|@near> <star_count(1-4)> <theme> <mission_type>"
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
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.usage.start", "&c使用法: /arenaa start <player|@s|@near> <star_count(1-4)> <theme> <mission_type>"))
            return true
        }
        val difficultyId = "star_$starCount"
        val theme = args[3]
        val missionType = ArenaMissionType.fromId(args[4].lowercase())
        if (missionType == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.start_error.invalid_mission_type", "&c無効なミッションタイプです: {type}", "type" to args[4]))
            return true
        }

        val startedAt = System.nanoTime()
        val missionModifiers = when (missionType) {
            ArenaMissionType.CLEARING -> ArenaMissionModifiers.CLEARING
            else -> ArenaMissionModifiers.NONE
        }
        when (val result = manager.startSession(
            target,
            difficultyId,
            theme,
            initialParticipants = initialParticipants,
            missionModifiers = missionModifiers,
            missionTypeId = missionType
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

    private fun handleLicense(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size != 4 || !args[1].equals("set", ignoreCase = true)) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.usage.license",
                    "&c使用法: /arenaa license set <player> <paper|bronze|silver|gold>"
                )
            )
            return true
        }

        val target = Bukkit.getPlayer(args[2])
        if (target == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.target_not_found", "&c対象プレイヤーが見つかりません"))
            return true
        }

        val service = missionService
        if (service == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable", "§cArena feature は初期化に失敗したため利用できません"))
            featureFailureReasonProvider()?.let { sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable_reason", "§7理由: {reason}", "reason" to it)) }
            return true
        }

        val licenseTier = ArenaLicenseTier.fromId(args[3])
        if (licenseTier == null) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.usage.license",
                    "&c使用法: /arenaa license set <player> <paper|bronze|silver|gold>"
                )
            )
            return true
        }

        val updatedTier = service.setLicenseTier(target.uniqueId, licenseTier)
        sender.sendMessage(
            ArenaI18n.text(
                sender,
                "arena.messages.command.license_set_success",
                "&a{player} のライセンスを {tier} に設定しました",
                "player" to target.name,
                "tier" to ArenaI18n.text(sender, updatedTier.displayNameKey, updatedTier.id)
            )
        )
        return true
    }

    private fun handleBroadcast(sender: CommandSender): Boolean {
        val target = sender as? Player
        if (target == null) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.usage.menu",
                    "&c使用法: /arenaa menu <mission|broadcast|pedestal> [player]"
                )
            )
            return true
        }
        if (!ArenaPermissions.hasBroadcastMenuPermission(target)) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.menu_permission_denied",
                    "&cこのメニューを開く権限がありません"
                )
            )
            return true
        }
        return openMenuByType(sender, target, ArenaMenuType.BROADCAST)
    }

    private fun handlePedestal(sender: CommandSender): Boolean {
        val target = sender as? Player
        if (target == null) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.usage.menu",
                    "&c使用法: /arenaa menu <mission|broadcast|pedestal> [player]"
                )
            )
            return true
        }
        if (!ArenaPermissions.hasPedestalMenuPermission(target)) {
            sender.sendMessage(
                ArenaI18n.text(
                    sender,
                    "arena.messages.command.menu_permission_denied",
                    "&cこのメニューを開く権限がありません"
                )
            )
            return true
        }
        return openMenuByType(sender, target, ArenaMenuType.PEDESTAL)
    }

    private fun resolveTargetPlayer(sender: CommandSender, targetArg: String?): Player? {
        if (targetArg.isNullOrBlank()) {
            val self = sender as? Player
            if (self == null) {
                sender.sendMessage(
                    ArenaI18n.text(
                        sender,
                        "arena.messages.command.target_required_from_console",
                        "&cコンソール実行時は対象プレイヤーを指定してください"
                    )
                )
                return null
            }
            return self
        }

        val target = Bukkit.getPlayer(targetArg)
        if (target == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.target_not_found", "&c対象プレイヤーが見つかりません"))
            return null
        }
        return target
    }

    private fun openMenuByType(sender: CommandSender, target: Player, menuType: ArenaMenuType): Boolean {
        return when (menuType) {
            ArenaMenuType.MISSION -> {
                val service = missionService
                if (service == null) {
                    sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.menu_open_failed", "§cアリーナメニューを開けませんでした"))
                    return true
                }
                service.openMenu(target)
                true
            }

            ArenaMenuType.BROADCAST -> {
                val menu = sessionInfoMenu
                if (menu == null) {
                    sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable", "§cArena feature は初期化に失敗したため利用できません"))
                    return true
                }
                menu.openMenu(target)
                true
            }

            ArenaMenuType.PEDESTAL -> {
                val menu = pedestalMenu
                if (menu == null) {
                    sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable", "§cArena feature は初期化に失敗したため利用できません"))
                    return true
                }
                menu.openMenu(target)
                true
            }
        }
    }

    private fun showUsage(sender: CommandSender) {
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.header", "&6=== Arena 管理コマンド ==="))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.menu", "&f/arenaa menu <mission|broadcast|pedestal> [player]"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.lobby", "&f/arenaa lobby <player> [tutorial|main]"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.start", "&f/arenaa start <player|@s|@near> <star_count> <theme> <mission_type>"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.stop", "&f/arenaa stop <player>"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.license", "&f/arenaa license set <player> <paper|bronze|silver|gold>"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.theme", "&f/arenaa theme list"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.status", "&f/arenaa status"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.broadcast", "&f/arenaa broadcast"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.pedestal", "&f/arenaa pedestal"))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!ArenaPermissions.hasAdminAccess(sender)) return emptyList()
        if (!featureEnabledProvider()) return emptyList()

        return when (args.size) {
            1 -> listOf("menu", "lobby", "start", "stop", "license", "theme", "status", "broadcast", "pedestal").filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> when (args[0].lowercase()) {
                "menu" -> ArenaMenuType.entries.map { it.id }.filter { it.startsWith(args[1], ignoreCase = true) }
                "lobby" -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                "start" -> listOf("@s", "@near") + Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                "stop" -> arenaManagerProvider()?.getActiveSessionPlayerNames()?.filter { it.startsWith(args[1], ignoreCase = true) } ?: emptyList()
                "license" -> listOf("set").filter { it.startsWith(args[1], ignoreCase = true) }
                "theme" -> listOf("list").filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "menu" -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) }
                "lobby" -> listOf("tutorial", "main").filter { it.startsWith(args[2], ignoreCase = true) }
                "start" -> listOf("1", "2", "3", "4").filter { it.startsWith(args[2], ignoreCase = true) }
                "license" -> if (args[1].equals("set", ignoreCase = true)) {
                    Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) }
                } else {
                    emptyList()
                }
                else -> emptyList()
            }
            4 -> when (args[0].lowercase()) {
                "start" -> arenaManagerProvider()?.getThemeIds()?.filter { it.startsWith(args[3], ignoreCase = true) } ?: emptyList()
                "license" -> if (args[1].equals("set", ignoreCase = true)) {
                    ArenaLicenseTier.entries.map { it.id }.filter { it.startsWith(args[3], ignoreCase = true) }
                } else {
                    emptyList()
                }
                else -> emptyList()
            }
            5 -> when (args[0].lowercase()) {
                "start" -> ArenaMissionType.entries.map { it.id }.filter { it.startsWith(args[4], ignoreCase = true) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
