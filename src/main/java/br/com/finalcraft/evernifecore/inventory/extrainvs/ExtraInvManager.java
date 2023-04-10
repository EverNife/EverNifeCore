package br.com.finalcraft.evernifecore.inventory.extrainvs;

import br.com.finalcraft.evernifecore.integration.everforgelib.EverForgeLibIntegration;
import br.com.finalcraft.evernifecore.inventory.extrainvs.factory.IExtraInvFactory;
import br.com.finalcraft.evernifecore.inventory.extrainvs.factory.imp.ArmourersInvFactory;
import br.com.finalcraft.evernifecore.inventory.extrainvs.factory.imp.BaublesInvFactory;
import br.com.finalcraft.evernifecore.inventory.extrainvs.factory.imp.TinkersInvFactory;

import java.util.Collection;
import java.util.LinkedHashMap;

public class ExtraInvManager {

    private static LinkedHashMap<String, IExtraInvFactory<?>> INVENTORY_FACTORIES = new LinkedHashMap<>();

    public static void registerFactory(IExtraInvFactory<?> factory){
        INVENTORY_FACTORIES.put(factory.getId().toLowerCase(), factory);
    }

    static {
        if (EverForgeLibIntegration.baublesLoaded) registerFactory(new BaublesInvFactory());
        if (EverForgeLibIntegration.tinkersLoaded) registerFactory(new TinkersInvFactory());
        if (EverForgeLibIntegration.armourersWorkShopLoaded) registerFactory(new ArmourersInvFactory());
    }

    public static Collection<IExtraInvFactory<?>> getAllFactories() {
        return INVENTORY_FACTORIES.values();
    }

    public static IExtraInvFactory<?> getFactory(String factoryId){
        return INVENTORY_FACTORIES.get(factoryId.toLowerCase());
    }
}
