package br.com.finalcraft.evernifecore.minecraft.itemstack;

import br.com.finalcraft.evernifecore.minecraft.util.FCNBTUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
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
    @JsonValue
    public String serialize() {
        String base = super.serialize();               // material[:damage]
        if (FCNBTUtil.isEmpty(nbtCompound)) return base; // no NBT -> compact form (back-compat)
        return base + " " + nbtCompound.toString();      // material[:damage] {<snbt>}
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
    @JsonCreator
    public static ComparableItemComplex deserialize(String serializedLine){
        // Peel off the optional NBT suffix ({...}) before ComparableItem.deserialize, which splits on
        // ':'/' ' and would choke on the SNBT. Configs saved without NBT have no '{' and load as before.
        String materialPart = serializedLine;
        NBTCompound nbt = null;
        int nbtStart = serializedLine.indexOf('{');
        if (nbtStart >= 0) {
            materialPart = serializedLine.substring(0, nbtStart).trim();
            nbt = FCNBTUtil.getFrom(serializedLine.substring(nbtStart).trim());
        }
        ComparableItem base = ComparableItem.deserialize(materialPart);
        return nbt != null
                ? new ComparableItemComplex(base.getItemStack(), base.getMaterial(), base.getDamageValue(), nbt)
                : new ComparableItemComplex(base.getItemStack(), base.getMaterial(), base.getDamageValue());
    }
}
