package br.com.finalcraft.evernifecore.minecraft.listeners.forge.imp;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.ForgeRegistration;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.IForgeListener;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArclightForgeListener implements IForgeListener {

    private static final String ARCLIGHT = "io.izzel.arclight.api.Arclight";
    private static final String REGISTER_FORGE_EVENT = "registerForgeEvent";

    @Override
    public ForgeRegistration registerListener(Plugin plugin, ECListener listener, Object... eventBus) {
        List<Object> reached = new ArrayList<>();
        for (Object bus : eventBus) {
            if (ForgeReflection.isModernEventBus(bus)){
                registerForgeEvent().invoke(null, plugin, bus, listener);
                reached.add(bus);
            }
        }
        return ForgeReflection.unregisterFrom(reached, listener);
    }

    @Override
    public ForgeRegistration registerListener(Plugin plugin, ECListener listener) {
        Object bus = ForgeReflection.defaultEventBus();
        registerForgeEvent().invoke(null, plugin, bus, listener);
        return ForgeReflection.unregisterFrom(Collections.singletonList(bus), listener);
    }

    private static MethodInvoker<Object> registerForgeEvent() {
        return ForgeReflection.method(ARCLIGHT, REGISTER_FORGE_EVENT, 3);
    }

}
