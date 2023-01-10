package br.com.finalcraft.evernifecore.gui.layout;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.yaml.anntation.Loadable;
import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.gui.PlayerGui;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.itemstack.itembuilder.FCItemBuilder;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.placeholder.replacer.CompoundReplacer;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.version.MCVersion;
import com.google.common.collect.ImmutableList;
import dev.triumphteam.gui.guis.GuiItem;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
                    EverNifeCore.warning("Failed to apply LayoutIcon for {" + guiItem.getItemStack().getType() + "}: " + e.getMessage());
                    e.printStackTrace();
                }
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
    public LayoutIcon parse(CompoundReplacer replacer){
        return parse(stringList -> replacer.apply(stringList));
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

    public LayoutIconBuilder asLayoutBuilder(){
        return LayoutIconBuilder.of(this);
    }

    @Loadable
    public static LayoutIcon onConfigLoad(ConfigSection section){
        int[] slot = section.getStringList("Slot")
                .stream()
                .mapToInt(value -> Integer.valueOf(value))
                .toArray();

        String permission = section.getString("Permission","");

        ItemStack itemStack = null;
        if (section.contains("DisplayItem")){
            itemStack = FCItemFactory.from(
                    section.getStringList("DisplayItem")
            ).build();
        }

        return new LayoutIcon(itemStack, slot, false, permission, null);
    }

    public static class DefaultIcons {

        @FCLocale(lang = LocaleType.EN_US, text = "§e§l<§e<§l<§7 Previous Page", hover = "\n§7§l§o Previous Page!\n ")
        @FCLocale(lang = LocaleType.PT_BR, text = "§e§l<§e<§l<§7 Página Anterior", hover = "\n§7§l§o Página Anterior!\n ")
        private static LocaleMessage PREVIOUS_PAGE;

        public static LayoutIcon getPreviousPageButton(){
            return FCItemFactory
                    .from(Material.REDSTONE)
                    .applyIf(() -> MCVersion.isHigherEquals(MCVersion.v1_16), builder -> builder.material(Material.SPECTRAL_ARROW))
                    .applyIf(() -> FCInputReader.parseMaterial("EVERPOKEUTILS_BACK") != null, fcItemBuilder -> fcItemBuilder.material("EVERPOKEUTILS_BACK"))
                    .displayName(PREVIOUS_PAGE.getDefaultFancyText().getText())
                    .lore(PREVIOUS_PAGE.getDefaultFancyText().getHoverText())
                    .asLayout();
        }

        @FCLocale(lang = LocaleType.EN_US, text = "§7Next Page §a§l>§a>§l>", hover = "\n§7§l§o Next Page!\n ")
        @FCLocale(lang = LocaleType.PT_BR, text = "§7Próxima Página §a§l>§a>§l>", hover = "\n§7§l§o Próxima Página!\n ")
        private static LocaleMessage NEXT_PAGE;

        public static LayoutIcon getNextPageButton(){
            return FCItemFactory
                    .from(Material.REDSTONE)
                    .applyIf(() -> MCVersion.isHigherEquals(MCVersion.v1_16), builder -> builder.material(Material.SPECTRAL_ARROW))
                    .applyIf(() -> FCInputReader.parseMaterial("EVERPOKEUTILS_FORWARD") != null, fcItemBuilder -> fcItemBuilder.material("EVERPOKEUTILS_FORWARD"))
                    .displayName(NEXT_PAGE.getDefaultFancyText().getText())
                    .lore(NEXT_PAGE.getDefaultFancyText().getHoverText())
                    .asLayout();
        }

        @FCLocale(lang = LocaleType.EN_US, text = "§6§l<§6<§l<", hover = "\n§7§l§o Back!\n ")
        @FCLocale(lang = LocaleType.PT_BR, text = "§6§l<§6<§l<", hover = "\n§7§l§o Voltar!\n ")
        private static LocaleMessage BACK_BUTTON;

        public static LayoutIcon getBackButton(){
            return FCItemFactory
                    .from(Material.REDSTONE)
                    .applyIf(() -> MCVersion.isHigherEquals(MCVersion.v1_16), builder -> builder.material(Material.SPECTRAL_ARROW))
                    .applyIf(() -> FCInputReader.parseMaterial("EVERPOKEUTILS_BACK") != null, fcItemBuilder -> fcItemBuilder.material("EVERPOKEUTILS_BACK"))
                    .displayName(BACK_BUTTON.getDefaultFancyText().getText())
                    .lore(BACK_BUTTON.getDefaultFancyText().getHoverText())
                    .asLayout();
        }
    }
}
