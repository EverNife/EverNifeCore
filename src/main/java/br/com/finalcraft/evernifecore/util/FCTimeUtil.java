package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.time.DayOfToday;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class FCTimeUtil {

    public static String getFormatted(long millis){
        return Instant.ofEpochMilli(millis)
                .atZone(DayOfToday.getInstance().getZoneId())
                .format(ECSettings.DATE_FORMAT_WITH_HOURS);
    }

    public static String getFormattedNoHours(long millis){
        return Instant.ofEpochMilli(millis)
                .atZone(DayOfToday.getInstance().getZoneId())
                .format(ECSettings.SIMPLE_DATE_FORMAT);
    }

    /**
     * Convert milliseconds to a readable time string.
     *
     * @param millis the amount of milliseconds
     * @return a readable time string (e.g., "1h 30m 45s")
     */
    public static String fromMillis(long millis) {
        if (millis <= 0) return "0s";
        
        StringBuilder result = new StringBuilder();
        
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        
        if (days > 0) result.append(days).append("d ");
        if (hours > 0) result.append(hours).append("h ");
        if (minutes > 0) result.append(minutes).append("m ");
        if (seconds > 0) result.append(seconds).append("s");
        
        return result.toString().trim();
    }

    /**
     * Convert a Time String to the amount of milliseconds.
     *
     * The text input can be in two formats:
     *  1- HH:mm:ss     --> 20:             : 20 hours
     *  2- HH:mm:ss     --> 20:30           : 20 hours and 30 minutes
     *  3- HH:mm:ss     --> 20:00:30        : 20 hours and 30 seconds
     *  4- XhYmZs       --> 20h30m          : 20 hours and 30 minutes
     *  5- XhYmZs       --> 20              : 20 seconds
     *  6- XhYmZs       --> 20 hours 1 m    : 20 hours and 1 minute
     *
     * @param text the time string
     * @return the amount of milliseconds equivalent to the given string
     */
    public static Long toMillis(String text) {
        try {
            if (text.contains(":")){
                int[] times = new int[]{0,0,0};

                String[] split = text.split(Pattern.quote(":"));
                for (int i = 0; i < split.length; i++) {
                    times[i] = Integer.parseInt(split[i]);
                }

                return TimeUnit.HOURS.toMillis(times[0]) + TimeUnit.MINUTES.toMillis(times[1]) + TimeUnit.SECONDS.toMillis(times[2]);
            }else {
                long totalValue = 0;

                // Regex for: 'How to split a string between letters and digits' | From: http://stackoverflow.com/a/8270824
                String[] split = (text + " ").split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
                for (int i = 0; i < split.length; i+= 2) {
                    Long time = Long.parseLong(split[i].trim());
                    String type = i + 1 < split.length ? split[i + 1].trim() : null;

                    totalValue += toMillis(time, type);
                }

                return totalValue;
            }
        } catch (Exception ignored) {

        }
        return null;
    }

    private static Long toMillis(long value, String type){
        switch (type == null ? "" : type.toLowerCase()) {
            case "n":
            case "nanos":
                return value;
            case "":
            case "s":
            case "seg":
            case "sec":
            case "second":
            case "seconds":
            case "segundo":
            case "segundos":
                return TimeUnit.SECONDS.toMillis(value);
            case "m":
            case "min":
            case "minute":
            case "minutes":
            case "minuto":
            case "minutos":
                return TimeUnit.MINUTES.toMillis(value);
            case "h":
            case "hour":
            case "hours":
            case "hora":
            case "horas":
                return TimeUnit.HOURS.toMillis(value);
            case "d":
            case "day":
            case "days":
            case "dia":
            case "dias":
                return TimeUnit.DAYS.toMillis(value);
            case "w":
            case "week":
            case "weeks":
            case "sem":
            case "semana":
            case "semanas":
                return TimeUnit.DAYS.toMillis(value * 7);
            case "mo":
            case "month":
            case "mes":
            case "mês":
            case "meses":
                return TimeUnit.DAYS.toMillis(value * 30);
            default:
                return null;
        }
    }

    public static final DateTimeFormatter FORMATTER_DEFAULT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public static final DateTimeFormatter FORMATTER_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    public static final DateTimeFormatter FORMATTER_DATETIME_ALT = DateTimeFormatter.ofPattern("yyyy/MM/dd'T'HH:mm");
    public static final DateTimeFormatter FORMATTER_DATETIME_WITH_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    public static final DateTimeFormatter FORMATTER_DATETIME_ALT_WITH_SECONDS = DateTimeFormatter.ofPattern("yyyy/MM/dd'T'HH:mm:ss");
    public static final DateTimeFormatter FORMATTER_DATETIME_REVERSE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    public static final DateTimeFormatter FORMATTER_DATETIME_REVERSE_COMPLETE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    public static final DateTimeFormatter FORMATTER_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    public static final DateTimeFormatter FORMATTER_DATE_ALT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter FORMATTER_DATE_REVERSE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter FORMATTER_DATE_REVERSE_ALT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /**
     * Given a date string, converts to LocalDateTime.
     *
     * The idea if to convert the most usual timeframes
     *
     * Accepted TimeFrames patterns are:
     *   - yyyy-MM-dd'T'HH:mm
     *   - yyyy/MM/dd'T'HH:mm
     *   - dd/MM/yyyy HH:mm
     *   - dd/MM/yyyy HH:mm:ss
     *   - yyyy/MM/dd
     *   - yyyy-MM-dd
     *   - dd/MM/yyyy
     *   - dd-MM-yyyy
     *
     * @param dateString
     * @return LocalDateTime
     */
    public static LocalDateTime universalDateConverter(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }

        try {
            if (dateString.length() <= 10) { //Quando em formato menor, não temos as horas inclusas
                if (dateString.charAt(4) == '/') return LocalDate.parse(dateString, FORMATTER_DATE).atTime(LocalTime.MIN);
                if (dateString.charAt(4) == '-') return LocalDate.parse(dateString, FORMATTER_DATE_ALT).atTime(LocalTime.MIN);
                if (dateString.charAt(2) == '/') return LocalDate.parse(dateString, FORMATTER_DATE_REVERSE).atTime(LocalTime.MIN);
                if (dateString.charAt(2) == '-') return LocalDate.parse(dateString, FORMATTER_DATE_REVERSE_ALT).atTime(LocalTime.MIN);
            }

            if (dateString.length() >= 27 && dateString.length() <= 29) { // yyyy-MM-dd HH:mm:ss.SSSSSSSSS
                return Timestamp.valueOf(dateString).toLocalDateTime(); // e.g., with nanoseconds
            }

            if (dateString.charAt(4) == '/') {
                if (dateString.contains("T")) {
                    if (dateString.length() <= 16) {
                        return LocalDateTime.parse(dateString, FORMATTER_DATETIME_ALT);
                    } else {
                        return LocalDateTime.parse(dateString, FORMATTER_DATETIME_ALT_WITH_SECONDS);
                    }
                } else {
                    return LocalDateTime.parse(dateString, FORMATTER_DEFAULT);
                }
            } else if (dateString.charAt(2) == '/') {
                if (dateString.length() <= 16) {
                    return LocalDateTime.parse(dateString, FORMATTER_DATETIME_REVERSE);
                } else {
                    return LocalDateTime.parse(dateString, FORMATTER_DATETIME_REVERSE_COMPLETE);
                }
            } else {
                if (dateString.contains("T")) {
                    if (dateString.length() <= 16) {
                        return LocalDateTime.parse(dateString, FORMATTER_DATETIME);
                    } else {
                        return LocalDateTime.parse(dateString, FORMATTER_DATETIME_WITH_SECONDS);
                    }
                }
            }

        } catch (Exception ignored) {
            return null;
        }

        return null;
    }

}
