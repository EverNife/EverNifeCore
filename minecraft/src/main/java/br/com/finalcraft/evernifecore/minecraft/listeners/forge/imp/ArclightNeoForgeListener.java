package br.com.finalcraft.evernifecore.minecraft.listeners.forge.imp;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.ForgeRegistration;
import br.com.finalcraft.evernifecore.minecraft.listeners.forge.IForgeListener;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The route to the NeoForge side of an Arclight hybrid: the listener is registered on the bus itself.
 *
 * <p>Arclight's own entry point is not used. On its NeoForge build that class still declares a method
 * taking a {@code net.minecraftforge} bus, a type the runtime does not carry, so listing its methods by
 * name fails before any of them can be picked - and what it would do for a NeoForge bus is call the
 * bus's {@code register(Object)}, which is what happens here.</p>
 */
public class ArclightNeoForgeListener implements IForgeListener {

    @Override
    public ForgeRegistration registerListener(Plugin plugin, ECListener listener, Object... eventBus) {
        List<Object> buses = Arrays.asList(eventBus);
        for (Object bus : buses) {
            if (!ForgeReflection.isNeoForgeEventBus(bus)){
                throw new IllegalArgumentException("Cannot register " + listener.getClass().getName() + " on "
                        + (bus == null ? "a null bus" : "a bus of type " + bus.getClass().getName())
                        + ": this server's Forge side is NeoForge, and only a net.neoforged.bus.api.IEventBus"
                        + " can take a listener here. Nothing was registered - hand over NeoForge.EVENT_BUS,"
                        + " a mod's own IEventBus, or use registerListener(plugin, listener) for the default one.");
            }
        }
        for (Object bus : buses) {
            ForgeReflection.registerOnNeoForgeBus(bus, listener);
        }
        return ForgeReflection.unregisterFrom(buses, listener);
    }

    @Override
    public ForgeRegistration registerListener(Plugin plugin, ECListener listener) {
        Object bus = ForgeReflection.neoForgeEventBus();
        ForgeReflection.registerOnNeoForgeBus(bus, listener);
        return ForgeReflection.unregisterFrom(Collections.singletonList(bus), listener);
    }

}
