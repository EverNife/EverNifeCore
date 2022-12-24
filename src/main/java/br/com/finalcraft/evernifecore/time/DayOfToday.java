package br.com.finalcraft.evernifecore.time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;

public class DayOfToday {

    private final ZoneId zoneId;
    private final TimeZone timeZone;

    public DayOfToday(String zoneIdName) {
        this(ZoneId.of(zoneIdName));
    }

    public DayOfToday(ZoneId zoneId) {
        this.zoneId = zoneId;
        this.timeZone = TimeZone.getTimeZone(this.zoneId);
    }

    public ZonedDateTime getZonedDateTime(){
        return Instant.ofEpochMilli(System.currentTimeMillis())
                .atZone(this.zoneId);
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public TimeZone getTimeZone(){
        return timeZone;
    }

    public long currentMillisWithOffset(){
        return System.currentTimeMillis() + timeZone.getRawOffset();
    }

    public long getMillisToTomorrow(){
        return 86400000 - (this.currentMillisWithOffset() % 86400000);// (1000*60*60*24) = 86400000;
    }

    public FCTimeFrame getTimeToTomorrow(){
        return FCTimeFrame.of(this.getMillisToTomorrow());
    }

    /**
     * > It returns the time of the day (in millis) of the last daily-cycle
     *
     * @param timeOfTheDay The time of the day in milliseconds. For example, if you want to get the last time the daily
     * cycle started at 13:00, you would pass 46800000L. Use {@link br.com.finalcraft.evernifecore.util.FCTimeUtil}
     * to help on conversions.
     *
     * @return The time of the day in millis.
     */
    public long getLastDailyCycleStartTime(long timeOfTheDay){
        long millisOfRightNow = this.currentMillisWithOffset(); // millis with offset
        long millisOfToday = millisOfRightNow % 86400000L; //Time passed today
        long midNightOfTodayAtGMT0 = System.currentTimeMillis() - millisOfToday; //Midnight of today at GMT0 + (diff with offset)
        long timeToGoBackOrForward = (millisOfToday > timeOfTheDay
                ? timeOfTheDay
                : timeOfTheDay - 86400000L);
        return midNightOfTodayAtGMT0 + timeToGoBackOrForward;
    }

    public long getNextDailyCycleStartTime(long timeOfTheDay){
        // Last Day Cycle Start Time + 1 Day - millisOfToday
        return (getLastDailyCycleStartTime(timeOfTheDay) + 86400000L) - System.currentTimeMillis();
    }

    // ----------------------------------------------------------------------------------------------------------------//
    //  Static Methods
    // ----------------------------------------------------------------------------------------------------------------//

    private static DayOfToday INSTANCE = new DayOfToday("GMT"); //Populated through reflections at 'ECSettings.initialize()'

    public static DayOfToday getInstance() {
        return INSTANCE;
    }
}
