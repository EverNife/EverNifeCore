package br.com.finalcraft.evernifecore.api.events.reload;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

/**
 * ECPlugins fire these event before/after they are reloaded.
 */
public abstract class ECPluginReloadEvent {

    private final ECPluginData ecPluginData;

    public ECPluginReloadEvent(ECPluginData ecPluginData) {
        this.ecPluginData = ecPluginData;
    }

    public ECPluginData getECPlugin() {
        return ecPluginData;
    }

    public static class Pre extends ECPluginReloadEvent implements IECEvent {

        public Pre(ECPluginData ecPluginData) {
            super(ecPluginData);
        }

    }

    public static class Post extends ECPluginReloadEvent implements IECEvent {

        public Post(ECPluginData ecPluginData) {
            super(ecPluginData);
        }

    }

}
