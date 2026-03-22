package jp.awabi2048.cccontent.features.arena

import jp.awabi2048.cccontent.config.CoreConfigManager
import jp.awabi2048.cccontent.features.arena.generator.ArenaStageGenerator
import jp.awabi2048.cccontent.features.arena.generator.ArenaThemeLoader
import jp.awabi2048.cccontent.features.arena.event.ArenaSessionEndedEvent
import jp.awabi2048.cccontent.features.arena.quest.ArenaQuestModifiers
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.VoidChunkGenerator
import jp.awabi2048.cccontent.items.CustomItemManager
import jp.awabi2048.cccontent.items.arena.ArenaMobTokenItem
import jp.awabi2048.cccontent.mob.MobDefinition
import jp.awabi2048.cccontent.mob.MobService
import jp.awabi2048.cccontent.mob.MobSpawnOptions
import jp.awabi2048.cccontent.util.FeatureInitializationLogger
import jp.awabi2048.cccontent.util.OageMessageSender
import jp.awabi2048.cccontent.world.WorldSettingsHelper
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EnderCrystal
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.PlayerInventory
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

sealed class ArenaStartResult {
    data class Success(
        val themeId: String,
        val waves: Int,
        val mobTypeId: String,
        val difficultyId: String,
        val difficultyDisplay: String
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
    val difficultyRange: ClosedFloatingPointRange<Double>,
    val mobSpawnIntervalTicks: Long,
    val mobCountMultiplier: Double,
    val waveMultiplier: Double,
    val waves: Int,
    val display: String,
    val mobStatsMaxHealth: Double,
    val mobStatsMaxAttack: Double,
    val mobStatsMaxMovementSpeed: Double,
    val mobStatsMaxArmor: Double,
    val mobStatsMaxScale: Double
) {
    val difficultyValue: Double
        get() = (difficultyRange.start + difficultyRange.endInclusive) / 2.0

    fun waveScale(wave: Int): Double {
        return (1.0 + waveMultiplier * wave).coerceAtLeast(0.0)
    }
}

private data class ArenaDropEntry(
    val itemId: String,
    val baseAmount: Int
)

private data class ArenaDropConfig(
    val equipmentDropChance: Double,
    val additionalDefaultDrops: List<ArenaDropEntry>,
    val additionalByMobType: Map<String, List<ArenaDropEntry>>,
    val mockItemCountBonus: Int,
    val mockExpBonusRate: Double,
    val mockRarity: String
)

private data class BarrierRestartConfig(
    val defaultDurationSeconds: Int,
    val corruptionRatioBase: Double
)

class ArenaManager(
    private val plugin: JavaPlugin,
    private val mobService: MobService = MobService(plugin)
) {
    private companion object {
        const val DOOR_ANIMATION_START_DELAY_TICKS = 40L
        const val DOOR_ANIMATION_FRAME_INTERVAL_TICKS = 20L
        const val BARRIER_RESTART_PROGRESS_STEP_TICKS = 5L
        const val BARRIER_RESTART_PROGRESS_STEP_MILLIS = 250L
        const val BARRIER_DEFENSE_TARGET_RATIO = 1.0 / 3.0
        const val BARRIER_DEFENSE_PRESSURE_RADIUS = 4.5
        const val BARRIER_DEFENSE_PRESSURE_RADIUS_SQUARED = BARRIER_DEFENSE_PRESSURE_RADIUS * BARRIER_DEFENSE_PRESSURE_RADIUS
        const val BARRIER_DEFENSE_ATTACK_VELOCITY = 0.18
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
    private val knownMobTypeIds = mutableSetOf<String>()
    private val mobToDefinitionTypeId = mutableMapOf<UUID, String>()
    private var maintenanceTask: BukkitTask? = null
    private var playerMonitorTask: BukkitTask? = null
    private var barrierRestartConfig = BarrierRestartConfig(30, 0.05)
    private var dropConfig = ArenaDropConfig(
        equipmentDropChance = 0.0,
        additionalDefaultDrops = emptyList(),
        additionalByMobType = emptyMap(),
        mockItemCountBonus = 0,
        mockExpBonusRate = 1.0,
        mockRarity = "common"
    )

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
        val dropFile = ensureArenaConfig("config/arena/drop.yml")

        loadBarrierRestartConfig()
        loadDifficultyConfigs(difficultyFile)
        loadMobDefinitions()
        loadMobTypeConfigs(mobTypeFile)
        loadDropConfig(dropFile)
        validateWaveCoverage()
    }

    private fun loadBarrierRestartConfig() {
        val config = CoreConfigManager.get(plugin)
        barrierRestartConfig = BarrierRestartConfig(
            defaultDurationSeconds = config.getInt("arena.barrier_restart.default_duration_seconds", 30).coerceAtLeast(1),
            corruptionRatioBase = config.getDouble("arena.barrier_restart.corruption_ratio_base", 0.05).coerceAtLeast(0.0)
        )
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

            val difficultyRange = parseDifficultyRange(section.getString("difficulty_range"))
            if (difficultyRange == null) {
                plugin.logger.severe("[Arena] difficulty.yml の difficulty_range が不正です: $difficultyId")
                continue
            }
            val mobSpawnIntervalTicks = section.getLong("mob_spawn_interval", 20L).coerceAtLeast(1L)
            val mobCountMultiplier = section.getDouble("mob_count_multiplier", 1.0).coerceAtLeast(0.01)
            val waveMultiplier = section.getDouble("wave_multiplier", 0.0)
            val waves = section.getInt("wave", 5).coerceAtLeast(1)
            val display = section.getString("display", difficultyId) ?: difficultyId
            val mobStatsMaxHealth = section.getDouble("mob_stats_max_health", 20.0).coerceAtLeast(1.0)
            val mobStatsMaxAttack = section.getDouble("mob_stats_max_attack", 1.0).coerceAtLeast(0.0)
            val mobStatsMaxMovementSpeed = section.getDouble("mob_stats_max_movement_speed", 0.23).coerceAtLeast(0.01)
            val mobStatsMaxArmor = section.getDouble("mob_stats_max_armor", 0.0).coerceAtLeast(0.0)
            val mobStatsMaxScale = section.getDouble("mob_stats_max_scale", 1.0).coerceAtLeast(0.1)

            loaded[difficultyId] = ArenaDifficultyConfig(
                id = difficultyId,
                difficultyRange = difficultyRange,
                mobSpawnIntervalTicks = mobSpawnIntervalTicks,
                mobCountMultiplier = mobCountMultiplier,
                waveMultiplier = waveMultiplier,
                waves = waves,
                display = display,
                mobStatsMaxHealth = mobStatsMaxHealth,
                mobStatsMaxAttack = mobStatsMaxAttack,
                mobStatsMaxMovementSpeed = mobStatsMaxMovementSpeed,
                mobStatsMaxArmor = mobStatsMaxArmor,
                mobStatsMaxScale = mobStatsMaxScale
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
        knownMobTypeIds.clear()
        knownMobTypeIds.addAll(loaded.values.map { it.typeId.trim().lowercase(Locale.ROOT) })
        registerMobTypeTokenItems()

        if (mobDefinitions.isEmpty()) {
            plugin.logger.severe("[Arena] config/mob_definition.yml が空のためアリーナを開始できません")
        }
    }

    private fun loadDropConfig(file: File) {
        val config = YamlConfiguration.loadConfiguration(file)
        val equipmentDropChance = config.getDouble("settings.equipment_drop_chance", 0.25)
            .coerceIn(0.0, 1.0)

        val mockItemCountBonus = config.getInt("difficulty_mock.item_count_bonus", 0)
        val mockExpBonusRate = config.getDouble("difficulty_mock.exp_bonus_rate", 1.0)
            .coerceAtLeast(0.0)
        val mockRarity = config.getString("difficulty_mock.rarity", "common") ?: "common"

        val additionalDefaultDrops = parseDropEntries(config, "additional_drops.default")
        val additionalByMobType = mutableMapOf<String, List<ArenaDropEntry>>()
        val byMobTypeSection = config.getConfigurationSection("additional_drops.by_mob_type")
        byMobTypeSection?.getKeys(false)?.forEach { mobTypeId ->
            val key = mobTypeId.trim().lowercase(Locale.ROOT)
            additionalByMobType[key] = parseDropEntries(config, "additional_drops.by_mob_type.$mobTypeId")
        }

        dropConfig = ArenaDropConfig(
            equipmentDropChance = equipmentDropChance,
            additionalDefaultDrops = additionalDefaultDrops,
            additionalByMobType = additionalByMobType,
            mockItemCountBonus = mockItemCountBonus,
            mockExpBonusRate = mockExpBonusRate,
            mockRarity = mockRarity
        )
    }

    private fun parseDropEntries(config: YamlConfiguration, path: String): List<ArenaDropEntry> {
        val raw = config.getMapList(path)
        val entries = mutableListOf<ArenaDropEntry>()
        for (entry in raw) {
            val itemId = entry["item"]?.toString()?.trim().orEmpty()
            if (itemId.isBlank()) {
                continue
            }
            val baseAmount = entry["amount"]?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            entries.add(ArenaDropEntry(itemId = itemId, baseAmount = baseAmount))
        }
        return entries
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

    private fun parseDifficultyRange(text: String?): ClosedFloatingPointRange<Double>? {
        val raw = text?.trim().orEmpty()
        val matched = Regex("^([0-9]+(?:\\.[0-9]+)?)\\.\\.([0-9]+(?:\\.[0-9]+)?)$").matchEntire(raw) ?: return null
        val min = matched.groupValues[1].toDoubleOrNull() ?: return null
        val max = matched.groupValues[2].toDoubleOrNull() ?: return null
        if (min <= 0.0) return null
        if (max < min) return null
        return min..max
    }

    fun startSession(
        target: Player,
        mobTypeId: String,
        difficultyId: String,
        requestedTheme: String?,
        questModifiers: ArenaQuestModifiers = ArenaQuestModifiers.NONE,
        difficultyScore: Double? = null
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
                difficultyValue = difficulty.difficultyValue,
                difficultyScore = difficultyScore ?: difficulty.difficultyValue,
                waves = difficulty.waves,
                questModifiers = questModifiers,
                participants = mutableSetOf(target.uniqueId),
                returnLocations = mutableMapOf(target.uniqueId to returnLocation),
                playerSpawn = stage.playerSpawn,
                entranceLocation = stage.entranceLocation,
                stageBounds = stage.stageBounds,
                roomBounds = stage.roomBounds,
                corridorBounds = stage.corridorBounds,
                roomMobSpawns = stage.roomMobSpawns,
                corridorDoorBlocks = stage.corridorDoorBlocks,
                barrierLocation = stage.barrierLocation,
                participantSpawnProtectionUntilMillis = System.currentTimeMillis() + 4000L
            )
            sessionsByWorld[world.name] = session
            playerToSessionWorld[target.uniqueId] = world.name
            initializeBarrierRestartState(session)
            spawnBarrierCrystal(session)
            if (session.barrierCrystalEntityId == null) {
                throw IllegalStateException("結界石エンドクリスタルの召喚に失敗しました")
            }
            startBarrierAmbientTask(session)
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
                    "difficulty" to difficulty.display,
                    "waves" to difficulty.waves
                )
            )

            initializeWavePipeline(session, mobType, difficulty)
            ArenaStartResult.Success(theme.id, difficulty.waves, mobType.id, difficulty.id, difficulty.display)
        } catch (e: Exception) {
            val failedSession = sessionsByWorld[world.name]
            if (failedSession != null) {
                terminateSession(failedSession, false)
            } else {
                tryDeleteWorld(world)
            }
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
        mobToDefinitionTypeId.clear()
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
            val now = System.currentTimeMillis()
            if (now < session.participantSpawnProtectionUntilMillis) {
                continue
            }
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

    fun handleMobDeath(event: EntityDeathEvent) {
        val entityId = event.entity.uniqueId
        applyArenaMobDrop(event)
        val worldName = mobToSessionWorld.remove(entityId) ?: return
        val session = sessionsByWorld[worldName] ?: return
        session.barrierDefenseMobIds.remove(entityId)
        session.barrierDefenseTargetMobIds.remove(entityId)
        session.barrierDefenseAssaultMobIds.remove(entityId)
        session.barrierDefenseLastAttackAnimationMillis.remove(entityId)
        consumeMob(session, entityId, countKill = true)
    }

    private fun applyArenaMobDrop(event: EntityDeathEvent) {
        val entity = event.entity
        if (!mobToSessionWorld.containsKey(entity.uniqueId)) {
            return
        }

        val killer = entity.killer
        val baseExp = event.droppedExp
        val vanillaDrops = event.drops.map { it.clone() }
        event.drops.clear()

        if (killer == null) {
            event.droppedExp = 0
            return
        }

        val definitionTypeId = mobToDefinitionTypeId[entity.uniqueId] ?: entity.type.name
        val normalizedTypeId = definitionTypeId.trim().lowercase(Locale.ROOT)
        val lootingLevel = resolveLootingLevel(killer.inventory)

        val rebuiltDrops = mutableListOf<ItemStack>()
        rebuiltDrops += rebuildVanillaNonEquipmentDrops(vanillaDrops)
        rebuiltDrops += buildConfiguredAdditionalDrops(killer, normalizedTypeId, lootingLevel)

        selectEquipmentDrop(entity.equipment)?.let { fullId ->
            val dropped = CustomItemManager.createItemForPlayer(fullId, killer, 1)
            if (dropped != null) {
                rebuiltDrops += dropped
            }
        }

        createMobTokenDrop(killer, normalizedTypeId)?.let { token ->
            rebuiltDrops += token
        }

        rebuiltDrops.forEach { stack ->
            if (stack.type.isAir || stack.amount <= 0) return@forEach
            event.drops += stack
        }
        event.droppedExp = floor(baseExp * dropConfig.mockExpBonusRate).toInt().coerceAtLeast(0)
    }

    private fun rebuildVanillaNonEquipmentDrops(vanillaDrops: List<ItemStack>): List<ItemStack> {
        return vanillaDrops
            .filter { !it.type.isAir && it.amount > 0 }
            .filterNot { classifyEquipmentDropType(it.type) != null }
            .map { it.clone() }
    }

    private fun buildConfiguredAdditionalDrops(killer: Player, mobTypeId: String, lootingLevel: Int): List<ItemStack> {
        val entries = mutableListOf<ArenaDropEntry>()
        entries += dropConfig.additionalDefaultDrops
        entries += dropConfig.additionalByMobType[mobTypeId].orEmpty()

        return entries.mapNotNull { entry ->
            val amount = applyAmountModifiers(entry.baseAmount, lootingLevel)
            resolveConfiguredDrop(entry.itemId, killer, amount)
        }
    }

    private fun resolveConfiguredDrop(itemId: String, killer: Player, amount: Int): ItemStack? {
        if (amount <= 0) return null
        CustomItemManager.createItemForPlayer(itemId, killer, amount)?.let { return it }
        val material = runCatching { Material.valueOf(itemId.uppercase(Locale.ROOT)) }.getOrNull() ?: return null
        if (!material.isItem || material.isAir) return null
        return ItemStack(material, amount)
    }

    private fun applyAmountModifiers(baseAmount: Int, lootingLevel: Int): Int {
        val base = baseAmount.coerceAtLeast(1)
        val mockBonus = dropConfig.mockItemCountBonus
        val lootingBonus = if (lootingLevel > 0) random.nextInt(lootingLevel + 1) else 0
        return (base + mockBonus + lootingBonus).coerceAtLeast(1)
    }

    private fun resolveLootingLevel(inventory: PlayerInventory): Int {
        val main = inventory.itemInMainHand
        val off = inventory.itemInOffHand
        val mainLevel = main.getEnchantmentLevel(Enchantment.LOOTING)
        val offLevel = off.getEnchantmentLevel(Enchantment.LOOTING)
        return maxOf(mainLevel, offLevel)
    }

    private fun selectEquipmentDrop(equipment: org.bukkit.inventory.EntityEquipment?): String? {
        if (equipment == null) return null
        if (random.nextDouble() > dropConfig.equipmentDropChance) return null

        val candidates = mutableListOf<String>()
        val slots = listOf(
            equipment.helmet,
            equipment.chestplate,
            equipment.leggings,
            equipment.boots,
            equipment.itemInMainHand,
            equipment.itemInOffHand
        )

        for (item in slots) {
            if (item == null || item.type.isAir) continue
            val category = classifyEquipmentDropType(item.type) ?: continue
            candidates += "arena.decayed_$category"
        }

        if (candidates.isEmpty()) return null
        return candidates[random.nextInt(candidates.size)]
    }

    private fun classifyEquipmentDropType(material: Material): String? {
        val name = material.name
        return when {
            name.endsWith("_AXE") -> "axe"
            name.endsWith("_SWORD") -> "sword"
            name == "SHIELD" -> "shield"
            name == "BOW" -> "bow"
            name.endsWith("_HELMET") -> "helmet"
            name.endsWith("_CHESTPLATE") -> "chestplate"
            name.endsWith("_LEGGINGS") -> "leggings"
            name.endsWith("_BOOTS") -> "boots"
            else -> null
        }
    }

    private fun createMobTokenDrop(killer: Player, mobTypeId: String): ItemStack? {
        val fullId = "arena.mob_token_${sanitizeMobTypeId(mobTypeId)}"
        return CustomItemManager.createItemForPlayer(fullId, killer, 1)
    }

    private fun sanitizeMobTypeId(typeId: String): String {
        val normalized = typeId.trim().lowercase(Locale.ROOT)
        return normalized.replace(Regex("[^a-z0-9_]+"), "_")
    }

    private fun registerMobTypeTokenItems() {
        for (typeId in knownMobTypeIds) {
            CustomItemManager.register(ArenaMobTokenItem(typeId))
        }
    }

    fun handleBarrierClick(player: Player, clicked: Location): Boolean {
        val session = getSession(player) ?: return false
        if (player.world.name != session.worldName) return false

        val block = clicked.block
        val core = session.barrierLocation.block
        if (block.x != core.x || block.y != core.y || block.z != core.z) return false

        return openBarrierRestartMenu(player, session)
    }

    fun handleBarrierCrystalInteract(player: Player, entity: Entity): Boolean {
        val session = getSession(player) ?: return false
        if (player.world.name != session.worldName) return false
        if (!isBarrierCrystal(session, entity)) return false
        return openBarrierRestartMenu(player, session)
    }

    fun handleBarrierCrystalDamage(entity: Entity): Boolean {
        val session = sessionsByWorld[entity.world.name] ?: return false
        return isBarrierCrystal(session, entity)
    }

    fun handleBarrierCrystalExplosion(entity: Entity): Boolean {
        val session = sessionsByWorld[entity.world.name] ?: return false
        return isBarrierCrystal(session, entity)
    }

    fun handleBarrierRestartMenuClick(player: Player, inventory: Inventory, slot: Int): Boolean {
        val session = getSession(player) ?: return false
        if (player.world.name != session.worldName) return false
        if (!isBarrierRestartMenu(inventory)) return false

        if (slot != ArenaBarrierRestartLayout.CENTER_SLOT) {
            return true
        }

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

        if (session.barrierRestartCompleted) {
            return true
        }

        if (session.barrierRestarting) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.barrier.activating", "&e結界石を再起動中です"))
            return true
        }

        startBarrierRestartSequence(player, session)
        return true
    }

