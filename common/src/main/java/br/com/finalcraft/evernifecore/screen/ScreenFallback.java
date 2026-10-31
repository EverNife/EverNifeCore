package br.com.finalcraft.evernifecore.screen;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * What {@link Screens#open} does when no renderer that serves the viewer covers what the screen needs:
 * {@link #REFUSE} (the default) fails the open with a text that names the missing capability, and
 * {@link #open(Screen)} / {@link #open(ScreenKey)} opens another screen instead, with the same arguments.
 */
public final class ScreenFallback {

    public static final ScreenFallback REFUSE = new ScreenFallback(null, null);

    private final Screen<?> alternative;
    private final ScreenKey alternativeKey;

    private ScreenFallback(@Nullable Screen<?> alternative, @Nullable ScreenKey alternativeKey) {
        this.alternative = alternative;
        this.alternativeKey = alternativeKey;
    }

    /** Opens {@code alternative} instead - a screen that needs less, typically one without its storage region. */
    @Nonnull
    public static ScreenFallback open(@Nonnull Screen<?> alternative) {
        return new ScreenFallback(Objects.requireNonNull(alternative, "alternative"), null);
    }

    /**
     * Opens whatever answers {@code alternative} for this viewer instead - through the same override layers as
     * {@link Screens#open(br.com.finalcraft.evernifecore.api.common.player.FPlayer, ScreenKey, ScreenRequest)}, the
     * content owner being the refused screen's owner.
     */
    @Nonnull
    public static ScreenFallback open(@Nonnull ScreenKey alternative) {
        return new ScreenFallback(null, Objects.requireNonNull(alternative, "alternative"));
    }

    /** The screen to open instead; {@code null} for {@link #REFUSE} and for a fallback by key. */
    @Nullable
    public Screen<?> getAlternative() {
        return alternative;
    }

    /** The key to open instead; {@code null} for {@link #REFUSE} and for a fallback by screen. */
    @Nullable
    public ScreenKey getAlternativeKey() {
        return alternativeKey;
    }

    @Override
    public String toString() {
        return alternative != null ? "open(" + alternative.describe() + ")"
                : alternativeKey != null ? "open(" + alternativeKey + ")" : "REFUSE";
    }

}
