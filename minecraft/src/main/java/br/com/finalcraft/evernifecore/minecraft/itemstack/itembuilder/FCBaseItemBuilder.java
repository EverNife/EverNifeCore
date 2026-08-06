package br.com.finalcraft.evernifecore.minecraft.itemstack.itembuilder;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.BuiltItem;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemBase;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEdit;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ParsedBlock;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.StandardParts;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.answer.ItemLineProblem;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemProbe;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.ItemRequirement;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.NbtDoor;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.Validate;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A recipe for an {@link ItemStack}: what it should start from and everything that should be done
 * to it, remembered rather than performed.
 *
 * <p>Nothing here touches a server. Every call stages an intention, and {@link #build()} is the one
 * moment an item exists - which is what lets a recipe be written, passed around and inspected on a
 * JVM with no Minecraft behind it. What cannot be judged without a server is deferred; what can is
 * judged now, so a typo in a material still fails on the line that wrote it.</p>
 *
 * <p>{@link #material} replaces the base and the edits are replayed over it, so the flags, the dye
 * and the tag of everything asked for before it survive the change. Copying a chosen handful of
 * fields onto a new item was how each of those quietly did not.</p>
 *
 * @param <B> the builder type, so the methods can answer as the subtype
 */
public abstract class FCBaseItemBuilder<B extends FCBaseItemBuilder<B>> {

    private ItemBase base;
    private final List<ItemEdit> edits = new ArrayList<>();
    private final List<ItemLineProblem> problems = new ArrayList<>();

    protected FCBaseItemBuilder(@Nonnull final ItemBase base) {
        Validate.notNull(base, "Base can't be null!");
        this.base = base;
    }

    /** A recipe that already carries what a block of item-data lines asked for. */
    protected FCBaseItemBuilder(@Nonnull final ItemBase base, @Nonnull final ParsedBlock block) {
        this(base);
        this.edits.addAll(block.getEdits());
        this.problems.addAll(block.getProblems());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  What the item is
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Starts the item over from {@code material}, keeping everything the recipe asks for.
     *
     * @param material the material of the item
     * @return this builder
     */
    @Nonnull
    public B material(@Nonnull final Material material) {
        this.base = ItemBase.of(material);
        return self();
    }

    /**
     * Starts the item over from a bukkit {@code NAME[:durability]} or a namespaced {@code mod:item}.
     *
     * @param itemIdentifier the identifier of the item
     * @return this builder
     * @throws IllegalArgumentException when a bukkit name is not one this server has
     */
    @Nonnull
    public B material(@Nonnull final String itemIdentifier) {
        this.base = ItemBase.ofIdentifier(itemIdentifier);
        return self();
    }

    /**
     * Starts the item over from another stack, keeping everything the recipe asks for.
     *
     * @param itemStack the stack to start from
     * @return this builder
     */
    @Nonnull
    public B material(@Nonnull final ItemStack itemStack) {
        this.base = ItemBase.of(itemStack);
        return self();
    }

    /** Damage on a tool, or the data value that used to pick a colour before 1.13. */
    @Nonnull
    public B durability(final int durability) {
        return set(StandardParts.DURABILITY, durability);
    }

    /** How many items the stack holds. */
    @Nonnull
    public B amount(final int amount) {
        return set(StandardParts.AMOUNT, amount);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Text
    // -----------------------------------------------------------------------------------------------------------------

    /** The name shown on the item, with {@code &} colour codes. */
    @Nonnull
    public B displayName(@Nonnull final String name) {
        return set(StandardParts.NAME, FCColorUtil.colorfy(name));
    }

    /** The lore, one entry per line, with {@code &} colour codes. */
    @Nonnull
    public B lore(@Nonnull final String... lore) {
        return lore(Arrays.asList(lore));
    }

    /** The lore, one entry per line, with {@code &} colour codes. */
    @Nonnull
    public B lore(final List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            return set(StandardParts.LORE, new ArrayList<String>());
        }
        return set(StandardParts.LORE, FCColorUtil.colorfy(lore));
    }

    /**
     * Edits the lore in place.
     *
     * @param lore a consumer over the lore the recipe holds so far
     * @return this builder
     */
    @Nonnull
    public B lore(@Nonnull final Consumer<List<String>> lore) {
        List<String> current = currentLore();
        lore.accept(current);
        return lore(current);
    }

    /**
     * Replaces the lore with whatever {@code lore} makes of it.
     *
     * @param lore a function over the lore the recipe holds so far
     * @return this builder
     */
    @Nonnull
    public B lore(@Nonnull final Function<List<String>, List<String>> lore) {
        return lore(lore.apply(currentLore()));
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Metadata
    // -----------------------------------------------------------------------------------------------------------------

    /** Enchants the item, past the level the enchantment normally allows. */
    @Nonnull
    public B addEnchant(@Nonnull final Enchantment enchantment, final int level,
                        final boolean ignoreLevelRestriction) {
        return meta("enchant " + enchantment.getName(), ItemDataPart.PRIORITY_EARLY,
                meta -> meta.addEnchant(enchantment, level, ignoreLevelRestriction));
    }

    @Nonnull
    public B addEnchant(@Nonnull final Enchantment enchantment, final int level) {
        return addEnchant(enchantment, level, true);
    }

    @Nonnull
    public B addEnchant(@Nonnull final Enchantment enchantment) {
        return addEnchant(enchantment, 1, true);
    }

    @Nonnull
    public B removeEnchantment(@Nonnull final Enchantment enchantment) {
        return meta("remove enchant " + enchantment.getName(), ItemDataPart.PRIORITY_EARLY,
                meta -> meta.removeEnchant(enchantment));
    }

    /** Hides part of the tooltip. Repeated calls pile up. */
    @Nonnull
    public B addItemFlags(@Nonnull final ItemFlag... flags) {
        //Not EnumSet.noneOf(ItemFlag.class): that loads ItemFlag right here, and 1.7.10 has no such
        //class - the part that consumes this set is the one gated on a server that does.
        Set<ItemFlag> wanted = new LinkedHashSet<>(Arrays.asList(flags));
        return add(StandardParts.HIDE_FLAGS, wanted);
    }

    @Nonnull
    public B setUnbreakable() {
        return setUnbreakable(true);
    }

    /** Makes the item survive use. Before 1.11 there was no metadata for it, only the tag. */
    @Nonnull
    public B setUnbreakable(final boolean unbreakable) {
        return stack("unbreakable", ItemRequirement.base().with(ItemProbe.ITEM_META),
                ItemDataPart.PRIORITY_LATE, item -> {
                    if (MCVersion.isLower(MCDetailedVersion.v1_11_R1)) {
                        NbtDoor.custom().modifyBatch(item, tag -> {
                            if (unbreakable) {
                                tag.setBoolean("Unbreakable", true);
                            } else {
                                tag.removeKey("Unbreakable");
                            }
                        });
                        return item;
                    }
                    ItemMeta meta = item.getItemMeta();
                    meta.setUnbreakable(unbreakable);
                    item.setItemMeta(meta);
                    return item;
                });
    }

    @Nonnull
    public B setGlow() {
        return setGlow(true);
    }

    /** The enchantment shimmer with no enchantment behind it. */
    @Nonnull
    public B setGlow(final boolean glow) {
        return meta("glow", ItemDataPart.PRIORITY_LATE, meta -> {
            if (MCVersion.isEqual(MCVersion.v1_7_10)) {
                //1.7.10 has no way to hide an enchantment, so the shimmer has to come from a real
                //one. Looked up by name because the constant was renamed to UNBREAKING in 1.21, and
                //naming either spelling would bind this line to one half of the supported range -
                //this branch runs only on the half that spells it DURABILITY.
                Enchantment durability = Enchantment.getByName("DURABILITY");
                if (durability == null) {
                    return;
                }
                if (glow) {
                    meta.addEnchant(durability, 1, true);
                } else {
                    meta.removeEnchant(durability);
                }
                return;
            }
            if (glow) {
                meta.addEnchant(Enchantment.LURE, 1, false);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                return;
            }
            for (Enchantment enchantment : new ArrayList<>(meta.getEnchants().keySet())) {
                meta.removeEnchant(enchantment);
            }
        });
    }

    /** The dye of leather armour. Ignored by every other material. */
    @Nonnull
    public B setColor(@Nonnull final Color color) {
        return meta("colour", ItemDataPart.PRIORITY_NORMAL, meta -> {
            if (meta instanceof LeatherArmorMeta) {
                ((LeatherArmorMeta) meta).setColor(color);
            }
        });
    }

    /** The plugin-owned data container the server keeps next to the item's own fields. */
    @Nonnull
    public B setPDC(@Nonnull final Consumer<PersistentDataContainer> consumer) {
        return stack("persistent data", ItemRequirement.atLeast(MCDetailedVersion.v1_14_R1)
                .with(ItemProbe.ITEM_META), ItemDataPart.PRIORITY_LATE, item -> {
                    ItemMeta meta = item.getItemMeta();
                    consumer.accept(meta.getPersistentDataContainer());
                    item.setItemMeta(meta);
                    return item;
                });
    }

    /** Which model of a resource pack the item wears. */
    @Nonnull
    public B setCustomModelData(final int modelData) {
        return set(StandardParts.CUSTOM_MODEL_DATA, modelData);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The tag
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Writes into the item's tag. Every call joins the same batch, so a recipe with ten of them
     * still costs one round trip through the nbt library instead of ten.
     *
     * @param editor what to write
     * @return this builder
     */
    @Nonnull
    public B editNbt(@Nonnull final Consumer<ReadWriteNBT> editor) {
        ItemEngine.get().stageNbt(edits, editor);
        return self();
    }

    /**
     * Reads the item's tag as the recipe stands.
     *
     * <p>A query has no reduced answer - either the tag can be read or the caller is asking for
     * something that does not exist here - so this is the one door that refuses out loud.</p>
     *
     * @param reader what to read out of the tag
     * @return whatever {@code reader} answered
     * @throws IllegalStateException on a runtime with no working nbt access
     */
    public <T> T queryNbt(@Nonnull final Function<ReadableNBT, T> reader) {
        if (!ItemEngine.get().getRuntime().has(ItemProbe.NBT)) {
            throw new IllegalStateException("The item's tag cannot be read here: "
                    + ItemProbe.NBT.getAbsence() + ". Run this where the nbt library works, or build the "
                    + "item and ask whoever can read it.");
        }
        return NbtDoor.custom().read(build(), reader);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Ending the chain
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Builds the item, applying whatever this runtime can.
     *
     * @return the item, always - a runtime that cannot do everything still builds what it can
     */
    @Nonnull
    public ItemStack build() {
        return materialize().getItemStack();
    }

    /**
     * Builds the item and says what was left out of it.
     *
     * @return the item next to every edit this runtime refused
     */
    @Nonnull
    public BuiltItem materialize() {
        return ItemEngine.get().materialize(base, edits, problems);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Staging
    // -----------------------------------------------------------------------------------------------------------------

    /** Replaces what {@code key} asks for, which is what every setter on this class does. */
    @Nonnull
    protected B set(@Nonnull final String key, @Nonnull final Object value) {
        ItemEngine.get().stage(edits, key, value, false);
        return self();
    }

    /** Joins what {@code key} already asks for, the way the part itself defines joining. */
    @Nonnull
    protected B add(@Nonnull final String key, @Nonnull final Object value) {
        ItemEngine.get().stage(edits, key, value, true);
        return self();
    }

    @Nonnull
    protected B meta(@Nonnull final String name, final int priority,
                     @Nonnull final Consumer<ItemMeta> editor) {
        edits.add(ItemEdit.ofMeta(name, ItemRequirement.base().with(ItemProbe.ITEM_META), priority, editor));
        return self();
    }

    @Nonnull
    protected B stack(@Nonnull final String name, @Nonnull final ItemRequirement requirement,
                      final int priority, @Nonnull final java.util.function.UnaryOperator<ItemStack> operation) {
        edits.add(ItemEdit.ofStack(name, requirement, priority, operation));
        return self();
    }

    /** The base this recipe starts from, for the subclasses that swap it. */
    @Nonnull
    protected ItemBase getBase() {
        return base;
    }

    protected void setBase(@Nonnull final ItemBase base) {
        this.base = base;
    }

    @SuppressWarnings("unchecked")
    private List<String> currentLore() {
        Object staged = ItemEngine.get().staged(edits, StandardParts.LORE);
        if (staged != null) {
            return new ArrayList<>((List<String>) staged);
        }
        Object fromBase = ItemEngine.get().extract(base.resolve(), StandardParts.LORE);
        return fromBase == null ? new ArrayList<>() : new ArrayList<>((List<String>) fromBase);
    }

    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }

}
