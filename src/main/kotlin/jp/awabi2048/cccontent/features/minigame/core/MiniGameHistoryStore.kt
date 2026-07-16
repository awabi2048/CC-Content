package jp.awabi2048.cccontent.features.minigame.core

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

data class MiniGameHistoryRecord(
    val recordId: String,
    val recordedAtMillis: Long,
    val result: MiniGameResult
)

data class MiniGameRankedRecord(
    val recordedAtMillis: Long,
    val entry: MiniGameResultEntry
)

class MiniGameHistoryStore(
    private val file: File,
    private val historyLimit: Int = 50
) {
    init {
        require(historyLimit > 0) { "historyLimit must be positive" }
    }

    fun append(result: MiniGameResult, recordedAtMillis: Long = System.currentTimeMillis()) {
        file.parentFile?.mkdirs()
        val yaml = YamlConfiguration.loadConfiguration(file)
        val base = basePath(result.game)
        val recordId = "${result.endedAtMillis}-${UUID.randomUUID()}"
        val path = "$base.records.$recordId"
        yaml.set("$path.recorded_at", recordedAtMillis)
        yaml.set("$path.type", result.type.name)
        yaml.set("$path.started_at", result.startedAtMillis)
        yaml.set("$path.ended_at", result.endedAtMillis)
        yaml.set("$path.reason", result.reason.name)
        result.entries.forEach { entry ->
            val entryPath = "$path.entries.${entry.playerUuid}"
            yaml.set("$entryPath.completed", entry.completed)
            yaml.set("$entryPath.elapsed", entry.elapsedMillis)
            yaml.set("$entryPath.rank", entry.rank)
            yaml.set("$entryPath.score", entry.score)
        }
        val records = requireNotNull(yaml.getConfigurationSection("$base.records"))
        records.getKeys(false)
            .map { it to requireLong(records.getConfigurationSection(it), "recorded_at", "$base.records.$it") }
            .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenByDescending { it.first })
            .drop(historyLimit)
            .forEach { (staleId, _) -> records.set(staleId, null) }
        yaml.save(file)
    }

    fun history(game: MiniGameId): List<MiniGameHistoryRecord> {
        if (!file.exists()) return emptyList()
        val yaml = YamlConfiguration.loadConfiguration(file)
        val records = yaml.getConfigurationSection("${basePath(game)}.records") ?: return emptyList()
        return records.getKeys(false).map { id -> parseRecord(game, id, requireSection(records, id, "records")) }
            .sortedWith(compareByDescending<MiniGameHistoryRecord> { it.recordedAtMillis }.thenByDescending { it.recordId })
    }

    fun personalBest(game: MiniGameId, playerUuid: UUID): MiniGameRankedRecord? =
        rankedEntries(game).filter { it.entry.playerUuid == playerUuid }.minWithOrNull(resultComparator())

    fun topRecords(game: MiniGameId, limit: Int): List<MiniGameRankedRecord> {
        require(limit > 0) { "limit must be positive" }
        return rankedEntries(game).sortedWith(resultComparator()).take(limit)
    }

    private fun rankedEntries(game: MiniGameId): List<MiniGameRankedRecord> = history(game).flatMap { record ->
        record.result.entries.filter(MiniGameResultEntry::completed).map { MiniGameRankedRecord(record.recordedAtMillis, it) }
    }

    private fun resultComparator(): Comparator<MiniGameRankedRecord> =
        compareBy<MiniGameRankedRecord> { it.entry.score ?: Int.MAX_VALUE }
            .thenBy { it.entry.elapsedMillis ?: Long.MAX_VALUE }
            .thenByDescending { it.recordedAtMillis }
            .thenBy { it.entry.playerUuid.toString() }

    private fun parseRecord(game: MiniGameId, id: String, section: ConfigurationSection): MiniGameHistoryRecord {
        val source = "${basePath(game)}.records.$id"
        val type = enumValueOf<MiniGameType>(requireString(section, "type", source))
        val startedAt = requireLong(section, "started_at", source)
        val endedAt = requireLong(section, "ended_at", source)
        require(endedAt >= startedAt) { "ended_at precedes started_at: $source" }
        val reason = enumValueOf<MiniGameEndReason>(requireString(section, "reason", source))
        val entriesSection = requireSection(section, "entries", source)
        val entries = entriesSection.getKeys(false).map { playerId ->
            val entry = requireSection(entriesSection, playerId, "$source.entries")
            val playerUuid = UUID.fromString(playerId)
            val completed = requireBoolean(entry, "completed", "$source.entries.$playerId")
            MiniGameResultEntry(
                playerUuid,
                completed,
                optionalLong(entry, "elapsed", "$source.entries.$playerId"),
                optionalInt(entry, "rank", "$source.entries.$playerId"),
                optionalInt(entry, "score", "$source.entries.$playerId")
            ).also {
                require(!completed || (it.elapsedMillis != null && it.rank != null)) {
                    "completed entry requires elapsed and rank: $source.entries.$playerId"
                }
            }
        }
        return MiniGameHistoryRecord(
            id,
            requireLong(section, "recorded_at", source),
            MiniGameResult(game, type, startedAt, endedAt, reason, entries)
        )
    }

    private fun basePath(game: MiniGameId): String = "worlds.${game.worldUuid}.${game.gameId}"

    private fun requireSection(parent: ConfigurationSection?, key: String, source: String): ConfigurationSection =
        requireNotNull(parent?.getConfigurationSection(key)) { "section is required: $source.$key" }

    private fun requireString(section: ConfigurationSection, key: String, source: String): String =
        (section.get(key) as? String)?.takeIf(String::isNotBlank)
            ?: error("non-blank string is required: $source.$key")

    private fun requireLong(section: ConfigurationSection?, key: String, source: String): Long {
        val value = section?.get(key)
        require(value is Number) { "number is required: $source.$key" }
        return value.toLong()
    }

    private fun requireBoolean(section: ConfigurationSection, key: String, source: String): Boolean =
        (section.get(key) as? Boolean) ?: error("boolean is required: $source.$key")

    private fun optionalLong(section: ConfigurationSection, key: String, source: String): Long? {
        val value = section.get(key) ?: return null
        require(value is Number) { "number is required: $source.$key" }
        return value.toLong()
    }

    private fun optionalInt(section: ConfigurationSection, key: String, source: String): Int? {
        val value = section.get(key) ?: return null
        require(value is Int) { "integer is required: $source.$key" }
        return value
    }
}
