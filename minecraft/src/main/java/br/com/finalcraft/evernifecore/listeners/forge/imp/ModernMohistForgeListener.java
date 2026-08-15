package br.com.finalcraft.evernifecore.listeners.forge.imp;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.listeners.forge.IForgeListener;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import org.bukkit.plugin.Plugin;

public class ModernMohistForgeListener implements IForgeListener {

    private static final String MOHIST_EVENT_BUS = "com.mohistmc.forge.MohistEventBus";
    private static final String REGISTER = "register";

    @Override
    public void registerListener(Plugin plugin, ECListener listener, Object... eventBus) {
        for (Object bus : eventBus) {
            register().invoke(null, bus, listener);
        }
    }

    @Override
    public void registerListener(Plugin plugin, ECListener listener) {
        register().invoke(null, ForgeReflection.defaultEventBus(), listener);
    }

    private static MethodInvoker<Object> register() {
        return ForgeReflection.method(MOHIST_EVENT_BUS, REGISTER, 2);
    }

}
