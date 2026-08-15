package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.events.base.ECCancellable;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
     * and the other three are how Bukkit finds and fires the listeners of an event.
     */
    public static final Set<String> RESERVED = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList("isAsynchronous", "getEventName", "getHandlers", "callEvent")));

    /**
     * The one Bukkit member an event MAY declare - and only in this shape: Bukkit finds it by name up
     * the hierarchy, invokes it static and casts the value to a HandlerList, so anything else is a
     * listener that fails to register on the server.
     */
    public static final String HANDLER_LIST = "getHandlerList";

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
                        + "getEventName/getHandlers/callEvent are how the server finds and fires "
                        + "listeners. Rename the method.");
                continue;
            }

            if (HANDLER_LIST.equals(name) && !isHandlerListShaped(method)) {
                failures.add(eventType.getName() + " declares '" + name + "' with the wrong shape. Bukkit "
                        + "finds that method by name up the hierarchy, invokes it static and casts the value "
                        + "to a HandlerList - so it has to be public, static, take no parameter and return "
                        + "something. Declare it as 'public static Object getHandlerList() { return "
                        + "ECEvent.getHandlerListOf(" + eventType.getSimpleName() + ".class); }', or do not "
                        + "declare it and share the family list.");
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

    private static boolean isHandlerListShaped(Method method) {
        int modifiers = method.getModifiers();
        return Modifier.isPublic(modifiers)
                && Modifier.isStatic(modifiers)
                && method.getParameterCount() == 0
                && method.getReturnType() != void.class;
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
