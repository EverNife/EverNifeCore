package br.com.finalcraft.evernifecore.minecraft.gui.component;

import br.com.finalcraft.evernifecore.minecraft.gui.state.MutableState;
import br.com.finalcraft.evernifecore.minecraft.gui.state.State;
import br.com.finalcraft.evernifecore.minecraft.gui.state.WatchState;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import jakarta.annotation.Nonnull;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A group of slots that renders together and re-renders alone.
 *
 * <p>A component exists per viewer, not per screen: the lambda handed to {@code Gui.component(...)}
 * runs once for each view, so a state created with {@link #remember(Object)} belongs to that one
 * player. State meant to be shared is created outside, with {@code State.of(...)}, and handed to
 * {@link #remember(State)} by every component that reads it.</p>
 *
 * <p>When any remembered state is invalidated, only this component renders again, and only into its
 * own layer. The commit that follows still writes just the slots whose output actually changed, so
 * a re-render that produces the same picture costs nothing.</p>
 */
public final class GuiComponent {

    private final GuiView view;
    private final int layer;

    private Consumer<SlotWriter> renderer;
    private long everyTicks = 0L;

    public GuiComponent(@Nonnull GuiView view, int layer) {
        this.view = view;
        this.layer = layer;
    }

    public int getLayer() {
        return layer;
    }

    @Nonnull
    public GuiView getView() {
        return view;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------------------------------------------------

    /** A state of this component's own, seeded with {@code initial}. */
    @Nonnull
    public <T> MutableState<T> remember(T initial) {
        MutableState<T> state = new MutableState<>(initial);
        remember(state);
        return state;
    }

    /** Reads a state created elsewhere. Two components that remember the same state both re-render. */
    @Nonnull
    public <T> State<T> remember(@Nonnull State<T> state) {
        view.own(state.addListener(this::invalidate));
        return state;
    }

    /**
     * Watches a value that lives outside the gui. The supplier is read once per tick while the view
     * is open - and not at all while it is not - and the component re-renders when the value changes.
     */
    @Nonnull
    public <T> State<T> watch(@Nonnull Supplier<T> snapshot) {
        return watch(snapshot, value -> value);
    }

    /**
     * {@link #watch(Supplier)} at a cadence of the caller's choosing: {@code intervalTicks} is how many
     * ticks pass between two reads, so {@code 20} reads once a second. What is spaced out is the
     * reading, not the drawing - a read that answers the same thing renders nothing either way, so a
     * slower cadence is for a source that costs something to ask.
     */
    @Nonnull
    public <T> State<T> watch(@Nonnull Supplier<T> snapshot, long intervalTicks) {
        return install(new WatchState<>(snapshot, value -> value, intervalTicks));
    }

    /**
     * Watches an object whose {@code equals} cannot report a change, comparing {@code key} instead:
     * a version counter, a hash, the field the screen shows. Without a key, an object mutated in
     * place is invisible - see {@link WatchState}.
     */
    @Nonnull
    public <T> State<T> watch(@Nonnull Supplier<T> source, @Nonnull Function<T, ?> key) {
        return install(new WatchState<>(source, key));
    }

    private <T> State<T> install(WatchState<T> state) {
        remember(state);
        view.addWatch(state);
        return state;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------------------------------------------------

    /** What this component draws. Re-runs whenever a state it remembers is invalidated. */
    public void render(@Nonnull Consumer<SlotWriter> renderer) {
        this.renderer = renderer;
        invalidate();
    }

    /**
     * Renders again every {@code ticks} while the view is open - {@code every(20)} means once a
     * second. The task belongs to the view and dies with it; there is no global task.
     */
    @Nonnull
    public GuiComponent every(long ticks) {
        this.everyTicks = Math.max(0L, ticks);
        return this;
    }

    /** Marks this component for a re-render on the next tick. */
    public void invalidate() {
        view.markComponentDirty(this);
    }

    /** Arms the periodic re-render, once the declaration lambda has finished. */
    public void start() {
        if (everyTicks > 0) {
            view.repeat(everyTicks, this::invalidate);
        }
    }

    /** Erases this component's layer and draws it again. */
    public void renderNow() {
        view.clearLayer(layer);
        if (renderer != null) {
            renderer.accept(new SlotWriter(view, layer));
        }
    }

}
