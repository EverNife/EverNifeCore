package br.com.finalcraft.evernifecore.action;

/** A definition needs an action source - or its {@code Pre} - this platform never raises; thrown at load by {@link ActionSupport#requireServed}. */
public final class UnsupportedSourceException extends RuntimeException {

    private final EventSource source;

    public UnsupportedSourceException(EventSource source, String message) {
        super(message);
        this.source = source;
    }

    /** The source the platform does not raise, or raises only after the outcome. */
    public EventSource getSource() {
        return source;
    }
}
