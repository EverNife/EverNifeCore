package br.com.finalcraft.evernifecore.inventory.extrainvs;

import br.com.finalcraft.evernifecore.integration.everforgelib.EverForgeLibIntegration;
import br.com.finalcraft.evernifecore.inventory.extrainvs.factory.IExtraInvFactory;
import br.com.finalcraft.evernifecore.inventory.extrainvs.factory.imp.ArmourersInvFactory;
import br.com.finalcraft.evernifecore.inventory.extrainvs.factory.imp.BaublesInvFactory;
import br.com.finalcraft.evernifecore.inventory.extrainvs.factory.imp.TinkersInvFactory;

import java.util.ArrayList;
import java.util.List;

public class ExtraInvManager {

    public static List<IExtraInvFactory> INVENTORY_FACTORIES = new ArrayList<>();

    static {
        if (EverForgeLibIntegration.baublesLoaded) getAllFactories().add(new BaublesInvFactory());
        if (EverForgeLibIntegration.tinkersLoaded) getAllFactories().add(new TinkersInvFactory());
        if (EverForgeLibIntegration.armourersWorkShopLoaded) getAllFactories().add(new ArmourersInvFactory());
    }

    public static List<IExtraInvFactory> getAllFactories() {
        return INVENTORY_FACTORIES;
    }

    public static IExtraInvFactory getFactory(String factoryId){
        return INVENTORY_FACTORIES.stream()
                .filter(factory -> factory.getId().equals(factoryId))
                .findFirst()
                .orElse(null);
    }
}
