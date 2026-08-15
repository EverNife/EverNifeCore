package br.com.finalcraft.evernifecore.listeners.forge.imp;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.listeners.forge.IForgeListener;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import net.minecraftforge.common.MinecraftForge;
import org.bukkit.plugin.Plugin;

public class ModernMohistForgeListener implements IForgeListener {

    private static MethodInvoker<Object> MohistEventBus_register = FCReflectionUtil.getMethods().getMethod(
            FCReflectionUtil.getClasses().getClass("com.mohistmc.forge.MohistEventBus"),
            "register"
    );

    @Override
    public void registerListener(Plugin plugin, ECListener listener, Object... eventBus) {
        for (Object bus : eventBus) {
            MohistEventBus_register.invoke(null, bus, listener);
        }
    }

    @Override
    public void registerListener(Plugin plugin, ECListener listener) {
        MohistEventBus_register.invoke(null, MinecraftForge.EVENT_BUS, listener);
    }

}
