package br.com.finalcraft.evernifecore.minecraft.itemstack;

import br.com.finalcraft.ecnmsutils.NMSUtils;
import br.com.finalcraft.evernifecore.minecraft.util.FCNBTUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * A {@link ComparableItem} that also compares the item's NBT tag. On a server with no NMS bridge
 * ({@code NMSUtils.isSupported()} false) no tag can be read: {@link #getNbtCompound()} is then {@code null} and the
 * tag comparison falls back to what Bukkit answers ({@link ItemStack#isSimilar} - type, durability, meta).
 */
public class ComparableItemComplex extends ComparableItem {

    protected final @Nullable ReadWriteNBT nbtCompound;

    protected @Nullable ReadWriteNBT extractCompound(ItemStack itemStack){
        if (!NMSUtils.isSupported()){
            return null;
        }
        try {
            ItemStack safeClone = FCItemFactory.from(itemStack).build();
            return FCNBTUtil.parse(FCNBTUtil.readItem(safeClone).toString());
        }catch (Exception ignored){//Necessary, because some blocks are not able to have NBT
        }
        return FCNBTUtil.createEmpty();
    }

    protected @Nullable ReadWriteNBT extractCompound(ComparableItem comparableItem){
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

    public ComparableItemComplex(ItemStack itemStack, Material material, Short damageValue, ReadWriteNBT nbtCompound) {
        super(itemStack, material, damageValue);
        this.nbtCompound = nbtCompound;
    }

    public ComparableItemComplex(Material material, Short damageValue, ReadWriteNBT nbtCompound) {
        super(material, damageValue);
        this.nbtCompound = nbtCompound;
    }

    public @Nullable ReadWriteNBT getNbtCompound() {
        return nbtCompound;
    }

    @Override
    public boolean match(ItemStack itemStack) {
        return super.match(itemStack) && sameTag(extractCompound(itemStack), getItemStack(), itemStack);
    }

    private boolean sameTag(@Nullable ReadWriteNBT other, ItemStack ours, ItemStack theirs) {
        if (nbtCompound == null || other == null){
            //no tag readable on this server: Bukkit's own comparison stands in for it (it also weighs durability, so a
            //wildcard damage value is exact here)
            return ours != null && theirs != null && ours.isSimilar(theirs);
        }
        return nbtCompound.equals(other);
    }

    @Override
    public boolean match(Block block) {
        return super.match(block); //TODO Compare Block NBT as well
    }

    @Override
    public boolean match(ComparableItem comparableItem) {
        return super.match(comparableItem) && sameTag(extractCompound(comparableItem), getItemStack(), comparableItem.getItemStack());
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
        ReadWriteNBT nbt = null;
        int nbtStart = serializedLine.indexOf('{');
        if (nbtStart >= 0) {
            materialPart = serializedLine.substring(0, nbtStart).trim();
            nbt = FCNBTUtil.parse(serializedLine.substring(nbtStart).trim());
        }
        ComparableItem base = ComparableItem.deserialize(materialPart);
        return nbt != null
                ? new ComparableItemComplex(base.getItemStack(), base.getMaterial(), base.getDamageValue(), nbt)
                : new ComparableItemComplex(base.getItemStack(), base.getMaterial(), base.getDamageValue());
    }
}
