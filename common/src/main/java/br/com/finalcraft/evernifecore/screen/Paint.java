package br.com.finalcraft.evernifecore.screen;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Consumer;

/**
 * What a slot is painted BY: the element's id and kind, what a click on it does, and the per-kind state a renderer
 * that draws by element needs (a progress ratio, whether a choice is enabled and why not). The {@link Icon} says what
 * the slot looks like; this says what it is. Immutable - a {@link SlotWriter} paints the same one on every slot.
 */
public final class Paint {

    private final String elementId;
    private final ElementKind kind;
    private final Element element;
    private final Consumer<ClickContext> onClick;
    private final double progress;
    private final boolean enabled;
    private final String reason;

    private Paint(String elementId, ElementKind kind, Element element, Consumer<ClickContext> onClick, double progress,
                  boolean enabled, String reason) {
        this.elementId = elementId;
        this.kind = kind;
        this.element = element;
        this.onClick = onClick;
        this.progress = progress;
        this.enabled = enabled;
        this.reason = reason;
    }

    /** A paint with no behaviour - a decoration - under {@code elementId}. */
    @Nonnull
    public static Paint decoration(@Nonnull String elementId) {
        return new Paint(elementId, ElementKind.DECORATION, null, null, -1, true, null);
    }

    /** The paint of {@code element}, clicking nowhere until {@link #onClick(Consumer)} says otherwise. */
    @Nonnull
    public static Paint of(@Nonnull Element element) {
        return new Paint(element.getId(), element.getKind(), element, null, -1, true, null);
    }

    /** The same paint under another id - a list paints one id per entry. */
    @Nonnull
    public Paint withId(@Nonnull String elementId) {
        return new Paint(elementId, kind, element, onClick, progress, enabled, reason);
    }

    @Nonnull
    public Paint withKind(@Nonnull ElementKind kind) {
        return new Paint(elementId, kind, element, onClick, progress, enabled, reason);
    }

    @Nonnull
    public Paint onClick(@Nullable Consumer<ClickContext> onClick) {
        return new Paint(elementId, kind, element, onClick, progress, enabled, reason);
    }

    @Nonnull
    public Paint progress(double progress) {
        return new Paint(elementId, kind, element, onClick, Math.max(0D, Math.min(1D, progress)), enabled, reason);
    }

    /** Draws it disabled, {@code reason} saying why; a click on it still reaches the handler, which decides. */
    @Nonnull
    public Paint disabled(@Nullable String reason) {
        return new Paint(elementId, kind, element, onClick, progress, false, reason);
    }

    @Nonnull
    public String getElementId() {
        return elementId;
    }

    @Nonnull
    public ElementKind getKind() {
        return kind;
    }

    /** The element that painted, or {@code null} for a decoration. */
    @Nullable
    public Element getElement() {
        return element;
    }

    @Nullable
    public Consumer<ClickContext> getOnClick() {
        return onClick;
    }

    public double getProgress() {
        return progress;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

}
