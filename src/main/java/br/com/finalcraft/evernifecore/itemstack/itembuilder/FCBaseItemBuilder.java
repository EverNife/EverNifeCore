package br.com.finalcraft.evernifecore.itemstack.itembuilder;

import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCNBTUtil;
import br.com.finalcraft.evernifecore.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.version.MCVersion;
import de.tr7zw.changeme.nbtapi.NBTCompound;
import de.tr7zw.changeme.nbtapi.NBTItem;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.components.util.VersionHelper;
import org.apache.commons.lang.Validate;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This class is a mirror of {@link dev.triumphteam.gui.builder.item.BaseItemBuilder} but with:
 *  - few aditions
 *  - support to NBT
 *  - support to FCColorUtil
 *
 * @param <B> The ItemBuilder type so the methods can cast to the subtype
 */
public abstract class FCBaseItemBuilder<B extends FCBaseItemBuilder<B>> {

    private static final EnumSet<Material> LEATHER_ARMOR = EnumSet.of(
            Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS
    );

    protected @NotNull ItemStack itemStack;
    protected @NotNull ItemMeta meta;
    protected @NotNull transient NBTCompound nbtCompound; //Only populated when needed

    protected FCBaseItemBuilder(@NotNull final ItemStack itemStack) {
        Validate.notNull(itemStack, "Item can't be null!");
        Validate.isTrue(itemStack.getType() != Material.AIR, "Item can't be AIR!");

        this.itemStack = NMSUtils.get() != null ? NMSUtils.get().validateItemStackHandle(itemStack.clone()) : itemStack.clone();//Clone the item for this builder! Also, validade it!
        this.meta = itemStack.getItemMeta();//getItemMeta is only null when the material is AIR
        this.nbtCompound = FCNBTUtil.getFrom(FCNBTUtil.getFrom(itemStack).toString());//Create a copy of the NBTCompound of the itemStack
        this.nbtCompound.removeKey("display");//Remove LORE and DisplayName, its redundant as they are saved on the meta
    }

    protected B changeItemStack(@NotNull ItemStack newStack) {
        //So let's create a new meta
        ItemMeta newMeta = newStack.getItemMeta();

        //And merge the old meta into the new meta
        newMeta.setDisplayName(meta.getDisplayName());
        newMeta.setLore(meta.getLore());
        meta.getEnchants().forEach((enchantment, level) -> newMeta.addEnchant(enchantment, level, true));
        if (MCVersion.getCurrent().isHigherEquals(MCDetailedVersion.v1_10_R1) && meta.isUnbreakable()) newMeta.setUnbreakable(true);
        if (MCVersion.getCurrent().isHigherEquals(MCDetailedVersion.v1_14_R1)){
            if (meta.hasCustomModelData()) newMeta.setCustomModelData(meta.getCustomModelData());
            if (meta.hasAttributeModifiers()) newMeta.setAttributeModifiers(meta.getAttributeModifiers());
        }

        this.itemStack = newStack;
        this.meta = newMeta;
        //this.nbtCompound = ????; //There is no need to update the nbt! At least for now

        return (B) this;
    }

    /**
     * Sets the material of the item. It acually
     * creates a new ItemStack with that material, then
     * copy every single entry of the previous MetaData
     * to the new MetaData. There is no change to this
     * builder's NBTTagCompound!
     *
     * @param material The material of the item.
     * @return The FCItemBuilder class
     */
    @NotNull
    public B material(@NotNull Material material) {
        Validate.notNull(material, "Material can't be null!");
        Validate.isTrue(itemStack.getType() != Material.AIR, "Material can't be AIR!");

        //On modern versions there are no reliable way to change 'just the material'.
        //It turns the ItemStack into AIR sometimes, at least I think it does!!!
        //So let's create a new item
        ItemStack newStack = FCItemFactory.from(material).build();
        return this.changeItemStack(newStack);
    }

    /**
     * Sets the material of the item. It acually
     * creates a new ItemStack with that material, then
     * copy every single entry of the previous MetaData
     * to the new MetaData. There is no change to the
     * builders NBTTagCompound!
     *
     * @param itemIdentifier The itemIdentifier of the item.
     *                       can be a BukkitIdentifier
     *                       or a Minecraft Identifier
     * @return The FCItemBuilder object
     */
    @NotNull
    public B material(@NotNull String itemIdentifier) {
        ItemStack newStack = FCItemFactory.from(itemIdentifier).build();
        return this.changeItemStack(newStack);
    }

    /**
     * Sets the durability of the item.
     *
     * @param durability The durability of the item.
     * @return The FCItemBuilder object.
     */
    @NotNull
    public B durability(final int durability) {
        itemStack.setDurability((short) durability); //its deprecated since always, but works '-'
        return (B) this;
    }

