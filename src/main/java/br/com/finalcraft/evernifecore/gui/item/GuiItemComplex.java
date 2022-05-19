package br.com.finalcraft.evernifecore.gui.item;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.gui.custom.GuiComplex;
import br.com.finalcraft.evernifecore.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.itemstack.itembuilder.FCItemBuilder;
import dev.triumphteam.gui.components.GuiAction;
import dev.triumphteam.gui.guis.GuiItem;
import org.apache.commons.lang.Validate;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

public class GuiItemComplex extends GuiItem {

    private int updateInterval = ECSettings.DEFAULT_GUI_UPDATE_TIME;
    private transient int counter = 0;
    private Consumer<@NotNull Context> onItemUpdate;

    public GuiItemComplex(@NotNull ItemStack itemStack) {
        super(itemStack);
    }

    public boolean onItemUpdate(GuiComplex guiComplex, Player player) {
        if (onItemUpdate != null){
            counter = counter + ECSettings.DEFAULT_GUI_UPDATE_TIME;
            if (counter >= updateInterval){
                counter = 0;
                onItemUpdate.accept(Context.of(guiComplex, this, player));
                return true;
            }
        }
        return false;
    }

    public void forceUpdate(GuiComplex guiComplex, Player player){
        if (onItemUpdate != null){
            onItemUpdate.accept(Context.of(guiComplex, this, player));
        }
    }

    public GuiItemComplex setUpdateInterval(int updateTicks) {
        Validate.isTrue(updateTicks > 0, "updateTicks must be higher than 0");
        this.updateInterval = updateTicks;
        return this;
    }

    public GuiItemComplex setOnItemUpdate(Consumer<@NotNull Context> onItemUpdate){
        this.onItemUpdate = onItemUpdate;
        return this;
    }

    public GuiItemComplex updateItemStack(@NotNull Function<@NotNull FCItemBuilder, @NotNull ItemStack> update) {
        FCItemBuilder itemBuilder = FCItemFactory.from(this.getItemStack());
        this.setItemStack(update.apply(itemBuilder));
        return this;
    }

    @Override
    public GuiItemComplex setAction(@Nullable GuiAction<@NotNull InventoryClickEvent> action) {
        super.setAction(action);
        return this;
    }

    public static class Context {
        private final GuiComplex gui;
        private final GuiItemComplex guiItem;
        private final Player player;

        protected Context(GuiComplex guiComplex, GuiItemComplex guiItem, Player player) {
            this.gui = guiComplex;
            this.guiItem = guiItem;
            this.player = player;
        }

        public static Context of(GuiComplex guiComplex, GuiItemComplex guiItem, Player player){
            return new Context(guiComplex, guiItem, player);
        }

        public void updateItemStack(Function<FCItemBuilder, ItemStack> updateItemStack){
            ItemStack result = updateItemStack.apply(FCItemFactory.from(this.getGuiItem().getItemStack()));
            this.getGuiItem().setItemStack(result);
        }

        public Player getPlayer() {
            return player;
        }

        public GuiComplex getGui() {
            return gui;
        }

        public GuiItemComplex getGuiItem() {
            return guiItem;
        }
    }
}
