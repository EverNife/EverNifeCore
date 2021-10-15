package br.com.finalcraft.evernifecore.util;

import java.util.concurrent.TimeUnit;

public class TimeUtil {

    public static Long toMilliSec(long value, String type){
        switch (type == null ? "" : type.toLowerCase()) {
            case "":
            case "n":
            case "nanos":
                return value;
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

    public static Long toMilliSec(String text) {
        try {
            // Regex Imported From: http://stackoverflow.com/a/8270824
            String[] split = (text + " ").split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
            for (int i = 0; i < split.length; i++) {
                split[i] = split[i].trim();
            }

            long totalValue = 0;

            for (int i = 0; i < split.length; i+= 2) {
                Long time = Long.parseLong(split[i]);
                String type = split[i + 1];

                totalValue += toMilliSec(time, type);
            }

            return totalValue;
        } catch (Exception ignored) {

        }
        return null;
    }

}
