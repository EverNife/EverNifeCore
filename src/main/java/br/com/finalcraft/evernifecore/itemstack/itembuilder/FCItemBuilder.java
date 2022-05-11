package br.com.finalcraft.evernifecore.itemstack.itembuilder;

import br.com.finalcraft.evernifecore.gui.item.GuiItemComplex;
import br.com.finalcraft.evernifecore.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.util.FCNBTUtil;
import de.tr7zw.changeme.nbtapi.NBTCompound;
import de.tr7zw.changeme.nbtapi.NBTContainer;
import de.tr7zw.changeme.nbtapi.NBTItem;
import dev.triumphteam.gui.builder.item.BaseItemBuilder;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FCItemBuilder extends BaseItemBuilder<FCItemBuilder> {

    public FCItemBuilder(@NotNull ItemStack itemStack) {
        super(NMSUtils.get() != null
                ? NMSUtils.get().validateItemStackHandle(itemStack) //Always build an item with a Valid MCItemStack on it;
                : itemStack);
    }

    /**
     * Sets the display name of the item using {@link String}
     * TranslateAlternateColorCodes before applying
     *
     * @param name The {@link String} name
     * @return {@link FCItemBuilder}
     */
    @Override
    public @NotNull FCItemBuilder name(@NotNull String name) {
        return super.name(FCColorUtil.colorfy(name));
    }

    /**
     * Set the lore lines of an item
     * TranslateAlternateColorCodes before applying
     *
     * @param lore A {@link List} with the lore lines
     * @return {@link FCItemBuilder}
     */
    @Override
    public @NotNull FCItemBuilder lore(@NotNull List<String> lore) {
        return super.lore(lore.stream().map(FCColorUtil::colorfy).collect(Collectors.toList()));
    }

    /**
     * Returns a GuiItemComplex object that contains the ItemStack of this ItemBuilder.
     *
     * @return A GuiItemComplex object.
     */
    @NotNull
    public GuiItemComplex asGuiItemComplex() {
        return new GuiItemComplex(build());
    }


    /**
     * Returns a GuiItem object that contains the ItemStack of this ItemBuilder.
     *
     * @return A GuiItem object.
     */
    @NotNull
    public GuiItem asGuiItem() {
        return new GuiItem(build());
    }

    /**
     * "This function applies a consumer to the builder and returns the builder."
     *
     * The `apply` function is a very useful function that allows you to apply a consumer to the builder
     *
     * @param apply The function that will be applied to the builder.
     * @return The FCItemBuilder object.
     */
    @NotNull
    public FCItemBuilder apply(@NotNull Consumer<FCItemBuilder> apply){
        apply.accept(this);
        return this;
    }

    /**
     * "If the condition is true, apply the consumer to the builder."
     *
     * The `applyIf` function is a very useful function that allows you to apply a consumer to the builder only if a
     * condition is true
     *
     * @param condition A supplier that returns a boolean.
     * @param apply The consumer that will be applied to the builder if the condition is true.
     * @return The FCItemBuilder object.
     */
    @NotNull
    public FCItemBuilder applyIf(@NotNull Supplier<Boolean> condition, @NotNull Consumer<FCItemBuilder> apply){
        if (condition.get() == true){
            apply.accept(this);
        }
        return this;
    }

    /**
     * Sets the durability of the item.
     *
     * @param durability The durability of the item.
     * @return The FCItemBuilder object.
     */
    @NotNull
    public FCItemBuilder durability(final int durability) {
        itemStack.setDurability((short) durability);
        return this;
    }

    /**
     * Sets the material of the item.
     *
     * @param material The material of the item.
     * @return The FCItemBuilder class
     */
    @NotNull
    public FCItemBuilder material(@NotNull Material material) {
        itemStack.setType(material);
        return this;
    }

    /**
     * Sets the material of the item to the material with the given name.
     *
     * @param material The material of the item.
     * @return The FCItemBuilder object
     */
    @NotNull
    public FCItemBuilder material(@NotNull String material) {
        Material theMaterial = FCInputReader.parseMaterial(material);
        if (theMaterial == null){
            throw new IllegalArgumentException("The materialName '" + material + "' is not a valid Bukkit Material");
        }
        itemStack.setType(theMaterial);
        return this;
    }

    /**
     * Merges the given NBTCompound with the NBTCompound of the item
     *
     * @param compoundTag The NBTCompound to merge with the item's NBT.
     * @return The FCItemBuilder object.
     */
    @NotNull
    public FCItemBuilder mergeNBTCompound(@NotNull NBTCompound compoundTag) {
        FCNBTUtil.getFrom(this.itemStack).mergeCompound(compoundTag);
        return this;
    }

    /**
     * Merges the given NBTCompound with the NBTCompound of the item
     *
     * @param compoundTag The NBTCompound to merge with the item's NBT.
     * @return The FCItemBuilder object.
     */
    @NotNull
    public FCItemBuilder mergeNBTCompound(@NotNull String compoundTag) {
        FCNBTUtil.getFrom(this.itemStack).mergeCompound(new NBTContainer(compoundTag));
        return this;
    }

    /**
     * It returns an NBTCompound data of the ItemStack
     * It will directly apply changes to the item!
     *
     * @return The NBTItem object.
     */
    @NotNull
    public NBTItem getNBTCompound(){
        return FCNBTUtil.getFrom(this.itemStack);
    }

    /**
     * Read the ItemStack to a DataPart String List
     *
     * @return A list of strings.
     */
    @NotNull
    public List<String> toDataPart(){
        return ItemDataPart.readItem(this.build());
    }
}
