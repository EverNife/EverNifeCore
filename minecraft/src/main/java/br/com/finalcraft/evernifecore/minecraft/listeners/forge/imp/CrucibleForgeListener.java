package br.com.finalcraft.evernifecore.minecraft.listeners.forge.imp;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.IForgeListener;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

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
        for (Object bus : defaultEventBuses()) {
            register().invoke(null, plugin, bus, listener);
        }
    }

    /**
     * Both buses 1.7.10 posts on: {@code MinecraftForge.EVENT_BUS} and FML's own. A handler on a bus
     * that never posts its event just never runs, so registering on both costs nothing - and missing
     * FML's is a {@code TickEvent} handler that never fires with nothing saying why.
     */
    static List<Object> defaultEventBuses() {
        List<Object> buses = new ArrayList<>(2);
        Object forgeBus = ForgeReflection.defaultEventBus();
        Object fmlBus = ForgeReflection.fmlEventBus();
        buses.add(forgeBus);
        if (fmlBus != forgeBus) {
            buses.add(fmlBus); //the same bus twice would deliver every event twice
        }
        return buses;
    }

    private static MethodInvoker<Object> register() {
        return ForgeReflection.method(CRUCIBLE_EVENT_BUS, REGISTER, 3);
    }

}
