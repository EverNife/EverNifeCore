package br.com.finalcraft.evernifecore.region.flag.core;

import java.util.Objects;

/** The value of {@link TimeLockFlag}: which time of day the region's visitors see, and whether it keeps running from there. */
public final class TimeLock {

    /** The named times a region can lock its visitors' clock to, in Minecraft day ticks. */
    public enum TimeOfDay {
        DAY(1000), NOON(6000), NIGHT(13000), MIDNIGHT(18000);

        private final long ticks;

        TimeOfDay(long ticks) {
            this.ticks = ticks;
        }

        public long getTicks() {
            return ticks;
        }
    }

    private TimeOfDay time = TimeOfDay.NIGHT;
    private boolean relative = false;

    public TimeLock() {
    }

    public TimeLock(TimeOfDay time, boolean relative) {
        this.time = time;
        this.relative = relative;
    }

    public TimeOfDay getTime() {
        return time;
    }

    public void setTime(TimeOfDay time) {
        this.time = time;
    }

    public boolean isRelative() {
        return relative;
    }

    public void setRelative(boolean relative) {
        this.relative = relative;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TimeLock && time == ((TimeLock) other).time && relative == ((TimeLock) other).relative;
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, relative);
    }

    @Override
    public String toString() {
        return "time=" + time + " relative=" + relative;
    }
}
