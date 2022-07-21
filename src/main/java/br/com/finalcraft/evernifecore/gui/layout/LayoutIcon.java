package br.com.finalcraft.evernifecore.gui.layout;

import br.com.finalcraft.evernifecore.gui.PlayerGui;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.itemstack.itembuilder.FCItemBuilder;
import com.google.common.collect.ImmutableList;
import dev.triumphteam.gui.guis.GuiItem;
import lombok.Data;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Data
public class LayoutIcon {

    private final ItemStack itemStack;
    private final int[] slot;
    private final boolean background;
    private final String permission;

    private transient List<String> dataPart = null; //Only populated on demand

    public LayoutIcon(ItemStack itemStack, int[] slot, boolean background, String permission, List<String> dataPart) {
        this.itemStack = itemStack;
        this.slot = slot;
        this.background = background;
        this.permission = permission;
        this.dataPart = dataPart;
    }

    @NotNull
    public FCItemBuilder asFactory(){ //Even though it's return a FCItemBuilder, for better naming, lets keep 'asFactory'
        return new FCItemBuilder(this.getItemStack().clone(), this);
    }

    @NotNull
    public GuiItem applyTo(PlayerGui playerGui){
        return applyTo(playerGui, GuiItem.class);
    }

    @NotNull
    public <GI extends GuiItem> GI applyTo(PlayerGui playerGui, @NotNull Class<GI> customGuiItem) {
        GI guiItem = FCItemFactory.from(this.itemStack).asGuiItem(customGuiItem);

        if (this.permission.isEmpty() || (playerGui.getPlayer() != null && playerGui.getPlayer().hasPermission(this.permission))){
            for (int slot : this.slot) {
                playerGui.getGui().setItem(slot, guiItem);
            }
        }

        return guiItem;
    }

    @NotNull
    public LayoutIcon parse(Function<List<String>, List<String>> placeholderParser){
        List<String> newDataPart = placeholderParser.apply(new ArrayList<>(getDataPart()));

        LayoutIcon layoutIcon = new LayoutIcon(
                FCItemFactory.from(newDataPart).build(),
                this.getSlot(),
                this.background,
                this.getPermission(),
                newDataPart
        );

        return layoutIcon;
    }

    @NotNull
    public List<String> getDataPart() {
        if (this.dataPart == null){
            this.dataPart = ImmutableList.copyOf(FCItemFactory.from(this.getItemStack()).toDataPart());
        }
        return dataPart;
    }

    public boolean isBackground() {
        return background;
    }

    public LayoutIconBuilder asBuilder(){
        return LayoutIconBuilder.of(this);
    }
}