    fun handleBarrierRestartMenuDrag(inventory: Inventory): Boolean {
        return isBarrierRestartMenu(inventory)
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
        Bukkit.getPluginManager().callEvent(
            ArenaSessionEndedEvent(
                ownerPlayerId = session.ownerPlayerId,
                worldName = session.worldName,
                themeId = session.themeId,
                mobTypeId = session.mobTypeId,
                difficultyId = session.difficultyId,
                difficultyValue = session.difficultyValue,
                waves = session.waves,
                success = success
            )
        )

        sessionsByWorld.remove(session.worldName)

        session.waveSpawnTasks.values.forEach { it.cancel() }
        session.waveSpawnTasks.clear()
        session.transitionTasks.forEach { it.cancel() }
        session.transitionTasks.clear()
        cleanupBarrierRestartSession(session, removeDefenseMobs = true, smoke = false)

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
        session.barrierActive = false
        session.barrierRestarting = false
        session.barrierRestartCompleted = false
        session.barrierRestartCorruptedSlots.clear()
        removeBarrierCrystal(session)

        session.activeMobs.toList().forEach { mobId ->
            mobToSessionWorld.remove(mobId)
            mobToDefinitionTypeId.remove(mobId)
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

        val maxAlive = calculateWaveCount(mobType.maxSummonCount, difficulty, wave, session.questModifiers.maxSummonCountMultiplier)
        val clearTarget = calculateWaveCount(mobType.clearMobCount, difficulty, wave, session.questModifiers.clearMobCountMultiplier)

        session.startedWaves.add(wave)
        session.waveKillCount.putIfAbsent(wave, 0)
        session.waveClearTargets[wave] = clearTarget
        session.waveMaxAliveCounts[wave] = maxAlive
        session.waveMobIds.putIfAbsent(wave, mutableSetOf())

        startSpawnLoop(session, wave, mobType, difficulty, spawns)
        updateCurrentWave(session)
    }

    private fun calculateWaveCount(base: Int, difficulty: ArenaDifficultyConfig, wave: Int, questMultiplier: Double): Int {
        val scaled = base * difficulty.mobCountMultiplier * difficulty.waveScale(wave) * questMultiplier
        return ceil(scaled).toInt().coerceAtLeast(1)
    }

    private fun startSpawnLoop(
        session: ArenaSession,
        wave: Int,
        mobType: ArenaMobTypeConfig,
        difficulty: ArenaDifficultyConfig,
        spawns: List<Location>
    ) {
        val interval = (difficulty.mobSpawnIntervalTicks * session.questModifiers.spawnIntervalMultiplier)
            .roundToLong()
            .coerceAtLeast(1L)
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

        applyMobStats(entity, definition, difficulty, session.difficultyValue, session.questModifiers)

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
        mobToDefinitionTypeId[entityId] = definition.typeId
    }

    private fun applyMobStats(
        entity: LivingEntity,
        definition: MobDefinition,
        difficulty: ArenaDifficultyConfig,
        difficultyValue: Double,
        questModifiers: ArenaQuestModifiers
    ) {
        val progress = difficultyProgress(difficulty.difficultyRange, difficultyValue)
        val maxHealth = (interpolate(definition.health, difficulty.mobStatsMaxHealth, progress) * questModifiers.mobHealthMultiplier)
            .coerceAtLeast(1.0)
        val attack = (interpolate(definition.attack, difficulty.mobStatsMaxAttack, progress) * questModifiers.mobAttackMultiplier)
            .coerceAtLeast(0.0)
        val speed = interpolate(definition.movementSpeed, difficulty.mobStatsMaxMovementSpeed, progress).coerceAtLeast(0.01)
        val armor = interpolate(definition.armor, difficulty.mobStatsMaxArmor, progress).coerceAtLeast(0.0)
        val scale = interpolate(definition.scale, difficulty.mobStatsMaxScale, progress).coerceAtLeast(0.1)

        entity.getAttribute(Attribute.MAX_HEALTH)?.baseValue = maxHealth
        entity.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = attack
        entity.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = speed
        entity.getAttribute(Attribute.ARMOR)?.baseValue = armor
        entity.getAttribute(Attribute.SCALE)?.baseValue = scale
        entity.health = maxHealth
    }

    private fun interpolate(base: Double, maximum: Double, progress: Double): Double {
        return base + (maximum - base) * progress.coerceIn(0.0, 1.0)
    }

    private fun difficultyProgress(range: ClosedFloatingPointRange<Double>, difficultyValue: Double): Double {
        val width = range.endInclusive - range.start
        if (width <= 0.0) return 1.0
        return ((difficultyValue - range.start) / width).coerceIn(0.0, 1.0)
    }

    private fun consumeMob(session: ArenaSession, mobId: UUID, countKill: Boolean) {
        val removed = session.activeMobs.remove(mobId)
        if (!removed) return

        mobToSessionWorld.remove(mobId)
        mobToDefinitionTypeId.remove(mobId)
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
            mobToDefinitionTypeId.remove(mobId)
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

    private fun initializeBarrierRestartState(session: ArenaSession) {
        val ratio = (barrierRestartConfig.corruptionRatioBase * session.difficultyScore).coerceAtMost(0.2)
        val totalSlots = ArenaBarrierRestartLayout.MENU_SLOTS.size
        val dirtyCount = (totalSlots * ratio).roundToInt().coerceIn(1, totalSlots)

        session.barrierRestartCorruptedSlots.clear()
        session.barrierRestartCorruptedSlots.addAll(ArenaBarrierRestartLayout.MENU_SLOTS.shuffled(random).take(dirtyCount))
        session.barrierRestarting = false
        session.barrierRestartCompleted = false
        session.barrierRestartStartMillis = 0L
        session.barrierRestartDurationMillis = 0L
        session.barrierRestartProgressMillis = 0L
        session.barrierDefenseTargetMobIds.clear()
        session.barrierDefenseAssaultMobIds.clear()
        session.barrierDefenseLastAttackAnimationMillis.clear()
    }

    private fun spawnBarrierCrystal(session: ArenaSession) {
        removeBarrierCrystal(session)

        val world = Bukkit.getWorld(session.worldName) ?: return
        val location = session.barrierLocation.clone().add(0.5, 1.0, 0.5)
        val crystal = world.spawnEntity(location, EntityType.END_CRYSTAL) as? EnderCrystal
            ?: return

        crystal.isInvulnerable = true
        crystal.isShowingBottom = false
        session.barrierCrystalEntityId = crystal.uniqueId
    }

    private fun startBarrierAmbientTask(session: ArenaSession) {
        session.barrierAmbientTask?.cancel()
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val currentSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (currentSession.barrierRestarting) return@Runnable

            val world = Bukkit.getWorld(currentSession.worldName) ?: return@Runnable
            val crystal = currentSession.barrierCrystalEntityId?.let { Bukkit.getEntity(it) } ?: return@Runnable
            if (crystal.world.name != world.name) return@Runnable

            playSoundAtBarrier(currentSession, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 0.5f)
        }, 0L, 100L)
        session.barrierAmbientTask = task
    }

