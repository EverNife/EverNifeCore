package br.com.finalcraft.evernifecore.logic.core;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logic.ProgressOwnerProvider;
import br.com.finalcraft.evernifecore.logic.ProgressOwners;

/** The core's stock logic types and owner kinds. */
public final class CoreLogic {

    private CoreLogic() {
    }

    /** Registers every stock type and the core's owner kinds under {@code core}, the EverNifeCore plugin data. */
    public static void registerAll(ECPluginData core) {
        Compositions.register(core);
        for (ProgressOwnerProvider provider : CoreOwners.all()) {
            ProgressOwners.register(core, provider);
        }
    }
}
