package br.com.finalcraft.evernifecore.screen;

import jakarta.annotation.Nullable;

import java.util.Locale;

/**
 * Which gesture a click was, said the same way on every platform - what a {@link ClickRule} answers to. {@link ClickKind}
 * is the other half: what the gesture does with items.
 */
public enum ClickGesture {
    PRIMARY,
    SECONDARY,
    SHIFT_PRIMARY,
    SHIFT_SECONDARY,
    MIDDLE,
    DOUBLE,
    DROP,
    CONTROL_DROP,
    NUMBER_KEY,
    UNKNOWN;

    /**
     * The gesture a platform's own click name stands for - Bukkit's {@code ClickType} names ({@code LEFT},
     * {@code SHIFT_RIGHT}, {@code DOUBLE_CLICK}...) or this enum's own. Read by NAME so a constant a server version lacks
     * never has to be linked. Anything else is {@link #UNKNOWN}, which no policy allows.
     */
    public static ClickGesture ofName(@Nullable String name) {
        if (name == null) {
            return UNKNOWN;
        }
        String upper = name.trim().toUpperCase(Locale.ROOT);
        switch (upper) {
            case "LEFT":
                return PRIMARY;
            case "RIGHT":
                return SECONDARY;
            case "SHIFT_LEFT":
                return SHIFT_PRIMARY;
            case "SHIFT_RIGHT":
                return SHIFT_SECONDARY;
            case "DOUBLE_CLICK":
                return DOUBLE;
            default:
                for (ClickGesture gesture : values()) {
                    if (gesture.name().equals(upper)) {
                        return gesture;
                    }
                }
                return UNKNOWN;
        }
    }

    public boolean isShift() {
        return this == SHIFT_PRIMARY || this == SHIFT_SECONDARY;
    }

}
