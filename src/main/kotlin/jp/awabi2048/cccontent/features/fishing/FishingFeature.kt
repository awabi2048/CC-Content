package jp.awabi2048.cccontent.features.fishing

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.action.ContentAction
import com.awabi2048.ccsystem.api.action.ContentActionType
import jp.awabi2048.cccontent.CCContent
import jp.awabi2048.cccontent.features.catalog.CatalogCondition
import jp.awabi2048.cccontent.features.catalog.CatalogItem
import jp.awabi2048.cccontent.features.catalog.CatalogStore
import jp.awabi2048.cccontent.features.rank.RankReleasePolicy
import jp.awabi2048.cccontent.features.rank.profession.Profession
import jp.awabi2048.cccontent.features.rank.profession.profile.FisherSkillProfile
import jp.awabi2048.cccontent.features.rank.profession.profile.ProfessionExperience
import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import jp.awabi2048.cccontent.integration.myworld.MyWorldBridge
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.FluidCollisionMode
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.FishHook
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.io.File
import java.time.Duration
import java.util.Random
import java.util.UUID
import kotlin.math.roundToLong

class FishingFeature(
    private val plugin: CCContent,
    private val catalogStore: CatalogStore,
    private val myWorldBridge: MyWorldBridge
) : Listener {
    private enum class Phase { WAITING, HOOK_WINDOW, FIGHT }

    private data class ActiveSession(
        val hook: FishHook,
        var bait: BaitDefinition?,
        val rod: RodDefinition?,
        var phase: Phase = Phase.WAITING,
        var definition: FishDefinition? = null,
        var catchData: FishCatch? = null,
        var fightState: FishingFightState? = null,
        var bossBar: BossBar? = null,
        var task: BukkitTask? = null,
        var lastZone: FishingEffectivenessZone? = null,
        var statusTicksRemaining: Long = 0L,
        var lastStatus: FishingFightStatus? = null,
        var fightOrigin: org.bukkit.Location? = null,
        var forwardAxis: Vector? = null,
        var lateralAxis: Vector? = null,
        var fightElapsedTicks: Long = 0L,
        var rodMissingTicks: Long = 0L,
        var lastInputTick: Int = -1,
        var ignoredInstabilityEventsRemaining: Int = 0,
        var fightScore: FishingFightScore = FishingFightScore()
    )

    private lateinit var settings: FishingSettings
    private lateinit var items: FishingItems
    private lateinit var fishdex: FishdexStore
    private lateinit var searchStore: FishingSearchStore
    private val sessions = mutableMapOf<UUID, ActiveSession>()
    private val searchReady = mutableMapOf<UUID, Boolean>()
    private var searchTask: BukkitTask? = null
    private val random = Random()

    fun initialize(logger: FeatureInitializationLogger) {
        settings = FishingSettings.load(plugin)
        if (!settings.enabled) return
        items = FishingItems(plugin, settings)
        items.registerBaseItems()
        fishdex = FishdexStore(catalogStore)
        searchStore = FishingSearchStore(File(plugin.dataFolder, "data/fishing/search.yml"))
        plugin.server.pluginManager.registerEvents(this, plugin)
        searchTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable { updateSearchActionBars() }, 10L, 10L)
        logger.setStatus("Fishing", FeatureInitializationLogger.Status.SUCCESS)
        logger.addSummaryMessage("Fishing", "釣り・エサ・お魚辞典を登録しました (${settings.fishes.size}種類)")
    }

    fun registerDictionary(opener: (Player) -> Unit) {
        items.registerDictionary(opener) { player, event -> showLocalFishingHint(player, event) }
    }

    private fun showLocalFishingHint(player: Player, event: PlayerInteractEvent) {
        event.isCancelled = true
        val block = surveyWaterBlock(player)
        if (block == null || !isReadyResourceWorld(player)) {
            player.sendMessage(message(player, "fishing.dictionary.hint.not_water"))
            return
        }
        val bait = items.resolveBait(player.inventory.itemInOffHand)
        val candidates = FishingCatchSelector.candidates(
            FishingContext(player.world, block.location.add(0.5, 0.5, 0.5), fisherContext(player).level),
            settings.fishes,
            bait
        )
        if (candidates.isEmpty()) return
        val lines = buildList {
            add(GuiLoreLine.Text(message(player, "fishing.dictionary.hint.title")))
            candidates.forEach { fish ->
                add(GuiLoreLine.Text("§7・§f" + message(player, "fishing.catalog.item.${fish.id}")))
            }
        }
        CCSystem.getAPI().getLoreService()
            .render(GuiLoreSpec.Rich(lines, GuiLoreFrame.BOTH))
            .forEach(player::sendMessage)
        player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.4f)
    }

    fun shutdown() {
        searchTask?.cancel()
        searchTask = null
        sessions.keys.toList().forEach { fail(it, "fishing.failed.cancelled", false) }
        searchReady.keys.forEach { playerId ->
            Bukkit.getPlayer(playerId)?.sendActionBar(Component.empty())
        }
        searchReady.clear()
        if (::searchStore.isInitialized) searchStore.save()
        if (::items.isInitialized) items.unregister()
    }

    @EventHandler
    fun onFish(event: PlayerFishEvent) {
        val player = event.player
        when (event.state) {
            PlayerFishEvent.State.FISHING -> startCast(event, player)
            PlayerFishEvent.State.BITE -> onBite(event, player)
            PlayerFishEvent.State.CAUGHT_FISH,
            PlayerFishEvent.State.CAUGHT_ENTITY -> {
                val session = sessions[player.uniqueId]
                if (session != null) {
                    event.isCancelled = true
                    when (session.phase) {
                        Phase.HOOK_WINDOW -> startFight(player, session)
                        Phase.FIGHT -> if (session.fightElapsedTicks > 0L) {
                            applyFightInput(player, session, true)
                        }
                        else -> Unit
                    }
                } else if (event.state == PlayerFishEvent.State.CAUGHT_FISH) {
                    handleVanillaCatch(player, event.caught as? Item)
                }
            }
            PlayerFishEvent.State.REEL_IN -> {
                val session = sessions[player.uniqueId]
                when (session?.phase) {
                    Phase.HOOK_WINDOW -> {
                        event.isCancelled = true
                        startFight(player, session)
                    }
                    Phase.FIGHT -> {
                        event.isCancelled = true
                        if (session.fightElapsedTicks > 0L) applyFightInput(player, session, true)
                    }
                    else -> clearSession(player.uniqueId, removeHook = false)
                }
            }
            PlayerFishEvent.State.IN_GROUND -> {
                val session = sessions[player.uniqueId]
                if (session?.phase == Phase.WAITING) {
                    event.isCancelled = true
                    fail(player.uniqueId, "fishing.failed.invalid_water")
                } else if (session?.phase in setOf(Phase.HOOK_WINDOW, Phase.FIGHT)) {
                    event.isCancelled = true
                }
            }
            PlayerFishEvent.State.FAILED_ATTEMPT -> {
                if (sessions[player.uniqueId]?.phase in setOf(Phase.HOOK_WINDOW, Phase.FIGHT)) {
                    event.isCancelled = true
                } else clearSession(player.uniqueId, removeHook = false)
            }
            else -> Unit
        }
    }

    private fun startCast(event: PlayerFishEvent, player: Player) {
        clearSession(player.uniqueId, removeHook = false)
        val rodItem = player.inventory.itemInMainHand
        if (rodItem.type != Material.FISHING_ROD) return
        if (!isReadyResourceWorld(player)) {
            return
        }
        if (!items.isUsableRod(rodItem)) {
            event.isCancelled = true
            player.sendMessage(message(player, "fishing.error.rod_broken"))
            player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 0.9f, 0.8f)
            return
        }
        val fisher = fisherContext(player)
        val bait = items.resolveBait(player.inventory.itemInOffHand)
        val rod = items.resolveRod(rodItem)
        val lureLevel = rodItem.getEnchantmentLevel(Enchantment.LURE)
        val lureMultiplier = (1.0 - settings.minigame.lureReductionPerLevel * lureLevel).coerceAtLeast(0.2)
        val skillWaitMultiplier = (1.0 - fisher.waitTimeReduction).coerceAtLeast(0.8)
        val waitMultiplier = (bait?.waitTimeMultiplier ?: 1.0) * lureMultiplier * skillWaitMultiplier
        event.hook.setApplyLure(false)
        event.hook.setWaitTime(
            (settings.minigame.waitTimeMinTicks * waitMultiplier).roundToLong().toInt().coerceAtLeast(1),
            (settings.minigame.waitTimeMaxTicks * waitMultiplier).roundToLong().toInt().coerceAtLeast(1)
        )
        sessions[player.uniqueId] = ActiveSession(event.hook, bait, rod)
        player.playSound(player.location, Sound.ENTITY_FISHING_BOBBER_THROW, 0.7f, 1.0f)
    }

    private fun onBite(event: PlayerFishEvent, player: Player) {
        val session = sessions[player.uniqueId] ?: return
        if (session.hook.uniqueId != event.hook.uniqueId || session.phase != Phase.WAITING) return
        val fisher = fisherContext(player)
        val bait = items.resolveBait(player.inventory.itemInOffHand)
        val selected = FishingCatchSelector.select(
            FishingContext(player.world, event.hook.location, fisher.level),
            settings.fishes,
            bait,
            session.rod,
            random
        )
        if (selected == null) {
            event.isCancelled = true
            player.sendMessage(message(player, "fishing.no_catch"))
            player.playSound(player.location, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.6f, 0.6f)
            clearSession(player.uniqueId, removeHook = true)
            return
        }
        val preserveBait = fisher.active && random.nextDouble() < fisher.baitSaveChance
        if (bait != null && settings.consumeBaitOnValidSession) {
            items.consumeBait(player, consume = !preserveBait)
        }
        session.bait = bait
        session.phase = Phase.HOOK_WINDOW
        session.definition = selected.first
        session.catchData = selected.second
        player.showTitle(Title.title(
            Component.empty(),
            Component.text(message(player, "fishing.fight.hit_title")),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(200))
        ))
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f)
        val hookTicks = (settings.minigame.baseHookWindowTicks * fisher.hookWindowMultiplier)
            .roundToLong()
            .coerceAtLeast(1L)
        session.task = plugin.server.scheduler.runTaskLater(
            plugin,
            Runnable { fail(player.uniqueId, "fishing.failed.hook_timeout") },
            hookTicks
        )
    }

    @EventHandler
    fun onRodClick(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val session = sessions[event.player.uniqueId] ?: return
        val action = event.action
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK &&
            action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return
        if (event.player.inventory.itemInMainHand.type != Material.FISHING_ROD) return
        val right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK
        when (session.phase) {
            Phase.WAITING -> if (!right) {
                if (session.hook.state == FishHook.HookState.BOBBING && isValidFishingWater(session.hook.location)) {
                    event.isCancelled = true
                    adjustWaitingHook(event.player, session, event.player.isSneaking)
                }
            }
            Phase.HOOK_WINDOW -> if (right) {
                event.isCancelled = true
                startFight(event.player, session)
            }
            Phase.FIGHT -> {
                if (session.fightElapsedTicks <= 0L) return
                event.isCancelled = true
                applyFightInput(event.player, session, right)
            }
        }
    }

    private fun adjustWaitingHook(player: Player, session: ActiveSession, closer: Boolean) {
        if (!session.hook.isValid) return
        val playerVector = player.location.toVector()
        val direction = session.hook.location.toVector().subtract(playerVector).setY(0.0)
        if (direction.lengthSquared() < 0.0001) return
        val distance = direction.length()
        val nextDistance = (distance + if (closer) -0.75 else 0.75).coerceIn(2.0, 30.0)
        val horizontal = direction.normalize().multiply(nextDistance)
        val next = session.hook.location.clone()
        next.x = playerVector.x + horizontal.x
        next.z = playerVector.z + horizontal.z
        if (!isValidFishingWater(next)) return
        session.hook.teleport(next)
        session.hook.velocity = Vector()
        player.playSound(player.location, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.35f, 2.0f)
    }

    private fun startFight(player: Player, session: ActiveSession) {
        session.task?.cancel()
        val definition = session.definition ?: return
        val fisher = fisherContext(player)
        val duration = (
            settings.minigame.baseFightDurationTicks *
                definition.fight.durationMultiplier *
                fisher.durationMultiplier
            ).roundToLong().coerceAtLeast(settings.minigame.fightIntervalTicks)
        session.phase = Phase.FIGHT
        session.fightState = FishingFightState(
            settings.minigame.initialEffectiveness,
            duration,
            if (random.nextBoolean()) 1 else -1
        )
        session.ignoredInstabilityEventsRemaining = fisher.ignoredInstabilityEvents
        session.bossBar = BossBar.bossBar(
            Component.text(message(player, "fishing.fight.status.${FishingFightStatus.HOOKED.messageId}")),
            (settings.minigame.initialEffectiveness / 100.0).toFloat(),
            BossBar.Color.YELLOW,
            BossBar.Overlay.PROGRESS
        ).also(player::showBossBar)
        session.statusTicksRemaining = settings.minigame.statusMessageTicks
        session.lastStatus = FishingFightStatus.HOOKED
        val origin = session.hook.location.clone()
        val forward = origin.toVector().subtract(player.location.toVector()).setY(0.0)
        if (forward.lengthSquared() < 0.0001) forward.copy(player.location.direction.clone().setY(0.0))
        forward.normalize()
        session.fightOrigin = origin
        session.forwardAxis = forward
        session.lateralAxis = Vector(-forward.z, 0.0, forward.x)
        session.hook.setApplyLure(false)
        runCatching { session.hook.setTimeUntilBite(Int.MAX_VALUE) }
        session.hook.velocity = Vector()
        player.playSound(player.location, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.9f, 1.2f)
        updateFightDisplay(player, session)
        session.task = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable { advanceFight(player.uniqueId) },
            settings.minigame.fightIntervalTicks,
            settings.minigame.fightIntervalTicks
        )
    }

    private fun applyFightInput(player: Player, session: ActiveSession, rightClick: Boolean) {
        val tick = Bukkit.getCurrentTick()
        if (session.lastInputTick == tick) return
        session.lastInputTick = tick
        val current = session.fightState ?: return
        val rodPower = session.rod?.powerMultiplier ?: 1.0
        val bedrock = isBedrockPlayer(player)
        val increase = if (bedrock) !player.isSneaking else rightClick
        val facingBonus = facingResistanceBonus(player, session, current)
        val delta = settings.minigame.inputStep * rodPower * facingBonus * if (increase) 1.0 else -1.0
        session.fightState = current.applyInput(delta)
        player.playSound(
            player.location,
            Sound.BLOCK_NOTE_BLOCK_HAT,
            0.25f,
            if (increase) 1.5f else 0.8f
        )
        updateFightDisplay(player, session)
    }

    private fun isValidFishingWater(location: org.bukkit.Location): Boolean {
        val block = location.block
        return FishingWaterAnalyzer.isWater(block) ||
            FishingWaterAnalyzer.isWater(block.getRelative(0, -1, 0))
    }

    private fun surveyWaterBlock(player: Player): org.bukkit.block.Block? =
        player.rayTraceBlocks(10.0, FluidCollisionMode.ALWAYS)
            ?.hitBlock
            ?.takeIf(FishingWaterAnalyzer::isWater)

    private fun advanceFight(playerId: UUID) {
        val player = Bukkit.getPlayer(playerId)
        val session = sessions[playerId]
        if (player == null || session == null || session.phase != Phase.FIGHT) return
        if (!player.isOnline || !isReadyResourceWorld(player)) {
            fail(playerId, "fishing.failed.cancelled")
            return
        }
        if (player.inventory.itemInMainHand.type != Material.FISHING_ROD) {
            session.rodMissingTicks += settings.minigame.fightIntervalTicks
            if (session.rodMissingTicks >= 15L) fail(playerId, "fishing.failed.no_rod")
            return
        }
        session.rodMissingTicks = 0L
        val definition = session.definition ?: return
        val fisher = fisherContext(player)
        val stability = fisher.stabilityBonus.coerceAtMost(0.9)
        val current = session.fightState ?: return
        var next = current.advance(
            definition.fight,
            settings.minigame.fightIntervalTicks,
            stability,
            settings.minigame.resistanceSmoothing,
            settings.minigame.lateralSmoothing,
            random
        )
        if (session.ignoredInstabilityEventsRemaining > 0 && next.driftDirection != current.driftDirection) {
            next = next.copy(
                driftDirection = current.driftDirection,
                driftVelocity = current.driftVelocity
            )
            session.ignoredInstabilityEventsRemaining--
        }
        session.fightState = next
        session.fightElapsedTicks += settings.minigame.fightIntervalTicks
        updateHookVisual(session, next)
        val zone = updateFightDisplay(player, session)
        session.fightScore = session.fightScore.record(
            zone,
            next.effectiveness,
            settings.minigame.fightIntervalTicks
        )
        advanceFightStatus(player, session, zone)
        if (next.remainingTicks <= 0L) resolveFight(player, session)
    }

    private fun facingResistanceBonus(
        player: Player,
        session: ActiveSession,
        state: FishingFightState
    ): Double {
        if (kotlin.math.abs(state.lateralVelocity) < 0.001) return 1.0
        val lateral = session.lateralAxis ?: return 1.0
        val opposite = lateral.clone().multiply(-kotlin.math.sign(state.lateralVelocity))
        val view = player.location.direction.clone().setY(0.0)
        if (view.lengthSquared() < 0.0001) return 1.0
        return if (view.normalize().dot(opposite) >= 0.45) settings.minigame.facingBonusMultiplier else 1.0
    }

    private fun updateHookVisual(session: ActiveSession, state: FishingFightState) {
        val origin = session.fightOrigin ?: return
        val forward = session.forwardAxis ?: return
        val lateral = session.lateralAxis ?: return
        if (!session.hook.isValid) return
        val forwardOffset = ((state.effectiveness - 50.0) / 50.0) * settings.minigame.visualForwardRange
        val location = origin.clone()
            .add(forward.clone().multiply(forwardOffset))
            .add(lateral.clone().multiply(state.lateralOffset * settings.minigame.visualLateralRange))
        session.hook.teleport(location)
        session.hook.velocity = Vector()
        location.world.spawnParticle(Particle.SPLASH, location.clone().add(0.0, 0.08, 0.0), 3, 0.18, 0.04, 0.18, 0.02)
        location.world.spawnParticle(Particle.BUBBLE, location.clone().subtract(0.0, 0.15, 0.0), 2, 0.12, 0.04, 0.12, 0.01)
    }

    private fun updateFightDisplay(player: Player, session: ActiveSession): FishingEffectivenessZone {
        val state = requireNotNull(session.fightState) { "Fishing fight state is required while rendering" }
        val definition = requireNotNull(session.definition) { "Fish definition is required while rendering" }
        val zone = state.zone(
            definition.fight,
            settings.minigame.greenWidth,
            settings.minigame.yellowMargin
        )
        if (session.lastZone != null && session.lastZone != zone) {
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, when (zone) {
                FishingEffectivenessZone.GREEN -> 1.7f
                FishingEffectivenessZone.YELLOW -> 1.2f
                FishingEffectivenessZone.ORANGE -> 0.75f
            })
        }
        session.lastZone = zone
        session.bossBar?.apply {
            progress((state.effectiveness / 100.0).toFloat().coerceIn(0.0f, 1.0f))
            color(when (zone) {
                FishingEffectivenessZone.GREEN -> BossBar.Color.GREEN
                FishingEffectivenessZone.YELLOW -> BossBar.Color.YELLOW
                FishingEffectivenessZone.ORANGE -> BossBar.Color.RED
            })
        }
        player.sendActionBar(Component.text(message(
            player,
            if (isBedrockPlayer(player)) "fishing.fight.input_bedrock" else "fishing.fight.input_java"
        )))
        return zone
    }

    private fun advanceFightStatus(
        player: Player,
        session: ActiveSession,
        zone: FishingEffectivenessZone
    ) {
        session.statusTicksRemaining -= settings.minigame.fightIntervalTicks
        if (session.statusTicksRemaining > 0L) return
        val candidates = FishingFightStatus.candidates(zone)
        val available = candidates.filterNot { it == session.lastStatus }.ifEmpty { candidates }
        val next = available[random.nextInt(available.size)]
        session.lastStatus = next
        session.statusTicksRemaining = settings.minigame.statusMessageTicks
        session.bossBar?.name(Component.text(message(player, "fishing.fight.status.${next.messageId}")))
    }

    private fun resolveFight(player: Player, session: ActiveSession) {
        val definition = session.definition ?: return
        val successProbability = session.fightScore.successProbability(definition.rarity)
        if (random.nextDouble() <= successProbability) complete(player, session)
        else fail(player.uniqueId, "fishing.failed.final_roll")
    }

    private fun complete(player: Player, session: ActiveSession) {
        val catchData = session.catchData ?: return
        val before = fishdex.load(player.uniqueId)[catchData.fishId]
        clearSession(player.uniqueId, removeHook = true)
        val catchItem = items.createCatch(player, catchData)
        player.inventory.addItem(catchItem).values.forEach { overflow ->
            player.world.dropItemNaturally(player.location, overflow)
        }
        val fisher = fisherContext(player)
        val recorded = fishdex.record(player.uniqueId, catchData)
        if (fisher.active) {
            val experience = ProfessionExperience.NORMAL_ACTION +
                (if (before?.discovered != true) ProfessionExperience.FIRST_DISCOVERY_BONUS else 0L) +
                (if (catchData.quality != FishQuality.COMMON) ProfessionExperience.HIGH_QUALITY_BONUS else 0L)
            plugin.getRankManager()?.addProfessionExp(player.uniqueId, experience)
            plugin.getRankManager()?.recordProfessionCycleAction(
                player.uniqueId,
                highQuality = catchData.quality != FishQuality.COMMON,
                firstDiscovery = before?.discovered != true
            )
        }
        publishCatchAction(player, catchData, before?.discovered != true)
        val preserveDurability = fisher.active && random.nextDouble() < fisher.durabilitySaveChance
        if (!preserveDurability && items.damageRod(player)) {
            player.sendMessage(message(player, "fishing.error.rod_broken"))
            player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 0.9f, 0.9f)
        }
        player.sendMessage(message(player, "fishing.caught"))
        items.catchInformationLines(player, catchData).forEach(player::sendMessage)
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f)
        if (before?.discovered != true) {
            player.sendMessage(message(player, "fishing.dictionary.discovered"))
            player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.75f, 1.3f)
        } else if ((before.maximumWeight ?: 0) < (recorded.maximumWeight ?: 0)) {
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.65f, 1.8f)
        }
    }

    private fun fail(playerId: UUID, key: String, notify: Boolean = true) {
        val player = Bukkit.getPlayer(playerId)
        clearSession(playerId, removeHook = true)
        if (notify && player?.isOnline == true) {
            player.sendMessage(message(player, key))
            player.playSound(player.location, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.75f, 0.65f)
        }
    }

    private fun clearSession(playerId: UUID, removeHook: Boolean) {
        val session = sessions.remove(playerId) ?: return
        session.task?.cancel()
        val player = Bukkit.getPlayer(playerId)
        session.bossBar?.let { bar -> player?.hideBossBar(bar) }
        player?.sendActionBar(Component.empty())
        if (removeHook && session.hook.isValid) session.hook.remove()
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        fail(event.player.uniqueId, "fishing.failed.cancelled", false)
        searchReady.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        fail(event.player.uniqueId, "fishing.failed.cancelled", false)
        event.player.sendActionBar(Component.empty())
        searchReady.remove(event.player.uniqueId)
    }

    fun catalogItems(): List<CatalogItem> = settings.fishes.map { fish ->
        CatalogItem(
            fish.id,
            fish.material,
            buildList {
                add(CatalogCondition("fishing.dictionary.condition.biome", localizedValues =
                    if (fish.biomes.isEmpty()) listOf("fishing.dictionary.condition.anywhere")
                    else fish.biomes.map { "fishing.dictionary.biome.$it" }))
                add(CatalogCondition(
                    "fishing.dictionary.condition.water",
                    localizedValues = listOf(
                        if (fish.water.type == FishingWaterType.ANY) {
                            "fishing.dictionary.condition.anywhere"
                        } else {
                            "fishing.dictionary.water.${fish.water.type.id}"
                        }
                    )
                ))
                add(CatalogCondition("fishing.dictionary.condition.weather", localizedValues =
                    if (fish.weather.isEmpty()) listOf("fishing.dictionary.condition.anytime")
                    else fish.weather.map { "fishing.dictionary.weather.${it.name.lowercase()}" }))
                add(CatalogCondition("fishing.dictionary.condition.time", localizedValues =
                    if (fish.times.isEmpty()) listOf("fishing.dictionary.condition.anytime")
                    else fish.times.map { "fishing.dictionary.time.${it.name.lowercase()}" }))
                add(CatalogCondition("fishing.dictionary.condition.level",
                    rawValue = fish.minLevel.toString()))
                if (fish.requiredBaitTags.isNotEmpty()) {
                    val matchingBaits = settings.baits.filter { it.specialTags.containsAll(fish.requiredBaitTags) }
                    add(CatalogCondition(
                        "fishing.dictionary.condition.bait",
                        localizedValues = matchingBaits.map { "custom_items.fishing.bait_${it.id}.name" }
                    ))
                }
            }
        )
    }

    fun getSearchTarget(playerId: UUID): String? {
        val target = searchStore.get(playerId) ?: return null
        if (catalogStore.entries(playerId, jp.awabi2048.cccontent.features.catalog.CatalogType.FISHING)[target]?.discovered == true) {
            return target
        }
        searchStore.set(playerId, null)
        return null
    }

    fun setSearchTarget(player: Player, fishId: String?) {
        require(fishId == null || settings.fishes.any { it.id == fishId }) { "Unknown fishing target: $fishId" }
        require(fishId == null ||
            catalogStore.entries(player.uniqueId, jp.awabi2048.cccontent.features.catalog.CatalogType.FISHING)[fishId]?.discovered == true
        ) { "Undiscovered fish cannot be selected: $fishId" }
        searchStore.set(player.uniqueId, fishId)
        searchReady.remove(player.uniqueId)
        player.sendActionBar(Component.empty())
        player.playSound(
            player.location,
            if (fishId == null) Sound.UI_BUTTON_CLICK else Sound.BLOCK_NOTE_BLOCK_BELL,
            0.7f,
            if (fishId == null) 0.8f else 1.4f
        )
    }

    private fun updateSearchActionBars() {
        Bukkit.getOnlinePlayers().forEach { player ->
            if (sessions.containsKey(player.uniqueId) ||
                player.inventory.itemInMainHand.type != Material.FISHING_ROD) {
                if (searchReady.remove(player.uniqueId) != null && !sessions.containsKey(player.uniqueId)) {
                    player.sendActionBar(Component.empty())
                }
                return@forEach
            }
            val targetId = getSearchTarget(player.uniqueId) ?: run {
                if (searchReady.remove(player.uniqueId) != null) player.sendActionBar(Component.empty())
                return@forEach
            }
            val target = settings.fishes.firstOrNull { it.id == targetId } ?: return@forEach
            val bait = items.resolveBait(player.inventory.itemInOffHand)
            val surveyBlock = surveyWaterBlock(player)
            val ready = isReadyResourceWorld(player) &&
                surveyBlock != null &&
                target in FishingCatchSelector.candidates(
                    FishingContext(
                        player.world,
                        surveyBlock.location.add(0.5, 0.5, 0.5),
                        fisherContext(player).level
                    ),
                    settings.fishes,
                    bait
                )
            val previous = searchReady.put(player.uniqueId, ready)
            if (ready) {
                player.sendActionBar(Component.text("§7" + message(player, "fishing.search.ready")))
                if (previous != true) {
                    player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.45f, 1.45f)
                }
            } else if (previous == true) {
                player.sendActionBar(Component.empty())
            }
        }
    }

    private data class FisherContext(
        val active: Boolean,
        val level: Int,
        val hookWindowMultiplier: Double,
        val durationMultiplier: Double,
        val stabilityBonus: Double,
        val waitTimeReduction: Double,
        val baitSaveChance: Double,
        val durabilitySaveChance: Double,
        val vanillaExtraCatchChance: Double,
        val ignoredInstabilityEvents: Int
    )

    private fun fisherContext(player: Player): FisherContext {
        val inactive = FisherContext(false, 0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)
        val rank = plugin.getRankManager() ?: return inactive
        val profession = rank.getPlayerProfession(player.uniqueId)
        val active = profession?.profession == Profession.FISHER &&
            RankReleasePolicy.canAccessProfession(player, Profession.FISHER)
        if (!active) return inactive
        val profile = rank.getTypedProfessionProfile(player.uniqueId) as? FisherSkillProfile
            ?: return inactive.copy(active = true, level = rank.getCurrentProfessionLevel(player.uniqueId))
        return FisherContext(
            true,
            profile.level,
            (1.0 + profile.hookWindowBonus).coerceAtLeast(1.0),
            (1.0 - profile.fightDurationReduction).coerceIn(0.1, 1.0),
            profile.fightStabilityBonus.coerceIn(0.0, 0.9),
            profile.waitTimeReduction.coerceIn(0.0, 0.2),
            profile.baitSaveChance.coerceIn(0.0, 1.0),
            profile.durabilitySaveChance.coerceIn(0.0, 1.0),
            profile.vanillaExtraCatchChance.coerceIn(0.0, 1.0),
            profile.ignoredInstabilityEvents.coerceAtLeast(0)
        )
    }

    private fun handleVanillaCatch(player: Player, caught: Item?) {
        val fisher = fisherContext(player)
        if (fisher.active) {
            plugin.getRankManager()?.addProfessionExp(player.uniqueId, ProfessionExperience.NORMAL_ACTION)
            plugin.getRankManager()?.recordProfessionCycleAction(player.uniqueId)
            if (caught != null && random.nextDouble() < fisher.vanillaExtraCatchChance) {
                val extra = caught.itemStack.clone().apply { amount = 1 }
                player.inventory.addItem(extra).values.forEach { player.world.dropItemNaturally(player.location, it) }
            }
        }
        CCSystem.getAPI().getContentActionDispatcher().publish(
            ContentAction(
                actionId = UUID.randomUUID(),
                schemaVersion = 1,
                occurredAt = CCSystem.getAPI().getSharedClockService().now().toInstant(),
                playerId = player.uniqueId,
                actionType = ContentActionType.VANILLA_FISH_CAUGHT,
                amount = 1L,
                worldKey = player.world.key
            )
        )
    }

    private fun publishCatchAction(player: Player, catch: FishCatch, firstDiscovery: Boolean) {
        CCSystem.getAPI().getContentActionDispatcher().publish(
            ContentAction(
                actionId = UUID.randomUUID(),
                schemaVersion = 1,
                occurredAt = CCSystem.getAPI().getSharedClockService().now().toInstant(),
                playerId = player.uniqueId,
                actionType = ContentActionType.FISH_CAUGHT,
                amount = 1L,
                worldKey = player.world.key,
                metadata = mapOf(
                    "fishId" to catch.fishId,
                    "quality" to catch.quality.id,
                    "firstDiscovery" to firstDiscovery.toString()
                )
            )
        )
    }

    private fun isReadyResourceWorld(player: Player): Boolean =
        CCSystem.getAPI().getResourceWorldLifecycleService().isReady(player.world.key)

    private fun message(player: Player?, key: String, vararg placeholders: Pair<String, Any?>): String =
        CCSystem.getAPI().getI18nString(
            player,
            key,
            placeholders.associate { (name, value) -> name to (value ?: "null") }
        ).replace('&', '§')

    private fun isBedrockPlayer(player: Player): Boolean {
        return myWorldBridge.isBedrock(player)
    }
}
