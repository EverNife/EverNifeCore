package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.minecraft.gui.view.ClickContext;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemBase;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEdit;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.StandardParts;
import br.com.finalcraft.evernifecore.minecraft.itemstack.itembuilder.FCItemBuilder;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One item on a screen, with what happens when it is clicked and who is allowed to see it.
 *
 * <p>This is the single icon type of the framework: the same object serves an icon written by hand,
 * one produced by a list render and one restyled by the admin's YAML. It is the end of the
 * {@code FCItemFactory} chain - {@code FCItemFactory.from(...).displayName(...).onClick(...)}
 * already answers an {@code Icon}.</p>
 *
 * <p>The canonical stack is private and every read hands out a copy, so an icon shared by two
 * viewers cannot be mutated through one of them. Text is resolved LATE, in
 * {@link #renderFor(Player)}: two players reading different languages get two different stacks out
 * of this one object.</p>
 */
public class Icon {

    private ItemStack itemStack;
    private String permission = "";
    private boolean background = false;
    private Consumer<ClickContext> onClick;
    private long everyTicks = 0L;
    private Consumer<Icon> renderer;

    private final Map<String, ItemStack> states = new LinkedHashMap<>();
    private Supplier<String> currentState;
    private final CompoundReplacer scopes = new CompoundReplacer();
    //a screen renders the same language block once per viewer per redraw; reading it is worth doing once
    private final Map<String, List<ItemEdit>> localeEdits = new LinkedHashMap<>();
    private IconLocale locale;
    private ECPluginData localeOwner;
    private boolean integrateToPAPI = false;

    public Icon(@Nonnull ItemStack itemStack) {
        setItemStack(itemStack);
    }

    @Nonnull
    public static Icon of(@Nonnull ItemStack itemStack) {
        return new Icon(itemStack);
    }

    /**
     * An icon with no appearance of its own: it claims slots without painting anything over what is
     * already there, which is what an area whose contents come from elsewhere needs.
     */
    @Nonnull
    public static Icon empty() {
        return new Icon(new ItemStack(Material.AIR));
    }

    @Nonnull
    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    public void setItemStack(@Nonnull ItemStack itemStack) {
        if (itemStack == null) {
            throw new IllegalArgumentException("An Icon needs an ItemStack. To hide the icon instead, "
                    + "bind it to an empty SlotSet or give it a permission nobody has.");
        }
        this.itemStack = itemStack.clone();
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

    @Nullable
    public Consumer<ClickContext> getOnClick() {
        return onClick;
    }

    public void setOnClick(@Nullable Consumer<ClickContext> onClick) {
        this.onClick = onClick;
    }

    /** Whether {@code viewer} may see this icon at all. An icon without a permission is public. */
    public boolean isVisibleTo(@Nullable Player viewer) {
        return permission.isEmpty() || (viewer != null && viewer.hasPermission(permission));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Construction chain - the form used inline, where the icon is being built and not read
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
    public Icon onClick(@Nullable Consumer<ClickContext> onClick) {
        setOnClick(onClick);
        return this;
    }

    /**
     * Redraws this icon every {@code ticks} while a view showing it is open - {@code every(20)} is
     * once a second, not ten times. The task belongs to that view and is cancelled when it closes;
     * an icon nobody is looking at costs nothing.
     */
    @Nonnull
    public Icon every(long ticks) {
        this.everyTicks = Math.max(0L, ticks);
        return this;
    }

    /** What {@link #every(long)} runs: it edits the icon, and the changed slots are repainted. */
    @Nonnull
    public Icon render(@Nullable Consumer<Icon> renderer) {
        this.renderer = renderer;
        return this;
    }

    public long getEveryTicks() {
        return everyTicks;
    }

    @Nullable
    public Consumer<Icon> getRenderer() {
        return renderer;
    }

    /** Whether this icon redraws itself on a timer, which is what makes a view keep a copy of it. */
    public boolean isAnimated() {
        return everyTicks > 0 && renderer != null;
    }

    public void runRenderer() {
        if (renderer != null) {
            renderer.accept(this);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Named states - a second appearance for the same slot, chosen by the menu at runtime
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Declares an alternative appearance under {@code name}. The state carries a whole item, so it
     * may differ in material as well as in text, and it is restyled on its own in the YAML.
     */
    @Nonnull
    public Icon addState(@Nonnull String name, @Nonnull FCItemBuilder item) {
        return addState(name, item.build());
    }

    @Nonnull
    public Icon addState(@Nonnull String name, @Nonnull ItemStack item) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("A state needs a name - it is the key the menu selects it by "
                    + "and the key the admin restyles it under in the yml.");
        }
        states.put(name.trim(), item.clone());
        return this;
    }

    /**
     * The typed form of {@link #addState(String, ItemStack)}: the constant names the state, and the
     * yml key is {@link IconStates#keyOf(Enum)} of it.
     */
    @Nonnull
    public Icon addState(@Nonnull Enum<?> state, @Nonnull FCItemBuilder item) {
        return addState(state, item.build());
    }

    @Nonnull
    public Icon addState(@Nonnull Enum<?> state, @Nonnull ItemStack item) {
        String name = IconStates.keyOf(state);
        if (name.isEmpty()) {
            throw new IllegalArgumentException(state.name() + " names the icon's own appearance, not an "
                    + "alternative one. Build it with FCItemFactory.from(...) as the icon itself, and keep "
                    + "addState for the other constants.");
        }
        return addState(name, item);
    }

    /** The stack of a named state, or {@code null} when this icon has no such state. */
    @Nullable
    public ItemStack getState(@Nonnull String name) {
        ItemStack state = states.get(name);
        return state == null ? null : state.clone();
    }

    public boolean hasState(@Nonnull String name) {
        return states.containsKey(name);
    }

    @Nonnull
    public Set<String> getStateNames() {
        return Collections.unmodifiableSet(states.keySet());
    }

    /**
     * Which state this icon draws, asked again at every render.
     *
     * <p>This is the dynamic form, for a state name computed at runtime; {@link #states(Class,
     * Supplier)} is the one to reach for otherwise, because it is checked by the compiler and by the
     * scan.</p>
     */
    @Nonnull
    public Icon state(@Nullable Supplier<String> state) {
        this.currentState = state;
        return this;
    }

    /**
     * Which state this icon draws, chosen from {@code type}'s constants. A key under {@code States:}
     * that no constant answers to is reported here, which is where both halves are known.
     */
    @Nonnull
    public <E extends Enum<E>> Icon states(@Nonnull Class<E> type, @Nonnull Supplier<E> state) {
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
        String name = currentState.get();
        return name == null || name.isEmpty() ? null : name;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What is resolved per viewer - language, scopes and placeholders
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Declares this icon's name and lore in {@code lang}. Declaring any language makes the text of
     * this icon viewer-dependent: it is resolved in {@link #renderFor(Player)} instead of being baked
     * into the stack.
     */
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

    /** The per-language text of this icon, or {@code null} while it has none. */
    @Nullable
    public IconLocale getLocale() {
        return locale;
    }

    public void setLocale(@Nullable IconLocale locale) {
        this.locale = locale;
        this.localeEdits.clear();
    }

    /**
     * Whose plugin language answers when the viewer has not chosen one. Without an owner the icon
     * falls back to its first declared language instead.
     */
    public void setLocaleOwner(@Nullable ECPluginData localeOwner) {
        this.localeOwner = localeOwner;
    }

    @Nullable
    public ECPluginData getLocaleOwner() {
        return localeOwner;
    }

    /**
     * Registers placeholders this icon resolves on its own, over and above whatever the menu around
     * it registers. Scopes compose: a second call adds to the first, it does not replace it.
     */
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

    /**
     * What {@code viewer} actually sees: the canonical stack with the language block of their own
     * language applied over it, then the icon's scopes, then PlaceholderAPI when asked for.
     *
     * <p>An icon with no language block and no scope answers a plain copy, so the late path costs
     * nothing for the icons that do not need it.</p>
     */
    @Nonnull
    public ItemStack renderFor(@Nullable Player viewer) {
        return renderFor(viewer, itemStack);
    }

    /** {@link #renderFor(Player)} over a named state instead of the default appearance. */
    @Nonnull
    public ItemStack renderFor(@Nullable Player viewer, @Nullable String stateName) {
        ItemStack state = stateName == null ? null : states.get(stateName);
        return renderFor(viewer, state == null ? itemStack : state);
    }

    private ItemStack renderFor(Player viewer, ItemStack base) {
        ItemStack rendered = base.clone();
        if (locale != null) {
            String lang = languageOf(viewer);
            List<ItemEdit> edits = editsFor(lang);
            if (edits != null) {
                rendered = ItemEngine.get().materialize(ItemBase.of(rendered), edits).getItemStack();
            }
        }
        CompoundReplacer replacer = replacerFor(viewer);
        return replacer == null ? rendered : applyReplacer(rendered, replacer);
    }

    /** The language block of {@code lang}, read once and kept - a redraw asks for it constantly. */
    @Nullable
    private List<ItemEdit> editsFor(@Nullable String lang) {
        String key = lang == null ? "" : lang;
        List<ItemEdit> cached = localeEdits.get(key);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        List<String> lines = linesFor(lang);
        List<ItemEdit> edits = lines == null
                ? Collections.<ItemEdit>emptyList()
                : ItemEngine.get().parse(lines).getEdits();
        localeEdits.put(key, edits);
        return edits.isEmpty() ? null : edits;
    }

    /**
     * The block that answers for {@code lang}: their own, then the owning plugin's, then whatever the
     * icon declared first. The middle step is the one worth having - a viewer whose language nobody
     * wrote reads the language the server itself runs in, not the first one the developer typed.
     */
    @Nullable
    private List<String> linesFor(@Nullable String lang) {
        List<String> own = lang == null ? null : locale.get(lang);
        if (own != null) {
            return own;
        }
        if (localeOwner != null) {
            List<String> serverWide = locale.get(FCLocaleManager.getLangOf(localeOwner));
            if (serverWide != null) {
                return serverWide;
            }
        }
        return locale.resolve(null);
    }

    /**
     * The language this icon reads to {@code viewer} in: theirs, the owning plugin's, and finally
     * {@code null}, which leaves the first language the icon declared to answer.
     */
    @Nullable
    private String languageOf(@Nullable Player viewer) {
        if (localeOwner == null) {
            return null;
        }
        return viewer == null ? FCLocaleManager.getLangOf(localeOwner)
                : FCLocaleManager.getLangOf(FCBukkitUtil.adapt(viewer), localeOwner);
    }

    @Nullable
    private CompoundReplacer replacerFor(@Nullable Player viewer) {
        if (scopes.isEmpty() && !integrateToPAPI) {
            return null;
        }
        CompoundReplacer replacer = scopes.copy();
        if (integrateToPAPI && viewer != null) {
            replacer.usePAPI(FCBukkitUtil.adapt(viewer));
        }
        return replacer;
    }

    private static ItemStack applyReplacer(ItemStack rendered, CompoundReplacer replacer) {
        ItemMeta meta = rendered.getItemMeta();
        if (meta == null) {
            return rendered;
        }
        if (meta.hasDisplayName()) {
            meta.setDisplayName(replacer.apply(meta.getDisplayName()));
        }
        if (meta.hasLore()) {
            meta.setLore(replacer.apply(new ArrayList<>(meta.getLore())));
        }
        rendered.setItemMeta(meta);
        return rendered;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Editing the item - the same vocabulary as the factory, so a render function needs no second type
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Replaces what this icon looks like, keeping what it does: permission, handlers, scopes and
     * language block all survive. It is how a list entry starts from the template and then says which
     * item it actually is.
     */
    @Nonnull
    public Icon from(@Nonnull FCItemBuilder item) {
        return from(item.build());
    }

    @Nonnull
    public Icon from(@Nonnull ItemStack item) {
        setItemStack(item);
        return this;
    }

    /** {@link #from(ItemStack)} plus the other icon's named states - its whole appearance. */
    @Nonnull
    public Icon from(@Nonnull Icon item) {
        setItemStack(item.itemStack);
        for (Map.Entry<String, ItemStack> state : item.states.entrySet()) {
            states.put(state.getKey(), state.getValue().clone());
        }
        return this;
    }

    /** Runs the full item factory over this icon's stack and keeps the result. */
    @Nonnull
    public Icon edit(@Nonnull Consumer<FCItemBuilder> edit) {
        FCItemBuilder builder = FCItemFactory.from(itemStack);
        edit.accept(builder);
        setItemStack(builder.build());
        return this;
    }

    @Nonnull
    public Icon displayName(@Nonnull String displayName) {
        return edit(builder -> builder.displayName(displayName));
    }

    @Nonnull
    public Icon lore(@Nonnull String... lore) {
        return lore(Arrays.asList(lore));
    }

    @Nonnull
    public Icon lore(@Nonnull List<String> lore) {
        return edit(builder -> builder.lore(lore));
    }

    @Nonnull
    public Icon amount(int amount) {
        return edit(builder -> builder.amount(amount));
    }

    /** Sets the raw nbt of this icon's stack, in the same {@code {CustomModelData:1042}} form the yml takes. */
    @Nonnull
    public Icon nbt(@Nonnull String nbt) {
        setItemStack(ItemEngine.get().transform(itemStack,
                Collections.singletonList(StandardParts.NBT + ":" + nbt)));
        return this;
    }

    /** Whether the stack itself carries text - which is what a language block would silently overwrite. */
    public boolean hasBakedText() {
        ItemMeta meta = itemStack.getItemMeta();
        return meta != null && (meta.hasDisplayName() || meta.hasLore());
    }

    /** An independent copy: same stack, same permission, same handlers, no shared mutable state. */
    @Nonnull
    public Icon copy() {
        Icon copy = new Icon(itemStack);
        copy.permission = this.permission;
        copy.background = this.background;
        copy.onClick = this.onClick;
        copy.everyTicks = this.everyTicks;
        copy.renderer = this.renderer;
        copy.states.putAll(this.states);
        copy.currentState = this.currentState;
        copy.scopes.appendReplacer(this.scopes);
        copy.locale = this.locale == null ? null : this.locale.copy();
        copy.localeOwner = this.localeOwner;
        copy.integrateToPAPI = this.integrateToPAPI;
        return copy;
    }

    @Override
    public String toString() {
        return "Icon{" + itemStack.getType() + (permission.isEmpty() ? "" : ", permission=" + permission)
                + (background ? ", background" : "") + (states.isEmpty() ? "" : ", states=" + states.keySet())
                + (locale == null ? "" : ", " + locale) + "}";
    }

}
