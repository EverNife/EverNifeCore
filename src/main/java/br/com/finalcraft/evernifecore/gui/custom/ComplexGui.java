package br.com.finalcraft.evernifecore.gui.custom;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.gui.builders.ComplexGuiBuilder;
import br.com.finalcraft.evernifecore.gui.item.GuiItemUpdatable;
import dev.triumphteam.gui.components.GuiType;
import dev.triumphteam.gui.components.InteractionModifier;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ComplexGui extends Gui {

    private static final BukkitRunnable runnable;
    static {
        runnable = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    Inventory openInv = onlinePlayer.getOpenInventory().getTopInventory();
                    if (openInv.getHolder() instanceof ComplexGui){
                        ComplexGui complexGui = (ComplexGui) openInv.getHolder();
                        complexGui.onGuiUpdate(onlinePlayer);
                    }
                }
            }
        };
        runnable.runTaskTimer(EverNifeCore.instance, 1, 20);
    }

    private int updateFrequency = 10; //Update in ticks

    public void onGuiUpdate(Player player){

        HashSet<GuiItemUpdatable> updated = new HashSet<>();

        for (Map.Entry<Integer, GuiItem> entry : getGuiItems().entrySet()) {
            if (entry.getValue() instanceof GuiItemUpdatable){

                Integer slot = entry.getKey();
                GuiItemUpdatable guiItem = (GuiItemUpdatable) entry.getValue();

                if (!updated.contains(guiItem)){
                    guiItem.onItemUpdate(player);
                    updated.add(guiItem);
                }

                getInventory().setItem(slot, guiItem.getItemStack());
            }
        }

    }

    public ComplexGui(int rows, @NotNull String title, @NotNull Set<InteractionModifier> interactionModifiers) {
        super(rows, title, interactionModifiers);
    }

    public ComplexGui(@NotNull GuiType guiType, @NotNull String title, @NotNull Set<InteractionModifier> interactionModifiers) {
        super(guiType, title, interactionModifiers);
    }

    /**
     * Creates a {@link ComplexGui} with CHEST as the {@link GuiType}
     *
     * @return A CHEST {@link ComplexGui}
     * @since 3.0.0
     */
    @NotNull
    @Contract(" -> new")
    public static ComplexGuiBuilder complex() {
        return new ComplexGuiBuilder(GuiType.CHEST);
    }
}
