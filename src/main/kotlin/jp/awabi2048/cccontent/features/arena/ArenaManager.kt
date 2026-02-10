package jp.awabi2048.cccontent.features.arena

import jp.awabi2048.cccontent.features.arena.generator.ArenaStageGenerator
import jp.awabi2048.cccontent.features.arena.generator.ArenaThemeLoader
import jp.awabi2048.cccontent.features.sukima_dungeon.generator.VoidChunkGenerator
import jp.awabi2048.cccontent.world.WorldSettingsHelper
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Zombie
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.UUID

sealed class ArenaStartResult {
    data class Success(val themeId: String, val waves: Int) : ArenaStartResult()
    data class Error(val message: String) : ArenaStartResult()
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

class ArenaManager(private val plugin: JavaPlugin) {
    private val random = kotlin.random.Random.Default
    private val themeLoader = ArenaThemeLoader(plugin)
    private val stageGenerator = ArenaStageGenerator()
    private val sessionsByWorld = mutableMapOf<String, ArenaSession>()
    private val playerToSessionWorld = mutableMapOf<UUID, String>()
    private val mobToSessionWorld = mutableMapOf<UUID, String>()
    private val pendingWorldDeletions = mutableMapOf<String, PendingWorldDeletion>()
    private val pendingBarrierActivations = mutableMapOf<UUID, PendingBarrierActivation>()
    private var maintenanceTask: BukkitTask? = null

    private var clearThreshold: Double = 0.2
    private var spawnIntervalSeconds: Int = 5
    private var spawnRandomness: Double = 0.5

    fun initialize() {
        loadConfig()
        themeLoader.load()
        startMaintenanceTask()
    }

    fun reloadThemes() {
        loadConfig()
        themeLoader.load()
    }

    private fun loadConfig() {
        val configFile = File(plugin.dataFolder, "arena/config.yml")
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            plugin.saveResource("arena/config.yml", false)
        }

        val config = YamlConfiguration.loadConfiguration(configFile)
        clearThreshold = config.getDouble("arena.clear_threshold", 0.2).coerceIn(0.0, 1.0)
        spawnIntervalSeconds = config.getInt("arena.spawn_interval", 5).coerceIn(1, 60)
        spawnRandomness = config.getDouble("arena.spawn_randomness", 0.5).coerceIn(0.0, 1.0)
    }

    fun startSession(target: Player, waves: Int, requestedTheme: String?): ArenaStartResult {
        if (waves <= 0) {
            return ArenaStartResult.Error("waves は1以上で指定してください")
        }
        if (playerToSessionWorld.containsKey(target.uniqueId)) {
            return ArenaStartResult.Error("${target.name} はすでにアリーナセッション中です")
        }

        val theme = if (requestedTheme.isNullOrBlank()) {
            themeLoader.getRandomTheme(random)
        } else {
            themeLoader.getTheme(requestedTheme)
        } ?: return ArenaStartResult.Error("有効なテーマが見つかりません")

        val world = createArenaWorld() ?: return ArenaStartResult.Error("アリーナ用ワールド作成に失敗しました")

        return try {
            val returnLocation = target.location.clone()
            val origin = Location(world, 0.0, 64.0, 0.0)
            val stage = stageGenerator.build(world, origin, theme, waves, random)
            val session = ArenaSession(
                ownerPlayerId = target.uniqueId,
                worldName = world.name,
                themeId = theme.id,
                waves = waves,
                participants = mutableSetOf(target.uniqueId),
                returnLocations = mutableMapOf(target.uniqueId to returnLocation),
                playerSpawn = stage.playerSpawn,
                stageBounds = stage.stageBounds,
                roomBounds = stage.roomBounds,
                roomMobSpawns = stage.roomMobSpawns,
                barrierLocation = stage.barrierLocation
            )
            sessionsByWorld[world.name] = session
            playerToSessionWorld[target.uniqueId] = world.name

            target.teleport(stage.playerSpawn)
            target.sendMessage("§6[Arena] セッション開始: theme=${theme.id}, waves=$waves")
            target.sendMessage("§7最初の部屋から進み、各ウェーブを殲滅してください")
            ArenaStartResult.Success(theme.id, waves)
        } catch (e: Exception) {
            tryDeleteWorld(world)
            ArenaStartResult.Error("ステージ生成に失敗しました: ${e.message}")
        }
    }

    fun stopSession(player: Player, reason: String = "§cアリーナセッションが終了しました"): Boolean {
        return leavePlayerFromSession(player.uniqueId, reason)
    }

    fun stopSessionById(playerId: UUID, reason: String = "§cアリーナセッションが終了しました"): Boolean {
        return leavePlayerFromSession(playerId, reason)
    }

