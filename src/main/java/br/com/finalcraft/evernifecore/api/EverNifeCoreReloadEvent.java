package br.com.finalcraft.evernifecore.api;

import br.com.finalcraft.evernifecore.api.events.reload.ECPluginReloadEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPlugin;

public class EverNifeCoreReloadEvent extends ECPluginReloadEvent {

    public EverNifeCoreReloadEvent(ECPlugin ecPlugin) {
        super(ecPlugin);
    }

}
