package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.everyconfig.config.Config;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a layout's keys are read from: the base file, and the language overlay the admin may have put
 * beside it.
 *
 * <p>The overlay wins key by key - a key it holds replaces the base's, a key it omits falls through -
 * which is what lets one language move an icon or change its material without a second copy of the
 * whole file. Only the base is ever written to: the overlay is the admin's, and the framework neither
 * creates nor edits it.</p>
 */
public final class LayoutSource {

    private final Config base;
    private final Config overlay;

    public LayoutSource(@Nonnull Config base, @Nullable Config overlay) {
        this.base = base;
        this.overlay = overlay;
    }

    /** The file the framework seeds and saves. */
    @Nonnull
    public Config getBase() {
        return base;
    }

    /** The language overlay, or {@code null} when the admin made none. */
    @Nullable
    public Config getOverlay() {
        return overlay;
    }

    public boolean hasOverlay() {
        return overlay != null;
    }

    /** Whether the value at {@code path} is the overlay's rather than the base's. */
    public boolean isFromOverlay(@Nonnull String path) {
        return overlay != null && overlay.contains(path);
    }

    public boolean contains(@Nonnull String path) {
        return isFromOverlay(path) || base.contains(path);
    }

    @Nonnull
    private Config configOf(String path) {
        return isFromOverlay(path) ? overlay : base;
    }

    @Nullable
    public <T> T getValue(@Nonnull String path, @Nonnull Class<T> type) {
        return contains(path) ? configOf(path).getValue(path, type) : null;
    }

    @Nullable
    public String getString(@Nonnull String path) {
        return contains(path) ? configOf(path).getString(path) : null;
    }

    public boolean getBoolean(@Nonnull String path, boolean fallback) {
        return contains(path) ? configOf(path).getBoolean(path, fallback) : fallback;
    }

    public int getInt(@Nonnull String path, int fallback) {
        return contains(path) ? configOf(path).getInt(path, fallback) : fallback;
    }

    @Nonnull
    public List<String> getStringList(@Nonnull String path) {
        return contains(path) ? configOf(path).getStringList(path) : Collections.<String>emptyList();
    }

    /**
     * The child keys of {@code path}: the overlay's first, then the ones only the base names.
     *
     * <p>A section is answered key by key like a leaf is - naming one icon of a section in one language
     * does not take its siblings away from that language.</p>
     */
    @Nonnull
    public Set<String> getKeys(@Nonnull String path) {
        if (!contains(path)) {
            return Collections.<String>emptySet();
        }
        Set<String> merged = new LinkedHashSet<>();
        if (isFromOverlay(path)) {
            merged.addAll(overlay.getKeys(path));
        }
        if (base.contains(path)) {
            merged.addAll(base.getKeys(path));
        }
        return merged;
    }

}
