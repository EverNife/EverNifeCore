package br.com.finalcraft.evernifecore.screen;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * What {@link Screens#open} does when no renderer that serves the viewer covers what the screen needs:
 * {@link #REFUSE} (the default) fails the open with a text that names the missing capability, and
 * {@link #open(Screen)} opens another screen instead, with the same arguments.
 */
public final class ScreenFallback {

    public static final ScreenFallback REFUSE = new ScreenFallback(null);

    private final Screen<?> alternative;

    private ScreenFallback(@Nullable Screen<?> alternative) {
        this.alternative = alternative;
    }

    /** Opens {@code alternative} instead - a screen that needs less, typically one without its storage region. */
    @Nonnull
    public static ScreenFallback open(@Nonnull Screen<?> alternative) {
        return new ScreenFallback(Objects.requireNonNull(alternative, "alternative"));
    }

    /** The screen to open instead, or {@code null} for {@link #REFUSE}. */
    @Nullable
    public Screen<?> getAlternative() {
        return alternative;
    }

    @Override
    public String toString() {
        return alternative == null ? "REFUSE" : "open(" + alternative.describe() + ")";
    }

}
