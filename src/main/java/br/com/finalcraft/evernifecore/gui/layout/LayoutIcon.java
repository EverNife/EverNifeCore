package br.com.finalcraft.evernifecore.gui.layout;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Loadable;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.gui.PlayerGui;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.itemstack.itembuilder.FCItemBuilder;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import com.google.common.collect.ImmutableList;
import dev.triumphteam.gui.guis.GuiItem;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * You can edit the LayoutIcon when inside {@link LayoutBase}
 * with the usafe of {@link LayoutIconData}
 */
@Data
public class LayoutIcon {

    protected final ItemStack itemStack;
    protected final int[] slot;
    protected final boolean background;
    protected final String permission;

    protected @Nullable transient List<String> dataPart; //Only populated on demand

    public LayoutIcon(ItemStack itemStack, int[] slot, boolean background, String permission, List<String> dataPart) {
        this.itemStack = itemStack;
        this.slot = slot;
        this.background = background;
        this.permission = permission;
        this.dataPart = dataPart;
    }

    @Nonnull
    public FCItemBuilder asFactory(){ //Even though it's return a FCItemBuilder, for better naming, lets keep 'asFactory'
        return new FCItemBuilder(this.getItemStack().clone(), this);
    }

    @Nonnull
    public GuiItem applyTo(PlayerGui playerGui){
        return applyTo(playerGui, GuiItem.class);
    }

    @Nonnull
    public <GI extends GuiItem> GI applyTo(PlayerGui playerGui, @Nonnull Class<GI> customGuiItem) {

        ItemStack finalStack = this.itemStack;
        if (this.dataPart == null){ //If this LayoutIcon was never parsed before!
            CompoundReplacer replacer = playerGui.getReplacer();
            if (!replacer.isEmpty()){ //Maybe the layout we are applying requires PAPI, or other default replacers
                List<String> parsedDataPart = replacer.apply(FCItemFactory.from(this.getItemStack()).toDataPart());
                finalStack = FCItemFactory.from(parsedDataPart).build();
            }
        }

        GI guiItem = customGuiItem == GuiItem.class
                ? (GI) FCItemFactory.from(finalStack).asGuiItem()
                : FCItemFactory.from(finalStack).asGuiItem(customGuiItem);

        if (this.permission.isEmpty() || (playerGui.getPlayer() != null && playerGui.getPlayer().hasPermission(this.permission))){
            for (int slot : this.slot) {
                if (slot < 0) continue;
                try {
                    playerGui.getGui().setItem(slot, guiItem);
                }catch (Exception e){
                    EverNifeCore.getLog().warning("Failed to apply LayoutIcon for {" + guiItem.getItemStack().getType() + "}: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        return guiItem;
    }

    @Nonnull
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

    @Nonnull
    public LayoutIcon parse(CompoundReplacer replacer){
        return parse(stringList -> replacer.apply(stringList));
    }

    @Nonnull
    public List<String> getDataPart() {
        if (this.dataPart == null){
            this.dataPart = ImmutableList.copyOf(FCItemFactory.from(this.getItemStack()).toDataPart());
        }
        return dataPart;
    }

    public boolean isBackground() {
        return background;
    }

    public LayoutIconBuilder asLayoutBuilder(){
        return LayoutIconBuilder.of(this);
    }

    @Loadable
    public static LayoutIcon onConfigLoad(ConfigSection section){

        int[] slot = {};

        Object slotObject = section.getValue("Slot");
        if (slotObject instanceof String){
            String slotString = (String) slotObject;
            if (!slotString.isEmpty()){
                slot = Arrays.stream(slotString.replace("[", "")
                                .replace("]", "")
                                .split(","))
                        .mapToInt(value -> Integer.valueOf(value.trim()))
                        .toArray()
                ;
            }
        }else {
            slot = section.getStringList("Slot")
                    .stream()
                    .mapToInt(value -> Integer.valueOf(value))
                    .toArray();
        }

        String permission = section.getString("Permission","");

        ItemStack itemStack = null;
        if (section.contains("DisplayItem")){
            itemStack = FCItemFactory.from(
                    section.getStringList("DisplayItem")
            ).build();
        }

        return new LayoutIcon(itemStack, slot, false, permission, null);
    }

    public List<String> getLore(){
        return FCItemUtils.getLore(this.getItemStack());
    }

    public String getDisplayName(){
        return FCItemUtils.getDisplayName(this.getItemStack());
    }

}
