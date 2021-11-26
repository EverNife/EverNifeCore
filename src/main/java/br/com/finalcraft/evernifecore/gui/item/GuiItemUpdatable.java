package br.com.finalcraft.evernifecore.gui.item;

import dev.triumphteam.gui.components.GuiAction;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class GuiItemUpdatable extends GuiItem {
    private int updateTime;

    private Consumer<Player> onItemUpdate;

    public GuiItemUpdatable(@NotNull ItemStack itemStack, @Nullable GuiAction<@NotNull InventoryClickEvent> action) {
        super(itemStack, action);
    }

    public GuiItemUpdatable(@NotNull ItemStack itemStack) {
        super(itemStack);
    }

    public GuiItemUpdatable(@NotNull Material material) {
        super(material);
    }

    public GuiItemUpdatable(@NotNull Material material, @Nullable GuiAction<@NotNull InventoryClickEvent> action) {
        super(material, action);
    }

    public void onItemUpdate(Player player) {
        if (onItemUpdate != null){
            onItemUpdate.accept(player);
        }
    }

    public int getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(int updateTime) {
        this.updateTime = updateTime;
    }

    public void setOnItemUpdate(Consumer<Player> consumer){
        this.onItemUpdate = consumer;
    }
}
