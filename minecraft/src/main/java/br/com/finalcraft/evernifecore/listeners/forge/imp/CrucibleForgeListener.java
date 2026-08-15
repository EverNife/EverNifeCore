package br.com.finalcraft.evernifecore.listeners.forge.imp;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.listeners.forge.IForgeListener;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import org.bukkit.plugin.Plugin;

public class CrucibleForgeListener implements IForgeListener {

    private static final String CRUCIBLE_EVENT_BUS = "io.github.crucible.api.CrucibleEventBus";
    private static final String REGISTER = "register";

    @Override
    public void registerListener(Plugin plugin, ECListener listener, Object... eventBus) {
        for (Object bus : eventBus) {
            register().invoke(null, plugin, bus, listener);
        }
    }

    @Override
    public void registerListener(Plugin plugin, ECListener listener) {
        register().invoke(null, plugin, ForgeReflection.defaultEventBus(), listener);
    }

    private static MethodInvoker<Object> register() {
        return ForgeReflection.method(CRUCIBLE_EVENT_BUS, REGISTER, 3);
    }

}
