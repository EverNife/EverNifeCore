package br.com.finalcraft.evernifecore.notify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The steps a notification tries, in order. A step is a set of channels delivered together ({@code title+sound});
 * the {@link Notifier} takes the FIRST step whose channels are all supported on this platform and ignores the rest.
 * A channel is named by its bare name ({@code action-bar}) or its {@code owner:name} key.
 */
public final class NotifyChain {

    private final List<Set<String>> steps;

    private NotifyChain(List<Set<String>> steps) {
        this.steps = steps;
    }

    /**
     * One step per argument, its channels joined by {@code +}: {@code parse("title+sound", "chat+sound", "chat")}.
     *
     * @throws IllegalArgumentException when there is no step, or a step names no channel
     */
    public static NotifyChain parse(String... steps) {
        if (steps == null || steps.length == 0) {
            throw new IllegalArgumentException("A notify chain needs at least one step, like \"chat\" or \"title+sound\".");
        }
        List<Set<String>> parsed = new ArrayList<>();
        for (String step : steps) {
            Set<String> channels = new LinkedHashSet<>();
            for (String channel : (step == null ? "" : step).split("\\+")) {
                String name = channel.trim().toLowerCase(Locale.ROOT);
                if (!name.isEmpty()) {
                    channels.add(name);
                }
            }
            if (channels.isEmpty()) {
                throw new IllegalArgumentException("The notify chain step '" + step + "' names no channel: write one name"
                        + " (\"chat\") or several joined by '+' (\"title+sound\").");
            }
            parsed.add(Collections.unmodifiableSet(channels));
        }
        return new NotifyChain(Collections.unmodifiableList(parsed));
    }

    /** {@link #parse(String...)} over a list, as the settings file holds it. */
    public static NotifyChain parse(List<String> steps) {
        return parse(steps.toArray(new String[0]));
    }

    /** The steps in order, each the set of channel names delivered together. */
    public List<Set<String>> getSteps() {
        return steps;
    }

    /** Every channel name of every step, in order of appearance. */
    public Set<String> getChannelNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Set<String> step : steps) {
            names.addAll(step);
        }
        return names;
    }

    /** {@code [title+sound, chat+sound, chat]} - the form the error messages show. */
    public String describe() {
        List<String> written = new ArrayList<>();
        for (Set<String> step : steps) {
            written.add(String.join("+", step));
        }
        return written.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NotifyChain && ((NotifyChain) other).steps.equals(steps);
    }

    @Override
    public int hashCode() {
        return steps.hashCode();
    }

    @Override
    public String toString() {
        return describe();
    }
}
