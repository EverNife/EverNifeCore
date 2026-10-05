package br.com.finalcraft.evernifecore.minecraft.listeners.forge.imp;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.ForgeRegistration;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.IForgeListener;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ArclightForgeListener implements IForgeListener {

    private static final String ARCLIGHT = "io.izzel.arclight.api.Arclight";
    private static final String REGISTER_FORGE_EVENT = "registerForgeEvent";

    @Override
    public ForgeRegistration registerListener(Plugin plugin, ECListener listener, Object... eventBus) {
        List<Object> buses = Arrays.asList(eventBus);
        for (Object bus : buses) {
            if (!ForgeReflection.isModernEventBus(bus)){
                throw new IllegalArgumentException("Cannot register " + listener.getClass().getName() + " on "
                        + (bus == null ? "a null bus" : "a bus of type " + bus.getClass().getName())
                        + ": on this server only a net.minecraftforge.eventbus.api.IEventBus can take a"
                        + " listener. Nothing was registered - hand over MinecraftForge.EVENT_BUS, a mod's own"
                        + " IEventBus, or use registerListener(plugin, listener) for the default one.");
            }
        }
        for (Object bus : buses) {
            registerForgeEvent().invoke(null, plugin, bus, listener);
        }
        return ForgeReflection.unregisterFrom(buses, listener);
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
