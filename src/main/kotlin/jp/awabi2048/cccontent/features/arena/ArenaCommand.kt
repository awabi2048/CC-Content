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
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ArenaCommand(
    private val arenaManagerProvider: () -> ArenaManager? = { null },
    private val missionService: ArenaMissionService? = null,
    private val sessionInfoMenu: ArenaSessionInfoMenu? = null,
    private val pedestalMenu: ArenaEnchantPedestalMenu? = null,
    private val featureEnabledProvider: () -> Boolean = { true },
    private val featureFailureReasonProvider: () -> String? = { null }
) : CommandExecutor, TabCompleter {
    private val legacySerializer = LegacyComponentSerializer.legacySection()
    private val statusTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.JAPAN)
        .withZone(ZoneId.systemDefault())

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
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.no_permission"))
            return true
        }

        if (!featureEnabledProvider()) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable"))
            featureFailureReasonProvider()?.let { sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable_reason", "reason" to it)) }
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
            "lobby" -> handleLobby(sender, args)
            "status" -> handleStatus(sender)
            else -> {
                showUsage(sender)
                true
            }
        }
    }

    private fun handleMenu(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.usage.menu")
            )
            return true
        }

        val menuType = ArenaMenuType.fromId(args[1])
        if (menuType == null) {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.usage.menu")
            )
            return true
        }

        val target = resolveTargetPlayer(sender, args.getOrNull(2)) ?: return true
        val forcedByOther = sender !is Player || sender.uniqueId != target.uniqueId

        if (!forcedByOther && !menuType.permissionCheck(target)) {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.menu_permission_denied")
            )
            return true
        }

        return openMenuByType(sender, target, menuType)
    }

    private fun handleLobby(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.usage.lobby")
            )
            return true
        }

        val manager = arenaManagerProvider()
        if (manager == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable"))
            featureFailureReasonProvider()?.let { sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable_reason", "reason" to it)) }
            return true
        }

        val target = Bukkit.getPlayer(args[1])
        if (target == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.target_not_found"))
            return true
        }
        if (!target.isOnline) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.target_not_found"))
            return true
        }

        val lobbyTypeRaw = args.getOrNull(2)?.lowercase()
        if (lobbyTypeRaw != null && lobbyTypeRaw != "tutorial" && lobbyTypeRaw != "main") {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.usage.lobby")
            )
            return true
        }

        val moved = manager.sendPlayerToLobby(target, lobbyTypeRaw)
        if (!moved) {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.lobby_failed")
            )
            return true
        }

        val resolvedType = if (lobbyTypeRaw == "tutorial") "tutorial" else if (lobbyTypeRaw == "main") "main" else "auto"
        sender.sendMessage(
            ArenaI18n.text(sender, "arena.messages.command.lobby_success", "player" to target.name, "type" to resolvedType)
        )
        return true
    }

    private fun handleStatus(sender: CommandSender): Boolean {
        val manager = arenaManagerProvider()
        if (manager == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable"))
            featureFailureReasonProvider()?.let { sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable_reason", "reason" to it)) }
            return true
        }

        val report = manager.buildStatusReport()
        sender.sendMessage(component("§6=== Arena Status ==="))
        sender.sendMessage(buildPoolStatusLine(report))
        sender.sendMessage(buildLobbyStatusLine(report))
        sender.sendMessage(buildMissionStatusLine(report))
        sender.sendMessage(buildThemeStatusLine(report))
        return true
    }

    private fun buildPoolStatusLine(report: ArenaStatusReport): Component {
        val detail = listOf(
            "§f使用中: §e${report.inUseWorldCount}",
            "§f待機中: §a${report.readyWorldCount}",
            "§f清掃中: §b${report.cleaningWorldCount}",
            "§fエラー: §c${report.brokenWorldCount}",
            "§7同時セッション: §f${report.activeSessionCount}/${report.maxConcurrentSessions}"
        ).joinToString("\n")
        val worlds = report.poolWorlds.ifEmpty {
            (1..report.arenaWorldTotal).map { ArenaPoolWorldStatus("pool.$it", "broken") }
        }

        var line = component("§7セッション状態: ")
        worlds.forEachIndexed { index, pool ->
            if (index > 0) {
                line = line.append(component("§8, "))
            }
            val color = when (pool.state) {
                "in_use" -> "§e"
                "ready" -> "§a"
                "broken" -> "§c"
                "cleaning" -> "§b"
                else -> "§7"
            }
            val label = pool.name.removePrefix("arena.")
            line = line.append(component("$color$label").hoverEvent(HoverEvent.showText(component(detail))))
        }
        return line
    }

    private fun buildLobbyStatusLine(report: ArenaStatusReport): Component {
        val ok = report.lobbyMainReady && report.lobbyTutorialReady && report.returnLobbyCount > 0
        val missing = mutableListOf<String>()
        if (!report.lobbyMainReady) missing += "main lobby marker"
        if (!report.lobbyTutorialReady) missing += "tutorial_start marker"
        if (report.returnLobbyCount <= 0) missing += "return marker"
        val detail = buildString {
            appendLine("§fmain: §b${report.mainLobbyCount}")
            appendLine("§ftutorial_start: §b${report.tutorialStartCount}")
            appendLine("§ftutorial_step: §b${report.tutorialStepCount}")
            appendLine("§freturn: §b${report.returnLobbyCount}")
            appendLine("§fpedestal: §b${report.pedestalCount}")
            append("§flift: ")
            append(if (report.liftReady) "§a利用可能" else "§e待機中")
            if (missing.isNotEmpty()) {
                appendLine()
                append("§c不足: ${missing.joinToString(", ")}")
            }
        }
        val status = if (ok) "§a利用可能" else "§cエラー"
        return component("§7ロビー: ")
            .append(component(status).hoverEvent(HoverEvent.showText(component(detail))))
    }

    private fun buildMissionStatusLine(report: ArenaStatusReport): Component {
        val mission = report.missionProgress
        val generated = mission?.hasCurrentMissionSet == true
        val generatedAt = mission?.currentMissionGeneratedAtMillis
            ?.let { statusTimeFormatter.format(Instant.ofEpochMilli(it)) }
            ?: "なし"
        val detail = if (mission == null) {
            "§cMission service unavailable"
        } else {
            listOf(
                "§f生成日時: §b$generatedAt",
                "§f生成数: §b${mission.generateCount}",
                "§f進行中: §b${mission.activeMissionCount}",
                "§fテーマ数: §b${mission.themeCount}"
            ).joinToString("\n")
        }
        val status = if (generated) "§a生成済み" else "§e生成まだ"
        val click = if (generated) {
            ClickEvent.runCommand("/arenaa menu mission")
        } else {
            ClickEvent.suggestCommand("/ccc debug update_day arena")
        }
        return component("§7ミッション: ")
            .append(component(status).hoverEvent(HoverEvent.showText(component(detail))).clickEvent(click))
    }

    private fun buildThemeStatusLine(report: ArenaStatusReport): Component {
        val loadStatus = report.themeLoadStatus
        var line = component("§7テーマ読み込み状況: ")
        val entries = mutableListOf<Component>()
        loadStatus.availableThemeIds.sorted().forEach { themeId ->
            entries += component("§a$themeId")
                .hoverEvent(HoverEvent.showText(component("§a利用可能")))
        }
        loadStatus.unavailableThemes.forEach { issue ->
            entries += component("§c${issue.themeId}")
                .hoverEvent(HoverEvent.showText(component(issue.details.joinToString("\n"))))
        }
        if (entries.isEmpty()) {
            entries += component("§cなし")
                .hoverEvent(HoverEvent.showText(component(loadStatus.generalWarnings.joinToString("\n").ifBlank { "テーマが読み込まれていません" })))
        }
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                line = line.append(component("§8, "))
            }
            line = line.append(entry)
        }
        if (loadStatus.generalWarnings.isNotEmpty()) {
            line = line.append(component(" §e(!)")
                .hoverEvent(HoverEvent.showText(component(loadStatus.generalWarnings.joinToString("\n")))))
        }
        return line
    }

    private fun component(text: String): Component {
        return legacySerializer.deserialize(text)
    }

    private fun handleStart(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size !in 4..5) {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.usage.start")
            )
            return true
        }

        val manager = arenaManagerProvider()
        if (manager == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable"))
            featureFailureReasonProvider()?.let { sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable_reason", "reason" to it)) }
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
                    sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.target_not_found"))
                    return true
                }
                player to emptyList()
            }
        }

        val theme = args[2]
        val missionType = ArenaMissionType.fromId(args[3].lowercase())
        if (missionType == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.start_error.invalid_mission_type", "type" to args[3]))
            return true
        }
        val variantArg = args.getOrNull(4)
        if (variantArg != null && !variantArg.equals("promoted", ignoreCase = true)) {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.usage.start")
            )
            return true
        }
        val promoted = variantArg != null

        val startedAt = System.nanoTime()
        val missionModifiers = when (missionType) {
            ArenaMissionType.CLEARING -> ArenaMissionModifiers.CLEARING
            else -> ArenaMissionModifiers.NONE
        }
        when (val result = manager.startSession(
            target,
            theme,
            promoted = promoted,
            initialParticipants = initialParticipants,
            missionModifiers = missionModifiers,
            missionTypeId = missionType
        )) {
            is ArenaStartResult.Success -> {
                sender.sendMessage(
                    ArenaI18n.text(sender, "arena.messages.command.start_success", "player" to target.name, "mob_type" to result.themeId, "difficulty" to result.difficultyDisplay, "theme" to result.themeId, "waves" to result.waves)
                )
            }
            is ArenaStartResult.Error -> {
                sender.sendMessage(
                    ArenaI18n.text(sender, result.messageKey, *result.placeholders)
                )
            }
        }
        sender.sendMessage("§7処理時間: ${((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)} ms")
        return true
    }

    private fun handleStop(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.usage.stop"))
            return true
        }
        val target = Bukkit.getPlayer(args[1])
        if (target == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.target_not_found"))
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
            ArenaI18n.text(target, "arena.messages.session.stopped_by_admin")
        )
        if (stopped) {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.stop_success", "player" to target.name)
            )
        } else {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.stop_not_in_session", "player" to target.name)
            )
        }
        return true
    }

    private fun handleTheme(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2 || !args[1].equals("list", ignoreCase = true)) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.usage.theme"))
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
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.theme_none"))
            return true
        }

        sender.sendMessage(
            ArenaI18n.text(sender, "arena.messages.command.theme_list", "themes" to ids.joinToString(", "))
        )
        return true
    }

    private fun handleLicense(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size != 4 || !args[1].equals("set", ignoreCase = true)) {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.usage.license")
            )
            return true
        }

        val target = Bukkit.getPlayer(args[2])
        if (target == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.target_not_found"))
            return true
        }

        val service = missionService
        if (service == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable"))
            featureFailureReasonProvider()?.let { sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable_reason", "reason" to it)) }
            return true
        }

        val licenseTier = ArenaLicenseTier.fromId(args[3])
        if (licenseTier == null) {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.usage.license")
            )
            return true
        }

        val updatedTier = service.setLicenseTier(target.uniqueId, licenseTier)
        sender.sendMessage(
            ArenaI18n.text(sender, "arena.messages.command.license_set_success", "player" to target.name, "tier" to ArenaI18n.text(sender, updatedTier.displayNameKey))
        )
        return true
    }

    private fun handleBroadcast(sender: CommandSender): Boolean {
        val target = sender as? Player
        if (target == null) {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.usage.menu")
            )
            return true
        }
        if (!ArenaPermissions.hasBroadcastMenuPermission(target)) {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.menu_permission_denied")
            )
            return true
        }
        return openMenuByType(sender, target, ArenaMenuType.BROADCAST)
    }

    private fun handlePedestal(sender: CommandSender): Boolean {
        val target = sender as? Player
        if (target == null) {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.usage.menu")
            )
            return true
        }
        if (!ArenaPermissions.hasPedestalMenuPermission(target)) {
            sender.sendMessage(
                ArenaI18n.text(sender, "arena.messages.command.menu_permission_denied")
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
                    ArenaI18n.text(sender, "arena.messages.command.target_required_from_console")
                )
                return null
            }
            return self
        }

        val target = Bukkit.getPlayer(targetArg)
        if (target == null) {
            sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.target_not_found"))
            return null
        }
        return target
    }

    private fun openMenuByType(sender: CommandSender, target: Player, menuType: ArenaMenuType): Boolean {
        return when (menuType) {
            ArenaMenuType.MISSION -> {
                val service = missionService
                if (service == null) {
                    sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.menu_open_failed"))
                    return true
                }
                service.openMenu(target)
                true
            }

            ArenaMenuType.BROADCAST -> {
                val menu = sessionInfoMenu
                if (menu == null) {
                    sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable"))
                    return true
                }
                menu.openMenu(target)
                true
            }

            ArenaMenuType.PEDESTAL -> {
                val menu = pedestalMenu
                if (menu == null) {
                    sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.feature_unavailable"))
                    return true
                }
                menu.openMenu(target)
                true
            }
        }
    }

    private fun showUsage(sender: CommandSender) {
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.header"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.menu"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.lobby"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.start"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.stop"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.license"))
        sender.sendMessage(ArenaI18n.text(sender, "arena.messages.command.help.status"))
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
            1 -> listOf("menu", "lobby", "start", "stop", "license", "status").filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> when (args[0].lowercase()) {
                "menu" -> ArenaMenuType.entries.map { it.id }.filter { it.startsWith(args[1], ignoreCase = true) }
                "lobby" -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                "start" -> listOf("@s", "@near") + Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                "stop" -> arenaManagerProvider()?.getActiveSessionPlayerNames()?.filter { it.startsWith(args[1], ignoreCase = true) } ?: emptyList()
                "license" -> listOf("set").filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "menu" -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) }
                "lobby" -> listOf("tutorial", "main").filter { it.startsWith(args[2], ignoreCase = true) }
                "start" -> arenaManagerProvider()?.getThemeIds()?.filter { it.startsWith(args[2], ignoreCase = true) } ?: emptyList()
                "license" -> if (args[1].equals("set", ignoreCase = true)) {
                    Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) }
                } else {
                    emptyList()
                }
                else -> emptyList()
            }
            4 -> when (args[0].lowercase()) {
                "start" -> ArenaMissionType.entries.map { it.id }.filter { it.startsWith(args[3], ignoreCase = true) }
                "license" -> if (args[1].equals("set", ignoreCase = true)) {
                    ArenaLicenseTier.entries.map { it.id }.filter { it.startsWith(args[3], ignoreCase = true) }
                } else {
                    emptyList()
                }
                else -> emptyList()
            }
            5 -> when (args[0].lowercase()) {
                "start" -> {
                    val theme = arenaManagerProvider()?.getTheme(args[2])
                    if (theme?.promotedVariant != null) {
                        listOf("promoted").filter { it.startsWith(args[4], ignoreCase = true) }
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
