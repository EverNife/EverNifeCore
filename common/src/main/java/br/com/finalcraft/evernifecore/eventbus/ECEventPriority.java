package br.com.finalcraft.evernifecore.eventbus;

public enum ECEventPriority {
    FIRST((short)-21844),
    EARLY((short)-10922),
    NORMAL((short)0),
    LATE((short)10922),
    LAST((short)21844);

    private final short value;

    private ECEventPriority(final short value) {
        this.value = value;
    }

    public short getValue() {
        return this.value;
    }

    /**
     * The priority a handler asked for: its {@link ECEventHandler#priorityValue()} when it named one,
     * the {@link ECEventHandler#priority()} step otherwise. Every route that registers an
     * {@code @ECEventHandler} resolves it through here, so none of them can honour only half of it.
     */
    public static short of(ECEventHandler annotation) {
        return annotation.priorityValue() != -1
                ? annotation.priorityValue()
                : annotation.priority().getValue();
    }
}
