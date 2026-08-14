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
     * Define if the handler ignores a cancelled event.
     * <p>
     * On a platform event that can be cancelled, a true value keeps the method from being called once
     * something else cancelled it. On an {@code IECEvent} it decides nothing: that marker carries no
     * cancellation contract, so the handler is called either way.
     *
     * @return whether cancelled events should be ignored
     */
    boolean ignoreCancelled() default false;
}
