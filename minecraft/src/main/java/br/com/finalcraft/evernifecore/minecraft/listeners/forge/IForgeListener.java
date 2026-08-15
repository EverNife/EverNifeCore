package br.com.finalcraft.evernifecore.minecraft.listeners.forge;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import org.bukkit.plugin.Plugin;

public interface IForgeListener {

    /**
     * Register a Listener to a specific EventBus
     *
     * @param plugin The plugin that is registering the listener
     * @param listener The listener to register
     * @param eventBus The EventBus to register the listener to.
     *                 Can be anything that is accepted by the specific implementation
     */
    public void registerListener(Plugin plugin, ECListener listener, Object... eventBus);

    /**
     * Register a Listener to the default EventBuses
     * Usually this is the:
     *      MinecraftForge.EVENT_BUS
     *      FMLCommonHandler.instance().bus()
     *
     * But depending on the implementation it can be different
     * It's a good practice to use {@link #registerListener(Plugin, ECListener, Object...)} instead
     *
     * @param plugin The plugin that is registering the listener
     * @param listener The listener to register
     */
    public void registerListener(Plugin plugin, ECListener listener);

}
