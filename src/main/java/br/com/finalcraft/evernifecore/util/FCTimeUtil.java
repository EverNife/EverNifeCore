package br.com.finalcraft.evernifecore.util;

public class FCTimeUtil {

    /**
     * Convert a Time String to the amount of milliseconds.
     * These Strings are used for the temporary advancedban punish commands.
     *
     * @param s the time string
     * @return the amount of milliseconds equivalent to the given string
     */
    public static long toMilliSec(String s) {
        // This is not my regex :P | From: http://stackoverflow.com/a/8270824
        // This is not my code  :P | From: https://github.com/DevLeoko/AdvancedBan/blob/master/core/src/main/java/me/leoko/advancedban/manager/TimeManager.java
        String[] sl = s.toLowerCase().split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");

        long i = Long.parseLong(sl[0]);
        switch (sl[1]) {
            case "s":
                return i * 1000;
            case "m":
                return i * 1000 * 60;
            case "h":
                return i * 1000 * 60 * 60;
            case "d":
                return i * 1000 * 60 * 60 * 24;
            case "w":
                return i * 1000 * 60 * 60 * 24 * 7;
            case "mo":
                return i * 1000 * 60 * 60 * 24 * 30;
            default:
                return -1;
        }
    }

}
