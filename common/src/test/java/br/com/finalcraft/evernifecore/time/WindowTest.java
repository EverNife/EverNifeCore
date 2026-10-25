package br.com.finalcraft.evernifecore.time;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Windows in a zone: daily/weekly/monthly/cron boundaries, daylight saving gap and overlap, seasons, the text forms. */
class WindowTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static Instant berlin(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, BERLIN).toInstant();
    }

    private static Instant utc(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, UTC).toInstant();
    }

    @Test
    void dailyStartsAtTodaysBoundaryOnceItPassedAndAtYesterdaysBeforeIt() {
        Window daily = Window.daily(LocalTime.of(4, 0), UTC);

        assertEquals(utc(5, 10, 4, 0), daily.currentStart(utc(5, 10, 4, 0)));
        assertEquals(utc(5, 9, 4, 0), daily.currentStart(utc(5, 10, 3, 59)));
        assertEquals(utc(5, 11, 4, 0), daily.nextBoundary(utc(5, 10, 4, 0)));
        assertEquals(utc(5, 10, 4, 0), daily.nextBoundary(utc(5, 10, 3, 59)));
        assertTrue(daily.isOpen(utc(5, 10, 3, 59)));
    }

    @Test
    void dailyAtAWallTimeTheSpringGapSkipsMovesForwardByTheGap() {
        Window daily = Window.daily(LocalTime.of(2, 30), BERLIN);
        Instant gapDayBoundary = ZonedDateTime.of(2026, 3, 29, 3, 30, 0, 0, BERLIN).toInstant();

        assertEquals(gapDayBoundary, daily.currentStart(berlin(3, 29, 12, 0)));
        assertEquals(gapDayBoundary, daily.nextBoundary(berlin(3, 28, 12, 0)));
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), gapDayBoundary);
    }

    @Test
    void dailyAtAWallTimeTheAutumnOverlapRepeatsCountsTheFirstOccurrence() {
        Window daily = Window.daily(LocalTime.of(2, 30), BERLIN);
        Instant first = Instant.parse("2026-10-25T00:30:00Z"); //02:30 CEST; 02:30 CET would be 01:30Z

        assertEquals(first, daily.currentStart(Instant.parse("2026-10-25T12:00:00Z")));
        assertEquals(first, daily.currentStart(Instant.parse("2026-10-25T01:00:00Z"))); //02:00 CET, the second pass
        assertEquals(first, daily.nextBoundary(Instant.parse("2026-10-24T12:00:00Z")));
        assertEquals(Instant.parse("2026-10-26T01:30:00Z"), daily.nextBoundary(first));
    }

    @Test
    void weeklyFallsOnItsDayOfTheWeek() {
        Window weekly = Window.weekly(DayOfWeek.MONDAY, LocalTime.of(18, 0), UTC);

        //2026-10-24 is a Saturday
        assertEquals(utc(10, 19, 18, 0), weekly.currentStart(utc(10, 24, 12, 0)));
        assertEquals(utc(10, 26, 18, 0), weekly.nextBoundary(utc(10, 24, 12, 0)));
        assertEquals(utc(10, 19, 18, 0), weekly.currentStart(utc(10, 26, 17, 59)));
    }

    @Test
    void monthlyOnThe31stFallsOnTheLastDayOfAShorterMonth() {
        Window monthly = Window.monthly(31, LocalTime.of(4, 0), UTC);

        assertEquals(utc(1, 31, 4, 0), monthly.currentStart(utc(2, 27, 0, 0)));
        assertEquals(utc(2, 28, 4, 0), monthly.nextBoundary(utc(2, 27, 0, 0)));
        assertEquals(utc(3, 31, 4, 0), monthly.nextBoundary(utc(2, 28, 4, 0)));
        assertEquals(utc(4, 30, 4, 0), monthly.currentStart(utc(5, 1, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> Window.monthly(32, LocalTime.MIDNIGHT, UTC));
    }

    @Test
    void cronAtFourOnMondaysMatchesOnlyThen() {
        Window cron = Window.cron("0 4 * * 1", UTC);

        assertEquals(utc(10, 19, 4, 0), cron.currentStart(utc(10, 24, 12, 0)));
        assertEquals(utc(10, 26, 4, 0), cron.nextBoundary(utc(10, 24, 12, 0)));
        assertEquals("cron@0 4 * * 1", cron.getId());
    }

    @Test
    void cronReadsStepsRangesListsAndNames() {
        Window everyQuarterHour = Window.cron("*/15 9-17 * * mon-fri", UTC);
        Window twiceAMonth = Window.cron("30 6 1,15 jan,jul *", UTC);

        assertEquals(utc(10, 23, 17, 45), everyQuarterHour.currentStart(utc(10, 24, 12, 0))); //Saturday: back to Friday's last
        assertEquals(utc(10, 26, 9, 0), everyQuarterHour.nextBoundary(utc(10, 24, 12, 0)));
        assertEquals(utc(7, 15, 6, 30), twiceAMonth.currentStart(utc(10, 24, 12, 0)));
    }

    @Test
    void aCronThatCanNeverMatchIsAParseErrorNamingWhy() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> Window.cron("0 4 31 2 *", UTC));
        IllegalArgumentException badField = assertThrows(IllegalArgumentException.class, () -> Window.cron("0 25 * * *", UTC));

        assertEquals("Cron \"0 4 31 2 *\" never matches: February has no day 31. Change the day of month or the month.", error.getMessage());
        assertTrue(badField.getMessage().contains("the hour field '25' does not read"), badField.getMessage());
    }

    @Test
    void aMonthlyCronFindsItsBoundaryWellUnderFiftyMilliseconds() {
        Window cron = Window.cron("0 4 1 * *", UTC);
        cron.nextBoundary(utc(1, 2, 0, 0)); //warm up the classes

        long started = System.nanoTime();
        Instant next = cron.nextBoundary(utc(1, 2, 0, 0));
        Instant start = cron.currentStart(utc(1, 31, 0, 0));
        long tookMillis = (System.nanoTime() - started) / 1_000_000;

        assertEquals(utc(2, 1, 4, 0), next);
        assertEquals(utc(1, 1, 4, 0), start);
        assertTrue(tookMillis < 50, "took " + tookMillis + " ms");
    }

    @Test
    void aSeasonIsOpenOnlyFromItsStartUpToItsEnd() {
        Season winter = Season.of("winter-2026", ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, UTC), ZonedDateTime.of(2027, 3, 1, 0, 0, 0, 0, UTC));
        Window window = Window.season(winter);

        assertFalse(window.isOpen(utc(11, 30, 23, 59)));
        assertNull(window.currentStart(utc(11, 30, 23, 59)));
        assertEquals(utc(12, 1, 0, 0), window.nextBoundary(utc(11, 30, 23, 59)));
        assertTrue(window.isOpen(utc(12, 1, 0, 0)));
        assertEquals(winter.getTo().toInstant(), window.nextBoundary(utc(12, 25, 0, 0)));
        assertFalse(window.isOpen(winter.getTo().toInstant()));
        assertNull(window.nextBoundary(winter.getTo().toInstant()));
        assertEquals("season:winter-2026", window.getId());
    }

    @Test
    void theTextFormsReadWithAndWithoutAZone() {
        assertEquals(Window.daily(LocalTime.of(4, 0), BERLIN), Window.parse("daily at 04:00", BERLIN));
        assertEquals(Window.daily(LocalTime.MIDNIGHT, BERLIN), Window.parse("daily", BERLIN));
        assertEquals(Window.weekly(DayOfWeek.MONDAY, LocalTime.of(18, 0), ZoneId.of("Europe/Lisbon")),
                Window.parse("weekly on monday at 18:00 zone Europe/Lisbon", BERLIN));
        assertEquals(Window.monthly(1, LocalTime.MIDNIGHT, BERLIN), Window.parse("Monthly on 1", BERLIN));
        assertEquals(Window.cron("0 4 * * 1", UTC), Window.parse("cron 0 4 * * 1 zone UTC", BERLIN));
        assertEquals("daily@04:00", Window.parse("daily at 4:00", BERLIN).getId());
    }

    @Test
    void anUnreadableTextFormTeachesTheAcceptedForms() {
        IllegalArgumentException kind = assertThrows(IllegalArgumentException.class, () -> Window.parse("hourly", UTC));
        IllegalArgumentException zone = assertThrows(IllegalArgumentException.class, () -> Window.parse("daily zone Mars/Base", UTC));
        IllegalArgumentException time = assertThrows(IllegalArgumentException.class, () -> Window.parse("daily at noon", UTC));

        assertTrue(kind.getMessage().contains("\"weekly on monday at 18:00\""), kind.getMessage());
        assertTrue(zone.getMessage().contains("'Mars/Base' is not a time zone"), zone.getMessage());
        assertTrue(time.getMessage().contains("'noon' is not a time of day"), time.getMessage());
    }

    @Test
    void aSeasonNodeReadsItsDatesInItsZone() throws IOException {
        Window window = Window.parse(new ObjectMapper().readTree(
                "{\"season\": {\"id\": \"summer\", \"from\": \"2026-06-01\", \"to\": \"2026-09-01T18:00\", \"zone\": \"Europe/Berlin\"}}"), UTC);

        assertEquals(berlin(6, 1, 0, 0), window.currentStart(berlin(7, 1, 0, 0)));
        assertEquals(berlin(9, 1, 18, 0), window.nextBoundary(berlin(7, 1, 0, 0)));
        assertEquals(BERLIN, window.getZone());
        assertEquals(Window.daily(LocalTime.of(4, 0), UTC), Window.parse(new ObjectMapper().readTree("\"daily at 04:00\""), UTC));
    }
}
