package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECNativeAudience;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A native audience with no native bus behind it: it writes down what the mirror handed it, and its
 * gate is a switch a test flips.
 *
 * <pre>{@code
 * ECEventBus bus = EventBuses.mirroring();
 * RecordingAudience audience = new RecordingAudience();
 * bus.addNativeAudience(audience);
 *
 * bus.post(new MyEvent());
 * assertEquals(1, audience.getDispatched().size());
 * }</pre>
 *
 * <p>It is also the third audience the Forge project will be: registered through the same
 * {@link ECEventBus#addNativeAudience(ECNativeAudience)} as the built-in two, with a
 * {@link #getName()} of its own.</p>
 */
public class RecordingAudience implements ECNativeAudience {

    /** The name of an audience built without one - a test that never asks does not have to name it. */
    public static final String DEFAULT_NAME = "recording";

    private final String name;
    private final List<IECEvent> dispatched = new ArrayList<IECEvent>();
    private boolean hasListeners = true;
    private int gateChecks = 0;

    public RecordingAudience() {
        this(DEFAULT_NAME);
    }

    public RecordingAudience(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean hasListeners(Class<? extends IECEvent> eventType) {
        gateChecks++;
        return hasListeners;
    }

    @Override
    public void dispatch(IECEvent event) {
        dispatched.add(event);
    }

    /** The same string as {@link #name()}, spelled the way the rest of this engine reads. */
    public String getName() {
        return name;
    }

    /** Everything this audience was handed, in the order it arrived. */
    public List<IECEvent> getDispatched() {
        return Collections.unmodifiableList(new ArrayList<IECEvent>(dispatched));
    }

    /** Only the dispatches of {@code eventType} - what a test asserting on one event asks for. */
    public <T extends IECEvent> List<T> getDispatchedOf(Class<T> eventType) {
        List<T> found = new ArrayList<T>();
        for (IECEvent event : dispatched) {
            if (eventType.isInstance(event)) {
                found.add(eventType.cast(event));
            }
        }
        return found;
    }

    /**
     * Closes or opens the gate. Closed is the audience nobody listens to on the native side: the bus
     * asks, hears no, and skips {@link #dispatch(IECEvent)} entirely. Flipping it does not tell the
     * bus by itself - a test that wants the listener watches to notice calls
     * {@link ECEventBus#refreshListenerWatches()} afterwards, exactly as a real native audience does.
     */
    public void setHasListeners(boolean hasListeners) {
        this.hasListeners = hasListeners;
    }

    /** How many times the bus asked the gate - zero is the proof it never even considered this audience. */
    public int getGateChecks() {
        return gateChecks;
    }

    /** Forgets everything recorded so far, gate included, so one test can watch two posts apart. */
    public void reset() {
        dispatched.clear();
        gateChecks = 0;
    }

}
