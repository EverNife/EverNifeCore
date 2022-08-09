package br.com.finalcraft.evernifecore.time;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;

import java.time.*;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class DayOfToday {

    private static DayOfToday INSTANCE;

    private final ZoneId zoneId;
    private final TimeZone timeZone;
    private final Calendar calendar;

    public DayOfToday(ZoneId zoneId, TimeZone timeZone, Calendar calendar) {
        this.zoneId = zoneId;
        this.timeZone = timeZone;
        this.calendar = calendar;
    }

    public DayOfToday(String zoneIdName) {
        this.zoneId = ZoneId.of(zoneIdName);
        this.timeZone = TimeZone.getTimeZone(zoneId);
        this.calendar = Calendar.getInstance(timeZone);
    }

    public static void initialize(){
        INSTANCE = new DayOfToday(ECSettings.ZONE_ID_OF_DAY_OF_TODAY);
    }

    public static DayOfToday getInstance() {
        return INSTANCE;
    }

    public ZonedDateTime getZonedDateTime(){
        return Instant.ofEpochMilli(System.currentTimeMillis()).atZone(this.zoneId);
    }

    public LocalDate getLocalDate(){
        return getZonedDateTime().toLocalDate();
    }

    public LocalDateTime getLocalDateTime(){
        return getZonedDateTime().toLocalDateTime();
    }

    public TimeZone getTimeZone(){
        return timeZone;
    }

    public Calendar getCalendar(){
        return calendar;
    }

    public long currentTimeMillis(){
        return System.currentTimeMillis() + timeZone.getRawOffset();
    }

    public long getMillisToTomorrow(){
        return 86400000 - (this.currentTimeMillis() % 86400000);// (1000*60*60*24) = 86400000;
    }

    public FCTimeFrame getTimeOfToday(){
        return new FCTimeFrame(this.currentTimeMillis());
    }

    public FCTimeFrame getTimeToTomorrow(){
        return new FCTimeFrame(this.getMillisToTomorrow());
    }

    public Date getDate(){
        return new Date(this.currentTimeMillis());
    }

    public int getDayOfMonth(){
        return getZonedDateTime().getDayOfMonth();
    }

    public int getMonthValue(){
        return getZonedDateTime().getMonthValue();
    }

    public int getYear(){
        return getZonedDateTime().getYear();
    }

}
