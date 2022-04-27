package br.com.finalcraft.evernifecore.api;

import br.com.finalcraft.evernifecore.api.events.reload.ECPluginReloadEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

public class EverNifeCoreReloadEvent extends ECPluginReloadEvent {

    public EverNifeCoreReloadEvent(ECPluginData ecPluginData) {
        super(ecPluginData);
    }

}
