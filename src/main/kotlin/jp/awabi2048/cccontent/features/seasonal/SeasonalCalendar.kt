package jp.awabi2048.cccontent.features.seasonal

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

enum class SeasonalEventState {
    UPCOMING,
    ACTIVE,
    GRACE,
    ENDED,
    DISABLED
}

data class SeasonalCycle(
    val baseYear: Int,
    val cycleLength: Int,
    val allowedPhases: Set<Int>
) {
    init {
        require(cycleLength > 0)
        require(allowedPhases.isNotEmpty())
        require(allowedPhases.all { it in 0 until cycleLength })
    }

    fun allows(year: Int): Boolean =
        Math.floorMod(year - baseYear, cycleLength) in allowedPhases
}

data class SeasonalOccurrence(
    val start: ZonedDateTime,
    val end: ZonedDateTime
) {
    init {
        require(end.isAfter(start))
    }
}

sealed interface SeasonalSchedule {
    fun occurrences(referenceYear: Int, zoneId: ZoneId): List<SeasonalOccurrence>
}

data class AnnualRangeSchedule(
    val startMonthDay: MonthDay,
    val startTime: java.time.LocalTime,
    val endMonthDay: MonthDay,
    val endTime: java.time.LocalTime
) : SeasonalSchedule {
    override fun occurrences(referenceYear: Int, zoneId: ZoneId): List<SeasonalOccurrence> =
        ((referenceYear - 1)..(referenceYear + 1)).map { year ->
            val start = startMonthDay.atYear(year).atTime(startTime).atZone(zoneId)
            var end = endMonthDay.atYear(year).atTime(endTime).atZone(zoneId)
            if (!end.isAfter(start)) end = end.plusYears(1)
            SeasonalOccurrence(start, end)
        }
}

data class FixedRangeSchedule(
    val start: OffsetDateTime,
    val end: OffsetDateTime
) : SeasonalSchedule {
    init {
        require(end.isAfter(start))
    }

    override fun occurrences(referenceYear: Int, zoneId: ZoneId): List<SeasonalOccurrence> =
        listOf(SeasonalOccurrence(start.atZoneSameInstant(zoneId), end.atZoneSameInstant(zoneId)))
}

data class NthWeekdaySchedule(
    val month: Int,
    val ordinal: Int,
    val dayOfWeek: DayOfWeek,
    val startTime: java.time.LocalTime,
    val duration: Duration
) : SeasonalSchedule {
    init {
        require(month in 1..12)
        require(ordinal in 1..5)
        require(!duration.isNegative && !duration.isZero)
    }

    override fun occurrences(referenceYear: Int, zoneId: ZoneId): List<SeasonalOccurrence> =
        ((referenceYear - 1)..(referenceYear + 1)).mapNotNull { year ->
            val first = LocalDate.of(year, month, 1)
            val date = first.with(TemporalAdjusters.dayOfWeekInMonth(ordinal, dayOfWeek))
            if (date.monthValue != month) return@mapNotNull null
            val start = LocalDateTime.of(date, startTime).atZone(zoneId)
            SeasonalOccurrence(start, start.plus(duration))
        }
}

data class SeasonalEventDefinition(
    val id: String,
    val enabled: Boolean,
    val displayNameKey: String,
    val schedule: SeasonalSchedule,
    val gracePeriod: Duration,
    val cycle: SeasonalCycle?,
    val worldScopes: Set<String>,
    val requiredFeatures: Set<String>
) {
    init {
        require(id.matches(Regex("[a-z0-9_]+")))
        require(displayNameKey.matches(Regex("[a-z0-9_.-]+")))
        require(!gracePeriod.isNegative)
    }
}

data class SeasonalEventStatus(
    val definition: SeasonalEventDefinition,
    val state: SeasonalEventState,
    val occurrence: SeasonalOccurrence?
)

class SeasonalCalendarService(
    private var zoneId: ZoneId,
    private var upcomingWindow: Duration = Duration.ofDays(7)
) {
    fun reconfigure(zoneId: ZoneId, upcomingWindow: Duration) {
        require(!upcomingWindow.isNegative)
        this.zoneId = zoneId
        this.upcomingWindow = upcomingWindow
    }

    fun evaluate(definition: SeasonalEventDefinition, now: ZonedDateTime): SeasonalEventStatus {
        if (!definition.enabled) {
            return SeasonalEventStatus(definition, SeasonalEventState.DISABLED, null)
        }
        val localNow = now.withZoneSameInstant(zoneId)
        val occurrences = definition.schedule.occurrences(localNow.year, zoneId)
            .filter { occurrence -> definition.cycle?.allows(occurrence.start.year) != false }
            .sortedBy(SeasonalOccurrence::start)
        occurrences.firstOrNull { occurrence ->
            !localNow.isBefore(occurrence.start) && !localNow.isAfter(occurrence.end)
        }?.let { return SeasonalEventStatus(definition, SeasonalEventState.ACTIVE, it) }
        occurrences.firstOrNull { occurrence ->
            localNow.isAfter(occurrence.end) &&
                !localNow.isAfter(occurrence.end.plus(definition.gracePeriod))
        }?.let { return SeasonalEventStatus(definition, SeasonalEventState.GRACE, it) }
        occurrences.firstOrNull { occurrence ->
            localNow.isBefore(occurrence.start) &&
                !localNow.plus(upcomingWindow).isBefore(occurrence.start)
        }?.let { return SeasonalEventStatus(definition, SeasonalEventState.UPCOMING, it) }
        return SeasonalEventStatus(definition, SeasonalEventState.ENDED, occurrences.lastOrNull())
    }

    fun evaluateAll(
        definitions: Collection<SeasonalEventDefinition>,
        now: ZonedDateTime
    ): List<SeasonalEventStatus> = definitions.map { evaluate(it, now) }
}
