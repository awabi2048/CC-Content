package jp.awabi2048.cccontent.features.arena

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.time.LocalDate
import java.util.UUID

class ArenaHistoryStore(private val file: File) {
    private val records = mutableListOf<ArenaHistoryRecord>()

    fun load() {
        records.clear()
        if (!file.isFile) return
        val yaml = YamlConfiguration.loadConfiguration(file)
        yaml.getMapList("records").forEach { raw ->
            val playerId = UUID.fromString(raw["player"]?.toString() ?: error("history record player is missing"))
            val date = LocalDate.parse(raw["date"]?.toString() ?: error("history record date is missing"))
            val star = (raw["difficulty_star"] as? Number)?.toInt()
                ?: raw["difficulty_star"]?.toString()?.toIntOrNull()
                ?: error("history record difficulty_star is invalid")
            val duration = (raw["duration_seconds"] as? Number)?.toLong()
                ?: raw["duration_seconds"]?.toString()?.toLongOrNull()
                ?: error("history record duration_seconds is invalid")
            require(star > 0 && duration >= 0) { "history record values are invalid" }
            records += ArenaHistoryRecord(playerId, date, star, duration)
        }
    }

    fun all(): List<ArenaHistoryRecord> = records.toList()

    fun add(record: ArenaHistoryRecord) {
        records += record
        save()
    }

    fun save() {
        file.parentFile.mkdirs()
        val yaml = YamlConfiguration()
        yaml.set("records", records.map {
            mapOf(
                "player" to it.playerId.toString(),
                "date" to it.date.toString(),
                "difficulty_star" to it.difficultyStar,
                "duration_seconds" to it.durationSeconds
            )
        })
        yaml.save(file)
    }
}
