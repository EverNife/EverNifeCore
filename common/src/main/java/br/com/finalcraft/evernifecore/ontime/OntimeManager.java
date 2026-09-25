package br.com.finalcraft.evernifecore.ontime;

import br.com.finalcraft.evernifecore.playerdata.IPlayerData;

public class OntimeManager {

    /** The core's own answer while no plugin provides ontime: zero for everyone. */
    private static final IOntimeProvider NO_ONTIME = playerData -> 0;

    private static volatile IOntimeProvider ONTIME_PROVIDER = NO_ONTIME;

    public static synchronized void setOntimeProvider(IOntimeProvider provider){
        ONTIME_PROVIDER = provider;
    }

    /**
     * Takes {@code provider} back if it is still the one answering, and the core's own answer - zero for
     * everyone - returns. A provider that was already replaced is left alone, so a plugin disabling after
     * its successor registered never evicts it. Call it from the providing plugin's shutdown.
     *
     * @return whether {@code provider} was the one answering
     */
    public static synchronized boolean removeProvider(IOntimeProvider provider){
        if (ONTIME_PROVIDER != provider){
            return false;
        }
        ONTIME_PROVIDER = NO_ONTIME;
        return true;
    }

    public static IOntimeProvider getProvider() {
        return ONTIME_PROVIDER;
    }

}
