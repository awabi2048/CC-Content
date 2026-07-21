package jp.awabi2048.cccontent.features.fishing

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

data class CatchJournalRecord(
    val recordId: UUID,
    val fishId: String,
    val weightGrams: Int,
    val sizeCm: Int,
    val quality: FishQuality,
    val caughtAtEpochMillis: Long,
    val anglerUuid: UUID,
    val anglerName: String,
    val worldKey: String,
    val biomeKey: String,
    val season: String,
    var gyotakuIssued: Boolean = false
)

class CatchJournalStore(private val file: File) {
    private val records = linkedMapOf<UUID, MutableList<CatchJournalRecord>>()

    init {
        file.parentFile?.mkdirs()
        load()
    }

    @Synchronized
    fun add(record: CatchJournalRecord) {
        val journal = records.getOrPut(record.anglerUuid) { mutableListOf() }
        journal.removeAll { it.recordId == record.recordId }
        journal.add(0, record)
        if (journal.size > MAX_RECENT) journal.subList(MAX_RECENT, journal.size).clear()
        save()
    }

    @Synchronized
    fun recent(playerId: UUID): List<CatchJournalRecord> =
        records[playerId].orEmpty().map(CatchJournalRecord::copy)

    @Synchronized
    fun find(playerId: UUID, recordId: UUID): CatchJournalRecord? =
        records[playerId]?.firstOrNull { it.recordId == recordId }?.copy()

    @Synchronized
    fun markGyotakuIssued(playerId: UUID, recordId: UUID): Boolean {
        val record = records[playerId]?.firstOrNull { it.recordId == recordId } ?: return false
        if (record.gyotakuIssued) return false
        record.gyotakuIssued = true
        save()
        return true
    }

    @Synchronized
    fun save() {
        val output = YamlConfiguration()
        output.set("schema_version", SCHEMA_VERSION)
        records.forEach { (playerId, journal) ->
            output.set("players.$playerId", journal.map { record ->
                linkedMapOf(
                    "record_id" to record.recordId.toString(),
                    "fish_id" to record.fishId,
                    "weight_grams" to record.weightGrams,
                    "size_cm" to record.sizeCm,
                    "quality" to record.quality.id,
                    "caught_at_epoch_millis" to record.caughtAtEpochMillis,
                    "angler_uuid" to record.anglerUuid.toString(),
                    "angler_name" to record.anglerName,
                    "world_key" to record.worldKey,
                    "biome_key" to record.biomeKey,
                    "season" to record.season,
                    "gyotaku_issued" to record.gyotakuIssued
                )
            })
        }
        output.save(file)
    }

    private fun load() {
        if (!file.exists()) return
        val input = YamlConfiguration.loadConfiguration(file)
        require(input.getInt("schema_version") == SCHEMA_VERSION) {
            "data/fishing/catch_journal.yml.schema_version must be $SCHEMA_VERSION"
        }
        input.getConfigurationSection("players")?.getKeys(false)?.forEach { rawPlayerId ->
            val playerId = runCatching { UUID.fromString(rawPlayerId) }.getOrNull() ?: return@forEach
            val loaded = input.getMapList("players.$rawPlayerId").mapNotNull(::decode)
                .distinctBy(CatchJournalRecord::recordId)
                .take(MAX_RECENT)
                .toMutableList()
            if (loaded.isNotEmpty()) records[playerId] = loaded
        }
    }

    private fun decode(raw: Map<*, *>): CatchJournalRecord? = runCatching {
        CatchJournalRecord(
            UUID.fromString(raw["record_id"] as String),
            raw["fish_id"] as String,
            (raw["weight_grams"] as Number).toInt(),
            (raw["size_cm"] as Number).toInt(),
            FishQuality.fromStoredId(raw["quality"] as String),
            (raw["caught_at_epoch_millis"] as Number).toLong(),
            UUID.fromString(raw["angler_uuid"] as String),
            raw["angler_name"] as String,
            raw["world_key"] as String,
            raw["biome_key"] as String,
            raw["season"] as String,
            raw["gyotaku_issued"] as? Boolean ?: false
        )
    }.getOrNull()

    companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_RECENT = 63
    }
}
