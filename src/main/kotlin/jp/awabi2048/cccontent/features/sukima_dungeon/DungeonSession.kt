package jp.awabi2048.cccontent.features.sukima_dungeon

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

data class DungeonSession(
    val playerUUID: UUID,
    val tier: DungeonTier,
    val themeName: String,
    val durationSeconds: Int,
    var collectedSprouts: Int = 0,
    var totalSprouts: Int = 0,
    var elapsedMillis: Long = 0,
    @Transient var lastUpdate: Long = System.currentTimeMillis(),
    val startLocation: Location? = null,
    val tileSize: Int = 16,
    val gridWidth: Int = 0,
    val gridLength: Int = 0,
    val dungeonWorldName: String? = null,
    val minibossMarkers: Map<Pair<Int, Int>, Location> = emptyMap(),
    val minibossTriggered: MutableSet<Pair<Int, Int>> = mutableSetOf(),
    val mobSpawnPoints: List<Location> = emptyList(),
    val spawnedMobs: MutableMap<Location, UUID> = mutableMapOf(),
    val restCells: Set<Pair<Int, Int>> = emptySet(),
    var isMultiplayer: Boolean = false,
    var isCollapsing: Boolean = false,
    var collapseRemainingMillis: Long = 0,
    var isDown: Boolean = false
) {
    val player: Player?
        get() = Bukkit.getPlayer(playerUUID)

    fun updateElapsed() {
        val now = System.currentTimeMillis()
        val delta = now - lastUpdate
        if (isCollapsing) {
            collapseRemainingMillis = (collapseRemainingMillis - delta).coerceAtLeast(0)
        } else {
            elapsedMillis += delta
        }
        lastUpdate = now
    }
    
    fun getRemainingTime(): Long {
        return if (isCollapsing) collapseRemainingMillis else Long.MAX_VALUE
    }
    
    fun getFormattedRemainingTime(): String {
        val seconds = (getRemainingTime() / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    fun getFormattedTime(): String {
        val seconds = (elapsedMillis / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }
}

object DungeonSessionManager {
    private val sessions = mutableMapOf<UUID, DungeonSession>()
    
    fun startSession(
        player: Player, 
        tier: DungeonTier, 
        themeName: String, 
        durationSeconds: Int, 
        totalSprouts: Int = 0,
        startLocation: Location? = null,
        tileSize: Int = 16,
        gridWidth: Int = 0,
        gridLength: Int = 0,
        worldName: String? = null,
        minibossMarkers: Map<Pair<Int, Int>, Location> = emptyMap(),
        mobSpawnPoints: List<Location> = emptyList(),
        restCells: Set<Pair<Int, Int>> = emptySet(),
        isMultiplayer: Boolean = false
    ) {
        val session = DungeonSession(
            player.uniqueId, tier, themeName, durationSeconds,
            totalSprouts = totalSprouts,
            startLocation = startLocation,
            tileSize = tileSize,
            gridWidth = gridWidth,
            gridLength = gridLength,
            dungeonWorldName = worldName,
            minibossMarkers = minibossMarkers,
            mobSpawnPoints = mobSpawnPoints,
            restCells = restCells,
            isMultiplayer = isMultiplayer
        )
        sessions[player.uniqueId] = session
    }

    fun endSession(playerUUID: UUID) {
        sessions.remove(playerUUID)
    }
    
    fun endSession(player: Player) {
        endSession(player.uniqueId)
    }
    
    fun getSession(playerUUID: UUID): DungeonSession? {
        return sessions[playerUUID]
    }

    fun getSession(player: Player): DungeonSession? {
        return getSession(player.uniqueId)
    }
    
    fun getAllSessions(): Collection<DungeonSession> {
        return sessions.values
    }
    
    fun clearAll() {
        sessions.clear()
    }

    fun saveSessions(plugin: JavaPlugin) {
        val file = File(plugin.dataFolder, "sessions.yml")
        val config = YamlConfiguration()
        
        for (session in sessions.values) {
            session.updateElapsed() // 菫晏ｭ伜燕縺ｫ邨碁℃譎る俣繧呈峩譁ｰ
            val path = "sessions.${session.playerUUID}"
            config.set("$path.tier", session.tier.name)
            config.set("$path.themeName", session.themeName)
            config.set("$path.durationSeconds", session.durationSeconds)
            config.set("$path.collectedSprouts", session.collectedSprouts)
            config.set("$path.totalSprouts", session.totalSprouts)
            config.set("$path.elapsedMillis", session.elapsedMillis)
            config.set("$path.startLocation", session.startLocation)
            config.set("$path.tileSize", session.tileSize)
            config.set("$path.gridWidth", session.gridWidth)
            config.set("$path.gridLength", session.gridLength)
            config.set("$path.worldName", session.dungeonWorldName)
            config.set("$path.isMultiplayer", session.isMultiplayer)
            config.set("$path.isDown", session.isDown)
            
            // restCells の保存
            val restList = session.restCells.map { "${it.first},${it.second}" }
            config.set("$path.restCells", restList)

            // minibossMarkers の保存
            val minibossList = session.minibossMarkers.map { "${it.key.first},${it.key.second}|${it.value.world?.name ?: ""},${it.value.x},${it.value.y},${it.value.z},${it.value.yaw},${it.value.pitch}" }
            config.set("$path.minibossMarkers", minibossList)

            // minibossTriggered の保存
            val triggeredList = session.minibossTriggered.map { "${it.first},${it.second}" }
            config.set("$path.minibossTriggered", triggeredList)

            // mobSpawnPoints の保存
            config.set("$path.mobSpawnPoints", session.mobSpawnPoints)

            // spawnedMobs の保存 (UUIDのみ保存し、ロード時に Location と紐付け直すが、Locationをキーにするのは難しいので
            // スポーン地点のインデックスなどで保存するか、Location自体を文字列化する)
            val spawnedMobsList = session.spawnedMobs.map { "${it.key.world?.name ?: ""},${it.key.x},${it.key.y},${it.key.z},${it.key.yaw},${it.key.pitch}|${it.value}" }
            config.set("$path.spawnedMobs", spawnedMobsList)
        }
        
        config.save(file)
        plugin.logger.info("Saved ${sessions.size} dungeon sessions.")
    }

    fun loadSessions(plugin: JavaPlugin) {
        val file = File(plugin.dataFolder, "sessions.yml")
        if (!file.exists()) return
        
        val config = YamlConfiguration.loadConfiguration(file)
        val sessionsSection = config.getConfigurationSection("sessions") ?: return
        
        for (uuidString in sessionsSection.getKeys(false)) {
            val uuid = UUID.fromString(uuidString)
            val path = "sessions.$uuidString"
            
            val tier = DungeonTier.valueOf(config.getString("$path.tier") ?: "BROKEN")
            val themeName = config.getString("$path.themeName") ?: ""
            val durationSeconds = config.getInt("$path.durationSeconds")
            val collectedSprouts = config.getInt("$path.collectedSprouts")
            val totalSprouts = config.getInt("$path.totalSprouts")
            val elapsedMillis = config.getLong("$path.elapsedMillis")
            val startLocation = config.get("$path.startLocation") as? Location
            val tileSize = config.getInt("$path.tileSize")
            val gridWidth = config.getInt("$path.gridWidth")
            val gridLength = config.getInt("$path.gridLength")
            val worldName = config.getString("$path.worldName")
            val isMultiplayer = config.getBoolean("$path.isMultiplayer", false)
            val isDown = config.getBoolean("$path.isDown", false)
            
            val restList = config.getStringList("$path.restCells")
            val restCells = restList.mapNotNull {
                val parts = it.split(",")
                if (parts.size == 2) parts[0].toInt() to parts[1].toInt() else null
            }.toSet()

            val minibossList = config.getStringList("$path.minibossMarkers")
            val minibossMarkers = minibossList.mapNotNull {
                val parts = it.split("|")
                if (parts.size != 2) return@mapNotNull null
                val cellParts = parts[0].split(",")
                val locParts = parts[1].split(",")
                if (cellParts.size != 2 || locParts.size != 6) return@mapNotNull null
                
                val cell = cellParts[0].toInt() to cellParts[1].toInt()
                val world = Bukkit.getWorld(locParts[0]) ?: return@mapNotNull null
                val loc = Location(world, locParts[1].toDouble(), locParts[2].toDouble(), locParts[3].toDouble(), locParts[4].toFloat(), locParts[5].toFloat())
                cell to loc
            }.toMap()

            val triggeredList = config.getStringList("$path.minibossTriggered")
            val minibossTriggered = triggeredList.mapNotNull {
                val parts = it.split(",")
                if (parts.size == 2) parts[0].toInt() to parts[1].toInt() else null
            }.toMutableSet()

            val mobSpawnPoints = config.getList("$path.mobSpawnPoints") as? List<Location> ?: emptyList()

            val spawnedMobsList = config.getStringList("$path.spawnedMobs")
            val spawnedMobs = spawnedMobsList.mapNotNull {
                val parts = it.split("|")
                if (parts.size != 2) return@mapNotNull null
                val locParts = parts[0].split(",")
                if (locParts.size != 6) return@mapNotNull null
                
                val world = Bukkit.getWorld(locParts[0]) ?: return@mapNotNull null
                val loc = Location(world, locParts[1].toDouble(), locParts[2].toDouble(), locParts[3].toDouble(), locParts[4].toFloat(), locParts[5].toFloat())
                val uuid = UUID.fromString(parts[1])
                loc to uuid
            }.toMap().toMutableMap()

            val session = DungeonSession(
                uuid, tier, themeName, durationSeconds,
                collectedSprouts, totalSprouts, elapsedMillis,
                System.currentTimeMillis(), // ロード時点を lastUpdate に
                startLocation, tileSize, gridWidth, gridLength, worldName,
                minibossMarkers = minibossMarkers,
                minibossTriggered = minibossTriggered,
                mobSpawnPoints = mobSpawnPoints,
                spawnedMobs = spawnedMobs,
                restCells = restCells,
                isMultiplayer = isMultiplayer,
                isDown = isDown
            )
            sessions[uuid] = session
            
            // サーバー再起動時にワールドをロードする必要がある
            worldName?.let {
                if (Bukkit.getWorld(it) == null) {
                    DungeonManager.loadDungeonWorld(it, themeName)
    }

    fun removeSessionFromFile(plugin: JavaPlugin, playerUUID: UUID) {
        val file = File(plugin.dataFolder, "sessions.yml")
        if (!file.exists()) return
        val config = YamlConfiguration.loadConfiguration(file)
        config.set("sessions.$playerUUID", null)
        config.save(file)
    }
}
        }
        // file.delete() // ロード後にファイルを削除（重複ロード防止）
        plugin.logger.info("Loaded ${sessions.size} dungeon sessions.")
    }
}
