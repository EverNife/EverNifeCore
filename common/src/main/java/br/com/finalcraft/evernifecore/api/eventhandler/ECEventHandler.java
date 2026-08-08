package br.com.finalcraft.evernifecore.api.eventhandler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ECEventHandler {

    /**
     * Define the priority of the event.
     * <p>
     * First priority to the last priority executed:
     * <ol>
     * <li>LOWEST
     * <li>LOW
     * <li>NORMAL
     * <li>HIGH
     * <li>HIGHEST
     * <li>MONITOR
     * </ol>
     *
     * @return the priority
     */
    ECEventPriority priority() default ECEventPriority.NORMAL;

    /**
     * This allow for a more fine-tunning compared ot the ECEventPriority enum
     * whem -1 means the norma priority enum will be used.
     * @return
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
