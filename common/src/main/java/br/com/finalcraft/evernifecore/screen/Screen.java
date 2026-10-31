package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A screen: what it shows, where, and what a click on it may do - {@code Screen.of(3).title("...").icon(13, ...).open(player)}.
 *
 * <p>It is a description, not a window: the same screen opens for any number of players, and each of them gets a
 * {@link ScreenView}. Who draws it is a {@link ScreenRenderer}, chosen per player when it opens ({@link Screens#open}).</p>
 *
 * <p>Nothing moves until it is opened up: the default policy is {@link ClickPolicy#DENY_ALL}, so items cannot be
 * taken, placed, dragged or shift-moved out of a screen until a region says so.</p>
 *
 * <p>{@code L} is the layout this screen was built from: {@link #of(Layout)} paints every icon the file resolved and
 * types {@link #icon(Function)}, so {@code icon(l -> l.UPGRADE)} is checked by the compiler. A screen sized by hand has
 * no layout and only the raw-slot forms.</p>
 */
public class Screen<L extends Layout> {

    /** Enough to swallow a double click, short enough to be invisible to a human. {@code 0} switches it off. */
    public static final long DEFAULT_DEBOUNCE_MILLIS = 200L;

    private final int rows;
    private final L layout;

    private ScreenKey key;
    private ECPluginData owner;
    private Supplier<String> title = () -> "";
    private boolean titleOverridden = false;
    private ClickPolicy policy = ClickPolicy.DENY_ALL;
    private long debounceMillis = DEFAULT_DEBOUNCE_MILLIS;
    private ScreenFallback onMissingCapability = ScreenFallback.REFUSE;

    private final Map<String, IconBinding> layoutIcons = new LinkedHashMap<>();
    private final Set<String> claimedIcons = new LinkedHashSet<>();
    private final List<Element> elements = new ArrayList<>();
    private final List<Consumer<ScreenComponent>> componentDeclarations = new ArrayList<>();
    private final List<StorageBinding> storages = new ArrayList<>();
    private final Map<String, NamedRegion> regions = new LinkedHashMap<>();
    private final CompoundReplacer replacers = new CompoundReplacer();
    private Subject<IPlayerData> subject;
    private final Set<Consumer<ScreenComponent>> backgroundDeclarations = Collections.newSetFromMap(new IdentityHashMap<>());
    private Consumer<CloseContext<L>> onClose;

    protected Screen(int rows, @Nullable L layout) {
        Grid.rows(rows); //fail here, on the line the caller wrote, not later when the window is asked for
        this.rows = rows;
        this.layout = layout;
        this.owner = layout == null ? null : layout.getPlugin();
        layoutIcons.putAll(paintedIconsOf(layout));
    }

    private static Map<String, IconBinding> paintedIconsOf(Layout layout) {
        Map<String, IconBinding> painted = new LinkedHashMap<>();
        if (layout != null) {
            for (Layout.PlacedIcon placed : layout.getIcons().values()) {
                if (placed.isVisible()) {
                    painted.put(placed.getName(), new IconBinding(placed.getSlots(), placed.getIcon()));
                }
            }
        }
        return painted;
    }

    /** A screen of {@code rows} rows of nine. */
    @Nonnull
    public static Screen<Layout> of(int rows) {
        return new Screen<>(rows, null);
    }

    /**
     * A screen sized, titled and decorated by {@code layout}: every icon the file resolved is already painted, and
     * {@link #icon(Function)} is how one of them gains behaviour. The title follows each viewer's own language; a
     * {@link #title(String)} set by hand wins over the layout's, for every viewer.
     */
    @Nonnull
    public static <L extends Layout> Screen<L> of(@Nonnull L layout) {
        return new Screen<>(layout.getRows(), layout);
    }

    /** {@link #of(Layout)} over {@link Layouts#of(Class)} - the form that reads the file itself. */
    @Nonnull
    public static <L extends Layout> Screen<L> of(@Nonnull Class<L> layoutType) {
        return of(Layouts.of(layoutType));
    }

    /** {@code layout}, or the refusal that teaches - for a subclass constructor that reads it before super(). */
    @Nonnull
    protected static <L extends Layout> L requiredLayout(@Nullable L layout) {
        if (layout == null) {
            throw new IllegalArgumentException("A screen built from a layout cannot be built without one. "
                    + "Read it with Layouts.of(MyLayout.class).");
        }
        return layout;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Declaration
    // -----------------------------------------------------------------------------------------------------------------

    /** The key others replace this screen by; {@code null} for an ad-hoc screen. */
    @Nonnull
    public Screen<L> key(@Nullable ScreenKey key) {
        this.key = key;
        return this;
    }

    /** The plugin whose content this is - the caller logic on it runs as. A layout's plugin by default. */
    @Nonnull
    public Screen<L> owner(@Nullable ECPluginData owner) {
        this.owner = owner;
        return this;
    }

    @Nonnull
    public Screen<L> title(@Nonnull String title) {
        return title(() -> title);
    }

    /**
     * A title read again on every pass, so it can carry a page number or a filter. Only a renderer with
     * {@link RendererCapability#TITLE_CHANGE} shows a change; the chest pays one frame of flicker for it.
     */
    @Nonnull
    public Screen<L> title(@Nonnull Supplier<String> title) {
        this.title = title;
        this.titleOverridden = true;
        return this;
    }

    /** A button on one raw, 0-based slot; its id is {@code slot#<n>} unless {@link Button#id(String)} says otherwise. */
    @Nonnull
    public Button icon(int slot, @Nonnull Icon icon) {
        return icon(Slots.of(slot), icon);
    }

    /** A button on every slot of {@code slots} - see {@link Slots}. */
    @Nonnull
    public Button icon(@Nonnull SlotSet slots, @Nonnull Icon icon) {
        return declare(new Button(this, rawId(slots, icon), requireSlots(slots, icon), icon));
    }

    /**
     * Gives one icon of the layout a behaviour of its own - a click, a state, a cycle - on the slots the file put it on.
     * The button works on a copy per viewer, and the plain copy the layout painted stops being drawn.
     */
    @Nonnull
    public Button icon(@Nonnull Function<L, Icon> selector) {
        Layout.PlacedIcon placed = takeOver(selector);
        return declare(new Button(this, placed.getName(), placed.getSlots(), placed.getIcon()));
    }

    /** Text only: a layout icon a renderer that draws by element shows as a label. */
    @Nonnull
    public Label label(@Nonnull Function<L, Icon> selector) {
        Layout.PlacedIcon placed = takeOver(selector);
        return declare(new Label(this, placed.getName(), placed.getSlots(), placed.getIcon()));
    }

    /** A field the viewer types into - see {@link InputField}. */
    @Nonnull
    public InputField input(@Nonnull Function<L, Icon> selector) {
        Layout.PlacedIcon placed = takeOver(selector);
        return declare(new InputField(this, placed.getName(), placed.getSlots(), placed.getIcon()));
    }

    /** A bar drawing {@code ratio}, 0 to 1 - see {@link ProgressBar}. */
    @Nonnull
    public ProgressBar progress(@Nonnull Function<L, Icon> selector, @Nonnull State<Double> ratio) {
        Layout.PlacedIcon placed = takeOver(selector);
        return declare(new ProgressBar(this, placed.getName(), placed.getSlots(), placed.getIcon(), ratio));
    }

    /** A dialogue option: {@code text}, picked when {@code gate} passes and is paid, running {@code effects}. */
    @Nonnull
    public Choice choice(@Nonnull Function<L, Icon> selector, @Nonnull FancyText text, @Nonnull Gate gate,
                         @Nonnull List<Effect> effects) {
        Layout.PlacedIcon placed = takeOver(selector);
        return declare(new Choice(this, placed.getName(), placed.getSlots(), placed.getIcon(), text, gate, effects));
    }

    private <B extends Button> B declare(B button) {
        addElement(button);
        Consumer<ScreenComponent> bind = button::bind;
        addComponent(bind);
        if (button.isBackground()) {
            backgroundDeclarations.add(bind);
        }
        return button;
    }

    private String rawId(SlotSet slots, Icon icon) {
        if (icon.getName() != null) {
            return icon.getName();
        }
        SlotSet fixed = slots.resolve(getGrid()); //a shape (Slots.all(), border...) is only slots once it meets the grid
        int[] resolved = fixed.toArray();
        return "slot#" + (resolved.length == 0 ? fixed.serialize() : String.valueOf(resolved[0]));
    }

    private static SlotSet requireSlots(SlotSet slots, Icon icon) {
        if (slots == null || icon == null) {
            throw new IllegalArgumentException("Both the slots and the icon are required. To leave a slot empty, simply "
                    + "do not bind an icon to it.");
        }
        return slots;
    }

    /** Adds {@code element} to the elements renderers address by id; ids are checked when a view opens. */
    void addElement(@Nonnull Element element) {
        elements.add(element);
    }

    /** A list poured into a region, one entry per slot, paginated by the size of the region itself. */
    @Nonnull
    public <T> PagedList<T, L> list(@Nonnull Supplier<List<T>> source) {
        return declare(new PagedList<T, L>(this, source, null));
    }

    @Nonnull
    public <T> PagedList<T, L> list(@Nonnull List<T> source) {
        return list(() -> source);
    }

    /** A source that answers one page at a time; it needs {@link PagedList#total(Supplier)}. */
    @Nonnull
    public <T> PagedList<T, L> list(@Nonnull PagedList.PageSource<T> source) {
        return declare(new PagedList<T, L>(this, null, source));
    }

    private <T> PagedList<T, L> declare(PagedList<T, L> list) {
        addElement(list);
        addComponent(list::bind);
        return list;
    }

    /**
     * Hands the slots of one layout icon over to the viewer: an area they may really take items out of and put items
     * into. Only a renderer with {@link RendererCapability#STORAGE_SLOTS} can draw it; the platform's binding says
     * where the items are kept.
     */
    @Nonnull
    public StorageBinding storage(@Nonnull Function<L, Icon> selector) {
        Layout.PlacedIcon placed = takeOver(selector);
        return declare(new StorageBinding(placed.getName(), placed.getSlots()));
    }

    /** {@link #storage(Function)} over raw slots, on a screen sized by hand. */
    @Nonnull
    public StorageBinding storage(@Nonnull SlotSet slots) {
        if (slots == null) {
            throw new IllegalArgumentException("A storage region needs the slots it occupies. Slots.box(2, 2, 5, 8) "
                    + "is the rectangular form, and storage(l -> l.AREA) is the one a layout names.");
        }
        return declare(new StorageBinding("storage#" + (storages.size() + 1), slots));
    }

    private StorageBinding declare(StorageBinding storage) {
        storages.add(storage);
        return storage;
    }

    /**
     * Declares a group of slots that render together and re-render alone. The lambda runs once per viewer, when their
     * view is built, so a state created inside it with {@code c.remember(...)} belongs to that player.
     */
    @Nonnull
    public Screen<L> addComponent(@Nonnull Consumer<ScreenComponent> declaration) {
        if (declaration == null) {
            throw new IllegalArgumentException("A component needs a declaration - the lambda that remembers its state "
                    + "and says what it renders.");
        }
        componentDeclarations.add(declaration);
        return this;
    }

    /** Declares a named area with its own layer and click policy. */
    @Nonnull
    public Screen<L> addRegion(@Nonnull NamedRegion region) {
        if (region == null) {
            throw new IllegalArgumentException("A screen cannot add a null region. Build one with "
                    + "new NamedRegion(name, slots, policy), or drop the call.");
        }
        if (regions.containsKey(region.getName())) {
            throw new IllegalArgumentException("This screen already has a region named [" + region.getName() + "]. "
                    + "Region names are how clicks address an area, so they have to be unique within a screen.");
        }
        regions.put(region.getName(), region);
        return this;
    }

    /** Placeholders the whole screen answers for - its title and every icon on it. Replacers add up. */
    @Nonnull
    public Screen<L> addReplacer(@Nonnull CompoundReplacer replacer) {
        if (replacer == null) {
            throw new IllegalArgumentException("A screen cannot add a null replacer. Build one with "
                    + "myRegexReplacer.compound(theObject), or drop the call.");
        }
        replacers.appendReplacer(replacer);
        return this;
    }

    @Nonnull
    public <O> Screen<L> addReplacer(@Nonnull RegexReplacer<O> replacer, @Nonnull O object) {
        if (replacer == null || object == null) {
            throw new IllegalArgumentException("A screen cannot add a null replacer or subject. Pass the RegexReplacer "
                    + "and the object it speaks about, or drop the call.");
        }
        replacers.appendReplacer(replacer, object);
        return this;
    }

    /** Makes {@code %playerdata_*%} on this screen speak of {@code subject}, not of the viewer. */
    @Nonnull
    public Screen<L> subject(@Nonnull IPlayerData subject) {
        this.subject = new Subject<>(subject);
        return this;
    }

    /** The policy applied to any slot no region claims. */
    @Nonnull
    public Screen<L> policy(@Nonnull ClickPolicy policy) {
        this.policy = policy == null ? ClickPolicy.DENY_ALL : policy;
        return this;
    }

    /** How long after an accepted click the next one is ignored, in milliseconds. {@code 0} switches it off. */
    @Nonnull
    public Screen<L> debounce(long millis) {
        this.debounceMillis = Math.max(0L, millis);
        return this;
    }

    /** Runs when the screen goes away, whatever the reason - closed, left, shutdown. Always. */
    @Nonnull
    public Screen<L> onClose(@Nullable Consumer<CloseContext<L>> onClose) {
        this.onClose = onClose;
        return this;
    }

    /** What happens when no renderer serving the viewer covers what this screen needs; {@link ScreenFallback#REFUSE} by default. */
    @Nonnull
    public Screen<L> onMissingCapability(@Nonnull ScreenFallback fallback) {
        this.onMissingCapability = fallback == null ? ScreenFallback.REFUSE : fallback;
        return this;
    }

    /** The layout icon {@code selector} names, handed over to whatever binding asked for it. */
    @Nonnull
    public Layout.PlacedIcon takeOver(@Nonnull Function<L, Icon> selector) {
        if (layout == null) {
            throw new IllegalArgumentException("This screen has no layout, so an icon cannot be selected by field. Build "
                    + "it with Screen.of(MyLayout.class) to address icons by name, or bind them to raw slots with "
                    + "icon(slot, icon).");
        }
        String name = layout.getIconName(selector.apply(layout));
        if (name == null) {
            throw new IllegalArgumentException("The selector did not answer an icon of " + layout.getLayoutName()
                    + ". It has to return one of the @IconData fields of the layout itself - l -> l.UPGRADE - and not "
                    + "an icon built on the spot.");
        }
        Layout.PlacedIcon placed = layout.getIcons().get(name);
        if (placed == null) {
            throw new IllegalArgumentException(layout.getLayoutName() + "." + name + " failed to load and was dropped, "
                    + "so nothing can be bound to it. The log says which key of the yml broke it.");
        }
        claimedIcons.add(name);
        return placed;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Opening
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Opens this screen for {@code viewer}; {@code args} are what {@code {0}}..{@code {n}} read in its text.
     * See {@link Screens#open(FPlayer, Screen, Object...)}.
     */
    @Nonnull
    public CompletableFuture<ScreenView> open(@Nonnull FPlayer viewer, @Nonnull Object... args) {
        return Screens.open(viewer, this, args);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading
    // -----------------------------------------------------------------------------------------------------------------

    @Nullable
    public ScreenKey getKey() {
        return key;
    }

    @Nullable
    public ECPluginData getOwner() {
        return owner;
    }

    public int getRows() {
        return rows;
    }

    @Nonnull
    public Grid getGrid() {
        return Grid.rows(rows);
    }

    @Nullable
    public L getLayout() {
        return layout;
    }

    /** The layout as {@code viewer} reads it: the copy for their language when the admin wrote an overlay of it. */
    @Nullable
    public Layout getLayoutFor(@Nullable FPlayer viewer) {
        if (layout == null || viewer == null) {
            return layout;
        }
        return Layouts.of(layout.getClass(), FCLocaleManager.getLangOf(viewer, layout.getPlugin()));
    }

    /** A fresh copy of the placeholders this screen answers for, so composing it cannot write back into the screen. */
    @Nonnull
    public CompoundReplacer getReplacer() {
        //the subject answers last whatever the call order: a screen's own replacer wins over %playerdata_*%
        return subject == null ? replacers.copy() : subject.appendTo(replacers.copy());
    }

    /** Whether PlaceholderAPI runs over this screen's text. It is the layout that asks for it. */
    public boolean isIntegrateToPAPI() {
        return layout != null && layout.isIntegrateToPAPI();
    }

    @Nonnull
    public String getTitle() {
        return getTitleFor(null);
    }

    /** The title as {@code viewer} reads it: their language when a layout titles the screen, then placeholders. */
    @Nonnull
    public String getTitleFor(@Nullable FPlayer viewer) {
        return getTitleFor(viewer, null);
    }

    /** {@link #getTitleFor(FPlayer)} with the opener's {@code {0}}..{@code {n}} put in by {@code arguments} first. */
    @Nonnull
    public String getTitleFor(@Nullable FPlayer viewer, @Nullable UnaryOperator<String> arguments) {
        String resolved = layout != null && !titleOverridden ? layout.getTitleFor(viewer) : title.get();
        if (resolved == null) {
            return "";
        }
        if (arguments != null) {
            resolved = arguments.apply(resolved);
        }
        CompoundReplacer replacer = getReplacer();
        if (viewer != null && isIntegrateToPAPI()) {
            replacer.usePAPI(viewer);
        }
        return replacer.isEmpty() ? resolved : replacer.apply(resolved);
    }

    /** How a message names this screen: its key, or its title. */
    @Nonnull
    public String describe() {
        return key != null ? key.toString() : getTitle();
    }

    @Nonnull
    public ClickPolicy getPolicy() {
        return policy;
    }

    public long getDebounceMillis() {
        return debounceMillis;
    }

    @Nonnull
    public ScreenFallback getOnMissingCapability() {
        return onMissingCapability;
    }

    /** What a renderer must be able to do to draw this screen - derived from its elements, never declared by hand. */
    @Nonnull
    public Set<RendererCapability> getRequiredCapabilities() {
        Set<RendererCapability> needed = EnumSet.noneOf(RendererCapability.class);
        if (!storages.isEmpty()) {
            needed.add(RendererCapability.STORAGE_SLOTS);
        }
        return Collections.unmodifiableSet(needed);
    }

    /**
     * Every element renderers address by id, in declaration order.
     *
     * @throws IllegalArgumentException when two elements share an id - the id is how a renderer patches one of them,
     *                                  so the second would be unreachable
     */
    @Nonnull
    public Map<String, Element> getElements() {
        Map<String, Element> byId = new LinkedHashMap<>();
        for (Element element : elements) {
            if (byId.put(element.getId(), element) != null) {
                throw new IllegalArgumentException("The screen [" + describe() + "] already has an element ["
                        + element.getId() + "]. Ids are how a renderer addresses an element, so give the second one "
                        + "Button.id(\"" + element.getId() + "-2\").");
            }
        }
        return Collections.unmodifiableMap(byId);
    }

    /**
     * The layout's own icons that no binding took over, in PAINT order (the winner of a contested slot last, so it is
     * the one left showing), as {@code exemplar}'s copy of the layout answers them.
     */
    @Nonnull
    public List<IconBinding> getIconBindings(@Nullable Layout exemplar) {
        Map<String, IconBinding> painted = exemplar == null || exemplar == layout ? this.layoutIcons : paintedIconsOf(exemplar);
        List<IconBinding> bindings = new ArrayList<>(painted.size());
        for (Map.Entry<String, IconBinding> entry : painted.entrySet()) {
            if (!claimedIcons.contains(entry.getKey())) {
                bindings.add(entry.getValue());
            }
        }
        bindings.sort(Comparator.comparing(IconBinding::getIcon, Icon.BY_SLOT_PRIORITY).reversed());
        return Collections.unmodifiableList(bindings);
    }

    @Nonnull
    public List<IconBinding> getIconBindings() {
        return getIconBindings(layout);
    }

    @Nonnull
    public List<Consumer<ScreenComponent>> getComponentDeclarations() {
        return Collections.unmodifiableList(componentDeclarations);
    }

    /** Whether {@code declaration} is a button whose icon is a background: it paints under every other component. */
    public boolean isBackgroundDeclaration(@Nonnull Consumer<ScreenComponent> declaration) {
        return backgroundDeclarations.contains(declaration);
    }

    @Nonnull
    public Map<String, NamedRegion> getRegions() {
        return Collections.unmodifiableMap(regions);
    }

    @Nonnull
    public List<StorageBinding> getStorages() {
        return Collections.unmodifiableList(storages);
    }

    @Nullable
    public Consumer<CloseContext<L>> getOnClose() {
        return onClose;
    }

    /** One layout icon and the slots it was put on. */
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
