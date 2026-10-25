package br.com.finalcraft.evernifecore.time;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A recurring boundary in a time zone - daily, weekly, monthly, a 5-field cron - or a one-off {@link Season}. A counter
 * that resets "every day at 04:00" asks {@link #currentStart} which window it is in. Every computation runs on
 * {@link ZonedDateTime}, so daylight saving is honored: a boundary at a wall time that does not exist that day (the
 * spring gap) moves forward by the gap, and one at a wall time that happens twice (the autumn overlap) takes the first.
 */
public abstract class Window {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter TIME2 = DateTimeFormatter.ofPattern("HH:mm");
    private static final String FORMS = "Write \"daily at 04:00\", \"weekly on monday at 18:00\", \"monthly on 1 at 04:00\" or"
            + " \"cron 0 4 * * 1\", optionally ending in \"zone Europe/Lisbon\"; a season is {season: {id, from, to, zone}}.";

    private final ZoneId zone;
    private final String id;

    private Window(ZoneId zone, String id) {
        this.zone = zone;
        this.id = id;
    }

    /** The zone EverNifeCore's config names ({@code Settings.Time.ZONE_ID_OF_DAY_OF_TODAY}); the system's when unset or unreadable. */
    public static ZoneId getServerZone() {
        String configured = ECSettings.ZONE_ID_OF_DAY_OF_TODAY;
        if (configured != null) {
            try {
                return ZoneId.of(configured);
            } catch (DateTimeException unreadable) {
                //the config value is the admin's; a bad one falls back like an unset one
            }
        }
        return ZoneId.systemDefault();
    }

    /** Every day at {@code at}. */
    public static Window daily(LocalTime at, ZoneId zone) {
        return new Periodic(zone, "daily@" + at.format(TIME2), at) {
            @Override
            LocalDate latestOnOrBefore(LocalDate date) {
                return date;
            }

            @Override
            LocalDate step(LocalDate boundary, int direction) {
                return boundary.plusDays(direction);
            }
        };
    }

    /** Every week on {@code on} at {@code at}. */
    public static Window weekly(DayOfWeek on, LocalTime at, ZoneId zone) {
        return new Periodic(zone, "weekly@" + on.name().toLowerCase(Locale.ROOT) + "@" + at.format(TIME2), at) {
            @Override
            LocalDate latestOnOrBefore(LocalDate date) {
                return date.with(TemporalAdjusters.previousOrSame(on));
            }

            @Override
            LocalDate step(LocalDate boundary, int direction) {
                return boundary.plusWeeks(direction);
            }
        };
    }

    /**
     * Every month on {@code dayOfMonth} at {@code at}; 29 to 31 fall on the month's last day when it is shorter.
     *
     * @throws IllegalArgumentException when the day is outside 1..31
     */
    public static Window monthly(int dayOfMonth, LocalTime at, ZoneId zone) {
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new IllegalArgumentException("A monthly window falls on a day 1 to 31 (29-31 meaning the last day of a shorter month),"
                    + " not " + dayOfMonth + ".");
        }
        return new Periodic(zone, "monthly@" + dayOfMonth + "@" + at.format(TIME2), at) {
            private LocalDate in(YearMonth month) {
                return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
            }

            @Override
            LocalDate latestOnOrBefore(LocalDate date) {
                LocalDate thisMonth = in(YearMonth.from(date));
                return thisMonth.isAfter(date) ? in(YearMonth.from(date).minusMonths(1)) : thisMonth;
            }

            @Override
            LocalDate step(LocalDate boundary, int direction) {
                return in(YearMonth.from(boundary).plusMonths(direction));
            }
        };
    }

    /**
     * A boundary at every minute the 5 fields match - minute, hour, day of month, month, day of week - with {@code *},
     * {@code * /n}, {@code a-b}, {@code a-b/n}, {@code a,b} and month/day names. When both day fields are restricted,
     * either one matching is enough (the cron rule).
     *
     * @throws IllegalArgumentException when a field does not read, or the fields can never match ({@code 0 4 31 2 *})
     */
    public static Window cron(String fiveFields, ZoneId zone) {
        return new Cron(zone, fiveFields);
    }

    /** Open from the season's start up to its end; its zone is the start's. */
    public static Window season(Season season) {
        return new SeasonWindow(season);
    }

    /**
     * Reads the text form: {@code daily at 04:00}, {@code weekly on monday at 18:00}, {@code monthly on 1 at 04:00},
     * {@code cron 0 4 * * 1}, each optionally followed by {@code zone <ZoneId>}; without one it is in {@code defaultZone}.
     * {@code at} may be left out (midnight).
     *
     * @throws IllegalArgumentException naming what did not read and the accepted forms
     */
    public static Window parse(String text, ZoneId defaultZone) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("A window cannot be empty. " + FORMS);
        }
        List<String> tokens = new ArrayList<>(Arrays.asList(text.trim().split("\\s+")));
        ZoneId zone = defaultZone;
        int zoneAt = indexOfIgnoringCase(tokens, "zone");
        if (zoneAt >= 0) {
            if (zoneAt != tokens.size() - 2) {
                throw new IllegalArgumentException("Window \"" + text + "\": \"zone\" comes last, followed by one zone id, e.g."
                        + " \"daily at 04:00 zone Europe/Lisbon\".");
            }
            try {
                zone = ZoneId.of(tokens.get(zoneAt + 1));
            } catch (DateTimeException unknown) {
                throw new IllegalArgumentException("Window \"" + text + "\": '" + tokens.get(zoneAt + 1) + "' is not a time zone. Use"
                        + " a region id like Europe/Lisbon or America/Sao_Paulo, or an offset like UTC-03:00.");
            }
            tokens = tokens.subList(0, zoneAt);
        }
        String kind = tokens.get(0).toLowerCase(Locale.ROOT);
        try {
            switch (kind) {
                case "daily":
                    return daily(timeAfter(tokens, 1, text), zone);
                case "weekly":
                    requireWord(tokens, 1, "on", text);
                    return weekly(dayOfWeek(tokens, text), timeAfter(tokens, 3, text), zone);
                case "monthly":
                    requireWord(tokens, 1, "on", text);
                    return monthly(Integer.parseInt(tokens.get(2)), timeAfter(tokens, 3, text), zone);
                case "cron":
                    if (tokens.size() != 6) {
                        throw new IllegalArgumentException("Window \"" + text + "\": a cron has 5 fields (minute hour day-of-month"
                                + " month day-of-week), e.g. \"cron 0 4 * * 1\".");
                    }
                    return cron(String.join(" ", tokens.subList(1, 6)), zone);
                default:
                    throw new IllegalArgumentException("Window \"" + text + "\" does not start with daily, weekly, monthly or cron. " + FORMS);
            }
        } catch (IndexOutOfBoundsException | NumberFormatException incomplete) {
            throw new IllegalArgumentException("Window \"" + text + "\" does not read. " + FORMS);
        }
    }

    /**
     * Reads a window node: the text form ({@link #parse(String, ZoneId)}), or {@code {season: {id, from, to, zone}}}
     * where {@code from}/{@code to} are {@code 2026-12-01} or {@code 2026-12-01T18:00} in that zone.
     *
     * @throws IllegalArgumentException naming what did not read and the accepted forms
     */
    public static Window parse(JsonNode node, ZoneId defaultZone) {
        if (node != null && node.isTextual()) {
            return parse(node.asText(), defaultZone);
        }
        JsonNode season = node == null ? null : node.get("season");
        if (season == null || !season.isObject()) {
            throw new IllegalArgumentException("A window is a text or a season node, not " + node + ". " + FORMS);
        }
        ZoneId zone = defaultZone;
        if (season.hasNonNull("zone")) {
            try {
                zone = ZoneId.of(season.get("zone").asText());
            } catch (DateTimeException unknown) {
                throw new IllegalArgumentException("Season zone '" + season.get("zone").asText() + "' is not a time zone, e.g."
                        + " Europe/Lisbon.");
            }
        }
        String seasonId = season.path("id").asText("");
        return season(Season.of(seasonId, seasonInstant(season, "from", zone), seasonInstant(season, "to", zone)));
    }

    /** The latest boundary not after {@code now}; a season answers its start, or null before it. */
    public abstract @Nullable Instant currentStart(Instant now);

    /** The first boundary strictly after {@code now}; a season answers its start before it, its end during it, null after it. */
    public abstract @Nullable Instant nextBoundary(Instant now);

    /** Always true, but for a season outside {@code [from, to)}. */
    public boolean isOpen(Instant now) {
        return true;
    }

    public ZoneId getZone() {
        return zone;
    }

    /** {@code daily@04:00}, {@code weekly@monday@18:00}, {@code monthly@1@04:00}, {@code cron@0 4 * * 1}, {@code season:winter-2026}. */
    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Window && id.equals(((Window) other).id) && zone.equals(((Window) other).zone);
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + zone.hashCode();
    }

    @Override
    public String toString() {
        return id + " (" + zone + ")";
    }

    /** The instant of {@code wallTime} in this window's zone: forward by the gap in a gap, the first one in an overlap. */
    final Instant at(LocalDateTime wallTime) {
        return ZonedDateTime.of(wallTime, zone).withEarlierOffsetAtOverlap().toInstant();
    }

    final LocalDateTime local(Instant now) {
        return LocalDateTime.ofInstant(now, zone);
    }


    // ------------------------------------------------------------------
    //  daily / weekly / monthly
    // ------------------------------------------------------------------

    private abstract static class Periodic extends Window {
        private final LocalTime at;

        Periodic(ZoneId zone, String id, LocalTime at) {
            super(zone, id);
            this.at = at;
        }

        /** The date of the period boundary on or before {@code date}, ignoring the time of day. */
        abstract LocalDate latestOnOrBefore(LocalDate date);

        /** The boundary date one period before (-1) or after (+1). */
        abstract LocalDate step(LocalDate boundary, int direction);

        @Override
        public Instant currentStart(Instant now) {
            LocalDate boundary = latestOnOrBefore(local(now).toLocalDate());
            Instant start = at(boundary.atTime(at));
            //today's boundary may still be ahead of now; a period is at least a day, so one step back is enough
            return start.isAfter(now) ? at(step(boundary, -1).atTime(at)) : start;
        }

        @Override
        public Instant nextBoundary(Instant now) {
            LocalDate boundary = latestOnOrBefore(local(now).toLocalDate());
            Instant next = at(boundary.atTime(at));
            while (!next.isAfter(now)) {
                boundary = step(boundary, 1);
                next = at(boundary.atTime(at));
            }
            return next;
        }
    }

    // ------------------------------------------------------------------
    //  cron
    // ------------------------------------------------------------------

    private static final class Cron extends Window {
        private static final String[] MONTHS = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        private static final String[] DAYS = {"sun", "mon", "tue", "wed", "thu", "fri", "sat"};
        /** How far a search runs before giving up: a cron that matched or matches only further away answers null. */
        private static final int SEARCH_DAYS = 366;

        private final boolean[] minutes;
        private final boolean[] hours;
        private final boolean[] daysOfMonth;
        private final boolean[] months;
        private final boolean[] daysOfWeek;
        private final boolean anyDayOfMonth;
        private final boolean anyDayOfWeek;

        Cron(ZoneId zone, String expression) {
            super(zone, "cron@" + expression.trim().replaceAll("\\s+", " "));
            String[] fields = expression.trim().split("\\s+");
            if (fields.length != 5) {
                throw new IllegalArgumentException("Cron \"" + expression + "\" has " + fields.length + " fields: write 5 (minute hour"
                        + " day-of-month month day-of-week), e.g. \"0 4 * * 1\".");
            }
            minutes = field(expression, "minute", fields[0], 0, 59, null);
            hours = field(expression, "hour", fields[1], 0, 23, null);
            daysOfMonth = field(expression, "day of month", fields[2], 1, 31, null);
            months = field(expression, "month", fields[3], 1, 12, MONTHS);
            boolean[] week = field(expression, "day of week", fields[4], 0, 7, DAYS);
            week[0] |= week[7]; //7 is Sunday too
            daysOfWeek = Arrays.copyOf(week, 7);
            anyDayOfMonth = fields[2].equals("*");
            anyDayOfWeek = fields[4].equals("*");
            requireReachable(expression);
        }

        private void requireReachable(String expression) {
            if (anyDayOfMonth || !anyDayOfWeek) {
                return; //every month has every weekday, so a day-of-week match always comes
            }
            List<String> allowedMonths = new ArrayList<>();
            for (int month = 1; month <= 12; month++) {
                if (!months[month]) {
                    continue;
                }
                allowedMonths.add(Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH));
                for (int day = 1; day <= Month.of(month).maxLength(); day++) {
                    if (daysOfMonth[day]) {
                        return;
                    }
                }
            }
            List<String> allowedDays = new ArrayList<>();
            for (int day = 1; day <= 31; day++) {
                if (daysOfMonth[day]) {
                    allowedDays.add(String.valueOf(day));
                }
            }
            throw new IllegalArgumentException("Cron \"" + expression + "\" never matches: " + String.join(", ", allowedMonths)
                    + " has no day " + String.join(", ", allowedDays) + ". Change the day of month or the month.");
        }

        private boolean dayMatches(LocalDate date) {
            if (!months[date.getMonthValue()]) {
                return false;
            }
            boolean dom = daysOfMonth[date.getDayOfMonth()];
            boolean dow = daysOfWeek[date.getDayOfWeek().getValue() % 7];
            if (anyDayOfMonth && anyDayOfWeek) {
                return true;
            }
            if (anyDayOfMonth) {
                return dow;
            }
            if (anyDayOfWeek) {
                return dom;
            }
            return dom || dow;
        }

        // ponytail: walks minute by minute (skipping whole days and hours that cannot match) for up to a year;
        // a yearly-or-rarer cron answers null - jump field by field if one ever needs to look further
        @Override
        public @Nullable Instant currentStart(Instant now) {
            LocalDateTime time = local(now).truncatedTo(ChronoUnit.MINUTES);
            LocalDateTime limit = time.minusDays(SEARCH_DAYS);
            while (!time.isBefore(limit)) {
                if (!dayMatches(time.toLocalDate())) {
                    time = time.toLocalDate().atStartOfDay().minusMinutes(1);
                } else if (!hours[time.getHour()]) {
                    time = time.truncatedTo(ChronoUnit.HOURS).minusMinutes(1);
                } else {
                    if (minutes[time.getMinute()]) {
                        Instant boundary = at(time);
                        if (!boundary.isAfter(now)) {
                            return boundary;
                        }
                    }
                    time = time.minusMinutes(1);
                }
            }
            return null;
        }

        @Override
        public @Nullable Instant nextBoundary(Instant now) {
            LocalDateTime time = local(now).truncatedTo(ChronoUnit.MINUTES);
            LocalDateTime limit = time.plusDays(SEARCH_DAYS);
            while (!time.isAfter(limit)) {
                if (!dayMatches(time.toLocalDate())) {
                    time = time.toLocalDate().plusDays(1).atStartOfDay();
                } else if (!hours[time.getHour()]) {
                    time = time.truncatedTo(ChronoUnit.HOURS).plusHours(1);
                } else {
                    if (minutes[time.getMinute()]) {
                        Instant boundary = at(time);
                        if (boundary.isAfter(now)) {
                            return boundary;
                        }
                    }
                    time = time.plusMinutes(1);
                }
            }
            return null;
        }

        private static boolean[] field(String expression, String name, String text, int min, int max, @Nullable String[] names) {
            boolean[] allowed = new boolean[max + 1];
            for (String part : text.split(",")) {
                try {
                    int step = 1;
                    String range = part;
                    int slash = part.indexOf('/');
                    if (slash >= 0) {
                        step = Integer.parseInt(part.substring(slash + 1));
                        range = part.substring(0, slash);
                        if (step < 1) {
                            throw new NumberFormatException();
                        }
                    }
                    int from;
                    int to;
                    if (range.equals("*")) {
                        from = min;
                        to = max;
                    } else {
                        int dash = range.indexOf('-');
                        from = value(range.substring(0, dash < 0 ? range.length() : dash), names, min);
                        to = dash < 0 ? (slash >= 0 ? max : from) : value(range.substring(dash + 1), names, min);
                    }
                    if (from < min || to > max || from > to) {
                        throw new NumberFormatException();
                    }
                    for (int value = from; value <= to; value += step) {
                        allowed[value] = true;
                    }
                } catch (NumberFormatException | StringIndexOutOfBoundsException unreadable) {
                    throw new IllegalArgumentException("Cron \"" + expression + "\": the " + name + " field '" + text + "' does not read."
                            + " It takes " + min + "-" + max + (names == null ? "" : " or names like " + names[0] + "-" + names[names.length - 1])
                            + ", written as *, */n, a-b, a-b/n or a,b.");
                }
            }
            return allowed;
        }

        private static int value(String text, @Nullable String[] names, int min) {
            if (names != null) {
                for (int index = 0; index < names.length; index++) {
                    if (names[index].equalsIgnoreCase(text)) {
                        return index + (min == 1 ? 1 : 0);
                    }
                }
            }
            return Integer.parseInt(text);
        }
    }

    // ------------------------------------------------------------------
    //  season
    // ------------------------------------------------------------------

    private static final class SeasonWindow extends Window {
        private final Season season;

        SeasonWindow(Season season) {
            super(season.getFrom().getZone(), "season:" + season.getId());
            this.season = season;
        }

        @Override
        public @Nullable Instant currentStart(Instant now) {
            Instant from = season.getFrom().toInstant();
            return now.isBefore(from) ? null : from;
        }

        @Override
        public @Nullable Instant nextBoundary(Instant now) {
            Instant from = season.getFrom().toInstant();
            Instant to = season.getTo().toInstant();
            return now.isBefore(from) ? from : now.isBefore(to) ? to : null;
        }

        @Override
        public boolean isOpen(Instant now) {
            return season.contains(now);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SeasonWindow && season.equals(((SeasonWindow) other).season);
        }

        @Override
        public int hashCode() {
            return season.hashCode();
        }
    }

    // ------------------------------------------------------------------
    //  text parsing
    // ------------------------------------------------------------------

    private static LocalTime timeAfter(List<String> tokens, int index, String text) {
        if (tokens.size() == index) {
            return LocalTime.MIDNIGHT;
        }
        requireWord(tokens, index, "at", text);
        if (tokens.size() != index + 2) {
            throw new IllegalArgumentException("Window \"" + text + "\" does not read. " + FORMS);
        }
        try {
            return LocalTime.parse(tokens.get(index + 1), TIME);
        } catch (DateTimeParseException unreadable) {
            throw new IllegalArgumentException("Window \"" + text + "\": '" + tokens.get(index + 1) + "' is not a time of day; write"
                    + " it as HH:mm, e.g. 04:00 or 18:30.");
        }
    }

    private static DayOfWeek dayOfWeek(List<String> tokens, String text) {
        String day = tokens.get(2).toUpperCase(Locale.ROOT);
        for (DayOfWeek each : DayOfWeek.values()) {
            if (each.name().equals(day) || (day.length() == 3 && each.name().startsWith(day))) {
                return each;
            }
        }
        throw new IllegalArgumentException("Window \"" + text + "\": '" + tokens.get(2) + "' is not a day of the week; write monday"
                + " to sunday (or mon to sun).");
    }

    private static void requireWord(List<String> tokens, int index, String word, String text) {
        if (!tokens.get(index).equalsIgnoreCase(word)) {
            throw new IllegalArgumentException("Window \"" + text + "\": expected '" + word + "' where it says '" + tokens.get(index)
                    + "'. " + FORMS);
        }
    }

    private static int indexOfIgnoringCase(List<String> tokens, String word) {
        for (int index = 0; index < tokens.size(); index++) {
            if (tokens.get(index).equalsIgnoreCase(word)) {
                return index;
            }
        }
        return -1;
    }

    private static ZonedDateTime seasonInstant(JsonNode season, String field, ZoneId zone) {
        String text = season.path(field).asText("");
        try {
            return ZonedDateTime.of(LocalDateTime.parse(text), zone).withEarlierOffsetAtOverlap();
        } catch (DateTimeParseException notADateTime) {
            try {
                return LocalDate.parse(text).atStartOfDay(zone);
            } catch (DateTimeParseException notADate) {
                throw new IllegalArgumentException("Season '" + field + "' is '" + text + "': write a date like 2026-12-01 or a date and"
                        + " time like 2026-12-01T18:00.");
            }
        }
    }
}
