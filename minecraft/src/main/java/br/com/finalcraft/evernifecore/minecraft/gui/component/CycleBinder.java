package br.com.finalcraft.evernifecore.minecraft.gui.component;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.state.MutableState;
import br.com.finalcraft.evernifecore.minecraft.gui.view.ClickContext;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An icon that walks through its own states on click, in the order they were declared and wrapping
 * around at the end.
 *
 * <p>The order is written once - in the enum - instead of once in the layout and once again in a
 * handler that has to know what comes next. {@link #onCycle(Consumer)} is handed the new value
 * already typed, so nothing has to translate a name back into a constant.</p>
 */
public final class CycleBinder<T> {

    private final IconBinder icon;
    private final List<T> values;
    private final Function<T, String> stateNameOf;
    private final MutableState<T> shared;

    private Consumer<T> onCycle;
    private final Map<T, Consumer<ClickContext>> onState = new LinkedHashMap<>();

    CycleBinder(IconBinder icon, List<T> values, Function<T, String> stateNameOf,
                @Nullable MutableState<T> shared) {
        this.icon = icon;
        this.values = new ArrayList<>(values);
        this.stateNameOf = stateNameOf;
        this.shared = shared;
    }

    /** Runs after every advance, with the state the icon has just moved to. */
    @Nonnull
    public CycleBinder<T> onCycle(@Nullable Consumer<T> onCycle) {
        this.onCycle = onCycle;
        return this;
    }

    /** Runs when the advance lands on {@code value}, and only then. */
    @Nonnull
    public CycleBinder<T> onState(@Nonnull T value, @Nonnull Consumer<ClickContext> handler) {
        onState.put(value, handler);
        return this;
    }

    /** The icon this cycle was started from, to keep configuring it after the cycle is declared. */
    @Nonnull
    public IconBinder getBinder() {
        return icon;
    }

    @Nonnull
    public Gui<?> getGui() {
        return icon.getGui();
    }

    /** Opens the screen this icon belongs to - the chain's way out, as on {@link IconBinder}. */
    @Nonnull
    public CompletableFuture<GuiView> open(@Nonnull Player player) {
        return icon.open(player);
    }

    /** This viewer's own cursor over the states, unless the binder was given one to share. */
    @Nonnull
    Bound bind(GuiComponent component) {
        final MutableState<T> value = shared != null ? shared : new MutableState<>(values.get(0));
        component.remember(value);
        return new Bound(
                () -> {
                    T current = value.get();
                    return current == null ? null : stateNameOf.apply(current);
                },
                context -> {
                    T next = after(value.get());
                    value.set(next);
                    if (onCycle != null) {
                        onCycle.accept(next);
                    }
                    Consumer<ClickContext> handler = onState.get(next);
                    if (handler != null) {
                        handler.accept(context);
                    }
                });
    }

    /** The state after {@code current}, wrapping around. A value out of the list restarts the walk. */
    private T after(T current) {
        int index = values.indexOf(current);
        return index < 0 ? values.get(0) : values.get((index + 1) % values.size());
    }

    /** One viewer's wiring: which state their icon draws and what their click does. */
    static final class Bound {

        private final Supplier<String> state;
        private final Consumer<ClickContext> onClick;

        Bound(Supplier<String> state, Consumer<ClickContext> onClick) {
            this.state = state;
            this.onClick = onClick;
        }

        Supplier<String> getState() {
            return state;
        }

        Consumer<ClickContext> getOnClick() {
            return onClick;
        }

    }

}
