package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * One change to an editable area, as its {@link StorageBinding#onChange handler} sees it: what the area holds now that
 * its store has been written, and whether this is the last word on it.
 */
public final class StorageChange {

    private final ScreenView view;
    private final StorageBinding binding;
    private final Object backing;
    private final List<ItemSpec> contents;
    private final boolean last;

    public StorageChange(@Nonnull ScreenView view, @Nonnull StorageBinding binding, @Nonnull Object backing,
                         @Nonnull List<ItemSpec> contents, boolean last) {
        this.view = view;
        this.binding = binding;
        this.backing = backing;
        this.contents = Collections.unmodifiableList(contents);
        this.last = last;
    }

    /** The player who has it open, or {@code null} once the view has been released. */
    @Nullable
    public FPlayer getViewer() {
        return view.getViewer();
    }

    @Nonnull
    public ScreenView getView() {
        return view;
    }

    @Nonnull
    public StorageBinding getBinding() {
        return binding;
    }

    /**
     * The store this change was written into, already holding what {@link #getContents()} answers - what tells two
     * areas apart when one handler serves both. Its type is the platform's ({@code ItemStore} on Bukkit).
     */
    @Nonnull
    public Object getBacking() {
        return backing;
    }

    /**
     * What the area holds, in area order, an empty slot as {@code null}. Copies: changing them reaches neither the store
     * nor the open window. On a platform with native items each one carries its {@code NativeItem}.
     */
    @Nonnull
    public List<ItemSpec> getContents() {
        return contents;
    }

    /** Whether the screen is going away, making this the last change it reports - when a lazy saver has to flush. */
    public boolean isLast() {
        return last;
    }

}
