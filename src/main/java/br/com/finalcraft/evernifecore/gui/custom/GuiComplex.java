package br.com.finalcraft.evernifecore.gui.custom;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.gui.item.GuiItemComplex;
import br.com.finalcraft.evernifecore.util.commons.SimpleEntry;
import dev.triumphteam.gui.components.GuiType;
import dev.triumphteam.gui.components.InteractionModifier;
import dev.triumphteam.gui.guis.Gui;
import org.apache.commons.lang3.Validate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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

    protected int updateInterval = ECSettings.DEFAULT_GUI_UPDATE_TIME;
    protected int counter = 0;
    protected BiConsumer<Player, GuiComplex> onGuiUpdate;

    public GuiComplex(int rows, @NotNull String title, @NotNull Set<InteractionModifier> interactionModifiers) {
        super(rows, title, interactionModifiers);
    }

    public GuiComplex(@NotNull GuiType guiType, @NotNull String title, @NotNull Set<InteractionModifier> interactionModifiers) {
        super(guiType, title, interactionModifiers);
    }

    /**
     * This method is used to get all GuiItems that are GuiItemComplex and are inside a slot.
     *
     * @return A list of Map.Entry with the slot and it's given GuiItemComplex
     */
    protected List<Map.Entry<Integer, GuiItemComplex>> getAllGuiItemsComplexThatCanBeUpdated(){
        return getGuiItems().entrySet().stream()
                .filter(integerGuiItemEntry -> integerGuiItemEntry.getValue() instanceof GuiItemComplex)
                .map(entry -> SimpleEntry.of(entry.getKey(), (GuiItemComplex) entry.getValue()))
                .collect(Collectors.toList());
    }

    protected void onGuiUpdate(Player player){
        if (onGuiUpdate != null){
//            counter = counter + ECSettings.DEFAULT_GUI_UPDATE_TIME;
//            if (counter >= updateInterval){
//                counter = 0;
                onGuiUpdate.accept(player, this); //Update this Gui
//            }
        }

        HashSet<GuiItemComplex> checked = new HashSet<>();
        HashSet<GuiItemComplex> updated = new HashSet<>();

        //Now update each GuiItem attached to this GUI.
        //If an GuiItem is the same for more than one slot, the GuiItem will be updated only once!
        for (Map.Entry<Integer, GuiItemComplex> entry : getAllGuiItemsComplexThatCanBeUpdated()) {
            Integer slot = entry.getKey();
            GuiItemComplex guiItem = entry.getValue();

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

    public GuiComplex setUpdateInterval(int updateTicks) {
        Validate.isTrue(updateTicks > 0, "updateTicks must be higher than 0");
        this.updateInterval = updateTicks;
        return this;
    }

    public GuiComplex setOnGuiUpdate(BiConsumer<@NotNull Player, @NotNull GuiComplex> onGuiUpdate){
        this.onGuiUpdate = onGuiUpdate;
        return this;
    }

    public void softUpdate() {
        if (this.getInventory().getViewers().size() == 0) return;
        Player player = (Player) this.getInventory().getViewers().get(0);

        for (Map.Entry<Integer, GuiItemComplex> entry : getAllGuiItemsComplexThatCanBeUpdated()) {
            GuiItemComplex complex = entry.getValue();
            complex.forceUpdate(this, player);
            updateItem(entry.getKey(), complex);
        }
    }

}
