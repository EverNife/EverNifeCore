package br.com.finalcraft.evernifecore.eventbus;

/**
 * The buses a test runs against, and the one knob production keeps off the API.
 *
 * <pre>{@code
 * ECEventBus bus = EventBuses.mirroring();
 * bus.addNativeAudience(new RecordingAudience());
 * }</pre>
 *
 * <p>It lives in the bus's own package because that is the only place the mirroring constructor and
 * the exception-handler setter can be reached from: production hands out exactly two kinds of bus,
 * the global one and the scoped one that never mirrors, and neither of them is what a test of the
 * native phase needs.</p>
 */
public final class EventBuses {

    private EventBuses() {
    }

    /**
     * A bus that mirrors into its audiences without being {@link ECEventBus#global()} - so a test can
     * exercise the native phase and still leave the process-wide bus as it found it. Failures are logged.
     */
    public static ECEventBus mirroring() {
        return mirroring(ECEventExceptionHandler.LOGGING);
    }

    /** As {@link #mirroring()}, with {@code exceptionHandler} deciding what a failing subscriber or watch callback becomes. */
    public static ECEventBus mirroring(ECEventExceptionHandler exceptionHandler) {
        return new ECEventBus(true, exceptionHandler);
    }

    /**
     * Swaps the handler of ANY bus - the global one included - and hands back the previous one, for the
     * caller to restore. The setter is package-private on the bus on purpose: production never re-routes
     * the failures of another plugin's subscribers; a test engine does, for one test class, and puts the
     * previous handler back. A fence, not a lock - on the Java 8 floor any class declared in the bus
     * package reaches it - so it keeps the knob out of the public API, no more.
     */
    public static ECEventExceptionHandler installExceptionHandler(ECEventBus bus, ECEventExceptionHandler exceptionHandler) {
        ECEventExceptionHandler previous = bus.getExceptionHandler();
        bus.setExceptionHandler(exceptionHandler);
        return previous;
    }

}
