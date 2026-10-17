package br.com.finalcraft.evernifecore.conformance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** The time each tick of a budgeted job took on the world's thread, summed up the way the cost cases report it. */
public final class TickTimes {

    private final List<Long> nanos = new ArrayList<>();

    public synchronized void add(long tickNanos) {
        nanos.add(tickNanos);
    }

    public synchronized int getTicks() {
        return nanos.size();
    }

    public synchronized double getMedianMillis() {
        if (nanos.isEmpty()) return Double.NaN;
        List<Long> sorted = new ArrayList<>(nanos);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        double median = sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
        return median / 1_000_000.0;
    }

    public synchronized double getMaxMillis() {
        return nanos.isEmpty() ? Double.NaN : Collections.max(nanos) / 1_000_000.0;
    }

    /** Every tick's time added up: the work, without the waits between ticks. */
    public synchronized double getWorkMillis() {
        long sum = 0;
        for (long tick : nanos) sum += tick;
        return sum / 1_000_000.0;
    }

    /** The mean of the last {@code ticks} ticks (all of them when there are fewer), in milliseconds. */
    public synchronized double getMeanOfLastMillis(int ticks) {
        if (nanos.isEmpty()) return Double.NaN;
        List<Long> last = nanos.subList(Math.max(0, nanos.size() - ticks), nanos.size());
        long sum = 0;
        for (long tick : last) sum += tick;
        return sum / (double) last.size() / 1_000_000.0;
    }

    /** {@code median=..ms max=..ms ticks=.. work=..ms wall=..ms}, {@code wallMillis} being the job's start to its end. */
    public String describe(double wallMillis) {
        return String.format(Locale.ROOT, "median=%.3fms max=%.3fms ticks=%d work=%.1fms wall=%.0fms", getMedianMillis(), getMaxMillis(),
                getTicks(), getWorkMillis(), wallMillis);
    }
}
