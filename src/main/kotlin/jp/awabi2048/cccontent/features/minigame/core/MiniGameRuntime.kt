@file:Suppress("DEPRECATION")

package jp.awabi2048.cccontent.features.minigame.core

import jp.awabi2048.cccontent.features.minigame.race.RaceCourse
import jp.awabi2048.cccontent.features.minigame.race.RaceSession
import jp.awabi2048.cccontent.features.minigame.race.RaceVisitStatus
import jp.awabi2048.cccontent.features.minigame.hideandseek.CaptureStatus
import jp.awabi2048.cccontent.features.minigame.hideandseek.HideAndSeekSession
import jp.awabi2048.cccontent.features.minigame.chase.ChaseSession
import jp.awabi2048.cccontent.features.minigame.colosseum.ColosseumSession
import jp.awabi2048.cccontent.features.minigame.colosseum.ColosseumRoundStatus
import jp.awabi2048.cccontent.features.minigame.endergolf.EnderGolfCourse
import jp.awabi2048.cccontent.features.minigame.endergolf.EnderGolfLanding
import jp.awabi2048.cccontent.features.minigame.endergolf.EnderGolfSession
import jp.awabi2048.cccontent.features.party.PartyService
import jp.awabi2048.cccontent.gui.GuiMenuItems
import jp.awabi2048.cccontent.gui.MenuEventGuards
import jp.awabi2048.cccontent.gui.OwnedMenuHolder
import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuIconAction
import com.awabi2048.ccsystem.api.gui.GuiMenuIconData
import com.awabi2048.ccsystem.api.gui.GuiMenuIconSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.entity.EnderPearl
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import jp.awabi2048.cccontent.integration.myworld.MyWorldBridge

