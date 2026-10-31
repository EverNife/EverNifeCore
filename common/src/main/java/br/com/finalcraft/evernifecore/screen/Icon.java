package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * What a slot looks like - and only that. The item ({@link ItemSpec}, with candidates for a renderer that does not know
 * the first), the item-data lines the renderer applies as written, named states, per-language text, placeholder scopes,
 * who may see it and where it stands among the icons claiming the same slot. What a click on it DOES, when it is
 * visible and how it animates belong to the element that paints it ({@link Button} and its siblings).
 *
 * <p>Text is resolved LATE, in {@link #renderFor(FPlayer, String, CompoundReplacer)}: two players reading different
 * languages get two different {@link RenderedIcon}s out of this one object. Everything an icon holds is immutable or
 * copied on read, so an icon shared by two viewers cannot be changed through one of them.</p>
 */
public class Icon {

    /** The id of nothing: {@link #empty()} draws it, and a slot whose top paint is air shows what is under it. */
    public static final String AIR = "minecraft:air";

    private Look look;
    private String name;
    private String permission = "";
    private boolean background = false;
    private int order = 0;

    private final Map<String, Look> states = new LinkedHashMap<>();
    private final Map<String, IconLocale> stateLocales = new LinkedHashMap<>();
    private Supplier<String> currentState;
    private Class<? extends Enum<?>> stateType;
    private final CompoundReplacer scopes = new CompoundReplacer();
    private IconLocale locale;
    private ECPluginData localeOwner;
    private boolean integrateToPAPI = false;
    //the icon a copy was taken from, so the copy a screen drew still answers to the one the plugin declared
    private Icon origin;

    public Icon(@Nonnull ItemSpec item) {
        this.look = new Look(requireItem(item), Collections.<ItemSpec>emptyList(), Collections.<String>emptyList());
    }

    @Nonnull
    public static Icon of(@Nonnull ItemSpec item) {
        return new Icon(item);
    }

    /** An icon drawn as {@code first}, or as the first of {@code fallbacks} the renderer knows when it does not know it. */
    @Nonnull
    public static Icon of(@Nonnull ItemSpec first, @Nonnull ItemSpec... fallbacks) {
        Icon icon = new Icon(first);
        icon.look = icon.look.withFallbacks(Arrays.asList(fallbacks));
        return icon;
    }

    /** {@code materialId} with no text of its own - {@code Icon.of("minecraft:stone")}. */
    @Nonnull
    public static Icon of(@Nonnull String materialId) {
        return new Icon(ItemSpec.of(materialId, 1));
    }

    /**
     * An icon with no appearance of its own: it claims slots without painting anything over what is already there,
     * which is what an area whose contents come from elsewhere needs.
     */
    @Nonnull
    public static Icon empty() {
        return of(AIR);
    }

    private static ItemSpec requireItem(ItemSpec item) {
        if (item == null) {
            throw new IllegalArgumentException("An Icon needs an ItemSpec. To hide the icon instead, bind it to an empty "
                    + "SlotSet or give it a permission nobody has.");
        }
        return item;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Who wins a slot two icons claim - the one arithmetic, read by both reports and by the screen
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Which of two claims to the same slot of the same layer is the one that shows: lowest {@code order} first, the
     * name breaking a tie, a nameless icon last. Deliberately independent of declaration order, which the JVM does not
     * promise to keep stable between runs.
     */
    public static int compareForSlot(int leftOrder, @Nullable String leftName, int rightOrder, @Nullable String rightName) {
        int byOrder = Integer.compare(leftOrder, rightOrder);
        if (byOrder != 0) {
            return byOrder;
        }
        if (leftName == null || rightName == null) {
            //an icon bound straight to a slot has no key to compare, and loses to one that has
            return leftName == rightName ? 0 : leftName == null ? 1 : -1;
        }
        return leftName.compareTo(rightName);
    }

    /** {@link #compareForSlot(int, String, int, String)} over two icons: the winner of a slot comes first. */
    public static final Comparator<Icon> BY_SLOT_PRIORITY = (left, right) ->
            compareForSlot(left.getOrder(), left.getName(), right.getOrder(), right.getName());

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading
    // -----------------------------------------------------------------------------------------------------------------

    /** The item this icon draws, with the text baked into it (not the per-language one). */
    @Nonnull
    public ItemSpec getItem() {
        return look.item;
    }

    /** Replaces the item, keeping the candidates and the item-data lines. */
    public void setItem(@Nonnull ItemSpec item) {
        this.look = new Look(requireItem(item), look.fallbacks, look.dataLines);
    }

    @Nonnull
    public List<ItemSpec> getFallbacks() {
        return look.fallbacks;
    }

    /** The item-data lines the renderer applies as written: {@code nbt:...}, {@code enchant:...}. */
    @Nonnull
    public List<String> getDataLines() {
        return look.dataLines;
    }

    /** The layout key this icon answers to - the field name, which is also its key in the yml - or {@code null}. */
    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    @Nonnull
    public String getPermission() {
        return permission;
    }

    public void setPermission(@Nullable String permission) {
        this.permission = permission == null ? "" : permission;
    }

    public boolean isBackground() {
        return background;
    }

    public void setBackground(boolean background) {
        this.background = background;
    }

    /** Where this icon stands among the icons claiming the same slot - see {@link #compareForSlot}. */
    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    /** Whether {@code viewer} may see this icon: an icon with no permission is public. */
    public boolean isVisibleTo(@Nullable FPlayer viewer) {
        return permission.isEmpty() || (viewer != null && viewer.hasPermission(permission));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Construction chain
    // -----------------------------------------------------------------------------------------------------------------

    /** Only this permission's holders see the icon; to everyone else the slot is simply not painted. */
    @Nonnull
    public Icon permission(@Nullable String permission) {
        setPermission(permission);
        return this;
    }

    /** Paints the icon on the background layer, underneath the content of the screen. */
    @Nonnull
    public Icon background() {
        return background(true);
    }

    @Nonnull
    public Icon background(boolean background) {
        setBackground(background);
        return this;
    }

    @Nonnull
    public Icon displayName(@Nonnull String displayName) {
        setItem(look.item.withText(displayName, look.item.getLore()));
        return this;
    }

    @Nonnull
    public Icon lore(@Nonnull String... lore) {
        return lore(Arrays.asList(lore));
    }

    @Nonnull
    public Icon lore(@Nonnull List<String> lore) {
        setItem(look.item.withText(look.item.getDisplayName(), lore));
        return this;
    }

    @Nonnull
    public Icon amount(int amount) {
        setItem(look.item.withAmount(amount));
        return this;
    }

    /** Sets the raw nbt the renderer applies, in the same {@code {CustomModelData:1042}} form the yml takes. */
    @Nonnull
    public Icon nbt(@Nonnull String nbt) {
        return data(ItemDataLines.NBT + ":" + nbt);
    }

    /** Adds an item-data line the renderer applies as written; a second line of the same key replaces the first. */
    @Nonnull
    public Icon data(@Nonnull String line) {
        List<String> lines = new ArrayList<>();
        String key = ItemDataLines.keyOf(line);
        for (String existing : look.dataLines) {
            if (!ItemDataLines.keyOf(existing).equalsIgnoreCase(key)) {
                lines.add(existing);
            }
        }
        lines.add(line);
        this.look = new Look(look.item, look.fallbacks, lines);
        return this;
    }

    /** Whether the item itself carries text - which is what a language block would silently overwrite. */
    public boolean hasBakedText() {
        return !look.item.getDisplayName().isEmpty() || !look.item.getLore().isEmpty();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Named states - a second appearance for the same slot, chosen by the menu at runtime
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Declares an alternative appearance under {@code name}. It carries a whole item, so it may differ in material as
     * well as in text, and its text is its own - see {@link #addStateLocale(String, String, String, String...)}.
     */
    @Nonnull
    public Icon addState(@Nonnull String name, @Nonnull ItemSpec item) {
        states.put(requireStateName(name), new Look(requireItem(item), Collections.<ItemSpec>emptyList(),
                Collections.<String>emptyList()));
        return this;
    }

    /** {@link #addState(String, ItemSpec)} with another icon's appearance, its item-data lines included. */
    @Nonnull
    public Icon addState(@Nonnull String name, @Nonnull Icon appearance) {
        states.put(requireStateName(name), appearance.look);
        return this;
    }

    /** The typed form: the constant names the state, and the yml key is {@link IconStates#keyOf(Enum)} of it. */
    @Nonnull
    public Icon addState(@Nonnull Enum<?> state, @Nonnull ItemSpec item) {
        String key = IconStates.keyOf(state);
        if (key.isEmpty()) {
            throw new IllegalArgumentException(state.name() + " names the icon's own appearance, not an alternative one. "
                    + "Give it to Icon.of(...) as the icon itself, and keep addState for the other constants.");
        }
        return addState(key, item);
    }

    private static String requireStateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("A state needs a name - it is the key the menu selects it by and the key "
                    + "the admin restyles it under in the yml.");
        }
        return name.trim();
    }

    /** The item of a named state, or {@code null} when this icon has no such state. */
    @Nullable
    public ItemSpec getState(@Nonnull String name) {
        Look state = states.get(name);
        return state == null ? null : state.item;
    }

    /** The item-data lines of a named state; empty when it has none or there is no such state. */
    @Nonnull
    public List<String> getStateDataLines(@Nonnull String name) {
        Look state = states.get(name);
        return state == null ? Collections.<String>emptyList() : state.dataLines;
    }

    /** Replaces a state's appearance as a whole - the scanner's door for what the yml says about it. */
    public void setState(@Nonnull String name, @Nonnull ItemSpec item, @Nonnull List<String> dataLines) {
        states.put(requireStateName(name), new Look(requireItem(item), Collections.<ItemSpec>emptyList(), dataLines));
    }

    public boolean hasState(@Nonnull String name) {
        return states.containsKey(name);
    }

    @Nonnull
    public Set<String> getStateNames() {
        return Collections.unmodifiableSet(states.keySet());
    }

    /** Declares the name and lore state {@code state} reads in {@code lang}. */
    @Nonnull
    public Icon addStateLocale(@Nonnull String state, @Nonnull String lang, @Nullable String name, @Nonnull String... lore) {
        return addStateLocale(state, lang, IconLocale.linesOf(name, Arrays.asList(lore)));
    }

    /** Declares a state's language block in its item-data form, which is how the yml holds it. */
    @Nonnull
    public Icon addStateLocale(@Nonnull String state, @Nonnull String lang, @Nonnull List<String> itemDataLines) {
        String key = requireStateName(state);
        IconLocale stateLocale = stateLocales.get(key);
        if (stateLocale == null) {
            stateLocale = new IconLocale();
            stateLocales.put(key, stateLocale);
        }
        stateLocale.put(lang, itemDataLines);
        return this;
    }

    @Nullable
    public IconLocale getStateLocale(@Nonnull String state) {
        return stateLocales.get(state);
    }

    public void setStateLocale(@Nonnull String state, @Nullable IconLocale stateLocale) {
        if (stateLocale == null) {
            stateLocales.remove(state);
        } else {
            stateLocales.put(state, stateLocale);
        }
    }

    /** The enum whose constants name this icon's states, or {@code null} when nothing typed them. */
    @Nullable
    public Class<? extends Enum<?>> getStateType() {
        return stateType;
    }

    /** Which state this icon draws, asked again at every render - the dynamic form, for a name computed at runtime. */
    @Nonnull
    public Icon state(@Nullable Supplier<String> state) {
        this.currentState = state;
        return this;
    }

    /** Which state this icon draws, chosen from {@code type}'s constants; unknown keys under {@code States:} are reported. */
    @Nonnull
    public <E extends Enum<E>> Icon states(@Nonnull Class<E> type, @Nonnull Supplier<E> state) {
        this.stateType = type;
        IconStates.warnUnknownKeys(type, states.keySet(), toString());
        return state(() -> {
            E chosen = state.get();
            return chosen == null ? null : IconStates.keyOf(chosen);
        });
    }

    /** The state to draw right now, or {@code null} for this icon's own appearance. */
    @Nullable
    public String getCurrentState() {
        if (currentState == null) {
            return null;
        }
        String chosen = currentState.get();
        return chosen == null || chosen.isEmpty() ? null : chosen;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What is resolved per viewer - language, scopes and placeholders
    // -----------------------------------------------------------------------------------------------------------------

    /** Declares this icon's name and lore in {@code lang}: its text becomes viewer-dependent. */
    @Nonnull
    public Icon addLocale(@Nonnull String lang, @Nullable String name, @Nonnull String... lore) {
        return addLocale(lang, name, Arrays.asList(lore));
    }

    @Nonnull
    public Icon addLocale(@Nonnull String lang, @Nullable String name, @Nonnull List<String> lore) {
        return addLocale(lang, IconLocale.linesOf(name, lore));
    }

    /** Declares a language block straight in its item-data form, which is how the yml holds it. */
    @Nonnull
    public Icon addLocale(@Nonnull String lang, @Nonnull List<String> itemDataLines) {
        if (locale == null) {
            locale = new IconLocale();
        }
        locale.put(lang, itemDataLines);
        return this;
    }

    @Nullable
    public IconLocale getLocale() {
        return locale;
    }

    public void setLocale(@Nullable IconLocale locale) {
        this.locale = locale;
    }

    /** Whose plugin language answers when the viewer has not chosen one; without an owner the first declared one does. */
    public void setLocaleOwner(@Nullable ECPluginData localeOwner) {
        this.localeOwner = localeOwner;
    }

    @Nullable
    public ECPluginData getLocaleOwner() {
        return localeOwner;
    }

    /** Placeholders this icon resolves on its own, over what the screen registers. A second call adds to the first. */
    @Nonnull
    public <O> Icon addScope(@Nonnull RegexReplacer<O> replacer, @Nonnull O object) {
        scopes.appendReplacer(replacer, object);
        return this;
    }

    @Nonnull
    public CompoundReplacer getScopes() {
        return scopes;
    }

    /** Whether PlaceholderAPI runs over this icon's text, with the viewer as the placeholder subject. */
    @Nonnull
    public Icon integrateToPAPI(boolean integrateToPAPI) {
        this.integrateToPAPI = integrateToPAPI;
        return this;
    }

    public boolean isIntegrateToPAPI() {
        return integrateToPAPI;
    }

    @Nonnull
    public RenderedIcon renderFor(@Nullable FPlayer viewer) {
        return renderFor(viewer, null, null);
    }

    @Nonnull
    public RenderedIcon renderFor(@Nullable FPlayer viewer, @Nullable String stateName) {
        return renderFor(viewer, stateName, null);
    }

    /**
     * What {@code viewer} sees: the item (or the named state's) with the language block of their own language over it,
     * then the icon's scopes, then the screen's replacers, then PlaceholderAPI when asked for. While a state is on
     * screen the block that answers is the state's own, never the icon's.
     */
    @Nonnull
    public RenderedIcon renderFor(@Nullable FPlayer viewer, @Nullable String stateName, @Nullable CompoundReplacer menuReplacer) {
        return renderFor(viewer, stateName, menuReplacer, null);
    }

    /**
     * {@link #renderFor(FPlayer, String, CompoundReplacer)} with the opener's {@code {0}}..{@code {n}} put in by
     * {@code arguments} right after the language block, before any placeholder: an argument may itself carry one.
     */
    @Nonnull
    public RenderedIcon renderFor(@Nullable FPlayer viewer, @Nullable String stateName, @Nullable CompoundReplacer menuReplacer,
                                  @Nullable UnaryOperator<String> arguments) {
        Look state = stateName == null ? null : states.get(stateName);
        Look drawn = state == null ? look : state;
        ItemSpec item = drawn.item;
        IconLocale spoken = state == null ? locale : stateLocales.get(stateName);
        if (spoken != null) {
            List<String> lines = linesFor(spoken, languageOf(viewer));
            if (lines != null) {
                item = ItemDataLines.applyText(item, lines);
            }
        }
        if (arguments != null) {
            List<String> lore = new ArrayList<>(item.getLore().size());
            for (String line : item.getLore()) {
                lore.add(arguments.apply(line));
            }
            item = item.withText(arguments.apply(item.getDisplayName()), lore);
        }
        CompoundReplacer replacer = replacerFor(viewer, menuReplacer);
        if (replacer != null) {
            item = item.withText(replacer.apply(item.getDisplayName()), replacer.apply(new ArrayList<>(item.getLore())));
        }
        return new RenderedIcon(item, drawn.fallbacks, drawn.dataLines);
    }

    /** The block for {@code lang}: their own, then the owning plugin's, then whatever the icon declared first. */
    @Nullable
    private List<String> linesFor(IconLocale spoken, @Nullable String lang) {
        List<String> own = lang == null ? null : spoken.get(lang);
        if (own != null) {
            return own;
        }
        if (localeOwner != null) {
            List<String> serverWide = spoken.get(FCLocaleManager.getLangOf(localeOwner));
            if (serverWide != null) {
                return serverWide;
            }
        }
        return spoken.resolve(null);
    }

    @Nullable
    private String languageOf(@Nullable FPlayer viewer) {
        if (localeOwner == null) {
            return null;
        }
        return viewer == null ? FCLocaleManager.getLangOf(localeOwner) : FCLocaleManager.getLangOf(viewer, localeOwner);
    }

    @Nullable
    private CompoundReplacer replacerFor(@Nullable FPlayer viewer, @Nullable CompoundReplacer menuReplacer) {
        boolean fromMenu = menuReplacer != null && !menuReplacer.isEmpty();
        if (scopes.isEmpty() && !fromMenu && !integrateToPAPI) {
            return null;
        }
        CompoundReplacer replacer = scopes.copy();
        if (fromMenu) {
            replacer.appendReplacer(menuReplacer);
        }
        if (integrateToPAPI && viewer != null) {
            replacer.usePAPI(viewer);
        }
        return replacer;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Re-dressing - a list entry starts from the template and then says which item it actually is
    // -----------------------------------------------------------------------------------------------------------------

    /** Replaces what this icon looks like with {@code item}, keeping permission, scopes, states and language block. */
    @Nonnull
    public Icon from(@Nonnull ItemSpec item) {
        this.look = new Look(requireItem(item), Collections.<ItemSpec>emptyList(), Collections.<String>emptyList());
        return this;
    }

    /** {@link #from(ItemSpec)} with another icon's whole appearance and its named states, text of their own included. */
    @Nonnull
    public Icon from(@Nonnull Icon item) {
        this.look = item.look;
        states.putAll(item.states);
        for (Map.Entry<String, IconLocale> stateLocale : item.stateLocales.entrySet()) {
            stateLocales.put(stateLocale.getKey(), stateLocale.getValue().copy());
        }
        return this;
    }

    /** The {@code DisplayItem} lines of this icon, as the yml holds them; text omitted when it lives in a language block. */
    @Nonnull
    public List<String> toDisplayItem(boolean textLivesInLocale) {
        return ItemDataLines.write(look.item, look.dataLines, textLivesInLocale);
    }

    /** An icon read back from its {@code DisplayItem} lines - the inverse of {@link #toDisplayItem(boolean)}. */
    @Nonnull
    public static Icon fromDisplayItem(@Nonnull List<String> lines) {
        Icon icon = new Icon(ItemDataLines.readItem(lines));
        icon.look = new Look(icon.look.item, icon.look.fallbacks, ItemDataLines.rawLines(lines));
        return icon;
    }

    /** An independent copy: same appearance, same permission, same states, no shared mutable state. */
    @Nonnull
    public Icon copy() {
        Icon copy = new Icon(look.item);
        copy.look = this.look;
        copy.name = this.name;
        copy.permission = this.permission;
        copy.background = this.background;
        copy.order = this.order;
        copy.states.putAll(this.states);
        for (Map.Entry<String, IconLocale> stateLocale : this.stateLocales.entrySet()) {
            copy.stateLocales.put(stateLocale.getKey(), stateLocale.getValue().copy());
        }
        copy.currentState = this.currentState;
        copy.stateType = this.stateType;
        copy.scopes.appendReplacer(this.scopes);
        copy.locale = this.locale == null ? null : this.locale.copy();
        copy.localeOwner = this.localeOwner;
        copy.integrateToPAPI = this.integrateToPAPI;
        copy.origin = getOrigin();
        return copy;
    }

    /** The declared icon this one was copied from - itself when it is no copy. */
    @Nonnull
    Icon getOrigin() {
        return origin == null ? this : origin;
    }

    @Override
    public String toString() {
        return "Icon{" + look.item.getMaterialId() + (permission.isEmpty() ? "" : ", permission=" + permission)
                + (background ? ", background" : "") + (states.isEmpty() ? "" : ", states=" + states.keySet())
                + (locale == null ? "" : ", " + locale) + "}";
    }

    /** One appearance: immutable, so icons and states share it freely. */
    private static final class Look {

        private final ItemSpec item;
        private final List<ItemSpec> fallbacks;
        private final List<String> dataLines;

        private Look(ItemSpec item, List<ItemSpec> fallbacks, List<String> dataLines) {
            this.item = item;
            this.fallbacks = Collections.unmodifiableList(new ArrayList<>(fallbacks));
            this.dataLines = Collections.unmodifiableList(new ArrayList<>(dataLines));
        }

        private Look withFallbacks(List<ItemSpec> fallbacks) {
            for (ItemSpec fallback : fallbacks) {
                requireItem(fallback);
            }
            return new Look(item, fallbacks, dataLines);
        }

    }

}
