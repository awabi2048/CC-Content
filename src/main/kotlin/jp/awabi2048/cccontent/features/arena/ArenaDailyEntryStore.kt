package jp.awabi2048.cccontent.features.arena

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.time.LocalDate
import java.util.UUID

class ArenaDailyEntryReservation internal constructor(
    internal val token: UUID,
    val playerIds: Set<UUID>,
    val date: LocalDate
)

class ArenaDailyEntryStore(private val file: File) {
    private val lastEntryDates = mutableMapOf<UUID, LocalDate>()
    private val pendingByToken = mutableMapOf<UUID, ArenaDailyEntryReservation>()
    private val pendingPlayerIds = mutableSetOf<UUID>()

    fun load() {
        lastEntryDates.clear()
        pendingByToken.clear()
        pendingPlayerIds.clear()
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
        val reservation = beginReservation(playerIds, today) ?: return false
        return try {
            commit(reservation)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 実際のステージ転送が完了するまで、日次参加枠を永続データへ確定しません。 */
    fun beginReservation(playerIds: Collection<UUID>, today: LocalDate): ArenaDailyEntryReservation? {
        val uniquePlayerIds = playerIds.toSet()
        if (uniquePlayerIds.any { lastEntryDates[it] == today || it in pendingPlayerIds }) return null
        val reservation = ArenaDailyEntryReservation(UUID.randomUUID(), uniquePlayerIds, today)
        pendingByToken[reservation.token] = reservation
        pendingPlayerIds.addAll(uniquePlayerIds)
        return reservation
    }

    fun commit(reservation: ArenaDailyEntryReservation) {
        check(pendingByToken[reservation.token] === reservation) { "Daily entry reservation is not active" }
        val previousDates = reservation.playerIds.associateWith { lastEntryDates[it] }
        reservation.playerIds.forEach { lastEntryDates[it] = reservation.date }
        try {
            if (reservation.playerIds.isNotEmpty()) save()
        } catch (error: Exception) {
            previousDates.forEach { (playerId, previousDate) ->
                if (previousDate == null) lastEntryDates.remove(playerId) else lastEntryDates[playerId] = previousDate
            }
            throw error
        } finally {
            releasePending(reservation)
        }
    }

    fun cancel(reservation: ArenaDailyEntryReservation) {
        if (pendingByToken[reservation.token] === reservation) releasePending(reservation)
    }

    fun lastEntryDate(playerId: UUID): LocalDate? = lastEntryDates[playerId]

    fun save() {
        file.parentFile.mkdirs()
        val yaml = YamlConfiguration()
        lastEntryDates.forEach { (player, date) -> yaml.set("entries.$player", date.toString()) }
        ArenaYamlFiles.saveAtomically(file, yaml)
    }

    private fun releasePending(reservation: ArenaDailyEntryReservation) {
        pendingByToken.remove(reservation.token)
        pendingPlayerIds.removeAll(reservation.playerIds)
    }
}
