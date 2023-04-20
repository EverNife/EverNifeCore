package br.com.finalcraft.evernifecore.itemstack;

import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

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

    public boolean match(Material material, @Nullable Short damageValue) {
        return this.getMaterial() == material && (this.getDamageValue() == null ||  damageValue == null || this.getDamageValue() == damageValue);
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
        //This will accept the following formats:
        // 1. Material
        // 2. Material:<DamageValue|*|-1>

        // 3. minecraft:identifier
        // 4. minecraft:identifier <DamageValue|*|-1>
        // 5. minecraft:identifier:<DamageValue|*|-1>

        String[] split = serializedLine.split(":");

        //Case 1
        if (split.length == 1){
            Material material = FCInputReader.parseMaterial(split[0]);
            if (material == null){
                throw new IllegalArgumentException("Invalid bukkit material: " + split[0]);
            }
            return new ComparableItem(material, (short) 0);
        }

        //Case 2 (if the second part is a damage value)
        String damagePartString = split[1].trim();
        Short damageValue = damagePartString.equals("*") ? -1 : FCInputReader.parseInt(damagePartString).shortValue();
        if (damageValue != null){
            Material material = FCInputReader.parseMaterial(split[0]);
            if (material == null){
                throw new IllegalArgumentException("Invalid bukkit material: " + split[0]);
            }
            return new ComparableItem(material, damageValue < 0 ? null : damageValue);
        }

        String[] splitSecondPart = split[1].split(" ");
        String mcIdentifier = split[0] + ":" + splitSecondPart[0];
        ItemStack itemStack = FCItemUtils.fromMinecraftIdentifier(mcIdentifier);
        if (itemStack == null){
            throw new IllegalArgumentException("Invalid Minecraft Identifier: " + mcIdentifier);
        }

        if (splitSecondPart.length > 1){ //Case 4: Pattern is minecraft:identifier <DamageValue|*|-1>
            damagePartString = splitSecondPart[1].trim();
            damageValue = damagePartString.equals("*") ? -1 : FCInputReader.parseInt(damagePartString).shortValue();
            return new ComparableItem(itemStack.getType(), damageValue < 0 ? null : damageValue);
        }

        if (split.length > 2){ //Case 5: Pattern is minecraft:identifier:<DamageValue|*|-1>
            damagePartString = split[2].trim();
            damageValue = damagePartString.equals("*") ? -1 : FCInputReader.parseInt(damagePartString).shortValue();
            return new ComparableItem(itemStack.getType(), damageValue < 0 ? null : damageValue);
        }

        return new ComparableItem(itemStack);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComparableItem that = (ComparableItem) o;
        return material == that.material && Objects.equals(damageValue, that.damageValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(material, damageValue);
    }
}
