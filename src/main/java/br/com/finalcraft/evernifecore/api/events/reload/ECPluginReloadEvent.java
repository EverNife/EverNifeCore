package br.com.finalcraft.evernifecore.api.events.reload;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * ECPlugins should fire this event after they are reloaded.
 *
 * @author EverNife
 */
public class ECPluginReloadEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final ECPluginData ecPluginData;

    public ECPluginReloadEvent(ECPluginData ecPluginData) {
        this.ecPluginData = ecPluginData;
    }

    public ECPluginData getECPlugin() {
        return ecPluginData;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

}