    /**
     * Sets the display name of the item using {@link String}
     *
     * @param name The {@link String} name
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B displayName(@NotNull final String name) {
        meta.setDisplayName(FCColorUtil.colorfy(name));
        return (B) this;
    }

    /**
     * Sets the amount of items
     *
     * @param amount the amount of items
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B amount(final int amount) {
        itemStack.setAmount(amount);
        return (B) this;
    }

    /**
     * Set the lore lines of an item
     * TranslateAlternateColorCodes before applying
     * Also split at '\n'
     *
     * @param lore A {@link List} with the lore lines
     * @return {@link FCItemBuilder}
     */
    @NotNull
    public B lore(@NotNull final String... lore) {
        return lore(Arrays.asList(lore));
    }

    /**
     * Set the lore lines of an item
     * TranslateAlternateColorCodes before applying
     * Also split at '\n'
     *
     * @param lore A {@link List} with the lore lines
     * @return {@link FCItemBuilder}
     */
    @NotNull
    public B lore(@NotNull final List<String> lore) {
        meta.setLore(FCColorUtil.colorfy(lore));
        return (B) this;
    }

    /**
     * Consumer for freely editing to the lore
     *
     * @param lore A {@link Consumer} with the {@link List} of lore {@link String}
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B lore(@NotNull final Consumer<List<String>> lore) {
        final List<String> newLore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        lore.accept(newLore);
        return lore(newLore.isEmpty() ? null : newLore);
    }

    /**
     * Function for freely editing to the lore
     *
     * @param lore A {@link Function} with the {@link List} of lore {@link String}
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B lore(@NotNull final Function<List<String>, List<String>> lore) {
        List<String> newLore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        newLore = lore.apply(newLore);
        return lore(newLore.isEmpty() ? null : newLore);
    }

    /**
     * Enchants the {@link ItemStack}
     *
     * @param enchantment            The {@link Enchantment} to add
     * @param level                  The level of the {@link Enchantment}
     * @param ignoreLevelRestriction If should or not ignore it
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B addEnchant(@NotNull final Enchantment enchantment, final int level, final boolean ignoreLevelRestriction) {
        meta.addEnchant(enchantment, level, ignoreLevelRestriction);
        return (B) this;
    }

    /**
     * Enchants the {@link ItemStack}
     *
     * @param enchantment The {@link Enchantment} to add
     * @param level       The level of the {@link Enchantment}
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B addEnchant(@NotNull final Enchantment enchantment, final int level) {
        return addEnchant(enchantment, level, true);
    }

    /**
     * Enchants the {@link ItemStack}
     *
     * @param enchantment The {@link Enchantment} to add
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B addEnchant(@NotNull final Enchantment enchantment) {
        return addEnchant(enchantment, 1, true);
    }

    /**
     * Disenchants a certain {@link Enchantment} from the {@link ItemStack}
     *
     * @param enchantment The {@link Enchantment} to remove
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B removeEnchantment(@NotNull final Enchantment enchantment) {
        itemStack.removeEnchantment(enchantment);
        return (B) this;
    }

    /**
     * Add an {@link ItemFlag} to the item
     *
     * @param flags The {@link ItemFlag} to add
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B addItemFlags(@NotNull final ItemFlag... flags) {
        meta.addItemFlags(flags);
        return (B) this;
    }

    /**
     * Makes the {@link ItemStack} unbreakable
     *
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B setUnbreakable() {
        return setUnbreakable(true);
    }

    /**
     * Sets the item as unbreakable
     *
     * @param unbreakable If should or not be unbreakable
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B setUnbreakable(boolean unbreakable) {
        if (VersionHelper.IS_UNBREAKABLE_LEGACY) {
            return setNbt(nbtCompound -> {
                if (unbreakable){
                    nbtCompound.setBoolean("Unbreakable", true);
                }else {
                    nbtCompound.removeKey("Unbreakable");
                }
            });
        }

        meta.setUnbreakable(unbreakable);
        return (B) this;
    }

    /**
     * Makes the {@link ItemStack} glow
     *
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B setGlow() {
        return setGlow(true);
    }

    /**
     * Adds or removes the {@link ItemStack} glow
     *
     * @param glow Should the item glow
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B setGlow(boolean glow) {
        if (MCVersion.isEqual(MCVersion.v1_7_10)) { //On 1.7.10 we tread glow as Durability enchantment.
            if (glow) return addEnchant(Enchantment.DURABILITY);
            else return removeEnchantment(Enchantment.DURABILITY);
        }

        if (glow) {
            meta.addEnchant(Enchantment.LURE, 1, false);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            return (B) this;
        }

        for (final Enchantment enchantment : meta.getEnchants().keySet()) {
            meta.removeEnchant(enchantment);
        }

        return (B) this;
    }

    /**
     * Consumer for applying {@link PersistentDataContainer} to the item
     * This method will only work on versions above 1.14
     *
     * @param consumer The {@link Consumer} with the PDC
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B setPDC(@NotNull final Consumer<PersistentDataContainer> consumer) {
        consumer.accept(meta.getPersistentDataContainer());
        return (B) this;
    }

    /**
     * Sets the custom model data of the item
     * Added in 1.13
     *
     * @param modelData The custom model data from the resource pack
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B setCustomModelData(final int modelData) {
        if (VersionHelper.IS_CUSTOM_MODEL_DATA) {
            meta.setCustomModelData(modelData);
        }

        return (B) this;
    }

    /**
     * {@inheritDoc}
     * @param color color
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B setColor(@NotNull final Color color) {
        if (LEATHER_ARMOR.contains(itemStack.getType())) {
            final LeatherArmorMeta lam = (LeatherArmorMeta) getMeta();

            lam.setColor(color);
            setMeta(lam);
        }

        return (B) this;
    }


    /**
     * It returns an NBTCompound data of the ItemStack
     * It will be applied only on build()
     *
     * @return The NBTItem object.
     */
    @NotNull
    public NBTCompound getNBTCompound(){
        return this.nbtCompound;
    }

