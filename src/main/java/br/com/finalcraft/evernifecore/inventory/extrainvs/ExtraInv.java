package br.com.finalcraft.evernifecore.inventory.extrainvs;

import br.com.finalcraft.evernifecore.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.inventory.extrainvs.factory.IExtraInvFactory;

import java.util.Collection;

public class ExtraInv extends GenericInventory {

    private final IExtraInvFactory factory;

    public ExtraInv(IExtraInvFactory factory, Collection<ItemInSlot> itemsInSlots) {
        super(itemsInSlots);
        this.factory = factory;
    }

    public IExtraInvFactory getFactory() {
        return factory;
    }

}
