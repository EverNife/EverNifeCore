package br.com.finalcraft.evernifecore.gui.layout;

import br.com.finalcraft.evernifecore.gui.PlayerGui;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class LayoutIconBuilder {
    private ItemStack itemStack = null;
    private int[] slot = {};
    private boolean background = false;
    private String permission = "";
    private List<String> dataPart = null;

    public LayoutIconBuilder() {
    }

    public LayoutIconBuilder(ItemStack itemStack, int[] slot, boolean background, String permission, List<String> dataPart) {
        this.itemStack = itemStack;
        this.slot = slot;
        this.background = background;
        this.permission = permission;
        this.dataPart = dataPart;
    }

    public LayoutIconBuilder setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
        return this;
    }

    public LayoutIconBuilder setSlot(int slot) {
        this.slot = new int[]{slot};
        return this;
    }

    public LayoutIconBuilder setSlot(int[] slot) {
        this.slot = slot;
        return this;
    }

    public LayoutIconBuilder appendSlot(int slot) {
        this.slot = Arrays.copyOf(this.slot, this.slot.length + 1);
        this.slot[this.slot.length - 1] = slot;
        return this;
    }

    public LayoutIconBuilder appendSlot(int[] slot) {
        int currentSize = this.slot.length;
        this.slot = Arrays.copyOf(this.slot, this.slot.length + slot.length);

        int index = currentSize;
        for (int j = 0; j < slot.length; j++) {
            this.slot[index] = slot[j];
            index++;
        }

        return this;
    }

    public LayoutIconBuilder setBackground(boolean background) {
        this.background = background;
        return this;
    }

    public LayoutIconBuilder setPermission(String permission) {
        this.permission = permission;
        return this;
    }

    public LayoutIconBuilder setDataPart(List<String> dataPart) {
        this.dataPart = dataPart;
        return this;
    }

    public LayoutIcon build() {
        return new LayoutIcon(itemStack, slot, background, permission, dataPart);
    }

    @NotNull
    public GuiItem applyTo(PlayerGui playerGui){
        return build().applyTo(playerGui);
    }

    public static LayoutIconBuilder of(){
        return new LayoutIconBuilder();
    }

    public static LayoutIconBuilder of(LayoutIcon layoutIcon){
        return new LayoutIconBuilder(layoutIcon.getItemStack().clone(), layoutIcon.getSlot(), layoutIcon.isBackground(), layoutIcon.getPermission(), layoutIcon.getDataPart());
    }
}