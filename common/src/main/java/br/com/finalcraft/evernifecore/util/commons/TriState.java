package br.com.finalcraft.evernifecore.util.commons;

public enum TriState {
    TRUE(true),
    FALSE(false),
    UNKNOWN(null);

    private final Boolean value;

    private TriState(Boolean value) {
        this.value = value;
    }

    public static TriState of(Boolean value) {
        return value == null ? UNKNOWN
                : value ? TRUE : FALSE;
    }

    public boolean isSet() {
        return this != UNKNOWN;
    }

    public boolean isTrue() {
        return this == TRUE;
    }

    public boolean isFalse() {
        return this == FALSE;
    }

    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    public boolean isKnown() {
        return this != UNKNOWN;
    }

    public Boolean toBoolean() {
        return value;
    }
}
