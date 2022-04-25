package br.com.finalcraft.evernifecore.api.events.reload;

import br.com.finalcraft.evernifecore.ecplugin.ECPlugin;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * ECPlugins fire this event before they are reloaded.
 *
 * @author EverNife
 */
public class ECPluginPreReloadEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final ECPlugin ecPlugin;

    public ECPluginPreReloadEvent(ECPlugin ecPlugin) {
        this.ecPlugin = ecPlugin;
    }

    public ECPlugin getEcPlugin() {
        return ecPlugin;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

}
