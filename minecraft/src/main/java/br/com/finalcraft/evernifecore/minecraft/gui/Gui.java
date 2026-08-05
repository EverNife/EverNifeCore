package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.minecraft.gui.component.GuiComponent;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
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
 */
public class Gui {

    /** Enough to swallow a double click, short enough to be invisible to a human. {@code 0} switches it off. */
    public static final long DEFAULT_DEBOUNCE_MILLIS = 200L;

    private final GuiType type;
    private final int rows;

    private Supplier<String> title = () -> "";
    private ClickPolicy policy = ClickPolicy.DENY_ALL;
    private long debounceMillis = DEFAULT_DEBOUNCE_MILLIS;

    private final List<IconBinding> iconBindings = new ArrayList<>();
    private final List<Consumer<GuiComponent>> componentDeclarations = new ArrayList<>();
    private final Map<String, Region> regions = new LinkedHashMap<>();
    private Consumer<CloseContext> onClose;

    protected Gui(GuiType type, int rows) {
        this.type = type;
        this.rows = rows;
        type.sizeOf(rows); //fail here, on the line the caller wrote, not later when the window is asked for
    }

    /** A chest of {@code rows} rows. */
    @Nonnull
    public static Gui of(int rows) {
        return new Gui(GuiType.CHEST, rows);
    }

    /** A window of another type - hopper, dispenser, brewing stand or workbench. */
    @Nonnull
    public static Gui of(@Nonnull GuiType type) {
        return new Gui(type, GuiType.MAX_CHEST_ROWS);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Declaration
    // -----------------------------------------------------------------------------------------------------------------

    @Nonnull
    public Gui title(@Nonnull String title) {
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
    public Gui title(@Nonnull Supplier<String> title) {
        this.title = title;
        return this;
    }

    /** Puts {@code icon} on one raw, 0-based slot. */
    @Nonnull
    public Gui icon(int slot, @Nonnull Icon icon) {
        return icon(Slots.of(slot), icon);
    }

    /** Puts {@code icon} on every slot of {@code slots} - see {@link Slots}. */
    @Nonnull
    public Gui icon(@Nonnull SlotSet slots, @Nonnull Icon icon) {
        if (slots == null || icon == null) {
            throw new IllegalArgumentException("Both the slots and the icon are required. "
                    + "To leave a slot empty, simply do not bind an icon to it.");
        }
        iconBindings.add(new IconBinding(slots, icon));
        return this;
    }

    /**
     * Declares a group of slots that render together and re-render alone.
     *
     * <p>The lambda runs once per viewer, when their view is built, so a state created inside it
     * with {@code c.remember(...)} belongs to that player. State meant to be shared lives outside,
     * in a {@code State.of(...)} that several components remember.</p>
     */
    @Nonnull
    public Gui component(@Nonnull Consumer<GuiComponent> declaration) {
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
    public Gui addRegion(@Nonnull Region region) {
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
    public Gui policy(@Nonnull ClickPolicy policy) {
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
    public Gui debounce(long millis) {
        this.debounceMillis = Math.max(0L, millis);
        return this;
    }

    /**
     * Runs when the screen goes away - the player closed it, disconnected, changed world, or the
     * server is shutting the framework down. Always: it is where an item held by the screen becomes
     * an item back in the player's hands.
     */
    @Nonnull
    public Gui onClose(@Nullable Consumer<CloseContext> onClose) {
        this.onClose = onClose;
        return this;
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

    @Nonnull
    public List<IconBinding> getIconBindings() {
        return Collections.unmodifiableList(iconBindings);
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