    private fun openBarrierRestartMenu(player: Player, session: ArenaSession): Boolean {
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

        val holder = ArenaBarrierRestartMenuHolder(player.uniqueId, session.worldName)
        val inventory = Bukkit.createInventory(holder, ArenaBarrierRestartLayout.MENU_SIZE, ArenaBarrierRestartLayout.MENU_TITLE)
        holder.backingInventory = inventory
        renderBarrierRestartMenu(session, inventory, barrierRestartProgress(session))
        player.openInventory(inventory)
        return true
    }

    private fun isBarrierRestartMenu(inventory: Inventory): Boolean {
        return inventory.holder is ArenaBarrierRestartMenuHolder
    }

    private fun isBarrierCrystal(session: ArenaSession, entity: Entity): Boolean {
        return session.barrierCrystalEntityId == entity.uniqueId
    }

    private fun barrierRestartProgress(session: ArenaSession): Double {
        if (session.barrierRestartCompleted) return 1.0
        if (!session.barrierRestarting) return 0.0

        val duration = session.barrierRestartDurationMillis.coerceAtLeast(1L)
        return (session.barrierRestartProgressMillis.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun renderBarrierRestartMenu(session: ArenaSession, inventory: Inventory, progress: Double) {
        val headerFooter = createBarrierHeaderFooterPane()
        val background = createBarrierBackgroundPane()
        val corruption = createBarrierCorruptedPane()
        val orderedSlots = session.barrierRestartCorruptedSlots
            .sortedWith(compareBy<Int> { slotDistanceSquared(it) }.thenBy { it })
        val restoredCount = floor(orderedSlots.size * progress.coerceIn(0.0, 1.0)).toInt().coerceIn(0, orderedSlots.size)
        val restoredSlots = orderedSlots.take(restoredCount).toSet()

        for (slot in ArenaBarrierRestartLayout.HEADER_SLOTS) {
            inventory.setItem(slot, headerFooter)
        }

        for (slot in ArenaBarrierRestartLayout.FOOTER_SLOTS) {
            inventory.setItem(slot, headerFooter)
        }

        for (slot in ArenaBarrierRestartLayout.MENU_SLOTS) {
            val item = if (slot in restoredSlots || slot !in session.barrierRestartCorruptedSlots) background else corruption
            inventory.setItem(slot, item)
        }

        inventory.setItem(ArenaBarrierRestartLayout.CENTER_SLOT, createBarrierCenterItem(session))
    }

    private fun createBarrierBackgroundPane(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(" ")
        item.itemMeta = meta
        return item
    }

    private fun createBarrierHeaderFooterPane(): ItemStack {
        val item = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(" ")
        item.itemMeta = meta
        return item
    }

    private fun createBarrierCorruptedPane(): ItemStack {
        val item = ItemStack(Material.SCULK_VEIN)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(" ")
        meta.addEnchant(Enchantment.UNBREAKING, 1, true)
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES, org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
        item.itemMeta = meta
        return item
    }

    private fun createBarrierCenterItem(session: ArenaSession): ItemStack {
        val item = ItemStack(Material.END_CRYSTAL)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(if (session.barrierRestarting) "§e再起動中です..." else "§d結界石を再起動する")
        item.itemMeta = meta
        return item
    }

    private fun slotDistanceSquared(slot: Int): Int {
        val x = slot % 9
        val y = slot / 9
        val dx = x - 4
        val dy = y - 2
        return dx * dx + dy * dy
    }

    private fun startBarrierRestartSequence(player: Player, session: ArenaSession) {
        if (!session.barrierActive) {
            player.sendMessage(
                ArenaI18n.text(
                    player,
                    "arena.messages.barrier.not_ready",
                    "&cまだ結界石を再起動できません。全ウェーブをクリアしてください"
                )
            )
            return
        }
        if (session.barrierRestartCompleted) return
        if (session.barrierRestarting) {
            player.sendMessage(ArenaI18n.text(player, "arena.messages.barrier.activating", "&e結界石を再起動中です"))
            return
        }

        val world = Bukkit.getWorld(session.worldName) ?: return
        val durationMillis = ((barrierRestartConfig.defaultDurationSeconds * session.difficultyScore) * 1000.0)
            .roundToLong()
            .coerceAtLeast(1000L)

        session.barrierRestarting = true
        session.barrierRestartCompleted = false
        session.barrierRestartStartMillis = System.currentTimeMillis()
        session.barrierRestartDurationMillis = durationMillis
        session.barrierRestartProgressMillis = 0L

        broadcastRawMessage(session, "§c結界石の再起動を開始しました！結界を一時的に解除します！")

        session.barrierAmbientTask?.cancel()
        session.barrierAmbientTask = null
        playSoundAtBarrier(session, Sound.BLOCK_BEACON_DEACTIVATE, 5.0f, 0.5f)
        spawnBarrierRestartStartParticles(world, session)

        startBarrierDefenseSpawnTask(session)
        startBarrierDefensePressureTask(session)
        updateBarrierRestartBossBar(session, 0.0)
        refreshBarrierRestartMenus(session, 0.0)

        session.barrierRestartTask?.cancel()
        scheduleBarrierRestartProgressTick(session)
    }

    private fun scheduleBarrierRestartProgressTick(session: ArenaSession) {
        session.barrierRestartTask?.cancel()
        val delay = calculateBarrierRestartTickDelay(session)
        val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            tickBarrierRestartSequence(session)
        }, delay)
        session.barrierRestartTask = task
    }

