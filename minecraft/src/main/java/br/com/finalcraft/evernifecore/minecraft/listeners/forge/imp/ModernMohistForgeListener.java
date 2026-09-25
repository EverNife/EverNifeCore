package br.com.finalcraft.evernifecore.minecraft.listeners.forge.imp;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.ForgeRegistration;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.IForgeListener;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Collections;

public class ModernMohistForgeListener implements IForgeListener {

    private static final String MOHIST_EVENT_BUS = "com.mohistmc.forge.MohistEventBus";
    private static final String REGISTER = "register";

    @Override
    public ForgeRegistration registerListener(Plugin plugin, ECListener listener, Object... eventBus) {
        for (Object bus : eventBus) {
            register().invoke(null, bus, listener);
        }
        return ForgeReflection.unregisterFrom(Arrays.asList(eventBus), listener);
    }

    @Override
    public ForgeRegistration registerListener(Plugin plugin, ECListener listener) {
        Object bus = ForgeReflection.defaultEventBus();
        register().invoke(null, bus, listener);
        return ForgeReflection.unregisterFrom(Collections.singletonList(bus), listener);
    }

    private static MethodInvoker<Object> register() {
        return ForgeReflection.method(MOHIST_EVENT_BUS, REGISTER, 2);
    }

}
