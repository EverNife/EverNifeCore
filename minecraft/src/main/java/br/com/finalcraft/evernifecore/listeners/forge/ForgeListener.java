package br.com.finalcraft.evernifecore.listeners.forge;

import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.listeners.forge.imp.ArclightForgeListener;
import br.com.finalcraft.evernifecore.listeners.forge.imp.CrucibleForgeListener;
import br.com.finalcraft.evernifecore.listeners.forge.imp.ModernMohistForgeListener;
import br.com.finalcraft.evernifecore.listeners.forge.imp.MohistForgeListener;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import org.bukkit.plugin.Plugin;

public class ForgeListener {

    private static IForgeListener INSTANCE; static {
        if (FCReflectionUtil.getClasses().isClassLoaded("io.github.crucible.api.CrucibleEventBus")){
            //Present on 1.7.10
            INSTANCE = new CrucibleForgeListener();
        }else if (FCReflectionUtil.getClasses().isClassLoaded("io.izzel.arclight.api.Arclight")){
            //Present on 1.12.2 and 1.16.5 and 1.20.x
            INSTANCE = new ArclightForgeListener();
        }else if (FCReflectionUtil.getClasses().isClassLoaded("com.mohistmc.forge.MohistEventBus")){
            //Present on 1.20.x
            INSTANCE = new ModernMohistForgeListener();
        }else if (FCReflectionUtil.getClasses().isClassLoaded("com.mohistmc.api.event.BukkitHookForgeEvent")){
            //present on 1.12.2 and 1.16.5
            INSTANCE = new MohistForgeListener();
        }else if (FCReflectionUtil.getClasses().isClassLoaded("catserver.api.bukkit.ForgeEventV2")){
            //present on 1.16.5; CatServer is served by the Mohist bridge and has no adapter of its own
            INSTANCE = new MohistForgeListener();
        }
    }

    /**
     * Whether this server has a hybrid Forge platform behind it - the cheap question anything
     * bridging to the Forge side asks before it builds anything.
     *
     * <p>Answering it is what runs the detection above, so the first caller pays for building the
     * implementation and everyone after it reads a field.</p>
     */
    public static boolean isAvailable(){
        return INSTANCE != null;
    }

    public static void registerListener(Plugin plugin, ECListener listener, Object... eventBus){
        if (INSTANCE == null){
            throw new IllegalStateException("Tried to register ForgeEvents but there is no IForgeListener available for EverNifeCore on this Server.");
        }
        INSTANCE.registerListener(plugin, listener, eventBus);
    }

    public static void registerListener(Plugin plugin, ECListener listener){
        if (INSTANCE == null){
            throw new IllegalStateException("Tried to register ForgeEvents but there is no IForgeListener available for EverNifeCore on this Server.");
        }
        INSTANCE.registerListener(plugin, listener);
    }

}