    private fun tickBarrierRestartSequence(session: ArenaSession) {
        val activeSession = sessionsByWorld[session.worldName] ?: return
        if (!activeSession.barrierRestarting) return

        activeSession.barrierRestartProgressMillis = (activeSession.barrierRestartProgressMillis + BARRIER_RESTART_PROGRESS_STEP_MILLIS)
            .coerceAtMost(activeSession.barrierRestartDurationMillis)

        val progress = barrierRestartProgress(activeSession)
        updateBarrierRestartBossBar(activeSession, progress)
        refreshBarrierRestartMenus(activeSession, progress)

        if (progress >= 1.0) {
            activeSession.barrierRestartTask = null
            completeBarrierRestartSequence(activeSession)
            return
        }

        scheduleBarrierRestartProgressTick(activeSession)
    }

    private fun calculateBarrierRestartTickDelay(session: ArenaSession): Long {
        val pressureCount = session.barrierDefenseAssaultMobIds.count { mobId ->
            val entity = Bukkit.getEntity(mobId) as? LivingEntity
            entity != null && entity.isValid && !entity.isDead && entity.world.name == session.worldName
        }
        if (pressureCount <= 0) return BARRIER_RESTART_PROGRESS_STEP_TICKS

        val slowdownPercent = max(100, 10 * pressureCount)
        val factor = 1.0 + slowdownPercent / 100.0
        return (BARRIER_RESTART_PROGRESS_STEP_TICKS * factor).roundToLong().coerceAtLeast(BARRIER_RESTART_PROGRESS_STEP_TICKS)
    }

