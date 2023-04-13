package br.com.finalcraft.evernifecore.itemstack;

import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ComparableItem {

    protected final ItemStack itemStack;
    protected final Material material;
    protected final Short damageValue;

    protected transient String localized_name;

    public ComparableItem(ItemStack itemStack) {
        this.itemStack = itemStack;
        this.material = itemStack.getType();
        this.damageValue = itemStack.getDurability();
    }

    public ComparableItem(ItemStack itemStack, Material material, Short damageValue) {
        this.itemStack = itemStack;
        this.material = material;
        this.damageValue = damageValue;
    }

    public ComparableItem(Material material, Short damageValue) {
        this.itemStack = new ItemStack(material, 1, damageValue != null ? damageValue : 0);
        this.material = material;
        this.damageValue = damageValue;
    }

    public boolean match(ItemStack itemStack) {
        return itemStack.getType() == this.getMaterial() && (this.getDamageValue() == null || this.getDamageValue() == itemStack.getDurability());
    }

    public boolean match(Block block) {
        return block.getType() == this.getMaterial() && (this.getDamageValue() == null || this.getDamageValue() == block.getData());
    }

    public boolean match(ComparableItem comparableItem) {
        return this.getMaterial() == comparableItem.getMaterial() && (this.getDamageValue() == null ||  comparableItem.getDamageValue() == null|| this.getDamageValue() == comparableItem.getDamageValue());
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public Material getMaterial() {
        return material;
    }

    public @Nullable Short getDamageValue() {
        return damageValue;
    }

    public String getLocalizedName() {
        if (localized_name == null){
            localized_name = getItemStack() == null ? "null" : FCItemUtils.getLocalizedName(getItemStack());
        }
        return localized_name;
    }

    public String serialize(){
        return material + (damageValue != null ? (":" + damageValue) : "");
    }

    public static ComparableItem deserialize(String serializedLine) {
        String[] split = serializedLine.split(":");
        Material material = FCInputReader.parseMaterial(split[0]);
        if (material == null){
            throw new IllegalArgumentException("Invalid bukkit material: " + split[0]);
        }
        Short damageValue = split.length == 1
                ? null
                : split[1].equals("-1") || split[1].equals("*") ? null : FCInputReader.parseInt(split[1]).shortValue();
        return new ComparableItem(
                material,
                damageValue
        );
    }
}
