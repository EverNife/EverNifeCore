package net.neoforged.bus.api;

/**
 * A test-only target for a lookup that goes by name: the interface every NeoForge bus implements.
 *
 * <p>Unlike its Forge counterpart it declares members, because the NeoForge route resolves
 * {@code register} and {@code unregister} on this interface rather than on the class behind a bus.</p>
 */
public interface IEventBus {

    void register(Object listener);

    void unregister(Object listener);

}
