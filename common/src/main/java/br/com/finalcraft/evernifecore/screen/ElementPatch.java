package br.com.finalcraft.evernifecore.screen;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * One slot that changed, and the element now drawn there - the unit {@link ScreenSurface#apply} receives. A grid
 * renderer reads {@link #getSlot()} and {@link #getIcon()}; a renderer that draws by element groups the patches by
 * {@link #getElementId()}. The text is already resolved for the viewer: a renderer never sees a template.
 */
public final class ElementPatch {

    private final String elementId;
    private final ElementKind kind;
    private final int slot;
    private final RenderedIcon icon;
    private final double progress;
    private final boolean enabled;
    private final String reason;

    public ElementPatch(@Nonnull String elementId, @Nonnull ElementKind kind, int slot, @Nullable RenderedIcon icon,
                        double progress, boolean enabled, @Nullable String reason) {
        this.elementId = elementId;
        this.kind = kind;
        this.slot = slot;
        this.icon = icon;
        this.progress = progress;
        this.enabled = enabled;
        this.reason = reason;
    }

    /** The stable id of the element on this slot; {@code slot#<n>} for one painted straight on a slot. */
    @Nonnull
    public String getElementId() {
        return elementId;
    }

    @Nonnull
    public ElementKind getKind() {
        return kind;
    }

    public int getSlot() {
        return slot;
    }

    /** What the slot shows now, or {@code null} when it was cleared. */
    @Nullable
    public RenderedIcon getIcon() {
        return icon;
    }

    /** A {@link ProgressBar}'s ratio, 0..1; {@code -1} on any other element. */
    public double getProgress() {
        return progress;
    }

    /** Whether a click on it may do anything - {@code false} for a {@link Choice} whose gate the viewer fails. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Why it is disabled, already resolved for the viewer, or {@code null}. */
    @Nullable
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "ElementPatch{" + elementId + "@" + slot + ", " + kind + ", " + icon + "}";
    }

}
