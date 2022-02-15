package br.com.finalcraft.evernifecore.gui.custom;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.gui.item.GuiItemComplex;
import dev.triumphteam.gui.components.GuiType;
import dev.triumphteam.gui.components.InteractionModifier;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class GuiComplex extends Gui {

    private static final BukkitRunnable runnable;
    static{
        //TODO Add reload command for this? Maybe when there is a public plugin that uses this feature
        runnable = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    InventoryView inventoryView = onlinePlayer.getOpenInventory();
                    if (inventoryView != null){ //Should be notNull...
                        Inventory openInv = inventoryView.getTopInventory();
                        if (openInv.getHolder() instanceof GuiComplex){
                            GuiComplex guiComplex = (GuiComplex) openInv.getHolder();
                            guiComplex.onGuiUpdate(onlinePlayer);
                        }
                    }

                }
            }
        };
        runnable.runTaskTimer(EverNifeCore.instance, 1, ECSettings.DEFAULT_GUI_UPDATE_TIME); //2 Ticks might be enought for almost any case
    }

    private int updateInterval = ECSettings.DEFAULT_GUI_UPDATE_TIME;
    private int counter = 0;
    private BiConsumer<Player, GuiComplex> onGuiUpdate;

    public GuiComplex(int rows, @NotNull String title, @NotNull Set<InteractionModifier> interactionModifiers) {
        super(rows, title, interactionModifiers);
    }

    public GuiComplex(@NotNull GuiType guiType, @NotNull String title, @NotNull Set<InteractionModifier> interactionModifiers) {
        super(guiType, title, interactionModifiers);
    }

    protected void onGuiUpdate(Player player){
        if (onGuiUpdate != null){
            counter = counter + ECSettings.DEFAULT_GUI_UPDATE_TIME;
            if (counter >= updateInterval){
                counter = 0;
                onGuiUpdate.accept(player, this); //Update this Gui
            }
        }

        HashSet<GuiItemComplex> checked = new HashSet<>();
        HashSet<GuiItemComplex> updated = new HashSet<>();

        //Now update each GuiItem attached to this GUI.
        //If an GuiItem is the same for more than one slot, the GuiItem will be updated only once!
        for (Map.Entry<Integer, GuiItem> entry : getGuiItems().entrySet()) {
            if (entry.getValue() instanceof GuiItemComplex){

                Integer slot = entry.getKey();
                GuiItemComplex guiItem = (GuiItemComplex) entry.getValue();

                if (!checked.contains(guiItem)){
                    if (guiItem.onItemUpdate(this, player)){
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

    public GuiComplex setUpdateInterval(int updateInterval) {
        assert updateInterval > 0 : "UpdateInterval must be higher than 0";
        this.updateInterval = updateInterval;
        return this;
    }

    public GuiComplex setOnGuiUpdate(BiConsumer<@NotNull Player, @NotNull GuiComplex> onGuiUpdate){
        this.onGuiUpdate = onGuiUpdate;
        return this;
    }

    public void softUpdate() {
        if (this.getInventory().getViewers().size() == 0) return;
        Player player = (Player) this.getInventory().getViewers().get(0);

        for (Map.Entry<Integer, GuiItem> entry : new ArrayList<>(getGuiItems().entrySet())) {
            if (entry.getValue() instanceof GuiItemComplex){
                GuiItemComplex complex = (GuiItemComplex) entry.getValue();
                complex.forceUpdate(this, player);
                updateItem(entry.getKey(), complex);
            }
        }
    }

}