    fun shutdown() {
        maintenanceTask?.cancel()
        maintenanceTask = null

        sessionsByWorld.values.toList().forEach { session ->
            terminateSession(session, false, "§cサーバー停止によりアリーナを終了しました")
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

    fun handleMove(player: Player, to: Location, from: Location) {
        val session = getSession(player) ?: return

        if (pendingBarrierActivations.containsKey(player.uniqueId)) {
            return
        }

        if (to.world?.name != session.worldName || !session.stageBounds.contains(to.x, to.z)) {
            leavePlayerFromSession(player.uniqueId, "§cステージ外に出たためアリーナを終了しました")
            return
        }

        val roomIndex = locateRoom(session, to) ?: return
        if (roomIndex == 0) return

        if (session.activeMobs.isNotEmpty() && roomIndex > session.currentWave) {
            player.teleport(from)
            player.sendMessage("§c現在のウェーブをクリアしてから先に進んでください")
            return
        }

        if (roomIndex == session.currentWave + 1 && !session.startedWaves.contains(roomIndex)) {
            startWave(session, roomIndex)
        }
    }

    fun handleTeleport(player: Player, to: Location?) {
        val session = getSession(player) ?: return
        if (pendingBarrierActivations.containsKey(player.uniqueId)) {
            return
        }
        if (to == null || to.world?.name != session.worldName || !session.stageBounds.contains(to.x, to.z)) {
            leavePlayerFromSession(player.uniqueId, "§cステージ外に出たためアリーナを終了しました")
        }
    }

    fun handleMobDeath(entityId: UUID) {
        val worldName = mobToSessionWorld.remove(entityId) ?: return
        val session = sessionsByWorld[worldName] ?: return
        consumeMob(session, entityId)
    }

    fun handleBarrierClick(player: Player, clicked: Location): Boolean {
        val session = getSession(player) ?: return false
        if (player.world.name != session.worldName) return false

        val block = clicked.block
        val core = session.barrierLocation.block
        if (block.x != core.x || block.y != core.y || block.z != core.z) return false

        if (!session.barrierActive) {
            player.sendMessage("§cまだ結界石を再起動できません。最終ウェーブをクリアしてください")
            return true
        }

        if (pendingBarrierActivations.containsKey(player.uniqueId)) {
            player.sendMessage("§e結界石を再起動中です")
            return true
        }

        startBarrierActivation(player, session)
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

    private fun leavePlayerFromSession(playerId: UUID, reason: String): Boolean {
        val worldName = playerToSessionWorld[playerId] ?: return false
        val session = sessionsByWorld[worldName] ?: return false

        cancelBarrierActivation(playerId)

        playerToSessionWorld.remove(playerId)
        session.participants.remove(playerId)
        val returnLocation = session.returnLocations.remove(playerId)

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
            terminateSession(session, false, null)
        }
        return true
    }

    private fun terminateSession(session: ArenaSession, success: Boolean, message: String?) {
        sessionsByWorld.remove(session.worldName)

        session.waveSpawnTasks.values.forEach { it.cancel() }
        session.waveSpawnTasks.clear()

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
                if (message != null) {
                    player.sendMessage(message)
                    if (success) {
                        player.sendMessage("§7管理コマンドで任意wave/テーマを指定して再挑戦できます")
                    }
                }
            }
        }

        session.participants.clear()
        session.returnLocations.clear()

        session.activeMobs.toList().forEach { mobId ->
            Bukkit.getEntity(mobId)?.remove()
            session.activeMobs.remove(mobId)
            mobToSessionWorld.remove(mobId)
        }

        val world = Bukkit.getWorld(session.worldName)
        if (world != null) {
            tryDeleteWorld(world)
        } else {
            queueWorldDeletion(session.worldName, File(Bukkit.getWorldContainer(), session.worldName))
        }
    }

    private fun startWave(session: ArenaSession, wave: Int) {
        val world = Bukkit.getWorld(session.worldName) ?: return
        session.currentWave = wave
        session.startedWaves.add(wave)

        val spawns = session.roomMobSpawns[wave].orEmpty()
        if (spawns.isEmpty()) {
            broadcastSubtitle(session, "§6Wave $wave", 10, 70, 20)
            playSound(session, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f)
            onWaveCleared(session)
            return
        }

        val initialCount = spawns.size
        session.waveMobCount[wave] = initialCount

        for (spawn in spawns) {
            spawnMob(world, session, spawn)
        }

        broadcastSubtitle(session, "§6Wave $wave", 10, 70, 20)
        playSound(session, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f)

        startSpawnLoop(session, wave, spawns)
    }

    private fun spawnMob(world: World, session: ArenaSession, spawn: Location) {
        val zombie = world.spawnEntity(spawn, EntityType.ZOMBIE) as Zombie
        zombie.removeWhenFarAway = false
        session.activeMobs.add(zombie.uniqueId)
        mobToSessionWorld[zombie.uniqueId] = session.worldName
    }

