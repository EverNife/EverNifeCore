package br.com.finalcraft.evernifecore.gui.custom;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.gui.builders.ComplexGuiBuilder;
import br.com.finalcraft.evernifecore.gui.item.GuiItemComplex;
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

public class GuiComplex extends Gui {

    private static final BukkitRunnable runnable;
    static{
        //TODO Add reload command for this? Maybe when there is a public plugin that uses this feature
        runnable = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    Inventory openInv = onlinePlayer.getOpenInventory().getTopInventory();
                    if (openInv.getHolder() instanceof GuiComplex){
                        GuiComplex guiComplex = (GuiComplex) openInv.getHolder();
                        guiComplex.onGuiUpdate(onlinePlayer);
                    }
                }
            }
        };
        runnable.runTaskTimer(EverNifeCore.instance, 1, ECSettings.DEFAULT_GUI_UPDATE_TIME); //2 Ticks might be enought for almost any case
    }

    public GuiComplex(int rows, @NotNull String title, @NotNull Set<InteractionModifier> interactionModifiers) {
        super(rows, title, interactionModifiers);
    }

    public GuiComplex(@NotNull GuiType guiType, @NotNull String title, @NotNull Set<InteractionModifier> interactionModifiers) {
        super(guiType, title, interactionModifiers);
    }

    protected void onGuiUpdate(Player player){
        class UpdateItem{
            GuiItemComplex itemComplex;
            boolean wasUpdated = false;
            @Override
            public int hashCode() {
                return itemComplex.hashCode();
            }
        }

        HashSet<GuiItemComplex> checked = new HashSet<>();
        HashSet<GuiItemComplex> updated = new HashSet<>();

        for (Map.Entry<Integer, GuiItem> entry : getGuiItems().entrySet()) {
            if (entry.getValue() instanceof GuiItemComplex){

                Integer slot = entry.getKey();
                GuiItemComplex guiItem = (GuiItemComplex) entry.getValue();

                if (!checked.contains(guiItem)){
                    if (guiItem.onItemUpdate(player)){
                        updated.add(guiItem);
                    }
                    checked.add(guiItem);
                }

                if (updated.contains(guiItem)){
                    getInventory().setItem(slot, guiItem.getItemStack());
                }
            }
        }

    }

    /**
     * Creates a {@link GuiComplex} with CHEST as the {@link GuiType}
     *
     * @return A CHEST {@link GuiComplex}
     * @since 3.0.0
     */
    @NotNull
    @Contract(" -> new")
    public static ComplexGuiBuilder complex() {
        return new ComplexGuiBuilder(GuiType.CHEST);
    }
}
