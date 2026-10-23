package net.minecraftforge.eventbus.api;

import java.util.function.Consumer;

/**
 * A test-only target for a lookup that goes by name.
 *
 * <p>From 1.16.5 on this is the interface every Forge bus implements, and the adapters ask whether a
 * bus they were handed is one - by name, so nothing compiles against it. Proving that the question can
 * answer <em>yes</em> needs a type under this exact name on the test classpath; without one the answer
 * is always no and a typo in the name reads as a pass.</p>
 *
 * <p>It declares only the member an adapter subscribes through, {@code addListener}, with the real
 * signature; a stand-in bus that does not record subscriptions leaves the default.</p>
 */
public interface IEventBus {

    default <T> void addListener(EventPriority priority, boolean receiveCancelled, Class<T> eventType, Consumer<T> consumer) {
        throw new UnsupportedOperationException("this stand-in bus takes no listener");
    }
}
