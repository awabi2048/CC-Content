package jp.awabi2048.cccontent.features.arena

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.time.LocalDate
import java.util.UUID

class ArenaDailyEntryStore(private val file: File) {
    private val lastEntryDates = mutableMapOf<UUID, LocalDate>()

    fun load() {
        lastEntryDates.clear()
        if (!file.isFile) return
        val yaml = YamlConfiguration.loadConfiguration(file)
        yaml.getConfigurationSection("entries")?.getKeys(false)?.forEach { key ->
            lastEntryDates[UUID.fromString(key)] = LocalDate.parse(yaml.getString("entries.$key"))
        }
    }

    fun tryReserve(playerId: UUID, today: LocalDate): Boolean {
        return tryReserveAll(listOf(playerId), today)
    }

    fun tryReserveAll(playerIds: Collection<UUID>, today: LocalDate): Boolean {
        val uniquePlayerIds = playerIds.toSet()
        if (uniquePlayerIds.any { lastEntryDates[it] == today }) return false
        uniquePlayerIds.forEach { lastEntryDates[it] = today }
        if (uniquePlayerIds.isNotEmpty()) save()
        return true
    }

    fun lastEntryDate(playerId: UUID): LocalDate? = lastEntryDates[playerId]

    fun save() {
        file.parentFile.mkdirs()
        val yaml = YamlConfiguration()
        lastEntryDates.forEach { (player, date) -> yaml.set("entries.$player", date.toString()) }
        yaml.save(file)
    }
}
