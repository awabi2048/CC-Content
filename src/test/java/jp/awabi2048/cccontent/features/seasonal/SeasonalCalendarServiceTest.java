package jp.awabi2048.cccontent.features.seasonal;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeasonalCalendarServiceTest {
    private final ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    private final SeasonalCalendarService calendar = new SeasonalCalendarService(tokyo, Duration.ofDays(7));

    @Test
    void annualRangeHandlesYearBoundaryAndGracePeriod() {
        var event = definition(new AnnualRangeSchedule(
            MonthDay.of(12, 30), LocalTime.NOON,
            MonthDay.of(1, 3), LocalTime.NOON
        ), Duration.ofDays(2), null);

        assertEquals(SeasonalEventState.ACTIVE, state(event, "2027-01-01T10:00:00+09:00"));
        assertEquals(SeasonalEventState.GRACE, state(event, "2027-01-04T10:00:00+09:00"));
        assertEquals(SeasonalEventState.ENDED, state(event, "2027-01-10T10:00:00+09:00"));
    }

    @Test
    void fixedRangeUsesExplicitOffsetAndUpcomingWindow() {
        var event = definition(new FixedRangeSchedule(
            OffsetDateTime.parse("2026-08-08T18:00:00+09:00"),
            OffsetDateTime.parse("2026-08-16T23:59:00+09:00")
        ), Duration.ZERO, null);

        assertEquals(SeasonalEventState.UPCOMING, state(event, "2026-08-03T18:00:00+09:00"));
        assertEquals(SeasonalEventState.ACTIVE, state(event, "2026-08-10T18:00:00+09:00"));
    }

    @Test
    void nthWeekdayAndCycleAreEvaluatedFromOccurrenceYear() {
        var schedule = new NthWeekdaySchedule(
            5, 2, DayOfWeek.SUNDAY, LocalTime.MIDNIGHT, Duration.ofDays(1)
        );
        var evenYears = new SeasonalCycle(2026, 2, Set.of(0));
        var event = definition(schedule, Duration.ZERO, evenYears);

        assertEquals(SeasonalEventState.ACTIVE, state(event, "2026-05-10T10:00:00+09:00"));
        assertEquals(SeasonalEventState.ENDED, state(event, "2027-05-09T10:00:00+09:00"));
    }

    @Test
    void disabledDefinitionAlwaysRemainsDisabled() {
        var event = new SeasonalEventDefinition(
            "disabled_event",
            false,
            "seasonal.disabled.name",
            new AnnualRangeSchedule(
                MonthDay.of(1, 1), LocalTime.MIDNIGHT,
                MonthDay.of(1, 2), LocalTime.MIDNIGHT
            ),
            Duration.ZERO,
            null,
            Set.of(),
            Set.of()
        );

        assertEquals(SeasonalEventState.DISABLED, state(event, "2026-01-01T10:00:00+09:00"));
    }

    private SeasonalEventDefinition definition(
        SeasonalSchedule schedule,
        Duration grace,
        SeasonalCycle cycle
    ) {
        return new SeasonalEventDefinition(
            "test_event",
            true,
            "seasonal.test.name",
            schedule,
            grace,
            cycle,
            Set.of("RESOURCE"),
            Set.of()
        );
    }

    private SeasonalEventState state(SeasonalEventDefinition event, String timestamp) {
        return calendar.evaluate(event, ZonedDateTime.parse(timestamp)).getState();
    }
}
