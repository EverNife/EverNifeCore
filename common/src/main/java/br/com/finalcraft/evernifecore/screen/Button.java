package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.logic.Condition;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The element a click is aimed at: an {@link Icon} on some slots plus what it does - {@link ClickRule}s or a plain
 * {@link #onClick}, which state it draws, when it is visible, what makes it redraw. {@code screen.icon(l -> l.BUY)}
 * answers one for a layout icon; {@code screen.icon(slots, icon)} for raw slots.
 *
 * <p>Declaring is all this does. Per viewer, when the view is built, the button binds a COPY of its icon into a
 * component of that view, so two players looking at the same screen never share a state or a cycle position.</p>
 */
public class Button implements Element {

    /** How often a condition with no event source of its own is asked again, by default: once a second. */
    public static final long DEFAULT_SAMPLE_TICKS = 20L;

    private final Screen<?> screen;
    private final SlotSet slots;
    private final Icon template;
    private String id;
    private ElementKind kind = ElementKind.BUTTON;

    private final List<State<?>> dependencies = new ArrayList<>();
    private final List<Condition> conditions = new ArrayList<>();
    private long sampleTicks = DEFAULT_SAMPLE_TICKS;
    private final List<ClickRule> rules = new ArrayList<>();
    private Supplier<String> state;
    private Supplier<Boolean> visibleWhen;
    private long visibleWhenTicks;
    private Consumer<ClickContext> onClick;
    private CycleBinder<?> cycle;
    private long everyTicks = 0L;
    private Consumer<Icon> renderer;

    public Button(@Nonnull Screen<?> screen, @Nonnull String id, @Nonnull SlotSet slots, @Nonnull Icon template) {
        this.screen = screen;
        this.id = id;
        this.slots = slots;
        this.template = template.copy();
    }

    /** Whether the icon is a background - its paints go under every component that is not. */
    boolean isBackground() {
        return template.isBackground();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Identity
    // -----------------------------------------------------------------------------------------------------------------

    @Nonnull
    @Override
    public String getId() {
        return id;
    }

    /**
     * Gives this element the id renderers address it by. A layout icon is already named after its field and a raw-slot
     * one is {@code slot#<n>}; a second element under the same id is refused when the view is built.
     */
    @Nonnull
    public Button id(@Nonnull String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("An element id cannot be blank: renderers address the element by it. "
                    + "Give it a short name, like Button.id(\"buy\").");
        }
        this.id = id.trim();
        return this;
    }

    @Nonnull
    @Override
    public ElementKind getKind() {
        return kind;
    }

    /** For the elements that are a button with another face ({@link InputField}, {@link Choice}...). */
    void setKind(ElementKind kind) {
        this.kind = kind;
    }

    @Nonnull
    @Override
    public SlotSet getSlots() {
        return slots;
    }

    @Nonnull
    @Override
    public Set<EventSource> getNeeds() {
        Set<EventSource> needs = new LinkedHashSet<>();
        for (Condition condition : conditions) {
            needs.addAll(condition.getNeeds());
        }
        return Collections.unmodifiableSet(needs);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Behaviour
    // -----------------------------------------------------------------------------------------------------------------

    /** What a click on it does, after its {@link #rule rules}. */
    @Nonnull
    public Button onClick(@Nullable Consumer<ClickContext> onClick) {
        this.onClick = onClick;
        return this;
    }

    /** Adds a rule; on a click the first rule that answers the gesture runs, and only it - see {@link ClickRule#dispatch}. */
    @Nonnull
    public Button rule(@Nonnull ClickRule... rules) {
        this.rules.addAll(Arrays.asList(rules));
        return this;
    }

    /** For an element whose clicks another engine routes ({@link Choice#pickedBy}). */
    void clearRules() {
        rules.clear();
    }

    @Nonnull
    public List<ClickRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    /** Which state the icon draws, chosen from {@code type}'s constants and asked again each tick. */
    @Nonnull
    public <E extends Enum<E>> Button states(@Nonnull Class<E> type, @Nonnull Supplier<E> state) {
        IconStates.warnUnknownKeys(type, template.getStateNames(), template.toString());
        return state(() -> {
            E chosen = state.get();
            return chosen == null ? null : IconStates.keyOf(chosen);
        });
    }

    /** The dynamic form of {@link #states}: a state name computed at runtime, {@code null} for the icon's own look. */
    @Nonnull
    public Button state(@Nullable Supplier<String> state) {
        if (this.state != null) {
            throw new IllegalStateException("This icon was already told which state it draws, and a second answer would "
                    + "silently replace the first. Compose the two conditions into one supplier, or bind the second "
                    + "appearance to an icon of its own.");
        }
        this.state = state;
        return this;
    }

    /** Draws it only while {@code live} answers true, asked once a tick. A {@code null} answer hides it. */
    @Nonnull
    public Button visibleWhen(@Nullable Supplier<Boolean> live) {
        return visibleWhen(live, 1L);
    }

    /** {@link #visibleWhen(Supplier)} asked every {@code checkEveryTicks}; {@code 0} asks only when something redraws. */
    @Nonnull
    public Button visibleWhen(@Nullable Supplier<Boolean> live, long checkEveryTicks) {
        this.visibleWhen = live;
        this.visibleWhenTicks = Math.max(0L, checkEveryTicks);
        return this;
    }

    /** Redraws it whenever one of {@code states} changes. */
    @Nonnull
    public Button dependsOn(@Nonnull State<?>... states) {
        Collections.addAll(dependencies, states);
        return this;
    }

    /**
     * Redraws it when the answer of one of {@code conditions} may have changed for the viewer: when one of the
     * condition's event sources fires with the viewer as the actor, or - for a condition with no source - at every
     * {@link #sampleEvery(long) sample}. While a view of it is open, each source is held needed.
     */
    @Nonnull
    public Button dependsOn(@Nonnull Condition... conditions) {
        Collections.addAll(this.conditions, conditions);
        return this;
    }

    /** How often a condition with no event source is asked again; {@link #DEFAULT_SAMPLE_TICKS} by default. */
    @Nonnull
    public Button sampleEvery(long ticks) {
        this.sampleTicks = Math.max(1L, ticks);
        return this;
    }

    @Nonnull
    public List<Condition> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    /** Clicking it walks through every constant of {@code type}, drawing the state each one names. */
    @Nonnull
    public <E extends Enum<E>> CycleBinder<E> cycle(@Nonnull Class<E> type) {
        return cycle(type, null);
    }

    /** {@link #cycle(Class)} over a value the plugin holds, so it can read and set it from outside. */
    @Nonnull
    public <E extends Enum<E>> CycleBinder<E> cycle(@Nonnull Class<E> type, @Nullable MutableState<E> value) {
        E[] constants = type.getEnumConstants();
        if (constants == null || constants.length == 0) {
            throw new IllegalArgumentException(type.getSimpleName() + " declares no constant, so there is nothing to cycle "
                    + "through. Give the enum its states, or use states(...) with a supplier.");
        }
        IconStates.warnUnknownKeys(type, template.getStateNames(), template.toString());
        return install(new CycleBinder<>(this, Arrays.asList(constants), IconStates::keyOf, value));
    }

    @Nonnull
    public CycleBinder<String> cycle(@Nonnull String... states) {
        if (states == null || states.length == 0) {
            throw new IllegalArgumentException("A cycle needs at least one state name to walk through.");
        }
        return install(new CycleBinder<>(this, Arrays.asList(states), name -> name, null));
    }

    private <T> CycleBinder<T> install(CycleBinder<T> binder) {
        if (this.cycle != null) {
            throw new IllegalStateException("This icon already cycles through a set of states, and a second cycle would "
                    + "silently replace the first. One cycle per icon: put the second one on an icon of its own.");
        }
        this.cycle = binder;
        return binder;
    }

    /** Redraws it every {@code ticks} while a view of it is open - {@code every(20)} is once a second. */
    @Nonnull
    public Button every(long ticks) {
        this.everyTicks = Math.max(0L, ticks);
        return this;
    }

    /** What each redraw runs over this viewer's copy of the icon before painting it - an animation, a counter. */
    @Nonnull
    public Button render(@Nullable Consumer<Icon> renderer) {
        this.renderer = renderer;
        return this;
    }

    /** The icon as declared - edits here reach every view opened from now on. */
    @Nonnull
    public Icon getIcon() {
        return template;
    }

    @Nonnull
    public Screen<?> getScreen() {
        return screen;
    }

    /** {@link Screen#open} of the screen this button belongs to - so a declaration chain can end in an open. */
    @Nonnull
    public CompletableFuture<ScreenView> open(@Nonnull FPlayer viewer, @Nonnull Object... args) {
        return screen.open(viewer, args);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Binding into one viewer's view
    // -----------------------------------------------------------------------------------------------------------------

    /** Binds a copy of this button into {@code component}, which belongs to one viewer's view. */
    public void bind(@Nonnull ScreenComponent component) {
        Icon icon = template.copy();
        Supplier<String> chosen = state;
        Consumer<ClickContext> cycleClick = null;
        if (cycle != null) {
            CycleBinder.Bound bound = cycle.bind(component);
            chosen = bound.getState();
            cycleClick = bound.getOnClick();
        }
        if (chosen != null) {
            icon.state(chosen);
            component.watch(chosen);
        }
        if (visibleWhen != null && visibleWhenTicks > 0) {
            component.watch(visibleWhen, visibleWhenTicks);
        }
        if (everyTicks > 0) {
            component.every(everyTicks);
        }
        for (State<?> dependency : dependencies) {
            component.remember(dependency);
        }
        component.dependOn(conditions, sampleTicks);

        Paint base = Paint.of(this).onClick(clickHandler(cycleClick));
        ScreenView view = component.getView();
        Supplier<Boolean> visible = visibleWhen;
        Consumer<Icon> perRender = renderer;
        component.render(writer -> {
            if (visible != null && !Boolean.TRUE.equals(visible.get())) {
                return;
            }
            if (perRender != null) {
                perRender.accept(icon);
            }
            draw(writer, base, view, icon);
        });
    }

    /**
     * Paints this viewer's {@code icon} for one redraw, with {@link #decorate} over {@code base}. A subclass that dresses
     * the icon per redraw paints a copy - {@code icon} lives as long as the view. Renderer thread.
     */
    void draw(@Nonnull SlotWriter writer, @Nonnull Paint base, @Nonnull ScreenView view, @Nonnull Icon icon) {
        writer.paint(decorate(base, view), slots, icon);
    }

    /** The per-kind state a subclass adds to the paint - a progress ratio, a disabled choice. Asked on every render. */
    @Nonnull
    Paint decorate(@Nonnull Paint paint, @Nonnull ScreenView view) {
        return paint;
    }

    @Nullable
    private Consumer<ClickContext> clickHandler(@Nullable Consumer<ClickContext> cycleClick) {
        List<ClickRule> declaredRules = new ArrayList<>(rules);
        Consumer<ClickContext> declared = onClick;
        if (cycleClick == null && declared == null && declaredRules.isEmpty()) {
            return null;
        }
        return context -> {
            if (cycleClick != null) {
                cycleClick.accept(context);
            }
            if (!declaredRules.isEmpty()) {
                ClickRule.dispatch(declaredRules, context);
            }
            if (declared != null) {
                declared.accept(context);
            }
        };
    }

}