/** ミニゲームのBukkit接続とセッション単一性を管理する実行サービス。 */
class MiniGameRuntime(
    private val plugin: JavaPlugin,
    private val partyService: PartyService? = null,
    private val myWorldBridge: MyWorldBridge
) : Listener {
    companion object {
        var current: MiniGameRuntime? = null
            private set
    }

    private data class ActiveGame(
        val game: MiniGameId,
        val session: MiniGameSessionContract,
        val snapshots: Map<UUID, MiniGamePlayerSnapshot>,
        val jailLocation: org.bukkit.Location? = null,
        val roundStartLocation: org.bukkit.Location? = null,
        val pendingPearls: MutableMap<UUID, UUID> = mutableMapOf(),
        val touching: MutableMap<UUID, Set<UUID>> = mutableMapOf(),
        val golfSafeLocations: MutableMap<UUID, org.bukkit.Location> = mutableMapOf(),
        val withdrawnPlayers: MutableSet<UUID> = mutableSetOf()
    )

    private val pdc = MiniGamePdc(plugin)
    private val settings = MiniGameSettings(plugin)
    private val accessPolicy = MiniGameAccessPolicy(myWorldBridge::findMyWorldByUuid)
    private val markerService = MiniGameMarkerService(plugin, pdc, accessPolicy, settings, ::isRunning)
    private val persistence = MiniGamePersistence(plugin)
    private val active = linkedMapOf<MiniGameId, ActiveGame>()
    private val playerGames = mutableMapOf<UUID, MiniGameId>()
    private val participantSelections = mutableMapOf<MiniGameId, LinkedHashSet<UUID>>()
    private var timerTask: BukkitTask? = null
    private lateinit var menu: MiniGameAdminMenu

    fun initialize() {
        settings.initialize()
        persistence.initialize()
        persistence.closeInterruptedSessions()
        current = this
        menu = MiniGameAdminMenu(this)
        plugin.server.pluginManager.registerEvents(markerService, plugin)
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.server.pluginManager.registerEvents(menu, plugin)
        timerTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 1L, 20L)
    }

    fun markerPdc(): MiniGamePdc = pdc

    fun defaultGameId(): String = settings.defaultGameId()

    fun openManager(player: Player, itemData: MiniGameItemData?) {
        if (itemData?.manager != true) return
        val game = MiniGameId(itemData.worldUuid, itemData.gameId)
        if (player.world.uid != game.worldUuid || !accessPolicy.canEdit(player, game.worldUuid, itemData.ownerUuid)) {
            player.sendMessage(MiniGameMessages.text(player, "messages.no_edit_permission"))
            return
        }
        menu.open(player, itemData)
    }

    fun start(player: Player, itemData: MiniGameItemData): Boolean {
        val game = MiniGameId(itemData.worldUuid, itemData.gameId)
        if (game.gameId !in MiniGameSupportedGames.ids) {
            player.sendMessage(MiniGameMessages.text(player, "messages.invalid_game"))
            return false
        }
        if (active.containsKey(game)) {
            player.sendMessage(MiniGameMessages.text(player, "messages.already_running"))
            return false
        }
        if (!accessPolicy.canEdit(player, game.worldUuid, itemData.ownerUuid)) {
            player.sendMessage(MiniGameMessages.text(player, "messages.no_edit_permission"))
            return false
        }
        val placed = markerService.markers(game)
        val party = partyService?.partyOf(player.uniqueId)
        if (party == null || party.leader != player.uniqueId) {
            player.sendMessage(MiniGameMessages.text(player, "messages.no_participants"))
            return false
        }
        val onlineIds = Bukkit.getOnlinePlayers().map { it.uniqueId }.toSet()
        val worldIds = Bukkit.getOnlinePlayers()
            .filter { it.world.uid == game.worldUuid && it.gameMode != org.bukkit.GameMode.SPECTATOR }
            .map { it.uniqueId }
            .toSet()
        val eligibleParticipantIds = MiniGameParticipantPolicy.select(
            player.uniqueId, party, onlineIds, worldIds, playerGames.keys
        )
        val selected = participantSelections[game].orEmpty().filter { it in eligibleParticipantIds }.toSet()
        val participantIds = if (game.gameId == "colosseum") {
            selected.takeIf { it.size == 2 } ?: run {
                player.sendMessage(MiniGameMessages.text(player, "messages.participant_selection_required", "count" to 2))
                return false
            }
        } else {
            eligibleParticipantIds
        }
        val participants = participantIds.mapNotNull(Bukkit::getPlayer)
        val spectators = if (game.gameId == "colosseum") {
            (eligibleParticipantIds - participantIds).mapNotNull(Bukkit::getPlayer)
        } else {
            emptyList()
        }
        if (participants.isEmpty()) {
            player.sendMessage(MiniGameMessages.text(player, "messages.no_participants"))
            return false
        }
        val snapshots = (participants + spectators).associate { it.uniqueId to MiniGamePlayerSnapshot.capture(it) }
        val session: MiniGameSessionContract
        val jailLocation = if (game.gameId == "chase") {
            val jail = placed.filter { it.marker.type == MiniGameMarkerType.JAIL }
            if (jail.size != 1) {
                player.sendMessage(MiniGameMessages.text(player, "messages.invalid_jail"))
                return false
            }
            jail.single().location
        } else null
        val roundStartLocation = placed.singleOrNull { it.marker.type == MiniGameMarkerType.START }?.location
        session = when (game.gameId) {
            "race" -> {
                val course = runCatching { RaceCourse.fromMarkers(placed.map { it.marker }) }.getOrElse {
                    player.sendMessage(MiniGameMessages.text(player, "messages.invalid_course", "reason" to (it.message ?: "unknown")))
                    return false
                }
                RaceSession(game, participantIds, course, settings.timeLimitSeconds(game) * 1000L)
            }
            "hideandseek" -> {
                val count = settings.hunterCount(game)
                if (selected.size != count || selected.size >= participantIds.size) {
                    player.sendMessage(MiniGameMessages.text(player, "messages.hunter_selection_required", "count" to count))
                    return false
                }
                HideAndSeekSession(
                    game,
                    participantIds,
                    settings.timeLimitSeconds(game) * 1000L,
                    selected,
                    settings.preparationSeconds(game) * 1000L
                )
            }
            "chase" -> {
                val count = settings.hunterCount(game)
                if (selected.size != count || selected.size >= participantIds.size) {
                    player.sendMessage(MiniGameMessages.text(player, "messages.hunter_selection_required", "count" to count))
                    return false
                }
                ChaseSession(game, participantIds, settings.timeLimitSeconds(game) * 1000L, selected)
            }
            "colosseum" -> {
                if (participants.size != 2 || roundStartLocation == null) {
                    player.sendMessage(MiniGameMessages.text(player, "messages.invalid_colosseum"))
                    return false
                }
                ColosseumSession(game, participantIds, settings.timeLimitSeconds(game) * 1000L, settings.firstTo(game))
            }
            "endergolf" -> {
                val course = runCatching { EnderGolfCourse.fromMarkers(placed.map { it.marker }) }.getOrElse {
                    player.sendMessage(MiniGameMessages.text(player, "messages.invalid_endergolf", "reason" to (it.message ?: "unknown")))
                    return false
                }
                EnderGolfSession(game, participantIds, course, settings.timeLimitSeconds(game) * 1000L)
            }
            else -> {
                player.sendMessage(MiniGameMessages.text(player, "messages.invalid_game"))
                return false
            }
        }
        session.start(System.currentTimeMillis())
        persistence.saveSessionStarted(session)
        // 正常停止だけでなくプロセス断にも備え、開始前状態を先に永続化する。
        snapshots.values.forEach { snapshot ->
            Bukkit.getPlayer(snapshot.playerUuid)?.let { persistence.saveRecovery(it, snapshot) }
        }
        active[game] = ActiveGame(game, session, snapshots, jailLocation, roundStartLocation)
        participants.forEach { participant -> playerGames[participant.uniqueId] = game }
        participants.forEach { participant ->
            participant.sendMessage(MiniGameMessages.text(participant, "messages.started", "game" to game.gameId))
            if (session is HideAndSeekSession) {
                participant.sendMessage(
                    MiniGameMessages.text(
                        participant,
                        "messages.preparation_started",
                        "seconds" to settings.preparationSeconds(game)
                    )
                )
            }
            val role = when (session) {
                is HideAndSeekSession -> session.roles[participant.uniqueId]
                is ChaseSession -> session.roles[participant.uniqueId]
                else -> null
            }
            role?.let { participant.sendMessage(MiniGameMessages.text(participant, "messages.role_assigned", "role" to it.name)) }
        }
        if (session is ColosseumSession) {
            participants.forEach { it.teleport(roundStartLocation ?: it.location) }
            spectators.forEach { spectator ->
                spectator.gameMode = org.bukkit.GameMode.SPECTATOR
                spectator.teleport(roundStartLocation ?: spectator.location)
                spectator.sendMessage(MiniGameMessages.text(spectator, "messages.colosseum_spectating"))
            }
        }
        return true
    }

    fun stop(player: Player, itemData: MiniGameItemData, forced: Boolean = false): Boolean {
        val game = MiniGameId(itemData.worldUuid, itemData.gameId)
        val running = active[game] ?: return false.also {
            player.sendMessage(MiniGameMessages.text(player, "messages.not_running"))
        }
        if (!accessPolicy.canEdit(player, game.worldUuid, itemData.ownerUuid)) return false
        val result = running.session.forceEnd(
            System.currentTimeMillis(),
            if (forced) MiniGameEndReason.FORCED else MiniGameEndReason.STOPPED
        )
        finish(running, result)
        return true
    }

    fun adjustTimeLimit(player: Player, itemData: MiniGameItemData, deltaSeconds: Int) {
        val game = MiniGameId(itemData.worldUuid, itemData.gameId)
        if (!accessPolicy.canEdit(player, game.worldUuid, itemData.ownerUuid)) return
        val seconds = settings.adjustTimeLimit(game, deltaSeconds)
        player.sendMessage(MiniGameMessages.text(player, "messages.time_limit_changed", "seconds" to seconds))
    }

    fun timeLimitSeconds(itemData: MiniGameItemData): Int = settings.timeLimitSeconds(
        MiniGameId(itemData.worldUuid, itemData.gameId)
    )

    fun preparationSeconds(itemData: MiniGameItemData): Int = settings.preparationSeconds(
        MiniGameId(itemData.worldUuid, itemData.gameId)
    )

    fun adjustPreparation(player: Player, itemData: MiniGameItemData, deltaSeconds: Int) {
        val game = MiniGameId(itemData.worldUuid, itemData.gameId)
        if (game.gameId != "hideandseek" || !accessPolicy.canEdit(player, game.worldUuid, itemData.ownerUuid)) return
        val seconds = settings.adjustPreparation(game, deltaSeconds)
        player.sendMessage(MiniGameMessages.text(player, "messages.preparation_changed", "seconds" to seconds))
    }

    fun isRunning(game: MiniGameId): Boolean = active.containsKey(game)

    fun isRunning(itemData: MiniGameItemData): Boolean =
        isRunning(MiniGameId(itemData.worldUuid, itemData.gameId))

    fun selectionCandidates(player: Player, itemData: MiniGameItemData): List<UUID>? {
        val game = MiniGameId(itemData.worldUuid, itemData.gameId)
        if (game.gameId !in setOf("hideandseek", "chase", "colosseum")) return null
        if (!accessPolicy.canEdit(player, game.worldUuid, itemData.ownerUuid)) return null
        val party = partyService?.partyOf(player.uniqueId) ?: return null
        if (party.leader != player.uniqueId) return null
        return party.members.filter { uuid ->
            val member = Bukkit.getPlayer(uuid)
            member != null && member.isOnline && member.world.uid == game.worldUuid &&
                member.gameMode != org.bukkit.GameMode.SPECTATOR && uuid !in playerGames
        }.sortedBy { Bukkit.getOfflinePlayer(it).name ?: it.toString() }
    }

    fun selectedParticipants(itemData: MiniGameItemData): Set<UUID> =
        participantSelections[MiniGameId(itemData.worldUuid, itemData.gameId)].orEmpty().toSet()

    fun toggleParticipantSelection(player: Player, itemData: MiniGameItemData, target: UUID): Boolean {
        val candidates = selectionCandidates(player, itemData) ?: return false
        if (target !in candidates) return false
        val game = MiniGameId(itemData.worldUuid, itemData.gameId)
        val selection = participantSelections.getOrPut(game) { linkedSetOf() }
        if (!selection.add(target)) {
            selection.remove(target)
            return true
        }
        val limit = if (game.gameId == "colosseum") 2 else settings.hunterCount(game)
        if (selection.size > limit) {
            selection.remove(target)
            player.sendMessage(MiniGameMessages.text(player, "messages.selection_limit", "count" to limit))
            return false
        }
        return true
    }

    fun gameHistory(player: Player, itemData: MiniGameItemData): List<MiniGameHistoryRecord>? {
        if (!accessPolicy.canView(player, itemData.worldUuid)) return null
        return persistence.gameHistory(MiniGameId(itemData.worldUuid, itemData.gameId))
    }

    fun topRecords(player: Player, itemData: MiniGameItemData, limit: Int = 50): List<MiniGameRankedRecord>? {
        if (!accessPolicy.canView(player, itemData.worldUuid)) return null
        return persistence.topRecords(MiniGameId(itemData.worldUuid, itemData.gameId), limit)
    }

    fun personalBest(player: Player, itemData: MiniGameItemData): MiniGameRankedRecord? {
        if (!accessPolicy.canView(player, itemData.worldUuid)) return null
        return persistence.personalBest(MiniGameId(itemData.worldUuid, itemData.gameId), player.uniqueId)
    }

    fun shutdown() {
        active.values.toList().forEach { running ->
            val result = running.session.forceEnd(System.currentTimeMillis(), MiniGameEndReason.FORCED)
            finish(running, result)
        }
        active.clear()
        playerGames.clear()
        participantSelections.clear()
        timerTask?.cancel()
        timerTask = null
        if (current === this) current = null
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        persistence.restoreRecovery(event.player)
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val to = event.to
        if (event.from.world.uid == to.world.uid && event.from.x == to.x && event.from.y == to.y && event.from.z == to.z) return
        val game = playerGames[event.player.uniqueId] ?: return
        val running = active[game] ?: return
        val radius = settings.markerRadius()
        val radiusSquared = radius * radius
        if (running.session is EnderGolfSession) {
            val golf = running.session
            val nearby = markerService.markers(game)
                .filter { it.location.distanceSquared(to) <= radiusSquared }
            val previous = running.touching[event.player.uniqueId].orEmpty()
            val current = nearby.map { it.marker.markerUuid }.toSet()
            running.touching[event.player.uniqueId] = current
            nearby.filter { it.marker.markerUuid !in previous }
                .filter { it.marker.type == MiniGameMarkerType.START }
                .forEach { marker ->
                    if (golf.beginHole(event.player.uniqueId, marker.marker.markerUuid) == EnderGolfLanding.STARTED) {
                        running.golfSafeLocations[event.player.uniqueId] = marker.location.clone()
                        event.player.sendMessage(MiniGameMessages.text(event.player, "messages.golf_hole_started", "hole" to (marker.marker.checkpointIndex ?: 1)))
                    }
                }
            return
        }
        if (running.session !is RaceSession) return
        val nearby = markerService.markers(game)
            .filter { it.location.distanceSquared(to) <= radiusSquared }
            .map { it.marker.markerUuid }
            .toSet()
        val previous = running.touching[event.player.uniqueId].orEmpty()
        running.touching[event.player.uniqueId] = nearby
        val entered = nearby - previous
        entered.forEach { markerUuid ->
            val result = running.session.visit(event.player.uniqueId, markerUuid, System.currentTimeMillis())
            when (result.status) {
                RaceVisitStatus.STARTED -> event.player.sendMessage(MiniGameMessages.text(event.player, "messages.race_started"))
                RaceVisitStatus.CHECKPOINT_REACHED -> event.player.sendMessage(
                    MiniGameMessages.text(event.player, "messages.checkpoint_reached")
                )
                RaceVisitStatus.FINISHED -> event.player.sendMessage(
                    MiniGameMessages.text(event.player, "messages.race_finished", "milliseconds" to result.elapsedMillis)
                )
                else -> Unit
            }
        }
        if (running.session.allParticipantsFinished()) {
            finish(running, running.session.result(System.currentTimeMillis(), MiniGameEndReason.COMPLETED))
        }
    }

    @EventHandler
    fun onDamage(event: EntityDamageByEntityEvent) {
        val hunter = event.damager as? Player ?: return
        val runner = event.entity as? Player ?: return
        val game = playerGames[hunter.uniqueId] ?: return
        if (playerGames[runner.uniqueId] != game) return
        val running = active[game] ?: return
        val now = System.currentTimeMillis()
        when (val session = running.session) {
            is HideAndSeekSession -> {
                event.isCancelled = true
                when (session.capture(hunter.uniqueId, runner.uniqueId, now)) {
                    CaptureStatus.CAPTURED -> {
                        runner.sendMessage(MiniGameMessages.text(runner, "messages.captured"))
                        hunter.sendMessage(MiniGameMessages.text(hunter, "messages.capture_success"))
                    }
                    else -> Unit
                }
                if (session.allRunnersCaptured()) finish(running, session.result(now, MiniGameEndReason.COMPLETED))
            }
            is ChaseSession -> if (session.capture(hunter.uniqueId, runner.uniqueId, now)) {
                event.isCancelled = true
                running.jailLocation?.let { runner.teleport(it) }
                runner.sendMessage(MiniGameMessages.text(runner, "messages.captured"))
                hunter.sendMessage(MiniGameMessages.text(hunter, "messages.capture_success"))
                if (session.allRunnersCaptured()) finish(running, session.result(now, MiniGameEndReason.COMPLETED))
            }
            else -> Unit
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val game = playerGames[player.uniqueId] ?: return
        val running = active[game] ?: return
        event.keepInventory = true
        event.keepLevel = true
        event.drops.clear()
        val now = System.currentTimeMillis()
        when (val session = running.session) {
            is ColosseumSession -> {
                when (session.recordDeath(player.uniqueId, now)) {
                    ColosseumRoundStatus.MATCH_WON -> finish(running, session.result(now, MiniGameEndReason.COMPLETED))
                    ColosseumRoundStatus.ROUND_WON -> plugin.server.scheduler.runTask(plugin, Runnable {
                        running.roundStartLocation?.let { location ->
                            running.session.participantUuids.mapNotNull(Bukkit::getPlayer).forEach { it.teleport(location) }
                        }
                        running.session.participantUuids.mapNotNull(Bukkit::getPlayer).forEach {
                            it.health = it.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
                        }
                    })
                    ColosseumRoundStatus.IGNORED -> Unit
                }
            }
            else -> finish(running, session.forceEnd(now, MiniGameEndReason.PLAYER_DEATH))
        }
    }

    @EventHandler
    fun onPearlLaunch(event: ProjectileLaunchEvent) {
        val pearl = event.entity as? EnderPearl ?: return
        val player = pearl.shooter as? Player ?: return
        val game = playerGames[player.uniqueId] ?: return
        val running = active[game] ?: return
        val golf = running.session as? EnderGolfSession ?: return
        if (!golf.registerThrow(player.uniqueId)) {
            event.isCancelled = true
            return
        }
        running.pendingPearls[pearl.uniqueId] = player.uniqueId
    }

    @EventHandler
    fun onPearlHit(event: ProjectileHitEvent) {
        val pearl = event.entity as? EnderPearl ?: return
        val game = playerGames[runningPlayerForPearl(pearl.uniqueId) ?: return] ?: return
        val running = active[game] ?: return
        val golf = running.session as? EnderGolfSession ?: return
        val playerUuid = running.pendingPearls.remove(pearl.uniqueId) ?: return
        val hit = pearl.location
        val marker = markerService.markers(game).firstOrNull {
            it.location.distanceSquared(hit) <= settings.markerRadius() * settings.markerRadius()
        }
        val landing = if (marker == null) {
            golf.recordOrdinaryLanding(playerUuid)
        } else {
            golf.recordLanding(playerUuid, marker.marker.markerUuid, marker.marker.type, System.currentTimeMillis())
        }
        when (landing) {
            EnderGolfLanding.WATER_HAZARD -> {
                val player = Bukkit.getPlayer(playerUuid)
                player?.sendMessage(MiniGameMessages.text(player, "messages.golf_water_hazard"))
                running.golfSafeLocations[playerUuid]?.let { player?.teleport(it) }
            }
            EnderGolfLanding.BUNKER -> {
                val player = Bukkit.getPlayer(playerUuid)
                player?.sendMessage(MiniGameMessages.text(player, "messages.golf_bunker"))
            }
            EnderGolfLanding.CUP -> {
                val player = Bukkit.getPlayer(playerUuid)
                player?.sendMessage(MiniGameMessages.text(player, "messages.golf_cup"))
            }
            EnderGolfLanding.LANDING -> running.golfSafeLocations[playerUuid] = hit.clone()
            else -> Unit
        }
        if (golf.allPlayersFinished()) finish(running, golf.result(System.currentTimeMillis(), MiniGameEndReason.COMPLETED))
    }

    private fun runningPlayerForPearl(pearlUuid: UUID): UUID? = active.values.firstNotNullOfOrNull { it.pendingPearls[pearlUuid] }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val game = playerGames[event.player.uniqueId] ?: return
        val running = active[game] ?: return
        running.snapshots[event.player.uniqueId]?.let { persistence.saveRecovery(event.player, it, System.currentTimeMillis()) }
        val now = System.currentTimeMillis()
        when (val session = running.session) {
            is ColosseumSession -> {
                session.forfeit(event.player.uniqueId, now)
                finish(running, session.result(now, MiniGameEndReason.PLAYER_LEFT))
            }
            is RaceSession -> {
                session.withdraw(event.player.uniqueId)
                markWithdrawn(running, event.player.uniqueId)
                if (session.allParticipantsResolved()) {
                    finish(running, session.result(now, MiniGameEndReason.PLAYER_LEFT))
                }
            }
            is EnderGolfSession -> {
                session.withdraw(event.player.uniqueId)
                markWithdrawn(running, event.player.uniqueId)
                if (session.allPlayersResolved()) {
                    finish(running, session.result(now, MiniGameEndReason.PLAYER_LEFT))
                }
            }
            is HideAndSeekSession -> {
                val outcomeImpossible = session.withdraw(event.player.uniqueId)
                markWithdrawn(running, event.player.uniqueId)
                if (outcomeImpossible) finish(running, session.result(now, MiniGameEndReason.PLAYER_LEFT))
            }
            is ChaseSession -> {
                val outcomeImpossible = session.withdraw(event.player.uniqueId)
                markWithdrawn(running, event.player.uniqueId)
                if (outcomeImpossible) finish(running, session.result(now, MiniGameEndReason.PLAYER_LEFT))
            }
        }
    }

    private fun markWithdrawn(running: ActiveGame, playerUuid: UUID) {
        running.withdrawnPlayers.add(playerUuid)
        playerGames.remove(playerUuid)
        running.pendingPearls.entries.removeIf { it.value == playerUuid }
        running.touching.remove(playerUuid)
        running.golfSafeLocations.remove(playerUuid)
    }

    private fun tick() {
        active.values.toList().forEach { running ->
            val result = running.session.tick(System.currentTimeMillis()) ?: return@forEach
            finish(running, result)
        }
    }

    private fun finish(running: ActiveGame, result: MiniGameResult) {
        if (active[running.game] !== running) return
        active.remove(running.game)
        persistence.saveGameResult(result)
        persistence.saveSessionEnded(result)
        running.snapshots.values.filterNot { it.playerUuid in running.withdrawnPlayers }.forEach { snapshot ->
            Bukkit.getPlayer(snapshot.playerUuid)?.takeIf { it.isOnline }?.let { player ->
                snapshot.restore(player)
                persistence.removeRecovery(player.uniqueId)
                playerGames.remove(player.uniqueId)
            } ?: Bukkit.getPlayer(snapshot.playerUuid)?.let { persistence.saveRecovery(it, snapshot) }
        }
        running.session.participantUuids.forEach(playerGames::remove)
        running.session.participantUuids.mapNotNull(Bukkit::getPlayer).forEach { player ->
            player.sendMessage(MiniGameMessages.text(player, "messages.ended", "reason" to result.reason.name.lowercase()))
            result.entries.filter { it.completed }.forEach { entry ->
                player.sendMessage(
                    MiniGameMessages.text(
                        player,
                        "messages.result_entry",
                        "rank" to entry.rank,
                        "player" to (Bukkit.getOfflinePlayer(entry.playerUuid).name ?: entry.playerUuid.toString()),
                        "milliseconds" to (entry.elapsedMillis ?: "-"),
                        "score" to (entry.score ?: "-")
                    )
                )
            }
        }
    }
}

