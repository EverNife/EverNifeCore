package net.minecraftforge.eventbus.api;

/**
 * A test-only target for a lookup that goes by name.
 *
 * <p>From 1.16.5 on this is the interface every Forge bus implements, and the adapters ask whether a
 * bus they were handed is one - by name, so nothing compiles against it. Proving that the question can
 * answer <em>yes</em> needs a type under this exact name on the test classpath; without one the answer
 * is always no and a typo in the name reads as a pass.</p>
 *
 * <p>It declares nothing. The lookup only ever asks whether a bus is an instance of it, and a bus this
 * suite hands over is a stand-in with no Forge behind it.</p>
 */
public interface IEventBus {

}
