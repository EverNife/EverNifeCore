package br.com.finalcraft.evernifecore.listeners.forge.imp;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.listeners.forge.IForgeListener;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import net.minecraftforge.common.MinecraftForge;
import org.bukkit.plugin.Plugin;

public class CrucibleForgeListener implements IForgeListener {

    private static MethodInvoker<Object> CrucibleEventBus_register = FCReflectionUtil.getMethods().getMethod(
            FCReflectionUtil.getClasses().getClass("io.github.crucible.api.CrucibleEventBus"),
            "register"
    );

    @Override
    public void registerListener(Plugin plugin, ECListener listener, Object... eventBus) {
        for (Object bus : eventBus) {
            CrucibleEventBus_register.invoke(null, plugin, bus, listener);
        }
    }

    @Override
    public void registerListener(Plugin plugin, ECListener listener) {
        CrucibleEventBus_register.invoke(null, plugin, MinecraftForge.EVENT_BUS, listener);
    }

}
