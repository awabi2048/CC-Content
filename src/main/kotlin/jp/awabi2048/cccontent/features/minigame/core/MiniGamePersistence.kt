package jp.awabi2048.cccontent.features.minigame.core

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

data class MiniGameGameRecord(
    val game: MiniGameId,
    val type: MiniGameType,
    val result: MiniGameResult,
    val recordedAtMillis: Long
)

data class MiniGameSessionRecord(
    val game: MiniGameId,
    val type: MiniGameType,
    val participantUuids: Set<UUID>,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val reason: MiniGameEndReason?
)

data class MiniGameRecoveryRecord(
    val playerUuid: UUID,
    val snapshot: MiniGamePlayerSnapshot,
    val savedAtMillis: Long
)

/** ゲーム結果、共通セッション、切断復元を別ファイル・別モデルで管理する。 */
class MiniGamePersistence(private val plugin: JavaPlugin) {
    private val root = File(plugin.dataFolder, "data/minigame")
    private val gameFile = File(root, "game-records.yml")
    private val sessionFile = File(root, "session-records.yml")
    private val recoveryFile = File(root, "recovery.yml")
    private val historyStore = MiniGameHistoryStore(gameFile)

    fun initialize() { root.mkdirs() }

    fun saveSessionStarted(session: MiniGameSessionContract) {
        val yaml = YamlConfiguration.loadConfiguration(sessionFile)
        val path = key(session.game)
        yaml.set("$path.world", session.game.worldUuid.toString())
        yaml.set("$path.game", session.game.gameId)
        yaml.set("$path.type", session.type.name)
        yaml.set("$path.participants", session.participantUuids.map(UUID::toString))
        yaml.set("$path.started_at", session.startedAtMillis)
        yaml.set("$path.ended_at", null)
        yaml.set("$path.reason", null)
        yaml.save(sessionFile)
    }

    fun saveSessionEnded(result: MiniGameResult) {
        val yaml = YamlConfiguration.loadConfiguration(sessionFile)
        val path = key(result.game)
        yaml.set("$path.world", result.game.worldUuid.toString())
        yaml.set("$path.game", result.game.gameId)
        yaml.set("$path.type", result.type.name)
        yaml.set("$path.participants", result.entries.map { it.playerUuid.toString() })
        yaml.set("$path.started_at", result.startedAtMillis)
        yaml.set("$path.ended_at", result.endedAtMillis)
        yaml.set("$path.reason", result.reason.name)
        yaml.save(sessionFile)
    }

    fun saveGameResult(result: MiniGameResult) {
        historyStore.append(result)
    }

    fun gameHistory(game: MiniGameId): List<MiniGameHistoryRecord> = historyStore.history(game)

    fun personalBest(game: MiniGameId, playerUuid: UUID): MiniGameRankedRecord? =
        historyStore.personalBest(game, playerUuid)

    fun topRecords(game: MiniGameId, limit: Int): List<MiniGameRankedRecord> =
        historyStore.topRecords(game, limit)

    fun saveRecovery(player: Player, snapshot: MiniGamePlayerSnapshot, savedAtMillis: Long = System.currentTimeMillis()) {
        val yaml = YamlConfiguration.loadConfiguration(recoveryFile)
        val path = player.uniqueId.toString()
        yaml.set("$path.saved_at", savedAtMillis)
        yaml.set("$path.snapshot", snapshot.toMap())
        yaml.save(recoveryFile)
    }

    fun restoreRecovery(player: Player): Boolean {
        val yaml = YamlConfiguration.loadConfiguration(recoveryFile)
        val map = yaml.getConfigurationSection(player.uniqueId.toString())?.getConfigurationSection("snapshot")?.getValues(false)
            ?: return false
        val snapshot = runCatching { MiniGamePlayerSnapshot.fromMap(player.uniqueId, map) }.getOrNull() ?: return false
        snapshot.restore(player)
        yaml.set(player.uniqueId.toString(), null)
        yaml.save(recoveryFile)
        return true
    }

    fun removeRecovery(playerUuid: UUID) {
        val yaml = YamlConfiguration.loadConfiguration(recoveryFile)
        if (yaml.contains(playerUuid.toString())) {
            yaml.set(playerUuid.toString(), null)
            yaml.save(recoveryFile)
        }
    }

    /** 起動前に残ったRUNNING記録をFORCEDとして閉じ、再構築不能なゲームを実行しない。 */
    fun closeInterruptedSessions(nowMillis: Long = System.currentTimeMillis()) {
        val yaml = YamlConfiguration.loadConfiguration(sessionFile)
        yaml.getKeys(false).forEach { key ->
            val section = yaml.getConfigurationSection(key) ?: return@forEach
            if (section.getString("reason") != null) return@forEach
            section.set("ended_at", nowMillis)
            section.set("reason", MiniGameEndReason.FORCED.name)
        }
        yaml.save(sessionFile)
    }

    private fun key(game: MiniGameId) = "${game.worldUuid}.${game.gameId}"
}

private fun Location.toSnapshotMap(): Map<String, Any?> = mapOf(
    "world" to world?.uid?.toString(), "x" to x, "y" to y, "z" to z, "yaw" to yaw, "pitch" to pitch
)

private fun ItemStack.toSnapshotMap(): Map<String, Any?> = serialize()

private fun itemFromSnapshotMap(map: Map<*, *>): ItemStack = ItemStack.deserialize(map.mapKeys { it.key.toString() })

private fun MiniGamePlayerSnapshot.toMap(): Map<String, Any?> = mapOf(
    "location" to location.toSnapshotMap(), "inventory" to inventory.map { it?.toSnapshotMap() },
    "armor" to armor.map { it?.toSnapshotMap() }, "off_hand" to offHand?.toSnapshotMap(),
    "game_mode" to gameMode.name, "health" to health, "food" to foodLevel, "saturation" to saturation,
    "level" to level, "experience" to experience, "allow_flight" to allowFlight, "flying" to flying
)

private fun MiniGamePlayerSnapshot.Companion.fromMap(uuid: UUID, map: Map<*, *>): MiniGamePlayerSnapshot {
    fun section(name: String): Map<*, *> = (map[name] as? Map<*, *>) ?: emptyMap<Any, Any>()
    fun item(value: Any?): ItemStack? = (value as? Map<*, *>)?.let(::itemFromSnapshotMap)
    fun items(name: String) = (map[name] as? List<*>)?.map(::item)?.toTypedArray() ?: emptyArray()
    val loc = section("location")
    val world = (loc["world"] as? String)?.let(UUID::fromString)?.let(Bukkit::getWorld)
        ?: error("recovery world is unavailable")
    return MiniGamePlayerSnapshot(
        uuid, Location(world, (loc["x"] as Number).toDouble(), (loc["y"] as Number).toDouble(), (loc["z"] as Number).toDouble(),
            (loc["yaw"] as Number).toFloat(), (loc["pitch"] as Number).toFloat()), items("inventory"), items("armor"), item(map["off_hand"]),
        org.bukkit.GameMode.valueOf(map["game_mode"] as String), (map["health"] as Number).toDouble(), (map["food"] as Number).toInt(),
        (map["saturation"] as Number).toFloat(), (map["level"] as Number).toInt(), (map["experience"] as Number).toFloat(),
        map["allow_flight"] as Boolean, map["flying"] as Boolean
    )
}
