package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.minecraft.gui.component.GuiComponent;
import br.com.finalcraft.evernifecore.minecraft.gui.component.IconBinder;
import br.com.finalcraft.evernifecore.minecraft.gui.component.ListComponent;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Layouts;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiGeometry;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiType;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Region;
import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.view.CloseContext;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiViews;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A screen: what it shows, where, and what a click on it may do. The entry point of the whole gui
 * framework - {@code Gui.of(3).title("...").icon(13, ...).open(player)}.
 *
 * <p>It is a description, not a window. It holds no {@code Inventory}, is not an
 * {@code InventoryHolder}, and the same {@code Gui} opens for any number of players; each of them
 * gets a {@link GuiView}, which is what owns the container, the buffer and the scheduling.</p>
 *
 * <p>Nothing is clickable until it is opened up: the default policy is {@link ClickPolicy#DENY_ALL},
 * so items cannot be taken, placed, dragged or shift-moved out of a screen until a region says so.</p>
 *
 * <p>{@code L} is the layout this screen was built from. {@link #of(LayoutBase)} paints every icon the
 * file resolved and types {@link #icon(Function)}, so {@code icon(l -> l.UPGRADE)} is checked by the
 * compiler and refactors with the field. A screen sized by hand has no layout and only the raw-slot
 * forms.</p>
 */
public class Gui<L extends LayoutBase> {

    /** Enough to swallow a double click, short enough to be invisible to a human. {@code 0} switches it off. */
    public static final long DEFAULT_DEBOUNCE_MILLIS = 200L;

    private final GuiType type;
    private final int rows;
    private final L layout;

    private Supplier<String> title = () -> "";
    private ClickPolicy policy = ClickPolicy.DENY_ALL;
    private long debounceMillis = DEFAULT_DEBOUNCE_MILLIS;

    private final Map<String, IconBinding> layoutIcons = new LinkedHashMap<>();
    private final Set<String> claimedIcons = new LinkedHashSet<>();
    private final List<IconBinding> iconBindings = new ArrayList<>();
    private final List<Consumer<GuiComponent>> componentDeclarations = new ArrayList<>();
    private final Map<String, Region> regions = new LinkedHashMap<>();
    private Consumer<CloseContext> onClose;

    protected Gui(GuiType type, int rows, @Nullable L layout) {
        this.type = type;
        this.rows = rows;
        this.layout = layout;
        type.sizeOf(rows); //fail here, on the line the caller wrote, not later when the window is asked for
        if (layout != null) {
            title(layout::getTitle);
            for (LayoutBase.PlacedIcon placed : layout.getIcons().values()) {
                if (placed.isVisible()) {
                    layoutIcons.put(placed.getName(), new IconBinding(placed.getSlots(), placed.getIcon()));
                }
            }
        }
    }

    /** A chest of {@code rows} rows. */
    @Nonnull
    public static Gui<LayoutBase> of(int rows) {
        return new Gui<>(GuiType.CHEST, rows, null);
    }

    /** A window of another type - hopper, dispenser, brewing stand or workbench. */
    @Nonnull
    public static Gui<LayoutBase> of(@Nonnull GuiType type) {
        return new Gui<>(type, GuiType.MAX_CHEST_ROWS, null);
    }

    /**
     * A screen sized, titled and decorated by {@code layout}: every icon the file resolved is already
     * painted, and {@link #icon(Function)} is how one of them gains behaviour.
     *
     * <p>The title is the one the owning plugin's language answers with. A title that follows each
     * viewer needs the viewer, which a description does not have.</p>
     */
    @Nonnull
    public static <L extends LayoutBase> Gui<L> of(@Nonnull L layout) {
        return new Gui<>(layout.getType(), layout.getRows(), layout);
    }

    /** {@link #of(LayoutBase)} over {@link Layouts#of(Class)} - the form that reads the file itself. */
    @Nonnull
    public static <L extends LayoutBase> Gui<L> of(@Nonnull Class<L> layoutType) {
        return of(Layouts.of(layoutType));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Declaration
    // -----------------------------------------------------------------------------------------------------------------

    @Nonnull
    public Gui<L> title(@Nonnull String title) {
        return title(() -> title);
    }

    /**
     * A title read again on every render, so it can carry a page number or a filter.
     *
     * <p><b>A title change costs a reopen.</b> The vanilla protocol has no way to rename an open
     * window, and renaming one through NMS would mean a per-version module matrix this framework
     * deliberately does not carry. So the container is replaced and the player sees one frame of
     * flicker; the view itself survives - component state, scheduled tasks and the back stack are
     * all preserved. A screen whose title changes on every tick will flicker on every tick.</p>
     */
    @Nonnull
    public Gui<L> title(@Nonnull Supplier<String> title) {
        this.title = title;
        return this;
    }

    /** Puts {@code icon} on one raw, 0-based slot. */
    @Nonnull
    public Gui<L> icon(int slot, @Nonnull Icon icon) {
        return icon(Slots.of(slot), icon);
    }

    /** Puts {@code icon} on every slot of {@code slots} - see {@link Slots}. */
    @Nonnull
    public Gui<L> icon(@Nonnull SlotSet slots, @Nonnull Icon icon) {
        if (slots == null || icon == null) {
            throw new IllegalArgumentException("Both the slots and the icon are required. "
                    + "To leave a slot empty, simply do not bind an icon to it.");
        }
        iconBindings.add(new IconBinding(slots, icon));
        return this;
    }

    /**
     * Gives one icon of the layout a behaviour of its own - a click, a state, a cycle - on the slots
     * the file put it on.
     *
     * <p>The binding works on a copy, per viewer, so two screens sharing the same cached layout never
     * write into each other, and the plain copy the layout painted stops being drawn.</p>
     */
    @Nonnull
    public IconBinder icon(@Nonnull Function<L, Icon> selector) {
        LayoutBase.PlacedIcon placed = takeOver(selector);
        IconBinder binder = new IconBinder(this, placed.getSlots(), placed.getIcon());
        component(binder::bind);
        return binder;
    }

    /**
     * A list poured into a region, one entry per slot, paginated by the size of the region itself.
     *
     * <p>The source is read whenever the list renders. What makes it render again is a state it
     * {@link ListComponent#dependsOn(br.com.finalcraft.evernifecore.minecraft.gui.state.State...)},
     * a page turn or a refresh - never a poll of the source.</p>
     */
    @Nonnull
    public <T> ListComponent<T, L> list(@Nonnull Supplier<List<T>> source) {
        return declare(new ListComponent<T, L>(this, source, null));
    }

    /** {@link #list(Supplier)} over a list that is already in hand. */
    @Nonnull
    public <T> ListComponent<T, L> list(@Nonnull List<T> source) {
        return list(() -> source);
    }

    /**
     * A source that answers one page at a time, for a catalogue too big to materialise. It needs
     * {@link ListComponent#total(Supplier)}: one page cannot say how many pages there are.
     */
    @Nonnull
    public <T> ListComponent<T, L> list(@Nonnull ListComponent.PageSource<T> source) {
        return declare(new ListComponent<T, L>(this, null, source));
    }

    private <T> ListComponent<T, L> declare(ListComponent<T, L> list) {
        component(list::bind);
        return list;
    }

    /**
     * Declares a group of slots that render together and re-render alone.
     *
     * <p>The lambda runs once per viewer, when their view is built, so a state created inside it
     * with {@code c.remember(...)} belongs to that player. State meant to be shared lives outside,
     * in a {@code State.of(...)} that several components remember.</p>
     */
    @Nonnull
    public Gui<L> component(@Nonnull Consumer<GuiComponent> declaration) {
        if (declaration == null) {
            throw new IllegalArgumentException("A component needs a declaration - the lambda that "
                    + "remembers its state and says what it renders.");
        }
        componentDeclarations.add(declaration);
        return this;
    }

    /**
     * Declares a named area with its own layer and click policy.
     *
     * @throws IllegalArgumentException when the name is already taken
     */
    @Nonnull
    public Gui<L> addRegion(@Nonnull Region region) {
        Region previous = regions.put(region.getName(), region);
        if (previous != null) {
            regions.put(region.getName(), previous);
            throw new IllegalArgumentException("This gui already has a region named ["
                    + region.getName() + "]. Region names are how clicks and layouts address an area, "
                    + "so they have to be unique within a screen.");
        }
        return this;
    }

    /** The policy applied to any slot no region claims. */
    @Nonnull
    public Gui<L> policy(@Nonnull ClickPolicy policy) {
        this.policy = policy == null ? ClickPolicy.DENY_ALL : policy;
        return this;
    }

    /**
     * How long after an accepted click the next one is ignored, in milliseconds. {@code 0} switches
     * it off.
     *
     * <p>A rejected attempt does not restart the window, so holding the mouse down cannot lock a
     * player out of their own menu.</p>
     */
    @Nonnull
    public Gui<L> debounce(long millis) {
        this.debounceMillis = Math.max(0L, millis);
        return this;
    }

    /**
     * Runs when the screen goes away - the player closed it, disconnected, changed world, or the
     * server is shutting the framework down. Always: it is where an item held by the screen becomes
     * an item back in the player's hands.
     */
    @Nonnull
    public Gui<L> onClose(@Nullable Consumer<CloseContext> onClose) {
        this.onClose = onClose;
        return this;
    }

    /**
     * The layout icon {@code selector} names, handed over to whatever binding asked for it: from here
     * on the screen no longer paints it on its own.
     *
     * @throws IllegalArgumentException when the screen has no layout, or the selector answers
     *                                  something that is not one of its icons
     */
    @Nonnull
    public LayoutBase.PlacedIcon takeOver(@Nonnull Function<L, Icon> selector) {
        if (layout == null) {
            throw new IllegalArgumentException("This screen has no layout, so an icon cannot be selected "
                    + "by field. Build it with Gui.of(MyLayout.class) to address icons by name, or bind "
                    + "them to raw slots with icon(slot, icon).");
        }
        String name = layout.getIconName(selector.apply(layout));
        if (name == null) {
            throw new IllegalArgumentException("The selector did not answer an icon of "
                    + layout.getLayoutName() + ". It has to return one of the @IconData fields of the layout "
                    + "itself - l -> l.UPGRADE - and not an icon built on the spot.");
        }
        LayoutBase.PlacedIcon placed = layout.getIcons().get(name);
        if (placed == null) {
            throw new IllegalArgumentException(layout.getLayoutName() + "." + name + " failed to load and was "
                    + "dropped, so nothing can be bound to it. The log says which key of the yml broke it.");
        }
        claimedIcons.add(name);
        return placed;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Opening
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Opens this screen for {@code player}.
     *
     * <p>The future completes once the server has confirmed the window is open - not when this
     * method returns. It completes exceptionally when the platform refuses (the player is sleeping,
     * is leaving, or another plugin cancelled the open), and the refusal is logged either way, so
     * ignoring the future is fine.</p>
     *
     * <p>Until that confirmation nothing is registered and nothing is scheduled, which is what stops
     * a refused open from leaking a task and a {@code Player} reference.</p>
     */
    @Nonnull
    public CompletableFuture<GuiView> open(@Nonnull Player player) {
        return GuiViews.open(this, player);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading
    // -----------------------------------------------------------------------------------------------------------------

    @Nonnull
    public GuiType getType() {
        return type;
    }

    public int getRows() {
        return rows;
    }

    /** The layout this screen was built from, or {@code null} when it was sized by hand. */
    @Nullable
    public L getLayout() {
        return layout;
    }

    /** The nominal measurements. A view measures the container it actually got instead. */
    @Nonnull
    public GuiGeometry getGeometry() {
        return new GuiGeometry(type, type.sizeOf(rows));
    }

    @Nonnull
    public String getTitle() {
        String resolved = title.get();
        return resolved == null ? "" : resolved;
    }

    @Nonnull
    public ClickPolicy getPolicy() {
        return policy;
    }

    public long getDebounceMillis() {
        return debounceMillis;
    }

    /** The layout's own icons that no binding took over, then the ones bound by hand. */
    @Nonnull
    public List<IconBinding> getIconBindings() {
        if (layoutIcons.isEmpty()) {
            return Collections.unmodifiableList(iconBindings);
        }
        List<IconBinding> bindings = new ArrayList<>(layoutIcons.size() + iconBindings.size());
        for (Map.Entry<String, IconBinding> entry : layoutIcons.entrySet()) {
            if (!claimedIcons.contains(entry.getKey())) {
                bindings.add(entry.getValue());
            }
        }
        bindings.addAll(iconBindings);
        return Collections.unmodifiableList(bindings);
    }

    @Nonnull
    public List<Consumer<GuiComponent>> getComponentDeclarations() {
        return Collections.unmodifiableList(componentDeclarations);
    }

    @Nonnull
    public Map<String, Region> getRegions() {
        return Collections.unmodifiableMap(regions);
    }

    @Nullable
    public Consumer<CloseContext> getOnClose() {
        return onClose;
    }

    /** One icon and the slots it was bound to, still unresolved while the slots are a shape. */
    public static final class IconBinding {

        private final SlotSet slots;
        private final Icon icon;

        IconBinding(SlotSet slots, Icon icon) {
            this.slots = slots;
            this.icon = icon;
        }

        @Nonnull
        public SlotSet getSlots() {
            return slots;
        }

        @Nonnull
        public Icon getIcon() {
            return icon;
        }

    }

}
