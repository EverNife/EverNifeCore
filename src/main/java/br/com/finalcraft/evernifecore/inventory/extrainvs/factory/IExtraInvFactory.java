package br.com.finalcraft.evernifecore.inventory.extrainvs.factory;

import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.inventory.extrainvs.ExtraInv;
import org.bukkit.entity.Player;

import java.util.Collections;

public interface IExtraInvFactory {

    public String getId();

    public int getInvMaxSize();

    public ExtraInv getPlayerExtraInv(Player player);

    public void setPlayerExtraInv(Player player, ExtraInv e);

    public default ExtraInv loadExtraInv(ConfigSection section) {
        GenericInventory genericInventory = section.getLoadable("", GenericInventory.class); //This will load the GenericInventory under the 'path.items'
        return new ExtraInv(
                this,
                genericInventory.getItems()
        );
    }

    public default ExtraInv createEmptyExtraInv(){
        return new ExtraInv(this, Collections.EMPTY_LIST);
    }

}
