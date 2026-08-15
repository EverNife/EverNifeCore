package br.com.finalcraft.evernifecore.listeners.forge.imp;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.listeners.forge.IForgeListener;
import io.izzel.arclight.api.Arclight;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import org.bukkit.plugin.Plugin;

public class ArclightForgeListener implements IForgeListener {

    @Override
    public void registerListener(Plugin plugin, ECListener listener, Object... eventBus) {
        for (Object bus : eventBus) {
            if (bus instanceof IEventBus){
                IEventBus iEventBus = (IEventBus) bus;
                Arclight.registerForgeEvent(plugin, iEventBus, listener);
            }
        }
    }

    @Override
    public void registerListener(Plugin plugin, ECListener listener) {
        Arclight.registerForgeEvent(plugin, MinecraftForge.EVENT_BUS, listener);
    }

}
