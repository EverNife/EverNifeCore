package br.com.finalcraft.evernifecore.minecraft.inventory.extrainvs;

import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.minecraft.inventory.data.ItemInSlot;
import br.com.finalcraft.evernifecore.minecraft.inventory.extrainvs.factory.IExtraInvFactory;

import java.util.Collection;

public class ExtraInv extends GenericInventory {

    private final IExtraInvFactory factory;

    public ExtraInv(IExtraInvFactory factory, Collection<ItemInSlot> itemsInSlots) {
        super(itemsInSlots);
        this.factory = factory;
    }

    public ExtraInv(IExtraInvFactory factory) {
        this.factory = factory;
    }

    public IExtraInvFactory getFactory() {
        return factory;
    }

}
