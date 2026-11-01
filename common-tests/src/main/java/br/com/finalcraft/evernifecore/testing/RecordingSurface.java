package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.ElementPatch;
import br.com.finalcraft.evernifecore.screen.GridSurface;
import br.com.finalcraft.evernifecore.screen.RenderedIcon;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The window a {@link RecordingRenderer} opens: what each slot shows, plus the whole history of what the view sent -
 * every {@code apply} with the patches in it, every title, the close. A test asserts on that history.
 */
public class RecordingSurface extends GridSurface {

    private final ScreenView view;
    private final RenderedIcon[] slots;
    private final List<List<ElementPatch>> applies = new ArrayList<>();
    private final List<String> titles = new ArrayList<>();
    private CloseReason closedWith;
    private boolean silent;
    private boolean suspended;

    public RecordingSurface(@Nonnull ScreenView view) {
        super(view.getGrid());
        this.view = view;
        this.slots = new RenderedIcon[view.getGrid().getSize()];
        this.titles.add(view.getCurrentTitle());
    }

    @Override
    protected void writeSlot(int slot, @Nullable RenderedIcon icon) {
        slots[slot] = icon;
    }

    @Nullable
    @Override
    protected RenderedIcon readSlot(int slot) {
        return slots[slot];
    }

    @Override
    public void apply(@Nonnull List<ElementPatch> patch) {
        applies.add(new ArrayList<>(patch));
        super.apply(patch);
    }

    @Override
    public void setTitle(@Nonnull String title) {
        titles.add(title);
    }

    @Override
    public void suspended() {
        suspended = true;
    }

    /** Takes the window away and reports it gone, as a platform confirming the close would. */
    @Override
    public void close(@Nonnull CloseReason reason) {
        if (closedWith != null) {
            return;
        }
        closedWith = reason;
        if (!silent) {
            Screens.windowClosed(view, reason);
        }
    }

    /** Takes the window away without a report - the window going because the view was set aside. */
    public void dropSilently() {
        silent = true;
        close(CloseReason.REQUESTED);
    }

    /** The player pressing escape. */
    public void playerCloses() {
        close(CloseReason.PLAYER_CLOSED);
    }

    @Override
    public boolean isBackedBy(@Nullable Object nativeWindow) {
        return nativeWindow == this;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What a test reads
    // -----------------------------------------------------------------------------------------------------------------

    @Nonnull
    public ScreenView getView() {
        return view;
    }

    /** What {@code slot} shows right now. */
    @Nullable
    public RenderedIcon getSlot(int slot) {
        return slots[slot];
    }

    /** Every {@code apply} the view made, in order. */
    @Nonnull
    public List<List<ElementPatch>> getApplies() {
        return Collections.unmodifiableList(applies);
    }

    /** The element ids the {@code index}-th {@code apply} touched. */
    @Nonnull
    public Set<String> getTouchedIds(int index) {
        Set<String> ids = new LinkedHashSet<>();
        for (ElementPatch patch : applies.get(index)) {
            ids.add(patch.getElementId());
        }
        return ids;
    }

    /** The patch of the last write to {@code slot}, or {@code null} when nothing ever wrote it. */
    @Nullable
    public ElementPatch getLastPatch(int slot) {
        for (int i = applies.size() - 1; i >= 0; i--) {
            for (ElementPatch patch : applies.get(i)) {
                if (patch.getSlot() == slot) {
                    return patch;
                }
            }
        }
        return null;
    }

    public void forgetApplies() {
        applies.clear();
    }

    /** The title it opened with, then every change. */
    @Nonnull
    public List<String> getTitles() {
        return Collections.unmodifiableList(titles);
    }

    public boolean isClosed() {
        return closedWith != null;
    }

    @Nullable
    public CloseReason getClosedWith() {
        return closedWith;
    }

    /** Whether the view drawn here was set aside - a prompt, or a screen opened on top of it. */
    public boolean isSuspended() {
        return suspended;
    }

}
