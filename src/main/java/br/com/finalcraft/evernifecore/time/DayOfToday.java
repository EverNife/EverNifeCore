package br.com.finalcraft.evernifecore.time;

import org.jetbrains.annotations.Range;

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

    public long currentTimeMillisWithOffset(){
        return System.currentTimeMillis() + timeZone.getRawOffset();
    }

    public FCTimeFrame getTimeOfTodayWithOffset(){
        return FCTimeFrame.of(this.currentTimeMillisWithOffset());
    }

    public long getMillisToTomorrow(){
        return 86400000 - (this.currentTimeMillisWithOffset() % 86400000);// (1000*60*60*24) = 86400000;
    }

    public FCTimeFrame getTimeToTomorrow(){
        return FCTimeFrame.of(this.getMillisToTomorrow());
    }

    /**
     * It returns the time of the day (in millis) of the last daily-cycle
     *
     * @param cycleReferenceTime The time of the day in milliseconds. For example, if you want to get the last timeframe (millis)
     * it was 13:00 of a day, you would pass 46800000L. Use {@link br.com.finalcraft.evernifecore.util.FCTimeUtil}
     * to help on conversions.
     *
     *  For example:
     *     - if you pass '13:00' and right now is '14:00' it will return "TimeOfRightNow less 1 hour"
     *     - if you pass '13:00' and right now is '08:00' it will return "TimeOfRightNow less 19 hours"
     *
     * @return The time of the day in millis.
     */
    public long getLastDailyCycleStartTime(@Range(from = 0, to = 86400000) long cycleReferenceTime){
        long millisOfRightNow = this.currentTimeMillisWithOffset(); // millis with offset
        long millisOfToday = millisOfRightNow % 86400000L; //Time passed today
        long midNightOfTodayAtGMT0 = System.currentTimeMillis() - millisOfToday; //Midnight of today at GMT0 + (diff with offset)
        long timeToGoBackOrForward = (millisOfToday > cycleReferenceTime
                ? cycleReferenceTime
                : cycleReferenceTime - 86400000L);
        return midNightOfTodayAtGMT0 + timeToGoBackOrForward;
    }

    public long getTimeToNextDailyCycle(@Range(from = 0, to = 86400000)long cycleReferenceTime){
        return (getLastDailyCycleStartTime(cycleReferenceTime) + 86400000L) - System.currentTimeMillis();
    }

    // ----------------------------------------------------------------------------------------------------------------//
    //  Static Methods
    // ----------------------------------------------------------------------------------------------------------------//

    private static DayOfToday INSTANCE = new DayOfToday("GMT"); //Populated through reflections at 'ECSettings.initialize()'

    public static DayOfToday getInstance() {
        return INSTANCE;
    }
}
