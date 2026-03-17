package jp.awabi2048.cccontent.features.arena

import jp.awabi2048.cccontent.features.arena.generator.ArenaStageGenerator
import jp.awabi2048.cccontent.features.arena.generator.ArenaThemeLoader
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.VoidChunkGenerator
import jp.awabi2048.cccontent.mob.MobDefinition
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.MobSpawnOptions
import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import jp.awabi2048.cccontent.world.WorldSettingsHelper
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.UUID
import kotlin.math.ceil

sealed class ArenaStartResult {
    data class Success(
        val themeId: String,
        val waves: Int,
        val mobTypeId: String,
        val difficultyId: String
    ) : ArenaStartResult()

    data class Error(
        val messageKey: String,
        val fallback: String,
        val placeholders: Array<out Pair<String, Any?>> = emptyArray()
    ) : ArenaStartResult()
}

private data class PendingWorldDeletion(
    val worldName: String,
    val folder: File,
    var attempts: Int = 0
)

private data class PendingBarrierActivation(
    val effectTask: BukkitTask,
    val completionTask: BukkitTask
)

private data class WaveRange(
    val minInclusive: Int,
    val maxInclusive: Int?
) {
    fun contains(wave: Int): Boolean {
        if (wave < minInclusive) return false
        return maxInclusive == null || wave <= maxInclusive
    }
}

private data class WeightedMobEntry(
    val mobId: String,
    val weight: Int,
    val range: WaveRange
)

private data class ArenaMobTypeConfig(
    val id: String,
    val structure: String,
    val iconMaterial: Material,
    val maxSummonCount: Int,
    val clearMobCount: Int,
    val weightedMobs: List<WeightedMobEntry>
) {
    fun candidatesForWave(wave: Int): List<WeightedMobEntry> {
        return weightedMobs.filter { it.range.contains(wave) }
    }
}

private data class ArenaDifficultyConfig(
    val id: String,
    val mobStatsMultiplier: Double,
    val mobSpawnIntervalTicks: Long,
    val mobCountMultiplier: Double,
    val waveMultiplier: Double,
    val waves: Int,
    val color: String,
    val display: String
) {
    fun waveScale(wave: Int): Double {
        return (1.0 + waveMultiplier * wave).coerceAtLeast(0.0)
    }
}

