package br.com.finalcraft.evernifecore.time;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;

import java.text.SimpleDateFormat;
import java.time.*;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class DayOfToday {

    private static ZoneId zoneId;
    private static TimeZone timeZone;
    private static Calendar calendar;

    public static void initialize(){
        zoneId = ZoneId.of(ECSettings.ZONE_ID_OF_DAY_OF_TODAY);
        timeZone = TimeZone.getTimeZone(zoneId);
        calendar = Calendar.getInstance(timeZone);
    }

    public static ZoneId getZoneId() {
        return zoneId;
    }

    public static ZonedDateTime getZonedDateTime(){
        return Instant.ofEpochMilli(System.currentTimeMillis()).atZone(getZoneId());
    }

    public static LocalDate getLocalDate(){
        return getZonedDateTime().toLocalDate();
    }

    public static LocalDateTime getLocalDateTime(){
        return getZonedDateTime().toLocalDateTime();
    }

    public static TimeZone getTimeZone(){
        return timeZone;
    }

    public static Calendar getCalendar(){
        return calendar;
    }

    public static long getMillisToTomorrow(){
        return 86400000 - (DayOfToday.currentTimeMillis() % 86400000);// (1000*60*60*24) = 86400000;
    }

    public static FCTimeFrame getTimeToTomorrow(){
        return new FCTimeFrame(DayOfToday.getMillisToTomorrow());
    }

    public static FCTimeFrame getTimeOfToday(){
        return new FCTimeFrame(DayOfToday.currentTimeMillis());
    }

    public static long currentTimeMillis(){
        return System.currentTimeMillis() + timeZone.getRawOffset();
    }

    private static Date getDate(){
        return new Date(DayOfToday.currentTimeMillis());
    }

    public static int getDayOfToday(){
        return getZonedDateTime().getDayOfMonth();
    }

    public static int getMonthOfToday(){
        return getZonedDateTime().getMonthValue();
    }

    public static int getYearOfToday(){
        return getZonedDateTime().getYear();
    }
}
