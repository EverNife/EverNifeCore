package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.time.DayOfToday;

import java.time.Instant;
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

}