class ArenaManager(
    private val plugin: JavaPlugin,
    private val mobService: MobService = MobService(plugin)
) {
    private companion object {
        const val DOOR_ANIMATION_START_DELAY_TICKS = 40L
        const val DOOR_ANIMATION_FRAME_INTERVAL_TICKS = 20L
        const val POSITION_SAMPLE_INTERVAL_MILLIS = 1000L
        const val POSITION_RESTORE_LOOKBACK_MILLIS = 10_000L
        const val POSITION_HISTORY_RETENTION_MILLIS = 12_000L
        const val POSITION_HISTORY_MAX_SAMPLES = 24
        val DOOR_ANIMATION_ANGLES = intArrayOf(30, 60, 90)
    }

    private val random = kotlin.random.Random.Default
    private val themeLoader = ArenaThemeLoader(plugin)
    private val stageGenerator = ArenaStageGenerator()
    private val sessionsByWorld = mutableMapOf<String, ArenaSession>()
    private val playerToSessionWorld = mutableMapOf<UUID, String>()
    private val mobToSessionWorld = mutableMapOf<UUID, String>()
    private val pendingWorldDeletions = mutableMapOf<String, PendingWorldDeletion>()
    private val pendingBarrierActivations = mutableMapOf<UUID, PendingBarrierActivation>()
    private val difficultyConfigs = mutableMapOf<String, ArenaDifficultyConfig>()
    private val mobTypeConfigs = mutableMapOf<String, ArenaMobTypeConfig>()
    private val mobDefinitions = mutableMapOf<String, MobDefinition>()
    private var maintenanceTask: BukkitTask? = null
    private var playerMonitorTask: BukkitTask? = null

    fun initialize(featureInitLogger: FeatureInitializationLogger? = null) {
        loadBattleConfigs()
        themeLoader.load(featureInitLogger)
        startMaintenanceTask()
    }

    fun reloadThemes(featureInitLogger: FeatureInitializationLogger? = null) {
        loadBattleConfigs()
        themeLoader.load(featureInitLogger)
    }

    fun getMobTypeIds(): Set<String> = mobTypeConfigs.keys

    fun getDifficultyIds(): Set<String> = difficultyConfigs.keys

    private fun loadBattleConfigs() {
        val difficultyFile = ensureArenaConfig("config/arena/difficulty.yml")
        val mobTypeFile = ensureArenaConfig("config/arena/mob_type.yml")

        loadDifficultyConfigs(difficultyFile)
        loadMobDefinitions()
        loadMobTypeConfigs(mobTypeFile)
        validateWaveCoverage()
    }

    private fun ensureArenaConfig(resourcePath: String): File {
        val file = File(plugin.dataFolder, resourcePath)
        if (!file.exists()) {
            file.parentFile.mkdirs()
            plugin.saveResource(resourcePath, false)
        }
        return file
    }

    private fun loadDifficultyConfigs(file: File) {
        val config = YamlConfiguration.loadConfiguration(file)
        val loaded = mutableMapOf<String, ArenaDifficultyConfig>()

        for (difficultyId in config.getKeys(false)) {
            val section = config.getConfigurationSection(difficultyId)
            if (section == null) {
                plugin.logger.severe("[Arena] difficulty.yml の読み込み失敗: $difficultyId")
                continue
            }

            val mobStatsMultiplier = section.getDouble("mob_stats_multiplier", 1.0).coerceAtLeast(0.01)
            val mobSpawnIntervalTicks = section.getLong("mob_spawn_interval", 20L).coerceAtLeast(1L)
            val mobCountMultiplier = section.getDouble("mob_count_multiplier", 1.0).coerceAtLeast(0.01)
            val waveMultiplier = section.getDouble("wave_multiplier", 0.0)
            val waves = section.getInt("wave", 5).coerceAtLeast(1)
            val color = section.getString("color", "white") ?: "white"
            val display = section.getString("display", "") ?: ""

            loaded[difficultyId] = ArenaDifficultyConfig(
                id = difficultyId,
                mobStatsMultiplier = mobStatsMultiplier,
                mobSpawnIntervalTicks = mobSpawnIntervalTicks,
                mobCountMultiplier = mobCountMultiplier,
                waveMultiplier = waveMultiplier,
                waves = waves,
                color = color,
                display = display
            )
        }

        difficultyConfigs.clear()
        difficultyConfigs.putAll(loaded)

        if (difficultyConfigs.isEmpty()) {
            plugin.logger.severe("[Arena] difficulty.yml が空のためアリーナを開始できません")
        }
    }

    private fun loadMobDefinitions() {
        val loaded = mobService.reloadDefinitions()
        mobDefinitions.clear()
        mobDefinitions.putAll(loaded)

        if (mobDefinitions.isEmpty()) {
            plugin.logger.severe("[Arena] config/mob_definition.yml が空のためアリーナを開始できません")
        }
    }

    private fun loadMobTypeConfigs(file: File) {
        val config = YamlConfiguration.loadConfiguration(file)
        val loaded = mutableMapOf<String, ArenaMobTypeConfig>()

        for (mobTypeId in config.getKeys(false)) {
            val section = config.getConfigurationSection(mobTypeId)
            if (section == null) {
                plugin.logger.severe("[Arena] mob_type.yml の読み込み失敗: $mobTypeId")
                continue
            }

            val structure = section.getString("structure", "") ?: ""
            val iconMaterialName = section.getString("icon_material", Material.PAPER.name) ?: Material.PAPER.name
            val iconMaterial = try {
                Material.valueOf(iconMaterialName)
            } catch (_: IllegalArgumentException) {
                plugin.logger.severe("[Arena] icon_material が不正です: $mobTypeId material=$iconMaterialName")
                continue
            }

            val maxSummonCount = section.getInt("max_summon_count", 1).coerceAtLeast(1)
            val clearMobCount = section.getInt("clear_mob_count", 1).coerceAtLeast(1)

            val mobsSection = section.getConfigurationSection("mobs")
            if (mobsSection == null) {
                plugin.logger.severe("[Arena] mobs セクションがありません: $mobTypeId")
                continue
            }

            val weightedMobs = mutableListOf<WeightedMobEntry>()
            for (mobId in mobsSection.getKeys(false)) {
                if (!mobDefinitions.containsKey(mobId)) {
                    plugin.logger.severe("[Arena] mob_type が未定義mobを参照しています: mob_type=$mobTypeId mob=$mobId")
                    continue
                }
                val weight = mobsSection.getInt("$mobId.weight", 0)
                if (weight <= 0) {
                    plugin.logger.severe("[Arena] weight は1以上である必要があります: mob_type=$mobTypeId mob=$mobId")
                    continue
                }
                val waveText = mobsSection.getString("$mobId.wave", "1..") ?: "1.."
                val waveRange = parseWaveRange(waveText)
                if (waveRange == null) {
                    plugin.logger.severe("[Arena] wave 指定が不正です: mob_type=$mobTypeId mob=$mobId wave=$waveText")
                    continue
                }
                weightedMobs.add(WeightedMobEntry(mobId, weight, waveRange))
            }

            if (weightedMobs.isEmpty()) {
                plugin.logger.severe("[Arena] 有効な mobs 定義が1件もありません: $mobTypeId")
                continue
            }

            loaded[mobTypeId] = ArenaMobTypeConfig(
                id = mobTypeId,
                structure = structure,
                iconMaterial = iconMaterial,
                maxSummonCount = maxSummonCount,
                clearMobCount = clearMobCount,
                weightedMobs = weightedMobs
            )
        }

        mobTypeConfigs.clear()
        mobTypeConfigs.putAll(loaded)

        if (mobTypeConfigs.isEmpty()) {
            plugin.logger.severe("[Arena] mob_type.yml が空のためアリーナを開始できません")
        }
    }

    private fun validateWaveCoverage() {
        if (mobTypeConfigs.isEmpty() || difficultyConfigs.isEmpty()) return

        for ((mobTypeId, mobTypeConfig) in mobTypeConfigs) {
            for ((difficultyId, difficultyConfig) in difficultyConfigs) {
                for (wave in 1..difficultyConfig.waves) {
                    val candidates = mobTypeConfig.candidatesForWave(wave)
                    if (candidates.isEmpty()) {
                        plugin.logger.severe(
                            "[Arena] mobスポーン未定義ウェーブを検出: mob_type=$mobTypeId difficulty=$difficultyId wave=$wave"
                        )
                    }
                }
            }
        }
    }

    private fun parseWaveRange(text: String): WaveRange? {
        val exact = Regex("^(\\d+)$")
        exact.matchEntire(text.trim())?.let {
            val value = it.groupValues[1].toIntOrNull() ?: return null
            if (value <= 0) return null
            return WaveRange(value, value)
        }

        val range = Regex("^(\\d+)\\.\\.(\\d+)?$")
        val matched = range.matchEntire(text.trim()) ?: return null
        val min = matched.groupValues[1].toIntOrNull() ?: return null
        val max = matched.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull()
        if (min <= 0) return null
        if (max != null && max < min) return null
        return WaveRange(min, max)
    }

    fun startSession(
        target: Player,
        mobTypeId: String,
        difficultyId: String,
        requestedTheme: String?
    ): ArenaStartResult {
        if (playerToSessionWorld.containsKey(target.uniqueId)) {
            return ArenaStartResult.Error(
                "arena.messages.command.start_error.already_in_session",
                "&c{player} はすでにアリーナセッション中です",
                arrayOf("player" to target.name)
            )
        }

        val mobType = mobTypeConfigs[mobTypeId]
            ?: return ArenaStartResult.Error(
                "arena.messages.command.start_error.mob_type_not_found",
                "&cmob_type が見つかりません: {mob_type}",
                arrayOf("mob_type" to mobTypeId)
            )
        val difficulty = difficultyConfigs[difficultyId]
            ?: return ArenaStartResult.Error(
                "arena.messages.command.start_error.difficulty_not_found",
                "&cdifficulty が見つかりません: {difficulty}",
                arrayOf("difficulty" to difficultyId)
            )

        val undefinedWave = (1..difficulty.waves).firstOrNull { wave ->
            mobType.candidatesForWave(wave).isEmpty()
        }
        if (undefinedWave != null) {
            plugin.logger.severe(
                "[Arena] セッション開始失敗: mobスポーン未定義ウェーブがあります " +
                    "mob_type=$mobTypeId difficulty=$difficultyId wave=$undefinedWave"
            )
            return ArenaStartResult.Error(
                "arena.messages.command.start_error.undefined_wave_spawn",
                "&cmob_type '{mob_type}' は wave {wave} のスポーンが未定義です",
                arrayOf("mob_type" to mobTypeId, "wave" to undefinedWave)
            )
        }

        val theme = if (requestedTheme.isNullOrBlank()) {
            themeLoader.getRandomTheme(random)
        } else {
            themeLoader.getTheme(requestedTheme)
        } ?: return ArenaStartResult.Error(
            "arena.messages.command.start_error.theme_not_found",
            "&c有効なテーマが見つかりません"
        )

        val world = createArenaWorld() ?: return ArenaStartResult.Error(
            "arena.messages.command.start_error.world_create_failed",
            "&cアリーナ用ワールド作成に失敗しました"
        )

        return try {
            val returnLocation = target.location.clone()
            val origin = Location(world, 0.0, 64.0, 0.0)
            val stage = stageGenerator.build(world, origin, theme, difficulty.waves, random)
            val session = ArenaSession(
                ownerPlayerId = target.uniqueId,
                worldName = world.name,
                themeId = theme.id,
                mobTypeId = mobType.id,
                difficultyId = difficulty.id,
                waves = difficulty.waves,
                participants = mutableSetOf(target.uniqueId),
                returnLocations = mutableMapOf(target.uniqueId to returnLocation),
                playerSpawn = stage.playerSpawn,
                entranceLocation = stage.entranceLocation,
                stageBounds = stage.stageBounds,
                roomBounds = stage.roomBounds,
                corridorBounds = stage.corridorBounds,
                roomMobSpawns = stage.roomMobSpawns,
                corridorDoorBlocks = stage.corridorDoorBlocks,
                barrierLocation = stage.barrierLocation
            )
            sessionsByWorld[world.name] = session
            playerToSessionWorld[target.uniqueId] = world.name
            val now = System.currentTimeMillis()
            session.participantLocationHistory[target.uniqueId] = ArrayDeque<TimedPlayerLocation>().apply {
                addLast(TimedPlayerLocation(now, stage.entranceLocation.clone()))
            }
            session.participantLastSampleMillis[target.uniqueId] = now

            session.participants.forEach { participantId ->
                val participant = Bukkit.getPlayer(participantId) ?: return@forEach
                if (!participant.isOnline) return@forEach
                participant.teleport(stage.entranceLocation)
            }
            target.sendMessage(
                ArenaI18n.text(
                    target,
                    "arena.messages.session.started",
                    "&6[Arena] セッション開始: theme={theme}, mob_type={mob_type}, difficulty={difficulty}, waves={waves}",
                    "theme" to theme.id,
                    "mob_type" to mobType.id,
                    "difficulty" to difficulty.id,
                    "waves" to difficulty.waves
                )
            )

            initializeWavePipeline(session, mobType, difficulty)
            ArenaStartResult.Success(theme.id, difficulty.waves, mobType.id, difficulty.id)
        } catch (e: Exception) {
            tryDeleteWorld(world)
            ArenaStartResult.Error(
                "arena.messages.command.start_error.stage_build_failed",
                "&cステージ生成に失敗しました: {message}",
                arrayOf("message" to (e.message ?: "unknown"))
            )
        }
    }

    fun stopSession(player: Player, reason: String = ArenaI18n.text(player, "arena.messages.session.ended", "&cアリーナセッションが終了しました")): Boolean {
        return leavePlayerFromSession(player.uniqueId, reason)
    }

    fun stopSessionById(playerId: UUID, reason: String? = null): Boolean {
        val player = Bukkit.getPlayer(playerId)
        val localizedReason = reason ?: ArenaI18n.text(player, "arena.messages.session.ended", "&cアリーナセッションが終了しました")
        return leavePlayerFromSession(playerId, localizedReason)
    }

    fun shutdown() {
        maintenanceTask?.cancel()
        maintenanceTask = null
        playerMonitorTask?.cancel()
        playerMonitorTask = null

        sessionsByWorld.values.toList().forEach { session ->
            terminateSession(
                session,
                false,
                messageKey = "arena.messages.session.ended_by_shutdown",
                fallbackMessage = "&cサーバー停止によりアリーナを終了しました"
            )
        }
        sessionsByWorld.clear()
        playerToSessionWorld.clear()
        mobToSessionWorld.clear()
        pendingBarrierActivations.values.toList().forEach { pending ->
            pending.effectTask.cancel()
            pending.completionTask.cancel()
        }
        pendingBarrierActivations.clear()
        processPendingWorldDeletions()
    }

    fun getSession(player: Player): ArenaSession? {
        val worldName = playerToSessionWorld[player.uniqueId] ?: return null
        return sessionsByWorld[worldName]
    }

    fun getThemeIds(): Set<String> = themeLoader.getThemeIds()

    fun getActiveSessionPlayerNames(): Set<String> {
        return playerToSessionWorld.keys.mapNotNull { uuid -> Bukkit.getPlayer(uuid)?.name }.toSet()
    }

    private fun monitorParticipantPositions() {
        for (session in sessionsByWorld.values.toList()) {
            val world = Bukkit.getWorld(session.worldName) ?: continue

            for (participantId in session.participants.toList()) {
                if (pendingBarrierActivations.containsKey(participantId)) continue

                val player = Bukkit.getPlayer(participantId)
                if (player == null || !player.isOnline) continue

                val location = player.location
                val currentWave = session.currentWave.coerceAtLeast(1)
                if (location.world?.name != world.name || !session.stageBounds.contains(location.x, location.y, location.z)) {
                    if (location.world?.name != world.name) {
                        stopSessionById(
                            participantId,
                            ArenaI18n.text(player, "arena.messages.session.ended_by_warp", "&cワープしたため死亡扱いでアリーナを終了しました")
                        )
                    } else {
                        teleportToRecentValidPosition(player, session, world, currentWave, applyBlindness = true)
                    }
                    continue
                }

                val room = locateRoom(session, location)
                if (room != null && room > currentWave) {
                    teleportToCurrentWavePosition(player, session, world, applyBlindness = true)
                    continue
                }
                if (room != null && room > 0) {
                    processRoomProgress(session, player, room)
                }

                val corridorWave = locateCorridorTargetWave(session, location)
                if (corridorWave != null) {
                    if (!session.openedCorridors.contains(corridorWave)) {
                        teleportToCurrentWavePosition(player, session, world, applyBlindness = true)
                        continue
                    }
                    if (corridorWave == currentWave) {
                        if (session.startedWaves.contains(corridorWave) &&
                            (corridorWave <= 1 || session.clearedWaves.contains(corridorWave - 1)) &&
                            session.corridorTriggeredWaves.add(corridorWave)
                        ) {
                            rebalanceTargetsForWave(session, corridorWave)
                        }
                    }
                }

                recordParticipantValidLocation(session, participantId, location)
            }
        }
    }

    fun handleMobDeath(entityId: UUID) {
        val worldName = mobToSessionWorld.remove(entityId) ?: return
        val session = sessionsByWorld[worldName] ?: return
        consumeMob(session, entityId, countKill = true)
    }

    fun handleBarrierClick(player: Player, clicked: Location): Boolean {
        val session = getSession(player) ?: return false
        if (player.world.name != session.worldName) return false

        val block = clicked.block
        val core = session.barrierLocation.block
        if (block.x != core.x || block.y != core.y || block.z != core.z) return false

        if (!session.barrierActive) {
            player.sendMessage(
                ArenaI18n.text(
                    player,
                    "arena.messages.barrier.not_ready",
                    "&cまだ結界石を再起動できません。全ウェーブをクリアしてください"
                )
            )
            return true
        }

        if (pendingBarrierActivations.containsKey(player.uniqueId)) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.barrier.activating", "&e結界石を再起動中です"))
            return true
        }

        startBarrierActivation(player, session)
        return true
    }

    fun handleDoorClick(player: Player, clicked: Location): Boolean {
        val session = getSession(player) ?: return false
        if (player.world.name != session.worldName) return false

        val wave = locateDoorWave(session, clicked) ?: return false
        if (wave != 1) return true
        if (session.stageStarted) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.stage.already_started", "&eステージはすでに開始しています"))
            return true
        }
        if (session.animatingDoorWaves.contains(wave)) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.door.animating", "&e開扉中です"))
            return true
        }

        startDoorAnimation(session, wave)
        return true
    }

    fun isBarrierBlock(location: Location): Boolean {
        return sessionsByWorld.values.any { session ->
            val block = session.barrierLocation.block
            block.world.name == location.world?.name &&
                block.x == location.blockX &&
                block.y == location.blockY &&
                block.z == location.blockZ
        }
    }

    fun isDoorBlock(location: Location): Boolean {
        return sessionsByWorld.values.any { session -> locateDoorWave(session, location) != null }
    }

    private fun leavePlayerFromSession(playerId: UUID, reason: String): Boolean {
        val worldName = playerToSessionWorld[playerId] ?: return false
        val session = sessionsByWorld[worldName] ?: return false

        cancelBarrierActivation(playerId)

        playerToSessionWorld.remove(playerId)
        session.participants.remove(playerId)
        val returnLocation = session.returnLocations.remove(playerId)
        session.playerNotifiedWaves.remove(playerId)
        session.participantLocationHistory.remove(playerId)
        session.participantLastSampleMillis.remove(playerId)

        val player = Bukkit.getPlayer(playerId)
        if (player != null && player.isOnline) {
            val fallback = Bukkit.getWorlds().firstOrNull()?.spawnLocation
            val destination = if (returnLocation?.world != null) returnLocation else fallback
            if (destination != null) {
                player.teleport(destination)
            }
            player.sendMessage(reason)
        }

        if (session.participants.isEmpty()) {
            terminateSession(session, false)
        }
        return true
    }

    private fun terminateSession(
        session: ArenaSession,
        success: Boolean,
        messageKey: String? = null,
        fallbackMessage: String? = null,
        vararg messagePlaceholders: Pair<String, Any?>
    ) {
        sessionsByWorld.remove(session.worldName)

        session.waveSpawnTasks.values.forEach { it.cancel() }
        session.waveSpawnTasks.clear()
        session.transitionTasks.forEach { it.cancel() }
        session.transitionTasks.clear()

        session.participants.toList().forEach { participantId ->
            cancelBarrierActivation(participantId)
            playerToSessionWorld.remove(participantId)
            val player = Bukkit.getPlayer(participantId)
            if (player != null && player.isOnline) {
                val fallback = Bukkit.getWorlds().firstOrNull()?.spawnLocation
                val destination = if (session.returnLocations[participantId]?.world != null) {
                    session.returnLocations[participantId]
                } else {
                    fallback
                }
                if (destination != null) {
                    player.teleport(destination)
                }
                if (messageKey != null && fallbackMessage != null) {
                    player.sendMessage(ArenaI18n.text(player, messageKey, fallbackMessage, *messagePlaceholders))
                    if (success) {
                        player.sendMessage(
                            ArenaI18n.text(
                                player,
                                "arena.messages.session.retry_hint",
                                "&7管理コマンドで mob_type / difficulty / theme を指定して再挑戦できます"
                            )
                        )
                    }
                }
            }
        }

        session.participants.clear()
        session.returnLocations.clear()
        session.playerNotifiedWaves.clear()
        session.participantLocationHistory.clear()
        session.participantLastSampleMillis.clear()
        session.corridorTriggeredWaves.clear()
        session.openedCorridors.clear()
        session.corridorOpenAnnouncements.clear()
        session.enteredWaves.clear()
        session.waveSpawningStopped.clear()
        session.animatingDoorWaves.clear()
        session.stageStarted = false

        session.activeMobs.toList().forEach { mobId ->
            mobToSessionWorld.remove(mobId)
            session.mobWaveMap.remove(mobId)
            mobService.untrack(mobId)
            Bukkit.getEntity(mobId)?.remove()
            session.activeMobs.remove(mobId)
        }
        session.waveMobIds.clear()

        val world = Bukkit.getWorld(session.worldName)
        if (world != null) {
            tryDeleteWorld(world)
        } else {
            queueWorldDeletion(session.worldName, File(Bukkit.getWorldContainer(), session.worldName))
        }
    }

    private fun initializeWavePipeline(
        session: ArenaSession,
        mobType: ArenaMobTypeConfig,
        difficulty: ArenaDifficultyConfig
    ) {
        startWave(session, 1, mobType, difficulty)
        if (session.waves >= 2) {
            startWave(session, 2, mobType, difficulty)
        }
        updateCurrentWave(session)
    }

    private fun startWave(
        session: ArenaSession,
        wave: Int,
        mobType: ArenaMobTypeConfig,
        difficulty: ArenaDifficultyConfig
    ) {
        if (wave <= 0 || wave > session.waves) return
        if (session.startedWaves.contains(wave)) return
        if (session.clearedWaves.contains(wave)) return

        val spawns = session.roomMobSpawns[wave].orEmpty()
        val candidates = mobType.candidatesForWave(wave)
        if (candidates.isEmpty()) {
            plugin.logger.severe(
                "[Arena] 開始不能ウェーブを検出: world=${session.worldName} mob_type=${mobType.id} wave=$wave"
            )
            session.startedWaves.add(wave)
            clearWave(session, wave, mobType, difficulty)
            return
        }

        if (spawns.isEmpty()) {
            plugin.logger.severe(
                "[Arena] wave=$wave の mob スポーン位置が0件のため自動クリアします。" +
                    " 対象部屋に 'arena.marker.mob' を1個以上配置してください: world=${session.worldName}"
            )
            session.startedWaves.add(wave)
            clearWave(session, wave, mobType, difficulty)
            return
        }

        val maxAlive = calculateWaveCount(mobType.maxSummonCount, difficulty, wave)
        val clearTarget = calculateWaveCount(mobType.clearMobCount, difficulty, wave)

        session.startedWaves.add(wave)
        session.waveKillCount.putIfAbsent(wave, 0)
        session.waveClearTargets[wave] = clearTarget
        session.waveMaxAliveCounts[wave] = maxAlive
        session.waveMobIds.putIfAbsent(wave, mutableSetOf())

        startSpawnLoop(session, wave, mobType, difficulty, spawns)
        updateCurrentWave(session)
    }

    private fun calculateWaveCount(base: Int, difficulty: ArenaDifficultyConfig, wave: Int): Int {
        val scaled = base * difficulty.mobCountMultiplier * difficulty.waveScale(wave)
        return ceil(scaled).toInt().coerceAtLeast(1)
    }

    private fun startSpawnLoop(
        session: ArenaSession,
        wave: Int,
        mobType: ArenaMobTypeConfig,
        difficulty: ArenaDifficultyConfig,
        spawns: List<Location>
    ) {
        val interval = difficulty.mobSpawnIntervalTicks.coerceAtLeast(1L)
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val currentSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (!currentSession.startedWaves.contains(wave)) return@Runnable
            if (currentSession.waveSpawningStopped.contains(wave)) return@Runnable

            val maxAlive = currentSession.waveMaxAliveCounts[wave] ?: return@Runnable
            val aliveCount = currentSession.waveMobIds[wave]?.size ?: 0
            if (aliveCount >= maxAlive) return@Runnable

            val world = Bukkit.getWorld(currentSession.worldName) ?: return@Runnable
            val spawnPoint = selectSpawnPoint(currentSession, spawns) ?: return@Runnable
            val weightedMob = selectWeightedMob(mobType.candidatesForWave(wave)) ?: return@Runnable
            val definition = mobDefinitions[weightedMob.mobId] ?: return@Runnable

            spawnMob(world, currentSession, wave, spawnPoint, definition, difficulty)
        }, interval, interval)

        session.waveSpawnTasks[wave]?.cancel()
        session.waveSpawnTasks[wave] = task
    }

    private fun locateRoom(session: ArenaSession, location: Location): Int? {
        return session.roomBounds.entries.firstOrNull { (_, bounds) ->
            bounds.contains(location.x, location.y, location.z)
        }?.key
    }

    private fun locateCorridorTargetWave(session: ArenaSession, location: Location): Int? {
        return session.corridorBounds.entries.firstOrNull { (_, bounds) ->
            bounds.contains(location.x, location.y, location.z)
        }?.key
    }

    private fun teleportToCurrentWavePosition(player: Player, session: ArenaSession, world: World, applyBlindness: Boolean = false) {
        val target = currentWavePosition(session, world)
        target.yaw = player.location.yaw
        target.pitch = player.location.pitch
        player.teleport(target)
        if (applyBlindness) {
            player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 15, 0, false, false, false))
        }
    }

    private fun teleportToRecentValidPosition(
        player: Player,
        session: ArenaSession,
        world: World,
        currentWave: Int,
        applyBlindness: Boolean = false
    ) {
        val target = selectRecentValidPosition(session, player.uniqueId, world, currentWave)
            ?: currentWavePosition(session, world)
        target.yaw = player.location.yaw
        target.pitch = player.location.pitch
        player.teleport(target)
        if (applyBlindness) {
            player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 15, 0, false, false, false))
        }
    }

    private fun selectRecentValidPosition(session: ArenaSession, playerId: UUID, world: World, currentWave: Int): Location? {
        val history = session.participantLocationHistory[playerId] ?: return null
        if (history.isEmpty()) return null

        val now = System.currentTimeMillis()
        val targetMillis = now - POSITION_RESTORE_LOOKBACK_MILLIS
        var best: TimedPlayerLocation? = null
        var bestDistance = Long.MAX_VALUE

        for (sample in history) {
            if (!isValidPositionForRestore(session, sample.location, world, currentWave)) {
                continue
            }
            val distance = kotlin.math.abs(sample.timestampMillis - targetMillis)
            if (distance < bestDistance) {
                best = sample
                bestDistance = distance
            }
        }

        return best?.location?.clone()?.apply { this.world = world }
    }

    private fun isValidPositionForRestore(session: ArenaSession, location: Location, world: World, currentWave: Int): Boolean {
        if (location.world?.name != world.name) return false
        if (!session.stageBounds.contains(location.x, location.y, location.z)) return false

        val room = locateRoom(session, location)
        if (room != null && room > currentWave) return false

        val corridorWave = locateCorridorTargetWave(session, location) ?: return true
        return session.openedCorridors.contains(corridorWave)
    }

    private fun recordParticipantValidLocation(session: ArenaSession, participantId: UUID, location: Location) {
        val now = System.currentTimeMillis()
        val lastSampleMillis = session.participantLastSampleMillis[participantId] ?: 0L
        if (now - lastSampleMillis < POSITION_SAMPLE_INTERVAL_MILLIS) {
            return
        }

        val history = session.participantLocationHistory.getOrPut(participantId) { ArrayDeque() }
        history.addLast(TimedPlayerLocation(now, location.clone()))
        session.participantLastSampleMillis[participantId] = now

        while (history.size > POSITION_HISTORY_MAX_SAMPLES) {
            history.removeFirstOrNull()
        }
        while (history.firstOrNull()?.timestampMillis?.let { now - it > POSITION_HISTORY_RETENTION_MILLIS } == true) {
            history.removeFirstOrNull()
        }
    }

    private fun currentWavePosition(session: ArenaSession, world: World): Location {
        if (!session.stageStarted) {
            return session.entranceLocation.clone().apply {
                this.world = world
            }
        }

        val wave = session.fallbackWave.coerceIn(1, session.waves)
        val bounds = session.roomBounds[wave]
        if (bounds == null) {
            return session.playerSpawn.clone().apply {
                this.world = world
            }
        }

        val centerX = (bounds.minX + bounds.maxX + 1) / 2.0
        val centerY = bounds.minY + 1.0
        val centerZ = (bounds.minZ + bounds.maxZ + 1) / 2.0
        return Location(world, centerX, centerY, centerZ)
    }

    private fun notifyWaveEntryIfNeeded(session: ArenaSession, player: Player, wave: Int) {
        if (session.clearedWaves.contains(wave)) return

        val notified = session.playerNotifiedWaves.getOrPut(player.uniqueId) { mutableSetOf() }
        if (!notified.add(wave)) return

        player.sendTitle(
            "",
            ArenaI18n.text(player, "arena.messages.wave.title", "&6Wave {wave}", "wave" to wave),
            10,
            50,
            10
        )
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f)
    }

    private fun processRoomProgress(session: ArenaSession, player: Player, room: Int) {
        notifyWaveEntryIfNeeded(session, player, room)
        handleWaveRoomEntry(session, room)
    }

    private fun handleWaveRoomEntry(session: ArenaSession, wave: Int) {
        if (!session.enteredWaves.add(wave)) return

        session.fallbackWave = wave.coerceIn(1, session.waves)

        val previousWave = wave - 1
        if (previousWave <= 0) return

        stopWaveSpawning(session, previousWave)
        removeWaveMobs(session, previousWave)
    }

    private fun stopWaveSpawning(session: ArenaSession, wave: Int) {
        if (wave <= 0) return
        session.waveSpawningStopped.add(wave)
        session.waveSpawnTasks.remove(wave)?.cancel()
    }

    private fun selectSpawnPoint(session: ArenaSession, markers: List<Location>): Location? {
        if (markers.isEmpty()) return null
        val onlineParticipants = session.participants.mapNotNull { participantId ->
            val player = Bukkit.getPlayer(participantId)
            if (player == null || !player.isOnline || player.world.name != session.worldName) null else player
        }

        if (onlineParticipants.isEmpty()) {
            return markers[random.nextInt(markers.size)]
        }

        var farthestNearestDistance = -1.0
        val candidates = mutableListOf<Location>()

        for (marker in markers) {
            val nearestDistance = onlineParticipants.minOf { player ->
                marker.distanceSquared(player.location)
            }

            if (nearestDistance > farthestNearestDistance + 1.0e-6) {
                farthestNearestDistance = nearestDistance
                candidates.clear()
                candidates.add(marker)
            } else if (kotlin.math.abs(nearestDistance - farthestNearestDistance) <= 1.0e-6) {
                candidates.add(marker)
            }
        }

        if (candidates.isEmpty()) {
            return markers[random.nextInt(markers.size)]
        }
        return candidates[random.nextInt(candidates.size)]
    }

    private fun selectWeightedMob(candidates: List<WeightedMobEntry>): WeightedMobEntry? {
        if (candidates.isEmpty()) return null
        val totalWeight = candidates.sumOf { it.weight.coerceAtLeast(0) }
        if (totalWeight <= 0) return null

        var roll = random.nextInt(totalWeight)
        for (candidate in candidates) {
            roll -= candidate.weight
            if (roll < 0) {
                return candidate
            }
        }
        return candidates.last()
    }

    private fun spawnMob(
        world: World,
        session: ArenaSession,
        wave: Int,
        spawn: Location,
        definition: MobDefinition,
        difficulty: ArenaDifficultyConfig
    ) {
        val entity = mobService.spawn(
            definition,
            spawn,
            MobSpawnOptions(
                featureId = "arena",
                combatActiveProvider = { session.corridorTriggeredWaves.contains(wave) },
                metadata = mapOf("world" to session.worldName, "wave" to wave.toString())
            )
        ) ?: return
        entity.removeWhenFarAway = false
        entity.canPickupItems = false

        applyMobStats(entity, definition, difficulty, wave)

        val combatActivated = session.corridorTriggeredWaves.contains(wave)
        if (!combatActivated) {
            entity.setAI(false)
        } else {
            entity.setAI(true)
            if (entity is Mob) {
                entity.target = findNearestParticipant(session, entity.location)
            }
        }

        val entityId = entity.uniqueId
        session.activeMobs.add(entityId)
        session.waveMobIds.getOrPut(wave) { mutableSetOf() }.add(entityId)
        session.mobWaveMap[entityId] = wave
        mobToSessionWorld[entityId] = session.worldName
    }

    private fun applyMobStats(
        entity: LivingEntity,
        definition: MobDefinition,
        difficulty: ArenaDifficultyConfig,
        wave: Int
    ) {
        val statScale = (difficulty.mobStatsMultiplier * difficulty.waveScale(wave)).coerceAtLeast(0.01)

        val maxHealth = (definition.health * statScale).coerceAtLeast(1.0)
        val attack = (definition.attack * statScale).coerceAtLeast(0.0)
        val speed = (definition.movementSpeed * statScale).coerceAtLeast(0.01)
        val armor = (definition.armor * statScale).coerceAtLeast(0.0)

        entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = maxHealth
        entity.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = attack
        entity.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = speed
        entity.getAttribute(Attribute.ARMOR)?.baseValue = armor
        entity.getAttribute(Attribute.SCALE)?.baseValue = definition.scale
        entity.health = maxHealth
    }

    private fun consumeMob(session: ArenaSession, mobId: UUID, countKill: Boolean) {
        val removed = session.activeMobs.remove(mobId)
        if (!removed) return

        mobToSessionWorld.remove(mobId)
        val wave = session.mobWaveMap.remove(mobId)
        if (wave != null) {
            session.waveMobIds[wave]?.remove(mobId)
        }

        if (!countKill || wave == null) {
            return
        }

        session.totalKillCount += 1

        if (session.clearedWaves.contains(wave)) {
            return
        }

        val kills = (session.waveKillCount[wave] ?: 0) + 1
        session.waveKillCount[wave] = kills

        val target = session.waveClearTargets[wave] ?: return
        if (kills >= target) {
            val mobType = mobTypeConfigs[session.mobTypeId] ?: return
            val difficulty = difficultyConfigs[session.difficultyId] ?: return
            clearWave(session, wave, mobType, difficulty)
        }
    }

    private fun clearWave(
        session: ArenaSession,
        wave: Int,
        mobType: ArenaMobTypeConfig,
        difficulty: ArenaDifficultyConfig
    ) {
        if (session.clearedWaves.contains(wave)) return

        session.clearedWaves.add(wave)
        stopWaveSpawning(session, wave)

        val nextWave = wave + 2
        if (nextWave <= session.waves) {
            startWave(session, nextWave, mobType, difficulty)
        }

        if (session.clearedWaves.size >= session.waves) {
            onAllWavesCleared(session)
            return
        }

        startDoorAnimation(session, wave + 1)
    }

    private fun startDoorAnimation(session: ArenaSession, targetWave: Int) {
        if (targetWave <= 0 || targetWave > session.waves) return
        if (!session.animatingDoorWaves.add(targetWave)) return

        val delayedTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable delayedStart@{
            val activeSession = sessionsByWorld[session.worldName] ?: return@delayedStart
            if (!activeSession.animatingDoorWaves.contains(targetWave)) return@delayedStart

            onDoorAnimationStarted(activeSession, targetWave)

            var frameIndex = 0
            val taskRef = arrayOfNulls<BukkitTask>(1)
            val animationTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable animationTick@{
                val currentSession = sessionsByWorld[session.worldName] ?: run {
                    taskRef[0]?.cancel()
                    return@animationTick
                }

                if (frameIndex >= DOOR_ANIMATION_ANGLES.size) {
                    currentSession.animatingDoorWaves.remove(targetWave)
                    updateCurrentWave(currentSession)
                    taskRef[0]?.cancel()
                    return@animationTick
                }

                val angle = DOOR_ANIMATION_ANGLES[frameIndex]
                reinstallCorridorDoorMock(currentSession, targetWave, angle)
                playSound(currentSession, Sound.BLOCK_IRON_DOOR_OPEN, 0.85f, 0.9f + frameIndex * 0.1f)
                frameIndex += 1
            }, 0L, DOOR_ANIMATION_FRAME_INTERVAL_TICKS)
            taskRef[0] = animationTask
            activeSession.transitionTasks.add(animationTask)
        }, DOOR_ANIMATION_START_DELAY_TICKS)

        session.transitionTasks.add(delayedTask)
    }

    private fun onDoorAnimationStarted(session: ArenaSession, targetWave: Int) {
        session.openedCorridors.add(targetWave)
        onCorridorOpened(session, targetWave)
        if (targetWave == 1 && !session.stageStarted) {
            session.stageStarted = true
            broadcastSubtitle(session, "arena.messages.stage.start_subtitle", "&6ステージ開始", 5, 40, 10)
            playSound(session, Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.0f)
        }
        playSound(session, Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 0.8f)
        plugin.logger.info("[Arena] door animation start: world=${session.worldName} target_wave=$targetWave")
    }

    private fun reinstallCorridorDoorMock(session: ArenaSession, wave: Int, angle: Int) {
        plugin.logger.info("[Arena][Mock] corridor door reinstall: world=${session.worldName} wave=$wave angle=$angle")
    }

    private fun onCorridorOpened(session: ArenaSession, wave: Int) {
        if (!session.corridorOpenAnnouncements.add(wave)) return
        broadcastSubtitle(session, "arena.messages.door.corridor_opened_subtitle", "&a次の通路が開きました", 5, 35, 10)
        broadcastMessage(session, "arena.messages.door.corridor_opened", "&aWave {wave} への通路が解放されました", "wave" to wave)
        playSound(session, Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.2f)
    }

    private fun locateDoorWave(session: ArenaSession, clicked: Location): Int? {
        return session.corridorDoorBlocks.entries.firstOrNull { (_, markers) ->
            markers.any { marker ->
                marker.world?.name == clicked.world?.name &&
                    marker.blockX == clicked.blockX &&
                    marker.blockY == clicked.blockY &&
                    marker.blockZ == clicked.blockZ
            }
        }?.key
    }

    private fun rebalanceTargetsForWave(session: ArenaSession, wave: Int) {
        if (wave <= 0 || wave > session.waves) return

        val targets = session.participants
            .mapNotNull { participantId -> Bukkit.getPlayer(participantId) }
            .filter { it.isOnline && it.world.name == session.worldName }
            .shuffled(random)

        val mobs = session.waveMobIds[wave]
            .orEmpty()
            .mapNotNull { mobId -> Bukkit.getEntity(mobId) as? Mob }
            .filter { !it.isDead && it.isValid && it.world.name == session.worldName }
            .shuffled(random)

        if (mobs.isEmpty()) return
        if (targets.isEmpty()) {
            mobs.forEach { mob ->
                mob.setAI(false)
                mob.target = null
            }
            return
        }

        mobs.forEachIndexed { index, mob ->
            mob.setAI(true)
            val target = targets[index % targets.size]
            mob.target = target
        }
    }

    private fun findNearestParticipant(session: ArenaSession, location: Location): Player? {
        return session.participants
            .mapNotNull { participantId -> Bukkit.getPlayer(participantId) }
            .filter { it.isOnline && it.world.name == session.worldName }
            .minByOrNull { it.location.distanceSquared(location) }
    }

    private fun removeWaveMobs(session: ArenaSession, wave: Int) {
        val ids = session.waveMobIds.remove(wave)?.toList().orEmpty()
        for (mobId in ids) {
            session.activeMobs.remove(mobId)
            session.mobWaveMap.remove(mobId)
            mobToSessionWorld.remove(mobId)
            mobService.untrack(mobId)
            Bukkit.getEntity(mobId)?.remove()
        }
    }

    private fun updateCurrentWave(session: ArenaSession) {
        val next = (1..session.waves).firstOrNull { !session.clearedWaves.contains(it) } ?: session.waves
        session.currentWave = next
    }

    private fun onAllWavesCleared(session: ArenaSession) {
        if (session.barrierActive) return
        session.waveSpawnTasks.values.forEach { it.cancel() }
        session.waveSpawnTasks.clear()
        session.barrierActive = true
        broadcastSubtitle(session, "arena.messages.barrier.ready_subtitle", "&b結界石を右クリックして再起動", 10, 70, 20)
    }

    private fun startBarrierActivation(player: Player, session: ArenaSession) {
        val effectTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline || player.world.name != session.worldName) return@Runnable
            val center = session.barrierLocation.clone().add(0.5, 0.8, 0.5)
            player.world.spawnParticle(Particle.END_ROD, center, 24, 0.35, 0.45, 0.35, 0.01)
            player.sendTitle("", ArenaI18n.text(player, "arena.messages.barrier.activating_title", "&b結界石を再起動中..."), 0, 15, 5)
        }, 0L, 10L)

        val completionTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val pending = pendingBarrierActivations.remove(player.uniqueId) ?: return@Runnable
            pending.effectTask.cancel()

            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (!activeSession.participants.contains(player.uniqueId)) return@Runnable
            if (!player.isOnline || player.world.name != session.worldName) return@Runnable

            player.sendTitle("", ArenaI18n.text(player, "arena.messages.barrier.activation_complete_title", "&a再起動完了"), 5, 30, 10)
            terminateSession(
                activeSession,
                true,
                messageKey = "arena.messages.session.cleared",
                fallbackMessage = "&aアリーナクリア！"
            )
        }, 200L)

        pendingBarrierActivations[player.uniqueId] = PendingBarrierActivation(
            effectTask = effectTask,
            completionTask = completionTask
        )
    }

    private fun cancelBarrierActivation(playerId: UUID) {
        val pending = pendingBarrierActivations.remove(playerId) ?: return
        pending.effectTask.cancel()
        pending.completionTask.cancel()
    }

    private fun broadcastSubtitle(
        session: ArenaSession,
        key: String,
        fallback: String,
        fadeIn: Int,
        stay: Int,
        fadeOut: Int,
        vararg placeholders: Pair<String, Any?>
    ) {
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            player.sendTitle("", ArenaI18n.text(player, key, fallback, *placeholders), fadeIn, stay, fadeOut)
        }
    }

    private fun broadcastMessage(
        session: ArenaSession,
        key: String,
        fallback: String,
        vararg placeholders: Pair<String, Any?>
    ) {
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            player.sendMessage(ArenaI18n.text(player, key, fallback, *placeholders))
        }
    }

    private fun playSound(session: ArenaSession, sound: Sound, volume: Float, pitch: Float) {
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            player.playSound(player.location, sound, volume, pitch)
        }
    }

    private fun createArenaWorld(): World? {
        val worldName = "arena.${UUID.randomUUID()}"
        val creator = WorldCreator(worldName)
        creator.generator(VoidChunkGenerator())
        val world = creator.createWorld()
        world?.apply {
            setGameRule(GameRule.DO_MOB_SPAWNING, false)
            setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
            setGameRule(GameRule.DO_WEATHER_CYCLE, false)
            setGameRule(GameRule.KEEP_INVENTORY, true)
            time = 6000
            WorldSettingsHelper.applyDistanceSettings(plugin, this, "arena.world_settings")
        }
        return world
    }

    private fun tryDeleteWorld(world: World) {
        if (!world.name.startsWith("arena.")) return

        val spawn = Bukkit.getWorlds().firstOrNull()?.spawnLocation
        world.players.toList().forEach { player ->
            if (spawn != null) {
                player.teleport(spawn)
            }
        }

        val unloaded = Bukkit.unloadWorld(world, false)
        if (!unloaded) {
            queueWorldDeletion(world.name, world.worldFolder)
            return
        }

        queueWorldDeletion(world.name, world.worldFolder)
    }

    private fun deleteQueuedWorldFolder(pending: PendingWorldDeletion): Boolean {
        if (Bukkit.getWorld(pending.worldName) != null) {
            return false
        }

        if (!pending.folder.exists()) {
            return true
        }

        return deleteDirectory(pending.folder)
    }

    private fun unloadQueuedWorldIfLoaded(pending: PendingWorldDeletion): Boolean {
        val loaded = Bukkit.getWorld(pending.worldName) ?: return true
        return Bukkit.unloadWorld(loaded, false)
    }

    private fun markQueuedWorldDeletionFailed(pending: PendingWorldDeletion, maxAttempts: Int) {
        pending.attempts += 1
        if (pending.attempts >= maxAttempts) {
            plugin.logger.severe("[Arena] ワールド削除を断念: ${pending.worldName} path=${pending.folder.absolutePath}")
            pendingWorldDeletions.remove(pending.worldName)
        }
    }

    private fun queueWorldDeletion(worldName: String, folder: File) {
        pendingWorldDeletions.putIfAbsent(worldName, PendingWorldDeletion(worldName, folder))
    }

    private fun startMaintenanceTask() {
        if (maintenanceTask != null) return
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            reconcileActiveMobs()
            processPendingWorldDeletions()
        }, 20L, 20L)

        if (playerMonitorTask == null) {
            playerMonitorTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                monitorParticipantPositions()
            }, 5L, 5L)
        }
    }

    private fun reconcileActiveMobs() {
        sessionsByWorld.values.toList().forEach { session ->
            session.activeMobs.toList().forEach { mobId ->
                val entity = Bukkit.getEntity(mobId)
                val invalid = entity == null || entity.isDead || !entity.isValid || entity.type == EntityType.PLAYER || entity.world.name != session.worldName
                if (invalid) {
                    consumeMob(session, mobId, countKill = true)
                }
            }
        }
    }

    private fun processPendingWorldDeletions() {
        val maxAttempts = 30
        pendingWorldDeletions.values.toList().forEach { pending ->
            if (!unloadQueuedWorldIfLoaded(pending)) {
                markQueuedWorldDeletionFailed(pending, maxAttempts)
                return@forEach
            }

            if (deleteQueuedWorldFolder(pending)) {
                pendingWorldDeletions.remove(pending.worldName)
                plugin.logger.info("[Arena] 未削除ワールドの削除に成功: ${pending.worldName}")
                return@forEach
            }

            markQueuedWorldDeletionFailed(pending, maxAttempts)
        }
    }

    private fun deleteDirectory(file: File): Boolean {
        if (file.isDirectory) {
            val children = file.listFiles() ?: emptyArray()
            for (child in children) {
                if (!deleteDirectory(child)) return false
            }
        }
        return file.delete()
    }
}
