package br.com.finalcraft.evernifecore.itemstack;

import br.com.finalcraft.evernifecore.config.yaml.anntation.Loadable;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.util.FCNBTUtil;
import de.tr7zw.changeme.nbtapi.NBTCompound;
import de.tr7zw.changeme.nbtapi.NBTItem;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public class ComparableItemComplex extends ComparableItem {

    protected final NBTCompound nbtCompound;

    protected NBTCompound extractCompound(ItemStack itemStack){
        try {
            ItemStack safeClone = FCItemFactory.from(itemStack).build();
            NBTItem nbtItem = FCNBTUtil.getFrom(safeClone);
            return FCNBTUtil.getFrom(nbtItem.toString());
        }catch (Exception ignored){//Necessary, because some blocks are not able to have NBT
        }
        return FCNBTUtil.empyNBT();
    }

    protected NBTCompound extractCompound(ComparableItem comparableItem){
        if (comparableItem instanceof ComparableItemComplex){
            return ((ComparableItemComplex) comparableItem).getNbtCompound();
        }
        return extractCompound(comparableItem.getItemStack());
    }

    public ComparableItemComplex(Block block) {
        super(block);
        this.nbtCompound = extractCompound(this.getItemStack());
    }

    public ComparableItemComplex(ItemStack itemStack) {
        super(itemStack);
        this.nbtCompound = extractCompound(this.getItemStack());
    }

    public ComparableItemComplex(ItemStack itemStack, Material material, Short damageValue) {
        super(itemStack, material, damageValue);
        this.nbtCompound = extractCompound(this.getItemStack());
    }

    public ComparableItemComplex(Material material, Short damageValue) {
        super(material, damageValue);
        this.nbtCompound = extractCompound(this.getItemStack());
    }

    public ComparableItemComplex(ItemStack itemStack, Material material, Short damageValue, NBTCompound nbtCompound) {
        super(itemStack, material, damageValue);
        this.nbtCompound = nbtCompound;
    }

    public ComparableItemComplex(Material material, Short damageValue, NBTCompound nbtCompound) {
        super(material, damageValue);
        this.nbtCompound = nbtCompound;
    }

    public NBTCompound getNbtCompound() {
        return nbtCompound;
    }

    @Override
    public boolean match(ItemStack itemStack) {
        return super.match(itemStack) && this.nbtCompound.equals(extractCompound(itemStack));
    }

    @Override
    public boolean match(Block block) {
        return super.match(block); //TODO Compare Block NBT as well
    }

    @Override
    public boolean match(ComparableItem comparableItem) {
        return super.match(comparableItem) && this.nbtCompound.equals(extractCompound(comparableItem));
    }

    @Override
    public String serialize() {
        return super.serialize();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ComparableItemComplex == false) return false;
        ComparableItemComplex that = (ComparableItemComplex) o;
        return this.getMaterial() == that.getMaterial()
                && Objects.equals(this.getDamageValue(), that.getDamageValue())
                && Objects.equals(this.getNbtCompound(), that.getNbtCompound());
    }
    @Loadable
    public static ComparableItemComplex onConfigLoad(ConfigSection section){
        ComparableItem comparableItem = ComparableItem.deserialize(section.getString(""));
        return new ComparableItemComplex(comparableItem.getItemStack(), comparableItem.getMaterial(), comparableItem.getDamageValue());
    }
}
