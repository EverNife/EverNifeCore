package br.com.finalcraft.evernifecore.minecraft.gui.component;

import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.IconStates;
import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import br.com.finalcraft.evernifecore.minecraft.gui.state.MutableState;
import br.com.finalcraft.evernifecore.minecraft.gui.view.ClickContext;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What one icon of a layout does, once the file has said what it looks like and where it is.
 *
 * <p>Everything declared here is applied to a copy made per viewer, so a state, a cycle or a click
 * belongs to the player looking at it and never to the layout instance the whole server shares.</p>
 */
public final class IconBinder {

    private final Gui<?> gui;
    private final SlotSet slots;
    private final Icon template;

    private Supplier<String> state;
    private Consumer<ClickContext> onClick;
    private CycleBinder<?> cycle;
    private long everyTicks = 0L;

    public IconBinder(@Nonnull Gui<?> gui, @Nonnull SlotSet slots, @Nonnull Icon template) {
        this.gui = gui;
        this.slots = slots;
        this.template = template.copy();
    }

    /** What a click on this icon does. */
    @Nonnull
    public IconBinder onClick(@Nullable Consumer<ClickContext> onClick) {
        this.onClick = onClick;
        return this;
    }

    /**
     * Which state the icon draws, asked again on every render and once per tick while the screen is
     * open, so a change out in the plugin reaches the screen without anybody calling refresh.
     */
    @Nonnull
    public <E extends Enum<E>> IconBinder states(@Nonnull Class<E> type, @Nonnull Supplier<E> state) {
        IconStates.warnUnknownKeys(type, template.getStateNames(), template.toString());
        return state(() -> {
            E chosen = state.get();
            return chosen == null ? null : IconStates.keyOf(chosen);
        });
    }

    /** The dynamic form of {@link #states(Class, Supplier)}, for a state name computed at runtime. */
    @Nonnull
    public IconBinder state(@Nullable Supplier<String> state) {
        this.state = state;
        return this;
    }

    /**
     * Turns this icon into a toggle: a click advances to the next constant, in declaration order and
     * wrapping around, and the icon draws the state that constant names.
     *
     * <p>The value lives in the view, so each player toggles their own screen. {@link #cycle(Class,
     * MutableState)} is the form that writes somewhere else - a state bound to a {@code PDSection},
     * typically, which is what makes the choice survive the menu being closed.</p>
     */
    @Nonnull
    public <E extends Enum<E>> CycleBinder<E> cycle(@Nonnull Class<E> type) {
        return cycle(type, null);
    }

    @Nonnull
    public <E extends Enum<E>> CycleBinder<E> cycle(@Nonnull Class<E> type,
                                                    @Nullable MutableState<E> value) {
        E[] constants = type.getEnumConstants();
        if (constants == null || constants.length == 0) {
            throw new IllegalArgumentException(type.getSimpleName() + " declares no constant, so there is "
                    + "nothing to cycle through. Give the enum its states, or use states(...) with a supplier.");
        }
        IconStates.warnUnknownKeys(type, template.getStateNames(), template.toString());
        return install(new CycleBinder<>(this, Arrays.asList(constants), IconStates::keyOf, value));
    }

    /** The string form of {@link #cycle(Class)}, for states the plugin only knows by name. */
    @Nonnull
    public CycleBinder<String> cycle(@Nonnull String... states) {
        if (states == null || states.length == 0) {
            throw new IllegalArgumentException("A cycle needs at least one state name to walk through.");
        }
        return install(new CycleBinder<>(this, Arrays.asList(states), name -> name, null));
    }

    private <T> CycleBinder<T> install(CycleBinder<T> binder) {
        this.cycle = binder;
        return binder;
    }

    /** Redraws this icon every {@code ticks} while the screen is open - {@code every(20)} is once a second. */
    @Nonnull
    public IconBinder every(long ticks) {
        this.everyTicks = Math.max(0L, ticks);
        return this;
    }

    /** The icon as the file resolved it, for a tweak this binder has no method of its own for. */
    @Nonnull
    public Icon getIcon() {
        return template;
    }

    @Nonnull
    public Gui<?> getGui() {
        return gui;
    }

    /** Opens the screen this icon belongs to - the chain's way out. */
    @Nonnull
    public CompletableFuture<GuiView> open(@Nonnull Player player) {
        return gui.open(player);
    }

    /** Builds this viewer's own copy of the icon and paints it. */
    public void bind(@Nonnull GuiComponent component) {
        Icon icon = template.copy();
        Supplier<String> chosen = state;
        Consumer<ClickContext> click = onClick;

        if (cycle != null) {
            CycleBinder.Bound bound = cycle.bind(component);
            chosen = bound.getState();
            click = chain(bound.getOnClick(), click);
        }
        if (chosen != null) {
            icon.state(chosen);
            //the poll is what makes a value nobody told the screen about still reach it
            component.watch(chosen);
        }
        if (click != null) {
            icon.onClick(click);
        }
        if (everyTicks > 0) {
            component.every(everyTicks);
        }
        component.render(writer -> writer.icon(slots, icon));
    }

    private static Consumer<ClickContext> chain(Consumer<ClickContext> first, Consumer<ClickContext> then) {
        if (then == null) {
            return first;
        }
        return context -> {
            first.accept(context);
            then.accept(context);
        };
    }

}