    private fun startSpawnLoop(session: ArenaSession, wave: Int, spawns: List<Location>) {
        val baseInterval = spawnIntervalSeconds * 20L
        val randomRange = spawnRandomness * baseInterval

        fun scheduleNextSpawn() {
            val delay = baseInterval + (random.nextDouble() * randomRange * 2 - randomRange).toLong()
            val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val currentSession = sessionsByWorld[session.worldName] ?: return@Runnable
                if (currentSession.currentWave != wave) return@Runnable
                if (!currentSession.startedWaves.contains(wave)) return@Runnable

                val world = Bukkit.getWorld(session.worldName) ?: return@Runnable
                for (spawn in spawns) {
                    spawnMob(world, currentSession, spawn)
                }

                currentSession.waveMobCount[wave] = (currentSession.waveMobCount[wave] ?: 0) + spawns.size

                if (currentSession.startedWaves.contains(wave)) {
                    scheduleNextSpawn()
                }
            }, delay.coerceAtLeast(20L))

            session.waveSpawnTasks[wave]?.cancel()
            session.waveSpawnTasks[wave] = task
        }

        scheduleNextSpawn()
    }

    private fun locateRoom(session: ArenaSession, location: Location): Int? {
        return session.roomBounds.entries.firstOrNull { (_, bounds) ->
            bounds.contains(location.x, location.z)
        }?.key
    }

    private fun consumeMob(session: ArenaSession, mobId: UUID) {
        val removed = session.activeMobs.remove(mobId)
        if (!removed) return

        mobToSessionWorld.remove(mobId)

        val wave = session.currentWave
        val totalMobs = session.waveMobCount[wave] ?: 0
        val threshold = (totalMobs * clearThreshold).toInt()

        if (session.activeMobs.size <= threshold) {
            onWaveCleared(session)
        }
    }

    private fun onWaveCleared(session: ArenaSession) {
        val wave = session.currentWave
        session.waveSpawnTasks[wave]?.cancel()
        session.waveSpawnTasks.remove(wave)

        playSound(session, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
        if (session.currentWave >= session.waves) {
            session.barrierActive = true
            broadcastSubtitle(session, "§b結界石を右クリックして再起動", 10, 70, 20)
            return
        }
    }

    private fun startBarrierActivation(player: Player, session: ArenaSession) {
        val effectTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!player.isOnline || player.world.name != session.worldName) return@Runnable
            val center = session.barrierLocation.clone().add(0.5, 0.8, 0.5)
            player.world.spawnParticle(Particle.END_ROD, center, 24, 0.35, 0.45, 0.35, 0.01)
            player.sendTitle("", "§b結界石を再起動中...", 0, 15, 5)
        }, 0L, 10L)

        val completionTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val pending = pendingBarrierActivations.remove(player.uniqueId) ?: return@Runnable
            pending.effectTask.cancel()

            val activeSession = sessionsByWorld[session.worldName] ?: return@Runnable
            if (!activeSession.participants.contains(player.uniqueId)) return@Runnable
            if (!player.isOnline || player.world.name != session.worldName) return@Runnable

            player.sendTitle("", "§a再起動完了", 5, 30, 10)
            terminateSession(activeSession, true, "§aアリーナクリア！")
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

    private fun broadcastSubtitle(session: ArenaSession, subtitle: String, fadeIn: Int, stay: Int, fadeOut: Int) {
        session.participants.forEach { participantId ->
            Bukkit.getPlayer(participantId)?.sendTitle("", subtitle, fadeIn, stay, fadeOut)
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

        Bukkit.unloadWorld(world, false)
        if (!deleteDirectory(world.worldFolder)) {
            queueWorldDeletion(world.name, world.worldFolder)
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
    }

    private fun reconcileActiveMobs() {
        sessionsByWorld.values.toList().forEach { session ->
            session.activeMobs.toList().forEach { mobId ->
                val entity = Bukkit.getEntity(mobId)
                val invalid = entity == null || entity.isDead || entity.type != EntityType.ZOMBIE || entity.world.name != session.worldName
                if (invalid) {
                    consumeMob(session, mobId)
                }
            }
        }
    }

    private fun processPendingWorldDeletions() {
        val maxAttempts = 30
        pendingWorldDeletions.values.toList().forEach { pending ->
            Bukkit.getWorld(pending.worldName)?.let { loaded ->
                Bukkit.unloadWorld(loaded, false)
            }

            if (!pending.folder.exists() || deleteDirectory(pending.folder)) {
                pendingWorldDeletions.remove(pending.worldName)
                plugin.logger.info("[Arena] 未削除ワールドの削除に成功: ${pending.worldName}")
                return@forEach
            }

            pending.attempts += 1
            if (pending.attempts >= maxAttempts) {
                plugin.logger.severe("[Arena] ワールド削除を断念: ${pending.worldName} path=${pending.folder.absolutePath}")
                pendingWorldDeletions.remove(pending.worldName)
            }
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
