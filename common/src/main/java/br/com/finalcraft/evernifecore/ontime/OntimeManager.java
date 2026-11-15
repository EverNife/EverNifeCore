package br.com.finalcraft.evernifecore.ontime;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import jakarta.annotation.Nullable;

/**
 * The one {@link IOntimeProvider} the server asks, owned by the plugin that registered it. The core answers zero for
 * everyone while nobody provides; on Bukkit the core itself registers the vanilla statistic of whoever is online.
 */
public class OntimeManager {

    /** The answer while no plugin provides ontime: zero for everyone. */
    private static final IOntimeProvider NO_ONTIME = playerData -> 0;

    private static volatile IOntimeProvider ONTIME_PROVIDER = NO_ONTIME;
    private static volatile @Nullable ECPluginData OWNER;

    /**
     * Makes {@code provider}, owned by {@code owner}, the server's ontime. It leaves on its own when {@code owner} is
     * released (disable or reload): the plugin does not take it back.
     */
    public static synchronized void setOntimeProvider(ECPluginData owner, IOntimeProvider provider) {
        ONTIME_PROVIDER = provider;
        OWNER = owner;
    }

    /**
     * Zero for everyone again, if {@code owner} still owns the provider answering; a plugin that was already replaced
     * never evicts its successor. Run by the core when the plugin is released.
     *
     * @return whether {@code owner} owned the provider answering
     */
    public static synchronized boolean unregister(ECPluginData owner) {
        if (OWNER != owner) {
            return false;
        }
        ONTIME_PROVIDER = NO_ONTIME;
        OWNER = null;
        return true;
    }

    public static IOntimeProvider getProvider() {
        return ONTIME_PROVIDER;
    }

    /** The plugin whose provider answers, null while nobody provides. */
    public static @Nullable ECPluginData getOwner() {
        return OWNER;
    }

}