    private fun startBarrierDefenseSpawnTask(session: ArenaSession) {
        session.barrierDefenseSpawnTask?.cancel()

        val mobType = mobTypeConfigs[session.mobTypeId] ?: return
        val difficulty = difficultyConfigs[session.difficultyId] ?: return
        val spawnPoints = session.roomMobSpawns[session.waves].orEmpty()
        if (spawnPoints.isEmpty()) return

        val normalInterval = (difficulty.mobSpawnIntervalTicks * session.questModifiers.spawnIntervalMultiplier)
            .roundToLong()
            .coerceAtLeast(1L)
        val interval = (normalInterval / 2.0).roundToLong().coerceAtLeast(1L)

        val normalMaxAlive = calculateWaveCount(mobType.maxSummonCount, difficulty, session.waves, session.questModifiers.maxSummonCountMultiplier)
        val defenseMaxAlive = if (normalMaxAlive <= 1) 1 else (normalMaxAlive / 2.0).roundToInt().coerceIn(1, normalMaxAlive - 1)

        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (!activeSession.barrierRestarting) return@Runnable

            val alive = activeSession.barrierDefenseMobIds.count { mobId ->
                val entity = Bukkit.getEntity(mobId) as? LivingEntity
                entity != null && entity.isValid && !entity.isDead && entity.world.name == activeSession.worldName
            }
            if (alive >= defenseMaxAlive) return@Runnable

            spawnBarrierDefenseMob(activeSession, mobType, difficulty, spawnPoints)
        }, 0L, interval)

        session.barrierDefenseSpawnTask = task
    }

    private fun startBarrierDefensePressureTask(session: ArenaSession) {
        session.barrierDefensePressureTask?.cancel()
        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (!activeSession.barrierRestarting) return@Runnable

            val barrierLocation = getBarrierCrystalLocation(activeSession) ?: activeSession.barrierLocation.clone().add(0.5, 1.0, 0.5)
            val targetIds = activeSession.barrierDefenseTargetMobIds.toList()

            targetIds.forEach { mobId ->
                val mob = Bukkit.getEntity(mobId) as? LivingEntity ?: run {
                    activeSession.barrierDefenseTargetMobIds.remove(mobId)
                    activeSession.barrierDefenseAssaultMobIds.remove(mobId)
                    return@forEach
                }

                if (!mob.isValid || mob.isDead || mob.world.name != activeSession.worldName) {
                    activeSession.barrierDefenseTargetMobIds.remove(mobId)
                    activeSession.barrierDefenseAssaultMobIds.remove(mobId)
                    return@forEach
                }

                val currentLocation = mob.location
                val distanceSquared = currentLocation.distanceSquared(barrierLocation)
                if (distanceSquared <= BARRIER_DEFENSE_PRESSURE_RADIUS_SQUARED) {
                    activeSession.barrierDefenseAssaultMobIds.add(mobId)
                    mob.setAI(false)
                    mob.velocity = Vector(0.0, 0.0, 0.0)
                    faceLocation(mob, barrierLocation)
                    triggerBarrierAttackAnimation(mob)
                    return@forEach
                }

                activeSession.barrierDefenseAssaultMobIds.remove(mobId)
                mob.setAI(false)
                val direction = barrierLocation.toVector().subtract(currentLocation.toVector())
                if (direction.lengthSquared() > 0.0001) {
                    mob.velocity = direction.normalize().multiply(BARRIER_DEFENSE_ATTACK_VELOCITY)
                }
            }
        }, 0L, 1L)

        session.barrierDefensePressureTask = task
    }

    private fun spawnBarrierDefenseMob(
        session: ArenaSession,
        mobType: ArenaMobTypeConfig,
        difficulty: ArenaDifficultyConfig,
        spawnPoints: List<Location>
    ) {
        val spawnPoint = selectSpawnPoint(session, spawnPoints) ?: return
        val weightedMob = selectWeightedMob(mobType.candidatesForWave(session.waves)) ?: return
        val definition = mobDefinitions[weightedMob.mobId] ?: return

        val entity = mobService.spawn(
            definition,
            spawnPoint,
            MobSpawnOptions(
                featureId = "arena_barrier_restart",
                combatActiveProvider = { true },
                metadata = mapOf("world" to session.worldName, "restart" to "true")
            )
        ) ?: return

        entity.removeWhenFarAway = false
        entity.canPickupItems = false
        applyMobStats(entity, definition, difficulty, session.difficultyValue, session.questModifiers)
        val isTargetingBarrier = random.nextDouble() < BARRIER_DEFENSE_TARGET_RATIO
        if (isTargetingBarrier) {
            session.barrierDefenseTargetMobIds.add(entity.uniqueId)
            entity.setAI(false)
            if (entity is Mob) {
                entity.target = null
            }
        } else if (entity is Mob) {
            entity.setAI(true)
            entity.target = findNearestParticipant(session, entity.location)
        }

        val entityId = entity.uniqueId
        session.barrierDefenseMobIds.add(entityId)
        mobToSessionWorld[entityId] = session.worldName
        mobToDefinitionTypeId[entityId] = definition.typeId
    }

    private fun completeBarrierRestartSequence(session: ArenaSession) {
        if (!session.barrierRestarting) return

        session.barrierRestarting = false
        session.barrierRestartCompleted = true

        session.barrierRestartTask?.cancel()
        session.barrierRestartTask = null
        session.barrierDefenseSpawnTask?.cancel()
        session.barrierDefenseSpawnTask = null
        session.barrierAmbientTask?.cancel()
        session.barrierAmbientTask = null

        cleanupBarrierRestartSession(session, removeDefenseMobs = true, smoke = true)
        refreshBarrierRestartMenus(session, 1.0)

        playSoundAtBarrier(session, Sound.BLOCK_BEACON_ACTIVATE, 5.0f, 0.5f)
        val world = Bukkit.getWorld(session.worldName) ?: return
        spawnBarrierRestartSuccessParticles(world, session)
        broadcastRawMessage(session, "§a結界石の再起動に成功しました！")

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            activeSession.participants.forEach { participantId ->
                val player = Bukkit.getPlayer(participantId) ?: return@forEach
                if (!player.isOnline || player.world.name != activeSession.worldName) return@forEach
                player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f)
                player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f)
                OageMessageSender.send(player, "結界の再起動を確認しました！転送します！", plugin)
            }

            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val currentSession = sessionsByWorld[session.worldName] ?: return@Runnable
                terminateSession(
                    currentSession,
                    true,
                    messageKey = "arena.messages.session.cleared",
                    fallbackMessage = "&aアリーナクリア！"
                )
            }, 40L)
        }, 40L)
    }

    private fun refreshBarrierRestartMenus(session: ArenaSession, progress: Double) {
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            if (!player.isOnline || player.world.name != session.worldName) return@forEach
            val holder = player.openInventory.topInventory.holder as? ArenaBarrierRestartMenuHolder ?: return@forEach
            if (holder.worldName != session.worldName || holder.ownerId != participantId) return@forEach
            renderBarrierRestartMenu(session, player.openInventory.topInventory, progress)
        }
    }

    private fun updateBarrierRestartBossBar(session: ArenaSession, progress: Double) {
        val bossBar = session.barrierRestartBossBar ?: BossBar.bossBar(
            Component.text("§7結界石を再起動中..."),
            progress.toFloat().coerceIn(0.0f, 1.0f),
            BossBar.Color.PURPLE,
            BossBar.Overlay.PROGRESS
        ).also { created ->
            session.barrierRestartBossBar = created
            session.participants.forEach { participantId ->
                val player = Bukkit.getPlayer(participantId) ?: return@forEach
                if (player.isOnline && player.world.name == session.worldName) {
                    player.showBossBar(created)
                }
            }
        }

        bossBar.name(Component.text("§7結界石を再起動中..."))
        bossBar.progress(progress.toFloat().coerceIn(0.0f, 1.0f))
    }

    private fun hideBarrierRestartBossBar(session: ArenaSession) {
        val bossBar = session.barrierRestartBossBar ?: return
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            if (player.isOnline) {
                player.hideBossBar(bossBar)
            }
        }
        session.barrierRestartBossBar = null
    }

    private fun cleanupBarrierRestartSession(session: ArenaSession, removeDefenseMobs: Boolean, smoke: Boolean) {
        session.barrierAmbientTask?.cancel()
        session.barrierAmbientTask = null
        session.barrierRestartTask?.cancel()
        session.barrierRestartTask = null
        session.barrierDefenseSpawnTask?.cancel()
        session.barrierDefenseSpawnTask = null
        session.barrierDefensePressureTask?.cancel()
        session.barrierDefensePressureTask = null

        if (removeDefenseMobs) {
            removeBarrierDefenseMobs(session, smoke)
        }

        session.barrierDefenseTargetMobIds.clear()
        session.barrierDefenseAssaultMobIds.clear()
        session.barrierDefenseLastAttackAnimationMillis.clear()

        hideBarrierRestartBossBar(session)
        if (session.barrierRestartCompleted) {
            session.barrierRestartCorruptedSlots.clear()
        }
    }

    private fun removeBarrierDefenseMobs(session: ArenaSession, smoke: Boolean) {
        val ids = session.barrierDefenseMobIds.toList()
        ids.forEach { mobId ->
            mobToSessionWorld.remove(mobId)
            mobToDefinitionTypeId.remove(mobId)
            mobService.untrack(mobId)
            session.barrierDefenseTargetMobIds.remove(mobId)
            session.barrierDefenseAssaultMobIds.remove(mobId)
            session.barrierDefenseLastAttackAnimationMillis.remove(mobId)

            val entity = Bukkit.getEntity(mobId) as? LivingEntity
            if (entity != null && entity.isValid && !entity.isDead) {
                if (smoke) {
                    spawnSmoke(entity.location)
                }
                entity.remove()
            }
            session.barrierDefenseMobIds.remove(mobId)
        }
    }

    private fun removeBarrierCrystal(session: ArenaSession) {
        val entityId = session.barrierCrystalEntityId ?: return
        session.barrierCrystalEntityId = null
        val entity = Bukkit.getEntity(entityId) ?: return
        entity.remove()
    }

    private fun spawnSmoke(location: Location) {
        val world = location.world ?: return
        world.spawnParticle(Particle.SMOKE, location, 30, 0.5, 2.0, 0.5, 0.0)
    }

    private fun spawnBarrierRestartStartParticles(world: World, session: ArenaSession) {
        val center = session.barrierLocation.clone().add(0.5, 0.5, 0.5)
        world.spawnParticle(Particle.ENCHANTED_HIT, center, 150, 3.5, 3.5, 3.5, 0.0)
        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, center, 100, 3.5, 3.5, 3.5, 0.0)
    }

    private fun spawnBarrierRestartSuccessParticles(world: World, session: ArenaSession) {
        val center = session.barrierLocation.clone().add(0.5, 0.5, 0.5)
        world.spawnParticle(Particle.OMINOUS_SPAWNING, center, 150, 3.5, 3.5, 3.5, 0.0)
        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, center, 150, 3.5, 3.5, 3.5, 0.0)
    }

    private fun broadcastRawMessage(session: ArenaSession, message: String) {
        session.participants.forEach { participantId ->
            val player = Bukkit.getPlayer(participantId) ?: return@forEach
            if (player.isOnline && player.world.name == session.worldName) {
                player.sendMessage(message)
            }
        }
    }

    private fun playSoundAtBarrier(session: ArenaSession, sound: Sound, volume: Float, pitch: Float) {
        val world = Bukkit.getWorld(session.worldName) ?: return
        val center = session.barrierLocation.clone().add(0.5, 0.5, 0.5)
        world.playSound(center, sound, volume, pitch)
    }

    private fun getBarrierCrystalLocation(session: ArenaSession): Location? {
        val entityId = session.barrierCrystalEntityId ?: return null
        val entity = Bukkit.getEntity(entityId) ?: return null
        return entity.location.clone()
    }

    private fun faceLocation(entity: LivingEntity, target: Location) {
        val location = entity.location.clone()
        val dx = target.x - location.x
        val dy = target.y + 0.5 - (location.y + 0.5)
        val dz = target.z - location.z
        val horizontal = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.0001)
        location.yaw = Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat()
        location.pitch = Math.toDegrees(kotlin.math.atan2(-dy, horizontal)).toFloat()
        entity.teleport(location)
    }

    private fun triggerBarrierAttackAnimation(entity: LivingEntity) {
        val mobId = entity.uniqueId
        val now = System.currentTimeMillis()
        val last = sessionsByWorld[entity.world.name]?.barrierDefenseLastAttackAnimationMillis?.get(mobId) ?: 0L
        if (now - last < 500L) {
            return
        }
        sessionsByWorld[entity.world.name]?.barrierDefenseLastAttackAnimationMillis?.set(mobId, now)

        entity.world.spawnParticle(Particle.SWEEP_ATTACK, entity.location.clone().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0)
        entity.world.playSound(entity.location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.45f, 0.9f)
        runCatching {
            entity.javaClass.methods.firstOrNull { it.name == "swingMainHand" && it.parameterCount == 0 }?.invoke(entity)
        }
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
