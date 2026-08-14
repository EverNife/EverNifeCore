package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What an {@link ECEvent} subtype may not declare. The base is a different class on each platform,
 * and on Bukkit it is {@code org.bukkit.event.Event}, whose plumbing an event of this framework has
 * to leave alone - a clash there is not a compile error, it is a server that fails at classload or an
 * event nobody hears.
 *
 * <p>It reports instead of throwing, like the rest of the conformance suites here: the caller decides
 * whether a violation is an assertion failure or a line in a log.</p>
 */
public final class ECEventConformance {

    /**
     * The members of {@code org.bukkit.event.Event} an EC event must not redeclare. {@code
     * isAsynchronous} is FINAL there - redeclaring it is a VerifyError the moment the class loads -
     * and the other three are how Bukkit finds the listeners of an event.
     */
    public static final Set<String> RESERVED = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList("isAsynchronous", "getEventName", "getHandlers", "getHandlerList", "callEvent")));

    /** Reserved for an event that does NOT implement {@link ECCancellable} - see {@link #check(Class)}. */
    public static final Set<String> CANCELLATION = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList("isCancelled", "setCancelled")));

    private ECEventConformance() {
    }

    /** @return one line per violation, empty when the event type conforms. */
    public static List<String> check(Class<?> eventType) {
        List<String> failures = new ArrayList<String>();

        if (!ECEvent.class.isAssignableFrom(eventType)) {
            failures.add(eventType.getName() + " is no ECEvent subtype, so these names bind nothing on "
                    + "it - check the class you meant, or drop it from the list.");
            return Collections.unmodifiableList(failures);
        }

        boolean cancellable = ECCancellable.class.isAssignableFrom(eventType);

        for (Method method : eventType.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            String name = method.getName();

            if (RESERVED.contains(name)) {
                failures.add(eventType.getName() + " declares '" + name + "', which belongs to the "
                        + "platform base: on Bukkit an ECEvent IS an org.bukkit.event.Event, where "
                        + "isAsynchronous is final (redeclaring it is a VerifyError at classload) and "
                        + "getEventName/getHandlers/getHandlerList/callEvent are how the server finds and "
                        + "fires listeners. Rename the method.");
                continue;
            }

            if (!cancellable && CANCELLATION.contains(name)) {
                failures.add(eventType.getName() + " declares '" + name + "' without implementing "
                        + "ECCancellable, so nothing honours it: the bus only skips a handler for an "
                        + "ECCancellable, and a platform only offers 'cancel' for its own Cancellable. "
                        + "Implement ECCancellable - it extends the platform's - or rename the method.");
            }
        }

        return Collections.unmodifiableList(failures);
    }

    /** {@link #check(Class)} over several types, their violations in one list. */
    public static List<String> checkAll(Class<?>... eventTypes) {
        List<String> failures = new ArrayList<String>();
        for (Class<?> eventType : eventTypes) {
            failures.addAll(check(eventType));
        }
        return Collections.unmodifiableList(failures);
    }

}
