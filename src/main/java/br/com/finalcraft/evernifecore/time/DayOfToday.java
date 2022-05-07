package br.com.finalcraft.evernifecore.time;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;

import java.text.SimpleDateFormat;
import java.time.*;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class DayOfToday {

    private static Calendar calendar;
    private static TimeZone timeZone;
    private static ZoneId zoneId;

    public static ZoneId getZoneId() {
        return zoneId != null ? zoneId : (zoneId = ZoneId.of(ECSettings.ZONE_ID_OF_DAY_OF_TODAY));
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
        return timeZone != null ? timeZone : (timeZone = TimeZone.getTimeZone("America/Sao_Paulo"));
    }
    public static Calendar getCalendar(){
        return calendar != null ? calendar : (calendar = Calendar.getInstance(getTimeZone()));
    }

    public static final long FUSO_BRASIL = -3L;
    public static final long FUSO_BRASIL_MILLIS = 3600000 * FUSO_BRASIL;
    public static SimpleDateFormat sdfDay = new SimpleDateFormat("dd");
    public static SimpleDateFormat sdfMonth = new SimpleDateFormat("mm");
    public static SimpleDateFormat sdfYear = new SimpleDateFormat("yy");

    public static FCTimeFrame getTimeToTomorrow(){
        long timeOfNow = 86400000 - (DayOfToday.currentTimeMillis() % (86400000));// (1000*60*60*24) = 86400000
        return new FCTimeFrame(timeOfNow);
    }

    public static FCTimeFrame getTimeOfToday(){
        return new FCTimeFrame(currentTimeMillis());
    }

    public static String getTimeToTomorrowFormatted(){
        FCTimeFrame fcTimeFrame = getTimeToTomorrow();
        return fcTimeFrame.getFormattedDiscursive();
    }

    public static long currentTimeMillis(){
        return System.currentTimeMillis() + FUSO_BRASIL_MILLIS;        //Menos 3 horas (fuso brasil)
    }

    private static Date getDate(){
        return new Date(currentTimeMillis());
    }

    public static int getDayOfToday(){
        return Integer.parseInt(sdfDay.format(getDate()));
    }

    public static int getMonthOfToday(){
        return Integer.parseInt(sdfMonth.format(getDate()));
    }

    public static int getYearOfToday(){
        return Integer.parseInt(sdfYear.format(getDate()));
    }
}
