package br.com.finalcraft.evernifecore.minecraft.listeners.forge.imp;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.IForgeListener;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import org.bukkit.plugin.Plugin;

public class ArclightForgeListener implements IForgeListener {

    private static final String ARCLIGHT = "io.izzel.arclight.api.Arclight";
    private static final String REGISTER_FORGE_EVENT = "registerForgeEvent";

    @Override
    public void registerListener(Plugin plugin, ECListener listener, Object... eventBus) {
        for (Object bus : eventBus) {
            if (ForgeReflection.isModernEventBus(bus)){
                registerForgeEvent().invoke(null, plugin, bus, listener);
            }
        }
    }

    @Override
    public void registerListener(Plugin plugin, ECListener listener) {
        registerForgeEvent().invoke(null, plugin, ForgeReflection.defaultEventBus(), listener);
    }

    private static MethodInvoker<Object> registerForgeEvent() {
        return ForgeReflection.method(ARCLIGHT, REGISTER_FORGE_EVENT, 3);
    }

}