    /**
     * Consumer for applying {@link NBTCompound} to the item
     *
     * @param consumer The {@link Consumer} with the NBTCompound
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B setNbt(@NotNull final Consumer<NBTCompound> consumer) {
        consumer.accept(this.nbtCompound);
        return (B) this;
    }

    /**
     * Set the {@link NBTCompound} of the item
     *
     * @param nbtCompound The {@link NBTCompound}
     * @return {@link ItemBuilder}
     */
    @NotNull
    public B setNbt(@NotNull final NBTCompound nbtCompound) {
        this.nbtCompound = nbtCompound;
        return (B) this;
    }

    /**
     * Builds the item into {@link ItemStack}
     *
     * @return The fully built {@link ItemStack}
     */
    @NotNull
    public ItemStack build() {
        if (nbtCompound.isEmpty()){
            ItemStack clone = this.itemStack.clone();
            clone.setItemMeta(meta.clone());
            return clone;
        }else {
//            System.out.println("FCItemBuilder Compound: " + nbtCompound.toString());
//            System.out.println("FCItemBuilder Lore: " + Arrays.toString(this.meta.getLore() != null ? this.meta.getLore().toArray() : new String[0]));

            NBTItem cloneNBT = new NBTItem(this.itemStack);//NBTItem creates an internal clone
//            System.out.println("NBTItem Identifier: " + FCItemUtils.getMinecraftIdentifier(cloneNBT.getItem()));

//            cloneNBT.removeKey("display"); //Enforce the removal of the display key, so the 'this.meta' takes priority

            cloneNBT.getItem().setItemMeta(this.meta.clone());
            cloneNBT.mergeCompound(this.nbtCompound);
//            System.out.println("NBTItem [AfterMerge] MetaLore: " + Arrays.toString(cloneNBT.getItem().getItemMeta().getLore() != null ? cloneNBT.getItem().getItemMeta().getLore().toArray() : new String[0]));
//            System.out.println("NBTItem [AfterMerge] cloneNBT.toString(): " + cloneNBT.toString());
//            System.out.println("NBTItem [AfterMerge] cloneNBT.getItem().getLore(): " + ("CloneNBT Meta Lore AFter Merge: " + Arrays.toString(cloneNBT.getItem().getItemMeta().getLore() != null ? cloneNBT.getItem().getItemMeta().getLore().toArray() : new String[0])));

            return cloneNBT.getItem();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Protected Methods
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Package private getter for extended builders
     *
     * @return The ItemStack
     */
    @NotNull
    protected ItemStack getItemStack() {
        return itemStack;
    }

    /**
     * Package private setter for the extended builders
     *
     * @param itemStack The ItemStack
     */
    protected void setItemStack(@NotNull final ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    /**
     * Package private getter for extended builders
     *
     * @return The ItemMeta
     */
    @NotNull
    protected ItemMeta getMeta() {
        return meta;
    }

    /**
     * Package private setter for the extended builders
     *
     * @param meta The ItemMeta
     */
    protected void setMeta(@NotNull final ItemMeta meta) {
        this.meta = meta;
    }

}
