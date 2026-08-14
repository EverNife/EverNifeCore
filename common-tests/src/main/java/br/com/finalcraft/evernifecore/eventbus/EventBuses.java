package br.com.finalcraft.evernifecore.eventbus;

/**
 * The buses a test runs against.
 *
 * <pre>{@code
 * ECEventBus bus = EventBuses.mirroring();
 * bus.addNativeAudience(new RecordingAudience());
 * }</pre>
 *
 * <p>It lives in the bus's own package because that is the only place the mirroring constructor can
 * be reached from: production hands out exactly two kinds of bus, the global one and the scoped one
 * that never mirrors, and neither of them is what a test of the native phase needs.</p>
 */
public final class EventBuses {

    private EventBuses() {
    }

    /**
     * A bus that mirrors into its audiences without being {@link ECEventBus#global()} - so a test can
     * exercise the native phase and still leave the process-wide bus as it found it.
     */
    public static ECEventBus mirroring() {
        return new ECEventBus(true);
    }

}
