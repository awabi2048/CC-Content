package jp.awabi2048.cccontent.features.arena

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.UUID

/**
 * Arena のクリア履歴を日別ファイルで保持します。
 *
 * 難易度需要の計算に不要な古い日付はロードせず、保存時も当日のファイルだけを書き換えます。
 */
class ArenaHistoryStore(
    private val directory: File,
    private val legacyFile: File? = null
) {
    private val recordsByDate = sortedMapOf<LocalDate, MutableList<ArenaHistoryRecord>>()

    fun load(today: LocalDate, maxAgeDays: Int) {
        require(maxAgeDays > 0) { "maxAgeDays must be positive" }
        migrateLegacyFileOnce()
        recordsByDate.clear()

        repeat(maxAgeDays) { age ->
            val date = today.minusDays(age.toLong())
            val file = fileFor(date)
            if (!file.isFile) return@repeat
            recordsByDate[date] = loadDailyFile(file, date).toMutableList()
        }
    }

    fun all(): List<ArenaHistoryRecord> = recordsByDate.values.flatten()

    /** 1セッション分を日付ごとにまとめ、各日付を1回だけ保存します。 */
    fun addAll(records: Collection<ArenaHistoryRecord>) {
        if (records.isEmpty()) return
        val grouped = records.groupBy(ArenaHistoryRecord::date)
        grouped.forEach { (date, additions) ->
            val dailyRecords = recordsByDate.getOrPut(date) {
                val file = fileFor(date)
                if (file.isFile) loadDailyFile(file, date).toMutableList() else mutableListOf()
            }
            dailyRecords += additions
            saveDailyFile(date, dailyRecords)
        }
    }

    private fun fileFor(date: LocalDate): File = File(directory, "$date.yml")

    private fun loadDailyFile(file: File, expectedDate: LocalDate): List<ArenaHistoryRecord> {
        val yaml = YamlConfiguration.loadConfiguration(file)
        val declaredDate = yaml.getString("date")?.let(LocalDate::parse)
            ?: error("history date is missing: ${file.name}")
        require(declaredDate == expectedDate) {
            "history date does not match file name: file=${file.name} date=$declaredDate"
        }
        return yaml.getMapList("records").map { raw -> parseRecord(raw, expectedDate) }
    }

    private fun saveDailyFile(date: LocalDate, records: Collection<ArenaHistoryRecord>) {
        val yaml = YamlConfiguration()
        yaml.set("date", date.toString())
        yaml.set("records", records.map(::serializeRecord))
        ArenaYamlFiles.saveAtomically(fileFor(date), yaml)
    }

    /**
     * 単一 history.yml は日別ディレクトリがまだ存在しない場合だけ移行します。
     * 日別データと旧データを恒久的に併用せず、移行後は旧ファイルをバックアップ名へ変更します。
     */
    private fun migrateLegacyFileOnce() {
        val source = legacyFile?.takeIf(File::isFile) ?: return
        val existingDailyFiles = directory.listFiles { file -> file.isFile && file.extension.equals("yml", true) }
            .orEmpty()
        if (existingDailyFiles.isNotEmpty()) return

        val legacyRecords = loadLegacyFile(source)
        val temporaryDirectory = File(directory.parentFile, "${directory.name}.migration.tmp")
        deleteRecursivelyIfExists(temporaryDirectory)
        temporaryDirectory.mkdirs()

        try {
            legacyRecords.groupBy(ArenaHistoryRecord::date).forEach { (date, records) ->
                val yaml = YamlConfiguration()
                yaml.set("date", date.toString())
                yaml.set("records", records.map(::serializeRecord))
                ArenaYamlFiles.saveAtomically(File(temporaryDirectory, "$date.yml"), yaml)
            }

            if (directory.exists()) {
                deleteRecursivelyIfExists(directory)
            }
            moveDirectory(temporaryDirectory, directory)
            Files.move(
                source.toPath(),
                File(source.parentFile, "${source.name}.migrated.bak").toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            deleteRecursivelyIfExists(temporaryDirectory)
        }
    }

    private fun loadLegacyFile(file: File): List<ArenaHistoryRecord> {
        val yaml = YamlConfiguration.loadConfiguration(file)
        return yaml.getMapList("records").map { raw ->
            val date = LocalDate.parse(raw["date"]?.toString() ?: error("history record date is missing"))
            parseRecord(raw, date)
        }
    }

    private fun parseRecord(raw: Map<*, *>, date: LocalDate): ArenaHistoryRecord {
        val playerId = UUID.fromString(raw["player"]?.toString() ?: error("history record player is missing"))
        val star = (raw["difficulty_star"] as? Number)?.toInt()
            ?: raw["difficulty_star"]?.toString()?.toIntOrNull()
            ?: error("history record difficulty_star is invalid")
        val duration = (raw["duration_seconds"] as? Number)?.toLong()
            ?: raw["duration_seconds"]?.toString()?.toLongOrNull()
            ?: error("history record duration_seconds is invalid")
        require(star > 0 && duration >= 0) { "history record values are invalid" }
        return ArenaHistoryRecord(playerId, date, star, duration)
    }

    private fun serializeRecord(record: ArenaHistoryRecord): Map<String, Any> = mapOf(
        "player" to record.playerId.toString(),
        "difficulty_star" to record.difficultyStar,
        "duration_seconds" to record.durationSeconds
    )

    private fun deleteRecursivelyIfExists(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) {
            file.listFiles().orEmpty().forEach(::deleteRecursivelyIfExists)
        }
        check(file.delete()) { "failed to delete temporary history path: ${file.absolutePath}" }
    }

    private fun moveDirectory(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath())
        }
    }
}
