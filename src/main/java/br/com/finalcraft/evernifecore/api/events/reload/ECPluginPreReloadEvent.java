package br.com.finalcraft.evernifecore.api.events.reload;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * ECPlugins fire this event before they are reloaded.
 *
 * @author EverNife
 */
public class ECPluginPreReloadEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final ECPluginData ecPluginData;

    public ECPluginPreReloadEvent(ECPluginData ecPluginData) {
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
