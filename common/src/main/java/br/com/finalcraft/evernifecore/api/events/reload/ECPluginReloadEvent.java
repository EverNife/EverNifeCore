package br.com.finalcraft.evernifecore.api.events.reload;

import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

/**
 * ECPlugins fire these event before/after they are reloaded. Subscribing to this base hears both
 * halves; the nested types are what is actually fired.
 */
public abstract class ECPluginReloadEvent extends ECEvent implements IECEvent {

    /**
     * Bukkit-only: the native handler list of the whole family, declared here and here only - a Bukkit
     * listener on this base hears Pre and Post because neither declares a list of its own.
     */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(ECPluginReloadEvent.class);
    }

    private final ECPluginData ecPluginData;

    public ECPluginReloadEvent(ECPluginData ecPluginData) {
        this.ecPluginData = ecPluginData;
    }

    public ECPluginData getECPlugin() {
        return ecPluginData;
    }

    public static class Pre extends ECPluginReloadEvent {

        public Pre(ECPluginData ecPluginData) {
            super(ecPluginData);
        }

    }

    public static class Post extends ECPluginReloadEvent {

        public Post(ECPluginData ecPluginData) {
            super(ecPluginData);
        }

    }

}
