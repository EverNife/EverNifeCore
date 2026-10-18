package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.time.FCTimeFrame;

import java.time.Duration;
import java.util.Objects;

/**
 * An amount applied once per interval - {@code 2 every 5s}. The amount is always positive: what it does (heal, harm)
 * is the flag's name to tell. The interval is at least {@link #MIN_INTERVAL}, the period the region tick runs at.
 */
public final class AmountEvery {

    public static final Duration MIN_INTERVAL = Duration.ofSeconds(1);

    private final int amount;
    private final Duration interval;

    private AmountEvery(int amount, Duration interval) {
        this.amount = amount;
        this.interval = interval;
    }

    /** @throws IllegalArgumentException when {@code amount} is not above zero or {@code interval} is under {@link #MIN_INTERVAL} */
    public static AmountEvery of(int amount, Duration interval) {
        if (amount <= 0) {
            throw new IllegalArgumentException("The amount " + amount + " must be above zero: the flag's name says whether it adds or takes.");
        }
        if (interval.compareTo(MIN_INTERVAL) < 0) {
            throw new IllegalArgumentException("The interval " + FCTimeFrame.of(interval).toConfigString() + " is under the region tick of 1s:"
                    + " write 1s or more.");
        }
        return new AmountEvery(amount, interval);
    }

    /** Reads {@code <amount> every <duration>}. */
    public static AmountEvery parse(String text) {
        String[] parts = text.trim().split("\\s+every\\s+", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("expected '<amount> every <duration>'");
        }
        return of(Integer.parseInt(parts[0].trim()), FCTimeFrame.fromConfigString(parts[1]).toDuration());
    }

    public int getAmount() {
        return amount;
    }

    public Duration getInterval() {
        return interval;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AmountEvery && amount == ((AmountEvery) other).amount && interval.equals(((AmountEvery) other).interval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, interval);
    }

    @Override
    public String toString() {
        return amount + " every " + FCTimeFrame.of(interval).toConfigString();
    }
}
