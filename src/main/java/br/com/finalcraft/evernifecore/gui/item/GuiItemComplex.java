package br.com.finalcraft.evernifecore.gui.item;

import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

public class GuiItemComplex extends GuiItem {

    private int updateInterval = ECSettings.DEFAULT_GUI_UPDATE_TIME;
    private int counter = 0;
    private BiConsumer<Player, GuiItemComplex> onItemUpdate;

    public GuiItemComplex(@NotNull ItemStack itemStack) {
        super(itemStack);
    }

    public boolean onItemUpdate(Player player) {
        if (onItemUpdate != null){
            counter = counter + ECSettings.DEFAULT_GUI_UPDATE_TIME;
            if (counter >= updateInterval){
                counter = 0;
                onItemUpdate.accept(player, this);
                return true;
            }
        }
        return false;
    }

    public GuiItemComplex setUpdateInterval(int updateInterval) {
        assert updateInterval > 0 : "UpdateInterval must be higher than 0";
        this.updateInterval = updateInterval;
        return this;
    }

    public GuiItemComplex setOnItemUpdate(BiConsumer<@NotNull Player, @NotNull GuiItemComplex> onItemUpdate){
        this.onItemUpdate = onItemUpdate;
        return this;
    }
}
