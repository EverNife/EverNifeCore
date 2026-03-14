package br.com.finalcraft.evernifecore.inventory.extrainvs.factory;

import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.inventory.extrainvs.ExtraInv;
import org.bukkit.entity.Player;

import java.util.Collections;

public interface IExtraInvFactory<E extends ExtraInv> {

    public String getId();

    public int getInvMaxSize();

    public E extractFromPlayer(Player player);

    public void applyToPlayer(Player player, E e);

    public default E onConfigLoad(ConfigSection section) {
        GenericInventory genericInventory = section.getLoadable("", GenericInventory.class); //This will load the GenericInventory under the 'path.items'
        return (E) new ExtraInv(
                this,
                genericInventory.getItems()
        );
    }

    public default E createEmptyExtraInv(){
        return (E) new ExtraInv(this, Collections.EMPTY_LIST);
    }

}
