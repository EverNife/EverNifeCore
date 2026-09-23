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
     * Register a Listener to the buses Forge posts its own events on: {@code MinecraftForge.EVENT_BUS},
     * plus {@code FMLCommonHandler.instance().bus()} on 1.7.10, the one era where FML keeps a bus of its
     * own ({@code TickEvent}, FML's {@code PlayerEvent} family). A mod's private bus is never among
     * them - hand it to {@link #registerListener(Plugin, ECListener, Object...)}.
     *
     * @param plugin The plugin that is registering the listener
     * @param listener The listener to register
     */
    public void registerListener(Plugin plugin, ECListener listener);

}
