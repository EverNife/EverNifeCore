package br.com.finalcraft.evernifecore.fcitemstack;

import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public class FCItemStack {

    private ItemStack itemStack;

    public static FCItemStack fromMinecraftIdentifier(String minecraftIdentifier){
        ItemStack itemStack = FCItemUtils.fromMinecraftIdentifier(minecraftIdentifier);
        return new FCItemStack(itemStack);
    }

    public static FCItemStack fromBukkitIdentifier(String bukkitIdentifier){
        ItemStack itemStack = FCItemUtils.fromBukkitIdentifier(bukkitIdentifier);
        return new FCItemStack(itemStack);
    }

    public String getMinecraftIdentifier(){
        return getMinecraftIdentifier(true);
    }

    public String getMinecraftIdentifier(boolean withNbt){
        return FCItemUtils.getMinecraftIdentifier(this.itemStack, withNbt);
    }

    public String getBukkitIdentifier(){
        return FCItemUtils.getBukkitIdentifier(this.itemStack);
    }

    public FCItemStack(ItemStack itemStack) {
        this.itemStack = NMSUtils.get().validateItemStackHandle(itemStack.clone());
    }

    public String getItemLocalizedName(){
        return FCItemUtils.getLocalizedName(this.itemStack);
    }

    public FCItemStack setItemStack(ItemStack itemStack){
        this.itemStack = itemStack;
        return this;
    }

    public ItemStack getItemStack() {
        return this.itemStack;
    }

    public ItemStack copyItemStack() {
        return this.itemStack.clone();
    }

    public Material getType(){
        return this.itemStack.getType();
    }

    public int getAmount(){
        return this.itemStack.getAmount();
    }

    public FCItemStack setAmount(int amount){
        this.itemStack.setAmount(amount);
        return this;
    }

    public String getBukkitMaterialName() {
        return this.itemStack.getType().name();
    }

    public FCItemStack setItemDurability(int durability){
        this.itemStack.setDurability((short) durability);
        return this;
    }

    public int getItemDurability(){
        return this.itemStack.getDurability();
    }

    public boolean hasNBTTag(){
        return NMSUtils.get().hasNBTTagCompound(this.itemStack);
    }

    public @Nullable String getNBTtoString(){
        return NMSUtils.get().getNBTtoString(this.itemStack);
    }

    @Override
    public FCItemStack clone() {
        return new FCItemStack(this.itemStack);
    }
}