class MiniGameAdminMenu(
    private val runtime: MiniGameRuntime
) : Listener {
    private class Holder(val playerUuid: UUID, val itemData: MiniGameItemData) : OwnedMenuHolder(playerUuid)
    private class SelectionHolder(
        val playerUuid: UUID,
        val itemData: MiniGameItemData,
        var page: Int,
        val targets: MutableMap<Int, UUID> = mutableMapOf()
    ) : OwnedMenuHolder(playerUuid)
    private enum class HistoryView { RECENT, TOP }
    private class HistoryHolder(
        val playerUuid: UUID,
        val itemData: MiniGameItemData,
        val view: HistoryView,
        var page: Int
    ) : OwnedMenuHolder(playerUuid)

    fun open(player: Player, itemData: MiniGameItemData) {
        val holder = Holder(player.uniqueId, itemData)
        val inventory = Bukkit.createInventory(holder, 45, MiniGameMessages.text(player, "gui.title"))
        holder.backingInventory = inventory
        render(player, holder, inventory)
        player.openInventory(inventory)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        when (val holder = event.view.topInventory.holder) {
            is Holder -> {
                val player = MenuEventGuards.ownedTopClick(event, holder, "MiniGameAdminMenu.onClick: menu_click") ?: return
                when (event.rawSlot) {
                    20 -> if (!runtime.isRunning(holder.itemData) && runtime.start(player, holder.itemData)) player.closeInventory()
                    22 -> if (runtime.isRunning(holder.itemData) && runtime.stop(player, holder.itemData)) player.closeInventory()
                    24 -> openSelection(player, holder.itemData, 0)
                    29 -> {
                        runtime.adjustTimeLimit(player, holder.itemData, if (event.isLeftClick) 30 else -30)
                        render(player, holder, holder.backingInventory)
                    }
                    31 -> if (holder.itemData.gameId == "hideandseek") {
                        runtime.adjustPreparation(player, holder.itemData, if (event.isLeftClick) 10 else -10)
                        render(player, holder, holder.backingInventory)
                    }
                    33 -> openHistory(
                        player,
                        holder.itemData,
                        if (event.isRightClick) HistoryView.TOP else HistoryView.RECENT,
                        0
                    )
                    40 -> player.closeInventory()
                }
            }
            is SelectionHolder -> {
                val player = MenuEventGuards.ownedTopClick(event, holder, "MiniGameAdminMenu.onClick: selection_click") ?: return
                val layout = CCSystem.getAPI().getGuiLayoutService().pagedList54()
                when (event.rawSlot) {
                    layout.previousPageSlot -> openSelection(player, holder.itemData, holder.page - 1)
                    layout.nextPageSlot -> openSelection(player, holder.itemData, holder.page + 1)
                    layout.backSlot -> open(player, holder.itemData)
                    else -> holder.targets[event.rawSlot]?.let { target ->
                        runtime.toggleParticipantSelection(player, holder.itemData, target)
                        openSelection(player, holder.itemData, holder.page)
                    }
                }
            }
            is HistoryHolder -> {
                val player = MenuEventGuards.ownedTopClick(event, holder, "MiniGameAdminMenu.onClick: history_click") ?: return
                val layout = CCSystem.getAPI().getGuiLayoutService().pagedList54()
                when (event.rawSlot) {
                    layout.previousPageSlot -> openHistory(player, holder.itemData, holder.view, holder.page - 1)
                    layout.nextPageSlot -> openHistory(player, holder.itemData, holder.view, holder.page + 1)
                    layout.backSlot -> open(player, holder.itemData)
                }
            }
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? OwnedMenuHolder ?: return
        MenuEventGuards.cancelOwnedTopDrag(event, holder, "MiniGameAdminMenu.onDrag: menu_drag")
    }

    private fun render(player: Player, holder: Holder, inventory: Inventory) {
        GuiMenuItems.fillFramed(inventory)
        val game = MiniGameId(holder.itemData.worldUuid, holder.itemData.gameId)
        inventory.setItem(13, menuIcon(player, Material.CLOCK, "gui.game_info", data = listOf(data(player, "gui.game_label", game.gameId)), role = GuiElementRole.CONTENT))
        if (runtime.isRunning(holder.itemData)) {
            inventory.setItem(22, menuIcon(player, Material.RED_WOOL, "gui.stop", actions = singleAction(player, "gui.stop_action")))
        } else {
            inventory.setItem(20, menuIcon(player, Material.LIME_WOOL, "gui.start", actions = singleAction(player, "gui.start_action")))
        }
        if (game.gameId in setOf("hideandseek", "chase", "colosseum")) {
            inventory.setItem(24, menuIcon(player, Material.PLAYER_HEAD, "gui.participants", actions = singleAction(player, "gui.participants_action")))
        }
        inventory.setItem(
            29,
            menuIcon(
                player,
                Material.CLOCK,
                "gui.time_name",
                data = listOf(data(player, "gui.current_seconds", runtime.timeLimitSeconds(holder.itemData))),
                actions = adjustmentActions(player, 30)
            )
        )
        if (game.gameId == "hideandseek") {
            inventory.setItem(
                31,
                menuIcon(
                    player,
                    Material.REPEATER,
                    "gui.preparation_name",
                    data = listOf(data(player, "gui.current_seconds", runtime.preparationSeconds(holder.itemData))),
                    actions = adjustmentActions(player, 10)
                )
            )
        }
        inventory.setItem(
            33,
            menuIcon(
                player,
                Material.BOOK,
                "gui.history",
                actions = listOf(
                    action(player, "lore.click.left", "gui.history_recent"),
                    action(player, "lore.click.right", "gui.history_top")
                )
            )
        )
        inventory.setItem(40, menuIcon(player, Material.BARRIER, "gui.close", actions = singleAction(player, "gui.close_action")))
    }

    private fun openSelection(player: Player, itemData: MiniGameItemData, requestedPage: Int) {
        val candidates = runtime.selectionCandidates(player, itemData) ?: return
        val layoutService = CCSystem.getAPI().getGuiLayoutService()
        val layout = layoutService.pagedList54()
        val pages = maxOf(1, (candidates.size + layout.itemSlots.size - 1) / layout.itemSlots.size)
        val page = requestedPage.coerceIn(0, pages - 1)
        val holder = SelectionHolder(player.uniqueId, itemData, page)
        val inventory = Bukkit.createInventory(
            holder,
            54,
            MiniGameMessages.text(player, "gui.participant_title", "page" to (page + 1), "pages" to pages)
        )
        holder.backingInventory = inventory
        layoutService.applyStandardFrame(inventory)
        val selected = runtime.selectedParticipants(itemData)
        candidates.drop(page * layout.itemSlots.size).take(layout.itemSlots.size).forEachIndexed { index, uuid ->
            val slot = layout.itemSlots[index]
            holder.targets[slot] = uuid
            inventory.setItem(slot, participantItem(player, uuid, uuid in selected))
        }
        if (page > 0) inventory.setItem(layout.previousPageSlot, GuiMenuItems.icon(Material.ARROW, MiniGameMessages.text(player, "gui.previous")))
        if (page + 1 < pages) inventory.setItem(layout.nextPageSlot, GuiMenuItems.icon(Material.ARROW, MiniGameMessages.text(player, "gui.next")))
        inventory.setItem(layout.backSlot, GuiMenuItems.icon(Material.BARRIER, MiniGameMessages.text(player, "gui.back")))
        player.openInventory(inventory)
    }

    private fun participantItem(viewer: Player, playerUuid: UUID, selected: Boolean): ItemStack {
        val offline = Bukkit.getOfflinePlayer(playerUuid)
        val item = menuIcon(
            viewer,
            Material.PLAYER_HEAD,
            "gui.participant",
            data = listOf(
                GuiMenuIconData(MiniGameMessages.text(viewer, "gui.player_label"), offline.name ?: playerUuid.toString(), "§f"),
                GuiMenuIconData(MiniGameMessages.text(viewer, "gui.selection_label"), MiniGameMessages.text(viewer, if (selected) "gui.selected" else "gui.unselected"), if (selected) "§a" else "§7")
            ),
            actions = singleAction(viewer, "gui.participant_toggle"),
            glint = selected
        )
        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.owningPlayer = offline
        meta.setEnchantmentGlintOverride(selected)
        item.itemMeta = meta
        return item
    }

    private fun data(player: Player, key: String, value: Any?) =
        GuiMenuIconData(MiniGameMessages.text(player, key), value, "§f")

    private fun adjustmentActions(player: Player, seconds: Int) = listOf(
        action(player, "lore.click.left", "gui.increase", "seconds" to seconds),
        action(player, "lore.click.right", "gui.decrease", "seconds" to seconds)
    )

    private fun singleAction(player: Player, actionKey: String): List<GuiMenuIconAction> {
        val operation = MiniGameMessages.text(player, "lore.click.any")
        val action = MiniGameMessages.text(player, actionKey)
        return listOf(GuiMenuIconAction(operation, action, MiniGameMessages.text(player, "lore.action_single_with_operation", "operation" to operation, "action" to action), true))
    }

    private fun action(player: Player, operationKey: String, actionKey: String, vararg placeholders: Pair<String, Any?>): GuiMenuIconAction =
        GuiMenuIconAction(MiniGameMessages.text(player, operationKey), MiniGameMessages.text(player, actionKey, *placeholders), null, true)

    private fun menuIcon(
        player: Player,
        material: Material,
        nameKey: String,
        role: GuiElementRole = GuiElementRole.ACTION,
        data: List<GuiMenuIconData> = emptyList(),
        actions: List<GuiMenuIconAction> = emptyList(),
        glint: Boolean? = null
    ): ItemStack = CCSystem.getAPI().getGuiElementService().menuIcon(
        GuiMenuIconSpec(material, GuiNameSpec.Text(MiniGameMessages.text(player, nameKey), GuiNameStyle.DEFAULT), role, 1, emptyList(), data, emptyList(), emptyList(), emptyList(), actions, glint)
    )

    private fun openHistory(player: Player, itemData: MiniGameItemData, view: HistoryView, requestedPage: Int) {
        val recent = runtime.gameHistory(player, itemData) ?: return
        val top = if (view == HistoryView.TOP) runtime.topRecords(player, itemData).orEmpty() else emptyList()
        val count = if (view == HistoryView.RECENT) recent.size else top.size
        val layoutService = CCSystem.getAPI().getGuiLayoutService()
        val layout = layoutService.pagedList54()
        val pages = maxOf(1, (count + layout.itemSlots.size - 1) / layout.itemSlots.size)
        val page = requestedPage.coerceIn(0, pages - 1)
        val holder = HistoryHolder(player.uniqueId, itemData, view, page)
        val inventory = Bukkit.createInventory(
            holder,
            54,
            MiniGameMessages.text(
                player,
                if (view == HistoryView.RECENT) "gui.history_recent_title" else "gui.history_top_title",
                "page" to (page + 1),
                "pages" to pages
            )
        )
        holder.backingInventory = inventory
        layoutService.applyStandardFrame(inventory)
        if (view == HistoryView.RECENT) {
            recent.drop(page * layout.itemSlots.size).take(layout.itemSlots.size).forEachIndexed { index, record ->
                inventory.setItem(layout.itemSlots[index], historyItem(player, record))
            }
        } else {
            top.drop(page * layout.itemSlots.size).take(layout.itemSlots.size).forEachIndexed { index, record ->
                inventory.setItem(layout.itemSlots[index], topRecordItem(player, page * layout.itemSlots.size + index + 1, record))
            }
        }
        runtime.personalBest(player, itemData)?.let { best ->
            inventory.setItem(layout.infoSlot, personalBestItem(player, best))
        }
        if (page > 0) inventory.setItem(layout.previousPageSlot, GuiMenuItems.icon(Material.ARROW, MiniGameMessages.text(player, "gui.previous")))
        if (page + 1 < pages) inventory.setItem(layout.nextPageSlot, GuiMenuItems.icon(Material.ARROW, MiniGameMessages.text(player, "gui.next")))
        inventory.setItem(layout.backSlot, GuiMenuItems.icon(Material.BARRIER, MiniGameMessages.text(player, "gui.back")))
        player.openInventory(inventory)
    }

    private fun historyItem(player: Player, record: MiniGameHistoryRecord): ItemStack = GuiMenuItems.icon(
        Material.PAPER,
        MiniGameMessages.text(player, "gui.history_record", "date" to formatDate(record.recordedAtMillis)),
        listOf(
            MiniGameMessages.text(player, "gui.history_reason", "reason" to record.result.reason.name.lowercase()),
            MiniGameMessages.text(player, "gui.history_entries", "count" to record.result.entries.size)
        ).map { GuiLoreLine.Text(it) }
    )

    private fun topRecordItem(player: Player, position: Int, record: MiniGameRankedRecord): ItemStack = GuiMenuItems.icon(
        Material.GOLD_INGOT,
        MiniGameMessages.text(
            player,
            "gui.top_record",
            "rank" to position,
            "player" to (Bukkit.getOfflinePlayer(record.entry.playerUuid).name ?: record.entry.playerUuid.toString())
        ),
        listOf(
            MiniGameMessages.text(player, "gui.record_score", "score" to (record.entry.score ?: "-")),
            MiniGameMessages.text(player, "gui.record_time", "milliseconds" to (record.entry.elapsedMillis ?: "-")),
            MiniGameMessages.text(player, "gui.record_date", "date" to formatDate(record.recordedAtMillis))
        ).map { GuiLoreLine.Text(it) }
    )

    private fun personalBestItem(player: Player, record: MiniGameRankedRecord): ItemStack = GuiMenuItems.icon(
        Material.NETHER_STAR,
        MiniGameMessages.text(player, "gui.personal_best"),
        listOf(
            MiniGameMessages.text(player, "gui.record_score", "score" to (record.entry.score ?: "-")),
            MiniGameMessages.text(player, "gui.record_time", "milliseconds" to (record.entry.elapsedMillis ?: "-"))
        ).map { GuiLoreLine.Text(it) }
    )

    private fun formatDate(epochMillis: Long): String = HISTORY_DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis))

    private companion object {
        val HISTORY_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.systemDefault())
    }
}
