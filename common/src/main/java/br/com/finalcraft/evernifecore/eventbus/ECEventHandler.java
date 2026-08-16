package br.com.finalcraft.evernifecore.eventbus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a listener method as an event handler. The single parameter decides where it is registered:
 * an {@code IECEvent} goes to the {@link ECEventBus}, a platform's own event type to that platform's
 * native bus.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ECEventHandler {

    /**
     * The priority of this handler, lowest value first:
     * {@link ECEventPriority#FIRST} -&gt; {@link ECEventPriority#EARLY} -&gt;
     * {@link ECEventPriority#NORMAL} -&gt; {@link ECEventPriority#LATE} -&gt;
     * {@link ECEventPriority#LAST}.
     */
    ECEventPriority priority() default ECEventPriority.NORMAL;

    /**
     * A priority between the {@link ECEventPriority} steps, for the rare handler that has to sit
     * right before or after another one. {@code -1} means the {@link #priority()} enum is used.
     */
    short priorityValue() default -1;

    /**
     * Whether this handler steps aside once the event is cancelled. It only decides something for an
     * event that can be cancelled at all - an {@code ECCancellable} on the bus, or a cancellable
     * event of the platform's own; anything else is delivered either way.
     */
    boolean ignoreCancelled() default false;

    /**
     * Whether this handler hears the parameter's class only, and no subtype of it. On the bus and on
     * the Bukkit route a handler hears the whole subtree by default; on the Hytale route delivery is
     * by exact class already, so this changes nothing there.
     */
    boolean exact() default false;
}
