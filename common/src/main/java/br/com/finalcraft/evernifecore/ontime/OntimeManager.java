package br.com.finalcraft.evernifecore.ontime;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;

public class OntimeManager {

    private static IOntimeProvider ONTIME_PROVIDER = new IOntimeProvider() {
        @Override
        public long getOntime(IPlayerData playerData) {
            return 0;
        }
    };

    public static void setOntimeProvider(IOntimeProvider provider){
        ONTIME_PROVIDER = provider;
    }

    public static IOntimeProvider getProvider() {
        return ONTIME_PROVIDER;
    }

}
